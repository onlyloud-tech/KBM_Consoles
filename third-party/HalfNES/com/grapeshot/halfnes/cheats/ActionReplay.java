/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.cheats;

import com.grapeshot.halfnes.CPURAM;
import com.grapeshot.halfnes.cheats.Patch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ActionReplay {
    private static final int RAM_SIZE = 2047;
    private final CPURAM cpuram;
    private final HashMap<Integer, Patch> patches = new HashMap();
    private final List<Integer> foundAddresses = new ArrayList<Integer>();

    public ActionReplay(CPURAM cPURAM) {
        this.cpuram = cPURAM;
    }

    public HashMap<Integer, Patch> getPatches() {
        return this.patches;
    }

    public void addMemoryPatch(Patch patch) {
        if (!this.patches.containsKey(patch.getAddress())) {
            this.patches.put(patch.getAddress(), patch);
        }
    }

    public void applyPatches() {
        this.cpuram.setPatches(this.patches);
    }

    public void clear() {
        this.patches.clear();
    }

    public List<Integer> newSearchInMemory(byte by) {
        this.foundAddresses.clear();
        for (int i = 0; i < 2047; ++i) {
            if ((this.cpuram.read(i) & 0xFF) != (by & 0xFF)) continue;
            this.foundAddresses.add(i);
        }
        return this.foundAddresses;
    }

    public List<Integer> getFoundAddresses() {
        return this.foundAddresses;
    }

    public List<Integer> continueSearch(byte by) {
        ArrayList<Integer> arrayList = new ArrayList<Integer>();
        for (int n : this.foundAddresses) {
            if ((this.cpuram.read(n) & 0xFF) == (by & 0xFF)) continue;
            arrayList.add(n);
        }
        this.foundAddresses.removeAll(arrayList);
        return this.foundAddresses;
    }
}

