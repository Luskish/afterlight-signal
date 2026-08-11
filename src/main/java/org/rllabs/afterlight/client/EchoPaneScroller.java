package org.rllabs.afterlight.client;

import java.util.List;
import java.util.Objects;

final class EchoPaneScroller {
    private int offset;
    private int totalLines;
    private int capacity;

    <T> List<T> window(List<T> lines, int visibleCapacity) {
        Objects.requireNonNull(lines);
        this.totalLines = lines.size();
        this.capacity = Math.max(0, visibleCapacity);
        this.offset = Math.min(this.offset, maximumOffset());
        if (this.capacity == 0 || lines.isEmpty()) {
            return List.of();
        }
        int end = Math.min(lines.size(), this.offset + this.capacity);
        return List.copyOf(lines.subList(this.offset, end));
    }

    boolean scroll(double verticalAmount) {
        if (verticalAmount == 0.0D) {
            return false;
        }
        int nextOffset = verticalAmount < 0.0D ? this.offset + 1 : this.offset - 1;
        nextOffset = Math.max(0, Math.min(maximumOffset(), nextOffset));
        if (nextOffset == this.offset) {
            return false;
        }
        this.offset = nextOffset;
        return true;
    }

    void reset() {
        this.offset = 0;
    }

    int offset() {
        return this.offset;
    }

    int totalLines() {
        return this.totalLines;
    }

    int capacity() {
        return this.capacity;
    }

    int maximumOffset() {
        return Math.max(0, this.totalLines - this.capacity);
    }
}
