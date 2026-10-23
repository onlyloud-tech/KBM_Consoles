/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.audio;

import com.grapeshot.halfnes.audio.ExpansionSoundChip;
import com.grapeshot.halfnes.audio.SquareTimer;
import com.grapeshot.halfnes.audio.Timer;

public class MMC5SoundChip
implements ExpansionSoundChip {
    private final Timer[] timers = new Timer[]{new SquareTimer(8, 2), new SquareTimer(8, 2)};
    private static final int[] DUTYLOOKUP = new int[]{1, 2, 4, 6};
    private final int[] volume = new int[2];
    private final boolean[] lenCtrEnable = new boolean[]{true, true, true, true};
    private boolean pcmMode;
    private boolean pcmIRQen;
    private int cycles;
    private int pcmOut;
    private int[] lengthctr = new int[]{0, 0, 0, 0};
    private static final int[] LENCTRLOAD = new int[]{10, 254, 20, 2, 40, 4, 80, 6, 160, 8, 60, 10, 14, 12, 26, 14, 12, 16, 24, 18, 48, 20, 96, 22, 192, 24, 72, 26, 16, 28, 32, 30};
    private final boolean[] lenctrHalt = new boolean[]{true, true, true, true};
    private final int[] envelopeValue = new int[]{15, 15, 15, 15};
    private final int[] envelopeCounter = new int[]{0, 0, 0, 0};
    private final int[] envelopePos = new int[]{0, 0, 0, 0};
    private final boolean[] envConstVolume = new boolean[]{true, true, true, true};
    private final boolean[] envelopeStartFlag = new boolean[]{false, false, false, false};
    private int framectr = 0;
    private final int ctrmode = 4;

    @Override
    public final void clock(int n) {
        this.cycles += n;
        if (this.cycles % 7445 != this.cycles) {
            this.clockframecounter();
            this.cycles %= 6445;
        }
        this.timers[0].clock(n);
        this.timers[1].clock(n);
    }

    @Override
    public void write(int n, int n2) {
        switch (n) {
            case 0: {
                this.lenctrHalt[0] = (n2 & 0x20) != 0;
                this.timers[0].setduty(DUTYLOOKUP[n2 >> 6]);
                this.envConstVolume[0] = (n2 & 0x10) != 0;
                this.envelopeValue[0] = n2 & 0xF;
                break;
            }
            case 1: {
                break;
            }
            case 2: {
                this.timers[0].setperiod((this.timers[0].getperiod() & 0xFE00) + (n2 << 1));
                break;
            }
            case 3: {
                if (this.lenCtrEnable[0]) {
                    this.lengthctr[0] = LENCTRLOAD[n2 >> 3];
                }
                this.timers[0].setperiod((this.timers[0].getperiod() & 0x1FF) + ((n2 & 7) << 9));
                this.timers[0].reset();
                this.envelopeStartFlag[0] = true;
                break;
            }
            case 4: {
                this.lenctrHalt[1] = (n2 & 0x20) != 0;
                this.timers[1].setduty(DUTYLOOKUP[n2 >> 6]);
                this.envConstVolume[1] = (n2 & 0x10) != 0;
                this.envelopeValue[1] = n2 & 0xF;
                break;
            }
            case 5: {
                break;
            }
            case 6: {
                this.timers[1].setperiod((this.timers[1].getperiod() & 0xFE00) + (n2 << 1));
                break;
            }
            case 7: {
                if (this.lenCtrEnable[1]) {
                    this.lengthctr[1] = LENCTRLOAD[n2 >> 3];
                }
                this.timers[1].setperiod((this.timers[1].getperiod() & 0x1FF) + ((n2 & 7) << 9));
                this.timers[1].reset();
                this.envelopeStartFlag[1] = true;
                break;
            }
            case 16: {
                this.pcmMode = (n2 & 1) != 0;
                boolean bl = this.pcmIRQen = (n2 & 0x80) != 0;
                if (!this.pcmIRQen && !this.pcmMode) break;
                System.err.println("Implement the MMC5 PCM IRQ, something's using it!");
                break;
            }
            case 17: {
                if (this.pcmMode || n2 == 0) break;
                this.pcmOut = n2;
                break;
            }
        }
    }

    @Override
    public int getval() {
        int n = 0;
        for (int i = 0; i < 2; ++i) {
            n += this.volume[i] * this.timers[i].getval() * 750;
        }
        return n += this.pcmOut << 5;
    }

    public int status() {
        return (this.lengthctr[0] == 0 ? 0 : 1) + (this.lengthctr[1] == 0 ? 0 : 2);
    }

    private void setlength() {
        for (int i = 0; i < 4; ++i) {
            if (this.lenctrHalt[i] || this.lengthctr[i] <= 0) continue;
            int n = i;
            this.lengthctr[n] = this.lengthctr[n] - 1;
            if (this.lengthctr[i] != 0) continue;
            this.setvolumes();
        }
    }

    private void setenvelope() {
        for (int i = 0; i < 2; ++i) {
            if (this.envelopeStartFlag[i]) {
                this.envelopeStartFlag[i] = false;
                this.envelopePos[i] = this.envelopeValue[i] + 1;
                this.envelopeCounter[i] = 15;
            } else {
                int n = i;
                this.envelopePos[n] = this.envelopePos[n] - 1;
            }
            if (this.envelopePos[i] > 0) continue;
            this.envelopePos[i] = this.envelopeValue[i] + 1;
            if (this.envelopeCounter[i] > 0) {
                int n = i;
                this.envelopeCounter[n] = this.envelopeCounter[n] - 1;
                continue;
            }
            if (!this.lenctrHalt[i] || this.envelopeCounter[i] > 0) continue;
            this.envelopeCounter[i] = 15;
        }
    }

    private void setvolumes() {
        int n = this.lengthctr[0] <= 0 ? 0 : (this.volume[0] = this.envConstVolume[0] ? this.envelopeValue[0] : this.envelopeCounter[0]);
        this.volume[1] = this.lengthctr[1] <= 0 ? 0 : (this.envConstVolume[1] ? this.envelopeValue[1] : this.envelopeCounter[1]);
    }

    private void clockframecounter() {
        if (this.framectr < 4) {
            this.setenvelope();
        }
        if (this.framectr == 1 || this.framectr == 3) {
            this.setlength();
        }
        ++this.framectr;
        this.framectr %= 4;
        this.setvolumes();
    }
}

