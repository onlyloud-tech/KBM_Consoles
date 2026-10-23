/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class IremH3001Mapper
extends Mapper {
    private int[] chrbank = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
    private int irqctr;
    private int irqreload = 0;
    private boolean irqenable;
    private boolean interrupted = false;

    @Override
    public void loadrom() throws BadMapperException {
        int n;
        super.loadrom();
        for (n = 1; n <= 32; ++n) {
            this.prg_map[32 - n] = this.prgsize - 1024 * n;
        }
        for (n = 0; n < 8; ++n) {
            this.chr_map[n] = 1024 * n & this.chrsize - 1;
        }
    }

    @Override
    public final void cartWrite(int n, int n2) {
        block14: {
            block17: {
                block16: {
                    block15: {
                        block13: {
                            if (n < 32768 || n > 53247) {
                                super.cartWrite(n, n2);
                                return;
                            }
                            if (n < 32768 || n > 36863) break block13;
                            for (int i = 0; i < 8; ++i) {
                                this.prg_map[i] = 1024 * (i + n2 * 8) & this.prgsize - 1;
                            }
                            break block14;
                        }
                        if (n != 36865) break block15;
                        this.setmirroring((n2 & 0x80) != 0 ? Mapper.MirrorType.H_MIRROR : Mapper.MirrorType.V_MIRROR);
                        break block14;
                    }
                    if (n != 36867) break block16;
                    boolean bl = this.irqenable = (n2 & 0x80) != 0;
                    if (!this.interrupted) break block14;
                    --this.cpu.interrupt;
                    this.interrupted = false;
                    break block14;
                }
                if (n != 36868) break block17;
                this.irqctr = this.irqreload;
                if (!this.interrupted) break block14;
                --this.cpu.interrupt;
                this.interrupted = false;
                break block14;
            }
            if (n == 36869) {
                this.irqreload = this.irqreload & 0xFF | n2 << 8;
            } else if (n == 36870) {
                this.irqreload = this.irqreload & 0xFF00 | n2;
            } else if (n >= 40960 && n <= 45055) {
                for (int i = 0; i < 8; ++i) {
                    this.prg_map[i + 8] = 1024 * (i + n2 * 8) & this.prgsize - 1;
                }
            } else if (n >= 45056 && n <= 49151) {
                this.chrbank[n & 7] = n2;
                this.setppubank(1, n & 7, this.chrbank[n & 7]);
            } else if (n >= 49152 && n <= 53247) {
                for (int i = 0; i < 8; ++i) {
                    this.prg_map[i + 16] = 1024 * (i + n2 * 8) & this.prgsize - 1;
                }
            }
        }
    }

    @Override
    public void cpucycle(int n) {
        if (this.irqenable) {
            if (this.irqctr <= 0) {
                if (!this.interrupted) {
                    ++this.cpu.interrupt;
                    this.interrupted = true;
                }
                this.irqenable = false;
            } else {
                this.irqctr -= n;
            }
        }
    }

    private void setppubank(int n, int n2, int n3) {
        for (int i = 0; i < n; ++i) {
            this.chr_map[i + n2] = 1024 * (n3 + i) & this.chrsize - 1;
        }
    }
}

