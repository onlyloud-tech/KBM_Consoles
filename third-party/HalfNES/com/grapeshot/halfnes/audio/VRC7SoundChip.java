/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.audio;

import com.grapeshot.halfnes.audio.ExpansionSoundChip;
import java.util.Arrays;

public class VRC7SoundChip
implements ExpansionSoundChip {
    private final EnvState[] modenv_state = new EnvState[6];
    private final EnvState[] carenv_state = new EnvState[6];
    private final int[] vol = new int[6];
    private final int[] freq = new int[6];
    private final int[] octave = new int[6];
    private final int[] instrument = new int[6];
    private final int[] mod = new int[6];
    private final int[] oldmodout = new int[6];
    private final int[] out = new int[6];
    private final boolean[] key = new boolean[6];
    private final boolean[] chSust = new boolean[6];
    private int fmctr = 0;
    private int amctr = 0;
    private final double[] phase = new double[6];
    private final int[] usertone = new int[8];
    private final int[] modenv_vol = new int[6];
    private final int[] carenv_vol = new int[6];
    private final int[][] instdata = new int[][]{this.usertone, {3, 33, 5, 6, 232, 129, 66, 39}, {19, 65, 20, 13, 216, 246, 35, 18}, {17, 17, 8, 8, 250, 178, 32, 18}, {49, 97, 12, 7, 168, 100, 97, 39}, {50, 33, 30, 6, 225, 118, 1, 40}, {2, 1, 6, 0, 163, 226, 244, 244}, {33, 97, 29, 7, 130, 129, 17, 7}, {35, 33, 34, 23, 162, 114, 1, 23}, {53, 17, 37, 0, 64, 115, 114, 1}, {181, 1, 15, 15, 168, 165, 81, 2}, {23, 193, 36, 7, 248, 248, 34, 18}, {113, 35, 17, 6, 101, 116, 24, 22}, {1, 2, 211, 5, 201, 149, 3, 2}, {97, 99, 12, 0, 148, 192, 51, 246}, {33, 114, 13, 0, 193, 213, 86, 6}};
    private static final int[] LOGSIN = VRC7SoundChip.genlogsintbl();
    private static final int[] EXP = VRC7SoundChip.genexptbl();
    private static final int[] AM = VRC7SoundChip.genamtbl();
    private static final double[] MULTIPLIER = new double[]{0.5, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 10.0, 12.0, 12.0, 15.0, 15.0};
    private static final double[] VIBRATO = VRC7SoundChip.genvibtbl();
    private static final int[] KEYSCALE = new int[]{0, 1536, 2048, 2368, 2560, 2752, 2880, 3008, 3072, 3200, 3264, 3328, 3392, 3456, 3520, 3584};
    int ch = 0;
    private int s;
    int lpaccum = 0;
    int lpaccum2 = 0;
    private static final int ZEROVOL = 0x800000;
    private static final int MAXVOL = 0;
    private static final int[] ATTACKVAL = new int[]{0, 0, 0, 0, 98, 120, 146, 171, 195, 216, 293, 341, 390, 471, 602, 683, 780, 964, 1168, 1366, 1560, 1927, 2315, 2731, 3075, 3855, 4682, 5461, 6242, 8035, 9364, 10921, 12480, 15423, 18727, 21856, 24960, 30847, 37413, 43713, 51130, 61580, 74991, 87425, 99841, 123161, 149319, 173949, 200870, 241044, 281218, 312464, 337461, 401739, 496266, 562435, 602609, 766957, 937392, 1205218, 0x7FFFFF, 0x7FFFFF, 0x7FFFFF, 0x7FFFFF, 0x7FFFFF, 0x7FFFFF, 0x7FFFFF, 0x7FFFFF, 0x7FFFFF, 0x7FFFFF, 0x7FFFFF, 0x7FFFFF, 0x7FFFFF, 0x7FFFFF, 0x7FFFFF, 0x7FFFFF};
    private static final int[] DECAYVAL = new int[]{0, 0, 0, 0, 8, 10, 12, 14, 16, 20, 24, 28, 32, 40, 48, 56, 65, 77, 96, 112, 129, 161, 193, 224, 258, 321, 386, 449, 516, 643, 771, 898, 1032, 1285, 1542, 1796, 2064, 2570, 3084, 3591, 4211, 5268, 6167, 7183, 8255, 10282, 12407, 14360, 16510, 20552, 24668, 28745, 33020, 41154, 49336, 57391, 66169, 82308, 98673, 114783, 132859, 132859, 132859, 132859, 132859, 132859, 132859, 132859, 132859, 132859, 132859, 132859, 132859, 132859, 132859, 132859};

    public VRC7SoundChip() {
        Arrays.fill((Object[])this.modenv_state, (Object)EnvState.CUTOFF);
        Arrays.fill((Object[])this.carenv_state, (Object)EnvState.CUTOFF);
        Arrays.fill(this.modenv_vol, 511);
        Arrays.fill(this.carenv_vol, 511);
    }

    public static int clamp(int n) {
        return n != (n & 0xFF) ? (n < 0 ? 0 : 255) : n;
    }

    private static double[] genvibtbl() {
        double d = 298295.5;
        double d2 = 6.4;
        int n = 10;
        double[] dArray = new double[(int)Math.ceil(d / d2)];
        for (int i = 0; i < dArray.length; ++i) {
            dArray[i] = (double)n * VRC7SoundChip.tri(Math.PI * 2 * d2 * (double)i / d);
        }
        return dArray;
    }

    private static int[] genamtbl() {
        double d = 298295.5;
        double d2 = 3.7;
        int n = 128;
        int[] nArray = new int[(int)Math.ceil(d / d2)];
        for (int i = 0; i < nArray.length; ++i) {
            nArray[i] = (int)((double)n * VRC7SoundChip.tri(Math.PI * 2 * d2 * (double)i / d) + (double)n);
        }
        return nArray;
    }

    private static double tri(double d) {
        if ((d %= Math.PI * 2) < 1.5707963267948966) {
            return d / Math.PI;
        }
        if (d < 4.71238898038469) {
            return 1.0 - d / Math.PI;
        }
        return d / Math.PI - 2.0;
    }

    private static int[] genlogsintbl() {
        int[] nArray = new int[256];
        for (int i = 0; i < nArray.length; ++i) {
            nArray[i] = (int)Math.round(-Math.log(Math.sin(((double)i + 0.5) * Math.PI / 256.0 / 2.0)) / Math.log(2.0) * 256.0);
        }
        return nArray;
    }

    private static int[] genexptbl() {
        int[] nArray = new int[256];
        for (int i = 0; i < nArray.length; ++i) {
            nArray[i] = (int)Math.round((Math.pow(2.0, (double)i / 256.0) - 1.0) * 1024.0);
        }
        return nArray;
    }

    @Override
    public final void write(int n, int n2) {
        switch (n) {
            case 0: 
            case 1: 
            case 2: 
            case 3: 
            case 4: 
            case 5: 
            case 6: 
            case 7: {
                this.usertone[n & 7] = n2;
                break;
            }
            case 16: 
            case 17: 
            case 18: 
            case 19: 
            case 20: 
            case 21: {
                int n3 = n - 16;
                this.freq[n3] = this.freq[n3] & 0xF00 | n2;
                break;
            }
            case 32: 
            case 33: 
            case 34: 
            case 35: 
            case 36: 
            case 37: {
                int n4 = n - 32;
                this.octave[n4] = n2 >> 1 & 7;
                this.freq[n4] = this.freq[n4] & 0xFF | (n2 & 1) << 8;
                if ((n2 & 0x10) != 0 && !this.key[n4]) {
                    this.carenv_state[n4] = EnvState.CUTOFF;
                    this.modenv_state[n4] = EnvState.CUTOFF;
                }
                this.key[n4] = (n2 & 0x10) != 0;
                this.chSust[n4] = (n2 & 0x20) != 0;
                break;
            }
            case 48: 
            case 49: 
            case 50: 
            case 51: 
            case 52: 
            case 53: {
                int n5 = n - 48;
                this.vol[n5] = n2 & 0xF;
                this.instrument[n5] = n2 >> 4 & 0xF;
                break;
            }
        }
    }

    @Override
    public final void clock(int n) {
        for (int i = 0; i < n; ++i) {
            this.ch = (this.ch + 1) % 36;
            if (this.ch >= 6) continue;
            this.operate();
        }
    }

    private void operate() {
        int n;
        this.fmctr = (this.fmctr + 1) % VIBRATO.length;
        this.amctr = (this.amctr + 1) % AM.length;
        int n2 = this.ch;
        this.phase[n2] = this.phase[n2] + 0.001953125 * (double)(this.freq[this.ch] << this.octave[this.ch]);
        int n3 = this.ch;
        this.phase[n3] = this.phase[n3] % 1024.0;
        int[] nArray = this.instdata[this.instrument[this.ch]];
        int n4 = this.setenvelope(nArray, this.modenv_state, this.modenv_vol, this.ch, false) << 2;
        int n5 = this.setenvelope(nArray, this.carenv_state, this.carenv_vol, this.ch, true) << 2;
        int n6 = KEYSCALE[this.freq[this.ch] >> 5] - 512 * (7 - this.octave[this.ch]);
        if (n6 < 0) {
            n6 = 0;
        }
        n = (n = nArray[2] >> 6) == 0 ? 0 : n6 >> 3 - n;
        int n7 = nArray[3] >> 6;
        n7 = n7 == 0 ? 0 : n6 >> 3 - n7;
        int n8 = ~nArray[3] & 7;
        double d = (nArray[0] & 0x40) != 0 ? VIBRATO[this.fmctr] * (double)(1 << this.octave[this.ch]) : 0.0;
        double d2 = MULTIPLIER[nArray[0] & 0xF];
        int n9 = n8 == 7 ? 0 : this.mod[this.ch] + this.oldmodout[this.ch] >> 2 + n8;
        int n10 = n9 + (int)(d + d2 * this.phase[this.ch]);
        int n11 = (nArray[2] & 0x3F) * 32;
        int n12 = (nArray[0] & 0x80) != 0 ? AM[this.amctr] : 0;
        boolean bl = (nArray[3] & 8) != 0;
        this.mod[this.ch] = this.operator(n10, n11 + n4 + n + n12, bl) << 2;
        this.oldmodout[this.ch] = this.mod[this.ch];
        double d3 = (nArray[1] & 0x40) != 0 ? VIBRATO[this.fmctr] * (double)(this.freq[this.ch] << this.octave[this.ch]) / 512.0 : 0.0;
        double d4 = MULTIPLIER[nArray[1] & 0xF];
        int n13 = this.mod[this.ch] + this.oldmodout[this.ch] >> 1;
        int n14 = n13 + (int)(d3 + d4 * this.phase[this.ch]);
        int n15 = this.vol[this.ch] * 128;
        int n16 = (nArray[1] & 0x80) != 0 ? AM[this.amctr] : 0;
        boolean bl2 = (nArray[3] & 0x10) != 0;
        this.out[this.ch] = this.operator(n14, n15 + n5 + n7 + n16, bl2) << 2;
        this.outputSample(this.ch);
    }

    private int operator(int n, int n2, boolean bl) {
        return this.exp(this.logsin(n, bl) + n2);
    }

    private int exp(int n) {
        if (n > 8191) {
            n = 8191;
        }
        int n2 = EXP[-n & 0xFF];
        int n3 = -n >> 8;
        return (n2 + 1024 >> -n3) * this.s;
    }

    private int logsin(int n, boolean bl) {
        switch (n >> 8 & 3) {
            case 0: {
                this.s = 1;
                return LOGSIN[n & 0xFF];
            }
            case 1: {
                this.s = 1;
                return LOGSIN[255 - (n & 0xFF)];
            }
            case 2: {
                this.s = bl ? 0 : -1;
                return LOGSIN[n & 0xFF];
            }
        }
        this.s = bl ? 0 : -1;
        return LOGSIN[255 - (n & 0xFF)];
    }

    private void outputSample(int n) {
        int n2 = this.out[n] * 24;
        this.lpaccum -= (n2 += this.lpaccum) >> 2;
        int n3 = this.lpaccum;
        this.lpaccum2 -= (n3 += this.lpaccum2) >> 2;
    }

    @Override
    public final int getval() {
        return this.lpaccum2;
    }

    private int setenvelope(int[] nArray, EnvState[] envStateArray, int[] nArray2, int n, boolean bl) {
        int n2;
        boolean bl2 = (nArray[bl ? 1 : 0] & 0x10) != 0;
        int n3 = bl2 ? (this.octave[n] << 1) + (this.freq[n] >> 8) : this.octave[n] >> 1;
        switch (envStateArray[n]) {
            default: {
                if (nArray2[n] < 0x800000) {
                    int n4 = n;
                    nArray2[n4] = nArray2[n4] + 16384;
                    break;
                }
                nArray2[n] = 0x800000;
                if (!this.key[n]) break;
                envStateArray[n] = EnvState.ATTACK;
                this.phase[n] = 0.0;
                break;
            }
            case ATTACK: {
                if (nArray2[n] > 8) {
                    int n5 = n;
                    nArray2[n5] = nArray2[n5] - ATTACKVAL[(nArray[bl ? 5 : 4] >> 4) * 4 + n3];
                } else {
                    envStateArray[n] = EnvState.DECAY;
                }
                if (this.key[n]) break;
                envStateArray[n] = EnvState.RELEASE;
                break;
            }
            case DECAY: {
                if (nArray2[n] < (nArray[bl ? 7 : 6] >> 4) * 524288) {
                    int n6 = n;
                    nArray2[n6] = nArray2[n6] + DECAYVAL[(nArray[bl ? 5 : 4] & 0xF) * 4 + n3];
                } else {
                    envStateArray[n] = EnvState.RELEASE;
                }
                if (this.key[n]) break;
                envStateArray[n] = EnvState.RELEASE;
                break;
            }
            case RELEASE: {
                n2 = (nArray[bl ? 1 : 0] & 0x20) != 0 ? 1 : 0;
                boolean bl3 = this.chSust[n];
                if (this.key[n]) {
                    if (n2 != 0) break;
                    int n7 = n;
                    nArray2[n7] = nArray2[n7] + DECAYVAL[(nArray[bl ? 7 : 6] & 0xF) * 4 + n3];
                    break;
                }
                if (n2 != 0) {
                    if (bl3) {
                        int n8 = n;
                        nArray2[n8] = nArray2[n8] + DECAYVAL[20 + n3];
                        break;
                    }
                    int n9 = n;
                    nArray2[n9] = nArray2[n9] + DECAYVAL[(nArray[bl ? 7 : 6] & 0xF) * 4 + n3];
                    break;
                }
                if (bl3) {
                    int n10 = n;
                    nArray2[n10] = nArray2[n10] + DECAYVAL[20 + n3];
                    break;
                }
                int n11 = n;
                nArray2[n11] = nArray2[n11] + DECAYVAL[28 + n3];
            }
        }
        if (nArray2[n] < 0) {
            nArray2[n] = 0;
        }
        if (nArray2[n] > 0x800000) {
            nArray2[n] = 0x800000;
        }
        if (envStateArray[n] == EnvState.ATTACK) {
            n2 = 0x800000 - (int)(8388608.0 * Math.log(0x800000 - nArray2[n]) / Math.log(8388608.0));
            return n2 >> 14;
        }
        return nArray2[n] >> 14;
    }

    private static enum EnvState {
        CUTOFF,
        ATTACK,
        DECAY,
        RELEASE;

    }
}

