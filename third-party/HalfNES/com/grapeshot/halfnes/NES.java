/*
 * Decompiled with CFR 0.152.
 */
package com.grapeshot.halfnes;

import com.grapeshot.halfnes.APU;
import com.grapeshot.halfnes.CPU;
import com.grapeshot.halfnes.CPURAM;
import com.grapeshot.halfnes.FileUtils;
import com.grapeshot.halfnes.PPU;
import com.grapeshot.halfnes.ProjectInfo;
import com.grapeshot.halfnes.ROMLoader;
import com.grapeshot.halfnes.cheats.ActionReplay;
import com.grapeshot.halfnes.mappers.BadMapperException;
import com.grapeshot.halfnes.mappers.Mapper;
import com.grapeshot.halfnes.ui.ControllerInterface;
import com.grapeshot.halfnes.ui.FrameLimiterImpl;
import com.grapeshot.halfnes.ui.FrameLimiterInterface;
import com.grapeshot.halfnes.ui.GUIInterface;

public class NES
implements ProjectInfo {
    private Mapper mapper;
    private APU apu;
    private CPU cpu;
    private CPURAM cpuram;
    private PPU ppu;
    private GUIInterface gui;
    private ControllerInterface controller1;
    private ControllerInterface controller2;
    public boolean runEmulation = false;
    private boolean dontSleep = false;
    private boolean shutdown = false;
    public long frameStartTime;
    public long framecount;
    public long frameDoneTime;
    private boolean frameLimiterOn = true;
    private String curRomPath;
    private String curRomName;
    private final FrameLimiterInterface limiter = new FrameLimiterImpl(this, 16639267L);
    private ActionReplay actionReplay;

    public NES(GUIInterface gUIInterface) {
        if (gUIInterface != null) {
            this.gui = gUIInterface;
            gUIInterface.setNES(this);
            gUIInterface.run();
        }
    }

    public CPURAM getCPURAM() {
        return this.cpuram;
    }

    public CPU getCPU() {
        return this.cpu;
    }

    public void run(String string) {
        Thread.currentThread().setPriority(6);
        this.curRomPath = string;
        this.gui.loadROMs(string);
        this.run();
    }

    public void run() {
        while (!this.shutdown) {
            if (this.runEmulation) {
                this.frameStartTime = System.nanoTime();
                this.actionReplay.applyPatches();
                this.runframe();
                if (this.frameLimiterOn && !this.dontSleep) {
                    this.limiter.sleep();
                }
                this.frameDoneTime = System.nanoTime() - this.frameStartTime;
                continue;
            }
            this.limiter.sleepFixed();
            if (this.ppu == null || this.framecount <= 1L) continue;
            this.gui.render();
        }
    }

    private synchronized void runframe() {
        this.ppu.runFrame();
        this.dontSleep = this.apu.bufferHasLessThan(1000);
        this.apu.finishframe();
        this.cpu.modcycles();
        this.ppu.renderFrame(this.gui);
        if ((this.framecount & 0x7FFL) == 0L) {
            this.saveSRAM(true);
        }
        ++this.framecount;
    }

    public void setControllers(ControllerInterface controllerInterface, ControllerInterface controllerInterface2) {
        this.controller1 = controllerInterface;
        this.controller2 = controllerInterface2;
    }

    public void toggleFrameLimiter() {
        this.frameLimiterOn = !this.frameLimiterOn;
    }

    public synchronized void loadROM(String string) {
        this.loadROM(string, null);
    }

    public synchronized void loadROM(String string, Integer n) {
        this.runEmulation = false;
        if (FileUtils.exists(string) && (FileUtils.getExtension(string).equalsIgnoreCase(".nes") || FileUtils.getExtension(string).equalsIgnoreCase(".nsf"))) {
            Mapper mapper;
            try {
                ROMLoader rOMLoader = new ROMLoader(string);
                rOMLoader.parseHeader();
                mapper = Mapper.getCorrectMapper(rOMLoader);
                mapper.setLoader(rOMLoader);
                mapper.loadrom();
            }
            catch (BadMapperException badMapperException) {
                this.gui.messageBox("Error Loading File: ROM is corrupted or uses an unsupported mapper.\n" + badMapperException.getMessage());
                return;
            }
            catch (Exception exception) {
                this.gui.messageBox("Error Loading File: ROM is corrupted or uses an unsupported mapper.\n" + exception.toString() + exception.getMessage());
                exception.printStackTrace();
                return;
            }
            if (this.apu != null) {
                this.apu.destroy();
                this.saveSRAM(false);
                this.mapper.destroy();
                this.cpu = null;
                this.cpuram = null;
                this.ppu = null;
            }
            this.mapper = mapper;
            this.cpuram = this.mapper.getCPURAM();
            this.actionReplay = new ActionReplay(this.cpuram);
            this.cpu = this.mapper.cpu;
            this.ppu = this.mapper.ppu;
            this.apu = new APU(this, this.cpu, this.cpuram);
            this.cpuram.setAPU(this.apu);
            this.cpuram.setPPU(this.ppu);
            this.curRomPath = string;
            this.curRomName = FileUtils.getFilenamefromPath(string);
            this.framecount = 0L;
            if (this.mapper.hasSRAM()) {
                this.loadSRAM();
            }
            this.cpu.init(n);
            this.mapper.init();
            this.setParameters();
            this.runEmulation = true;
        } else {
            this.gui.messageBox("Could not load file:\nFile " + string + "\ndoes not exist or is not a valid NES game.");
        }
    }

    private void saveSRAM(boolean bl) {
        if (this.mapper != null && this.mapper.hasSRAM() && this.mapper.supportsSaves()) {
            if (bl) {
                FileUtils.asyncwritetofile(this.mapper.getPRGRam(), FileUtils.stripExtension(this.curRomPath) + ".sav");
            } else {
                FileUtils.writetofile(this.mapper.getPRGRam(), FileUtils.stripExtension(this.curRomPath) + ".sav");
            }
        }
    }

    private void loadSRAM() {
        String string = FileUtils.stripExtension(this.curRomPath) + ".sav";
        if (FileUtils.exists(string) && this.mapper.supportsSaves()) {
            this.mapper.setPRGRAM(FileUtils.readfromfile(string));
        }
    }

    public void quit() {
        if (this.cpu != null && this.curRomPath != null) {
            this.runEmulation = false;
            this.saveSRAM(false);
        }
        this.shutdown = true;
    }

    public synchronized void reset() {
        if (this.cpu != null) {
            this.mapper.reset();
            this.cpu.reset();
            this.runEmulation = true;
            this.apu.pause();
            this.apu.resume();
        }
        this.framecount = 0L;
    }

    public synchronized void reloadROM() {
        this.loadROM(this.curRomPath);
    }

    public synchronized void pause() {
        if (this.apu != null) {
            this.apu.pause();
        }
        this.runEmulation = false;
    }

    public long getFrameTime() {
        return this.frameDoneTime;
    }

    public String getrominfo() {
        if (this.mapper != null) {
            return this.mapper.getrominfo();
        }
        return null;
    }

    public synchronized void frameAdvance() {
        this.runEmulation = false;
        if (this.cpu != null) {
            this.runframe();
        }
    }

    public synchronized void resume() {
        if (this.apu != null) {
            this.apu.resume();
        }
        if (this.cpu != null) {
            this.runEmulation = true;
        }
    }

    public String getCurrentRomName() {
        return this.curRomName;
    }

    public boolean isFrameLimiterOn() {
        return this.frameLimiterOn;
    }

    public void messageBox(String string) {
        if (this.gui != null) {
            this.gui.messageBox(string);
        }
    }

    public ControllerInterface getcontroller1() {
        return this.controller1;
    }

    public ControllerInterface getcontroller2() {
        return this.controller2;
    }

    public synchronized void setParameters() {
        if (this.apu != null) {
            this.apu.setParameters();
        }
        if (this.ppu != null) {
            this.ppu.setParameters();
        }
        if (this.limiter != null && this.mapper != null) {
            switch (this.mapper.getTVType()) {
                default: {
                    this.limiter.setInterval(16639267L);
                    break;
                }
                case PAL: 
                case DENDY: {
                    this.limiter.setInterval(19997200L);
                }
            }
        }
    }

    public synchronized ActionReplay getActionReplay() {
        return this.actionReplay;
    }
}

