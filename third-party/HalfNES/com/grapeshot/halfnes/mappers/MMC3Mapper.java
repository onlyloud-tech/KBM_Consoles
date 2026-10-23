/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class MMC3Mapper
extends Mapper {
    protected int whichbank = 0;
    protected boolean prgconfig = false;
    protected boolean chrconfig = false;
    protected int irqctrreload = 0;
    protected int irqctr = 0;
    protected boolean irqenable = false;
    protected boolean irqreload = false;
    protected int bank6 = 0;
    protected int[] chrreg = new int[]{0, 0, 0, 0, 0, 0};
    protected boolean interrupted = false;
    private boolean lastA12 = false;
    int a12timer = 0;

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
        if (n < 32768 || n > 65535) {
            super.cartWrite(n, n2);
            return;
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
                    for (int i = 0; i < 8; ++i) {
                        this.prg_map[i + 8] = 1024 * (i + n2 * 8) % this.prgsize;
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

    protected void setupchr() {
        if (this.chrconfig) {
            this.setppubank(1, 0, this.chrreg[2]);
            this.setppubank(1, 1, this.chrreg[3]);
            this.setppubank(1, 2, this.chrreg[4]);
            this.setppubank(1, 3, this.chrreg[5]);
            this.setppubank(2, 4, this.chrreg[0] >> 1 << 1);
            this.setppubank(2, 6, this.chrreg[1] >> 1 << 1);
        } else {
            this.setppubank(1, 4, this.chrreg[2]);
            this.setppubank(1, 5, this.chrreg[3]);
            this.setppubank(1, 6, this.chrreg[4]);
            this.setppubank(1, 7, this.chrreg[5]);
            this.setppubank(2, 0, this.chrreg[0] >> 1 << 1);
            this.setppubank(2, 2, this.chrreg[1] >> 1 << 1);
        }
    }

    protected void setbank6() {
        if (!this.prgconfig) {
            for (int i = 0; i < 8; ++i) {
                this.prg_map[i] = 1024 * (i + this.bank6 * 8) % this.prgsize;
                this.prg_map[i + 16] = this.prgsize - 16384 + 1024 * i;
            }
        } else {
            for (int i = 0; i < 8; ++i) {
                this.prg_map[i] = this.prgsize - 16384 + 1024 * i;
                this.prg_map[i + 16] = 1024 * (i + this.bank6 * 8) % this.prgsize;
            }
        }
    }

    @Override
    public int ppuRead(int n) {
        this.checkA12(n);
        return super.ppuRead(n);
    }

    @Override
    public void ppuWrite(int n, int n2) {
        this.checkA12(n);
        super.ppuWrite(n, n2);
    }

    @Override
    public void checkA12(int n) {
        boolean bl;
        boolean bl2 = bl = (n & 0x1000) != 0;
        if (bl && !this.lastA12) {
            if (this.a12timer <= 0) {
                this.clockScanCounter();
            }
        } else if (!bl && this.lastA12) {
            this.a12timer = 8;
        }
        --this.a12timer;
        this.lastA12 = bl;
    }

    private void clockScanCounter() {
        if (this.irqreload || this.irqctr == 0) {
            this.irqctr = this.irqctrreload;
            this.irqreload = false;
        } else {
            --this.irqctr;
        }
        if (this.irqctr == 0 && this.irqenable && !this.interrupted) {
            ++this.cpu.interrupt;
            this.interrupted = true;
        }
    }

    protected void setppubank(int n, int n2, int n3) {
        for (int i = 0; i < n; ++i) {
            this.chr_map[i + n2] = 1024 * (n3 + i) % this.chrsize;
        }
    }
}

