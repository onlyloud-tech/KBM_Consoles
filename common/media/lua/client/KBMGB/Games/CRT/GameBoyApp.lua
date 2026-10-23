-- GameBoyApp.lua â€” part of KnoxBox Media (Project Zomboid mod)
-- Copyright (C) 2026 Grymlock
-- SPDX-License-Identifier: GPL-3.0-or-later
--
-- Game Boy emulator app (Coffee GB) running via KBMGameBoy Java bridge.
--
-- PREREQUISITES:
--   1. ZombieBuddy (for @Exposer.LuaClass bridge)
--   2. media/java/KBMGB.jar (fat jar â€” Coffee GB core + KBM bridge)
--   3. Bundled homebrew ROMs, or optional local Zomboid/kbm_gb_roms files.
--
-- CONTROLS:
--   UP/DOWN/LEFT/RIGHT       - D-pad
--   SPACE / Z / J / NUMPAD2  - A button
--   LSHIFT / X / K / NUMPAD1 - B button
--   ENTER / NUMPADENTER      - Start
--   RSHIFT / BACK / NUMPAD0  - Select
--   R                        - Soft reset
--   Q                        - Toggle render quality (sharp/balanced/fast)
--   E                        - Increase display size
--   Shift+E                  - Decrease display size
--   TAB                      - Debug overlay
--   BACKSPACE / DELETE       - Close app

local GameBoyApp = {}
GameBoyApp.__index = GameBoyApp
local MediaCheck = require("KBMGB/System/MediaCheck")
local RomMediaCatalog = require("KBMGB/System/RomMediaCatalog")
local GBRomRuntime = require("KBMGB/System/GBRomRuntime")

-- Standalone sessions belong to the cartridge inserted through the radio hook.
local function cartridgeAllows(ctx, name)
    local args = ctx and ctx.args
    if type(args) ~= "table" or args.radioDevice ~= true then return false end
    if type(args.itemFullType) ~= "string" or type(name) ~= "string" then return false end
    local entry = RomMediaCatalog.getByItemType(args.itemFullType)
        or GBRomRuntime.getByItemType(args.itemFullType)
    return entry ~= nil and entry.appId == "gb"
        and type(entry.rom) == "string" and entry.rom:lower() == name:lower()
        and type(args.rom) == "string" and args.rom:lower() == name:lower()
end

GameBoyApp.meta = {
    api            = "ctx_v1",
    id             = "gb",
    name           = "Game Boy",
    author         = "Coffee GB (rekawek) / KBM wrapper",
    type           = "game",
    width          = 320,
    height         = 200,
    ticksPerSecond = 60,
}

-- ------------------------------------------------------------
-- Constants
-- ------------------------------------------------------------
-- Native GB framebuffer is 160x144. Fit inside 320x200 CRT screen.
-- 2x scale (320x288) would clip vertically; 1x leaves big margins.
-- Use srcStride=1, dstBlock=1 on "full" -> 160x144 centered with letterbox.
local GB_W, GB_H = 160, 144
local DISPLAY_W = 160
local DISPLAY_H = 144
local DISPLAY_X = math.floor((320 - DISPLAY_W) / 2)   -- 80
local DISPLAY_Y = math.floor((200 - DISPLAY_H) / 2)   -- 28
local GB_Y_SRC_START = 0     -- no overscan on GB
local GB_Y_SRC_END   = GB_H

local BLOCK_SIZE = 1
local GB_FRAME_DT = 1 / 60
-- Number of silent frames to push immediately after loadROM. This
-- pre-fills the AudioWorker's buffer so the very first user-visible
-- frame doesn't hit an empty SourceDataLine (which would underrun and
-- produce a click/pop on game start).
local GB_AUDIO_PREWARM_FRAMES = 6
-- Raised from 8 to 12 so heavier carts (CGB double-speed mode, MBC3+RTC)
-- don't get clipped by the spiral-of-death guard during normal PZ frame
-- jitter, which would chronically starve the audio buffer and pop.
local MAX_EMU_FRAMES_PER_UPDATE = 12

-- Streaming-texture blit path (experimental). When true, render() asks the
-- Java side for a zombie.core.textures.Texture seeded with the current GB
-- frame and draws it via ctx:drawTextureScaled, skipping the RLE-rect path
-- entirely (~200-500 drawRect calls per frame -> 1 textured quad).
--
-- Default OFF for safety: if the underlying Texture API isn't reachable on
-- this PZ build the renderer silently falls back to the RLE path. Toggle at
-- runtime in the debug console:
--     KBM_GB_setBlit(true)   -- enable
--     KBM_GB_setBlit(false)  -- disable
--     KBM_GB_blitStatus()    -- inspect Java-side status string
local USE_TEXTURE_BLIT = true

-- One-time log latches so we always emit a single diagnostic line per
-- session telling us which path was actually used and (if blit) what the
-- Java side reported. The GB app captures input, so the in-game console
-- isn't reachable while the device is open - the log is the only channel.
local LOGGED_BLIT_OK     = false
local LOGGED_BLIT_FALLBK = false

-- Aspect-correct viewport presets that fit inside the 320x200 CRT.
-- With getScaledRegionRLE(), viewport size is independent from quality.
-- Aspect-correct (10:9) sizes that fit inside the 320x200 CRT viewport.
-- xlarge (1.375x) is the largest scale that keeps the height inside 200px
-- without clipping; with the streaming-texture blit path we can render at
-- this size at native speed without the old RLE drawcall budget concern.
local VIEW_MODES = {
    { id = "quad",   width = 40,  height = 36  },
    { id = "half",   width = 80,  height = 72  },
    { id = "full",   width = 160, height = 144 },
    { id = "large",  width = 200, height = 180 },
    { id = "xlarge", width = 220, height = 198 },
}

local function findViewModeIndex(id)
    for i = 1, #VIEW_MODES do
        if VIEW_MODES[i].id == id then return i end
    end
    return 3 -- full
end

-- Button masks (must match the KBM Game Boy engine BTN_* constants).
local BTN_A      = 0x01
local BTN_B      = 0x02
local BTN_SELECT = 0x04
local BTN_START  = 0x08
local BTN_UP     = 0x10
local BTN_DOWN   = 0x20
local BTN_LEFT   = 0x40
local BTN_RIGHT  = 0x80

local GB_KEYMAP = {
    UP     = { Keyboard.KEY_UP },
    DOWN   = { Keyboard.KEY_DOWN },
    LEFT   = { Keyboard.KEY_LEFT },
    RIGHT  = { Keyboard.KEY_RIGHT },
    A      = { Keyboard.KEY_SPACE, Keyboard.KEY_Z, Keyboard.KEY_J,
               Keyboard.KEY_NUMPAD2 },
    B      = { Keyboard.KEY_LSHIFT, Keyboard.KEY_X, Keyboard.KEY_K,
               Keyboard.KEY_NUMPAD1 },
    START  = { Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER },
    SELECT = { Keyboard.KEY_RSHIFT, Keyboard.KEY_BACK,
               Keyboard.KEY_NUMPAD0 },
}

local KEY_R         = Keyboard.KEY_R
local KEY_TAB       = Keyboard.KEY_TAB
local KEY_ESCAPE    = Keyboard.KEY_ESCAPE
local KEY_BACK      = Keyboard.KEY_BACK
local KEY_DELETE    = Keyboard.KEY_DELETE
local KEY_UP_MENU   = Keyboard.KEY_UP
local KEY_DOWN_MENU = Keyboard.KEY_DOWN
local KEY_RETURN    = Keyboard.KEY_RETURN
local KEY_LSHIFT    = Keyboard.KEY_LSHIFT
local KEY_RSHIFT    = Keyboard.KEY_RSHIFT

-- ------------------------------------------------------------
-- Helpers
-- ------------------------------------------------------------
-- Returns the current time in milliseconds. Tries a few PZ APIs in turn
-- because not every build exposes the same one.
local function nowMs()
    if _G.getTimestampMs then
        local ok, v = pcall(_G.getTimestampMs)
        if ok and type(v) == "number" then return v end
    end
    if _G.getTimeInMillis then
        local ok, v = pcall(_G.getTimeInMillis)
        if ok and type(v) == "number" then return v end
    end
    if _G.os and type(_G.os.clock) == "function" then
        local ok, v = pcall(_G.os.clock)
        if ok and type(v) == "number" then return v * 1000 end
    end
    return 0
end

-- This app ships inside the standalone Game Boy mod.
local MOD_ID = "KBM_Consoles"

local function buildLocalRomCandidates(romName)
    return {
        "common/media/roms/gb/" .. romName,
        "42/media/roms/gb/" .. romName,
        "media/roms/gb/" .. romName,
    }
end

-- PZ fileExists accepts absolute paths. getFileReader instead prefixes the
-- user's Lua cache directory and cannot check packaged binary ROM paths.
local function fileExists(path)
    if not path or path == "" or type(_G.fileExists) ~= "function" then return false end
    local ok, exists = pcall(_G.fileExists, path)
    return ok and exists == true
end

local function findExistingUnderModDir(modDir, romName)
    if not modDir or modDir == "" then return nil end
    local sep = (modDir:find("/") and not modDir:find("\\")) and "/" or "\\"
    local base = modDir:gsub("[/\\]+$", "")
    local candidates = buildLocalRomCandidates(romName)
    for i = 1, #candidates do
        local absPath = base .. sep .. candidates[i]:gsub("/", sep)
        if fileExists(absPath) then return absPath end
    end
    return nil
end

-- Looks for a ROM file inside the mod's packaged media/roms/gb folders.
local function resolveLocalRomPath(name)
    local romName = tostring(name or "")
    if romName == "" then return nil end

    if _G.getModInfoByID then
        local okM, modInfo = pcall(_G.getModInfoByID, MOD_ID)
        if okM and modInfo then
            -- Build 42 separates the mod root, version directory and common assets.
            for _, getter in ipairs({ "getVersionDir", "getCommonDir", "getDir" }) do
                local okD, modDir = pcall(function() return modInfo[getter](modInfo) end)
                if okD and type(modDir) == "string" and modDir ~= "" then
                    local absPath = findExistingUnderModDir(modDir, romName)
                    if absPath then
                        if _G.print then print("[KBM][GameBoyApp] bundled ROM -> " .. absPath) end
                        return absPath
                    end
                end
            end
        end
    end

    local relCandidates = buildLocalRomCandidates(romName)

    if _G.getModFileByName then
        local candidates = {}
        for i = 1, #relCandidates do
            candidates[#candidates + 1] = MOD_ID .. "/" .. relCandidates[i]
            candidates[#candidates + 1] = MOD_ID .. "\\" .. relCandidates[i]:gsub("/", "\\")
            candidates[#candidates + 1] = relCandidates[i]
            candidates[#candidates + 1] = relCandidates[i]:gsub("/", "\\")
        end
        for i = 1, #candidates do
            local ok, path = pcall(_G.getModFileByName, candidates[i])
            if ok and type(path) == "string" and path ~= "" and fileExists(path) then
                if _G.print then print("[KBM][GameBoyApp] bundled ROM -> " .. path) end
                return path
            end
        end
    end

    if _G.getModFileReader then
        for i = 1, #relCandidates do
            local relPath = relCandidates[i]
            local ok, reader = pcall(_G.getModFileReader, MOD_ID, relPath, false)
            if ok and reader then
                if reader.close then pcall(function() reader:close() end) end
                if _G.getModFileByName then
                    local ok2, path = pcall(_G.getModFileByName, MOD_ID .. "/" .. relPath)
                    if ok2 and type(path) == "string" and path ~= "" and fileExists(path) then
                        if _G.print then print("[KBM][GameBoyApp] bundled ROM -> " .. path) end
                        return path
                    end
                end
            end
        end
    end

    return nil
end

-- Locates the Java KBMGameBoy bridge that ZombieBuddy exposes to Lua.
-- Tries the global name first, then the com.knoxboxgb.* package path.
-- Records what it found in `diag` for error messages.
local function findKBMGameBoyTable(diag)
    local function getPkg()
        local com = rawget(_G, "com")
        if type(com) ~= "table" then return nil end
        local kbmbasic = com.knoxboxgb
        if type(kbmbasic) ~= "table" then return nil end
        if type(kbmbasic.KBMGameBoy) == "table" then return kbmbasic.KBMGameBoy end
        return nil
    end
    local t = rawget(_G, "KBMGameBoy")
    if type(t) == "table" and type(t.init) == "function" then
        table.insert(diag, "bridge:KBMGameBoy global ok")
        return t
    end
    local pkg = getPkg()
    if type(pkg) == "table" and type(pkg.init) == "function" then
        table.insert(diag, "bridge:com.knoxboxgb.KBMGameBoy ok")
        return pkg
    end
    table.insert(diag, "bridge:KBMGameBoy NOT exposed")
    return nil
end

-- Reads the keyboard right now and returns a bitmask of which Game Boy
-- buttons are currently pressed (D-pad + A/B/Start/Select).
local function findRealtimeBridge()
    local bridge = rawget(_G, "KBMGameBoyRealtime")
    if type(bridge) == "table" and type(bridge.start) == "function" then return bridge end
    local com = rawget(_G, "com")
    local pkg = type(com) == "table" and com.knoxboxgb or nil
    bridge = type(pkg) == "table" and pkg.KBMGameBoyRealtime or nil
    if type(bridge) == "table" and type(bridge.start) == "function" then return bridge end
    return nil
end

local function buildPadMask()
    local kd = _G.isKeyDown
    local mask = 0
    local function any(list)
        for i = 1, #list do
            if kd(list[i]) then return true end
        end
        return false
    end
    if any(GB_KEYMAP.UP)     then mask = mask + BTN_UP     end
    if any(GB_KEYMAP.DOWN)   then mask = mask + BTN_DOWN   end
    if any(GB_KEYMAP.LEFT)   then mask = mask + BTN_LEFT   end
    if any(GB_KEYMAP.RIGHT)  then mask = mask + BTN_RIGHT  end
    if any(GB_KEYMAP.A)      then mask = mask + BTN_A      end
    if any(GB_KEYMAP.B)      then mask = mask + BTN_B      end
    if any(GB_KEYMAP.START)  then mask = mask + BTN_START  end
    if any(GB_KEYMAP.SELECT) then mask = mask + BTN_SELECT end
    return mask
end

-- ------------------------------------------------------------
-- Pixel render via KBMGameBoy.getScaledRegionRLE
-- ------------------------------------------------------------
-- Asks the Java side for the current emulator picture as a compact string
-- of (x, y, width, height, color) rectangles, then draws each one. This is
-- much faster than drawing 23,040 individual pixels per frame.
local function renderPixels(ctx, kbm, dstW, dstH, sampleStride, dstX, dstY)
    if not kbm then return 0 end
    dstW = dstW or DISPLAY_W
    dstH = dstH or DISPLAY_H
    sampleStride = sampleStride or 1
    dstX = dstX or DISPLAY_X
    dstY = dstY or DISPLAY_Y

    -- Streaming-texture path: 1 draw call per frame instead of N drawRects.
    if USE_TEXTURE_BLIT and type(kbm.getFrameTexture) == "function" then
        local okTex, tex = pcall(kbm.getFrameTexture)
        if okTex and tex ~= nil then
            -- ctx.drawTextureScaled is registered as a plain function field on
            -- the AppHost render-context table (NOT a method) - call with dot,
            -- not colon. Using ':' would pass ctx itself as the texture arg
            -- and the engine would error with
            --   "expected argument of type Texture, got KahluaTableImpl".
            local okDraw, drawErr = pcall(function()
                ctx.drawTextureScaled(tex, dstX, dstY, dstW, dstH, 1)
            end)
            if okDraw then
                if not LOGGED_BLIT_OK then
                    LOGGED_BLIT_OK = true
                    local s = "?"
                    if type(kbm.getFrameTextureStatus) == "function" then
                        local okS, sv = pcall(kbm.getFrameTextureStatus)
                        if okS then s = tostring(sv) end
                    end
                    local info = "?"
                    if type(kbm.getFrameTextureInfo) == "function" then
                        local okI, iv = pcall(kbm.getFrameTextureInfo)
                        if okI then info = tostring(iv) end
                    end
                    print("[KBM_GB] blit path ACTIVE - drew first textured frame (status="
                          .. s .. ", size=" .. info .. ")")
                end
                return 1
            else
                if not LOGGED_BLIT_FALLBK then
                    LOGGED_BLIT_FALLBK = true
                    print("[KBM_GB] blit path FALLBACK - drawTextureScaled threw: "
                          .. tostring(drawErr))
                end
            end
        else
            if not LOGGED_BLIT_FALLBK then
                LOGGED_BLIT_FALLBK = true
                local s = "?"
                if type(kbm.getFrameTextureStatus) == "function" then
                    local okS, sv = pcall(kbm.getFrameTextureStatus)
                    if okS then s = tostring(sv) end
                end
                print("[KBM_GB] blit path FALLBACK - getFrameTexture returned nil (status="
                      .. s .. ", err=" .. tostring(tex) .. ")")
            end
        end
        -- fall through to RLE if anything failed.
    end

    local ok, blob
    if type(kbm.getScaledRegionRLE) == "function" then
        ok, blob = pcall(function()
            return kbm.getScaledRegionRLE(GB_Y_SRC_START, GB_Y_SRC_END,
                                          dstX, dstY, dstW, dstH,
                                          sampleStride)
        end)
    elseif type(kbm.getRegionRLE2) == "function" then
        ok, blob = pcall(function()
            return kbm.getRegionRLE2(GB_Y_SRC_START, GB_Y_SRC_END,
                                      dstX, dstY, sampleStride, sampleStride)
        end)
    else
        return 0
    end
    if not ok or type(blob) ~= "string" or blob == "" then return 0 end

    local calls = 0
    local floor = math.floor
    for x, y, w, h, c in string.gmatch(blob,
        "(%-?%d+),(%-?%d+),(%-?%d+),(%-?%d+),(%-?%d+),") do
        local color = tonumber(c)
        if color and color >= 0 then
            local r = floor(color / 65536) % 256 / 255
            local g = floor(color / 256)   % 256 / 255
            local b = color % 256 / 255
            ctx.drawRect(tonumber(x), tonumber(y),
                         tonumber(w) + 1, tonumber(h) + 1,
                         1, r, g, b)
            calls = calls + 1
        end
    end
    return calls
end

-- ------------------------------------------------------------
-- ROM list
-- ------------------------------------------------------------
-- Build one menu from bundled catalog ROMs and the user's external library.
local function refreshRomList(self)
    self.romList = {}
    local seen = {}

    for _, entry in ipairs(RomMediaCatalog.entries or {}) do
        if entry.appId == "gb" and entry.rom and resolveLocalRomPath(entry.rom) then
            local key = entry.rom:lower()
            if not seen[key] then
                self.romList[#self.romList + 1] = entry.rom
                seen[key] = true
            end
        end
    end

    -- The Java bridge scans Zomboid/kbm_gb_roms/ and returns its filenames.
    -- Keep these user-supplied ROMs outside the bundled media catalog so they
    -- do not require packaged assets or recorded-media cartridge entries.
    if not self.kbm or type(self.kbm.listRoms) ~= "function" then return end
    local ok, s = pcall(function() return self.kbm.listRoms() end)
    if not ok or type(s) ~= "string" or s == "" then return end
    -- Server returns names separated by '|' (matches KBMGameBoy.listRoms).
    for name in s:gmatch("([^|\n]+)") do
        if name and name ~= "" then
            local key = name:lower()
            if not seen[key] then
                table.insert(self.romList, name)
                seen[key] = true
            end
        end
    end
end

-- ------------------------------------------------------------
-- Lifecycle
-- ------------------------------------------------------------
-- Called once when the app opens. Connects to the Java emulator, scans for
-- ROMs, and (if a specific ROM was requested via shell command) auto-loads it.
function GameBoyApp:init(ctx)
    self.ctx          = ctx
    self.kbm          = nil
    self.realtime     = findRealtimeBridge()
    self.realtimeActive = false
    self.lastWorkerFrames = 0
    self.error        = nil
    self.mode         = "menu"
    self.romList      = {}
    self.romDir       = ""
    self.menuCursor   = 1
    self.lastBits     = 0
    self.lastPadMask  = 0
    self.frameCount   = 0
    self.emuAcc       = 0
    self.lastUpdateMs = 0
    self.lastRenderMs = 0
    self.lastDrawCalls = 0
    self.loadedRom    = nil
    self.bridgeVersion = "unknown"
    self.blockSize    = BLOCK_SIZE
    self.dispMode     = "xlarge"
    self.fillViewport = ctx and type(ctx.args) == "table"
        and (ctx.args.fillViewport == true or ctx.args.radioDevice == true) or false

    if not cartridgeAllows(ctx, ctx and ctx.args and ctx.args.rom) then
        self.mode = "error"
        self.error = "Insert a detected Game Boy cartridge and press Play."
        return
    end

    local diag = {}
    local kbm = findKBMGameBoyTable(diag)
    if not kbm then
        self.mode = "error"
        self.error = "KBMGameBoy Java bridge not exposed.\n"
            .. table.concat(diag, "\n") .. "\n"
            .. "Ensure ZombieBuddy is loaded and KBMGB.jar is deployed."
        return
    end
    self.kbm = kbm

    if type(kbm.getBridgeVersion) == "function" then
        local okV, v = pcall(function() return kbm.getBridgeVersion() end)
        if okV and type(v) == "string" then self.bridgeVersion = v end
    end

    local okI, initErr = pcall(function() return kbm.init() end)
    if not okI then
        self.mode = "error"
        self.error = "KBMGameBoy.init() threw:\n" .. tostring(initErr)
        return
    end

    if type(kbm.getRomDir) == "function" then
        local okD, d = pcall(function() return kbm.getRomDir() end)
        if okD and type(d) == "string" then self.romDir = d end
    end

    refreshRomList(self)

    -- Autoboot from shell: `gb <n|filename>` passes { rom = "<name>" }
    if ctx and type(ctx.args) == "table" and ctx.args.rom then
        local want = tostring(ctx.args.rom)
        local wantLower = want:lower()
        local picked = nil
        for _, name in ipairs(self.romList) do
            if name:lower() == wantLower then picked = name; break end
        end
        if not picked then
            for _, name in ipairs(self.romList) do
                local lc = name:lower()
                if lc == wantLower .. ".gb" or lc == wantLower .. ".gbc" then
                    picked = name; break
                end
            end
        end
        if picked then
            self:loadSelectedRom(picked)
        else
            -- A cartridge hook requested a specific local ROM. Do not fall
            -- back to the library browser if that file disappeared after
            -- the cartridge was discovered.
            self.mode = "error"
            if RomMediaCatalog.getByAppAndRom("gb", want) then
                self.error = "Bundled cartridge ROM was not found:\n" .. want
                    .. "\nCheck the KnoxBox Game Boy installation and restart the game."
            else
                self.error = "Cartridge ROM is no longer available:\n" .. want
                    .. "\nCheck your local kbm_gb_roms folder and restart the game."
            end
        end
    end
end

-- Called every frame while the app is running. Reads the keyboard, sends
-- button state to the emulator, and tells the emulator to advance time
-- (running multiple Game Boy frames if real time has fallen behind).
function GameBoyApp:update(ctx, dt)
    if self.mode ~= "running" then return end
    if not self.kbm then return end

    local t0 = nowMs()

    local mask = buildPadMask()
    if mask ~= self.lastPadMask then
        if self.realtimeActive then
            pcall(function() self.realtime.setPadMask(mask) end)
        else
            pcall(function() self.kbm.setPadMask(1, mask) end)
        end
        self.lastPadMask = mask
    end

    local frames = 0
    if self.realtimeActive then
        local okRunning, running = pcall(function() return self.realtime.isRunning() end)
        if okRunning and running == true then
            local okCount, total = pcall(function() return self.realtime.getFrameCount() end)
            if okCount and type(total) == "number" then
                frames = math.max(0, total - self.lastWorkerFrames)
                self.lastWorkerFrames = total
            end
        else
            self.realtimeActive = false
            local okError, err = pcall(function() return self.realtime.getLastError() end)
            print("[KBM_GB] realtime worker stopped: " .. tostring(okError and err or "unknown"))
        end
    end

    local nowSec = nowMs() / 1000.0
    local realDt = (self.lastUpdateSec and (nowSec - self.lastUpdateSec)) or GB_FRAME_DT
    self.lastUpdateSec = nowSec
    if realDt < 0 then realDt = GB_FRAME_DT end
    if realDt > 0.5 then realDt = GB_FRAME_DT end
    local mirrorActive = _G.KBM_GB_3D_MIRROR_ACTIVE == true
    local maxFrames = mirrorActive and 3 or MAX_EMU_FRAMES_PER_UPDATE
    -- Viewpoint already consumes much of the frame budget. Never let one
    -- slow frame trigger a long emulator catch-up loop on the main thread.
    -- Three frames still bound the work, but let audio catch up when the host
    -- calls us around 20 times per second; two capped output at 40 fps.
    self.emuAcc = self.emuAcc + realDt
    if mirrorActive then self.emuAcc = math.min(self.emuAcc, GB_FRAME_DT * maxFrames) end
    if not self.realtimeActive then
        while self.emuAcc >= GB_FRAME_DT and frames < maxFrames do
            pcall(function() self.kbm.runFrame() end)
            self.emuAcc = self.emuAcc - GB_FRAME_DT
            frames = frames + 1
        end
        if self.emuAcc > GB_FRAME_DT * maxFrames then
            self.emuAcc = 0
        end
    else
        self.emuAcc = 0
    end

    self.lastUpdateMs = nowMs() - t0
    self.perfUpdates = (self.perfUpdates or 0) + 1
    self.perfFrames = (self.perfFrames or 0) + frames
    self.perfMaxUpdateMs = math.max(self.perfMaxUpdateMs or 0, self.lastUpdateMs)
    if not self.perfNextLogMs or nowMs() >= self.perfNextLogMs then
        print(string.format("[KBM_GB] update mirror=%s realtime=%s updates=%d frames=%d maxMs=%.1f lastMs=%.1f",
            tostring(mirrorActive), tostring(self.realtimeActive), self.perfUpdates, self.perfFrames,
            self.perfMaxUpdateMs, self.lastUpdateMs))
        if self.realtimeActive and type(self.realtime.getTimingStats) == "function" then
            local okStats, stats = pcall(function() return self.realtime.getTimingStats() end)
            if okStats then print("[KBM_GB] worker " .. tostring(stats)) end
        end
        self.perfNextLogMs = nowMs() + 5000
        self.perfUpdates, self.perfFrames, self.perfMaxUpdateMs = 0, 0, 0
    end
    self.lastBits = mask
end

-- Called every frame to draw the screen. Picks one of three modes:
-- error (red text), menu (ROM list), or running (the emulator picture).
function GameBoyApp:render(ctx)
    local t0 = nowMs()

    if self.mode == "error" then
        ctx.fillScreen(1, 0, 0, 0)
        ctx.drawCenteredText("GAME BOY LOAD ERROR", 70, 1, 1, 0.3, 0.3, 1)
        local y = 90
        for seg in (tostring(self.error) .. "\n"):gmatch("([^\n]*)\n") do
            ctx.drawCenteredText(seg, y, 1, 1, 0.7, 0.7, 1)
            y = y + 10
            if y > 190 then break end
        end
        self.lastRenderMs = nowMs() - t0
        return
    end

    if self.mode == "menu" then
        self:renderMenu(ctx)
        self.lastRenderMs = nowMs() - t0
        return
    end

    ctx.fillScreen(1, 0.05, 0.08, 0.05)  -- DMG-ish backdrop

    local mode = self.dispMode or "full"
    local view = VIEW_MODES[findViewModeIndex(mode)]
    local drawW = view.width
    local drawH = view.height
    local dx = math.floor((320 - drawW) / 2)
    local dy = math.floor((200 - drawH) / 2)
    if self.fillViewport then
        drawW = 320
        drawH = 200
        dx = 0
        dy = 0
    end
    local block = self.blockSize or BLOCK_SIZE
    local calls = renderPixels(ctx, self.kbm,
        drawW, drawH, block, dx, dy)
    self.lastDrawCalls = calls
    self.frameCount = self.frameCount + 1

    if self.debugOverlay then
        ctx.drawRect(0, 0, 320, 20, 0.75, 0, 0, 0)
        local cgb = (type(self.kbm.isCgb) == "function" and self.kbm.isCgb()) and "CGB" or "DMG"
        local info = string.format(
            "GB %s [%s] rom=%s blk=%d disp=%s %dx%d calls=%d up=%.1f ren=%.1f mask=0x%02X",
            tostring(self.bridgeVersion), cgb,
            tostring(self.loadedRom or "?"),
            self.blockSize or 1, tostring(mode), drawW, drawH,
            calls, self.lastUpdateMs or 0, self.lastRenderMs or 0,
            self.lastBits or 0)
        ctx.drawText(info, 2, 5, 1, 0.7, 1, 0.7, 1)
    end

    self.lastRenderMs = nowMs() - t0
end

-- Draws the ROM-picker menu: title, version, scrollable list with a green
-- highlight on the current selection, plus a help line at the bottom.
function GameBoyApp:renderMenu(ctx)
    ctx.fillScreen(1, 0.02, 0.05, 0.02)
    ctx.drawCenteredText("KBM GAME BOY", 12, 1, 0.7, 1, 0.7, 1)
    ctx.drawCenteredText("Coffee GB " .. tostring(self.bridgeVersion), 24, 1, 0.5, 0.7, 0.5, 1)

    if #self.romList == 0 then
        ctx.drawCenteredText("No ROMs found.", 70, 1, 1, 0.8, 0.4, 1)
        ctx.drawCenteredText("Drop .gb / .gbc files into:", 88, 1, 0.8, 0.8, 0.8, 1)
        local path = self.romDir or ""
        local y = 104
        while #path > 0 do
            local chunk = path:sub(1, 40)
            path = path:sub(41)
            ctx.drawCenteredText(chunk, y, 1, 0.6, 0.8, 1, 1)
            y = y + 10
        end
        ctx.drawCenteredText("Press R to rescan    BKSP to exit", 180, 1, 0.6, 0.6, 0.6, 1)
        return
    end

    ctx.drawCenteredText("Select a ROM  (Up/Down, Enter=load)", 40, 1, 0.7, 0.7, 0.7, 1)

    local rows = 12
    local first = 1
    if #self.romList > rows then
        first = self.menuCursor - math.floor(rows / 2)
        if first < 1 then first = 1 end
        if first + rows - 1 > #self.romList then
            first = #self.romList - rows + 1
        end
    end
    local y = 58
    for i = first, math.min(first + rows - 1, #self.romList) do
        local name = self.romList[i]
        if i == self.menuCursor then
            ctx.drawRect(24, y - 2, 272, 10, 0.35, 0.2, 0.6, 0.2)
            ctx.drawText("> " .. name, 30, y, 1, 1, 1, 0.6, 1)
        else
            ctx.drawText("  " .. name, 30, y, 1, 0.75, 0.85, 0.75, 1)
        end
        y = y + 10
    end

    ctx.drawCenteredText("R=rescan   BKSP=exit   Tab=debug", 192, 1, 0.5, 0.5, 0.5, 1)
end

-- ------------------------------------------------------------
-- Input
-- ------------------------------------------------------------
-- Handles a single key press. The same function serves the menu and the
-- running emulator: TAB toggles a debug overlay, Q changes render quality,
-- E resizes the picture, BKSP/DEL backs out, and the rest go to whichever
-- mode the app is currently in.
function GameBoyApp:onKey(ctx, key)
    if key == KEY_TAB then
        self.debugOverlay = not self.debugOverlay
        return true
    end
    if key == Keyboard.KEY_Q then
        local b = self.blockSize or BLOCK_SIZE
        if b == 1 then b = 2 elseif b == 2 then b = 4 else b = 1 end
        self.blockSize = b
        return true
    end
    if key == Keyboard.KEY_E then
        local idx = findViewModeIndex(self.dispMode or "full")
        local shiftDown = _G.isKeyDown and (_G.isKeyDown(KEY_LSHIFT)
                        or _G.isKeyDown(KEY_RSHIFT))
        if shiftDown then
            if idx > 1 then idx = idx - 1 end
        elseif idx < #VIEW_MODES then
            idx = idx + 1
            self.dispMode = VIEW_MODES[idx].id
            return true
        end
        self.dispMode = VIEW_MODES[idx].id
        return true
    end

    if self.mode == "error" then
        if key == KEY_ESCAPE or key == KEY_BACK or key == KEY_DELETE then ctx.close(); return true end
        return false
    end

    if self.mode == "menu" then
        if key == KEY_UP_MENU then
            if #self.romList > 0 then
                self.menuCursor = self.menuCursor - 1
                if self.menuCursor < 1 then self.menuCursor = #self.romList end
            end
            return true
        elseif key == KEY_DOWN_MENU then
            if #self.romList > 0 then
                self.menuCursor = self.menuCursor + 1
                if self.menuCursor > #self.romList then self.menuCursor = 1 end
            end
            return true
        elseif key == KEY_RETURN then
            if #self.romList > 0 then self:loadSelectedRom() end
            return true
        elseif key == KEY_R then
            refreshRomList(self)
            if self.menuCursor > #self.romList then
                self.menuCursor = math.max(1, #self.romList)
            end
            return true
        elseif key == KEY_ESCAPE or key == KEY_BACK or key == KEY_DELETE then
            ctx.close()
            return true
        end
        return false
    end

    -- running
    if key == KEY_ESCAPE or key == KEY_BACK or key == KEY_DELETE then
        pcall(function() self.kbm.setPadMask(1, 0) end)
        ctx.close()
        return true
    elseif key == KEY_R then
        if self.realtimeActive then
            pcall(function() self.realtime.stop() end)
            self.realtimeActive = false
        end
        pcall(function() self.kbm.reset() end)
        if self.realtime then
            local okStart, started = pcall(function() return self.realtime.start() end)
            self.realtimeActive = okStart and started == true
            self.lastWorkerFrames = 0
        end
        return true
    end
    return false
end

-- Loads the chosen ROM. Verifies the player has the required cartridge
-- item in their inventory (via MediaCheck), finds the file on disk, hands
-- it to the Java emulator, and switches the app into "running" mode.
function GameBoyApp:loadSelectedRom(nameOverride)
    local name = nameOverride or self.romList[self.menuCursor]
    if not name or name == "" then return false end
    if not self.kbm then return false end
    if not cartridgeAllows(self.ctx, name) then
        self.mode = "error"
        self.error = "This ROM does not match the inserted cartridge."
        return false
    end

    local mediaEntry = RomMediaCatalog.getByAppAndRom("gb", name)
    if mediaEntry then
        local okMedia = MediaCheck.hasRequiredMedia(self.ctx, "gb", mediaEntry.softwareKey)
        if not okMedia then
            self.mode = "error"
            self.error = "Required media not found for:\n" .. tostring(name)
                .. "\nNeed: " .. tostring(mediaEntry.label)
            return false
        end
    end

    local path = resolveLocalRomPath(name)
    if not path then
        local okP, p = pcall(function() return self.kbm.resolveRomPath(name) end)
        if okP and type(p) == "string" and p ~= "" then
            path = p
        else
            path = (self.romDir or "") .. "/" .. name
        end
    end

    if self.realtimeActive then
        pcall(function() self.realtime.stop() end)
        self.realtimeActive = false
    end
    local okL, result = pcall(function() return self.kbm.loadROM(path) end)
    if okL and result == true then
        self.loadedRom = name
        self.mode = "running"
        self.emuAcc = 0
        self.frameCount = 0
        -- Pre-warm the audio worker's buffer so the first audible frame
        -- doesn't pop. ~6 frames is enough to fill the SourceDataLine.
        for _ = 1, GB_AUDIO_PREWARM_FRAMES do
            pcall(function() self.kbm.runFrame() end)
        end
        if self.realtime then
            local okStart, started = pcall(function() return self.realtime.start() end)
            self.realtimeActive = okStart and started == true
            self.lastWorkerFrames = 0
            print("[KBM_GB] realtime worker start=" .. tostring(self.realtimeActive))
        end
        self.lastUpdateSec = nil
        return true
    else
        local err = ""
        if type(self.kbm.getLastError) == "function" then
            local okE, e = pcall(function() return self.kbm.getLastError() end)
            if okE and type(e) == "string" then err = e end
        end
        self.mode = "error"
        self.error = "loadROM failed for:\n" .. tostring(name)
            .. "\npath=" .. tostring(path) .. "\n" .. err
        return false
    end
end

-- Called when the window is closed. Releases all buttons and pauses audio,
-- but keeps the emulator instance alive so reopening the app is instant.
function GameBoyApp:shutdown(ctx)
    if self.realtimeActive then
        pcall(function() self.realtime.stop() end)
        self.realtimeActive = false
    end
    if self.kbm then
        pcall(function() self.kbm.setPadMask(1, 0) end)
        if type(self.kbm.pauseAudio) == "function" then
            pcall(function() self.kbm.pauseAudio() end)
        end
        -- Keep engine alive for fast re-open.
    end
end

-- ------------------------------------------------------------
-- Debug-console helpers for the streaming-texture blit path.
-- ------------------------------------------------------------
-- Lua console usage:
--     KBM_GB_setBlit(true)   -- enable streaming-texture path
--     KBM_GB_setBlit(false)  -- back to RLE drawRect path
--     KBM_GB_blitStatus()    -- prints the Java side's status string
function KBM_GB_setBlit(on)
    USE_TEXTURE_BLIT = on and true or false
    print("[KBM_GB] USE_TEXTURE_BLIT = " .. tostring(USE_TEXTURE_BLIT))
end

function KBM_GB_blitStatus()
    local KBMGameBoy = _G.KBMGameBoy
    if not KBMGameBoy or type(KBMGameBoy.getFrameTextureStatus) ~= "function" then
        print("[KBM_GB] getFrameTextureStatus not available on this jar")
        return
    end
    local ok, s = pcall(KBMGameBoy.getFrameTextureStatus)
    print("[KBM_GB] frame-texture status: " .. tostring(ok and s or "<err>"))
end

return GameBoyApp


