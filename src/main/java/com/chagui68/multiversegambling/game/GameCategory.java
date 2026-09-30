package com.chagui68.multiversegambling.game;

import org.bukkit.Material;

/**
 * The two families of the catalogue.
 *
 * <p>The label and the description are only a fallback: the menus read
 * {@code gui.category.<key>.name} and {@code gui.category.<key>.description} first,
 * so a translated language file can rename the tabs.</p>
 */
public enum GameCategory {

    SOLO("solo", "Solo", "Bets against the house, at your own pace.", Material.PLAYER_HEAD),
    GROUP("group", "Group", "Bets against and with other players.", Material.PLAYER_HEAD);

    private final String key;
    private final String label;
    private final String description;
    private final Material tabIcon;

    GameCategory(String key, String label, String description, Material tabIcon) {
        this.key = key;
        this.label = label;
        this.description = description;
        this.tabIcon = tabIcon;
    }

    /**
     * Message key fragment: {@code gui.category.<key>.*}.
     */
    public String key() {
        return key;
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
        return this == SOLO ? GROUP : SOLO;
    }
}
