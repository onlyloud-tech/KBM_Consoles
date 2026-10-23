/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.MMC3Mapper;

public class Mapper119
extends MMC3Mapper {
    int[] chrRam = new int[8192];

    @Override
    public int ppuRead(int n) {
        if (n < 8192) {
            this.checkA12(n);
            return this.chr_map[n >> 10] > 65535 ? this.chrRam[this.chr_map[n >> 10] + (n & 0x3FF) & 0x1FFF] : this.chr[(this.chr_map[n >> 10] & 0xFFFF) + (n & 0x3FF)];
        }
        return super.ppuRead(n);
    }

    @Override
    public void ppuWrite(int n, int n2) {
        if (n < 8192) {
            this.checkA12(n);
            if (this.chr_map[n >> 10] > 63) {
                this.chrRam[this.chr_map[n >> 10] + (n & 0x3FF) & 0x1FFF] = n2;
            }
        } else {
            super.ppuWrite(n, n2);
        }
    }

    @Override
    public void loadrom() throws BadMapperException {
        int n;
        super.loadrom();
        for (n = 0; n < 8; ++n) {
            this.prg_map[n] = 1024 * n;
            this.prg_map[n + 8] = 1024 * n;
        }
        for (n = 1; n <= 32; ++n) {
            this.prg_map[32 - n] = this.prgsize - 1024 * n;
        }
        for (n = 0; n < 8; ++n) {
            this.chr_map[n] = 0;
        }
        this.setbank6();
    }

    @Override
    protected void setppubank(int n, int n2, int n3) {
        for (int i = 0; i < n; ++i) {
            this.chr_map[i + n2] = 1024 * (n3 + i);
        }
    }
}

