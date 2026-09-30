package com.chagui68.multiversegambling.game;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

/** Ficha tecnica de un juego: como se llama, como se ve y cuanta gente admite. */
public record GameMeta(
        String id,
        String name,
        GameCategory category,
        Material icon,
        List<String> description,
        String permission,
        int minPlayers,
        int maxPlayers) {

    public static Builder builder(String id, String name, GameCategory category, Material icon) {
        return new Builder(id, name, category, icon);
    }

    public static final class Builder {

        private final String id;
        private final String name;
        private final GameCategory category;
        private final Material icon;
        private final List<String> description = new ArrayList<>();
        private String permission = "casino.play";
        private int minPlayers = 1;
        private int maxPlayers = 1;

        private Builder(String id, String name, GameCategory category, Material icon) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.icon = icon;
        }

        public Builder desc(String... lines) {
            for (String line : lines) {
                description.add(line);
            }
            return this;
        }

        public Builder permission(String permission) {
            this.permission = permission;
            return this;
        }

        public Builder players(int min, int max) {
            this.minPlayers = min;
            this.maxPlayers = max;
            return this;
        }

        public GameMeta build() {
            return new GameMeta(id, name, category, icon, List.copyOf(description),
                    permission, minPlayers, maxPlayers);
        }
    }
}
