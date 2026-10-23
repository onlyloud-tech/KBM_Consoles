/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class Sunsoft01Mapper
extends Mapper {
    private int lowBank = 0;
    private int highBank = 0;

    @Override
    public void loadrom() throws BadMapperException {
        int n;
        super.loadrom();
        for (n = 0; n < 32; ++n) {
            this.prg_map[n] = 1024 * n & this.prgsize - 1;
        }
        for (n = 0; n < 8; ++n) {
            this.chr_map[n] = 1024 * n & this.chrsize - 1;
        }
    }

    @Override
    public final void cartWrite(int n, int n2) {
        if (n >= 24576 && n < 32768) {
            int n3;
            this.lowBank = n2 & 7;
            this.highBank = n2 >> 4 & 7;
            for (n3 = 0; n3 < 4; ++n3) {
                this.chr_map[n3] = 1024 * (n3 + this.lowBank * 4) % this.chrsize;
            }
            for (n3 = 0; n3 < 4; ++n3) {
                this.chr_map[4 + n3] = 1024 * (n3 + this.highBank * 4) % this.chrsize;
            }
        }
    }
}

