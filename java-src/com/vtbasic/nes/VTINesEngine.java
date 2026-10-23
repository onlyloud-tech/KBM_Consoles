// Copyright (c) 2026 Grymlock.
// SPDX-License-Identifier: GPL-3.0-only
/*
 * Decompiled with CFR 0.152.
 */
package com.vtbasic.nes;

import com.grapeshot.halfnes.ui.HeadlessUI;
import com.grapeshot.halfnes.ui.PuppetController;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

public class VTINesEngine {
    public static final int SCREEN_WIDTH = 256;
    public static final int SCREEN_HEIGHT = 240;
    public static final int BTN_A = 1;
    public static final int BTN_B = 2;
    public static final int BTN_SELECT = 4;
    public static final int BTN_START = 8;
    public static final int BTN_UP = 16;
    public static final int BTN_DOWN = 32;
    public static final int BTN_LEFT = 64;
    public static final int BTN_RIGHT = 128;
    private HeadlessUI headless;
    private int[] pixelBuffer = new int[61440];
    private boolean romLoaded = false;
    private String romPath = null;
    private int pad1Mask = 0;
    private int pad2Mask = 0;

    public VTINesEngine() {
        this.fillTestPattern();
    }

    public boolean loadROM(String string) {
        if (string == null || string.isEmpty()) {
            return false;
        }
        try {
            if (this.headless == null) {
                this.headless = new HeadlessUI(string, true);
            } else {
                this.headless.loadROM(string);
            }
            this.headless.runFrame();
            this.romLoaded = true;
            this.romPath = string;
            return true;
        }
        catch (Throwable throwable) {
            this.romLoaded = false;
            System.err.println("[VTINesEngine] loadROM failed: " + String.valueOf(throwable));
            throwable.printStackTrace();
            return false;
        }
    }

    public boolean isRomLoaded() {
        return this.romLoaded;
    }

    public String getRomPath() {
        return this.romPath;
    }

    public void runFrame() {
        if (this.headless == null || !this.romLoaded) {
            return;
        }
        try {
            this.applyControllerMasks();
            this.headless.runFrame();
        }
        catch (Throwable throwable) {
            System.err.println("[VTINesEngine] runFrame failed: " + String.valueOf(throwable));
        }
    }

    public int[] getPixels() {
        if (this.headless == null || !this.romLoaded) {
            return this.pixelBuffer;
        }
        BufferedImage bufferedImage = this.headless.getLastFrame();
        if (bufferedImage == null) {
            return this.pixelBuffer;
        }
        int n = bufferedImage.getWidth();
        int n2 = bufferedImage.getHeight();
        if (n == 256 && n2 == 240) {
            try {
                int[] nArray = ((DataBufferInt)bufferedImage.getRaster().getDataBuffer()).getData();
                System.arraycopy(nArray, 0, this.pixelBuffer, 0, Math.min(nArray.length, this.pixelBuffer.length));
                return this.pixelBuffer;
            }
            catch (ClassCastException classCastException) {
                // empty catch block
            }
        }
        int n3 = Math.min(n, 256);
        int n4 = Math.min(n2, 240);
        int n5 = (240 - n4) / 2;
        int n6 = (256 - n3) / 2;
        for (int i = 0; i < n4; ++i) {
            bufferedImage.getRGB(0, i, n3, 1, this.pixelBuffer, (n5 + i) * 256 + n6, 256);
        }
        return this.pixelBuffer;
    }

    public void setPadMask(int n, int n2) {
        if (n == 1) {
            this.pad1Mask = n2;
        } else if (n == 2) {
            this.pad2Mask = n2;
        }
    }

    public void setButton(int n, int n2, boolean bl) {
        int n3;
        int n4 = n3 = n == 2 ? this.pad2Mask : this.pad1Mask;
        n3 = bl ? (n3 |= n2) : (n3 &= ~n2);
        if (n == 2) {
            this.pad2Mask = n3;
        } else {
            this.pad1Mask = n3;
        }
    }

    public void reset() {
        if (this.headless != null && this.romLoaded) {
            try {
                this.headless.getNes().reset();
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }

    public void pauseAudio() {
        if (this.headless == null) {
            return;
        }
        try {
            this.headless.getNes().pause();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public void destroy() {
        if (this.headless != null) {
            try {
                this.headless.getNes().quit();
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        this.headless = null;
        this.romLoaded = false;
        this.romPath = null;
    }

    private void applyControllerMasks() {
        if (this.headless == null) {
            return;
        }
        VTINesEngine.applyOnePad(this.headless.getController1(), this.pad1Mask);
        VTINesEngine.applyOnePad(this.headless.getController2(), this.pad2Mask);
    }

    private static void applyOnePad(PuppetController puppetController, int n) {
        if (puppetController == null) {
            return;
        }
        VTINesEngine.setBtn(puppetController, PuppetController.Button.A, (n & 1) != 0);
        VTINesEngine.setBtn(puppetController, PuppetController.Button.B, (n & 2) != 0);
        VTINesEngine.setBtn(puppetController, PuppetController.Button.SELECT, (n & 4) != 0);
        VTINesEngine.setBtn(puppetController, PuppetController.Button.START, (n & 8) != 0);
        VTINesEngine.setBtn(puppetController, PuppetController.Button.UP, (n & 0x10) != 0);
        VTINesEngine.setBtn(puppetController, PuppetController.Button.DOWN, (n & 0x20) != 0);
        VTINesEngine.setBtn(puppetController, PuppetController.Button.LEFT, (n & 0x40) != 0);
        VTINesEngine.setBtn(puppetController, PuppetController.Button.RIGHT, (n & 0x80) != 0);
    }

    private static void setBtn(PuppetController puppetController, PuppetController.Button button, boolean bl) {
        if (bl) {
            puppetController.pressButton(button);
        } else {
            puppetController.releaseButton(button);
        }
    }

    private void fillTestPattern() {
        for (int i = 0; i < 240; ++i) {
            for (int j = 0; j < 256; ++j) {
                int n = j < 2 || i < 2 || j >= 254 || i >= 238 ? -49088 : ((j >> 4 ^ i >> 4) == 0 ? -14655456 : -12566464);
                this.pixelBuffer[i * 256 + j] = n;
            }
        }
    }
}
