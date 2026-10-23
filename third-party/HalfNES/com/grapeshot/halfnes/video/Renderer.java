/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.video;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.WritableRaster;

public abstract class Renderer {
    int frame_width;
    int clip = 8;
    int height = 240 - 2 * this.clip;
    BufferedImage[] imgs = new BufferedImage[]{null, null, null, null};
    int imgctr = 0;

    protected final void init_images() {
        for (int i = 0; i < this.imgs.length; ++i) {
            this.imgs[i] = new BufferedImage(this.frame_width, this.height, 3);
        }
    }

    public abstract BufferedImage render(int[] var1, int[] var2, boolean var3);

    public void setClip(int n) {
        this.clip = n;
        this.height = 240 - 2 * this.clip;
    }

    public BufferedImage getBufferedImage(int[] nArray) {
        BufferedImage bufferedImage = this.imgs[++this.imgctr % this.imgs.length];
        WritableRaster writableRaster = bufferedImage.getRaster();
        int[] nArray2 = ((DataBufferInt)writableRaster.getDataBuffer()).getData();
        System.arraycopy(nArray, this.frame_width * this.clip, nArray2, 0, this.frame_width * this.height);
        return bufferedImage;
    }
}

