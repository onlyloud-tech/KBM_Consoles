/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.audio;

public interface AudioOutInterface {
    public void outputSample(int var1);

    public void flushFrame(boolean var1);

    public void pause();

    public void resume();

    public void destroy();

    public boolean bufferHasLessThan(int var1);
}

