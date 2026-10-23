/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.audio;

import com.grapeshot.halfnes.audio.ExpansionSoundChip;
import com.grapeshot.halfnes.audio.SquareTimer;
import com.grapeshot.halfnes.audio.Timer;

public class VRC6SoundChip
implements ExpansionSoundChip {
    private final Timer[] timers = new Timer[]{new SquareTimer(16), new SquareTimer(16)};
    private final boolean[] enable = new boolean[]{true, true, true};
    private final int[] volume = new int[]{0, 0, 0};
    private int sawdivider = 15;
    private int sawctr = 0;
    private int sawaccum = 0;
    private int sawseq = 0;
    private boolean clocknow = false;

    @Override
    public final void write(int n, int n2) {
        switch (n) {
            case 36864: {
                this.volume[0] = n2 & 0xF;
                this.timers[0].setduty((n2 & 0x80) != 0 ? 16 : (n2 >> 4 & 7) + 1);
                break;
            }
            case 36865: {
                this.timers[0].setperiod((this.timers[0].getperiod() & 0xF00) + n2);
                break;
            }
            case 36866: {
                this.timers[0].setperiod((this.timers[0].getperiod() & 0xFF) + ((n2 & 0xF) << 8));
                this.enable[0] = (n2 & 0x80) != 0;
                break;
            }
            case 40960: {
                this.volume[1] = n2 & 0xF;
                this.timers[1].setduty((n2 & 0x80) != 0 ? 16 : (n2 >> 4 & 7) + 1);
                break;
            }
            case 40961: {
                this.timers[1].setperiod((this.timers[1].getperiod() & 0xF00) + n2);
                break;
            }
            case 40962: {
                this.timers[1].setperiod((this.timers[1].getperiod() & 0xFF) + ((n2 & 0xF) << 8));
                this.enable[1] = (n2 & 0x80) != 0;
                break;
            }
            case 45056: {
                this.sawaccum = n2 & 0x3F;
                break;
            }
            case 45057: {
                this.sawdivider &= 0xF00;
                this.sawdivider += n2;
                break;
            }
            case 45058: {
                this.sawdivider &= 0xFF;
                this.sawdivider += (n2 & 0xF) << 8;
                this.enable[2] = (n2 & 0x80) != 0;
            }
        }
    }

    @Override
    public final void clock(int n) {
        this.timers[0].clock(n);
        this.timers[1].clock(n);
        for (int i = 0; i < n; ++i) {
            this.clocksaw();
        }
    }

    @Override
    public final int getval() {
        return 320 * ((this.enable[0] ? this.volume[0] : 0) * this.timers[0].getval() + (this.enable[1] ? this.volume[1] : 0) * this.timers[1].getval() + (this.enable[2] ? (this.volume[2] & 0xFF) >> 3 : 0));
    }

    private void clocksaw() {
        --this.sawctr;
        if (this.sawctr < 0) {
            this.sawctr = this.sawdivider;
            if (this.clocknow) {
                this.clocknow = false;
                this.volume[2] = this.volume[2] + this.sawaccum;
                ++this.sawseq;
                if (this.sawseq > 6) {
                    this.sawseq = 0;
                    this.volume[2] = 0;
                }
            } else {
                this.clocknow = true;
            }
        }
    }
}

