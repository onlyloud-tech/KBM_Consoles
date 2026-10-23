-- NesApp.lua — part of KnoxBox Media (Project Zomboid mod)
-- Copyright (C) 2026 Grymlock
-- SPDX-License-Identifier: GPL-3.0-or-later
--
-- NES emulator app (halfnes) running via KBMNes Java bridge.
--
-- PREREQUISITES:
--   1. ZombieBuddy (for @Exposer.LuaClass bridge)
--   2. media/java/KBMMods.jar (fat jar — halfnes core + KBM bridge)
--   3. Zomboid/kbm_nes_roms/<your>.nes — the mod ships no ROMs.
--
-- CONTROLS (NES controller mapping, player 1):
--   SPACE / Z / J / NUMPAD2  - A button
--   LSHIFT / X / K / NUMPAD1 - B button
--   ENTER / NUMPADENTER      - Start
--   RSHIFT / BACK / NUMPAD0  - Select
--   R                        - Soft reset (CPU reset vector; RAM preserved)
--   F2                       - Hard reset (re-loads the ROM from power-on)
--   Q                        - Toggle render quality (1=sharp, 2=fast)
--   TAB                      - Debug overlay
--   ESC                      - Back to menu / close app
--
-- Multiple keys per NES button are intentional. PZ engine-level bindings
-- (Z=run toggle, X=sneak, etc.) can swallow letter keys before Lua sees
-- them, so SPACE / LSHIFT / numpad keys are provided as reliable fallbacks.
-- Edit NES_KEYMAP below to remap.

local VTUtils = require("KBMGB/System/VTUtils")
local MediaCheck = require("KBMGB/System/MediaCheck")
local RomMediaCatalog = require("KBMGB/System/RomMediaCatalog")

local NesApp = {}
NesApp.__index = NesApp

NesApp.meta = {
    api            = "ctx_v1",
    id             = "nes",
    name           = "NES",
    author         = "halfnes (Andrew Hoffman) / KBM wrapper",
    type           = "game",
    width          = 320,
    height         = 200,
    -- NTSC NES output is conventionally displayed at 4:3 on a CRT TV,
    -- regardless of the underlying 256x240 framebuffer. Letterbox to that
    -- ratio inside the TV screen-hole.
    displayAspect  = 4 / 3,
    ticksPerSecond = 60,
}

-- ------------------------------------------------------------
-- Constants
-- ------------------------------------------------------------
local NES_W, NES_H = 256, 240
-- Display area inside 320x200 CRT frame. NES is 4:3 (256x240 with overscan
-- cropped becomes 256x224 ~= 8:7 pixels). We letterbox to 256x200 by
-- sampling NES y in [20..220) so the full playfield fits without squish.
-- Centered horizontally with 32px pillarbox each side.
local DISPLAY_W = 256
local DISPLAY_H = 200
local DISPLAY_X = math.floor((320 - DISPLAY_W) / 2)   -- 32
local DISPLAY_Y = math.floor((200 - DISPLAY_H) / 2)   -- 0
local NES_Y_SRC_START = 20    -- skip top 20 lines (overscan)
local NES_Y_SRC_END   = 220   -- skip bottom 20 lines

local BLOCK_SIZE = 2    -- 1 = pixel-perfect, 2 = 2x2 blocks (faster, halves bridge crossings)
local NES_FRAME_DT = 1 / 60
-- Number of silent frames to push immediately after loadROM. This
-- pre-fills the SourceDataLine / CircularBuffer in the audio worker so
-- the very first user-visible frame doesn't hit an empty buffer (which
-- would underrun and produce a click/pop).
local NES_AUDIO_PREWARM_FRAMES = 6
-- halfnes runs ~2-4ms/frame; render is the slow part. Allow plenty of catch-up
-- so a 70ms host tick still advances 4 NES frames (real-time gameplay speed).
-- Raised from 8 to 12 so heavier mappers (MMC3, etc.) don't get clipped
-- by the spiral-of-death guard during normal PZ frame jitter, which would
-- chronically starve the audio buffer and cause continuous popping.
local MAX_EMU_FRAMES_PER_UPDATE = 12

-- Streaming-texture blit path. When true, render() asks the Java side for a
-- zombie.core.textures.Texture seeded with the current NES frame and draws
-- it via ctx.drawTextureScaled, replacing ~hundreds of drawRect calls per
-- frame with a single textured quad.
--
-- Note: the source NES framebuffer is 256x240 (full PPU output). We use
-- DISPLAY_W x DISPLAY_H = 256x200 to trim 20 lines of top/bottom overscan in
-- the RLE path; the blit path draws the full 240 lines stretched to 200,
-- which slightly compresses overscan rows but still looks correct for the
-- vast majority of games (which keep the visible playfield in the central
-- 200 lines and either match-clear or repeat overscan).
local USE_TEXTURE_BLIT = true
local LOGGED_BLIT_OK     = false
local LOGGED_BLIT_FALLBK = false

-- NES button masks (must match the KBM NES engine BTN_* constants).
local BTN_A      = 0x01
local BTN_B      = 0x02
local BTN_SELECT = 0x04
local BTN_START  = 0x08
local BTN_UP     = 0x10
local BTN_DOWN   = 0x20
local BTN_LEFT   = 0x40
local BTN_RIGHT  = 0x80

-- ------------------------------------------------------------
-- Key mappings (player 1)
-- ------------------------------------------------------------
-- Each NES button can be triggered by ANY of the keys in its list.
-- Edit this table to remap. Use Keyboard.KEY_* constants from PZ.
-- Reference: https://javadoc.lwjgl.org/lwjgl/2.9.3/org/lwjgl/input/Keyboard.html
--
-- Defaults are the standard Mesen / FCEUX layout PLUS extra fallbacks so the
-- emulator stays usable when PZ engine-level bindings (Z = run, X = sneak,
-- N = godmode, etc.) intercept a key before Lua sees it. Space and LShift
-- are the most reliable A/B keys because no PZ binding uses them.
local NES_KEYMAP = {
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
local KEY_F2        = Keyboard.KEY_F2
local KEY_TAB       = Keyboard.KEY_TAB
local KEY_ESCAPE    = Keyboard.KEY_ESCAPE
local KEY_UP_MENU   = Keyboard.KEY_UP
local KEY_DOWN_MENU = Keyboard.KEY_DOWN

-- ------------------------------------------------------------
-- Helpers
-- ------------------------------------------------------------
-- Returns current time in milliseconds, trying several PZ APIs in turn.
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

local MOD_ID = "KBM_Consoles"

local function buildLocalRomCandidates(romName)
    return {
        "common/media/roms/nes/" .. romName,
        "42/media/roms/nes/" .. romName,
        "media/roms/nes/" .. romName,
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

-- Looks for a ROM inside the mod's packaged media/roms/nes folders.
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
                        if _G.print then print("[KBM][NesApp] bundled ROM -> " .. absPath) end
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
                if _G.print then print("[KBM][NesApp] bundled ROM -> " .. path) end
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
                        if _G.print then print("[KBM][NesApp] bundled ROM -> " .. path) end
                        return path
                    end
                end
            end
        end
    end

    return nil
end

-- Locate KBMNes static facade via ZombieBuddy's global exposure.
-- Tries the global name first, then com.kbmbasic.* package paths.
local function findKBMNesTable(diag)
    local function getPkg()
        local com = rawget(_G, "com")
        if type(com) ~= "table" then return nil end
        local kbmbasic = com.kbmbasic
        if type(kbmbasic) ~= "table" then return nil end
        -- New canonical location: com.kbmbasic.KBMNes (flat)
        if type(kbmbasic.KBMNes) == "table" then return kbmbasic.KBMNes end
        -- Legacy subpackage location: com.kbmbasic.KBMNes
        local nes = kbmbasic.nes
        if type(nes) ~= "table" then return nil end
        return nes.KBMNes
    end

    local t = rawget(_G, "KBMNes")
    if type(t) == "table" and type(t.init) == "function" then
        table.insert(diag, "bridge:KBMNes global ok")
        return t
    end
    local pkg = getPkg()
    if type(pkg) == "table" and type(pkg.init) == "function" then
        table.insert(diag, "bridge:com.kbmbasic.KBMNes ok")
        return pkg
    end
    table.insert(diag, "bridge:KBMNes NOT exposed")
    return nil
end

-- Reads the keyboard right now and returns a bitmask of which NES buttons
-- are currently pressed.
-- Build controller mask from current keyboard state.
-- Iterates NES_KEYMAP so any key in a button's list can trigger it.
local function buildPadMask()
    local kd = _G.isKeyDown
    local mask = 0
    local function any(list)
        for i = 1, #list do
            if kd(list[i]) then return true end
        end
        return false
    end
    if any(NES_KEYMAP.UP)     then mask = mask + BTN_UP     end
    if any(NES_KEYMAP.DOWN)   then mask = mask + BTN_DOWN   end
    if any(NES_KEYMAP.LEFT)   then mask = mask + BTN_LEFT   end
    if any(NES_KEYMAP.RIGHT)  then mask = mask + BTN_RIGHT  end
    if any(NES_KEYMAP.A)      then mask = mask + BTN_A      end
    if any(NES_KEYMAP.B)      then mask = mask + BTN_B      end
    if any(NES_KEYMAP.START)  then mask = mask + BTN_START  end
    if any(NES_KEYMAP.SELECT) then mask = mask + BTN_SELECT end
    return mask
end

-- ------------------------------------------------------------
-- Pixel -> drawRect (Java-side region RLE: ONE bridge crossing per frame)
-- ------------------------------------------------------------
-- KBMNes.getRegionRLE samples the cropped NES region with `block` row stride
-- and merges runs both horizontally AND vertically into rectangles. Output:
--   "dstX,dstY,w,h,rgb,..." (5 ints per rect, comma-separated)
-- Falls back to per-row RLE if the bridge predates getRegionRLE.
-- Asks the Java side for the current emulator picture as a compact string
-- of (x, y, w, h, color) rectangles, then draws each one. Falls back to
-- older per-row formats if the new bulk call isn't available.
local function renderPixels(ctx, KBMNes, block, srcStride, dstBlock, dstX, dstY)
    if not KBMNes then return 0 end
    block = block or 1
    dstX = dstX or DISPLAY_X
    dstY = dstY or DISPLAY_Y

    -- Streaming-texture path: 1 draw call per frame instead of N drawRects.
    if USE_TEXTURE_BLIT and type(KBMNes.getFrameTexture) == "function" then
        local okTex, tex = pcall(KBMNes.getFrameTexture)
        if okTex and tex ~= nil then
            local okDraw, drawErr = pcall(function()
                ctx.drawTextureScaled(tex, dstX, dstY, DISPLAY_W, DISPLAY_H, 1)
            end)
            if okDraw then
                if not LOGGED_BLIT_OK then
                    LOGGED_BLIT_OK = true
                    local s, info = "?", "?"
                    if type(KBMNes.getFrameTextureStatus) == "function" then
                        local okS, sv = pcall(KBMNes.getFrameTextureStatus)
                        if okS then s = tostring(sv) end
                    end
                    if type(KBMNes.getFrameTextureInfo) == "function" then
                        local okI, iv = pcall(KBMNes.getFrameTextureInfo)
                        if okI then info = tostring(iv) end
                    end
                    print("[KBM_NES] blit path ACTIVE - drew first textured frame (status="
                          .. s .. ", size=" .. info .. ")")
                end
                return 1
            else
                if not LOGGED_BLIT_FALLBK then
                    LOGGED_BLIT_FALLBK = true
                    print("[KBM_NES] blit path FALLBACK - drawTextureScaled threw: "
                          .. tostring(drawErr))
                end
            end
        else
            if not LOGGED_BLIT_FALLBK then
                LOGGED_BLIT_FALLBK = true
                local s = "?"
                if type(KBMNes.getFrameTextureStatus) == "function" then
                    local okS, sv = pcall(KBMNes.getFrameTextureStatus)
                    if okS then s = tostring(sv) end
                end
                print("[KBM_NES] blit path FALLBACK - getFrameTexture nil (status="
                      .. s .. ")")
            end
        end
    end

    -- Preferred: getRegionRLE2 (decoupled srcStride / dstBlock).
    if srcStride and dstBlock and type(KBMNes.getRegionRLE2) == "function" then
        local ok, blob = pcall(function()
            return KBMNes.getRegionRLE2(NES_Y_SRC_START, NES_Y_SRC_END,
                                         dstX, dstY, srcStride, dstBlock)
        end)
        if ok and type(blob) == "string" and blob ~= "" then
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
    end

    if type(KBMNes.getRegionRLE) == "function" then
        local ok, blob = pcall(function()
            return KBMNes.getRegionRLE(NES_Y_SRC_START, NES_Y_SRC_END,
                                       dstX, dstY, block)
        end)
        if ok and type(blob) == "string" and blob ~= "" then
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
    end

    -- Legacy per-row fallback (more bridge calls, no vertical merge).
    if type(KBMNes.getRowRLE) ~= "function" then return 0 end
    local calls = 0
    local gm = string.gmatch
    for row = 0, DISPLAY_H - 1, block do
        local srcY = NES_Y_SRC_START + row
        if srcY >= NES_Y_SRC_END then break end
        local rowDstY = dstY + row
        local ok, rle = pcall(function() return KBMNes.getRowRLE(srcY) end)
        if ok and type(rle) == "string" and rle ~= "" then
            for xs, ws, cs in gm(rle, "(%-?%d+),(%-?%d+),(%-?%d+);") do
                local runColor = tonumber(cs)
                if runColor and runColor >= 0 then
                    local x = tonumber(xs)
                    local w = tonumber(ws)
                    local r = math.floor(runColor / 65536) % 256 / 255
                    local g = math.floor(runColor / 256)   % 256 / 255
                    local b = runColor % 256 / 255
                    ctx.drawRect(dstX + x, rowDstY, w + 1, block + 1, 1, r, g, b)
                    calls = calls + 1
                end
            end
        end
    end
    return calls
end

-- ------------------------------------------------------------
-- ROM list / menu
-- ------------------------------------------------------------
-- Builds the ROM picker list. Prefers ROMs registered in the mod's catalog
-- that are present in media/roms/nes/, otherwise falls back to whatever's
-- in the user's external Zomboid/kbm_nes_roms/ folder.
local function refreshRomList(self)
    self.romList = {}

    for _, entry in ipairs(RomMediaCatalog.entries or {}) do
        if entry.appId == "nes" and entry.rom and resolveLocalRomPath(entry.rom) then
            self.romList[#self.romList + 1] = entry.rom
        end
    end
    if #self.romList > 0 then
        self.romDir = "packaged media/roms/nes"
        return
    end

    if not self.KBMNes or type(self.KBMNes.listRoms) ~= "function" then return end
    local ok, s = pcall(function() return self.KBMNes.listRoms() end)
    if not ok or type(s) ~= "string" or s == "" then return end
    for name in s:gmatch("([^\n]+)") do
        if name and name ~= "" then
            table.insert(self.romList, name)
        end
    end
end

-- ------------------------------------------------------------
-- Lifecycle
-- ------------------------------------------------------------
-- Called once when the app opens. Connects to the Java emulator, scans for
-- ROMs, and (if the shell command requested a specific ROM) auto-loads it.
-- Handles the "no signal" case where no console is attached to a TV.
function NesApp:init(ctx)
    self.ctx         = ctx
    self.KBMNes      = nil
    self.error       = nil
    self.mode        = "menu"    -- "menu" | "running" | "error" | "no_signal"
    self.romList     = {}
    self.romDir      = ""
    self.menuCursor  = 1
    self.lastBits    = 0
    self.lastPadMask = 0
    self.frameCount  = 0
    self.emuAcc      = 0
    self.lastUpdateMs = 0
    self.lastRenderMs = 0
    self.lastDrawCalls = 0
    self.pixelBuffer = nil
    self.loadedRom   = nil
    self.bridgeVersion = "unknown"
    self.blockSize   = BLOCK_SIZE   -- toggle with Q at runtime

    -- No NES Console attached to a nearby TV: show "no signal" instead of running.
    if ctx and type(ctx.args) == "table" and ctx.args.noSignal then
        self.mode = "no_signal"
        return
    end

    local diag = {}
    local kbm = findKBMNesTable(diag)
    if not kbm then
        self.mode  = "error"
        self.error = "KBMNes Java bridge not exposed.\n"
            .. table.concat(diag, "\n") .. "\n"
            .. "Ensure ZombieBuddy is loaded and KBMMods.jar is deployed."
        return
    end
    self.KBMNes = kbm

    if type(kbm.getBridgeVersion) == "function" then
        local okV, v = pcall(function() return kbm.getBridgeVersion() end)
        if okV and type(v) == "string" then self.bridgeVersion = v end
    end

    local okI, initErr = pcall(function() return kbm.init() end)
    if not okI then
        self.mode  = "error"
        self.error = "KBMNes.init() threw:\n" .. tostring(initErr)
        return
    end

    if type(kbm.getRomDir) == "function" then
        local okD, d = pcall(function() return kbm.getRomDir() end)
        if okD and type(d) == "string" then self.romDir = d end
    end

    refreshRomList(self)

    -- Autoboot from shell: `nes <n|filename>` passes { rom = "<name>" }.
    -- Matches the ROM list case-insensitively; full filename wins over number.
    if ctx and type(ctx.args) == "table" and ctx.args.rom then
        local want = tostring(ctx.args.rom)
        local wantLower = want:lower()
        local picked = nil
        for _, name in ipairs(self.romList) do
            if name:lower() == wantLower then picked = name; break end
        end
        if not picked then
            for _, name in ipairs(self.romList) do
                if name:lower() == wantLower .. ".nes" then picked = name; break end
            end
        end
        if picked then
            self:loadSelectedRom(picked)
        end
    end
end

-- Called every frame while running. Reads the keyboard, sends button state
-- to the emulator, and advances the emulator by however many NES frames
-- match the wall-clock time that has passed.
function NesApp:update(ctx, dt)
    if self.mode ~= "running" then return end
    if not self.KBMNes then return end

    local t0 = nowMs()

    -- Controller state
    local mask = buildPadMask()
    if mask ~= self.lastPadMask then
        pcall(function() self.KBMNes.setPadMask(1, mask) end)
        self.lastPadMask = mask
    end

    -- Advance emulation. AppHost passes a FAKE constant dt (1/ticksPerSecond)
    -- regardless of real elapsed time, so we measure wall-clock ourselves to
    -- keep the NES at real-time 60 Hz even when host updates lag.
    local nowSec = nowMs() / 1000.0
    local realDt = (self.lastUpdateSec and (nowSec - self.lastUpdateSec)) or NES_FRAME_DT
    self.lastUpdateSec = nowSec
    if realDt < 0 then realDt = NES_FRAME_DT end
    if realDt > 0.5 then realDt = NES_FRAME_DT end  -- ignore long pauses
    self.emuAcc = self.emuAcc + realDt
    local frames = 0
    while self.emuAcc >= NES_FRAME_DT and frames < MAX_EMU_FRAMES_PER_UPDATE do
        pcall(function() self.KBMNes.runFrame() end)
        self.emuAcc = self.emuAcc - NES_FRAME_DT
        frames = frames + 1
    end
    if self.emuAcc > NES_FRAME_DT * MAX_EMU_FRAMES_PER_UPDATE then
        -- Too far behind — drop accumulator to avoid spiral of death.
        self.emuAcc = 0
    end

    local t1 = nowMs()
    self.lastUpdateMs = t1 - t0
    self.lastBits = mask
end

-- Called every frame to draw. Picks one of: NO SIGNAL static screen, error
-- text, ROM picker menu, or the live emulator picture (with optional debug
-- overlay along the top).
function NesApp:render(ctx)
    local t0 = nowMs()

    if self.mode == "no_signal" then
        -- Static-noise effect: random gray dots over a black field.
        ctx.fillScreen(1, 0.02, 0.02, 0.02)
        local frame = (self.frameCount or 0)
        self.frameCount = frame + 1
        -- Cheap pseudo-random scatter; not seeded, just frame-tied for shimmer.
        local seed = (frame * 9301 + 49297) % 233280
        for i = 1, 220 do
            seed = (seed * 9301 + 49297) % 233280
            local x = seed % 320
            seed = (seed * 9301 + 49297) % 233280
            local y = seed % 200
            seed = (seed * 9301 + 49297) % 233280
            local g = 0.3 + ((seed % 700) / 1000)
            ctx.drawRect(x, y, 2, 2, 1, g, g, g)
        end
        if ctx.drawCenteredText then
            ctx.drawCenteredText("NO SIGNAL", 92, 1, 1, 1, 1, 1)
            ctx.drawCenteredText("Connect an NES Console to a TV nearby.", 110, 1, 0.8, 0.8, 0.8, 1)
        end
        self.lastRenderMs = nowMs() - t0
        return
    end

    if self.mode == "error" then
        ctx.fillScreen(1, 0, 0, 0)
        ctx.drawCenteredText("NES LOAD ERROR", 70, 1, 1, 0.3, 0.3, 1)
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

    -- running: fetch pixels and draw
    ctx.fillScreen(1, 0, 0, 0)

    -- Display size modes (press E to cycle):
    --   "full" : 256x200, srcStride=1 (or blockSize), dstBlock=blockSize
    --   "half" : 128x100, srcStride=2, dstBlock=1 (sharp, 4x less pixel fill)
    --   "quad" :  64x50,  srcStride=4, dstBlock=1 (sharp, 16x less pixel fill)
    local mode = self.dispMode or "full"
    local block = self.blockSize or BLOCK_SIZE
    local calls
    if mode == "half" then
        local dx = math.floor((320 - 128) / 2)
        local dy = math.floor((200 - 100) / 2)
        calls = renderPixels(ctx, self.KBMNes, block, 2, 1, dx, dy)
    elseif mode == "quad" then
        local dx = math.floor((320 - 64) / 2)
        local dy = math.floor((200 - 50) / 2)
        calls = renderPixels(ctx, self.KBMNes, block, 4, 1, dx, dy)
    else
        calls = renderPixels(ctx, self.KBMNes, block)
    end
    self.lastDrawCalls = calls
    self.frameCount = self.frameCount + 1

    if self.debugOverlay then
        ctx.drawRect(0, 0, 320, 20, 0.75, 0, 0, 0)
        local info = string.format(
            "NES %s  rom=%s  blk=%d disp=%s calls=%d up=%.1f ren=%.1f mask=0x%02X",
            tostring(self.bridgeVersion),
            tostring(self.loadedRom or "?"),
            self.blockSize or 1,
            tostring(mode),
            calls, self.lastUpdateMs or 0, self.lastRenderMs or 0,
            self.lastBits or 0)
        ctx.drawText(info, 2, 5, 1, 0.7, 1, 0.7, 1)
    end

    self.lastRenderMs = nowMs() - t0
end

-- Draws the ROM picker menu: title, version, scrollable list with a green
-- highlight on the current selection, plus help text at the bottom.
function NesApp:renderMenu(ctx)
    ctx.fillScreen(1, 0.02, 0.02, 0.05)
    ctx.drawCenteredText("KBM NES", 12, 1, 0.7, 1, 0.7, 1)
    ctx.drawCenteredText("halfnes " .. tostring(self.bridgeVersion), 24, 1, 0.5, 0.7, 0.5, 1)

    if #self.romList == 0 then
        ctx.drawCenteredText("No ROMs found.", 70, 1, 1, 0.8, 0.4, 1)
        ctx.drawCenteredText("Drop .nes files into:", 88, 1, 0.8, 0.8, 0.8, 1)
        -- Path may be long; draw at smaller scale if possible by wrapping.
        local path = self.romDir or ""
        -- Wrap at 40 char boundaries for readability.
        local y = 104
        while #path > 0 do
            local chunk = path:sub(1, 40)
            path = path:sub(41)
            ctx.drawCenteredText(chunk, y, 1, 0.6, 0.8, 1, 1)
            y = y + 10
        end
        ctx.drawCenteredText("Press R to rescan    ESC to exit", 180, 1, 0.6, 0.6, 0.6, 1)
        return
    end

    ctx.drawCenteredText("Select a ROM  (Up/Down, Enter=load)", 40, 1, 0.7, 0.7, 0.7, 1)

    -- Visible window: 12 rows, cursor-centered when possible.
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

    ctx.drawCenteredText("R=rescan   ESC=exit   Tab=debug", 192, 1, 0.5, 0.5, 0.5, 1)
end

-- ------------------------------------------------------------
-- Input
-- ------------------------------------------------------------
-- Handles a single key press. TAB toggles debug overlay, Q changes render
-- quality, E cycles display size, ESC backs out, and the rest get routed
-- to either the menu or the running emulator depending on mode.
function NesApp:onKey(ctx, key)
    if key == KEY_TAB then
        self.debugOverlay = not self.debugOverlay
        return true
    end
    if key == Keyboard.KEY_Q then
        -- Cycle render quality: 1 (sharp) -> 2 (balanced) -> 4 (fast).
        local b = self.blockSize or 2
        if b == 1 then b = 2
        elseif b == 2 then b = 4
        else b = 1 end
        self.blockSize = b
        return true
    end
    if key == Keyboard.KEY_E then
        -- Cycle display size: full -> half -> quad -> full
        local m = self.dispMode or "full"
        if m == "full" then m = "half"
        elseif m == "half" then m = "quad"
        else m = "full" end
        self.dispMode = m
        return true
    end

    if self.mode == "error" then
        if key == KEY_ESCAPE then ctx.close(); return true end
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
            if #self.romList > 0 then
                self:loadSelectedRom()
            end
            return true
        elseif key == KEY_R then
            refreshRomList(self)
            if self.menuCursor > #self.romList then self.menuCursor = math.max(1, #self.romList) end
            return true
        elseif key == KEY_ESCAPE then
            ctx.close()
            return true
        end
        return false
    end

    -- running mode
    if key == KEY_ESCAPE then
        -- Return to menu, release controller.
        pcall(function() self.KBMNes.setPadMask(1, 0) end)
        self.mode = "menu"
        return true
    elseif key == KEY_R then
        -- Soft reset: equivalent to the physical NES reset button.
        -- CPU jumps to its reset vector but RAM/PPU state may persist,
        -- so some games don't visibly restart. Use F2 for a power-cycle.
        pcall(function() self.KBMNes.reset() end)
        return true
    elseif key == KEY_F2 then
        -- Hard reset: re-load the ROM from disk. This is the only reliable
        -- way to fully restart games that softlock or that don't honor
        -- the soft-reset signal.
        if self.loadedRom and self.loadedRom ~= "" then
            self:loadSelectedRom(self.loadedRom)
        else
            pcall(function() self.KBMNes.reset() end)
        end
        return true
    end
    return false
end

-- Loads the chosen ROM. Verifies the player has the required cartridge in
-- their inventory (via MediaCheck), finds the file on disk, hands it to
-- the Java emulator, and switches the app into "running" mode.
function NesApp:loadSelectedRom(nameOverride)
    local name = nameOverride or self.romList[self.menuCursor]
    if not name or name == "" then return false end
    if not self.KBMNes then return false end

    local mediaEntry = RomMediaCatalog.getByAppAndRom("nes", name)
    if mediaEntry then
        local okMedia = MediaCheck.hasRequiredMedia(self.ctx, "nes", mediaEntry.softwareKey)
        if not okMedia then
            self.mode = "error"
            self.error = "Required media not found for:\n" .. tostring(name)
                .. "\nNeed: " .. tostring(mediaEntry.label)
            return false
        end
    end

    local path = resolveLocalRomPath(name)
    if not path then
        local okP, p = pcall(function() return self.KBMNes.resolveRomPath(name) end)
        if okP and type(p) == "string" and p ~= "" then
            path = p
        else
            path = (self.romDir or "") .. "/" .. name
        end
    end

    local okL, result = pcall(function() return self.KBMNes.loadROM(path) end)
    if okL and result == true then
        self.loadedRom = name
        self.mode = "running"
        self.emuAcc = 0
        self.frameCount = 0
        -- Pre-warm the audio worker's buffer so the first audible frame
        -- doesn't pop. Cheap: ~100us per frame, ~6 frames = barely
        -- noticeable hitch, but the SourceDataLine starts full instead
        -- of empty so playback is glitch-free from sample 0.
        for _ = 1, NES_AUDIO_PREWARM_FRAMES do
            pcall(function() self.KBMNes.runFrame() end)
        end
        self.lastUpdateSec = nil
        return true
    else
        local err = ""
        if type(self.KBMNes.getLastError) == "function" then
            local okE, e = pcall(function() return self.KBMNes.getLastError() end)
            if okE and type(e) == "string" then err = e end
        end
        self.mode  = "error"
        self.error = "loadROM failed for:\n" .. tostring(name) .. "\npath=" .. tostring(path) .. "\n" .. err
        return false
    end
end

-- Called when the window closes. Releases both controllers and pauses
-- audio, but keeps the emulator instance alive so reopening is instant.
function NesApp:shutdown(ctx)
    if self.KBMNes then
        pcall(function() self.KBMNes.setPadMask(1, 0) end)
        pcall(function() self.KBMNes.setPadMask(2, 0) end)
        -- Cleanly stop audio output to avoid the buzz/pop from a truncated
        -- SourceDataLine buffer. Engine instance stays alive for fast reopen.
        if type(self.KBMNes.pauseAudio) == "function" then
            pcall(function() self.KBMNes.pauseAudio() end)
        end
        -- Do NOT destroy: keep engine instance alive so reopening the app is fast.
        -- If you want a full teardown, uncomment:
        -- pcall(function() self.KBMNes.destroy() end)
    end
end

return NesApp
