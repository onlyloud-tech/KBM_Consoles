/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.MMC3Mapper;
import com.grapeshot.halfnes.mappers.Mapper;

public class Mapper47
extends MMC3Mapper {
    private int multibank = 1;

    @Override
    public void loadrom() throws BadMapperException {
        int n;
        super.loadrom();
        for (n = 1; n <= 32; ++n) {
            this.prg_map[32 - n] = this.prgsize - 1024 * n;
        }
        for (n = 0; n < 8; ++n) {
            this.chr_map[n] = 0;
        }
        this.setbank6();
    }

    @Override
    public void cartWrite(int n, int n2) {
        int n3;
        if (n < 24576 || n > 65535) {
            super.cartWrite(n, n2);
            return;
        }
        if (n >= 24576 && n <= Short.MAX_VALUE) {
            this.multibank = n2 & 1;
            for (n3 = 0; n3 < 8; ++n3) {
                this.prg_map[n3 + 8] = 1024 * (n3 + n2 * 8) % 131072 + this.multibank * 131072;
            }
            this.setbank6();
            this.setupchr();
            for (n3 = 1; n3 <= 8; ++n3) {
                this.prg_map[32 - n3] = 131072 - 1024 * n3 + this.multibank * 131072;
            }
        }
        if ((n & 1) != 0) {
            if (n >= 32768 && n <= 40959) {
                if (this.whichbank <= 5) {
                    this.chrreg[this.whichbank] = n2;
                    this.setupchr();
                } else if (this.whichbank == 6) {
                    this.bank6 = n2;
                    this.setbank6();
                } else if (this.whichbank == 7) {
                    for (n3 = 0; n3 < 8; ++n3) {
                        this.prg_map[n3 + 8] = 1024 * (n3 + n2 * 8) % 131072 + this.multibank * 131072;
                    }
                }
            } else if (n < 40960 || n > 49151) {
                if (n >= 49152 && n <= 57343) {
                    this.irqreload = true;
                } else if (n >= 57344 && n <= 65535) {
                    this.irqenable = true;
                }
            }
        } else if (n >= 32768 && n <= 40959) {
            this.whichbank = n2 & 7;
            this.prgconfig = (n2 & 0x40) != 0;
            this.chrconfig = (n2 & 0x80) != 0;
            this.setupchr();
            this.setbank6();
        } else if (n >= 40960 && n <= 49151) {
            if (this.scrolltype != Mapper.MirrorType.FOUR_SCREEN_MIRROR) {
                this.setmirroring((n2 & 1) != 0 ? Mapper.MirrorType.H_MIRROR : Mapper.MirrorType.V_MIRROR);
            }
        } else if (n >= 49152 && n <= 57343) {
            this.irqctrreload = n2;
        } else if (n >= 57344 && n <= 65535) {
            if (this.interrupted) {
                --this.cpu.interrupt;
            }
            this.interrupted = false;
            this.irqenable = false;
        }
    }

    @Override
    protected void setbank6() {
        if (!this.prgconfig) {
            for (int i = 0; i < 8; ++i) {
                this.prg_map[i] = 1024 * (i + this.bank6 * 8) % 131072 + this.multibank * 131072;
                this.prg_map[i + 16] = 114688 + 1024 * (i + this.multibank * 128);
            }
        } else {
            for (int i = 0; i < 8; ++i) {
                this.prg_map[i] = 114688 + 1024 * (i + this.multibank * 128);
                this.prg_map[i + 16] = 1024 * (i + this.bank6 * 8) % 131072 + this.multibank * 131072;
            }
        }
    }

    @Override
    protected void setppubank(int n, int n2, int n3) {
        for (int i = 0; i < n; ++i) {
            this.chr_map[i + n2] = 1024 * (n3 + i) % (this.chrsize / 2) + this.multibank * 131072;
        }
    }
}

