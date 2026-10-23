/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.cheats;

public class Patch {
    private final int type;
    private final int address;
    private final int data;
    private final int cmpData;

    public Patch(int n, int n2) {
        this.address = n;
        this.data = n2;
        this.type = 0;
        this.cmpData = 0;
    }

    public Patch(int n, int n2, int n3) {
        this.address = n;
        this.data = n2;
        this.type = 1;
        this.cmpData = n3;
    }

    public int getAddress() {
        return this.address;
    }

    public int getData() {
        return this.data;
    }

    public boolean matchesData(int n) {
        return this.type == 0 || n == this.cmpData;
    }

    public int hashCode() {
        int n = 5;
        n = 71 * n + this.address;
        n = 71 * n + this.data;
        return n;
    }

    public boolean equals(Object object) {
        if (object == null) {
            return false;
        }
        if (this.getClass() != object.getClass()) {
            return false;
        }
        Patch patch = (Patch)object;
        if (this.address != patch.address) {
            return false;
        }
        return this.data == patch.data;
    }

    public String toString() {
        int n;
        String string = Integer.toHexString(this.address);
        String string2 = Integer.toHexString(this.data & 0xFF);
        StringBuilder stringBuilder = new StringBuilder();
        for (n = 0; n < 6 - string.length(); ++n) {
            stringBuilder.append("0");
        }
        stringBuilder.append(string);
        for (n = 0; n < 2 - string2.length(); ++n) {
            stringBuilder.append("0");
        }
        stringBuilder.append(string2);
        return stringBuilder.toString().toUpperCase();
    }
}

