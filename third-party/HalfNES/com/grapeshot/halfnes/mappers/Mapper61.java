/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class Mapper61
extends Mapper {
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
    public void reset() {
        this.cartWrite(32768, this.cartRead(32768));
    }

    @Override
    public final void cartWrite(int n, int n2) {
        if (n < 32768 || n > 65535) {
            super.cartWrite(n, n2);
            return;
        }
        switch (n & 0x30) {
            case 0: 
            case 48: {
                for (int i = 0; i < 32; ++i) {
                    this.prg_map[i] = 1024 * (i + 32 * (n & 0xF)) & this.prgsize - 1;
                }
                break;
            }
            case 16: 
            case 32: {
                int n3;
                int n4 = n << 1 & 0x1E | n >> 4 & 2;
                for (n3 = 0; n3 < 16; ++n3) {
                    this.prg_map[n3] = 1024 * (n3 + 32 * n4) & this.prgsize - 1;
                }
                for (n3 = 0; n3 < 16; ++n3) {
                    this.prg_map[n3 + 16] = 1024 * (n3 + 32 * n4) & this.prgsize - 1;
                }
                break;
            }
        }
        this.setmirroring((n & 0x80) != 0 ? Mapper.MirrorType.H_MIRROR : Mapper.MirrorType.V_MIRROR);
    }
}

