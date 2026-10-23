/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.video;

import com.grapeshot.halfnes.video.Renderer;
import java.awt.image.BufferedImage;

public class AltNTSCRenderer
extends Renderer {
    private final int[] frame = new int[144960];
    private int frame_ptr = 0;
    private int frame_ctr;
    private int phase;
    private static final double attenuation = 0.746;
    private static final double[] levels = new double[]{-0.117f, 0.0, 0.308f, 0.715f, 0.397f, 0.681f, 1.0, 1.0};
    private static final int SAMPLESPERPIXEL = 8;
    private final double[] signal_levels = new double[2048];
    private int ntsc_buf_ptr = 0;
    private static final int width = 604;

    public AltNTSCRenderer() {
        this.frame_width = 604;
        this.init_images();
    }

    @Override
    public BufferedImage render(int[] nArray, int[] nArray2, boolean bl) {
        ++this.frame_ctr;
        this.phase = (this.frame_ctr & 1) == 0 ? 0 : 6;
        for (int i = 0; i < 240; ++i) {
            double d = ((double)this.phase + 3.9 - 0.3) % 12.0;
            for (int j = 0; j < 256; ++j) {
                this.ntsc_render(nArray[i * 256 + j]);
            }
            this.ntsc_decode(d);
            this.ntsc_buf_ptr = 0;
        }
        this.frame_ptr = 0;
        return this.getBufferedImage(this.frame);
    }

    private void ntsc_render(int n) {
        int n2 = n & 0xF;
        int n3 = n >> 4 & 3;
        int n4 = n >> 6;
        if (n2 > 13) {
            n3 = 1;
        }
        double d = levels[n3];
        double d2 = levels[4 + n3];
        if (n2 == 0) {
            d = d2;
        } else if (n2 > 12) {
            d2 = d;
        }
        int n5 = 0;
        while (n5 < 8) {
            double d3;
            double d4 = d3 = AltNTSCRenderer.inColorPhase(n2, this.phase) ? d2 : d;
            if (n4 != 0 && ((n4 & 1) != 0 && AltNTSCRenderer.inColorPhase(0, this.phase) || (n4 & 2) != 0 && AltNTSCRenderer.inColorPhase(4, this.phase) || (n4 & 4) != 0 && AltNTSCRenderer.inColorPhase(8, this.phase))) {
                d3 *= 0.746;
            }
            this.signal_levels[this.ntsc_buf_ptr++] = d3;
            ++n5;
            ++this.phase;
        }
    }

    private static boolean inColorPhase(int n, int n2) {
        return (n + n2) % 12 < 6;
    }

    private void ntsc_decode(double d) {
        for (int i = 0; i < 604; ++i) {
            int n;
            int n2 = i * 2048 / 604 + 0;
            int n3 = n2 - 6;
            if (n3 < 0) {
                n3 = 0;
            }
            if ((n = n2 + 6) > 2048) {
                n = 2048;
            }
            double d2 = 0.0;
            double d3 = 0.0;
            double d4 = 0.0;
            for (int j = n3; j < n; ++j) {
                double d5 = this.signal_levels[j] / 12.0;
                d2 += d5;
                d3 += d5 * Math.cos(Math.PI * (d + (double)j) / 6.0);
                d4 += d5 * Math.sin(Math.PI * (d + (double)j) / 6.0);
            }
            this.render_pixel(d2, d3, d4);
        }
    }

    private void render_pixel(double d, double d2, double d3) {
        int n = 0xFF000000 | 65536 * AltNTSCRenderer.clamp(255.95 * AltNTSCRenderer.gammafix(d + (double)0.946882f * d2 + (double)0.623557f * d3)) + 256 * AltNTSCRenderer.clamp(255.95 * AltNTSCRenderer.gammafix(d + (double)-0.274788f * d2 + (double)-0.635691f * d3)) + 1 * AltNTSCRenderer.clamp(255.95 * AltNTSCRenderer.gammafix(d + -1.108544945716858 * d2 + (double)1.709007f * d3));
        this.frame[this.frame_ptr++] = n;
    }

    public static int clamp(double d) {
        return (int)(d < 0.0 ? 0.0 : (d > 255.0 ? 255.0 : d));
    }

    public static double gammafix(double d) {
        return d <= 0.0 ? 0.0 : Math.pow(d, 1.1f);
    }
}

