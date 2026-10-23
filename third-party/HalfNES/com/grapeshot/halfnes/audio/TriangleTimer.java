/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.audio;

import com.grapeshot.halfnes.audio.Timer;

public class TriangleTimer
extends Timer {
    private int divider = 0;
    private static final int periodadd = 1;
    private static final int[] triangle = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0};

    public TriangleTimer() {
        this.period = 0;
        this.position = 0;
    }

    @Override
    public final void reset() {
    }

    @Override
    public final void clock() {
        if (this.period == 0) {
            return;
        }
        ++this.divider;
        int n = (this.divider + this.period + 1) / (this.period + 1);
        if (n < 0) {
            n = 0;
        }
        this.position = this.position + n & 0x1F;
        this.divider -= (this.period + 1) * n;
    }

    @Override
    public final void clock(int n) {
        if (this.period == 0) {
            return;
        }
        this.divider += n;
        int n2 = (this.divider + this.period + 1) / (this.period + 1);
        if (n2 < 0) {
            n2 = 0;
        }
        this.position = this.position + n2 & 0x1F;
        this.divider -= (this.period + 1) * n2;
    }

    @Override
    public final int getval() {
        return this.period == 0 ? 7 : triangle[this.position];
    }

    @Override
    public final void setperiod(int n) {
        this.period = n;
        if (this.period == 0) {
            this.position = 7;
        }
    }

    @Override
    public void setduty(int n) {
        throw new UnsupportedOperationException("Triangle counter has no duty setting.");
    }

    @Override
    public void setduty(int[] nArray) {
        throw new UnsupportedOperationException("Triangle counter has no duty setting.");
    }
}

