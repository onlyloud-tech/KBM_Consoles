-- DoomApp.lua - part of KnoxBox Media (Project Zomboid mod)
-- Copyright (C) 2026 Grymlock
--
-- This program is free software: you can redistribute it and/or modify
-- it under the terms of the GNU General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- This program is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
-- GNU General Public License for more details.
--
-- You should have received a copy of the GNU General Public License
-- along with this program. If not, see <https://www.gnu.org/licenses/>.

-- File: media/lua/client/KBM/Games/CRT/DoomApp.lua
--
-- DOOM (1993) running via the KBM headless DOOM engine bridge.
--
-- PREREQUISITES:
--   1. Java mod loader/framework installed (e.g. ZombieBuddy)
--   2. media/java/KBMConsolesNES.jar
--   3. Java bridge exposed to Lua as KBMDoom
--   4. media/data/DOOM1.WAD (shareware WAD - freely distributable)
--
-- CONTROLS:
--   W / UP      - Move forward
--   S / DOWN    - Move backward
--   LEFT        - Turn left
--   RIGHT       - Turn right
--   LCONTROL    - Fire
--   SPACE       - Use / open door
--   LSHIFT      - Walk (default is run)
--   ,           - Strafe left
--   .           - Strafe right
--   1-7         - Select weapon
--   TAB              - Automap toggle
--   F2               - Restart game (re-launches the WAD from pistol-start)
--   ESCAPE / BKSP / DELETE - Close app (return to terminal)
--
-- RENDERING:
--   Pixel buffer is RLE-compressed per scanline into drawRect calls.
--   320x200 native at 35 ticks/second. Falls back to 160x100 (2x2 pixels)
--   if the draw call budget is exceeded (> DRAW_CALL_BUDGET constant).

local VTUtils = require("KBMGB/System/VTUtils")
local clamp = (VTUtils and type(VTUtils.clamp) == "function") and VTUtils.clamp
if not clamp then
    -- Fallback for environments where VTUtils.clamp is unavailable.
    clamp = function(v, lo, hi)
        if v < lo then return lo end
        if v > hi then return hi end
        return v
    end
end

local DoomApp = {}
DoomApp.__index = DoomApp

-- ------------------------------------------------------------
-- Meta - ctx_v1 app declaration
-- ------------------------------------------------------------
DoomApp.meta = {
    api           = "ctx_v1",
    id            = "doom",
    name          = "DOOM",
    author        = "id Software / KBM wrapper",
    type          = "game",
    width         = 320,
    height        = 200,
    -- DOOM's 320x200 framebuffer was designed for CRT displays with
    -- non-square pixels (~1.2 vertical stretch), so the correct on-screen
    -- aspect is 4:3. NESConsoleWindow:getAppViewport() honors this when
    -- letterboxing is enabled.
    displayAspect = 4 / 3,
    ticksPerSecond = 35,
}

-- ------------------------------------------------------------
-- Constants
-- ------------------------------------------------------------
local WAD_PATH_KEY = "DOOM1.WAD"
local WAD_PATH_CANDIDATES = {
    "media/data/DOOM1.WAD",
    "media\\data\\DOOM1.WAD",
    "KBM_Consoles/media/data/DOOM1.WAD",
    "KBM_Consoles\\media\\data\\DOOM1.WAD",
    WAD_PATH_KEY,
}

-- RLE draw-call budget per frame. If a frame exceeds this,
-- we cut resolution to half (160x100, 2x2 pixel blocks).
local DRAW_CALL_BUDGET = 18000
local DOOM_TICK_DT = 1 / 35
-- Allow modest catch-up after pause/minimize. Each tick runs the full DOOM
-- sim and (subject to ENGINE_DISPLAY_HZ) may also software-raster a frame,
-- so the PER-UPDATE cost roughly equals MAX_TICKS_PER_UPDATE * ~30ms with
-- the current 960x600 internal buffer. With the old value of 4 the loop
-- self-stabilized at exactly ~8 PZ FPS (4 * 30 = 120ms = 8 Hz). Dropping
-- to 1 lets the PZ frame rate climb back toward 30+; the trade-off is that
-- if PZ can't sustain ~35 fps, the DOOM sim runs slightly slow rather than
-- bunching multiple tics into one heavy update. ENGINE_DISPLAY_HZ already
-- decouples render rate from sim rate, so missed display frames are cheap.
local MAX_TICKS_PER_UPDATE = 1
local RENDER_CAP_ENABLED = false
local RENDER_TARGET_FPS = 12
local RENDER_MIN_DT_MS = 1000 / RENDER_TARGET_FPS
local MOUSE_TURN_THRESHOLD = 2.0
local MOUSE_MAX_DELTA = 80
-- Analog turn scaling fed to Engine.setMouseTurnDelta(dx). Native DOOM consumes
-- 'mousex' as: cmd.angleturn -= mousex * 8. Fast key-turn = angleturn 640/tic =
-- mousex 80. Higher = faster mouse look. 24 makes ~360 deg in roughly one
-- second of moderate mouse movement.
local MOUSE_TURN_SCALE = 24
-- Per-frame analog turn fed when arrow keys are held (no mouse).
local KEY_TURN_DELTA = 28
local MOUSE_CLICK_FIRE = true
local MOUSE_FIRE_TICKS = 2
local KEY_FIRE_TICKS = 2
local COLOR_QUANT_BITS = 0
local COLOR_QUANT_MIN = 0
local COLOR_QUANT_MAX = 2
local MOTION_FALLBACK_ENABLED = true
local MOTION_QUANT_BITS = 2
local MOTION_ENTER_FRAMES = 2
local MOTION_EXIT_FRAMES = 12
local MOTION_RENDER_MS_TRIGGER = 72
local MOTION_DRAWCALL_TRIGGER = 8500
local LOWRES_DYNAMIC_QUANT_ENABLED = false
local LOWRES_DRAWCALL_EWMA_ALPHA = 0.2
local LOWRES_QUANT_UPSHIFT_MS = 92
local LOWRES_QUANT_DOWNSHIFT_MS = 74
local LOWRES_QUANT_UPSHIFT_CALLS = 9000
local LOWRES_QUANT_DOWNSHIFT_CALLS = 7600
local LOWRES_QUANT_UPSHIFT_FRAMES = 6
local LOWRES_QUANT_DOWNSHIFT_FRAMES = 16
-- DOOM display frame rate cap. Native is 35Hz. With the full-frame texture
-- blit path, gameplay rendering is ~1 draw call on the Lua/PZ side regardless
-- of resolution, BUT the engine still software-rasterizes its full internal
-- buffer (currently 960x600 = 1.8M pixels) every Display() call, which is
-- the dominant cost on the PZ main thread. Capping Display() at 20Hz frees
-- ~40% of that wall-clock budget while keeping motion smooth (DOOM's own
-- monsters/projectiles tic at the lower of sim/display rate anyway).
local ENGINE_DISPLAY_HZ = 20   -- 0 disables cap; sim still runs at 35Hz
local VIEW_WIDTH = 320
local VIEW_HEIGHT = 200
local LOWRES_GAME_VIEW_ENABLED = true
local GAMEPLAY_RENDER_MODE = "blit" -- "rle" or "blit"; blit path uses KBMFrameTexture (Texture(int,int,int) + setData on render thread)
-- Gameplay region within the 320x200 virtual canvas. The 200x150 box is a 4:3 area
-- centered horizontally, leaving 60px pillarbox each side and 32px HUD strip at bottom.
local GAME_VIEW_WIDTH = 200
local GAME_VIEW_HEIGHT = 150
local LOWRES_HUD_HEIGHT = 32   -- px on screen for the original DOOM status bar strip
local CRT_SCANLINES_ENABLED = false
local CRT_SCANLINE_ALPHA = 0.0  -- 0=off, higher=darker scanline bands
local HUD_RENDER_MODE = "mini_vanilla" -- "sprite" | "mini_vanilla" | "rle"
local MINI_VANILLA_HUD_WIDTH = 160
local MINI_VANILLA_HUD_HEIGHT = 16
local MINI_VANILLA_HUD_BOTTOM_MARGIN = 2
local MINI_VANILLA_HUD_BLOCK_SIZE = 1
local LOWRES_TEXT_HUD_ENABLED = false
local LOWRES_MESSAGE_BAND_ENABLED = false
local LOWRES_MESSAGE_BAND_HEIGHT = 12
local LOWRES_DYNAMIC_BLOCK_ENABLED = false
local LOWRES_BLOCK_MIN = 2
local LOWRES_BLOCK_MAX = 2
local LOWRES_HUD_BLOCK_SIZE = 4
local LOWRES_UPSHIFT_MS = 95
local LOWRES_DOWNSHIFT_MS = 62
local TEST_TEXTURE_PROBE_ENABLED = false  -- Runtime-created texture probe is unsafe; keep off in normal gameplay
local EXPECTED_BRIDGE_VERSION = "KBMDoom-bridge-2026-04-24-blit-disabled"
local TEST_TEXTURE_PROBE_X = 10            -- Draw test texture at fixed position for validation
local TEST_TEXTURE_PROBE_Y = 10
local TEST_TEXTURE_PROBE_SIZE = 64         -- 32x32 texture scaled 2x for visibility
local LOWRES_UPSHIFT_FRAMES = 14
local LOWRES_DOWNSHIFT_FRAMES = 6
local LOWRES_EWMA_ALPHA = 0.2
local SHOWCASE_2PX_GUARD_ENABLED = true
local SHOWCASE_2PX_HARD_UPSHIFT_MS = 120
local SHOWCASE_2PX_HARD_UPSHIFT_FRAMES = 10
local READABILITY_IDLE_TICKS = 20
local READABILITY_IDLE_MAX_RENDER_MS = 85
local READABILITY_IDLE_FORCE_BLOCK = 2
local READABILITY_IDLE_FORCE_QUANT = 0
local READABILITY_IDLE_LOCK_ENABLED = true
-- Intermission/finale tally screens: force crisp rendering regardless of perf pressure
local INTERMISSION_FORCE_BLOCK = 2      -- block size during GS_INTERMISSION / GS_FINALE
local INTERMISSION_FORCE_QUANT = 0      -- quant bits (0 = full color) during tally screens
local HUD_DETAIL_ENABLED = true
local HUD_HEIGHT = 40
local CROSSHAIR_ENABLED = true
local CROSSHAIR_SIZE = 4
local DEBUG_OVERLAY = false   -- toggle with Tab in-game
local DEBUG_TICK_LOG_ENABLED = false
local DEBUG_TICK_LOG_EVERY = 8   -- when enabled, sample instead of every tick

-- Quality ladder for controlled testing:
-- level 1 = 8px blocks  (40x25, 1000 tiles)
-- level 2 = 4px blocks  (80x50, 4000 tiles)
-- level 3 = 2px blocks  (160x100, 16000 tiles)
-- level 4 = 1px blocks  (320x200, full native)
local QUALITY_LEVELS = { 8, 4, 2, 1 }
local START_QUALITY_LEVEL = 4
-- Force 1px low-res block size for a full no-quant 1px test
LOWRES_DYNAMIC_BLOCK_ENABLED = true
LOWRES_BLOCK_MIN = 2
LOWRES_BLOCK_MAX = 2
LOWRES_HUD_BLOCK_SIZE = 1

-- Input bitmask values (must match Engine.setInputBits in mochadoom Engine.java)
local INPUT_FORWARD      = 0x001
local INPUT_BACKWARD     = 0x002
local INPUT_TURN_LEFT    = 0x004
local INPUT_TURN_RIGHT   = 0x008
local INPUT_FIRE         = 0x010
local INPUT_USE          = 0x020
local INPUT_RUN          = 0x040
local INPUT_STRAFE_LEFT  = 0x080
local INPUT_STRAFE_RIGHT = 0x100
-- Weapon-select bits: holding the digit key in DOOM picks that slot.
local INPUT_WEAPON_1     = 0x0200  -- fist / chainsaw
local INPUT_WEAPON_2     = 0x0400  -- pistol
local INPUT_WEAPON_3     = 0x0800  -- shotgun / SSG
local INPUT_WEAPON_4     = 0x1000  -- chaingun
local INPUT_WEAPON_5     = 0x2000  -- rocket launcher
local INPUT_WEAPON_6     = 0x4000  -- plasma rifle
local INPUT_WEAPON_7     = 0x8000  -- BFG 9000

-- ------------------------------------------------------------
-- Key mappings (explicit Keyboard.KEY_* - no dynamic indexing)
-- ------------------------------------------------------------
local KEY_W       = Keyboard.KEY_W
local KEY_Q       = Keyboard.KEY_Q
local KEY_E       = Keyboard.KEY_E
local KEY_S       = Keyboard.KEY_S
local KEY_A       = Keyboard.KEY_A
local KEY_D       = Keyboard.KEY_D
local KEY_UP      = Keyboard.KEY_UP
local KEY_DOWN    = Keyboard.KEY_DOWN
local KEY_LEFT    = Keyboard.KEY_LEFT
local KEY_RIGHT   = Keyboard.KEY_RIGHT
local KEY_LCTRL   = Keyboard.KEY_LCONTROL
local KEY_F       = Keyboard.KEY_F
local KEY_SPACE   = Keyboard.KEY_SPACE
local KEY_LSHIFT  = Keyboard.KEY_LSHIFT
local KEY_COMMA   = Keyboard.KEY_COMMA
local KEY_PERIOD  = Keyboard.KEY_PERIOD
local KEY_TAB     = Keyboard.KEY_TAB
local KEY_ESCAPE  = Keyboard.KEY_ESCAPE
local KEY_BACK    = Keyboard.KEY_BACK
local KEY_DELETE  = Keyboard.KEY_DELETE
local KEY_F2      = Keyboard.KEY_F2
local KEY_RETURN  = Keyboard.KEY_RETURN
local KEY_1       = Keyboard.KEY_1
local KEY_2       = Keyboard.KEY_2
local KEY_3       = Keyboard.KEY_3
local KEY_4       = Keyboard.KEY_4
local KEY_5       = Keyboard.KEY_5
local KEY_6       = Keyboard.KEY_6
local KEY_7       = Keyboard.KEY_7

local FRONTEND_CAPTURE_STATES = {
    title = true,
    demo = true,
    menu = true,
}

local function bridgeSupportsFrontend(bridge)
    if not bridge then return false end
    if type(bridge.supportsFrontend) == "function" then
        local ok, v = pcall(function() return bridge.supportsFrontend() end)
        if ok and v == true then return true end
    end
    return type(bridge.initAdvanced) == "function"
        and type(bridge.getFrontendState) == "function"
        and type(bridge.sendMenuInput) == "function"
end

local function sendFrontendAction(self, action)
    if not self or not self.KBMDoom then return false end
    if type(self.KBMDoom.sendMenuInput) ~= "function" then return false end
    local ok, sent = pcall(function() return self.KBMDoom.sendMenuInput(action) end)
    if not ok then
        if _G.print then
            print("[KBM][DoomApp] sendMenuInput pcall failed action=" .. tostring(action))
        end
        return false
    end
    if sent ~= true then
        if _G.print then
            print("[KBM][DoomApp] sendMenuInput rejected action=" .. tostring(action))
        end
        return false
    end
    return true
end

local function pumpFrontendInput(self)
    local kd = _G.isKeyDown
    if not kd then return end

    if type(self._frontendLatch) ~= "table" then
        self._frontendLatch = {}
    end

    -- Keep frontend/menu controls strict and deterministic.
    local pressed = {
        up      = kd(KEY_UP),
        down    = kd(KEY_DOWN),
        left    = kd(KEY_LEFT),
        right   = kd(KEY_RIGHT),
        confirm = kd(KEY_RETURN) or kd(KEY_SPACE),
        back    = kd(KEY_ESCAPE) or kd(KEY_BACK),
        start   = kd(KEY_TAB),
    }

    local order = { "up", "down", "left", "right", "confirm", "back", "start" }
    for i = 1, #order do
        local action = order[i]
        local nowDown = pressed[action] == true
        local wasDown = self._frontendLatch[action] == true
        if nowDown and not wasDown then
            sendFrontendAction(self, action)
        end
        self._frontendLatch[action] = nowDown
    end
end

-- ------------------------------------------------------------
-- Helpers
-- ------------------------------------------------------------
-- Layman summary of what lives in this section:
--   findKBMDoomTable     - locates the Java DOOM bridge exposed to Lua.
--   resolveWadPath       - finds the DOOM1.WAD shareware data file on disk.
--   buildInputBits       - turns the current keyboard state into a bitmask
--                          of DOOM player actions (move/turn/fire/etc.).
--   renderRegion / Cached / RectRLE / Rect
--                        - different ways to copy the emulator's picture
--                          onto the in-game CRT screen, picking the most
--                          efficient path the bridge supports.
--   drawScanlines / drawHudPanel / drawVanillaHud / renderGameplayRegion
--   renderTestTextureProbe / renderCoarse
--                        - drawing helpers used by the main render loop
--                          (HUD overlay, low-res mode, debug probes).
--   getHudStats          - reads health/armor/ammo/keys from the engine.
--   setQualityLevel      - cycles through render quality presets.
--   hasBit / isGameplayInputActive
--                        - tiny bitmask helpers.
--   logTick / updateMotionQuant / adaptLowresBlockSize
--                        - performance auto-tuner: drops resolution when
--                          framerate dips, raises it when things calm down.
--   nowMs                - current time in milliseconds (uses any PZ API).

-- Find the KBMDoom static-facade table exposed by ZombieBuddy.
-- ZombieBuddy exposes @Exposer.LuaClass classes as Lua tables with
-- their static methods. We never call methods on returned Java instances.
local function findKBMDoomTable(diag)
    local function getPkgKBMDoom()
        local com = rawget(_G, "com")
        if type(com) ~= "table" then return nil end
        local kbmbasic = com.kbmbasic
        if type(kbmbasic) ~= "table" then return nil end
        -- New canonical location: com.kbmbasic.KBMDoom (flat)
        if type(kbmbasic.KBMDoom) == "table" then return kbmbasic.KBMDoom end
        -- Legacy subpackage location: com.kbmbasic.KBMDoom
        local doom = kbmbasic.doom
        if type(doom) ~= "table" then return nil end
        return doom.KBMDoom
    end

    local KBMDoom = rawget(_G, "KBMDoom")
    if type(KBMDoom) == "table" and type(KBMDoom.init) == "function" then
        table.insert(diag, "bridge:KBMDoom static facade ok")
        return KBMDoom
    elseif type(KBMDoom) == "table" then
        table.insert(diag, "bridge:KBMDoom found but missing init() - stale jar?")
    else
        table.insert(diag, "bridge:KBMDoom not exposed")
    end

    local pkgKbmDoom = getPkgKBMDoom()
    if type(pkgKbmDoom) == "table" and type(pkgKbmDoom.init) == "function" then
        table.insert(diag, "bridge:com.kbmbasic.KBMDoom static facade ok")
        return pkgKbmDoom
    elseif type(pkgKbmDoom) == "table" then
        table.insert(diag, "bridge:com.kbmbasic.KBMDoom found but missing init()")
    else
        table.insert(diag, "bridge:com.kbmbasic.KBMDoom not exposed")
    end

    return nil
end

-- Resolve a WAD's absolute path using PZ's mod file locator.
-- Returns an absolute path string, or nil on failure.
-- PZ 41.78: getFileReader is NOT a global; we must use getModFileReader for validation.
--
-- @param filename  exact WAD filename (e.g. "DOOM1.WAD", "freedoom1.wad").
--                  Defaults to "DOOM1.WAD" for backward compatibility.
local function resolveWadPath(filename)
    local fname = (type(filename) == "string" and filename ~= "") and filename or "DOOM1.WAD"
    local MOD_ID  = "KBM_Consoles"
    -- Candidate relative paths under the mod root. Multi-target layout puts
    -- the WAD in common/media/data/; legacy single-target B41 had it in
    -- media/data/. We probe each in order and return the first that exists.
    local WAD_REL_CANDIDATES = {
        "common/media/data/" .. fname,
        "42/media/data/" .. fname,
        "media/data/" .. fname,
    }

    -- Validates an absolute path on disk. PZ exposes a global `fileExists`
    -- (distinct from getFileReader) which accepts absolute paths - vanilla
    -- map code uses it on absolute paths returned by getLotDirectories.
    local function diskExists(path)
        if not path or path == "" then return false end
        if _G.fileExists then
            local ok, v = pcall(_G.fileExists, path)
            if ok and v then return true end
        end
        -- Backup: getFileReader on absolute path. May or may not work.
        if _G.getFileReader then
            local ok, reader = pcall(_G.getFileReader, path, false)
            if ok and reader then
                if reader.close then pcall(function() reader:close() end) end
                return true
            end
        end
        return false
    end

    -- Builds an absolute path under modDir with native separators, returning
    -- the first candidate that actually exists on disk.
    local function findExistingUnderModDir(modDir)
        if not modDir or modDir == "" then return nil end
        local sep = (modDir:find("/") and not modDir:find("\\")) and "/" or "\\"
        local base = modDir:gsub("[/\\]+$", "")
        for _, rel in ipairs(WAD_REL_CANDIDATES) do
            local relNative = rel:gsub("/", sep)
            local absPath = base .. sep .. relNative
            if diskExists(absPath) then return absPath end
        end
        return nil
    end

    -- Build "<modsDir><sep>KBM_Consoles<sep>media\data\DOOM1.WAD" given the mods base dir.
    local function findExistingUnderModsDir(modsDir)
        if not modsDir or modsDir == "" then return nil end
        local sep = (modsDir:find("/") and not modsDir:find("\\")) and "/" or "\\"
        local base = modsDir:gsub("[/\\]+$", "") .. sep .. MOD_ID
        return findExistingUnderModDir(base)
    end

    -- Strategy 0: getModInfoByID(modID):getDir() - the proper PZ 41.78 API.
    -- Returns the absolute path to the mod's root folder.
    if _G.getModInfoByID then
        local okM, modInfo = pcall(_G.getModInfoByID, MOD_ID)
        if okM and modInfo and type(modInfo.getDir) == "function" then
            local okD, modDir = pcall(function() return modInfo:getDir() end)
            if okD and type(modDir) == "string" and modDir ~= "" then
                local absPath = findExistingUnderModDir(modDir)
                if absPath then
                    if _G.print then print("[KBM][DoomApp] resolveWadPath(modInfo) -> " .. absPath) end
                    return absPath
                end
            end
        end
    end

    -- Strategy 1: try multiple Core method names that may exist in PZ 41.78.
    -- Each is wrapped in pcall - Kahlua "Tried to call nil" errors are caught silently.
    local function tryCoreMethod(methodName)
        if not _G.getCore then return nil end
        local core = getCore()
        if core == nil then return nil end
        local m = core[methodName]
        if type(m) ~= "function" then return nil end
        local ok, v = pcall(m, core)
        if ok and type(v) == "string" and v ~= "" then return v end
        return nil
    end

    local docDir =
        tryCoreMethod("getMyDocumentFolder") or
        tryCoreMethod("getMyDocFolder") or
        tryCoreMethod("getCacheDir")

    -- Strategy 1a: env var fallback (Windows USERPROFILE -> "%USERPROFILE%/Zomboid").
    if (not docDir or docDir == "") and _G.os and os.getenv then
        local up = os.getenv("USERPROFILE")
        if up and up ~= "" then
            docDir = up:gsub("[/\\]+$", "") .. "\\Zomboid"
        end
    end

    if docDir and docDir ~= "" then
        local sep = (docDir:find("/") and not docDir:find("\\")) and "/" or "\\"
        local modsDir = docDir:gsub("[/\\]+$", "") .. sep .. "mods"
        local absPath = findExistingUnderModsDir(modsDir)
        if absPath then
            if _G.print then print("[KBM][DoomApp] resolveWadPath -> " .. absPath) end
            return absPath
        end
    end

    -- Strategy 2: getModFileByName fallback (returns absolute path on some PZ builds).
    if _G.getModFileByName then
        local candidates = {}
        for _, rel in ipairs(WAD_REL_CANDIDATES) do
            candidates[#candidates + 1] = MOD_ID .. "/" .. rel
            candidates[#candidates + 1] = MOD_ID .. "\\" .. rel:gsub("/", "\\")
            candidates[#candidates + 1] = rel
            candidates[#candidates + 1] = rel:gsub("/", "\\")
        end
        for i = 1, #candidates do
            local ok, path = pcall(_G.getModFileByName, candidates[i])
            if ok and type(path) == "string" and path ~= "" then
                return path
            end
        end
    end

    -- Strategy 3: getModFileReader as last-resort presence check.
    if _G.getModFileReader then
        for _, rel in ipairs(WAD_REL_CANDIDATES) do
            local ok, reader = pcall(_G.getModFileReader, MOD_ID, rel, false)
            if ok and reader then
                if reader.close then pcall(function() reader:close() end) end
                -- Reader works but we can't extract its absolute path; bail.
                return nil
            end
        end
    end

    return nil
end

-- Build engine input bitmask from current keyboard state.
local function buildInputBits(self, ctx)
    local bits = 0
    local kd = _G.isKeyDown
    if not kd then return 0 end

    if kd(KEY_W)      or kd(KEY_UP)    then bits = bits + INPUT_FORWARD      end
    if kd(KEY_S)      or kd(KEY_DOWN)  then bits = bits + INPUT_BACKWARD     end
    -- Keyboard arrow turn now goes through the analog channel below for
    -- smoothness; binary INPUT_TURN bits are no longer set from arrows.
    if kd(KEY_A) or kd(KEY_Q) or kd(KEY_COMMA)  then bits = bits + INPUT_STRAFE_LEFT  end
    if kd(KEY_D) or kd(KEY_E) or kd(KEY_PERIOD) then bits = bits + INPUT_STRAFE_RIGHT end
    if kd(KEY_SPACE)                   then bits = bits + INPUT_USE           end
    -- Hold-to-fire: LCTRL keyboard or held mouse button. (F key still gives a
    -- short fire pulse via self.keyFireTicks for users who prefer single-tap.)
    if kd(KEY_LCTRL)                   then bits = bits + INPUT_FIRE          end
    if self.mouseHeld                  then bits = bits + INPUT_FIRE          end
    -- Native DOOM: walk by default, LSHIFT to sprint.
    if kd(KEY_LSHIFT)                  then bits = bits + INPUT_RUN           end

    -- Weapon select 1-7. DOOM only switches if the digit is held while
    -- G_BuildTiccmd runs, so we just forward the key state directly.
    if kd(KEY_1) then bits = bits + INPUT_WEAPON_1 end
    if kd(KEY_2) then bits = bits + INPUT_WEAPON_2 end
    if kd(KEY_3) then bits = bits + INPUT_WEAPON_3 end
    if kd(KEY_4) then bits = bits + INPUT_WEAPON_4 end
    if kd(KEY_5) then bits = bits + INPUT_WEAPON_5 end
    if kd(KEY_6) then bits = bits + INPUT_WEAPON_6 end
    if kd(KEY_7) then bits = bits + INPUT_WEAPON_7 end

    -- Analog turn: combine mouse dx with keyboard arrows into one delta and
    -- feed it through Engine.setMouseTurnDelta. Native DOOM consumes mousex as
    -- angleturn -= mousex * 8 each tic, giving smooth sub-tic-resolution turn.
    local turnDelta = 0
    if kd(KEY_LEFT)  then turnDelta = turnDelta - KEY_TURN_DELTA end
    if kd(KEY_RIGHT) then turnDelta = turnDelta + KEY_TURN_DELTA end

    if ctx and ctx.getMouseX then
        local ok, mx = pcall(ctx.getMouseX)
        if ok and type(mx) == "number" then
            if self.lastMouseX == nil then
                self.lastMouseX = mx
                self.lastDx = 0
            else
                local dx = mx - self.lastMouseX
                self.lastMouseX = mx
                self.lastDx = dx  -- raw, before clamping (for debug overlay)
                if dx > MOUSE_MAX_DELTA then dx = MOUSE_MAX_DELTA end
                if dx < -MOUSE_MAX_DELTA then dx = -MOUSE_MAX_DELTA end
                turnDelta = turnDelta + dx * MOUSE_TURN_SCALE
            end
        end
    end

    if turnDelta ~= 0 and self.KBMDoom and self.KBMDoom.setMouseTurnDelta then
        pcall(function() self.KBMDoom.setMouseTurnDelta(turnDelta) end)
    end

    return bits
end

-- Batch renderer: one Java call per region. Java does all block sampling, coordinate
-- mapping, and RLE compression. Returns flat "x,y,w,h,rgb," string. Lua just parses
-- and draws. Bridge crossings: 1 per region (2 per frame) instead of ~20K.
local function renderRegion(ctx, self, srcX0, srcX1, srcY0, srcY1, dstX0, dstY0, dstW, dstH, blockSize)
    local rleStr = self.KBMDoom.getRegionRLE(srcX0, srcX1, srcY0, srcY1, dstX0, dstY0, dstW, dstH, blockSize)
    if not rleStr or rleStr == "" then return 0 end
    local calls = 0
    for x, y, w, h, color in rleStr:gmatch("(%d+),(%d+),(%d+),(%d+),(%d+),") do
        local c = tonumber(color)
        local r = math.floor(c / 65536) % 256 / 255
        local g = math.floor(c / 256)   % 256 / 255
        local b = c % 256 / 255
        ctx.drawRect(tonumber(x), tonumber(y), tonumber(w), tonumber(h), 1, r, g, b)
        calls = calls + 1
    end
    return calls
end

-- Draw CRT-style scanlines over a region: one semi-transparent 1px dark band per
-- block row. Gives a subtle CRT feel with only ~blockRows draw calls.
local function drawScanlines(ctx, x0, y0, w, h, blockSize, alpha)
    if not CRT_SCANLINES_ENABLED or alpha <= 0 or w <= 0 or h <= 0 then return end
    local blockRows = math.floor(h / blockSize)
    for row = 0, blockRows - 1 do
        ctx.drawRect(x0, y0 + row * blockSize, w, 1, alpha, 0, 0, 0)
    end
end

-- Like renderRegion but caches the parsed draw-call list in a Lua table so that on
-- frames where the Java engine has NOT produced a new display (20Hz cap means ~2/3
-- of 60fps Lua frames are repeats) we skip the Java bridge call AND string parsing
-- entirely and just re-iterate the cached table.
--
-- frameIsNew: true if self.doomFrameCount advanced this Lua frame.
-- cacheKey:   string key ("game" / "hud") to store separate caches per region.
local function renderRegionCached(ctx, self, cacheKey, frameIsNew,
                                  srcX0, srcX1, srcY0, srcY1,
                                  dstX0, dstY0, dstW, dstH, blockSize)
    local cache = self.renderCache
    if not cache then
        self.renderCache = {}
        cache = self.renderCache
    end

    local cached = cache[cacheKey]
    local function drawCached(tbl)
        local n = #tbl
        local calls = 0
        for i = 1, n, 7 do
            ctx.drawRect(tbl[i], tbl[i + 1], tbl[i + 2], tbl[i + 3], 1, tbl[i + 4], tbl[i + 5], tbl[i + 6])
            calls = calls + 1
        end
        return calls
    end

    if not frameIsNew and cached then
        -- Same Java display frame - re-draw from Lua cache, no bridge call.
        return drawCached(cached)
    end

    -- New frame (or cold start) - fetch from Java, parse, cache, and draw.
    local rleStr = self.KBMDoom.getRegionRLE(srcX0, srcX1, srcY0, srcY1, dstX0, dstY0, dstW, dstH, blockSize)
    if not rleStr or rleStr == "" then
        local empty = cache[cacheKey]
        if not empty then
            empty = {}
            cache[cacheKey] = empty
        else
            for i = #empty, 1, -1 do
                empty[i] = nil
            end
        end
        cache[cacheKey .. "_raw"] = ""
        return 0
    end

    -- Static/intermission frames often produce identical payloads for many ticks.
    -- If identical, reuse parsed cache to avoid per-frame table churn and GC spikes.
    local prevRaw = cache[cacheKey .. "_raw"]
    if cached and prevRaw and prevRaw == rleStr then
        return drawCached(cached)
    end

    local calls = 0
    local newCache = cached
    if not newCache then
        newCache = {}
    else
        -- Reuse the same Lua array to avoid per-frame GC churn.
        for i = #newCache, 1, -1 do
            newCache[i] = nil
        end
    end
    local wi = 1
    for x, y, w, h, color in rleStr:gmatch("(%d+),(%d+),(%d+),(%d+),(%d+),") do
        local c = tonumber(color)
        local r = math.floor(c / 65536) % 256 / 255
        local g = math.floor(c / 256)   % 256 / 255
        local b = c % 256 / 255
        local tx, ty, tw, th = tonumber(x), tonumber(y), tonumber(w), tonumber(h)
        ctx.drawRect(tx, ty, tw, th, 1, r, g, b)
        calls = calls + 1
        newCache[wi] = tx
        newCache[wi + 1] = ty
        newCache[wi + 2] = tw
        newCache[wi + 3] = th
        newCache[wi + 4] = r
        newCache[wi + 5] = g
        newCache[wi + 6] = b
        wi = wi + 7
    end
    cache[cacheKey] = newCache
    cache[cacheKey .. "_raw"] = rleStr
    return calls
end

-- RLE-based renderer. Java returns one scanline as "x,w,rgb;x,w,rgb;..." so we
-- only cross the bridge once per source row instead of once per pixel.
-- Bridge calls: ~dstH rows. drawRect calls: ~dstH * avg_runs (~15 for DOOM).
local function renderRectRLE(ctx, self, srcX0, srcX1, srcY0, srcY1, dstX0, dstX1, dstY0, dstY1)
    local dstH = dstY1 - dstY0
    local dstW = dstX1 - dstX0
    if dstH <= 0 or dstW <= 0 then return 0 end

    local srcRangeX = srcX1 - srcX0
    local srcRangeY = srcY1 - srcY0
    local scaleX = dstW / srcRangeX
    local srcH = self.srcHeight or VIEW_HEIGHT
    local calls = 0

    for row = 0, dstH - 1 do
        local srcY = math.floor(srcY0 + row * srcRangeY / dstH)
        if srcY < 0 then srcY = 0 end
        if srcY >= srcH then srcY = srcH - 1 end

        local dstY = dstY0 + row
        local rleStr = self.KBMDoom.getRowRLE(srcY)
        if rleStr and rleStr ~= "" then
            for sx, sw, color in rleStr:gmatch("(%d+),(%d+),(%d+);") do
                sx    = tonumber(sx)
                sw    = tonumber(sw)
                color = tonumber(color)
                local ex = sx + sw
                -- Clip run to our srcX window
                if ex > srcX0 and sx < srcX1 then
                    local csx = sx < srcX0 and srcX0 or sx
                    local cex = ex > srcX1 and srcX1 or ex
                    local px = dstX0 + math.floor((csx - srcX0) * scaleX)
                    local pw = math.max(1, math.floor((cex - csx) * scaleX))
                    if px + pw > dstX1 then pw = dstX1 - px end
                    if pw > 0 then
                        local r = math.floor(color / 65536) % 256 / 255
                        local g = math.floor(color / 256)   % 256 / 255
                        local b = color % 256 / 255
                        ctx.drawRect(px, dstY, pw, 1, 1, r, g, b)
                        calls = calls + 1
                    end
                end
            end
        end
    end
    return calls
end

-- Coarse tile renderer (fallback, used when RLE is unavailable).
-- Tile count scales with block size:
-- 8px: 1000 calls, 4px: 4000 calls, 2px: 16000 calls.
local function renderRect(ctx, self, srcX0, srcX1, srcY0, srcY1, dstX0, dstX1, dstY0, dstY1, blockSize)
    local calls = 0
    local stride = self.srcWidth or VIEW_WIDTH
    local srcHeight = self.srcHeight or VIEW_HEIGHT

    local width = dstX1 - dstX0
    local height = dstY1 - dstY0
    if height <= 0 or width <= 0 then return 0 end

    local blockCols = math.floor(width / blockSize)
    local blockRows = math.floor(height / blockSize)
    if blockCols < 1 then blockCols = 1 end
    if blockRows < 1 then blockRows = 1 end

    for by = 0, blockRows - 1 do
        local srcY = math.floor(srcY0 + (by * (srcY1 - srcY0) / blockRows))
        if srcY < 0 then srcY = 0 end
        if srcY >= srcHeight then srcY = srcHeight - 1 end

        local dstY = dstY0 + by * blockSize
        local rowBase = srcY * stride

        for bx = 0, blockCols - 1 do
            local srcX = math.floor(srcX0 + (bx * (srcX1 - srcX0) / blockCols))
            if srcX < 0 then srcX = 0 end
            if srcX >= stride then srcX = stride - 1 end

            local dstX = dstX0 + bx * blockSize
            local argb = self.KBMDoom.getPixel(rowBase + srcX)
            if argb == nil then argb = 0 end

            local r0 = math.floor(argb / 65536) % 256 / 255
            local g0 = math.floor(argb / 256)   % 256 / 255
            local b0 = argb % 256 / 255

            -- Slight overlap hides fractional-scale seams between tiles.
            local drawW = blockSize + 1
            local drawH = blockSize + 1
            if dstX + drawW > dstX1 then
                drawW = dstX1 - dstX
            end
            if dstY + drawH > dstY1 then
                drawH = dstY1 - dstY
            end

            ctx.drawRect(dstX, dstY, drawW, drawH, 1, r0, g0, b0)
            calls = calls + 1
        end
    end

    return calls
end

local function getHudStats(self)
    local stats = {
        hp = "--",
        ar = "--",
        bl = "--",
        sh = "--",
        rk = "--",
        cl = "--"
    }

    if not self.KBMDoom or type(self.KBMDoom.getHudText) ~= "function" then
        return stats
    end

    local ok, hudText = pcall(function() return self.KBMDoom.getHudText() end)
    if not ok or type(hudText) ~= "string" or hudText == "" then
        return stats
    end

    for key, value in string.gmatch(hudText, "([A-Z][A-Z]):(%-?%d+)") do
        local slot = string.lower(key)
        if stats[slot] ~= nil then
            stats[slot] = tostring(value)
        end
    end

    return stats
end

local function drawHudPanel(ctx, x, y, w, h, r, g, b)
    ctx.drawRect(x, y, w, h, 1, r, g, b)
    ctx.drawRect(x, y, w, 1, 1, 0.52, 0.44, 0.29)
    ctx.drawRect(x, y + h - 1, w, 1, 1, 0.16, 0.10, 0.05)
    ctx.drawRect(x, y, 1, h, 1, 0.52, 0.44, 0.29)
    ctx.drawRect(x + w - 1, y, 1, h, 1, 0.16, 0.10, 0.05)
end

local function drawVanillaHud(ctx, self, hudY0)
    local stats = getHudStats(self)
    local calls = 0

    ctx.drawRect(0, hudY0, VIEW_WIDTH, LOWRES_HUD_HEIGHT, 1, 0.10, 0.08, 0.06)
    calls = calls + 1
    ctx.drawRect(0, hudY0, VIEW_WIDTH, 2, 1, 0.34, 0.25, 0.14)
    calls = calls + 1

    local panelY = hudY0 + 3
    local panelH = LOWRES_HUD_HEIGHT - 5
    local leftX = 4
    local leftW = 94
    local centerX = 102
    local centerW = 116
    local rightX = 222
    local rightW = 94

    drawHudPanel(ctx, leftX, panelY, leftW, panelH, 0.24, 0.18, 0.10)
    drawHudPanel(ctx, centerX, panelY, centerW, panelH, 0.28, 0.20, 0.11)
    drawHudPanel(ctx, rightX, panelY, rightW, panelH, 0.24, 0.18, 0.10)
    calls = calls + 15

    ctx.drawText("AMMO", leftX + 4, panelY + 2, 0.95, 0.81, 0.39, 1, 1)
    ctx.drawText("BL " .. stats.bl, leftX + 4, panelY + 10, 0.93, 0.93, 0.93, 1, 1)
    ctx.drawText("SH " .. stats.sh, leftX + 46, panelY + 10, 0.93, 0.93, 0.93, 1, 1)

    ctx.drawText("DOOM", centerX + 43, panelY + 1, 0.95, 0.81, 0.39, 1, 1)
    ctx.drawText("HP " .. stats.hp .. "%", centerX + 10, panelY + 10, 0.98, 0.20, 0.17, 1, 1)
    ctx.drawText("AR " .. stats.ar .. "%", centerX + 68, panelY + 10, 0.18, 0.76, 0.98, 1, 1)

    ctx.drawText("RK " .. stats.rk, rightX + 4, panelY + 10, 0.93, 0.93, 0.93, 1, 1)
    ctx.drawText("CL " .. stats.cl, rightX + 46, panelY + 10, 0.93, 0.93, 0.93, 1, 1)

    -- drawText is intentionally excluded from this counter because PixelFont is not
    -- part of the DOOM framebuffer rect pipeline we are profiling.
    return calls
end

local function renderGameplayRegion(ctx, self, frameIsNew, srcWidth, srcHudY0, gameX0, gameY0, gameW, gameH, lowresBlock)
    -- Stage 2: direct texture blit path (single draw call) with safe fallback.
    if GAMEPLAY_RENDER_MODE == "blit"
    and ctx and type(ctx.drawTextureScaled) == "function"
    and self.KBMDoom
    and type(self.KBMDoom.supportsGameplayBlit) == "function"
    and type(self.KBMDoom.getGameplayTexture) == "function" then
        local okCap, hasCap = pcall(function() return self.KBMDoom.supportsGameplayBlit() end)
        if okCap and hasCap then
            local okTex, tex = pcall(function() return self.KBMDoom.getGameplayTexture() end)
            if okTex and tex then
                local okDraw = pcall(function()
                    ctx.drawTextureScaled(tex, gameX0, gameY0, gameW, gameH, 1)
                end)
                if okDraw then
                    if not self._loggedBlitOk then
                        self._loggedBlitOk = true
                        local s, info = "?", "?"
                        if type(self.KBMDoom.getFrameTextureStatus) == "function" then
                            local okS, sv = pcall(self.KBMDoom.getFrameTextureStatus)
                            if okS then s = tostring(sv) end
                        end
                        if type(self.KBMDoom.getFrameTextureInfo) == "function" then
                            local okI, iv = pcall(self.KBMDoom.getFrameTextureInfo)
                            if okI then info = tostring(iv) end
                        end
                        print("[KBM_DOOM] blit path ACTIVE - drew first textured frame (status="
                              .. s .. ", size=" .. info .. ")")
                    end
                    self.gameplayRenderPath = "blit"
                    return 1
                end
            end
        end
    end

    self.gameplayRenderPath = "rle"
    return renderRegionCached(ctx, self, "game", frameIsNew, 0, srcWidth, 0, srcHudY0, gameX0, gameY0, gameW, gameH, lowresBlock)
end

-- Render test texture probe: auto-sequential ladder (create -> upload -> draw).
-- Each stage runs exactly once; advances only on success; stops and reports on first failure.
local function renderTestTextureProbe(ctx, self)
    if not TEST_TEXTURE_PROBE_ENABLED or not self.KBMDoom then return 0 end

    if not self.experimentalProbeAllowed then
        self.testProbeStage = -1
        self.testProbeStatus = "probe_blocked_bridge"
        return 0
    end

    local stage = self.testProbeStage

    -- Stage 0 -> 1: create texture object
    if stage == 0 then
        -- Set stage defensively BEFORE the Java call.
        -- If pcall fails to catch the exception (PZ Kahlua quirk), the next frame
        -- will see stage=-1 and skip, preventing a per-frame crash loop.
        self.testProbeStage = -1
        self.testProbeStatus = "trying_create"
        stage = -1
        pcall(function()
            if type(self.KBMDoom.destroyTestTexture) == "function" then
                self.KBMDoom.destroyTestTexture()
            end
        end)
        if type(self.KBMDoom.runTextureProbe) == "function" then
            local ok, status = pcall(function() return self.KBMDoom.runTextureProbe("create") end)
            if ok and (status == "ok_create" or status == "ok_upload") then
                stage = 1
                self.testProbeStage = 1
            else
                self.testProbeStatus = ok and tostring(status or "java_create_failed") or "pcall_err_create"
            end
        elseif type(self.KBMDoom.createTestTextureObjectOnly) ~= "function" then
            self.testProbeStatus = "no_method_create"
        else
            local ok, result = pcall(function() return self.KBMDoom.createTestTextureObjectOnly() end)
            if ok and result then
                stage = 1
                self.testProbeStage = 1
            else
                self.testProbeStatus = ok and "java_returned_false_create" or "pcall_err_create"
            end
        end
    end

    -- Stage 1 -> 2: upload pixel data
    if stage == 1 then
        self.testProbeStage = -1
        self.testProbeStatus = "trying_upload"
        stage = -1
        if type(self.KBMDoom.runTextureProbe) == "function" then
            local ok, status = pcall(function() return self.KBMDoom.runTextureProbe("upload") end)
            if ok and (status == "ok_upload") then
                stage = 2
                self.testProbeStage = 2
            else
                self.testProbeStatus = ok and tostring(status or "java_upload_failed") or "pcall_err_upload"
            end
        elseif type(self.KBMDoom.uploadTestTextureSolid) ~= "function" then
            self.testProbeStatus = "no_method_upload"
        else
            local ok, result = pcall(function() return self.KBMDoom.uploadTestTextureSolid() end)
            if ok and result then
                stage = 2
                self.testProbeStage = 2
            else
                self.testProbeStatus = ok and "java_returned_false_upload" or "pcall_err_upload"
            end
        end
    end

    -- Sync status/error strings from Java after any one-shot work
    if type(self.KBMDoom.getTestTextureProbeStatus) == "function" then
        local ok, s = pcall(function() return self.KBMDoom.getTestTextureProbeStatus() end)
        if ok and type(s) == "string" and s ~= "" then self.testProbeStatus = s end
    end
    if type(self.KBMDoom.getTestTextureProbeError) == "function" then
        local ok, e = pcall(function() return self.KBMDoom.getTestTextureProbeError() end)
        if ok and type(e) == "string" then self.testProbeError = e end
    end

    if stage < 2 then return 0 end

    -- Stage 2 -> 3: draw (attempted every frame once upload passed)
    if not ctx or type(ctx.drawTextureScaled) ~= "function" then return 0 end
    local okTex, tex = pcall(function() return self.KBMDoom.getTestTexture() end)
    if not okTex or not tex then
        self.testProbeStage = -1
        self.testProbeStatus = "lua_get_failed"
        return 0
    end
    local okDraw = pcall(function()
        ctx.drawTextureScaled(tex, TEST_TEXTURE_PROBE_X, TEST_TEXTURE_PROBE_Y, TEST_TEXTURE_PROBE_SIZE, TEST_TEXTURE_PROBE_SIZE, 1)
    end)
    if okDraw then
        self.testProbeStage = 3
        self.testProbeStatus = "ok_draw"
        return 1
    else
        self.testProbeStage = -1
        self.testProbeStatus = "lua_draw_failed"
        return 0
    end
end

local function renderCoarse(ctx, self)
    local blockSize = self.blockSize or 8
    local srcHeight = self.srcHeight or VIEW_HEIGHT
    local srcWidth = self.srcWidth or VIEW_WIDTH

    -- ============================================================
    -- FAST PATH: full-frame texture blit.
    --
    -- The DOOM pixel buffer is the COMPLETE 320x200 game image with the
    -- status bar already baked in at the bottom. When the blit path is
    -- available we just upload it once and stretch it to fill the entire
    -- 320x200 virtual canvas. No letterbox, no pillarbox, no RLE HUD.
    -- This is 1-2 draw calls per frame instead of ~2300.
    -- ============================================================
    if GAMEPLAY_RENDER_MODE == "blit"
    and ctx and type(ctx.drawTextureScaled) == "function"
    and self.KBMDoom
    and type(self.KBMDoom.supportsGameplayBlit) == "function"
    and type(self.KBMDoom.getGameplayTexture) == "function" then
        local okCap, hasCap = pcall(function() return self.KBMDoom.supportsGameplayBlit() end)
        if okCap and hasCap then
            local okTex, tex = pcall(function() return self.KBMDoom.getGameplayTexture() end)
            if okTex and tex then
                local okDraw = pcall(function()
                    ctx.drawTextureScaled(tex, 0, 0, VIEW_WIDTH, VIEW_HEIGHT, 1)
                end)
                if okDraw then
                    if not self._loggedFullBlitOk then
                        self._loggedFullBlitOk = true
                        if _G.print then
                            print("[KBM_DOOM] full-frame blit ACTIVE - HUD baked into texture, RLE HUD bypassed")
                        end
                    end
                    self.gameplayRenderPath = "blit"
                    self.gameplayCalls = 1
                    self.hudCalls = 0
                    return 1
                end
            end
        end
    end

    -- Frame dedup: only call into Java when the engine has produced a new display frame.
    -- At 20Hz engine cap with 60fps Lua, ~2/3 of frames are repeats and skip the bridge.
    local frameIsNew = true
    if self.KBMDoom and type(self.KBMDoom.getFrameCount) == "function" then
        local ok, fc = pcall(function() return self.KBMDoom.getFrameCount() end)
        if ok and type(fc) == "number" then
            frameIsNew = (fc ~= (self.doomFrameCount or -1))
            self.doomFrameCount = fc
        end
    end
    -- Also invalidate cache when adaptive quality changed (rects have different coords).
    if not frameIsNew and self.renderCache then
        if (self.renderCache._blockSize ~= (self.lowresBlockSize or LOWRES_BLOCK_MIN))
        or (self.renderCache._quantBits ~= (self.quantBits or COLOR_QUANT_BITS)) then
            frameIsNew = true
        end
    end
    if frameIsNew and self.renderCache then
        self.renderCache._blockSize = self.lowresBlockSize or LOWRES_BLOCK_MIN
        self.renderCache._quantBits = self.quantBits or COLOR_QUANT_BITS
    end

    if LOWRES_GAME_VIEW_ENABLED then
        local lowresBlock = self.lowresBlockSize or LOWRES_BLOCK_MIN
        local gameX0 = math.floor((VIEW_WIDTH - GAME_VIEW_WIDTH) / 2)
        local gameX1 = gameX0 + GAME_VIEW_WIDTH
        -- Center the gameplay region vertically in the space above the HUD strip.
        local availH = VIEW_HEIGHT - LOWRES_HUD_HEIGHT
        local gameY0 = math.floor((availH - GAME_VIEW_HEIGHT) / 2)
        if gameY0 < 0 then gameY0 = 0 end
        local gameY1 = gameY0 + GAME_VIEW_HEIGHT

        local hudY0 = VIEW_HEIGHT - LOWRES_HUD_HEIGHT
        local hudY1 = VIEW_HEIGHT

        -- Black bars above and below the game view (letterbox in the gameplay area).
        if gameY0 > 0 then
            ctx.drawRect(0, 0, VIEW_WIDTH, gameY0, 1, 0, 0, 0)
        end
        if hudY0 > gameY1 then
            ctx.drawRect(0, gameY1, VIEW_WIDTH, hudY0 - gameY1, 1, 0, 0, 0)
        end
        -- Pillarbox: fill left/right margins when game area is narrower than viewport (4:3 mode).
        if gameX0 > 0 then
            ctx.drawRect(0, gameY0, gameX0, GAME_VIEW_HEIGHT, 1, 0, 0, 0)
            ctx.drawRect(gameX0 + GAME_VIEW_WIDTH, gameY0, gameX0, GAME_VIEW_HEIGHT, 1, 0, 0, 0)
        end

        local srcHudY0 = math.floor(srcHeight * 0.84)
        if srcHudY0 < 0 then srcHudY0 = 0 end
        if srcHudY0 > srcHeight then srcHudY0 = srcHeight end

        local callsGame = renderGameplayRegion(ctx, self, frameIsNew, srcWidth, srcHudY0, gameX0, gameY0, GAME_VIEW_WIDTH, GAME_VIEW_HEIGHT, lowresBlock)
        drawScanlines(ctx, gameX0, gameY0, GAME_VIEW_WIDTH, GAME_VIEW_HEIGHT, lowresBlock, CRT_SCANLINE_ALPHA)
        if LOWRES_MESSAGE_BAND_ENABLED and self.KBMDoom and type(self.KBMDoom.getMessageText) == "function" then
            local msg = ""
            local okMsg, txt = pcall(function() return self.KBMDoom.getMessageText() end)
            if okMsg and type(txt) == "string" then msg = txt end
            if msg ~= nil and msg ~= "" then
                local bandH = LOWRES_MESSAGE_BAND_HEIGHT
                if bandH < 8 then bandH = 8 end
                if bandH > GAME_VIEW_HEIGHT then bandH = GAME_VIEW_HEIGHT end
                local bandY = gameY0 + GAME_VIEW_HEIGHT - bandH
                ctx.drawRect(gameX0, bandY, GAME_VIEW_WIDTH, bandH, 1, 0, 0, 0)
                ctx.drawText(msg, gameX0 + 4, bandY + 2, 0.92, 0.92, 0.92, 1, 1)
                callsGame = callsGame + 1
            end
        end
        local callsHud = 0
        if HUD_RENDER_MODE == "sprite" or LOWRES_TEXT_HUD_ENABLED then
            -- Stage 1 HUD extraction: keep gameplay on RLE path, draw HUD separately.
            callsHud = drawVanillaHud(ctx, self, hudY0)
        elseif HUD_RENDER_MODE == "mini_vanilla" then
            -- Render the real DOOM status bar strip, but scaled into a smaller centered HUD.
            local miniW = MINI_VANILLA_HUD_WIDTH
            local miniH = MINI_VANILLA_HUD_HEIGHT
            if miniW < 64 then miniW = 64 end
            if miniW > VIEW_WIDTH then miniW = VIEW_WIDTH end
            if miniH < 12 then miniH = 12 end
            if miniH > LOWRES_HUD_HEIGHT then miniH = LOWRES_HUD_HEIGHT end

            local miniX = math.floor((VIEW_WIDTH - miniW) / 2)
            local miniY = VIEW_HEIGHT - miniH - MINI_VANILLA_HUD_BOTTOM_MARGIN
            if miniY < GAME_VIEW_HEIGHT then miniY = GAME_VIEW_HEIGHT end

            -- Small bezel/backdrop for visibility without a full-width band.
            ctx.drawRect(miniX - 2, miniY - 1, miniW + 4, miniH + 2, 1, 0.05, 0.05, 0.05)
            callsHud = callsHud + 1

            callsHud = callsHud + renderRegionCached(
                ctx, self, "hudmini", frameIsNew,
                0, srcWidth, srcHudY0, srcHeight,
                miniX, miniY, miniW, miniH,
                MINI_VANILLA_HUD_BLOCK_SIZE
            )
        else
            -- Fallback path for A/B testing.
            callsHud = renderRegionCached(ctx, self, "hud", frameIsNew, 0, srcWidth, srcHudY0, srcHeight, 0, hudY0, VIEW_WIDTH, LOWRES_HUD_HEIGHT, LOWRES_HUD_BLOCK_SIZE)
        end
        self.gameplayCalls = callsGame
        self.hudCalls = callsHud
        return callsGame + callsHud
    end

    if HUD_DETAIL_ENABLED then
        local hudY0 = VIEW_HEIGHT - HUD_HEIGHT
        if hudY0 < 0 then hudY0 = 0 end

        local srcHudY0 = math.floor(srcHeight * hudY0 / VIEW_HEIGHT)
        local srcHudY1 = srcHeight

        local callsTop = renderRect(ctx, self, 0, srcWidth, 0, srcHudY0, 0, VIEW_WIDTH, 0, hudY0, blockSize)
        local callsHud = renderRect(ctx, self, 0, srcWidth, srcHudY0, srcHudY1, 0, VIEW_WIDTH, hudY0, VIEW_HEIGHT, 2)
        return callsTop + callsHud
    end

    return renderRect(ctx, self, 0, srcWidth, 0, srcHeight, 0, VIEW_WIDTH, 0, VIEW_HEIGHT, blockSize)
end

local function setQualityLevel(self, level)
    local maxLevel = #QUALITY_LEVELS
    if level < 1 then level = 1 end
    if level > maxLevel then level = maxLevel end

    local blockSize = QUALITY_LEVELS[level]
    self.qualityLevel = level
    self.blockSize = blockSize
    self.blockCols = math.floor(VIEW_WIDTH / blockSize)
    self.blockRows = math.floor(VIEW_HEIGHT / blockSize)
end

local function hasBit(bits, mask)
    return math.floor((bits or 0) / mask) % 2 ~= 0
end

local function isGameplayInputActive(bits)
    return hasBit(bits, INPUT_FORWARD)
        or hasBit(bits, INPUT_BACKWARD)
        or hasBit(bits, INPUT_TURN_LEFT)
        or hasBit(bits, INPUT_TURN_RIGHT)
        or hasBit(bits, INPUT_STRAFE_LEFT)
        or hasBit(bits, INPUT_STRAFE_RIGHT)
        or hasBit(bits, INPUT_FIRE)
        or hasBit(bits, INPUT_USE)
end

local function logTick(self, bits)
    if not DEBUG_TICK_LOG_ENABLED then return end
    local n = (self.tickLogCounter or 0) + 1
    self.tickLogCounter = n
    if (n % DEBUG_TICK_LOG_EVERY) ~= 0 then return end

    local msg = string.format(
        "[DOOM-TICK] n=%d dt=%.3f acc=%.3f ticks=%d up=%.2fms ren=%.2fms avgRen=%.2f blk=%d dx=%.2f bits=0x%03X TL=%d TR=%d FIRE=%d",
        n,
        self.lastDt or 0,
        self.tickAccumulator or 0,
        self.lastTicks or 0,
        self.lastUpdateMs or 0,
        self.lastRenderMs or 0,
        self.lowresRenderAvgMs or 0,
        self.lowresBlockSize or LOWRES_BLOCK_MIN,
        self.lastDx or 0,
        bits or 0,
        hasBit(bits, INPUT_TURN_LEFT) and 1 or 0,
        hasBit(bits, INPUT_TURN_RIGHT) and 1 or 0,
        hasBit(bits, INPUT_FIRE) and 1 or 0
    )

    if _G.print then
        _G.print(msg)
    else
        print(msg)
    end
end

local function updateMotionQuant(self, bits, renderMs, drawCalls)
    if not MOTION_FALLBACK_ENABLED then return end
    if not self.KBMDoom or type(self.KBMDoom.setQuantizationBits) ~= "function" then return end

    local moving = isGameplayInputActive(bits or 0)
        or math.abs(self.lastDx or 0) >= MOUSE_TURN_THRESHOLD
        or (type(renderMs) == "number" and renderMs >= MOTION_RENDER_MS_TRIGGER)
        or (type(drawCalls) == "number" and drawCalls >= MOTION_DRAWCALL_TRIGGER)

    if moving then
        self.motionHighFrames = (self.motionHighFrames or 0) + 1
        self.motionLowFrames = 0
    else
        self.motionLowFrames = (self.motionLowFrames or 0) + 1
        self.motionHighFrames = 0
    end

    local baseQ = COLOR_QUANT_BITS
    local q = self.quantBits or baseQ
    local targetQ = q

    if (self.motionHighFrames or 0) >= MOTION_ENTER_FRAMES then
        targetQ = MOTION_QUANT_BITS
    elseif (self.motionLowFrames or 0) >= MOTION_EXIT_FRAMES then
        targetQ = baseQ
    end

    if targetQ < COLOR_QUANT_MIN then targetQ = COLOR_QUANT_MIN end
    if targetQ > COLOR_QUANT_MAX then targetQ = COLOR_QUANT_MAX end

    if targetQ ~= q then
        self.quantBits = targetQ
        pcall(function() self.KBMDoom.setQuantizationBits(targetQ) end)
    end
end

local function adaptLowresBlockSize(self, renderMs, drawCalls)
    -- During intermission (tally screen) or finale, force crisp block size regardless of perf.
    -- GS_INTERMISSION = kills/items/secrets/time screen between levels.
    -- GS_FINALE       = episode end text/graphic.
    local gs = self.doomGameState
    if gs == "GS_INTERMISSION" or gs == "GS_FINALE" then
        local targetBlock = INTERMISSION_FORCE_BLOCK
        if targetBlock < 1 then targetBlock = 1 end
        self.lowresBlockSize = targetBlock
        if LOWRES_DYNAMIC_QUANT_ENABLED and self.KBMDoom and type(self.KBMDoom.setQuantizationBits) == "function" then
            local tq = INTERMISSION_FORCE_QUANT
            if (self.quantBits or COLOR_QUANT_BITS) ~= tq then
                self.quantBits = tq
                pcall(function() self.KBMDoom.setQuantizationBits(tq) end)
            end
        end
        return
    end

    -- Leaving intermission/finale: restore the default lowres block size.
    -- Without this, the forced block=2 from the branch above sticks forever
    -- once we re-enter GS_LEVEL, pinning us to the heaviest render path and
    -- causing frametime spikes during regular gameplay.
    if self.lowresBlockSize ~= LOWRES_BLOCK_MIN then
        self.lowresBlockSize = LOWRES_BLOCK_MIN
    end

    if not LOWRES_DYNAMIC_BLOCK_ENABLED and not LOWRES_DYNAMIC_QUANT_ENABLED then return end
    if type(renderMs) ~= "number" then return end
    if type(drawCalls) ~= "number" then drawCalls = self.drawCalls or 0 end

    -- Smooth render-time jitter so quality can recover instead of getting stuck.
    if type(self.lowresRenderAvgMs) ~= "number" or self.lowresRenderAvgMs <= 0 then
        self.lowresRenderAvgMs = renderMs
    else
        self.lowresRenderAvgMs = (self.lowresRenderAvgMs * (1 - LOWRES_EWMA_ALPHA)) + (renderMs * LOWRES_EWMA_ALPHA)
    end

    -- Smooth draw-call jitter; this tracks actual UI submission pressure.
    if type(self.lowresDrawCallsAvg) ~= "number" or self.lowresDrawCallsAvg <= 0 then
        self.lowresDrawCallsAvg = drawCalls
    else
        self.lowresDrawCallsAvg = (self.lowresDrawCallsAvg * (1 - LOWRES_DRAWCALL_EWMA_ALPHA)) + (drawCalls * LOWRES_DRAWCALL_EWMA_ALPHA)
    end

    local metric = self.lowresRenderAvgMs
    local block = self.lowresBlockSize or LOWRES_BLOCK_MIN

    -- Hard lock quality in static/idle scenes (intermission, tally screens).
    -- In this immediate-mode UI we still redraw each frame, so this lock prevents
    -- readability flicker from adaptive oscillation while nothing is happening.
    local idleTicks = self.idleTicks or 0
    if READABILITY_IDLE_LOCK_ENABLED and idleTicks >= READABILITY_IDLE_TICKS then
        if LOWRES_DYNAMIC_BLOCK_ENABLED then
            local targetBlock = READABILITY_IDLE_FORCE_BLOCK
            if targetBlock < LOWRES_BLOCK_MIN then targetBlock = LOWRES_BLOCK_MIN end
            if targetBlock > LOWRES_BLOCK_MAX then targetBlock = LOWRES_BLOCK_MAX end
            if block ~= targetBlock then
                self.lowresBlockSize = targetBlock
                block = targetBlock
            end
        end

        if LOWRES_DYNAMIC_QUANT_ENABLED and self.KBMDoom and type(self.KBMDoom.setQuantizationBits) == "function" then
            local targetQ = READABILITY_IDLE_FORCE_QUANT
            if targetQ < 0 then targetQ = 0 end
            if targetQ > COLOR_QUANT_MAX then targetQ = COLOR_QUANT_MAX end
            if (self.quantBits or COLOR_QUANT_BITS) ~= targetQ then
                self.quantBits = targetQ
                pcall(function() self.KBMDoom.setQuantizationBits(targetQ) end)
            end
        end

        self.lowresHighMsFrames = 0
        self.lowresLowMsFrames = 0
        self.lowresHighPressureFrames = 0
        self.lowresLowPressureFrames = 0
        return
    end

    if LOWRES_DYNAMIC_BLOCK_ENABLED then
    local idleReadable = (self.idleTicks or 0) >= READABILITY_IDLE_TICKS and metric <= READABILITY_IDLE_MAX_RENDER_MS
    local maxBlock = LOWRES_BLOCK_MAX
    if idleReadable and READABILITY_IDLE_FORCE_BLOCK < maxBlock then
        maxBlock = READABILITY_IDLE_FORCE_BLOCK
    end

    if idleReadable and block > maxBlock then
        self.lowresBlockSize = maxBlock
        block = maxBlock
    end
    local upshiftMs = LOWRES_UPSHIFT_MS
    local upshiftFrames = LOWRES_UPSHIFT_FRAMES
    if SHOWCASE_2PX_GUARD_ENABLED and block <= LOWRES_BLOCK_MIN then
        upshiftMs = SHOWCASE_2PX_HARD_UPSHIFT_MS
        upshiftFrames = SHOWCASE_2PX_HARD_UPSHIFT_FRAMES
    end

    if metric >= upshiftMs then
        self.lowresHighMsFrames = (self.lowresHighMsFrames or 0) + 1
        self.lowresLowMsFrames = 0
    elseif metric <= LOWRES_DOWNSHIFT_MS then
        self.lowresLowMsFrames = (self.lowresLowMsFrames or 0) + 1
        self.lowresHighMsFrames = 0
    else
        self.lowresHighMsFrames = 0
        self.lowresLowMsFrames = 0
    end

    local downshiftFrames = LOWRES_DOWNSHIFT_FRAMES
    if (self.lowresHighMsFrames or 0) >= upshiftFrames and block < maxBlock then
        self.lowresBlockSize = block + 1
        self.lowresHighMsFrames = 0
        self.lowresLowMsFrames = 0
    elseif (self.lowresLowMsFrames or 0) >= downshiftFrames and block > LOWRES_BLOCK_MIN then
        self.lowresBlockSize = block - 1
        self.lowresHighMsFrames = 0
        self.lowresLowMsFrames = 0
    end
    else
        self.lowresHighMsFrames = 0
        self.lowresLowMsFrames = 0
    end

    if LOWRES_DYNAMIC_QUANT_ENABLED and self.KBMDoom and type(self.KBMDoom.setQuantizationBits) == "function" then
        local q = self.quantBits or COLOR_QUANT_BITS
        local quantMin = COLOR_QUANT_MIN
        local targetQ = q

        -- Draw-call pressure and render-time pressure both drive quantization.
        local highPressure = (metric >= LOWRES_QUANT_UPSHIFT_MS) or (self.lowresDrawCallsAvg >= LOWRES_QUANT_UPSHIFT_CALLS)
        local lowPressure = (metric <= LOWRES_QUANT_DOWNSHIFT_MS) and (self.lowresDrawCallsAvg <= LOWRES_QUANT_DOWNSHIFT_CALLS)

        if highPressure then
            self.lowresHighPressureFrames = (self.lowresHighPressureFrames or 0) + 1
            self.lowresLowPressureFrames = 0
        elseif lowPressure then
            self.lowresLowPressureFrames = (self.lowresLowPressureFrames or 0) + 1
            self.lowresHighPressureFrames = 0
        else
            self.lowresHighPressureFrames = 0
            self.lowresLowPressureFrames = 0
        end

        if (self.lowresHighPressureFrames or 0) >= LOWRES_QUANT_UPSHIFT_FRAMES and q < COLOR_QUANT_MAX then
            targetQ = q + 1
        elseif (self.lowresLowPressureFrames or 0) >= LOWRES_QUANT_DOWNSHIFT_FRAMES and q > quantMin then
            targetQ = q - 1
        end

        if targetQ ~= q then
            self.quantBits = targetQ
            self.lowresHighPressureFrames = 0
            self.lowresLowPressureFrames = 0
            pcall(function() self.KBMDoom.setQuantizationBits(targetQ) end)
        end
    end
end

-- Runtime-safe monotonic-ish timestamp in milliseconds.
-- Kahlua environments may not expose os.clock().
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
    return nil
end

-- ------------------------------------------------------------
-- App lifecycle
-- ------------------------------------------------------------

-- Called once when the app opens. Locates the Java DOOM bridge, finds
-- DOOM1.WAD on disk, boots the engine, and gets ready to render.
function DoomApp:init(ctx)
    -- Destroy any previously running engine before reinitialising so that
    -- inserting a different cart never leaves the old WAD playing in the
    -- background (Java bridge is a static singleton).
    if self.KBMDoom then
        pcall(function() self.KBMDoom.destroy() end)
        self.KBMDoom = nil
    end
    self.wadEntry     = nil
    self.wadFilename  = nil
    self.pwadPath     = nil
    self.error        = nil
    self.halfRes      = true
    self.drawCalls    = 0
    self.gameplayCalls = 0
    self.hudCalls = 0
    self.gameplayRenderPath = "rle"
    self.frameCount   = 0
    self.lastBudgetCheckFrame = 0
    self.tickAccumulator = 0
    self.srcWidth = VIEW_WIDTH
    self.srcHeight = VIEW_HEIGHT
    self.lastMouseX = nil
    self.lastDx = 0
    self.lastDt = 0
    self.lastBits = 0
    self.lastTicks = 0
    self.lastUpdateMs = 0
    self._lastUpdateWallMs = nil  -- set on first DoomApp:update; bootstrap dt = DOOM_TICK_DT

    -- No NES Console attached to a nearby TV: show "no signal" instead of running.
    self.noSignal = (ctx and type(ctx.args) == "table" and ctx.args.noSignal) and true or false
    if self.noSignal then return end
    self.lastRenderMs = 0
    self.lowresRenderAvgMs = 0
    self.lowresDrawCallsAvg = 0
    self.lowresBlockSize = LOWRES_BLOCK_MIN
    self.quantBits = COLOR_QUANT_BITS
    self.lowresHighMsFrames = 0
    self.lowresLowMsFrames = 0
    self.lowresHighPressureFrames = 0
    self.lowresLowPressureFrames = 0
    self.lastPresentMs = nil
    self.renderSkippedFrames = 0
    self.tickLogCounter = 0
    self.idleTicks = 0
    self.motionHighFrames = 0
    self.motionLowFrames = 0
    self.mouseFireTicks = 0
    self.keyFireTicks = 0
    self.mouseHeld = false
    self.doomFrameCount = -1
    self.renderCache = {}
    self.testProbeStage = 0   -- 0=not started, 1=created, 2=uploaded, 3=drawn, -1=failed
    self.testProbeStatus = "pending"
    self.testProbeError = ""
    setQualityLevel(self, START_QUALITY_LEVEL)

    local diag = {}

    -- Locate the ZombieBuddy-exposed KBMDoom static facade table.
    local KBMDoom = findKBMDoomTable(diag)
    self.experimentalProbeAllowed = false
    self.bridgeVersion = "unknown"
    self.bridgeCaps = ""
    if KBMDoom then
        if type(KBMDoom.getBridgeVersion) == "function" then
            local okVer, ver = pcall(function() return KBMDoom.getBridgeVersion() end)
            if okVer and type(ver) == "string" then
                self.bridgeVersion = ver
            end
        end
        if type(KBMDoom.getCapabilities) == "function" then
            local okCap, caps = pcall(function() return KBMDoom.getCapabilities() end)
            if okCap and type(caps) == "string" then
                self.bridgeCaps = caps
            end
        end
        if self.bridgeVersion == EXPECTED_BRIDGE_VERSION
        and string.find(self.bridgeCaps, "run_texture_probe", 1, true)
        and type(KBMDoom.runTextureProbe) == "function" then
            self.experimentalProbeAllowed = true
            table.insert(diag, "bridge:probe-api ok " .. tostring(self.bridgeVersion))
        else
            table.insert(diag, "bridge:probe-api disabled ver=" .. tostring(self.bridgeVersion)
                .. " caps=" .. tostring(self.bridgeCaps))
        end
    end


    if not KBMDoom then
        self.error = "KBMDoom Java bridge not exposed.\n"
            .. table.concat(diag, "\n") .. "\n"
            .. "Ensure ZombieBuddy is loaded and KBMConsolesNES.jar is deployed."
        return
    end

    -- Determine which WAD to load. Priority:
    --   1) ctx.wadId, ctx.args.wadId or self.wadId set by caller
    --      (cartridge insertion via NESRadioPatch passes wadId in ctx.args)
    --   2) DoomApp.requestedWadId set via setRequestedWad()
    --   3) WadCatalog.default() (Freedoom Phase 1 if shipped, else DOOM1.WAD)
    --   4) DOOM1.WAD legacy default
    local catalog = require("KBMGB/Games/CRT/WadCatalog")
    local wadFilename = "DOOM1.WAD"
    local wadEntry = nil
    local ctxArgsWadId = (ctx and ctx.args and ctx.args.wadId) or nil
    local requestedId = (ctx and ctx.wadId) or ctxArgsWadId or self.wadId or DoomApp.requestedWadId
    if catalog then
        if requestedId then wadEntry = catalog.get(requestedId) end
        if not wadEntry and catalog.default then wadEntry = catalog.default() end
    end
    if wadEntry and type(wadEntry.filename) == "string" and wadEntry.filename ~= "" then
        wadFilename = wadEntry.filename
    end
    self.wadEntry    = wadEntry
    self.wadFilename = wadFilename

    -- Resolve WAD path. If the entry declares a PWAD, it's a "standalone
    -- cartridge" that pairs an IWAD + PWAD under the hood for supported
    -- etc). When the preferred IWAD is missing on disk, fall back to the
    -- entry's iwadFallback (typically Freedoom) so the cartridge still
    -- runs without requiring the player to supply a commercial IWAD.
    local wadPath = resolveWadPath(wadFilename)
    if not wadPath and wadEntry and type(wadEntry.iwadFallback) == "string" and catalog then
        local fb = catalog.get(wadEntry.iwadFallback)
        if fb and type(fb.filename) == "string" and fb.filename ~= "" then
            local fbPath = resolveWadPath(fb.filename)
            if fbPath then
                if _G.print then
                    print("[KBM][DoomApp] iwad '" .. tostring(wadFilename)
                        .. "' missing; using fallback '" .. tostring(fb.filename) .. "'")
                end
                wadPath = fbPath
                wadFilename = fb.filename
                self.wadFilename = wadFilename
            end
        end
    end

    -- Resolve optional PWAD if the entry declares one.
    local pwadPath = nil
    if wadEntry and type(wadEntry.pwad) == "string" and wadEntry.pwad ~= "" then
        pwadPath = resolveWadPath(wadEntry.pwad)
        if not pwadPath then
            self.error = "PWAD '" .. tostring(wadEntry.pwad) .. "' not found.\n"
                .. "Drop the file into common/media/data/ to enable this cartridge."
            if _G.print then print("[KBM][DoomApp] " .. self.error:gsub("\n", " | ")) end
            return
        end
    end
    self.pwadPath = pwadPath
    if not wadPath then
        -- Build a diagnostic string so we can see what path resolution attempted.
        local docDir = "?"
        if _G.getCore then
            local core = getCore()
            if core then
                local methods = { "getMyDocumentFolder", "getMyDocFolder", "getCacheDir" }
                for i = 1, #methods do
                    local m = core[methods[i]]
                    if type(m) == "function" then
                        local ok2, d = pcall(m, core)
                        if ok2 and type(d) == "string" and d ~= "" then
                            docDir = methods[i] .. "=" .. d
                            break
                        end
                    end
                end
            end
        end
        local up = (_G.os and os.getenv) and os.getenv("USERPROFILE") or "?"
        local modInfoStatus = "no-api"
        if _G.getModInfoByID then
            local okM, mi = pcall(_G.getModInfoByID, "KBM_Consoles")
            if not okM or not mi then
                modInfoStatus = "nil"
            elseif type(mi.getDir) ~= "function" then
                modInfoStatus = "no-getDir"
            else
                local okD, d = pcall(function() return mi:getDir() end)
                modInfoStatus = (okD and tostring(d)) or "getDir-failed"
            end
        end
        self.error = (wadFilename or "WAD") .. " not found.\n"
            .. "ModInfo: " .. modInfoStatus .. "\n"
            .. "Core: " .. docDir .. "\n"
            .. "USERPROFILE: " .. tostring(up) .. "\n"
            .. "getModFileByName=" .. tostring(_G.getModFileByName ~= nil)
            .. " getModFileReader=" .. tostring(_G.getModFileReader ~= nil)
            .. " getModInfoByID=" .. tostring(_G.getModInfoByID ~= nil)
        if _G.print then print("[KBM][DoomApp] " .. self.error:gsub("\n", " | ")) end
        return
    end

    if _G.print then
        print("[KBM][DoomApp] launch wadId=" .. tostring(requestedId)
            .. " iwad=" .. tostring(wadPath)
            .. (pwadPath and (" pwad=" .. tostring(pwadPath)) or ""))
    end

    -- Initialize the engine via static call (no Java instance touches Lua).
    -- If the bridge exposes frontend APIs, allow per-WAD startup mode
    -- (title loop/menu/immediate game) with fallback to legacy init.
    local startupMode = (wadEntry and type(wadEntry.startupMode) == "string" and wadEntry.startupMode) or "immediate_game"
    local startupMap = wadEntry and wadEntry.startupMap or nil
    local startupEpisode = wadEntry and wadEntry.startupEpisode or nil
    local startupSkill = wadEntry and wadEntry.startupSkill or nil

    self.frontendRequestedMode = startupMode
    self.frontendBridgeEnabled = bridgeSupportsFrontend(KBMDoom)

    local ok, initErr
    if self.frontendBridgeEnabled and type(KBMDoom.initAdvanced) == "function" then
        ok, initErr = pcall(function()
            return KBMDoom.initAdvanced(wadPath, pwadPath, startupMode, startupMap, startupEpisode, startupSkill)
        end)
        if not ok and _G.print then
            print("[KBM][DoomApp] initAdvanced failed; falling back to init: " .. tostring(initErr))
        end
    end

    if not ok then
        if _G.print then
            if self.frontendBridgeEnabled then
                print("[KBM][DoomApp] initAdvanced unavailable or failed; using legacy init (startupMode=" .. tostring(startupMode) .. ")")
            else
                print("[KBM][DoomApp] frontend bridge not supported; using legacy init (startupMode=" .. tostring(startupMode) .. ")")
            end
        end
        ok, initErr = pcall(function() return KBMDoom.init(wadPath, pwadPath) end)
    end

    if not ok then
        self.error = "DoomEngine.init() failed:\nwad=" .. tostring(wadPath)
            .. (pwadPath and ("\npwad=" .. tostring(pwadPath)) or "")
            .. "\n" .. tostring(initErr)
        return
    end

    self.KBMDoom = KBMDoom

    -- Reduce color entropy before Java-side run merging to cut draw calls.
    if type(KBMDoom.setQuantizationBits) == "function" then
        pcall(function() KBMDoom.setQuantizationBits(COLOR_QUANT_BITS) end)
    end

    if type(KBMDoom.setGameplayBlitEnabled) == "function" then
        pcall(function() KBMDoom.setGameplayBlitEnabled(GAMEPLAY_RENDER_MODE == "blit") end)
    end

    -- Engine-side display cap (no flashing): keeps simulation timing intact.
    if type(KBMDoom.setDisplayHz) == "function" then
        pcall(function() KBMDoom.setDisplayHz(ENGINE_DISPLAY_HZ) end)
    end

    -- Native DOOM physics: keep player momentum so run/walk feel right and the
    -- player slides naturally on stops. Setting instantStop=true zeroes momx/momy
    -- each tick which removes momentum entirely (player stops on a dime, top
    -- speed only reaches first-tick acceleration).
    if type(KBMDoom.setInstantStop) == "function" then
        pcall(function() KBMDoom.setInstantStop(false) end)
    end

    -- Read source framebuffer size from Java so we can scale to the CRT view.
    local okW, w = pcall(function() return KBMDoom.getWidth() end)
    local okH, h = pcall(function() return KBMDoom.getHeight() end)
    if okW and type(w) == "number" and w > 0 then self.srcWidth = w end
    if okH and type(h) == "number" and h > 0 then self.srcHeight = h end
end

-- Called every frame while running. Reads the keyboard, sends actions to
-- DOOM, and ticks the engine forward to keep up with real time.
function DoomApp:update(ctx, dt)
    if not self.KBMDoom then return end
    if self.error then return end

    local updateStart = nowMs()

    -- AppHost feeds a fixed dt = 1 / ticksPerSecond per call, which only matches
    -- real time if prerender fires at exactly ticksPerSecond. PZ's render rate
    -- varies (30-60+ Hz depending on view/scene), so the fixed dt drifts the
    -- DOOM clock against wall-clock time and the player ends up walking too
    -- slow (or too fast). Compute real elapsed seconds from nowMs() instead so
    -- the sim stays locked to 35Hz of real time regardless of PZ frame rate.
    local nowMsVal = updateStart
    local realDt
    if self._lastUpdateWallMs and nowMsVal then
        realDt = (nowMsVal - self._lastUpdateWallMs) / 1000
    else
        realDt = dt or DOOM_TICK_DT
    end
    self._lastUpdateWallMs = nowMsVal
    realDt = clamp(realDt or 0, 0, 0.25)
    self.lastDt = realDt
    self.tickAccumulator = self.tickAccumulator + realDt

    -- Polled hold-to-fire. PZ's onMouseDown dispatch to the panel has been
    -- unreliable (capture-mouse + DOOM render thread interactions), so we
    -- also poll the global mouse state every frame: if LMB is down AND the
    -- mouse is over our window, treat it as held-fire. This is in addition
    -- to onMouseDown/Up so both paths converge to the same self.mouseHeld.
    do
        local lmb = false
        if _G.isMouseButtonDown then
            local ok, v = pcall(_G.isMouseButtonDown, 0)
            if ok then lmb = v and true or false end
        end
        local over = false
        local ui = ctx and ctx.ui
        if lmb and ui and ui.isMouseOver then
            local ok, v = pcall(ui.isMouseOver, ui)
            if ok then over = v and true or false end
        end
        local newHeld = lmb and over
        if newHeld and not self._polledMouseHeld then
            -- Edge: just pressed. Reset turn baseline so we don't get a
            -- spurious dx jump from any cursor movement during the click.
            if ctx and ctx.getMouseX then
                local ok2, mx = pcall(ctx.getMouseX)
                if ok2 and type(mx) == "number" then self.lastMouseX = mx end
            end
            if not self._loggedMouseFire then
                self._loggedMouseFire = true
                if _G.print then print("[KBM_DOOM] mouse-fire path active (polled LMB)") end
            end
        end
        self._polledMouseHeld = newHeld
        -- mouseHeld becomes the union of the click-event signal and the poll.
        self.mouseHeld = (self.mouseHeld or false) or newHeld
        if not lmb then
            -- LMB released globally: clear held flag regardless of which path set it.
            self.mouseHeld = false
        end
    end

    local baseBits = buildInputBits(self, ctx)
    if self.frontendCapture then
        pumpFrontendInput(self)
        -- Frontend/menu/title loop consumes key input through sendMenuInput.
        -- Keep gameplay input bits at zero while frontend state is active.
        baseBits = 0
    else
        self._frontendLatch = nil
    end
    self.lastBits = baseBits
    local ticks = 0
    while self.tickAccumulator >= DOOM_TICK_DT and ticks < MAX_TICKS_PER_UPDATE do
        self.tickAccumulator = self.tickAccumulator - DOOM_TICK_DT
        ticks = ticks + 1

        -- Fire pulses only consumed when a DOOM tick actually runs.
        -- Decrementing in buildInputBits (every PZ frame) loses them before DOOM sees them.
        local bits = baseBits
        if (self.mouseFireTicks or 0) > 0 then
            bits = bits + INPUT_FIRE
            self.mouseFireTicks = self.mouseFireTicks - 1
        end
        if (self.keyFireTicks or 0) > 0 then
            bits = bits + INPUT_FIRE
            self.keyFireTicks = self.keyFireTicks - 1
        end

        if isGameplayInputActive(bits) then
            self.idleTicks = 0
        else
            self.idleTicks = (self.idleTicks or 0) + 1
        end

        logTick(self, bits)

        local ok, err = pcall(function() return self.KBMDoom.tick(bits) end)
        if not ok then
            self.error = "DoomEngine.tick() error:\n" .. tostring(err)
            return
        end
        if type(self.KBMDoom.isTickFatal) == "function" and type(self.KBMDoom.getTickFatalReason) == "function" then
            local okFatal, isFatal = pcall(function() return self.KBMDoom.isTickFatal() end)
            if okFatal and isFatal then
                local okReason, reason = pcall(function() return self.KBMDoom.getTickFatalReason() end)
                self.error = "DoomEngine.tick() became fatal:\n"
                    .. (okReason and tostring(reason) or "unknown")
                if _G.print then print("[KBM][DoomApp] " .. self.error:gsub("\n", " | ")) end
                return
            end
        end
    end

    self.lastTicks = ticks

    -- Poll game state for intermission/finale detection (once per update, cheap reflection read)
    if self.KBMDoom and type(self.KBMDoom.getGameState) == "function" then
        local ok, gs = pcall(function() return self.KBMDoom.getGameState() end)
        self.doomGameState = (ok and type(gs) == "string") and gs or "UNKNOWN"
    end
    if self.KBMDoom and type(self.KBMDoom.getFrontendState) == "function" then
        local okFs, fs = pcall(function() return self.KBMDoom.getFrontendState() end)
        self.doomFrontendState = (okFs and type(fs) == "string") and fs or "unknown"
        self.frontendCapture = FRONTEND_CAPTURE_STATES[self.doomFrontendState] == true
    else
        self.doomFrontendState = "unsupported"
        self.frontendCapture = false
    end

    local updateEnd = nowMs()
    if updateStart and updateEnd then
        self.lastUpdateMs = updateEnd - updateStart
    else
        self.lastUpdateMs = 0
    end
end

-- Mouse click inside the CRT screen - used for the test texture probe and
-- a few interactive debug helpers.
function DoomApp:onMouseDown(ctx, x, y)
    -- Reset mouse delta baseline to avoid a turn spike.
    -- IMPORTANT: must use ctx.getMouseX() (virtual canvas coords), NOT the panel-local
    -- click x, otherwise next frame computes a large dx between two coordinate systems
    -- and the view jumps right on every click.
    if ctx and ctx.getMouseX then
        local ok, mx = pcall(ctx.getMouseX)
        if ok and type(mx) == "number" then
            self.lastMouseX = mx
        else
            self.lastMouseX = nil
        end
    else
        self.lastMouseX = nil
    end

    if MOUSE_CLICK_FIRE then
        self.mouseFireTicks = MOUSE_FIRE_TICKS
    end
    self.mouseHeld = true
    if not self._loggedMouseFire then
        self._loggedMouseFire = true
        if _G.print then print("[KBM_DOOM] mouse-fire path active (first click received)") end
    end
    return true
end

-- Mouse button released: drop the held-fire flag.
function DoomApp:onMouseUp(_ctx, _x, _y)
    self.mouseHeld = false
    return true
end

-- Called every frame to draw. Pulls the current DOOM frame from the engine
-- and paints it onto the CRT screen, plus the HUD and any debug overlays.
function DoomApp:render(ctx)
    local renderStart = nowMs()

    -- "No signal" state: NES Console not connected to a nearby TV.
    if self.noSignal then
        ctx.fillScreen(1, 0.02, 0.02, 0.02)
        local frame = (self.frameCount or 0)
        self.frameCount = frame + 1
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
        if ctx.ui and ctx.ui.drawText then
            ctx.ui:drawText("NO SIGNAL", ctx.x + 8, ctx.y + 8, 1, 1, 1, 1, UIFont.Small)
            ctx.ui:drawText("Connect an NES Console to a TV nearby.",
                ctx.x + 8, ctx.y + 24, 0.8, 0.8, 0.8, 1, UIFont.Small)
        end
        local renderEnd = nowMs()
        if renderStart and renderEnd then self.lastRenderMs = renderEnd - renderStart else self.lastRenderMs = 0 end
        return
    end

    -- Error state: show message and bail
    if self.error then
        ctx.fillScreen(1, 0, 0, 0)
        -- Use panel drawText directly (works without PixelFont)
        if ctx.ui and ctx.ui.drawText then
            local maxW = (ctx.hostWidth or ctx.w or 200) - 8
            local tm = _G.getTextManager and getTextManager() or nil
            local font = UIFont.Small
            -- Hard char-cap fallback: ~6px per char at UIFont.Small.
            local hardCharCap = math.max(10, math.floor(maxW / 6))

            local function fits(s)
                if tm then return tm:MeasureStringX(font, s) <= maxW end
                return #s <= hardCharCap
            end

            -- Word-wrap a single segment to fit within maxW.
            local function wrapLine(text, out)
                -- First split on spaces; if no spaces, treat the whole text as one word.
                local words = {}
                for w in (text .. " "):gmatch("(%S+)%s") do words[#words + 1] = w end
                if #words == 0 then out[#out + 1] = ""; return end

                local cur = ""
                for i = 1, #words do
                    local word = words[i]
                    -- Hard-break very long single words by chunking.
                    while not fits(word) and #word > 1 do
                        -- Find longest prefix that fits.
                        local n = #word
                        if not tm then
                            n = math.min(hardCharCap, #word)
                        else
                            local lo, hi = 1, #word
                            while lo < hi do
                                local mid = math.floor((lo + hi + 1) / 2)
                                if tm:MeasureStringX(font, word:sub(1, mid)) <= maxW then
                                    lo = mid
                                else
                                    hi = mid - 1
                                end
                            end
                            n = lo
                        end
                        if cur ~= "" then out[#out + 1] = cur; cur = "" end
                        out[#out + 1] = word:sub(1, n)
                        word = word:sub(n + 1)
                    end
                    local trial = (cur == "") and word or (cur .. " " .. word)
                    if fits(trial) then
                        cur = trial
                    else
                        if cur ~= "" then out[#out + 1] = cur end
                        cur = word
                    end
                end
                if cur ~= "" then out[#out + 1] = cur end
            end

            local lines = {}
            for segment in (self.error .. "\n"):gmatch("([^\n]*)\n") do
                if segment == "" then lines[#lines + 1] = "" else wrapLine(segment, lines) end
            end

            ctx.ui:drawText("DOOM LOAD ERROR", ctx.x + 4, ctx.y + 4, 1, 0.3, 0.3, 1, font)
            local y = ctx.y + 20
            local maxY = ctx.y + (ctx.hostHeight or ctx.h or 200) - 12
            for i = 1, #lines do
                if y > maxY then break end
                ctx.ui:drawText(lines[i], ctx.x + 4, y, 0.8, 0.8, 0.8, 1, font)
                y = y + 12
            end
        end
        local renderEnd = nowMs()
        if renderStart and renderEnd then self.lastRenderMs = renderEnd - renderStart else self.lastRenderMs = 0 end
        return
    end

    if not self.KBMDoom then
        ctx.fillScreen(1, 0, 0, 0)
        if ctx.ui and ctx.ui.drawText then
            ctx.ui:drawText("DOOM: Initializing...", ctx.x + 4, ctx.y + 4, 0.5, 0.8, 0.8, 1, UIFont.Small)
        end
        local renderEnd = nowMs()
        if renderStart and renderEnd then self.lastRenderMs = renderEnd - renderStart else self.lastRenderMs = 0 end
        return
    end

    -- Keep sim at 35Hz, but cap costly visual presentation rate.
    local now = nowMs()
    if RENDER_CAP_ENABLED and now and self.lastPresentMs then
        if (now - self.lastPresentMs) < RENDER_MIN_DT_MS then
            self.renderSkippedFrames = (self.renderSkippedFrames or 0) + 1
            return
        end
    end
    if now then
        self.lastPresentMs = now
    end

    local totalCalls = renderCoarse(ctx, self)
    self.drawCalls = totalCalls
    self.frameCount = self.frameCount + 1

    -- Render test texture probe in corner (diagnostic overlay)
    if TEST_TEXTURE_PROBE_ENABLED then
        local testCalls = renderTestTextureProbe(ctx, self)
        local stage = self.testProbeStage or 0
        local status = tostring(self.testProbeStatus or "?")
        local statusLabel = "PROBE[" .. stage .. "]:" .. status
        local isOk = (status == "ok_draw" or status == "ok_upload" or status == "ok_create")
        local r, g, b = 1, 1, 0  -- yellow = pending/in-progress
        if isOk then r, g, b = 0.6, 1, 0.6       -- green = success
        elseif stage == -1 then r, g, b = 1, 0.4, 0.4  -- red = failed
        end
        ctx.drawText(statusLabel, TEST_TEXTURE_PROBE_X, TEST_TEXTURE_PROBE_Y, r, g, b, 1, 1)
        if self.testProbeError and self.testProbeError ~= "" then
            local shortErr = self.testProbeError
            if #shortErr > 80 then shortErr = shortErr:sub(1, 80) .. "..." end
            ctx.drawText(shortErr, TEST_TEXTURE_PROBE_X, TEST_TEXTURE_PROBE_Y + 10, 1, 0.7, 0.7, 0.7, 1)
        end
    end

    if CROSSHAIR_ENABLED then
        local cx = math.floor(VIEW_WIDTH / 2)
        local cy = math.floor(VIEW_HEIGHT / 2)
        local s = CROSSHAIR_SIZE
        ctx.drawRect(cx - s, cy, s * 2 + 1, 1, 1, 1, 1, 1)
        ctx.drawRect(cx, cy - s, 1, s * 2 + 1, 1, 1, 1, 1)
    end

    if DEBUG_OVERLAY then
        -- Semi-transparent black background strip
        ctx.drawRect(0, 0, VIEW_WIDTH, 48, 0.75, 0, 0, 0)
        local dx   = self.lastDx or 0
        local bits = self.lastBits or 0
        local acc  = self.tickAccumulator or 0
        local dt   = self.lastDt or 0
        local fps  = dt > 0 and math.floor(1 / dt) or 0
        -- Turn indicator: show raw dx and whether threshold was crossed
        local turnStr = "dx=" .. string.format("%.1f", dx)
        if dx <= -MOUSE_TURN_THRESHOLD then turnStr = turnStr .. " <L"
        elseif dx >= MOUSE_TURN_THRESHOLD then turnStr = turnStr .. " R>"
        else turnStr = turnStr .. " (no turn, thr=" .. MOUSE_TURN_THRESHOLD .. ")"
        end
        -- Input bits as readable string
        local bStr = "bits=0x" .. string.format("%03X", bits)
        if math.floor(bits / INPUT_FORWARD)  % 2 ~= 0 then bStr = bStr .. " FWD"  end
        if math.floor(bits / INPUT_BACKWARD) % 2 ~= 0 then bStr = bStr .. " BACK" end
        if math.floor(bits / INPUT_TURN_LEFT)  % 2 ~= 0 then bStr = bStr .. " TL"  end
        if math.floor(bits / INPUT_TURN_RIGHT) % 2 ~= 0 then bStr = bStr .. " TR"  end
        if math.floor(bits / INPUT_FIRE) % 2 ~= 0 then bStr = bStr .. " FIRE" end
        ctx.drawText(turnStr, 2, 2,  1, 1, 0.2, 0.2, 1)
        ctx.drawText(bStr,    2, 12, 1, 1, 0.6, 1,   0.6, 1)
        ctx.drawText("dt=" .. string.format("%.1fms", dt*1000) .. " fps=" .. fps .. " acc=" .. string.format("%.3f", acc), 2, 22, 1, 0.8, 0.8, 0.8, 1)
        ctx.drawText("ticks=" .. (self.lastTicks or 0) .. " up=" .. string.format("%.2f", self.lastUpdateMs or 0) .. "ms ren=" .. string.format("%.2f", self.lastRenderMs or 0) .. "ms", 2, 32, 1, 0.7, 0.7, 1, 1)
        ctx.drawText("drawCalls=" .. (self.drawCalls or 0) .. " game=" .. (self.gameplayCalls or 0) .. " hud=" .. (self.hudCalls or 0) .. " gMode=" .. GAMEPLAY_RENDER_MODE .. "/" .. (self.gameplayRenderPath or "rle") .. " blk=" .. (self.lowresBlockSize or LOWRES_BLOCK_MIN) .. " hudMode=" .. HUD_RENDER_MODE .. " q=" .. (self.quantBits or COLOR_QUANT_BITS) .. " avgRen=" .. string.format("%.1f", self.lowresRenderAvgMs or 0) .. " skip=" .. (self.renderSkippedFrames or 0) .. "  Tab=hide", 2, 42, 1, 0.5, 0.5, 0.5, 1)
        if self.KBMDoom and type(self.KBMDoom.getBlitStatus) == "function" then
            local okS, s = pcall(function() return self.KBMDoom.getBlitStatus() end)
            local okE, e = pcall(function() return self.KBMDoom.getBlitError() end)
            local blitLine = "blit=" .. (okS and tostring(s) or "?")
            if okE and e and e ~= "" then blitLine = blitLine .. " err=" .. tostring(e):sub(1,120) end
            ctx.drawText(blitLine, 2, 52, 1, 1, 0.7, 0.4, 1)
        end
    end

    local renderEnd = nowMs()
    if renderStart and renderEnd then
        self.lastRenderMs = renderEnd - renderStart
        self.lastPresentMs = renderEnd
    else
        self.lastRenderMs = 0
    end
    updateMotionQuant(self, self.lastBits, self.lastRenderMs, self.drawCalls)
    adaptLowresBlockSize(self, self.lastRenderMs, self.drawCalls)
end

-- Handles a single key press. Used for non-action keys (TAB debug, quality
-- presets 1-7, Q to cycle quality, etc.) - actual movement/fire is read
-- continuously by buildInputBits during update().
function DoomApp:onKey(ctx, key)
    -- Frontend/menu input is handled in update() by pumpFrontendInput() with
    -- key-edge detection. Swallow nav keys here to avoid duplicate sends.
    if self.frontendCapture then
        if key == KEY_DELETE then
            ctx.close()
            return true
        end
        if key == KEY_UP or key == KEY_DOWN or key == KEY_LEFT or key == KEY_RIGHT
            or key == KEY_RETURN or key == KEY_SPACE
            or key == KEY_ESCAPE or key == KEY_BACK or key == KEY_TAB then
            return true
        end
    end

    -- Tab toggles the debug overlay (dx, bits, fps, accumulator).
    if key == KEY_TAB then
        DEBUG_OVERLAY = not DEBUG_OVERLAY
        return true
    end
    -- for its own hotkeys which bleed through and cause phantom fire.
    -- Guard against key-repeat: only arm if not already pending.
    if key == KEY_F then
        if (self.keyFireTicks or 0) == 0 then
            self.keyFireTicks = KEY_FIRE_TICKS
        end
        return true
    end

    -- F2: hard restart - tear the engine down and re-init with the same
    -- WAD. This is the only reliable way to get back to the title screen
    -- once the player is mid-level, since the bridge does not currently
    -- expose G_DeferedNewGame.
    if key == KEY_F2 then
        if self.KBMDoom and type(self.KBMDoom.restartToTitle) == "function" then
            local okR = pcall(function() return self.KBMDoom.restartToTitle() end)
            if okR then return true end
        end
        self:restart(ctx)
        return true
    end

    -- ESCAPE / BACKSPACE / DELETE: close the app (return to terminal).
    -- Matches the Game Boy / NES exit convention so players have a consistent
    -- "back" button across all emulated devices.
    if key == KEY_ESCAPE or key == KEY_BACK or key == KEY_DELETE then
        ctx.close()
        return true
    end
    -- All other keys are handled by isKeyDown polling in update().
    -- Returning false lets AppHost propagate normally.
    return false
end

-- Hard restart: destroy the current engine instance and re-run init() with
-- the same ctx so the WAD selection (Doom shareware vs Freedoom etc.) is
-- preserved. Equivalent to closing and reopening the app, but keeps the
-- AppHost window open so the player stays seated.
function DoomApp:restart(ctx)
    if self.KBMDoom then
        pcall(function() self.KBMDoom.destroy() end)
        self.KBMDoom = nil
    end
    self:init(ctx)
end

-- Called when the window closes. Tells the engine to stop and frees any
-- per-frame buffers, so reopening starts cleanly.
--- Set which WAD the NEXT DoomApp:init() call should load. The id must
--- match an entry in WadCatalog.lua. Pass nil to clear and use the default.
--- Safe to call from UI code; takes effect on the next launch.
function DoomApp.setRequestedWad(id)
    if id == nil or (type(id) == "string" and id ~= "") then
        DoomApp.requestedWadId = id
    end
end

--- Returns the currently requested WAD id, or nil if no override is set.
function DoomApp.getRequestedWad()
    return DoomApp.requestedWadId
 end

function DoomApp:shutdown(ctx)
    if self.KBMDoom then
        pcall(function() self.KBMDoom.destroy() end)
        self.KBMDoom = nil
    end
    -- Clear per-launch WAD state so a subsequent init() for a different
    -- cart cannot accidentally inherit the previous selection.
    self.wadEntry    = nil
    self.wadFilename = nil
    self.pwadPath    = nil
end

return DoomApp
