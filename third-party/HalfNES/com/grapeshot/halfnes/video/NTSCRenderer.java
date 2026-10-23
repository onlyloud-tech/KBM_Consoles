/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.video;

import com.grapeshot.halfnes.video.Renderer;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.zip.CRC32;

public class NTSCRenderer
extends Renderer {
    private static final List<Integer> lines = new ArrayList<Integer>();
    private static final int[][] colorphases;
    private static final float[][][] lumas;
    private static final int[][] coloremph;
    private int frames = 0;
    private final float[] i_filter = new float[12];
    private final float[] q_filter = new float[12];
    private static final int[] colortbl;
    public static final float chroma_filterfreq = 3579000.0f;
    public static final float pixel_rate = 4.295E7f;
    private static final int[] cbstphase;
    private static final int[][] iqm;
    public static final int frame_w = 2112;
    int[] frame = new int[506880];
    Map<Long, int[]> cache = Collections.synchronizedMap(new WeakHashMap(600));

    public NTSCRenderer() {
        this.frame_width = 2112;
        this.init_images();
        int n = -512;
        double d = 1.6973125884016973;
        for (int i = 0; i < 12; ++i) {
            float f = (float)(Math.PI * ((double)(n + (i << 8)) / 1536.0 - 0.18333333333333332));
            this.i_filter[i] = (float)(-d * Math.cos(f));
            this.q_filter[i] = (float)(d * Math.sin(f));
        }
    }

    public static int[] genColorCorrectTbl() {
        int[] nArray = new int[256];
        float f = 20.0f;
        float f2 = 1.0f;
        for (int i = 0; i < 256; ++i) {
            float f3 = ((float)i * f2 - 128.0f * f2 + 128.0f + f) / 255.0f;
            nArray[i] = NTSCRenderer.clamp((int)(255.0 * Math.pow(f3, 1.3)));
        }
        return nArray;
    }

    public static float[][][] genlumas() {
        float[][] fArrayArray = new float[][]{{-0.117f, 0.0f, 0.308f, 0.715f}, {0.397f, 0.681f, 1.0f, 1.0f}};
        float[][][] fArray = new float[fArrayArray.length][fArrayArray[0].length][2];
        for (int i = 0; i < fArrayArray.length; ++i) {
            for (int j = 0; j < fArrayArray[i].length; ++j) {
                fArray[i][j][0] = fArrayArray[i][j];
                fArray[i][j][1] = fArrayArray[i][j] * 0.735f;
            }
        }
        return fArray;
    }

    public final float[] ntsc_encode(int[] nArray, int n, int n2, int n3) {
        int n4;
        int n5;
        int n6;
        int n7 = n3 & 0xF;
        int n8 = n3 >> 4 & 3;
        int n9 = n3 >> 6;
        float[] fArray = new float[2488];
        for (n6 = 160; n6 < 280; ++n6) {
            n5 = (n6 + n) % 12;
            n4 = colorphases[n7][n5];
            fArray[n6] = lumas[n4][n8][coloremph[n9][n5]];
        }
        for (n6 = 2328; n6 < 2416; ++n6) {
            n5 = (n6 + n) % 12;
            n4 = colorphases[n7][n5];
            fArray[n6] = lumas[n4][n8][coloremph[n9][n5]];
        }
        for (n6 = 280; n6 < 2328; ++n6) {
            if ((n6 & 7) == 0) {
                n7 = nArray[n6 - 280 >> 3];
                if ((n7 & 0xF) > 13) {
                    n7 = 15;
                }
                n8 = n7 >> 4 & 3;
                n9 = n7 >> 6;
                n7 &= 0xF;
            }
            n5 = (n6 + n) % 12;
            n4 = colorphases[n7][n5];
            fArray[n6] = lumas[n4][n8][coloremph[n9][n5]];
        }
        fArray[2487] = n;
        return fArray;
    }

    public final int[] ntsc_decode(float[] fArray, int n) {
        float[] fArray2 = new float[2416];
        float[] fArray3 = new float[2416];
        float[] fArray4 = new float[2416];
        float[] fArray5 = new float[2416];
        int[] nArray = new int[2112];
        NTSCRenderer.box_filter(fArray, fArray3, fArray2, 12);
        int n2 = cbstphase[n];
        int n3 = 0;
        while (n2 < 2366) {
            fArray4[n2] = this.i_filter[n3] * fArray2[n2 + 12];
            fArray5[n2] = this.q_filter[n3] * fArray2[n2 + 12];
            ++n2;
            ++n3;
            n3 %= 12;
        }
        NTSCRenderer.lowpass_filter(fArray4, 0.06f);
        NTSCRenderer.lowpass_filter(fArray5, 0.05f);
        n2 = 0;
        n3 = 252;
        while (n2 < 2112) {
            nArray[n2] = NTSCRenderer.compose_col(fArray3[n3] <= 0.0f ? 0 : colortbl[NTSCRenderer.clamp((int)((float)iqm[0][0] * fArray3[n3] + (float)iqm[0][1] * fArray4[n3] + (float)iqm[0][2] * fArray5[n3]))], fArray3[n3] <= 0.0f ? 0 : colortbl[NTSCRenderer.clamp((int)((float)iqm[1][0] * fArray3[n3] + (float)iqm[1][1] * fArray4[n3] + (float)iqm[1][2] * fArray5[n3]))], fArray3[n3] <= 0.0f ? 0 : colortbl[NTSCRenderer.clamp((int)((float)iqm[2][0] * fArray3[n3] + (float)iqm[2][1] * fArray4[n3] + (float)iqm[2][2] * fArray5[n3]))]);
            ++n2;
            ++n3;
        }
        return nArray;
    }

    public static void box_filter(float[] fArray, float[] fArray2, float[] fArray3, int n) {
        float f = 0.0f;
        for (int i = 12; i < 2416; ++i) {
            fArray2[i] = (f += fArray[i] - fArray[i - n]) / (float)n;
            fArray3[i] = fArray[i] - fArray2[i];
        }
    }

    public static void lowpass_filter(float[] fArray, float f) {
        float f2 = 0.0f;
        for (int i = 0; i < 2416; ++i) {
            int n = i;
            fArray[n] = fArray[n] - f2;
            fArray[i] = f2 += fArray[i] * f;
        }
    }

    private static int compose_col(int n, int n2, int n3) {
        return n << 16 | n2 << 8 | n3 | 0xFF000000;
    }

    public static int clamp(int n) {
        return n != (n & 0xFF) ? (n < 0 ? 0 : 255) : n;
    }

    @Override
    public BufferedImage render(int[] nArray, int[] nArray2, boolean bl) {
        lines.parallelStream().forEach(n -> this.cacheRender(nArray, (int)n, nArray2, bl));
        BufferedImage bufferedImage = this.getBufferedImage(this.frame);
        ++this.frames;
        return bufferedImage;
    }

    private void cacheRender(int[] nArray, int n, int[] nArray2, boolean bl) {
        int n2 = (this.frames & 1) == 0 && bl ? 0 : 6;
        n2 = (4 * n + n2) % 12;
        int[] nArray3 = new int[256];
        System.arraycopy(nArray, n << 8, nArray3, 0, 256);
        long l = NTSCRenderer.crc32(nArray3, n2, nArray2[n]);
        int[] nArray4 = this.cache.get(l);
        if (nArray4 == null) {
            nArray4 = this.ntsc_decode(this.ntsc_encode(nArray3, n2, n, nArray2[n]), n2);
            this.cache.put(l, nArray4);
        }
        System.arraycopy(nArray4, 0, this.frame, n * 2112, 2112);
    }

    public static long crc32(int[] nArray, int n, int n2) {
        CRC32 cRC32 = new CRC32();
        for (int n3 : nArray) {
            cRC32.update(n3);
        }
        cRC32.update(n);
        cRC32.update(n2);
        return cRC32.getValue();
    }

    static {
        for (int i = 0; i < 240; ++i) {
            lines.add(i);
        }
        colorphases = new int[][]{{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1}, {0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0}, {1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0}, {1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1}, {1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1}, {1, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1}, {1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1}, {1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1}, {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}};
        lumas = NTSCRenderer.genlumas();
        coloremph = new int[][]{{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, {1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1}, {0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0}, {1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1}, {1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}};
        colortbl = NTSCRenderer.genColorCorrectTbl();
        cbstphase = new int[]{0, 0, 10, 0, 8, 0, 6, 0, 4, 0, 2, 0};
        iqm = new int[][]{{255, -244, 158}, {255, 69, -165}, {255, 282, 434}};
    }
}

