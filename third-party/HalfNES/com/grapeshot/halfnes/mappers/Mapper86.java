/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.mappers;

import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;

public class Mapper86
extends Mapper {
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
    }

    @Override
    public final void cartWrite(int n, int n2) {
        if (n >= 24576 && n <= 28671) {
            int n3;
            int n4 = n2 >> 4 & 3;
            int n5 = n2 & 3 | (n2 & 0x40) >> 4;
            for (n3 = 0; n3 < 8; ++n3) {
                this.chr_map[n3] = 1024 * (n3 + 8 * n5) & this.chrsize - 1;
            }
            for (n3 = 0; n3 < 32; ++n3) {
                this.prg_map[n3] = 1024 * (n3 + 32 * n4) & this.prgsize - 1;
            }
        } else if (n >= 28672 && n <= Short.MAX_VALUE) {
            if ((n2 & 0x30) != 32) {
                return;
            }
            switch (n2 & 0x1F) {
                case 0: {
                    System.out.println("\"Strike!\"");
                    break;
                }
                case 1: {
                    System.out.println("\"Ball!\"");
                    break;
                }
                case 2: {
                    System.out.println("\"Time!\"");
                    break;
                }
                case 3: {
                    System.out.println("\"Out!\"");
                    break;
                }
                case 4: {
                    System.out.println("\"Safe!\"");
                    break;
                }
                case 5: {
                    System.out.println("\"Foul!\"");
                    break;
                }
                case 6: {
                    System.out.println("\"Fair!\"");
                    break;
                }
                case 7: {
                    System.out.println("\"You're out!\"");
                    break;
                }
                case 8: {
                    System.out.println("\"Play ball!\"");
                    break;
                }
                case 9: {
                    System.out.println("\"Ball 4!\"");
                    break;
                }
                case 10: {
                    System.out.println("\"Home run!\"");
                    break;
                }
                case 11: {
                    System.out.println("\"New pitcher\"");
                    break;
                }
                case 12: {
                    System.out.println("\"Ouch!\"");
                    break;
                }
                case 13: {
                    System.out.println("\"Dummy!\"");
                    break;
                }
                case 14: {
                    System.out.println("*crack*");
                    break;
                }
                case 15: {
                    System.out.println("*cheer*");
                }
            }
        }
    }
}

