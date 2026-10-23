/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.ui;

import com.grapeshot.halfnes.NES;

public interface GUIInterface
extends Runnable {
    public NES getNes();

    public void setNES(NES var1);

    public void setFrame(int[] var1, int[] var2, boolean var3);

    public void messageBox(String var1);

    @Override
    public void run();

    public void render();

    public void loadROMs(String var1);
}

