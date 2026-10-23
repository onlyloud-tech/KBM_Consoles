/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class Action52Mapper
extends Mapper {
    int[] ram = new int[4];
    int prgchip = 0;
    int prgpage = 0;
    int chrpage = 0;
    boolean prgmode = false;

    @Override
    public void loadrom() throws BadMapperException {
        super.loadrom();
        this.cartWrite(32768, 0);
    }

    @Override
    public final void cartWrite(int n, int n2) {
        block11: {
            int n3;
            block10: {
                if (n > 24575) break block10;
                this.ram[n & 3] = n2 & 0xF;
                break block11;
            }
            if (n < 32768) break block11;
            this.chrpage = ((n & 0xF) << 2) + (n2 & 3);
            this.prgmode = (n & 0x20) != 0;
            this.prgpage = n >> 6 & 0x1F;
            this.prgchip = n >> 11 & 3;
            this.setmirroring((n & 0x2000) != 0 ? Mapper.MirrorType.H_MIRROR : Mapper.MirrorType.V_MIRROR);
            for (n3 = 0; n3 < 8; ++n3) {
                this.chr_map[n3] = 1024 * (this.chrpage * 8 + n3) % this.chrsize;
            }
            n3 = 0;
            switch (this.prgchip) {
                case 0: {
                    n3 = 0;
                    break;
                }
                case 1: {
                    n3 = 524288;
                    break;
                }
                case 3: {
                    n3 = 0x100000;
                    break;
                }
                default: {
                    System.err.println("Who knows.");
                }
            }
            if (this.prgmode) {
                for (int i = 0; i < 16; ++i) {
                    this.prg_map[i] = (1024 * (16 * this.prgpage + i) + n3) % this.prgsize;
                    this.prg_map[i + 16] = (1024 * (16 * this.prgpage + i) + n3) % this.prgsize;
                }
            } else {
                for (int i = 0; i < 32; ++i) {
                    this.prg_map[i] = (1024 * (32 * (this.prgpage >> 1) + i) + n3) % this.prgsize;
                }
            }
        }
    }

    @Override
    public final int cartRead(int n) {
        if (n >= 32768) {
            return this.prg[this.prg_map[(n & Short.MAX_VALUE) >> 10] + (n & 0x3FF)];
        }
        if (n < 24576) {
            return this.ram[n & 3] & 0xF;
        }
        return n >> 8;
    }

    @Override
    public void reset() {
        this.cartWrite(32768, 0);
    }
}

