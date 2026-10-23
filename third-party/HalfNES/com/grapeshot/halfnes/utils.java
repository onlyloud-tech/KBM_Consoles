/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes;

import java.util.Locale;

public class utils {
    public static final int BIT0 = 1;
    public static final int BIT1 = 2;
    public static final int BIT2 = 4;
    public static final int BIT3 = 8;
    public static final int BIT4 = 16;
    public static final int BIT5 = 32;
    public static final int BIT6 = 64;
    public static final int BIT7 = 128;
    public static final int BIT8 = 256;
    public static final int BIT9 = 512;
    public static final int BIT10 = 1024;
    public static final int BIT11 = 2048;
    public static final int BIT12 = 4096;
    public static final int BIT13 = 8192;
    public static final int BIT14 = 16384;
    public static final int BIT15 = 32768;

    private utils() {
    }

    public static int setbit(int n, int n2, boolean bl) {
        return bl ? n | 1 << n2 : n & ~(1 << n2);
    }

    public static String hex(int n) {
        Object object = Integer.toHexString(n).toUpperCase(Locale.US);
        if ((((String)object).length() & 1) == 1) {
            object = "0" + (String)object;
        }
        return (String)object;
    }

    public static String hex(long l) {
        Object object = Long.toHexString(l).toUpperCase(Locale.US);
        if ((((String)object).length() & 1) == 1) {
            object = "0" + (String)object;
        }
        return (String)object;
    }

    public static int reverseByte(int n) {
        return Integer.reverse(n) >> 24 & 0xFF;
    }

    public static void printarray(int[] nArray) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int n : nArray) {
            stringBuilder.append(n);
            stringBuilder.append(", ");
        }
        if (stringBuilder.length() >= 1) {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }
        stringBuilder.append("\n");
        System.err.print(stringBuilder.toString());
    }

    public static void printarray(boolean[] blArray) {
        StringBuilder stringBuilder = new StringBuilder();
        for (boolean bl : blArray) {
            stringBuilder.append(bl);
            stringBuilder.append(", ");
        }
        if (stringBuilder.length() >= 1) {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }
        stringBuilder.append("\n");
        System.err.print(stringBuilder.toString());
    }

    public static void printarray(double[] dArray) {
        StringBuilder stringBuilder = new StringBuilder();
        for (double d : dArray) {
            stringBuilder.append(d);
            stringBuilder.append(", ");
        }
        if (stringBuilder.length() >= 1) {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }
        stringBuilder.append("\n");
        System.err.print(stringBuilder.toString());
    }

    public static void printarray(float[] fArray) {
        StringBuilder stringBuilder = new StringBuilder();
        for (float f : fArray) {
            stringBuilder.append(f);
            stringBuilder.append(", ");
        }
        if (stringBuilder.length() >= 1) {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }
        stringBuilder.append("\n");
        System.err.print(stringBuilder.toString());
    }

    public static void printarray(Object[] objectArray) {
        StringBuilder stringBuilder = new StringBuilder();
        for (Object object : objectArray) {
            stringBuilder.append(object.toString());
            stringBuilder.append(", ");
        }
        if (stringBuilder.length() >= 1) {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }
        stringBuilder.append("\n");
        System.err.print(stringBuilder.toString());
    }

    public static int max(int[] nArray) {
        int n = nArray[0];
        int[] nArray2 = nArray;
        int n2 = nArray2.length;
        for (int i = 0; i < n2; ++i) {
            Integer n3 = nArray2[i];
            if (n3 <= n) continue;
            n = n3;
        }
        return n;
    }
}

