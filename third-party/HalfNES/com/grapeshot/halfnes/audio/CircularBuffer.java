/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes.audio;

public class CircularBuffer {
    int[] buffer;
    int bufsize;
    int write_ptr;
    int read_ptr;

    public CircularBuffer(int n) {
        for (int i = 0; i < 26; ++i) {
            int n2 = (int)Math.pow(2.0, i);
            if (n2 <= n) continue;
            this.buffer = new int[n2];
            this.bufsize = n2;
            break;
        }
        if (this.buffer == null) {
            throw new UnsupportedOperationException("Buffer too large");
        }
        this.write_ptr = n - 2;
        this.read_ptr = 0;
    }

    public void write(int n) {
        this.buffer[this.write_ptr] = n;
        ++this.write_ptr;
        this.write_ptr &= this.bufsize - 1;
    }

    public int read() {
        int n = this.buffer[this.read_ptr];
        ++this.read_ptr;
        this.read_ptr &= this.bufsize - 1;
        return n;
    }

    public void advanceRead(int n) {
        this.read_ptr = this.read_ptr + n & this.bufsize - 1;
    }

    public void advanceWrite(int n) {
        this.write_ptr = this.write_ptr + n & this.bufsize - 1;
    }

    public int peek() {
        return this.buffer[this.read_ptr];
    }
}

