/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.audio;

import com.grapeshot.halfnes.audio.Timer;

public class NoiseTimer
extends Timer {
    private int divider = 0;
    private int[] values = NoiseTimer.genvalues(1, 1);
    private int prevduty = 1;
    private static final int periodadd = 0;

    public NoiseTimer() {
        this.period = 0;
    }

    @Override
    public void setduty(int n) {
        if (n != this.prevduty) {
            this.values = NoiseTimer.genvalues(n, this.values[this.position]);
            this.position = 0;
        }
        this.prevduty = n;
    }

    @Override
    public final void clock() {
        ++this.divider;
        int n = (this.divider + this.period + 0) / (this.period + 0);
        if (n < 0) {
            n = 0;
        }
        this.position = (this.position + n) % this.values.length;
        this.divider -= (this.period + 0) * n;
    }

    @Override
    public final int getval() {
        return this.values[this.position] & 1;
    }

    @Override
    public final void reset() {
        this.position = 0;
    }

    @Override
    public final void clock(int n) {
        this.divider += n;
        int n2 = (this.divider + this.period + 0) / (this.period + 0);
        if (n2 < 0) {
            n2 = 0;
        }
        this.position = (this.position + n2) % this.values.length;
        this.divider -= (this.period + 0) * n2;
    }

    @Override
    public final void setperiod(int n) {
        this.period = n;
    }

    public static int[] genvalues(int n, int n2) {
        int[] nArray = new int[n == 1 ? Short.MAX_VALUE : 93];
        for (int i = 0; i < nArray.length; ++i) {
            nArray[i] = n2 = n2 >> 1 | ((n2 & 1 << n) != 0 ^ (n2 & 1) != 0 ? 16384 : 0);
        }
        return nArray;
    }

    @Override
    public void setduty(int[] nArray) {
        throw new UnsupportedOperationException("Not supported on noise channel.");
    }
}

