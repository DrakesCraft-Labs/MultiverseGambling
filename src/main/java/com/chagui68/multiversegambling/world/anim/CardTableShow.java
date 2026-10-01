package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Card;
import com.chagui68.multiversegambling.util.Text;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.joml.Vector3f;

/**
 * A card table: a green felt table in front of a dark board where the cards are hung
 * face out, the house on the top row and the player on the bottom one, each row with
 * its running total.
 *
 * <p>Every card is a text display with a white background, so it reads like a real
 * card from across the pavilion. New cards fly in from the shoe on the right and grow
 * into place; a face down card turns over when it is revealed. The cards come from the
 * game, which drew them already: the table only shows them.</p>
 */
public final class CardTableShow extends ArenaShow {

    private static final float CARD = 1.9f;
    private static final float CARD_GAP = 1.25f;
    private static final float TOP_Y = 4.6f;
    private static final float BOTTOM_Y = 2.45f;
    private static final float BOARD_Z = -0.55f;
    private static final Color FACE = Color.fromARGB(255, 250, 250, 242);
    private static final Color BACK = Color.fromARGB(255, 150, 22, 28);
    private static final Vector3f SHOE = new Vector3f(5.6f, 1.9f, 0.6f);

    private final String topName;
    private final String bottomName;
    /**
     * The cards a row shows right now.
     */
    private static final class Row {
        private final List<TextDisplay> displays = new ArrayList<>();
        private final List<Card> cards = new ArrayList<>();
        private final List<Boolean> hidden = new ArrayList<>();
    }

    private final Row topRow = new Row();
    private final Row bottomRow = new Row();
    private Location anchor;
    private TextDisplay topLabel;
    private TextDisplay bottomLabel;
    private boolean finished;

    /**
     * @param topName    label of the top row, such as the dealer
     * @param bottomName label of the bottom row, such as the player
     */
    public CardTableShow(MultiverseGamblingPlugin plugin, ArenaStage stage, String topName, String bottomName,
                         int ticks) {
        super(plugin, stage, ticks);
        this.topName = topName == null ? "" : topName;
        this.bottomName = bottomName == null ? "" : bottomName;
    }

    @Override
    protected void onStart() {
        anchor = local(0, 0, 0);
        // The board the cards hang on, in a golden frame.
        block(anchor, Material.GREEN_TERRACOTTA, Props.box(new Vector3f(0, 3.7f, BOARD_Z - 0.15f),
                new Vector3f(10.4f, 5.0f, 0.2f)));
        block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(0, 6.25f, BOARD_Z - 0.15f), new Vector3f(10.7f, 0.14f, 0.3f)));
        block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(0, 1.15f, BOARD_Z - 0.15f), new Vector3f(10.7f, 0.14f, 0.3f)));
        for (int side : new int[]{-1, 1}) {
            block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(side * 5.28f, 3.7f, BOARD_Z - 0.15f),
                    new Vector3f(0.14f, 5.2f, 0.3f)));
        }
        block(anchor, Material.WHITE_CONCRETE, Props.box(new Vector3f(0, 3.55f, BOARD_Z - 0.03f),
                new Vector3f(9.6f, 0.05f, 0.03f)));
        // The table in front of it: felt, a dark wooden rim and the shoe on the right.
        block(anchor, Material.GREEN_WOOL, Props.box(new Vector3f(0, 1.0f, 1.0f), new Vector3f(10.0f, 0.2f, 2.6f)));
        block(anchor, Material.DARK_OAK_PLANKS, Props.box(new Vector3f(0, 1.05f, 2.35f), new Vector3f(10.4f, 0.32f, 0.3f)));
        block(anchor, Material.DARK_OAK_PLANKS, Props.box(new Vector3f(0, 0.45f, 1.0f), new Vector3f(9.4f, 0.9f, 2.2f)));
        block(anchor, Material.BLACK_CONCRETE, Props.box(new Vector3f(SHOE.x, 1.4f, SHOE.z), new Vector3f(1.0f, 0.6f, 1.2f)));
        block(anchor, Material.RED_CONCRETE, Props.box(new Vector3f(SHOE.x, 1.72f, SHOE.z), new Vector3f(0.7f, 0.06f, 0.9f)));

        topLabel = text(local(-3.4, TOP_Y + 1.15, BOARD_Z), Text.c("&f&l" + topName), Props.scaled(1.0f),
                Display.Billboard.FIXED);
        topLabel.setAlignment(TextDisplay.TextAlignment.LEFT);
        bottomLabel = text(local(-3.4, BOTTOM_Y + 0.3, BOARD_Z), Text.c("&e&l" + bottomName),
                Props.scaled(1.0f), Display.Billboard.FIXED);
        bottomLabel.setAlignment(TextDisplay.TextAlignment.LEFT);
        playSound(Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 0.8f);
    }

    /**
     * Brings the table up to date: deals the cards that are new, turns over a card that
     * was revealed or replaced, takes away the ones that are gone and refreshes the
     * totals.
     *
     * @param top         cards of the top row
     * @param hideFirst   true while the second card of the top row is face down
     * @param topTotal    text of the top total, empty to hide it
     * @param bottom      cards of the bottom row
     * @param bottomTotal text of the bottom total
     */
    public void deal(List<Card> top, boolean hideFirst, String topTotal, List<Card> bottom, String bottomTotal) {
        if (anchor == null || finished) {
            return;
        }
        int delay = syncRow(topRow, top, hideFirst, TOP_Y, 0);
        syncRow(bottomRow, bottom, false, BOTTOM_Y, delay);
        if (topLabel.isValid()) {
            topLabel.text(Text.c("&f&l" + topName + (topTotal == null || topTotal.isEmpty() ? "" : "  &7" + topTotal)));
        }
        if (bottomLabel.isValid()) {
            bottomLabel.text(Text.c("&e&l" + bottomName
                    + (bottomTotal == null || bottomTotal.isEmpty() ? "" : "  &7" + bottomTotal)));
        }
    }

    /**
     * Makes one row show these cards.
     *
     * @return the delay the next new card should fly in with
     */
    private int syncRow(Row row, List<Card> cards, boolean hideSecond, float rowY, int delay) {
        while (row.displays.size() > cards.size()) {
            int last = row.displays.size() - 1;
            discard(row.displays.remove(last));
            row.cards.remove(last);
            row.hidden.remove(last);
        }
        for (int i = 0; i < cards.size(); i++) {
            boolean hidden = hideSecond && i == 1;
            Card card = cards.get(i);
            if (i < row.displays.size()) {
                if (row.hidden.get(i) != hidden || !row.cards.get(i).equals(card)) {
                    flip(row.displays.get(i), card, hidden);
                    row.cards.set(i, card);
                    row.hidden.set(i, hidden);
                }
                continue;
            }
            row.displays.add(fly(card, hidden, cards.size(), i, rowY, delay));
            row.cards.add(card);
            row.hidden.add(hidden);
            delay += 4;
        }
        // A row that grew pushes its old cards aside so the row stays centred.
        for (int i = 0; i < row.displays.size(); i++) {
            TextDisplay display = row.displays.get(i);
            // Cards still in flight find their own slot when they land.
            if (display.isValid() && display.getTransformation().getScale().x >= CARD * 0.9f) {
                display.teleport(slot(cards.size(), i, rowY));
            }
        }
        return delay;
    }

    /**
     * The round is over: a banner over the board and a party when the player won.
     */
    public void result(String legacy, boolean won) {
        if (anchor == null || finished) {
            return;
        }
        finished = true;
        TextDisplay banner = text(local(0, 6.7, BOARD_Z + 0.1), Text.c(legacy), Props.scaled(0.1f),
                Display.Billboard.FIXED);
        banner.setBackgroundColor(Props.argb(170, 0, 0, 0));
        later(1, () -> Props.animate(banner, Props.scaled(2.4f), 6));
        List<TextDisplay> winners = won ? bottomRow.displays : topRow.displays;
        for (TextDisplay card : winners) {
            if (card.isValid()) {
                card.setGlowColorOverride(won ? Color.fromRGB(0xFFD700) : Color.RED);
                card.setGlowing(true);
            }
        }
        if (won) {
            celebrate(0, BOTTOM_Y + 0.8, BOARD_Z + 0.6);
        } else {
            particles(Particle.LARGE_SMOKE, 0, BOTTOM_Y + 0.8, BOARD_Z + 0.6, 10, 1.0, 0.01);
            playSound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.6f);
        }
    }

    // ----------------------------------------------------------------- cards

    private TextDisplay fly(Card card, boolean hidden, int count, int index, float rowY, int delay) {
        TextDisplay display = text(local(SHOE.x, SHOE.y, SHOE.z), face(card, hidden), Props.scaled(0.2f),
                Display.Billboard.FIXED);
        display.setBackgroundColor(hidden ? BACK : FACE);
        display.setShadowed(false);
        display.setLineWidth(60);
        display.setTeleportDuration(6);
        later(1 + delay, () -> {
            if (!display.isValid()) {
                return;
            }
            display.teleport(slot(count, index, rowY));
            Props.animate(display, Props.scaled(CARD), 6);
            playSound(Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.3f);
        });
        return display;
    }

    private void flip(TextDisplay display, Card card, boolean hidden) {
        Props.animate(display, Props.centred(new Vector3f(), Props.none(), new Vector3f(0.02f, CARD, CARD)), 3);
        later(3, () -> {
            if (!display.isValid()) {
                return;
            }
            display.text(face(card, hidden));
            display.setBackgroundColor(hidden ? BACK : FACE);
            Props.animate(display, Props.scaled(CARD), 3);
        });
        playSound(Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
    }

    private Location slot(int count, int index, float rowY) {
        float gap = Math.min(CARD_GAP, 9.0f / Math.max(1, count));
        float x = (index - (count - 1) / 2.0f) * gap;
        return local(x, rowY - 0.9f, BOARD_Z);
    }

    private static Component face(Card card, boolean hidden) {
        if (hidden) {
            return Text.c("&f&l✦\n&f&l✦");
        }
        boolean red = card.suit() == Card.Suit.HEARTS || card.suit() == Card.Suit.DIAMONDS;
        String colour = red ? "&4&l" : "&0&l";
        return Text.c(colour + card.rankLabel() + "\n" + colour + card.suit().glyph());
    }

    @Override
    protected void onSettle() {
        finished = true;
    }

    @Override
    protected int extraLinger() {
        return 40;
    }
}
