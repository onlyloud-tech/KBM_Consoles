/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class Mapper48
extends Mapper {
    int prgbank0;
    int prgbank1 = 0;
    int[] chrbank = new int[]{0, 0, 0, 0, 0, 0};
    private int irqctrreload = 0;
    private int irqctr = 0;
    private boolean irqenable = false;
    private boolean irqreload = false;
    private boolean interrupted = false;

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
        if (n <= 40959) {
            switch (n & 3) {
                case 0: {
                    this.prgbank0 = n2;
                    this.setbanks();
                    break;
                }
                case 1: {
                    this.prgbank1 = n2;
                    this.setbanks();
                    break;
                }
                case 2: {
                    this.chrbank[0] = n2;
                    this.setbanks();
                    break;
                }
                case 3: {
                    this.chrbank[1] = n2;
                    this.setbanks();
                }
            }
        } else if (n <= 49151) {
            switch (n & 3) {
                case 0: {
                    this.chrbank[2] = n2;
                    this.setbanks();
                    break;
                }
                case 1: {
                    this.chrbank[3] = n2;
                    this.setbanks();
                    break;
                }
                case 2: {
                    this.chrbank[4] = n2;
                    this.setbanks();
                    break;
                }
                case 3: {
                    this.chrbank[5] = n2;
                    this.setbanks();
                }
            }
        } else if (n <= 57343) {
            switch (n & 3) {
                case 0: {
                    this.irqctrreload = n2 & 0xFF;
                    this.irqreload = true;
                    break;
                }
                case 1: {
                    this.irqctr = n2;
                    this.irqreload = true;
                    break;
                }
                case 2: {
                    this.irqenable = true;
                    break;
                }
                case 3: {
                    if (this.interrupted) {
                        --this.cpu.interrupt;
                    }
                    this.interrupted = false;
                    this.irqenable = false;
                    this.irqctr = this.irqctrreload;
                }
            }
        } else if (n <= 65535) {
            switch (n & 3) {
                case 0: {
                    this.setmirroring((n2 & 0x40) != 0 ? Mapper.MirrorType.H_MIRROR : Mapper.MirrorType.V_MIRROR);
                }
            }
        }
    }

    private void setbanks() {
        int n;
        for (n = 1; n <= 16; ++n) {
            this.prg_map[32 - n] = this.prgsize - 1024 * n;
        }
        for (n = 0; n < 8; ++n) {
            this.prg_map[n] = 1024 * (n + 8 * this.prgbank0) & this.prgsize - 1;
        }
        for (n = 0; n < 8; ++n) {
            this.prg_map[n + 8] = 1024 * (n + 8 * this.prgbank1) & this.prgsize - 1;
        }
        this.setppubank(1, 4, this.chrbank[2]);
        this.setppubank(1, 5, this.chrbank[3]);
        this.setppubank(1, 6, this.chrbank[4]);
        this.setppubank(1, 7, this.chrbank[5]);
        this.setppubank(2, 0, this.chrbank[0]);
        this.setppubank(2, 2, this.chrbank[1]);
    }

    private void setppubank(int n, int n2, int n3) {
        for (int i = 0; i < n; ++i) {
            this.chr_map[i + n2] = 1024 * (i + n * n3) & this.chrsize - 1;
        }
    }

    @Override
    public void notifyscanline(int n) {
        if (n > 239 && n != 261) {
            return;
        }
        if (!this.ppu.mmc3CounterClocking()) {
            return;
        }
        if (this.irqreload) {
            this.irqreload = false;
            this.irqctr = this.irqctrreload;
        }
        if (this.irqctr-- <= 0) {
            if (this.irqctrreload == 0) {
                return;
            }
            if (this.irqenable && !this.interrupted) {
                ++this.cpu.interrupt;
                this.interrupted = true;
            }
            this.irqctr = this.irqctrreload;
        }
    }
}

