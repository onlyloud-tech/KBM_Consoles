/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes;

import com.grapeshot.halfnes.APU;
import com.grapeshot.halfnes.PPU;
import com.grapeshot.halfnes.cheats.Patch;
import com.grapeshot.halfnes.mappers.Mapper;
import java.util.Arrays;
import java.util.HashMap;

public class CPURAM {
    private final int[] wram = new int[2048];
    Mapper mapper;
    public APU apu;
    PPU ppu;
    private HashMap<Integer, Patch> patches = new HashMap();

    public CPURAM(Mapper mapper) {
        this.mapper = mapper;
        Arrays.fill(this.wram, 255);
    }

    public final int read(int n) {
        if (!this.patches.isEmpty()) {
            int n2 = this._read(n);
            Patch patch = this.patches.get(n);
            if (patch != null && patch.getAddress() == n && patch.matchesData(n2)) {
                return patch.getData();
            }
            return n2;
        }
        return this._read(n);
    }

    public final int _read(int n) {
        if (n > 16408) {
            return this.mapper.cartRead(n);
        }
        if (n <= 8191) {
            return this.wram[n & 0x7FF];
        }
        if (n <= 16383) {
            return this.ppu.read(n & 7);
        }
        if (16384 <= n && n <= 16408) {
            return this.apu.read(n - 16384);
        }
        return n >> 8;
    }

    public final void write(int n, int n2) {
        if (n > 16408) {
            this.mapper.cartWrite(n, n2);
        } else if (n <= 8191) {
            this.wram[n & 0x7FF] = n2;
        } else if (n <= 16383) {
            this.ppu.write(n & 7, n2);
        } else if (16384 <= n && n <= 16408) {
            this.apu.write(n - 16384, n2);
        }
    }

    public void setAPU(APU aPU) {
        this.apu = aPU;
    }

    public void setPPU(PPU pPU) {
        this.ppu = pPU;
    }

    public void setPatches(HashMap<Integer, Patch> hashMap) {
        this.patches = hashMap;
    }
}

