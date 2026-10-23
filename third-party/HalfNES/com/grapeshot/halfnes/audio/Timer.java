/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.audio;

public abstract class Timer {
    protected int period;
    protected int position;

    public final int getperiod() {
        return this.period;
    }

    public abstract void setperiod(int var1);

    public abstract void setduty(int var1);

    public abstract void setduty(int[] var1);

    public abstract void reset();

    public abstract void clock();

    public abstract void clock(int var1);

    public abstract int getval();
}

