/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.ui;

import com.grapeshot.halfnes.audio.AudioOutInterface;

public class Oscilloscope
implements AudioOutInterface {
    private final AudioOutInterface delegate;

    public Oscilloscope(AudioOutInterface audioOutInterface) {
        this.delegate = audioOutInterface;
    }

    @Override
    public void outputSample(int n) {
        this.delegate.outputSample(n);
    }

    @Override
    public void flushFrame(boolean bl) {
        this.delegate.flushFrame(bl);
    }

    @Override
    public void pause() {
        this.delegate.pause();
    }

    @Override
    public void resume() {
        this.delegate.resume();
    }

    @Override
    public void destroy() {
        this.delegate.destroy();
    }

    @Override
    public boolean bufferHasLessThan(int n) {
        return this.delegate.bufferHasLessThan(n);
    }
}

