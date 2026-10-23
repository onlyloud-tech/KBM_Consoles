/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.ui;

import com.grapeshot.halfnes.CPURAM;
import com.grapeshot.halfnes.NES;
import com.grapeshot.halfnes.ui.GUIInterface;
import com.grapeshot.halfnes.ui.PuppetController;
import com.grapeshot.halfnes.video.RGBRenderer;
import com.grapeshot.halfnes.video.Renderer;
import java.awt.image.BufferedImage;

public class HeadlessUI
implements GUIInterface {
    private NES nes = new NES(this);
    private Renderer renderer;
    private boolean renderFrames;
    private BufferedImage lastFrame = null;
    private boolean updateImage;
    private PuppetController controller1;
    private PuppetController controller2;

    public HeadlessUI(String string, boolean bl) {
        this.loadROM(string);
        this.renderer = new RGBRenderer();
        this.controller1 = new PuppetController();
        this.controller2 = new PuppetController();
        this.nes.setControllers(this.controller1, this.controller2);
        this.renderFrames = bl;
    }

    public void loadROM(String string) {
        this.nes.loadROM(string);
    }

    public BufferedImage getLastFrame() {
        return this.lastFrame;
    }

    public PuppetController getController1() {
        return this.controller1;
    }

    public PuppetController getController2() {
        return this.controller2;
    }

    public synchronized void runFrame() {
        this.nes.frameAdvance();
    }

    public CPURAM getNESCPURAM() {
        return this.nes.getCPURAM();
    }

    @Override
    public NES getNes() {
        return this.nes;
    }

    @Override
    public void setNES(NES nES) {
        this.nes = nES;
    }

    @Override
    public void setFrame(int[] nArray, int[] nArray2, boolean bl) {
        if (this.renderFrames) {
            this.lastFrame = this.renderer.render(nArray, nArray2, bl);
        }
    }

    @Override
    public void messageBox(String string) {
        System.err.println(string);
    }

    @Override
    public void run() {
    }

    @Override
    public void render() {
    }

    @Override
    public void loadROMs(String string) {
    }
}

