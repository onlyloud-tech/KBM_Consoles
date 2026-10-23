/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class VRC3Mapper
extends Mapper {
    private int irqctr;
    private int irqreload = 0;
    private boolean irqmode;
    private boolean irqenable;
    private boolean irqackenable;
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
        switch (n >> 12) {
            case 8: {
                this.irqreload = this.irqreload & 0xFFF0 | n2 & 0xF;
                break;
            }
            case 9: {
                this.irqreload = this.irqreload & 0xFF0F | (n2 & 0xF) << 4;
                break;
            }
            case 10: {
                this.irqreload = this.irqreload & 0xF0FF | (n2 & 0xF) << 8;
                break;
            }
            case 11: {
                this.irqreload = this.irqreload & 0xFFF | (n2 & 0xF) << 12;
                break;
            }
            case 12: {
                this.irqmode = (n2 & 4) != 0;
                this.irqackenable = (n2 & 1) != 0;
                boolean bl = this.irqenable = (n2 & 2) != 0;
                if (!this.irqenable) break;
                if (this.irqmode) {
                    this.irqctr &= 0xFF00;
                    this.irqctr |= this.irqreload & 0xFF;
                } else {
                    this.irqctr = this.irqreload;
                }
                if (!this.interrupted) break;
                --this.cpu.interrupt;
                this.interrupted = false;
                break;
            }
            case 13: {
                this.irqenable = this.irqackenable;
                if (!this.interrupted) break;
                --this.cpu.interrupt;
                this.interrupted = false;
                break;
            }
            case 15: {
                for (int i = 0; i < 16; ++i) {
                    this.prg_map[i] = 1024 * (i + 16 * (n2 & 0xF)) & this.prgsize - 1;
                }
                break;
            }
        }
    }

    @Override
    public void cpucycle(int n) {
        if (this.irqenable) {
            if (this.irqmode) {
                int n2 = this.irqctr;
                this.irqctr &= 0xFF00;
                if (n2 >= 255) {
                    this.irqctr = this.irqreload;
                    this.irqctr |= this.irqreload & 0xFF;
                    if (!this.interrupted) {
                        ++this.cpu.interrupt;
                        this.interrupted = true;
                    }
                } else {
                    this.irqctr |= (n2 += n);
                }
            } else if (this.irqctr >= 65535) {
                this.irqctr = this.irqreload;
                if (!this.interrupted) {
                    ++this.cpu.interrupt;
                    this.interrupted = true;
                }
            } else {
                this.irqctr += n;
            }
        }
    }
}

