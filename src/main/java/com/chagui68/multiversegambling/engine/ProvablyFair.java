package com.chagui68.multiversegambling.engine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Provably fair randomness, in the style of serious betting houses.
 *
 * <p>The house publishes the hash of its secret before you play. Every roll is derived
 * from {@code HMAC_SHA256(secret, clientSeed:nonce:cursor)}. When the secret rotates it
 * is revealed, and anybody can recompute every roll to check that nothing was touched.
 * This is what makes roulette, crash or plinko auditable.</p>
 */
public final class ProvablyFair {

    private static final SecureRandom SECURE = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ProvablyFair() {
    }

    /**
     * 256 bit server secret, in hexadecimal.
     */
    public static String randomSeed() {
        byte[] bytes = new byte[32];
        SECURE.nextBytes(bytes);
        return hex(bytes);
    }

    /**
     * Reasonable client seed for when the player does not bring their own.
     */
    public static String randomClientSeed() {
        byte[] bytes = new byte[8];
        SECURE.nextBytes(bytes);
        return hex(bytes);
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static byte[] hmacSha256(String key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    private static double message(String serverSeed, String clientSeed, long nonce, int cursor) {
        byte[] mac = hmacSha256(serverSeed, clientSeed + ":" + nonce + ":" + cursor);
        // Careful: this must be widened to an unsigned long. With an int, ">>> 0" does
        // nothing and half of the rolls would come out negative.
        long value = ((long) (mac[0] & 0xFF) << 24)
                | ((mac[1] & 0xFF) << 16)
                | ((mac[2] & 0xFF) << 8)
                | (mac[3] & 0xFF);
        return value / 4294967296.0;
    }

    /**
     * Uniform in [0, 1) for roll {@code cursor} of a given nonce.
     */
    public static double roll(String serverSeed, String clientSeed, long nonce, int cursor) {
        return message(serverSeed, clientSeed, nonce, cursor);
    }

    /**
     * Verified uniform integer in [0, bound).
     */
    public static int rollInt(String serverSeed, String clientSeed, long nonce, int cursor, int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be > 0");
        }
        return (int) Math.floor(message(serverSeed, clientSeed, nonce, cursor) * bound);
    }

    /**
     * Generates {@code count} consecutive uniform values (a multi point roll).
     */
    public static double[] rollMany(String serverSeed, String clientSeed, long nonce, int count) {
        double[] out = new double[count];
        for (int i = 0; i < count; i++) {
            out[i] = message(serverSeed, clientSeed, nonce, i);
        }
        return out;
    }

    private static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            out[i * 2] = HEX[b >>> 4];
            out[i * 2 + 1] = HEX[b & 0x0F];
        }
        return new String(out);
    }

    /**
     * Console helper: {@code ProvablyFair <hash|seed|roll> ...}.
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            String seed = randomSeed();
            System.out.println("server = " + seed);
            System.out.println("hash   = " + sha256(seed));
            System.out.println("roll   = " + roll(seed, "cliente", 0, 0));
            return;
        }
        switch (args[0]) {
            case "hash" -> System.out.println(sha256(args[1]));
            case "seed" -> System.out.println(randomSeed());
            case "roll" -> System.out.println(HexFormat.of().formatHex(
                    hmacSha256(args[1], args[2] + ":" + args[3] + ":" + (args.length > 4 ? args[4] : "0"))));
            default -> System.out.println("Uso: hash <texto> | seed | roll <server> <client> <nonce> [cursor]");
        }
    }
}
