/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class Mapper226
extends Mapper {
    int[] reg = new int[]{0, 0};

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
        if (n < 32768 || n > 65535) {
            super.cartWrite(n, n2);
            return;
        }
        this.reg[n & 1] = n2;
        int n3 = this.reg[0] >> 1 & 0xF | this.reg[0] >> 3 & 0x10 | this.reg[1] << 5 & 0x20;
        this.setmirroring((this.reg[0] & 0x40) != 0 ? Mapper.MirrorType.V_MIRROR : Mapper.MirrorType.H_MIRROR);
        if ((this.reg[0] & 0x20) != 0) {
            int n4;
            n3 = n3 << 1 | this.reg[0] & 1;
            for (n4 = 0; n4 < 16; ++n4) {
                this.prg_map[n4] = 1024 * (n4 + 16 * n3) & this.prgsize - 1;
            }
            for (n4 = 0; n4 < 16; ++n4) {
                this.prg_map[n4 + 16] = 1024 * (n4 + 16 * n3) & this.prgsize - 1;
            }
        } else {
            for (int i = 0; i < 32; ++i) {
                this.prg_map[i] = 1024 * (i + 32 * n3) & this.prgsize - 1;
            }
        }
    }
}

