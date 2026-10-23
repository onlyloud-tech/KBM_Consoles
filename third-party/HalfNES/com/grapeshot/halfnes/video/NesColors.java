/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.video;

public class NesColors {
    private static final double att = 0.7;
    public static final int[][] col = NesColors.GetNESColors();
    public static final byte[][][] colbytes = NesColors.NESColorsToBytes(col);

    private NesColors() {
    }

    private static int[][] GetNESColors() {
        int[] nArray = new int[]{0x606060, 599694, 1708477, 3410358, 6162818, 7932217, 7277577, 5316361, 2701065, 870409, 609801, 609047, 604762, 0, 0, 0, 0xB1B1B1, 1464567, 0x4433FF, 8200447, 11867608, 13311347, 12986658, 9785097, 6255113, 2665481, 629769, 626738, 620194, 592137, 0, 0, 0xFFFFFF, 6139391, 10323199, 14117631, 0xFF5DFF, 16737222, 16744784, 16753933, 13419529, 7663625, 5569564, 3405953, 4183295, 0x494949, 0, 0, 0xFFFFFF, 13167359, 14801151, 0xFFCCFF, 0xFFC6FF, 0xFFCBFB, 16766914, 0xFFE999, 15792518, 14090128, 12451759, 11796439, 0xB3FFFF, 0xBCBCBC, 0, 0};
        int n = 0;
        while (n < nArray.length) {
            int n2 = n++;
            nArray[n2] = nArray[n2] | 0xFF000000;
        }
        int[][] nArray2 = new int[8][nArray.length];
        for (int i = 0; i < nArray.length; ++i) {
            int n3 = nArray[i];
            int n4 = NesColors.r(n3);
            int n5 = NesColors.b(n3);
            int n6 = NesColors.g(n3);
            nArray2[0][i] = n3;
            nArray2[1][i] = NesColors.compose_col(n4, (double)n6 * 0.7, (double)n5 * 0.7);
            nArray2[2][i] = NesColors.compose_col((double)n4 * 0.7, n6, (double)n5 * 0.7);
            nArray2[3][i] = NesColors.compose_col(n4, n6, (double)n5 * 0.7);
            nArray2[4][i] = NesColors.compose_col((double)n4 * 0.7, (double)n6 * 0.7, n5);
            nArray2[5][i] = NesColors.compose_col(n4, (double)n6 * 0.7, n5);
            nArray2[6][i] = NesColors.compose_col((double)n4 * 0.7, n6, n5);
            nArray2[7][i] = NesColors.compose_col((double)n4 * 0.7, (double)n6 * 0.7, (double)n5 * 0.7);
        }
        return nArray2;
    }

    private static byte[][][] NESColorsToBytes(int[][] nArray) {
        byte[][][] byArrayArray = new byte[nArray.length][][];
        for (int i = 0; i < nArray.length; ++i) {
            int[] nArray2 = nArray[i];
            byte[][] byArray = byArrayArray[i] = new byte[nArray2.length][3];
            for (int j = 0; j < nArray2.length; ++j) {
                byArray[j][0] = (byte)NesColors.b(nArray2[j]);
                byArray[j][1] = (byte)NesColors.g(nArray2[j]);
                byArray[j][2] = (byte)NesColors.r(nArray2[j]);
            }
        }
        return byArrayArray;
    }

    private static int r(int n) {
        return n >> 16 & 0xFF;
    }

    private static int g(int n) {
        return n >> 8 & 0xFF;
    }

    private static int b(int n) {
        return n & 0xFF;
    }

    private static int compose_col(double d, double d2, double d3) {
        return (((int)d & 0xFF) << 16) + (((int)d2 & 0xFF) << 8) + ((int)d3 & 0xFF) + -16777216;
    }
}

