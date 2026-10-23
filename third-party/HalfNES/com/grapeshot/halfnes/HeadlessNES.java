/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes;

import com.grapeshot.halfnes.ui.HeadlessUI;
import com.grapeshot.halfnes.ui.PuppetController;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;

public class HeadlessNES {
    public static final int scale = 4;

    private HeadlessNES() {
    }

    public static void main(String[] stringArray) {
        int n;
        BufferedImage bufferedImage = new BufferedImage(100, 100, 3);
        HeadlessUI headlessUI = new HeadlessUI("src/test/resources/nestest/nestest.nes", true);
        for (n = 0; n < 100; ++n) {
            headlessUI.runFrame();
        }
        headlessUI.getController1().pressButton(PuppetController.Button.START);
        headlessUI.runFrame();
        headlessUI.getController1().releaseButton(PuppetController.Button.START);
        for (n = 0; n < 5; ++n) {
            headlessUI.runFrame();
        }
        final BufferedImage bufferedImage2 = headlessUI.getLastFrame();
        JFrame jFrame = new JFrame("Display"){

            @Override
            public void paint(Graphics graphics) {
                super.paint(graphics);
                graphics.drawImage(bufferedImage2, 0, 0, bufferedImage2.getWidth() * 4, bufferedImage2.getHeight() * 4, this);
            }
        };
        jFrame.setSize(1024, 896);
        jFrame.setVisible(true);
        jFrame.setDefaultCloseOperation(3);
    }
}

