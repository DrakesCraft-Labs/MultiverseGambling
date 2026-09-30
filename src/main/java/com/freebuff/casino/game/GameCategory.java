package com.freebuff.casino.game;

import org.bukkit.Material;

/** Las dos familias del catalogo. */
public enum GameCategory {

    SOLO("Solitario", "Apuestas contra la casa, a tu ritmo.", Material.PLAYER_HEAD),
    GRUPO("En grupo", "Apuestas contra y con otros jugadores.", Material.PLAYER_HEAD);

    private final String label;
    private final String description;
    private final Material tabIcon;

    GameCategory(String label, String description, Material tabIcon) {
        this.label = label;
        this.description = description;
        this.tabIcon = tabIcon;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public Material tabIcon() {
        return tabIcon;
    }

    public GameCategory other() {
        return this == SOLO ? GRUPO : SOLO;
    }
}
