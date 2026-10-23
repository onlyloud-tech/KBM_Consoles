/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class MMC1Mapper
extends Mapper {
    private int mmc1shift = 0;
    private int mmc1latch = 0;
    private int mmc1ctrl = 12;
    private int mmc1chr0 = 0;
    private int mmc1chr1 = 0;
    private int mmc1prg = 0;
    private boolean soromlatch = false;
    private double cpucycleprev = 0.0;
    private long framecountprev = 0L;

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
        this.setbanks();
    }

    @Override
    public final void cartWrite(int n, int n2) {
        if (n < 32768 || n > 65535) {
            super.cartWrite(n, n2);
            return;
        }
        if ((double)this.cpu.clocks == this.cpucycleprev && this.cpuram.apu.nes.framecount == this.framecountprev) {
            return;
        }
        this.framecountprev = this.cpuram.apu.nes.framecount;
        this.cpucycleprev = this.cpu.clocks;
        if ((n2 & 0x80) != 0) {
            this.mmc1shift = 0;
            this.mmc1latch = 0;
            this.mmc1ctrl |= 0xC;
            this.setbanks();
            return;
        }
        this.mmc1shift = (this.mmc1shift >> 1) + (n2 & 1) * 16;
        ++this.mmc1latch;
        if (this.mmc1latch < 5) {
            return;
        }
        if (n >= 32768 && n <= 40959) {
            this.mmc1ctrl = this.mmc1shift & 0x1F;
            this.setmirroring(switch (this.mmc1ctrl & 3) {
                case 0 -> Mapper.MirrorType.SS_MIRROR0;
                case 1 -> Mapper.MirrorType.SS_MIRROR1;
                case 2 -> Mapper.MirrorType.V_MIRROR;
                default -> Mapper.MirrorType.H_MIRROR;
            });
        } else if (n >= 40960 && n <= 49151) {
            this.mmc1chr0 = this.mmc1shift & 0x1F;
            if (this.prgsize > 262144) {
                this.mmc1chr0 &= 0xF;
                this.soromlatch = (this.mmc1shift & 0x10) != 0;
            }
        } else if (n >= 49152 && n <= 57343) {
            this.mmc1chr1 = this.mmc1shift & 0x1F;
            if (this.prgsize > 262144) {
                this.mmc1chr1 &= 0xF;
            }
        } else if (n >= 57344 && n <= 65535) {
            this.mmc1prg = this.mmc1shift & 0xF;
        }
        this.setbanks();
        this.mmc1latch = 0;
        this.mmc1shift = 0;
    }

    private void setbanks() {
        int n;
        if ((this.mmc1ctrl & 0x10) != 0) {
            for (n = 0; n < 4; ++n) {
                this.chr_map[n] = 1024 * (n + 4 * this.mmc1chr0) % this.chrsize;
            }
            for (n = 0; n < 4; ++n) {
                this.chr_map[n + 4] = 1024 * (n + 4 * this.mmc1chr1) % this.chrsize;
            }
        } else {
            for (n = 0; n < 8; ++n) {
                this.chr_map[n] = 1024 * (n + 8 * (this.mmc1chr0 >> 1)) % this.chrsize;
            }
        }
        if ((this.mmc1ctrl & 8) == 0) {
            for (n = 0; n < 32; ++n) {
                this.prg_map[n] = (1024 * n + 32768 * (this.mmc1prg >> 1)) % this.prgsize;
            }
        } else if ((this.mmc1ctrl & 4) == 0) {
            for (n = 0; n < 16; ++n) {
                this.prg_map[n] = 1024 * n;
            }
            for (n = 0; n < 16; ++n) {
                this.prg_map[n + 16] = (1024 * n + 16384 * this.mmc1prg) % this.prgsize;
            }
        } else {
            for (n = 0; n < 16; ++n) {
                this.prg_map[n] = (1024 * n + 16384 * this.mmc1prg) % this.prgsize;
            }
            for (n = 1; n <= 16; ++n) {
                this.prg_map[32 - n] = this.prgsize - 1024 * n;
                if (this.prg_map[32 - n] <= 262144) continue;
                int n2 = 32 - n;
                this.prg_map[n2] = this.prg_map[n2] - 262144;
            }
        }
        if (this.soromlatch && this.prgsize > 262144) {
            n = 0;
            while (n < this.prg_map.length) {
                int n3 = n++;
                this.prg_map[n3] = this.prg_map[n3] + 262144;
            }
        }
    }
}

