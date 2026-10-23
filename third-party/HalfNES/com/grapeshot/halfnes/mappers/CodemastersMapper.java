/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class CodemastersMapper
extends Mapper {
    private int bank = 0;

    @Override
    public void loadrom() throws BadMapperException {
        int n;
        super.loadrom();
        for (n = 0; n < 16; ++n) {
            this.prg_map[n] = 1024 * n & this.prgsize - 1;
        }
        for (n = 1; n <= 16; ++n) {
            this.prg_map[32 - n] = this.prgsize - 1024 * n;
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
        if (n < 49152) {
            if (this.crc == 465995432L) {
                this.setmirroring((n2 & 0x10) != 0 ? Mapper.MirrorType.SS_MIRROR1 : Mapper.MirrorType.SS_MIRROR0);
            }
        } else {
            this.bank = n2 & 0xF;
            for (int i = 0; i < 16; ++i) {
                this.prg_map[i] = 1024 * (i + 16 * this.bank) & this.prgsize - 1;
            }
        }
    }
}

