package com.chagui68.multiversegambling.engine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Azar verificable ("provably fair") al estilo de las casas de apuestas serias.
 *
 * <p>La casa publica el hash del secreto antes de jugar. Cada tirada se deriva de
 * {@code HMAC_SHA256(secreto, semillaCliente:nonce:cursor)}. Al rotar el secreto se
 * revela, y cualquiera puede recalcular todas las tiradas para comprobar que no hubo
 * manipulación. Es lo que permite que la ruleta, el crash o el plinko sean auditables.</p>
 */
public final class ProvablyFair {

    private static final SecureRandom SECURE = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ProvablyFair() {
    }

    /** Secreto de servidor de 256 bits en hexadecimal. */
    public static String randomSeed() {
        byte[] bytes = new byte[32];
        SECURE.nextBytes(bytes);
        return hex(bytes);
    }

    /** Semilla de cliente razonable cuando el jugador no aporta la suya. */
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
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static byte[] hmacSha256(String key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 no disponible", e);
        }
    }

    private static double message(String serverSeed, String clientSeed, long nonce, int cursor) {
        byte[] mac = hmacSha256(serverSeed, clientSeed + ":" + nonce + ":" + cursor);
        // Ojo: hay que pasar a long sin signo. Con un int, ">>> 0" no hace nada y
        // la mitad de las tiradas saldrian negativas.
        long value = ((long) (mac[0] & 0xFF) << 24)
                | ((mac[1] & 0xFF) << 16)
                | ((mac[2] & 0xFF) << 8)
                | (mac[3] & 0xFF);
        return value / 4294967296.0;
    }

    /** Uniforme en [0, 1) para la tirada {@code cursor} de un mismo nonce. */
    public static double roll(String serverSeed, String clientSeed, long nonce, int cursor) {
        return message(serverSeed, clientSeed, nonce, cursor);
    }

    /** Entero uniforme en [0, bound) verificado. */
    public static int rollInt(String serverSeed, String clientSeed, long nonce, int cursor, int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound debe ser > 0");
        }
        return (int) Math.floor(message(serverSeed, clientSeed, nonce, cursor) * bound);
    }

    /** Genera {@code count} valores uniformes consecutivos (una tirada multipunto). */
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

    /** Utilidad de consola: {@code ProvablyFair <hash|seed|roll> ...}. */
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
