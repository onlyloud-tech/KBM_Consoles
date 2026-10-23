/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.video;

import com.grapeshot.halfnes.video.NesColors;
import com.grapeshot.halfnes.video.Renderer;
import java.awt.image.BufferedImage;

public class RGBRenderer
extends Renderer {
    public RGBRenderer() {
        this.frame_width = 256;
        this.init_images();
    }

    @Override
    public BufferedImage render(int[] nArray, int[] nArray2, boolean bl) {
        for (int i = 0; i < nArray.length; ++i) {
            nArray[i] = NesColors.col[(nArray[i] & 0x1C0) >> 6][nArray[i] & 0x3F];
        }
        return this.getBufferedImage(nArray);
    }
}

