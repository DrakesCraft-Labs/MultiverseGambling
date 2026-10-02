package com.chagui68.multiversegambling.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The rules of a no limit Texas hold'em table, free of Bukkit.
 *
 * <p>Amounts are whole chips ({@code long}), which the game maps to hundredths of the
 * currency, so no pot is ever split with floating point. The table never waits for
 * anybody: the game asks who is to act ({@link #toAct()}), what that player may do
 * ({@link #options(int)}) and hands the choice back with {@link #act(int, Action, long)}.
 * When a betting round is over the table stops in {@link Phase#STREET_DONE} until the
 * game calls {@link #nextStreet()}, which is what lets the game pace the deal with its
 * animations.</p>
 *
 * <p>The rules followed are the usual ones: the dealer button moves one seat per hand,
 * two players play heads-up with the button on the small blind, a raise must be at least
 * as big as the previous one, an all-in for less than a full raise does not reopen the
 * betting for players who already acted, money nobody called goes back, and the pots are
 * split into side pots by what every player could cover. Odd chips of a split pot go to
 * the winners closest to the left of the button.</p>
 */
public final class PokerTable {

    /** Where the hand is. */
    public enum Phase {
        /** No hand being played. */
        IDLE,
        /** Waiting for {@link #toAct()} to act. */
        BETTING,
        /** A betting round finished: the game calls {@link #nextStreet()}. */
        STREET_DONE,
        /** The hand is over and {@link #results()} holds the awards. */
        FINISHED
    }

    /** The betting rounds. */
    public enum Street {
        PREFLOP, FLOP, TURN, RIVER
    }

    /** What a player can do. */
    public enum Action {
        FOLD, CHECK, CALL, RAISE, ALL_IN
    }

    /**
     * What the player to act may do right now.
     *
     * @param toCall     chips needed to call, 0 when checking is possible
     * @param canCheck   true when nothing has to be called
     * @param callAmount chips a call really puts in (less than {@code toCall} when short)
     * @param canRaise   true when a bet or raise is allowed
     * @param minRaiseTo smallest total bet a raise can reach
     * @param maxRaiseTo largest total bet: everything the player has
     * @param canAllIn   true when going all in is allowed
     * @param betting    true when nobody has bet yet this round, so a raise is a bet
     */
    public record Options(long toCall, boolean canCheck, long callAmount, boolean canRaise,
                          long minRaiseTo, long maxRaiseTo, boolean canAllIn, boolean betting) {
    }

    /**
     * One pot (main or side) and who took it.
     *
     * @param amount  chips in the pot, after the rake
     * @param rake    chips the house took from it
     * @param winners seats that split it
     * @param shares  chips each winner got, in the order of {@code winners}
     * @param hand    the winning hand, or {@code null} when nobody had to show
     */
    public record Award(long amount, long rake, List<Integer> winners, List<Long> shares,
                        PokerHand.Result hand) {
    }

    /**
     * One player at the table.
     */
    public static final class Seat {
        private final UUID id;
        private long stack;
        private boolean inHand;
        private boolean folded;
        private boolean allIn;
        private long bet;
        private long contributed;
        private boolean acted;
        private boolean canRaise;
        private boolean sittingOut;
        private final List<Card> hole = new ArrayList<>(2);

        Seat(UUID id, long stack) {
            this.id = id;
            this.stack = stack;
        }

        public UUID id() {
            return id;
        }

        public long stack() {
            return stack;
        }

        /** True when the seat was dealt in the hand being played. */
        public boolean inHand() {
            return inHand;
        }

        public boolean folded() {
            return folded;
        }

        public boolean allIn() {
            return allIn;
        }

        /** Chips in front of the player in this betting round. */
        public long bet() {
            return bet;
        }

        /** Chips the player put in during the whole hand. */
        public long contributed() {
            return contributed;
        }

        public boolean sittingOut() {
            return sittingOut;
        }

        public List<Card> hole() {
            return Collections.unmodifiableList(hole);
        }

        /** Still fighting for the pot. */
        public boolean live() {
            return inHand && !folded;
        }

        /** Still able to take decisions this hand. */
        boolean canAct() {
            return inHand && !folded && !allIn;
        }
    }

    private final Seat[] seats;
    private final List<Card> board = new ArrayList<>(5);
    private final List<Award> results = new ArrayList<>();
    private Card.Deck deck;
    private long smallBlind;
    private long bigBlind;
    private double rakePercent;
    private long rakeCap;
    private Phase phase = Phase.IDLE;
    private Street street = Street.PREFLOP;
    private int button = -1;
    private int smallBlindSeat = -1;
    private int bigBlindSeat = -1;
    private int toAct = -1;
    private long currentBet;
    private long minRaise;
    private int hands;
    private boolean uncontested;

    public PokerTable(int seatCount, long smallBlind, long bigBlind) {
        this.seats = new Seat[Math.max(2, seatCount)];
        blinds(smallBlind, bigBlind);
    }

    // --------------------------------------------------------------- settings

    /**
     * Changes the blinds; they apply from the next hand on.
     */
    public void blinds(long small, long big) {
        this.bigBlind = Math.max(1, big);
        this.smallBlind = Math.max(1, Math.min(this.bigBlind, small));
    }

    /**
     * The share of every pot the house keeps once the flop was dealt, up to a cap per
     * pot (0 for no cap). A pot won before the flop is never raked.
     */
    public void rake(double percent, long cap) {
        this.rakePercent = Math.max(0.0, Math.min(0.5, percent));
        this.rakeCap = Math.max(0, cap);
    }

    public long smallBlind() {
        return smallBlind;
    }

    public long bigBlind() {
        return bigBlind;
    }

    // ------------------------------------------------------------------ seats

    public int size() {
        return seats.length;
    }

    public Seat seat(int index) {
        return index >= 0 && index < seats.length ? seats[index] : null;
    }

    /**
     * Seat of a player, or -1.
     */
    public int seatOf(UUID id) {
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] != null && seats[i].id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * First free seat, or -1 when the table is full.
     */
    public int freeSeat() {
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] == null) {
                return i;
            }
        }
        return -1;
    }

    public int occupied() {
        int count = 0;
        for (Seat seat : seats) {
            if (seat != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * Sits a player down with that many chips.
     */
    public boolean sit(int index, UUID id, long stack) {
        if (index < 0 || index >= seats.length || seats[index] != null || seatOf(id) >= 0 || stack <= 0) {
            return false;
        }
        seats[index] = new Seat(id, stack);
        return true;
    }

    /**
     * Adds chips to a seat between hands.
     */
    public boolean addChips(int index, long chips) {
        Seat seat = seat(index);
        if (seat == null || chips <= 0 || (phase != Phase.IDLE && phase != Phase.FINISHED && seat.inHand)) {
            return false;
        }
        seat.stack += chips;
        return true;
    }

    /**
     * Takes a player away from the table and returns the chips they leave with. Allowed
     * only for a seat that was not dealt in the hand being played: whatever a player put
     * in the pot has to stay there until the hand is over, so the game removes a player
     * who wants to go once the hand ends.
     *
     * @return the chips the player takes away, or -1 when the seat cannot be left now
     */
    public long leave(int index) {
        Seat seat = seat(index);
        if (seat == null) {
            return -1;
        }
        if (handRunning() && seat.inHand) {
            return -1;
        }
        seats[index] = null;
        return seat.stack;
    }

    /**
     * A sitting out player keeps the seat but is not dealt in.
     */
    public void sitOut(int index, boolean out) {
        Seat seat = seat(index);
        if (seat != null) {
            seat.sittingOut = out;
        }
    }

    /**
     * Seats that would be dealt in if a hand started now.
     */
    public int ready() {
        int count = 0;
        for (Seat seat : seats) {
            if (seat != null && !seat.sittingOut && seat.stack > 0) {
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- state

    public Phase phase() {
        return phase;
    }

    public Street street() {
        return street;
    }

    /** True from the deal until the results are in. */
    public boolean handRunning() {
        return phase == Phase.BETTING || phase == Phase.STREET_DONE;
    }

    public List<Card> board() {
        return Collections.unmodifiableList(board);
    }

    public int button() {
        return button;
    }

    public int smallBlindSeat() {
        return smallBlindSeat;
    }

    public int bigBlindSeat() {
        return bigBlindSeat;
    }

    /** Seat that has to act, or -1. */
    public int toAct() {
        return phase == Phase.BETTING ? toAct : -1;
    }

    /** Highest bet of the betting round. */
    public long currentBet() {
        return currentBet;
    }

    /** Hands dealt so far. */
    public int hands() {
        return hands;
    }

    /** True when the hand ended because everybody else folded. */
    public boolean uncontested() {
        return uncontested;
    }

    /** All the chips in the middle: the pots plus the bets of this round. */
    public long pot() {
        long total = 0;
        for (Seat seat : seats) {
            if (seat != null) {
                total += seat.contributed;
            }
        }
        return total;
    }

    /** Chips already gathered in the middle, not counting the bets of this round. */
    public long collected() {
        long total = 0;
        for (Seat seat : seats) {
            if (seat != null) {
                total += seat.contributed - seat.bet;
            }
        }
        return total;
    }

    public List<Award> results() {
        return Collections.unmodifiableList(results);
    }

    /** Players still fighting for the pot. */
    public int liveCount() {
        int count = 0;
        for (Seat seat : seats) {
            if (seat != null && seat.live()) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ hand

    /**
     * Deals a new hand: moves the button, posts the blinds and deals two cards to every
     * player that has chips and is not sitting out.
     *
     * @return false when fewer than two players can be dealt in
     */
    public boolean startHand(Card.Deck shuffled) {
        if (handRunning() || ready() < 2) {
            return false;
        }
        deck = shuffled;
        board.clear();
        results.clear();
        uncontested = false;
        street = Street.PREFLOP;
        for (Seat seat : seats) {
            if (seat == null) {
                continue;
            }
            seat.inHand = !seat.sittingOut && seat.stack > 0;
            seat.folded = false;
            seat.allIn = false;
            seat.bet = 0;
            seat.contributed = 0;
            seat.acted = false;
            seat.canRaise = true;
            seat.hole.clear();
        }
        button = nextDealt(button);
        boolean headsUp = dealtCount() == 2;
        smallBlindSeat = headsUp ? button : nextDealt(button);
        bigBlindSeat = nextDealt(smallBlindSeat);

        put(seats[smallBlindSeat], Math.min(smallBlind, seats[smallBlindSeat].stack));
        put(seats[bigBlindSeat], Math.min(bigBlind, seats[bigBlindSeat].stack));
        currentBet = Math.max(seats[smallBlindSeat].bet, seats[bigBlindSeat].bet);
        if (!headsUp) {
            // A big blind all in for less still leaves the others a full blind to call.
            currentBet = Math.max(currentBet, bigBlind);
        }
        minRaise = bigBlind;

        for (int round = 0; round < 2; round++) {
            int index = smallBlindSeat;
            for (int i = 0; i < dealtCount(); i++) {
                seats[index].hole.add(deck.draw());
                index = nextDealt(index);
            }
        }
        hands++;
        phase = Phase.BETTING;
        toAct = nextActor(bigBlindSeat);
        if (toAct < 0 || roundComplete()) {
            phase = Phase.STREET_DONE;
            toAct = -1;
        }
        return true;
    }

    /**
     * What the player in that seat may do, or {@code null} when it is not their turn.
     */
    public Options options(int index) {
        if (phase != Phase.BETTING || index != toAct) {
            return null;
        }
        Seat seat = seats[index];
        long toCall = Math.max(0, currentBet - seat.bet);
        long callAmount = Math.min(toCall, seat.stack);
        long maxTo = seat.bet + seat.stack;
        boolean opponents = othersCanAct(index);
        long minTo = currentBet == 0 ? Math.min(bigBlind, maxTo) : currentBet + minRaise;
        boolean canRaise = seat.canRaise && opponents && maxTo > currentBet && maxTo >= minTo;
        boolean canAllIn = seat.stack > 0 && (seat.stack <= toCall || (seat.canRaise && opponents));
        return new Options(toCall, toCall == 0, callAmount, canRaise, Math.min(minTo, maxTo), maxTo,
                canAllIn, currentBet == 0);
    }

    /**
     * The player to act takes a decision.
     *
     * @param raiseTo total bet for this round when raising; ignored otherwise
     * @return false when the move is not allowed, in which case nothing changes
     */
    public boolean act(int index, Action action, long raiseTo) {
        Options options = options(index);
        if (options == null) {
            return false;
        }
        Seat seat = seats[index];
        switch (action) {
            case FOLD -> seat.folded = true;
            case CHECK -> {
                if (!options.canCheck()) {
                    return false;
                }
            }
            case CALL -> {
                if (options.toCall() == 0) {
                    return false;
                }
                put(seat, options.callAmount());
            }
            case RAISE -> {
                if (!options.canRaise() || raiseTo < options.minRaiseTo() || raiseTo > options.maxRaiseTo()) {
                    return false;
                }
                raiseTo(index, raiseTo);
            }
            case ALL_IN -> {
                if (!options.canAllIn()) {
                    return false;
                }
                long total = seat.bet + seat.stack;
                if (total <= currentBet) {
                    put(seat, seat.stack);
                } else {
                    raiseTo(index, total);
                }
            }
        }
        seat.acted = true;
        advance(index);
        return true;
    }

    private void raiseTo(int index, long total) {
        Seat seat = seats[index];
        long increase = total - currentBet;
        put(seat, total - seat.bet);
        boolean full = increase >= minRaise;
        if (full) {
            minRaise = increase;
        }
        currentBet = total;
        for (int i = 0; i < seats.length; i++) {
            Seat other = seats[i];
            if (i == index || other == null || !other.canAct()) {
                continue;
            }
            if (full) {
                other.canRaise = true;
            } else if (other.acted) {
                // A short all-in only asks for the difference: it does not reopen the
                // betting for whoever already had their say.
                other.canRaise = false;
            }
            other.acted = false;
        }
    }

    private void advance(int lastActor) {
        if (liveCount() == 1) {
            finishUncontested();
            return;
        }
        if (roundComplete()) {
            phase = Phase.STREET_DONE;
            toAct = -1;
            return;
        }
        toAct = nextActor(lastActor);
        if (toAct < 0) {
            phase = Phase.STREET_DONE;
        }
    }

    /**
     * Moves on after a finished betting round: gathers the bets, gives back what nobody
     * called, then deals the next street or goes to the showdown.
     */
    public void nextStreet() {
        if (phase != Phase.STREET_DONE) {
            return;
        }
        returnUncalled();
        for (Seat seat : seats) {
            if (seat != null) {
                seat.bet = 0;
                seat.acted = false;
                seat.canRaise = true;
            }
        }
        currentBet = 0;
        minRaise = bigBlind;
        if (liveCount() == 1) {
            finishUncontested();
            return;
        }
        if (street == Street.RIVER) {
            showdown();
            return;
        }
        // One card burned before every street, as at a real table.
        deck.draw();
        switch (street) {
            case PREFLOP -> {
                board.add(deck.draw());
                board.add(deck.draw());
                board.add(deck.draw());
                street = Street.FLOP;
            }
            case FLOP -> {
                board.add(deck.draw());
                street = Street.TURN;
            }
            default -> {
                board.add(deck.draw());
                street = Street.RIVER;
            }
        }
        if (actorsLeft() < 2) {
            // Everybody else is all in: the board is simply run out.
            phase = Phase.STREET_DONE;
            toAct = -1;
            return;
        }
        phase = Phase.BETTING;
        toAct = nextActor(button);
    }

    /**
     * True when the betting is over for the rest of the hand and the remaining cards will
     * only be dealt out: at most one player can still act.
     */
    public boolean runningOut() {
        return handRunning() && actorsLeft() < 2;
    }

    /**
     * Abandons the hand being played and gives every player back what they put in it.
     */
    public void abortHand() {
        if (!handRunning()) {
            return;
        }
        for (Seat seat : seats) {
            if (seat != null) {
                seat.stack += seat.contributed;
                seat.contributed = 0;
                seat.bet = 0;
                seat.inHand = false;
                seat.hole.clear();
            }
        }
        board.clear();
        results.clear();
        phase = Phase.IDLE;
        toAct = -1;
    }

    // -------------------------------------------------------------- endings

    private void finishUncontested() {
        returnUncalled();
        int winner = -1;
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] != null && seats[i].live()) {
                winner = i;
            }
        }
        long total = pot();
        long rake = rakeOf(total);
        results.clear();
        if (winner >= 0) {
            seats[winner].stack += total - rake;
            results.add(new Award(total - rake, rake, List.of(winner), List.of(total - rake), null));
        }
        uncontested = true;
        clearContributions();
        phase = Phase.FINISHED;
        toAct = -1;
    }

    private void showdown() {
        List<Pot> pots = buildPots();
        results.clear();
        for (Pot pot : pots) {
            long rake = rakeOf(pot.amount);
            long amount = pot.amount - rake;
            PokerHand.Result best = null;
            List<Integer> winners = new ArrayList<>();
            for (int index : pot.eligible) {
                PokerHand.Result hand = PokerHand.best(seats[index].hole, board);
                if (best == null || hand.score() > best.score()) {
                    best = hand;
                    winners.clear();
                    winners.add(index);
                } else if (hand.score() == best.score()) {
                    winners.add(index);
                }
            }
            winners = fromButton(winners);
            List<Long> shares = split(amount, winners.size());
            for (int i = 0; i < winners.size(); i++) {
                seats[winners.get(i)].stack += shares.get(i);
            }
            results.add(new Award(amount, rake, List.copyOf(winners), List.copyOf(shares), best));
        }
        clearContributions();
        phase = Phase.FINISHED;
        toAct = -1;
    }

    /**
     * What the house keeps of a pot: nothing before the flop ("no flop, no drop").
     */
    private long rakeOf(long amount) {
        if (rakePercent <= 0 || board.isEmpty()) {
            return 0;
        }
        long rake = (long) Math.floor(amount * rakePercent);
        return rakeCap > 0 ? Math.min(rake, rakeCap) : rake;
    }

    /** A pot and the seats that can win it. */
    private record Pot(long amount, Set<Integer> eligible) {
    }

    /**
     * Splits everything put in this hand into a main pot and side pots: each pot is
     * capped by the smallest stake still in the fight, and can only be won by those who
     * covered it.
     */
    private List<Pot> buildPots() {
        long[] left = new long[seats.length];
        for (int i = 0; i < seats.length; i++) {
            left[i] = seats[i] == null ? 0 : seats[i].contributed;
        }
        List<Pot> pots = new ArrayList<>();
        while (true) {
            long level = Long.MAX_VALUE;
            for (int i = 0; i < seats.length; i++) {
                if (left[i] > 0 && seats[i].live()) {
                    level = Math.min(level, left[i]);
                }
            }
            if (level == Long.MAX_VALUE) {
                break;
            }
            long amount = 0;
            Set<Integer> eligible = new LinkedHashSet<>();
            for (int i = 0; i < seats.length; i++) {
                if (left[i] <= 0) {
                    continue;
                }
                if (seats[i].live()) {
                    eligible.add(i);
                }
                long taken = Math.min(left[i], level);
                amount += taken;
                left[i] -= taken;
            }
            if (!pots.isEmpty() && pots.get(pots.size() - 1).eligible.equals(eligible)) {
                Pot last = pots.remove(pots.size() - 1);
                pots.add(new Pot(last.amount + amount, eligible));
            } else {
                pots.add(new Pot(amount, eligible));
            }
        }
        // Chips of folded players above every live stake: nobody covered them, they join
        // the last pot (it only happens after the uncalled part was given back).
        long stray = 0;
        for (long chips : left) {
            stray += Math.max(0, chips);
        }
        if (stray > 0 && !pots.isEmpty()) {
            Pot last = pots.remove(pots.size() - 1);
            pots.add(new Pot(last.amount + stray, last.eligible));
        }
        return pots;
    }

    /**
     * Gives back the part of the biggest bet nobody else matched.
     */
    private void returnUncalled() {
        int top = -1;
        long topContribution = -1;
        long second = 0;
        for (int i = 0; i < seats.length; i++) {
            Seat seat = seats[i];
            if (seat == null || !seat.inHand) {
                continue;
            }
            if (seat.contributed > topContribution) {
                if (top >= 0) {
                    second = Math.max(second, topContribution);
                }
                top = i;
                topContribution = seat.contributed;
            } else {
                second = Math.max(second, seat.contributed);
            }
        }
        if (top < 0 || topContribution <= second) {
            return;
        }
        long back = topContribution - second;
        Seat seat = seats[top];
        seat.contributed -= back;
        seat.bet = Math.max(0, seat.bet - back);
        seat.stack += back;
        if (seat.stack > 0) {
            seat.allIn = false;
        }
    }

    private void clearContributions() {
        for (Seat seat : seats) {
            if (seat != null) {
                seat.contributed = 0;
                seat.bet = 0;
            }
        }
    }

    /**
     * Chips split as evenly as possible; the first winners get the odd chips.
     */
    static List<Long> split(long amount, int ways) {
        List<Long> shares = new ArrayList<>(ways);
        if (ways <= 0) {
            return shares;
        }
        long base = amount / ways;
        long odd = amount % ways;
        for (int i = 0; i < ways; i++) {
            shares.add(base + (i < odd ? 1 : 0));
        }
        return shares;
    }

    /**
     * Winners ordered from the first seat left of the button.
     */
    private List<Integer> fromButton(List<Integer> winners) {
        List<Integer> ordered = new ArrayList<>(winners);
        ordered.sort((a, b) -> Integer.compare(distanceFromButton(a), distanceFromButton(b)));
        return ordered;
    }

    private int distanceFromButton(int index) {
        int distance = Math.floorMod(index - button, seats.length);
        return distance == 0 ? seats.length : distance;
    }

    // -------------------------------------------------------------- helpers

    private void put(Seat seat, long chips) {
        long amount = Math.max(0, Math.min(chips, seat.stack));
        seat.stack -= amount;
        seat.bet += amount;
        seat.contributed += amount;
        if (seat.stack == 0) {
            seat.allIn = true;
        }
    }

    private int dealtCount() {
        int count = 0;
        for (Seat seat : seats) {
            if (seat != null && seat.inHand) {
                count++;
            }
        }
        return count;
    }

    /** Next seat after {@code from} dealt in this hand (or able to be). */
    private int nextDealt(int from) {
        for (int step = 1; step <= seats.length; step++) {
            int index = Math.floorMod(from + step, seats.length);
            if (seats[index] != null && seats[index].inHand) {
                return index;
            }
        }
        return -1;
    }

    /** Next seat after {@code from} that still has a decision to take. */
    private int nextActor(int from) {
        for (int step = 1; step <= seats.length; step++) {
            int index = Math.floorMod(from + step, seats.length);
            Seat seat = seats[index];
            if (seat != null && seat.canAct() && (!seat.acted || seat.bet < currentBet)) {
                return index;
            }
        }
        return -1;
    }

    private int actorsLeft() {
        int count = 0;
        for (Seat seat : seats) {
            if (seat != null && seat.canAct()) {
                count++;
            }
        }
        return count;
    }

    private boolean othersCanAct(int index) {
        for (int i = 0; i < seats.length; i++) {
            if (i != index && seats[i] != null && seats[i].canAct()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The round is over when every player who can still act has acted and matched the
     * highest bet; with a single player able to act, as soon as they matched it.
     */
    private boolean roundComplete() {
        int actors = 0;
        for (Seat seat : seats) {
            if (seat == null || !seat.canAct()) {
                continue;
            }
            actors++;
            if (seat.bet < currentBet) {
                return false;
            }
            if (!seat.acted) {
                // A lone player facing nothing has no decision left.
                if (actorsLeft() > 1) {
                    return false;
                }
            }
        }
        return actors <= 1 || allActed();
    }

    private boolean allActed() {
        for (Seat seat : seats) {
            if (seat != null && seat.canAct() && !seat.acted) {
                return false;
            }
        }
        return true;
    }
}
