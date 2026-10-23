/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.audio;

public class FFT {
    int n;
    int m;
    double[] cos;
    double[] sin;

    public FFT(int n) {
        this.n = n;
        this.m = (int)(Math.log(n) / Math.log(2.0));
        if (n != 1 << this.m) {
            throw new RuntimeException("FFT length must be power of 2");
        }
        this.cos = new double[n / 2];
        this.sin = new double[n / 2];
        for (int i = 0; i < n / 2; ++i) {
            this.cos[i] = Math.cos(Math.PI * -2 * (double)i / (double)n);
            this.sin[i] = Math.sin(Math.PI * -2 * (double)i / (double)n);
        }
    }

    public void fft(double[] dArray, double[] dArray2) {
        double d;
        int n;
        int n2;
        int n3 = 0;
        int n4 = this.n / 2;
        for (n2 = 1; n2 < this.n - 1; ++n2) {
            for (n = n4; n3 >= n; n3 -= n, n /= 2) {
            }
            if (n2 >= (n3 += n)) continue;
            d = dArray[n2];
            dArray[n2] = dArray[n3];
            dArray[n3] = d;
            d = dArray2[n2];
            dArray2[n2] = dArray2[n3];
            dArray2[n3] = d;
        }
        n = 0;
        n4 = 1;
        for (n2 = 0; n2 < this.m; ++n2) {
            n = n4;
            n4 += n4;
            int n5 = 0;
            for (n3 = 0; n3 < n; ++n3) {
                double d2 = this.cos[n5];
                double d3 = this.sin[n5];
                n5 += 1 << this.m - n2 - 1;
                for (int i = n3; i < this.n; i += n4) {
                    d = d2 * dArray[i + n] - d3 * dArray2[i + n];
                    double d4 = d3 * dArray[i + n] + d2 * dArray2[i + n];
                    dArray[i + n] = dArray[i] - d;
                    dArray2[i + n] = dArray2[i] - d4;
                    dArray[i] = dArray[i] + d;
                    dArray2[i] = dArray2[i] + d4;
                }
            }
        }
    }
}

