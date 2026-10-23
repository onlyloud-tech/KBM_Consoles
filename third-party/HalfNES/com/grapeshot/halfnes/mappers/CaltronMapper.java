/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class CaltronMapper
extends Mapper {
    int reg = 0;

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
        if (n >= 24576 && n <= 26623) {
            this.reg = n & 0xFF;
            for (int i = 0; i < 32; ++i) {
                this.prg_map[i] = 1024 * (i + 32 * (n & 7)) & this.prgsize - 1;
            }
            this.setmirroring((n & 0x20) != 0 ? Mapper.MirrorType.H_MIRROR : Mapper.MirrorType.V_MIRROR);
        } else if (n >= 32768 && n <= 65535 && (this.reg & 4) != 0) {
            for (int i = 0; i < 8; ++i) {
                this.chr_map[i] = 1024 * (i + 8 * (this.reg >> 1 & 0xC | n2 & 3)) & this.chrsize - 1;
            }
        }
    }
}

