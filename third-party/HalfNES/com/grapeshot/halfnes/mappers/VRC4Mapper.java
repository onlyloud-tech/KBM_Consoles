/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;
import com.grapeshot.halfnes.utils;

public class VRC4Mapper
extends Mapper {
    int[][][] registerselectbits = new int[][][]{new int[][]{{1, 2}, {6, 7}}, new int[][]{{2, 3}, {0, 1}}, new int[][]{{3, 2}, {1, 0}}};
    int[][] registers;
    int prgbank0;
    int prgbank1 = 0;
    int[] chrbank = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
    boolean prgmode;
    boolean irqmode;
    boolean irqenable;
    boolean irqack;
    boolean firedinterrupt = false;
    int irqreload;
    int irqcounter = 22;
    boolean vrc2mirror;
    int prescaler = 341;

    public VRC4Mapper(int n) {
        switch (n) {
            case 21: {
                this.registers = this.registerselectbits[0];
                break;
            }
            case 23: {
                this.registers = this.registerselectbits[1];
                break;
            }
            case 25: {
                this.registers = this.registerselectbits[2];
                break;
            }
            default: {
                this.registers = this.registerselectbits[0];
            }
        }
    }

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
        System.err.println(utils.hex(this.crc));
        this.vrc2mirror = this.crc == 3079733068L || this.crc == 764755005L || this.crc == 1686212549L || this.crc == 504541066L || this.crc == 880867291L;
        System.err.println(this.vrc2mirror);
    }

    @Override
    public final void cartWrite(int n, int n2) {
        if (n < 32768 || n > 65535) {
            super.cartWrite(n, n2);
            return;
        }
        boolean bl = (n & 1 << this.registers[0][0]) != 0 || (n & 1 << this.registers[1][0]) != 0;
        boolean bl2 = (n & 1 << this.registers[0][1]) != 0 || (n & 1 << this.registers[1][1]) != 0;
        switch (n >> 12) {
            case 8: {
                this.prgbank0 = n2 & 0x1F;
                break;
            }
            case 9: {
                if (!bl2) {
                    if (this.vrc2mirror) {
                        switch (n2 & 1) {
                            case 0: {
                                this.setmirroring(Mapper.MirrorType.V_MIRROR);
                                break;
                            }
                            case 1: {
                                this.setmirroring(Mapper.MirrorType.H_MIRROR);
                            }
                        }
                        break;
                    }
                    switch (n2 & 3) {
                        case 0: {
                            this.setmirroring(Mapper.MirrorType.V_MIRROR);
                            break;
                        }
                        case 1: {
                            this.setmirroring(Mapper.MirrorType.H_MIRROR);
                            break;
                        }
                        case 2: {
                            this.setmirroring(Mapper.MirrorType.SS_MIRROR0);
                            break;
                        }
                        case 3: {
                            this.setmirroring(Mapper.MirrorType.SS_MIRROR1);
                        }
                    }
                    break;
                }
                this.prgmode = (n2 & 2) != 0;
                break;
            }
            case 10: {
                this.prgbank1 = n2 & 0x1F;
                break;
            }
            case 11: 
            case 12: 
            case 13: 
            case 14: {
                n2 &= 0xF;
                int n3 = (n - 45056 >> 11) + (bl2 ? 1 : 0);
                int n4 = this.chrbank[n3];
                if (!bl) {
                    n4 &= 0xF0;
                    n4 |= n2;
                } else {
                    n4 &= 0xF;
                    n4 |= n2 << 4;
                }
                this.chrbank[n3] = n4;
                break;
            }
            case 15: {
                if (!bl2) {
                    if (!bl) {
                        this.irqreload &= 0xF0;
                        this.irqreload |= n2 & 0xF;
                        break;
                    }
                    this.irqreload &= 0xF;
                    this.irqreload |= (n2 & 0xF) << 4;
                    break;
                }
                if (!bl) {
                    this.irqack = (n2 & 1) != 0;
                    this.irqenable = (n2 & 2) != 0;
                    boolean bl3 = this.irqmode = (n2 & 4) != 0;
                    if (this.irqenable) {
                        this.irqcounter = this.irqreload;
                        this.prescaler = 341;
                    }
                    if (this.firedinterrupt) {
                        --this.cpu.interrupt;
                    }
                    this.firedinterrupt = false;
                    break;
                }
                this.irqenable = this.irqack;
                if (this.firedinterrupt) {
                    --this.cpu.interrupt;
                }
                this.firedinterrupt = false;
            }
        }
        if (n < 61440) {
            this.setbanks();
        }
    }

    private void setbanks() {
        int n;
        if (!this.prgmode) {
            for (n = 1; n <= 16; ++n) {
                this.prg_map[32 - n] = this.prgsize - 1024 * n;
            }
            for (n = 0; n < 8; ++n) {
                this.prg_map[n] = 1024 * (n + 8 * this.prgbank0) % this.prgsize;
            }
            for (n = 0; n < 8; ++n) {
                this.prg_map[n + 8] = 1024 * (n + 8 * this.prgbank1) % this.prgsize;
            }
        } else {
            for (n = 1; n <= 8; ++n) {
                this.prg_map[8 - n] = this.prgsize - 1024 * n;
            }
            for (n = 1; n <= 8; ++n) {
                this.prg_map[32 - n] = this.prgsize - 1024 * n;
            }
            for (n = 0; n < 8; ++n) {
                this.prg_map[n + 8] = 1024 * (n + 8 * this.prgbank0) % this.prgsize;
            }
            for (n = 0; n < 8; ++n) {
                this.prg_map[n + 16] = 1024 * (n + 8 * this.prgbank1) % this.prgsize;
            }
        }
        for (n = 0; n < 8; ++n) {
            this.setppubank(1, n, this.chrbank[n]);
        }
    }

    private void setppubank(int n, int n2, int n3) {
        for (int i = 0; i < n; ++i) {
            this.chr_map[i + n2] = 1024 * (n3 + i) % this.chrsize;
        }
    }

    @Override
    public void cpucycle(int n) {
        if (this.irqenable) {
            if (this.irqmode) {
                this.scanlinecount();
            } else {
                this.prescaler -= 3;
                if (this.prescaler <= 0) {
                    this.prescaler += 341;
                    this.scanlinecount();
                }
            }
        }
    }

    public void scanlinecount() {
        if (this.irqenable) {
            if (this.irqcounter == 255) {
                this.irqcounter = this.irqreload;
                if (!this.firedinterrupt) {
                    ++this.cpu.interrupt;
                }
                this.firedinterrupt = true;
            } else {
                ++this.irqcounter;
            }
        }
    }
}

