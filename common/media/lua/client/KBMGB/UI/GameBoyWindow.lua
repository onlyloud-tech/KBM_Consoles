-- GameBoyWindow.lua
-- The pop-up window the player sees when they use a Game Boy handheld.
-- It draws the Game Boy frame artwork, runs the emulator app inside the
-- screen area and routes keyboard input to the emulator while it has focus.
-- Shared init / focus / prerender / render / close come from AppWindowBase;
-- we override :new(), :getAppViewport(), and the input handlers.

local AppWindowBase = require("KBMGB/UI/AppWindowBase")
local AppHost       = require("KBMGB/System/AppHost")
local VTUtils       = require("KBMGB/System/VTUtils")
require "TimedActions/ISBaseTimedAction"
require "TimedActions/ISTimedActionQueue"

local GameBoyWindow = AppWindowBase:derive("GameBoyWindow")

local function screenBridge()
    if type(KBMViewpointScreenProbe) == "table" then return KBMViewpointScreenProbe end
    if type(com) == "table" and type(com.knoxboxprobe) == "table" then
        return com.knoxboxprobe.KBMViewpointScreenProbe
    end
end

-- Viewpoint.View.next() cycles isometric -> first -> third -> isometric.
-- The next mode is "first" only while the current view is isometric.
local function inViewpoint3D()
    local view = Viewpoint and Viewpoint.View
    if not view or not view.next then return false end
    local ok, nextMode = pcall(view.next)
    return ok and (nextMode == "third" or nextMode == "isometric")
end

-- Keep the real handheld in the player's hands while its emulator window is
-- open. The timed action drives the same held-model override as the camcorder.
local GameBoyHeldAction = ISBaseTimedAction:derive("KBMGameBoyHeldAction")

function GameBoyHeldAction:isValid()
    return self.window and not self.window._closing
        and not self.window.hiddenForMovement and self.device ~= nil
end

function GameBoyHeldAction:start()
    local player = self.character
    self.previousPrimary = player:getPrimaryHandItem()
    self.changedPrimary = self.previousPrimary ~= self.device
    if self.changedPrimary then player:setPrimaryHandItem(self.device) end
    self:setActionAnim("kbmplaygameboy")
    self:setOverrideHandModels(nil, self.device)
end

function GameBoyHeldAction:restoreHand()
    if self.changedPrimary and self.character:getPrimaryHandItem() == self.device then
        self.character:setPrimaryHandItem(self.previousPrimary)
    end
    self.changedPrimary = false
    if self.window and self.window.heldAction == self then self.window.heldAction = nil end
end

function GameBoyHeldAction:stop()
    self:restoreHand()
    ISBaseTimedAction.stop(self)
end

function GameBoyHeldAction:perform()
    self:restoreHand()
    ISBaseTimedAction.perform(self)
end

function GameBoyHeldAction:new(player, device, window)
    local action = ISBaseTimedAction.new(self, player)
    action.device = device
    action.window = window
    action.maxTime = 1000000
    action.useProgressBar = false
    action.stopOnWalk = false
    action.stopOnRun = false
    return action
end

-- The Game Boy frame PNG is 352x479. The screen "hole" inside that artwork
-- starts at (49, 77) and is 255x174 pixels. We use these numbers to figure
-- out where to draw the emulator picture inside the frame.
local CAL_W = 352
local CAL_H = 479
local VIEW_X = 49
local VIEW_Y = 77
local VIEW_W = 255
local VIEW_H = 174

-- Shell colour variants. Each handheld item type maps to one of these
-- keys via GameBoyRadioPatch.DEVICE_VARIANTS; the value is the path to
-- the artwork PNG. All variants share the same calibration (CAL_W/CAL_H/
-- VIEW_*) because the screen window position is identical across PNGs.
local SHELL_VARIANTS = {
    classic = "media/ui/gb_frame.png",
    green   = "media/ui/green_shell.png",
    grey    = "media/ui/grey_shell.png",
    red     = "media/ui/red_shell.png",
    yellow  = "media/ui/yellow_shell.png",
    blue    = "media/ui/blue_tparent_shell.png",
    pink    = "media/ui/pink_tparent_shell.png",
    clear   = "media/ui/tparent_shell.png",
}
local SHELL_VARIANT_KEYS = {
    "classic", "green", "grey", "red", "yellow", "blue", "pink", "clear",
}
GameBoyWindow.SHELL_VARIANTS = SHELL_VARIANTS
GameBoyWindow.SHELL_VARIANT_KEYS = SHELL_VARIANT_KEYS

-- Resolves a shellVariant key into an overlay PNG path. Unknown / missing
-- keys fall back to the original "classic" artwork so we never crash.
local function resolveOverlayPath(variantKey)
    if type(variantKey) == "string" and SHELL_VARIANTS[variantKey] then
        return SHELL_VARIANTS[variantKey]
    end
    return SHELL_VARIANTS.classic
end

-- Tries to read a width or height from a texture, falling back if the first
-- call doesn't work. Used to figure out the real size of the GB frame PNG.
local function textureDimension(tex, primaryName, fallbackName)
    if not tex then return nil end
    local primary = tex[primaryName]
    if type(primary) == "function" then
        local ok, value = pcall(primary, tex)
        if ok and type(value) == "number" and value > 0 then return value end
    end
    local fallback = tex[fallbackName]
    if type(fallback) == "function" then
        local ok, value = pcall(fallback, tex)
        if ok and type(value) == "number" and value > 0 then return value end
    end
    return nil
end

-- Returns the natural pixel dimensions of the overlay texture, falling
-- back to the calibration constants if the texture object can't be queried.
local function getOverlaySize(tex)
    local width = textureDimension(tex, "getWidthOrig", "getWidth")
    local height = textureDimension(tex, "getHeightOrig", "getHeight")
    if width and height then
        return math.floor(width), math.floor(height)
    end
    return CAL_W, CAL_H
end

-- Builds a new Game Boy window: figures out a sensible size, centers it on
-- screen, and sets a transparent background so only the artwork shows.
function GameBoyWindow:new(monitorUI, AppModule, args)
    local sw, sh = VTUtils.safeGetScreen()
    args = args or {}
    local shellVariant = args.shellVariant
    local overlayPath = resolveOverlayPath(shellVariant)
    local overlayTex = getTexture(overlayPath)
    -- Last-resort fallback if the configured overlay PNG is missing.
    if not overlayTex and overlayPath ~= SHELL_VARIANTS.classic then
        overlayTex = getTexture(SHELL_VARIANTS.classic)
    end
    local baseW, baseH = getOverlaySize(overlayTex)
    -- Display scale: the entire shell artwork (and therefore the screen
    -- hole inside it) scales by this factor. 1.0 = native PNG size. We
    -- pick the largest scale that still fits on screen, capped at 1.75x
    -- so the device doesn't dwarf the rest of the UI.
    local maxScaleW = (sw - 80) / baseW
    local maxScaleH = (sh - 40) / baseH
    local scale = math.min(1.75, maxScaleW, maxScaleH)
    if scale < 1.0 then scale = 1.0 end
    local ww = math.floor(baseW * scale)
    local wh = math.floor(baseH * scale)
    -- Final clamp in case the screen is unusually small.
    if ww > sw - 20 then ww = sw - 20 end
    if wh > sh - 20 then wh = sh - 20 end
    local x = math.floor((sw - ww) / 2)
    local y = math.floor((sh - wh) / 2)

    local o = AppWindowBase.new(self, x, y, ww, wh)
    setmetatable(o, self)
    self.__index = self

    o.monitorUI = monitorUI
    o.AppModule = AppModule
    o.args = args
    o.moveWithMouse = false
    o.backgroundColor = { r = 0, g = 0, b = 0, a = 0 }
    o.borderColor = { r = 0, g = 0, b = 0, a = 0 }
    o.modalLockId = "gameboywindow"
    o.overlayTex = overlayTex
    o.shellVariant = shellVariant or "classic"
    o.baseW = baseW
    o.baseH = baseH
    o.captureMouse = true   -- AppWindowBase:_ensureTopAndFocus will hold capture
    o.viewpointScreenOnly = false
    o.hiddenForMovement = false
    o.playerObj = (args and args.playerObj)
        or (_G.getSpecificPlayer and getSpecificPlayer(0))
        or (_G.getPlayer and getPlayer())
    return o
end

function GameBoyWindow:startHeldPose(device)
    if not device or not self.playerObj or self.heldAction then return end
    self.heldDevice = device
    self.heldAction = GameBoyHeldAction:new(self.playerObj, device, self)
    ISTimedActionQueue.add(self.heldAction)
    local probe = screenBridge()
    local variant = tostring(self.shellVariant or "classic")
    local model = "KBM.KBM_GameBoy_" .. variant:sub(1, 1):upper() .. variant:sub(2)
    if probe and probe.setGameBoyMirror then
        local ok, err = pcall(probe.setGameBoyMirror, model, true)
        self.viewpointMirror = ok
        _G.KBM_GB_3D_MIRROR_ACTIVE = ok
        if ok then
            self._lastViewpoint3D = inViewpoint3D()
            self.viewpointScreenOnly = self._lastViewpoint3D
            -- The hidden 2D window still receives keyboard events, but the
            -- player can move the cursor while using the 3D handheld.
            self.captureMouse = false
            if self.setCapture then pcall(self.setCapture, self, false) end
        end
        if not ok then print("[KBM][GameBoyWindow] 3D screen unavailable: " .. tostring(err)) end
    end
end

function GameBoyWindow:releaseToWorld()
    if self.hiddenForMovement or self._closing then return end
    self.hiddenForMovement = true
    self.resumeKeyArmed = false
    self.releasedAtMs = VTUtils.nowMs()
    self.captureMouse = false
    if self.app then
        if self.app.realtimeActive and self.app.realtime then
            pcall(function() self.app.realtime.stop() end)
            self.app.realtimeActive = false
        end
        if self.app.kbm then pcall(function() self.app.kbm.setPadMask(1, 0) end) end
        self.app.lastPadMask = 0
        self.app.emuAcc = 0
    end
    _G.KBM_GB_3D_MIRROR_ACTIVE = false
    if self.viewpointMirror then
        local probe = screenBridge()
        if probe and probe.setGameBoyMirror then pcall(probe.setGameBoyMirror, "", false) end
        self.viewpointMirror = false
    end
    if self.heldAction then
        local action = self.heldAction
        self.heldAction = nil
        if action.action then action:forceStop() else action:forceCancel() end
    end
    if self.setCapture then pcall(self.setCapture, self, false) end
    if self.setWantKeyEvents then pcall(self.setWantKeyEvents, self, false) end
    self:setVisible(false)
end

function GameBoyWindow:resumePlaying()
    if not self.hiddenForMovement or self._closing or not self.heldDevice then return end
    self.hiddenForMovement = false
    self.captureMouse = true
    self:startHeldPose(self.heldDevice)
    if self.app and self.app.mode == "running" then
        if self.app.realtime then
            local ok, started = pcall(function() return self.app.realtime.start() end)
            self.app.realtimeActive = ok and started == true
            self.app.lastWorkerFrames = 0
        end
        self.app.lastUpdateSec = nil
        self.app.emuAcc = 0
    end
    self:setVisible(true)
    self:_ensureTopAndFocus()
    if self.bringToFront then self:bringToFront() end
end

function GameBoyWindow:prerender()
    if self.viewpointMirror then
        local in3D = inViewpoint3D()
        if in3D ~= self._lastViewpoint3D then
            self._lastViewpoint3D = in3D
            self.viewpointScreenOnly = in3D
        end
    end
    AppWindowBase.prerender(self)
end

function GameBoyWindow:close()
    if self._closing then return end
    self._closing = true
    _G.KBM_GB_3D_MIRROR_ACTIVE = false
    if self.viewpointMirror then
        local probe = screenBridge()
        if probe and probe.setGameBoyMirror then pcall(probe.setGameBoyMirror, "", false) end
    end
    if self.heldAction then
        local action = self.heldAction
        self.heldAction = nil
        if action.action then action:forceStop() else action:forceCancel() end
    end
    if self.args and self.args.onClosed then pcall(self.args.onClosed, self) end
    AppWindowBase.close(self)
end

-- Calculates where the emulator picture should be drawn (the "screen hole"
-- inside the Game Boy frame), scaled to whatever size the window currently is.
function GameBoyWindow:getAppViewport()
    local baseW = tonumber(self.baseW) or CAL_W
    local baseH = tonumber(self.baseH) or CAL_H
    local sx = self.width / baseW
    local sy = self.height / baseH
    return math.floor(VIEW_X * sx),
           math.floor(VIEW_Y * sy),
           math.floor(VIEW_W * sx),
           math.floor(VIEW_H * sy)
end

-- Eat all mouse clicks; we don't want clicks to fall through to the world.
function GameBoyWindow:onMouseDown(_x, _y)
    self:_ensureTopAndFocus()
    return true
end

function GameBoyWindow:onRightMouseDown(_x, _y) return true end
function GameBoyWindow:onMouseUp(_x, _y) return true end
function GameBoyWindow:onMouseUpOutside(_x, _y) return true end

-- Swallows raw key-down events so PZ's global hotkeys (M for map, etc.)
-- don't fire while the player is using the Game Boy.
function GameBoyWindow:onKeyDown(_key)
    return true
end

-- Handles "real" key presses: Backspace or Delete closes the window
-- (we don't use ESC because PZ steals it for the pause menu); everything
-- else is forwarded to the running emulator app.
function GameBoyWindow:onKeyPress(key)
    if key == Keyboard.KEY_E and _G.isKeyDown
        and (_G.isKeyDown(Keyboard.KEY_LMENU)
            or _G.isKeyDown(Keyboard.KEY_RMENU)) then
        self:releaseToWorld()
        return true
    end
    if self.viewpointMirror and key == Keyboard.KEY_F9 then
        self.viewpointScreenOnly = not self.viewpointScreenOnly
        return true
    end
    if key == Keyboard.KEY_BACK or key == Keyboard.KEY_DELETE then
        self:close()
        return true
    end
    if self.app then
        AppHost.onKey(self.app, self, key)
    end
    return true
end

function GameBoyWindow:render()
    if self.viewpointScreenOnly then return end
    AppWindowBase.render(self)
end

return GameBoyWindow
