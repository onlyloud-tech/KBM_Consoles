/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class Mapper62
extends Mapper {
    boolean prg_mode;
    int prgselect;
    int chrselect;

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
        int n3;
        if (n < 32768 || n > 65535) {
            super.cartWrite(n, n2);
            return;
        }
        this.prg_mode = (n & 0x20) != 0;
        this.prgselect = n & 0x40 | n >> 8 & 0x3F;
        this.chrselect = n << 2 | n2 & 3;
        for (n3 = 0; n3 < 8; ++n3) {
            this.chr_map[n3] = 1024 * (n3 + 8 * this.chrselect) & this.chrsize - 1;
        }
        if (this.prg_mode) {
            for (n3 = 0; n3 < 16; ++n3) {
                this.prg_map[n3] = 1024 * (n3 + 16 * this.prgselect) & this.prgsize - 1;
            }
            for (n3 = 0; n3 < 16; ++n3) {
                this.prg_map[16 + n3] = 1024 * (n3 + 16 * this.prgselect) & this.prgsize - 1;
            }
        } else {
            for (n3 = 0; n3 < 32; ++n3) {
                this.prg_map[n3] = 1024 * (n3 + 32 * (this.prgselect >> 1)) & this.prgsize - 1;
            }
        }
        this.setmirroring((n & 0x80) != 0 ? Mapper.MirrorType.H_MIRROR : Mapper.MirrorType.V_MIRROR);
    }
}

