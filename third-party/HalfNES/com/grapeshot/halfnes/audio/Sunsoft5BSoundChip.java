/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.audio;

import com.grapeshot.halfnes.audio.ExpansionSoundChip;
import com.grapeshot.halfnes.audio.SquareTimer;
import com.grapeshot.halfnes.audio.Timer;

public class Sunsoft5BSoundChip
implements ExpansionSoundChip {
    private final Timer[] timers = new Timer[]{new SquareTimer(32), new SquareTimer(32), new SquareTimer(32)};
    private final boolean[] enable = new boolean[]{false, false, false};
    private final boolean[] useenvelope = new boolean[]{false, false, false};
    private final int[] volume = new int[]{0, 0, 0};
    int enval = 0;
    private static final int[] VOLTBL = Sunsoft5BSoundChip.getvoltbl();

    @Override
    public final void write(int n, int n2) {
        switch (n) {
            case 0: {
                this.timers[0].setperiod((this.timers[0].getperiod() & 0xF00) + n2);
                break;
            }
            case 1: {
                this.timers[0].setperiod((this.timers[0].getperiod() & 0xFF) + ((n2 & 0xF) << 8));
                break;
            }
            case 2: {
                this.timers[1].setperiod((this.timers[1].getperiod() & 0xF00) + n2);
                break;
            }
            case 3: {
                this.timers[1].setperiod((this.timers[1].getperiod() & 0xFF) + ((n2 & 0xF) << 8));
                break;
            }
            case 4: {
                this.timers[2].setperiod((this.timers[2].getperiod() & 0xF00) + n2);
                break;
            }
            case 5: {
                this.timers[2].setperiod((this.timers[2].getperiod() & 0xFF) + ((n2 & 0xF) << 8));
                break;
            }
            case 7: {
                for (int i = 0; i < 3; ++i) {
                    this.enable[i] = (n2 & 1 << i) == 0;
                }
                break;
            }
            case 8: {
                this.volume[0] = n2 & 0xF;
                this.useenvelope[0] = (n2 & 0x10) != 0;
                break;
            }
            case 9: {
                this.volume[1] = n2 & 0xF;
                this.useenvelope[1] = (n2 & 0x10) != 0;
                break;
            }
            case 10: {
                this.volume[2] = n2 & 0xF;
                this.useenvelope[2] = (n2 & 0x10) != 0;
                break;
            }
            case 13: {
                this.enval = 15;
                break;
            }
        }
    }

    @Override
    public final void clock(int n) {
        this.clockenvelope(n);
        this.timers[0].clock(n);
        this.timers[1].clock(n);
        this.timers[2].clock(n);
    }

    @Override
    public final int getval() {
        return (this.enable[0] ? (this.useenvelope[0] ? this.enval : VOLTBL[this.volume[0]]) * this.timers[0].getval() : 0) + (this.enable[1] ? (this.useenvelope[1] ? this.enval : VOLTBL[this.volume[1]]) * this.timers[1].getval() : 0) + (this.enable[2] ? (this.useenvelope[2] ? this.enval : VOLTBL[this.volume[2]]) * this.timers[2].getval() : 0);
    }

    public static int[] getvoltbl() {
        int[] nArray = new int[16];
        for (int i = 0; i < 16; ++i) {
            nArray[i] = (int)(90.0 * Math.pow(1.4, i));
        }
        return nArray;
    }

    private void clockenvelope(int n) {
        this.enval = 0;
    }
}

