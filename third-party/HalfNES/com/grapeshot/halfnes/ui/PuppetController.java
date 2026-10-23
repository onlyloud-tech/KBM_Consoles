/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.ui;

import com.grapeshot.halfnes.ui.ControllerInterface;

public class PuppetController
implements ControllerInterface {
    private int latchbyte = 0;
    private int controllerbyte = 0;
    private int outbyte = 0;

    @Override
    public void strobe() {
        this.outbyte = this.latchbyte & 1;
        this.latchbyte = this.latchbyte >> 1 | 0x100;
    }

    @Override
    public void output(boolean bl) {
        this.latchbyte = this.controllerbyte;
    }

    @Override
    public int peekOutput() {
        return this.latchbyte;
    }

    @Override
    public int getbyte() {
        return this.outbyte;
    }

    public void resetButtons() {
        this.controllerbyte = 0;
    }

    public void releaseButton(Button button) {
        switch (button) {
            case UP: {
                this.controllerbyte &= 0xFFFFFFEF;
                break;
            }
            case DOWN: {
                this.controllerbyte &= 0xFFFFFFDF;
                break;
            }
            case LEFT: {
                this.controllerbyte &= 0xFFFFFFBF;
                break;
            }
            case RIGHT: {
                this.controllerbyte &= 0xFFFFFF7F;
                break;
            }
            case A: {
                this.controllerbyte &= 0xFFFFFFFE;
                break;
            }
            case B: {
                this.controllerbyte &= 0xFFFFFFFD;
                break;
            }
            case SELECT: {
                this.controllerbyte &= 0xFFFFFFFB;
                break;
            }
            case START: {
                this.controllerbyte &= 0xFFFFFFF7;
            }
        }
    }

    public void pressButton(Button button) {
        switch (button) {
            case UP: {
                this.controllerbyte |= 0x10;
                break;
            }
            case DOWN: {
                this.controllerbyte |= 0x20;
                break;
            }
            case LEFT: {
                this.controllerbyte |= 0x40;
                break;
            }
            case RIGHT: {
                this.controllerbyte |= 0x80;
                break;
            }
            case A: {
                this.controllerbyte |= 1;
                break;
            }
            case B: {
                this.controllerbyte |= 2;
                break;
            }
            case SELECT: {
                this.controllerbyte |= 4;
                break;
            }
            case START: {
                this.controllerbyte |= 8;
            }
        }
    }

    public static enum Button {
        UP,
        DOWN,
        LEFT,
        RIGHT,
        A,
        B,
        SELECT,
        START;

    }
}

