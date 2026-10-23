/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.audio;

import com.grapeshot.halfnes.audio.ExpansionSoundChip;

public class Namco163SoundChip
implements ExpansionSoundChip {
    private final int[] registers = new int[128];
    private final int[] out = new int[8];
    private int numch;
    private int cycpos = 0;
    private int curch = 0;
    int lpaccum = 0;

    @Override
    public void clock(int n) {
        this.numch = 1 + (this.registers[127] >> 4 & 7);
        for (int i = 0; i < n; ++i) {
            ++this.cycpos;
            this.cycpos %= 15;
            if (this.cycpos != 0) continue;
            ++this.curch;
            this.curch %= this.numch;
            this.clock_channel(this.curch);
        }
    }

    private void clock_channel(int n) {
        int n2;
        int n3 = 128 - 8 * (n + 1);
        int n4 = (this.registers[n3 + 5] << 16) + (this.registers[n3 + 3] << 8) + this.registers[n3 + 1];
        int n5 = ((this.registers[n3 + 4] & 3) << 16) + (this.registers[n3 + 2] << 8) + this.registers[n3];
        int n6 = (64 - (this.registers[n3 + 4] >> 2)) * 4;
        if (n6 > 32 && this.registers[n3 + 4] != 0) {
            n2 = 2;
            while (n2 << 2 < n6) {
                if (this.registers[n2 - 2] == 0 && this.registers[n2 - 1] == 0 && this.registers[n2] == 0) {
                    n6 = 32 - (this.registers[n3 + 4] & 0x1C);
                    break;
                }
                ++n2;
            }
        }
        n2 = this.registers[n3 + 6];
        n4 = (n4 + n5) % (n6 << 16);
        int n7 = this.registers[n3 + 7] & 0xF;
        int n8 = (this.getWavefromRAM((n4 >> 16) + n2 & 0xFF) - 8) * n7;
        this.registers[n3 + 5] = n4 >> 16 & 0xFF;
        this.registers[n3 + 3] = n4 >> 8 & 0xFF;
        this.registers[n3 + 1] = n4 & 0xFF;
        this.out[n] = n8 * 16;
        this.output();
    }

    private int getWavefromRAM(int n) {
        int n2 = this.registers[n >> 1];
        return (n & 1) != 0 ? n2 >> 4 : n2 & 0xF;
    }

    @Override
    public void write(int n, int n2) {
        this.registers[n] = n2;
    }

    public int read(int n) {
        return this.registers[n];
    }

    @Override
    public int getval() {
        return this.lpaccum << 2;
    }

    private void output() {
        int n = 0;
        for (int i = 0; i < this.numch; ++i) {
            n += this.out[i];
        }
        this.lpaccum = (int)((double)this.lpaccum - (double)(n += this.lpaccum) * 0.0625);
    }
}

