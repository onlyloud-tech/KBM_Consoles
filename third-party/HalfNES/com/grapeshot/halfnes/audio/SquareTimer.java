/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.audio;

import com.grapeshot.halfnes.audio.Timer;

public class SquareTimer
extends Timer {
    protected int[] values;
    private final int periodadd;
    private int divider = 0;

    @Override
    public final void clock() {
        if (this.period + this.periodadd <= 0) {
            return;
        }
        ++this.divider;
        int n = (this.divider + this.period + this.periodadd) / (this.period + this.periodadd);
        if (n < 0) {
            n = 0;
        }
        this.position = (this.position + n) % this.values.length;
        this.divider -= (this.period + this.periodadd) * n;
    }

    @Override
    public final void clock(int n) {
        if (this.period < 8) {
            return;
        }
        this.divider += n;
        int n2 = (this.divider + this.period + this.periodadd) / (this.period + this.periodadd);
        if (n2 < 0) {
            n2 = 0;
        }
        this.position = (this.position + n2) % this.values.length;
        this.divider -= (this.period + this.periodadd) * n2;
    }

    public SquareTimer(int n, int n2) {
        this.periodadd = n2;
        this.values = new int[n];
        this.period = 0;
        this.position = 0;
        this.setduty(n / 2);
    }

    public SquareTimer(int n) {
        this.periodadd = 0;
        this.values = new int[n];
        this.period = 0;
        this.position = 0;
        this.setduty(n / 2);
    }

    @Override
    public final void reset() {
        this.position = 0;
    }

    @Override
    public final void setduty(int n) {
        for (int i = 0; i < this.values.length; ++i) {
            this.values[i] = i < n ? 1 : 0;
        }
    }

    @Override
    public final void setduty(int[] nArray) {
        this.values = nArray;
    }

    @Override
    public final int getval() {
        return this.values[this.position];
    }

    @Override
    public final void setperiod(int n) {
        this.period = n;
    }
}

