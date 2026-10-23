/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;
import com.grapeshot.halfnes.utils;

public class Mapper31
extends Mapper {
    public boolean nsfBanking;
    public int[] nsfBanks = new int[]{0, 0, 0, 0, 0, 0, 0, 255};

    @Override
    public void loadrom() throws BadMapperException {
        super.loadrom();
        this.setBanks();
    }

    @Override
    public void cartWrite(int n, int n2) {
        if (n >= 24576 && n < 32768) {
            this.prgram[n & 0x1FFF] = n2;
        } else if (n >= 20480 && n < 24576) {
            this.nsfBanks[n & 7] = n2;
            this.setBanks();
        } else {
            System.err.println("write to " + utils.hex(n) + " goes nowhere");
        }
    }

    @Override
    public int cartRead(int n) {
        if (n >= 32768) {
            int n2 = this.prg_map[(n & Short.MAX_VALUE) >> 10] + (n & 0x3FF);
            return this.prg[n2];
        }
        if (n >= 24576 && this.hasprgram) {
            return this.prgram[n & 0x1FFF];
        }
        if (n >= 20480) {
            return this.nsfBanks[n & 7];
        }
        System.err.println("reading open bus " + utils.hex(n));
        return n >> 8;
    }

    private void setBanks() {
        for (int i = 0; i < this.prg_map.length; ++i) {
            this.prg_map[i] = 4096 * this.nsfBanks[i / 4] + 1024 * (i % 4);
            if (this.prg_map[i] < this.prg.length) continue;
            int n = i;
            this.prg_map[n] = this.prg_map[n] & this.prgsize - 1;
        }
    }
}

