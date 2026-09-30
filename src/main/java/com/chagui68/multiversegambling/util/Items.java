package com.chagui68.multiversegambling.util;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Item builder for the menus, with colour and optional glow.
 */
public final class Items {

    private Items() {
    }

    public static Builder of(Material material) {
        return new Builder(material);
    }

    /**
     * Decorative item with no name or actions, used to fill borders.
     */
    public static ItemStack filler(Material material) {
        return new Builder(material).name(" ").build();
    }

    public static final class Builder {

        private final Material material;
        private final List<String> lore = new ArrayList<>();
        private int amount = 1;
        private String name;
        private Boolean glow;

        private Builder(Material material) {
            this.material = material;
        }

        public Builder name(String legacyName) {
            this.name = legacyName;
            return this;
        }

        public Builder lore(String... lines) {
            return lore(java.util.Arrays.asList(lines));
        }

        public Builder lore(List<String> lines) {
            for (String line : lines) {
                if (line != null) {
                    this.lore.add(line);
                }
            }
            return this;
        }

        public Builder loreIf(boolean condition, String line) {
            return condition ? lore(line) : this;
        }

        public Builder blank() {
            this.lore.add("");
            return this;
        }

        public Builder amount(int amount) {
            this.amount = Math.max(1, Math.min(64, amount));
            return this;
        }

        public Builder glow(boolean value) {
            this.glow = value;
            return this;
        }

        public ItemStack build() {
            ItemStack stack = new ItemStack(material, amount);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                if (name != null) {
                    meta.displayName(Text.c(name));
                }
                if (!lore.isEmpty()) {
                    meta.lore(Text.lines(lore));
                }
                if (glow != null) {
                    meta.setEnchantmentGlintOverride(glow);
                }
                stack.setItemMeta(meta);
            }
            return stack;
        }
    }
}
