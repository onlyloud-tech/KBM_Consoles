/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class Mapper15
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
    public final void cartWrite(int n, int n2) {
        if (n < 32768 || n > 65535) {
            super.cartWrite(n, n2);
            return;
        }
        int n3 = n2 << 1 & 0xFE;
        int n4 = n2 >> 7;
        this.setmirroring((n2 & 0x40) != 0 ? Mapper.MirrorType.H_MIRROR : Mapper.MirrorType.V_MIRROR);
        switch (n & 0xFFF) {
            case 0: {
                int n5;
                for (n5 = 0; n5 < 8; ++n5) {
                    this.prg_map[n5] = 1024 * (n5 + 8 * (n3 | 0 ^ n4)) & this.prgsize - 1;
                }
                for (n5 = 0; n5 < 8; ++n5) {
                    this.prg_map[8 + n5] = 1024 * (n5 + 8 * (n3 | 1 ^ n4)) & this.prgsize - 1;
                }
                for (n5 = 0; n5 < 8; ++n5) {
                    this.prg_map[16 + n5] = 1024 * (n5 + 8 * (n3 | 2 ^ n4)) & this.prgsize - 1;
                }
                for (n5 = 0; n5 < 8; ++n5) {
                    this.prg_map[24 + n5] = 1024 * (n5 + 8 * (n3 | 3 ^ n4)) & this.prgsize - 1;
                }
                break;
            }
            case 1: {
                int n6;
                for (n6 = 0; n6 < 8; ++n6) {
                    this.prg_map[n6] = 1024 * (n6 + 8 * (n3 | 0 ^ n4)) & this.prgsize - 1;
                }
                for (n6 = 0; n6 < 8; ++n6) {
                    this.prg_map[8 + n6] = 1024 * (n6 + 8 * (n3 | 1 ^ n4)) & this.prgsize - 1;
                }
                for (n6 = 0; n6 < 8; ++n6) {
                    this.prg_map[16 + n6] = 1024 * (n6 + 8 * (0x7E | 0 ^ n4)) & this.prgsize - 1;
                }
                for (n6 = 0; n6 < 8; ++n6) {
                    this.prg_map[24 + n6] = 1024 * (n6 + 8 * (0x7F | 1 ^ n4)) & this.prgsize - 1;
                }
                break;
            }
            case 2: {
                int n7;
                n3 |= n4;
                for (n7 = 0; n7 < 8; ++n7) {
                    this.prg_map[n7] = 1024 * (n7 + 8 * n3) & this.prgsize - 1;
                }
                for (n7 = 0; n7 < 8; ++n7) {
                    this.prg_map[8 + n7] = 1024 * (n7 + 8 * n3) & this.prgsize - 1;
                }
                for (n7 = 0; n7 < 8; ++n7) {
                    this.prg_map[16 + n7] = 1024 * (n7 + 8 * n3) & this.prgsize - 1;
                }
                for (n7 = 0; n7 < 8; ++n7) {
                    this.prg_map[24 + n7] = 1024 * (n7 + 8 * n3) & this.prgsize - 1;
                }
                break;
            }
            case 3: {
                int n8;
                n3 |= n4;
                for (n8 = 0; n8 < 8; ++n8) {
                    this.prg_map[n8] = 1024 * (n8 + 8 * n3) & this.prgsize - 1;
                }
                for (n8 = 0; n8 < 8; ++n8) {
                    this.prg_map[8 + n8] = 1024 * (n8 + 8 * (n3 + 1)) & this.prgsize - 1;
                }
                for (n8 = 0; n8 < 8; ++n8) {
                    this.prg_map[16 + n8] = 1024 * (n8 + 8 * (n3 + (~n >> 1 & 1))) & this.prgsize - 1;
                }
                for (n8 = 0; n8 < 8; ++n8) {
                    this.prg_map[24 + n8] = 1024 * (n8 + 8 * (n3 + 1)) & this.prgsize - 1;
                }
                break;
            }
        }
    }
}

