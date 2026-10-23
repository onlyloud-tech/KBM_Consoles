/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class NromMapper
extends Mapper {
    @Override
    public void loadrom() throws BadMapperException {
        super.loadrom();
        int[] nArray = new int[65536];
        System.arraycopy(this.prg, 0, nArray, 32768, this.prg.length);
        if (this.prgsize <= 16384) {
            System.arraycopy(this.prg, 0, nArray, 49152, this.prg.length);
        }
        this.prg = nArray;
    }

    @Override
    public int cartRead(int n) {
        if (n >= 32768) {
            return this.prg[n];
        }
        if (n >= 24576 && this.hasprgram) {
            return this.prgram[n & 0x1FFF];
        }
        return n >> 8;
    }

    @Override
    public int ppuRead(int n) {
        if (n < 8192) {
            return this.chr[n];
        }
        switch (n & 0xC00) {
            case 0: {
                return this.nt0[n & 0x3FF];
            }
            case 1024: {
                return this.nt1[n & 0x3FF];
            }
            case 2048: {
                return this.nt2[n & 0x3FF];
            }
        }
        if (n >= 16128) {
            if ((n &= 0x1F) >= 16 && (n & 3) == 0) {
                n -= 16;
            }
            return this.ppu.pal[n];
        }
        return this.nt3[n & 0x3FF];
    }
}

