/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.audio;

import com.grapeshot.halfnes.audio.AudioOutInterface;
import com.grapeshot.halfnes.audio.CircularBuffer;

public class Reverberator
implements AudioOutInterface {
    AudioOutInterface iface;
    CircularBuffer cb;
    double echo;
    double lp_coef;
    double hp_coef;
    int lpaccum = 0;
    private int dckiller = 0;

    public Reverberator(AudioOutInterface audioOutInterface, int n, double d, double d2, double d3) {
        this.echo = d;
        this.lp_coef = d2;
        this.hp_coef = d3;
        this.iface = audioOutInterface;
        this.cb = new CircularBuffer(n);
    }

    private int lowpass_filter(int n) {
        this.lpaccum = (int)((double)this.lpaccum - (double)(n += this.lpaccum) * this.lp_coef);
        return this.lpaccum;
    }

    private int highpass_filter(int n) {
        this.dckiller = (int)((double)this.dckiller - (double)(n += this.dckiller) * this.hp_coef);
        this.dckiller += n > 0 ? -1 : 1;
        return n;
    }

    @Override
    public void outputSample(int n) {
        if ((n = (int)((double)n - (double)this.cb.read() * this.echo)) < Short.MIN_VALUE) {
            n = Short.MIN_VALUE;
        }
        if (n > Short.MAX_VALUE) {
            n = Short.MAX_VALUE;
        }
        this.cb.write(this.lowpass_filter(this.highpass_filter(n)));
        if (this.iface != null) {
            this.iface.outputSample(n);
        }
    }

    @Override
    public void flushFrame(boolean bl) {
        if (this.iface != null) {
            this.iface.flushFrame(bl);
        }
    }

    @Override
    public void pause() {
        if (this.iface != null) {
            this.iface.pause();
        }
    }

    @Override
    public void resume() {
        if (this.iface != null) {
            this.iface.resume();
        }
    }

    @Override
    public void destroy() {
        if (this.iface != null) {
            this.iface.destroy();
        }
    }

    @Override
    public boolean bufferHasLessThan(int n) {
        if (this.iface != null) {
            return this.iface.bufferHasLessThan(n);
        }
        return false;
    }
}

