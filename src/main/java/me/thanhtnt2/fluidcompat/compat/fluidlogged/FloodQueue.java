package me.thanhtnt2.fluidcompat.compat.fluidlogged;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * The positions of the listed blocks that one level watches, handed out a few at a time in a rotating order.
 *
 * <p>Checking every listed block on every pass would cost time in proportion to how common the block is, so a pass
 * takes at most a fixed number of positions and the next pass carries on where it stopped. Nothing here touches
 * Minecraft, so the rotation can be tested alone. All methods are synchronised: chunk events and the level tick are
 * separate entry points and the cost of a lock is nothing next to a block lookup.
 */
public final class FloodQueue {

    private final List<Long> positions = new ArrayList<>();
    private final Set<Long> members = new HashSet<>();
    private int cursor;

    public synchronized boolean add(long position) {
        if (!members.add(position)) {
            return false;
        }
        positions.add(position);
        return true;
    }

    public synchronized void remove(long position) {
        if (members.remove(position)) {
            positions.remove(Long.valueOf(position));
            clampCursor();
        }
    }

    public synchronized void removeIf(LongPredicate predicate) {
        positions.removeIf(position -> {
            boolean gone = predicate.test(position);
            if (gone) {
                members.remove(position);
            }
            return gone;
        });
        clampCursor();
    }

    public synchronized int size() {
        return positions.size();
    }

    public synchronized void clear() {
        positions.clear();
        members.clear();
        cursor = 0;
    }

    /** @return up to {@code max} positions, continuing after the ones the previous call returned */
    public synchronized long[] nextBatch(int max) {
        int count = Math.min(max, positions.size());
        long[] batch = new long[count];
        for (int index = 0; index < count; index++) {
            batch[index] = positions.get(cursor);
            cursor = (cursor + 1) % positions.size();
        }
        return batch;
    }

    private void clampCursor() {
        cursor = positions.isEmpty() ? 0 : cursor % positions.size();
    }
}
