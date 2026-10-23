/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class Mapper246
extends Mapper {
    @Override
    public void loadrom() throws BadMapperException {
        int n;
        super.loadrom();
        for (n = 0; n < 24; ++n) {
            this.prg_map[n] = 1024 * n & this.prgsize - 1;
        }
        for (n = 1; n <= 8; ++n) {
            this.prg_map[32 - n] = this.prgsize - 1024 * n;
        }
        for (n = 0; n < 8; ++n) {
            this.chr_map[n] = 1024 * n & this.chrsize - 1;
        }
    }

    @Override
    public final void cartWrite(int n, int n2) {
        if (n < 24576 || n > 26623) {
            super.cartWrite(n, n2);
            return;
        }
        switch (n & 7) {
            case 0: {
                for (int i = 0; i < 8; ++i) {
                    this.prg_map[i] = 1024 * (i + 8 * n2) & this.prgsize - 1;
                }
                break;
            }
            case 1: {
                for (int i = 0; i < 8; ++i) {
                    this.prg_map[8 + i] = 1024 * (i + 8 * n2) & this.prgsize - 1;
                }
                break;
            }
            case 2: {
                for (int i = 0; i < 8; ++i) {
                    this.prg_map[16 + i] = 1024 * (i + 8 * n2) & this.prgsize - 1;
                }
                break;
            }
            case 3: {
                for (int i = 0; i < 8; ++i) {
                    this.prg_map[24 + i] = 1024 * (i + 8 * n2) & this.prgsize - 1;
                }
                break;
            }
            case 4: {
                for (int i = 0; i < 2; ++i) {
                    this.chr_map[i] = 1024 * (i + 2 * n2) & this.chrsize - 1;
                }
                break;
            }
            case 5: {
                for (int i = 0; i < 2; ++i) {
                    this.chr_map[2 + i] = 1024 * (i + 2 * n2) & this.chrsize - 1;
                }
                break;
            }
            case 6: {
                for (int i = 0; i < 2; ++i) {
                    this.chr_map[4 + i] = 1024 * (i + 2 * n2) & this.chrsize - 1;
                }
                break;
            }
            case 7: {
                for (int i = 0; i < 2; ++i) {
                    this.chr_map[6 + i] = 1024 * (i + 2 * n2) & this.chrsize - 1;
                }
                break;
            }
        }
    }
}

