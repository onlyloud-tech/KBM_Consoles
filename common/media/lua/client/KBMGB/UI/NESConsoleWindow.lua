-- NESConsoleWindow.lua
-- The pop-up window the player sees when they use an NES console. Shows a
-- TV-overlay graphic with a transparent "screen" hole, and runs the NES
-- emulator app inside that hole. While open, the player's character can't
-- move or shoot. Click the four screen corners to recalibrate the hole if
-- the overlay art ever changes.
-- Shared init / focus / prerender / close come from AppWindowBase; the
-- render override below adds calibration debug dots on top of the default.

local AppWindowBase = require("KBMGB/UI/AppWindowBase")
local AppHost       = require("KBMGB/System/AppHost")
local VTUtils       = require("KBMGB/System/VTUtils")

local NESConsoleWindow = AppWindowBase:derive("NESConsoleWindow")

-- Base dimensions and calibrated screen bounds of the current KnoxBox TV shell.
local BASE_W = 800
local BASE_H = 735

-- Screen viewport within the TV overlay (base pixel coordinates).
-- These define where the transparent TV screen hole sits in the overlay.
-- Click on the four screen corners in-game (left-click) to recalibrate; right-click resets.
local VIEW_X = 74
local VIEW_Y = 59
local VIEW_W = 661
local VIEW_H = 418

local TARGET_RATIO = VIEW_W / VIEW_H   -- ~1.76

-- Player-tunable window scale. Page Up enlarges the TV window, Page Down
-- shrinks it. The chosen scale persists for the lifetime of the Lua session
-- (re-applied each time the window is opened) so the player only has to dial
-- it in once. Scale of 1.0 reproduces the original 60% w / 75% h cap; scales
-- above 1.0 push toward (but never quite hit) the full screen.
local SCALE_MIN  = 0.50
local SCALE_MAX  = 1.60
local SCALE_STEP = 0.05
local g_scale    = 1.0

-- Letterbox toggle. When true, the app viewport inside the TV screen-hole
-- is constrained to the app's preferred display aspect ratio
-- (`AppModule.meta.displayAspect`, fallback `meta.width/meta.height`,
-- fallback the screen-hole's own ratio). DOOM and NES both target 4:3, so
-- with the screen-hole being ~16:9 they get black bars on the sides instead
-- of being horizontally stretched. Toggle at runtime with Home; persists
-- for the rest of the Lua session.
local g_letterbox = true

-- Computes the (ww, wh) window size for a given scale, plus current screen
-- dims. Maintains the overlay's aspect ratio and never exceeds ~95% of the
-- visible screen on either axis.
local function computeWindowSize(scale, baseW, baseH)
    baseW = baseW or BASE_W
    baseH = baseH or BASE_H
    local VTUtilsLocal = require("KBMGB/System/VTUtils")
    local sw, sh = VTUtilsLocal.safeGetScreen()
    local fracW = math.min(0.95, 0.60 * scale)
    local fracH = math.min(0.95, 0.75 * scale)
    local maxW = math.max(500, math.floor(sw * fracW))
    local maxH = math.max(330, math.floor(sh * fracH))
    local ww = maxW
    local wh = math.floor(ww * baseH / baseW)
    if wh > maxH then
        wh = maxH
        ww = math.floor(wh * baseW / baseH)
    end
    ww = math.max(500, ww)
    wh = math.max(330, wh)
    return ww, wh, sw, sh
end

local CORNER_LABELS = { "TL", "TR", "BL", "BR" }

-- Calibration tooling. Only active in development; disabled for release
-- so player clicks don't spam the console or paint debug dots.
local CALIBRATION_DEBUG = false

-- Named overlay presets. Each entry describes a bezel/frame texture, its
-- native pixel size, and the rectangle (in those native pixels) where the
-- emulator picture should be drawn (the "screen hole"). Callers select an
-- overlay by passing args.overlayId at construction time. The default
-- ("tv") preserves the original NES console look; "monitor" swaps in the
-- desktop-PC bezel so software launched from the desktop OS (`mount` from
-- KBM_MonitorUI) looks like it's running on the player's computer.
local OVERLAYS = {
    tv = {
        path  = "media/textures/Item_tv_overlay_kbm.png",
        baseW = BASE_W, baseH = BASE_H,
        viewX = VIEW_X, viewY = VIEW_Y,
        viewW = VIEW_W, viewH = VIEW_H,
    },
}

-- True if either Shift key is being held; used to manually reset calibration.
local function isShiftDown()
    if not _G.isKeyDown then return false end
    return _G.isKeyDown(Keyboard.KEY_LSHIFT) or _G.isKeyDown(Keyboard.KEY_RSHIFT)
end

-- Rounds a number to 3 decimal places for tidy debug output.
local function round3(value)
    return math.floor((tonumber(value) or 0) * 1000 + 0.5) / 1000
end

-- Constructor. Sizes the window to the screen, picks the overlay texture,
-- locks the player so the emulator session is uninterrupted, and gets the
-- panel ready to be added to the UI manager.
function NESConsoleWindow:new(monitorUI, AppModule, args)
    args = args or {}
    local overlay = OVERLAYS[args.overlayId or "tv"] or OVERLAYS.tv

    local ww, wh, sw, sh = computeWindowSize(g_scale, overlay.baseW, overlay.baseH)
    local x = math.floor((sw - ww) / 2)
    local y = math.floor((sh - wh) / 2)

    local o = AppWindowBase.new(self, x, y, ww, wh)
    setmetatable(o, self)
    self.__index = self

    o.monitorUI       = monitorUI
    o.AppModule       = AppModule
    o.args            = args
    o.viewpointScreenOnly = args.viewpointMirror == true
    o.moveWithMouse   = false
    o.backgroundColor = { r = 0, g = 0, b = 0, a = 0 }
    o.borderColor     = { r = 0, g = 0, b = 0, a = 0 }
    o.modalLockId     = "nesconsolewindow"
    o.overlayId       = args.overlayId or "tv"
    o.baseW           = overlay.baseW
    o.baseH           = overlay.baseH
    o.viewX           = overlay.viewX
    o.viewY           = overlay.viewY
    o.viewW           = overlay.viewW
    o.viewH           = overlay.viewH
    o.overlayTex      = getTexture(overlay.path)
    o.calibrationClicks = {}
    o.captureMouse    = true   -- AppWindowBase:_ensureTopAndFocus holds capture
    o.hiddenForMovement = false
    o.playerObj       = (args and args.playerObj)
        or (_G.getSpecificPlayer and getSpecificPlayer(0))
        or (_G.getPlayer and getPlayer())
    VTUtils.setPlayerLocked(o.playerObj, true)
    return o
end

-- Release the player without ending the emulator session. A hidden panel no
-- longer receives keys or runs AppHost.update, so the game waits at this frame.
function NESConsoleWindow:releaseToWorld()
    if self.hiddenForMovement then return end
    self.hiddenForMovement = true
    self.resumeKeyArmed = false
    self.releasedAtMs = VTUtils.nowMs()
    self.captureMouse = false
    if self.app and self.app.KBMNes and self.app.KBMNes.setPadMask then
        pcall(self.app.KBMNes.setPadMask, 1, 0)
    end
    if self.app then self.app.mouseHeld = false end
    if self.setCapture then pcall(self.setCapture, self, false) end
    if self.setWantKeyEvents then pcall(self.setWantKeyEvents, self, false) end
    VTUtils.setPlayerLocked(self.playerObj, false)
    self:setVisible(false)
end

function NESConsoleWindow:resumePlaying()
    if not self.hiddenForMovement then return end
    self.hiddenForMovement = false
    self.captureMouse = true
    VTUtils.setPlayerLocked(self.playerObj, true)
    self:setVisible(true)
    self:_ensureTopAndFocus()
    if self.bringToFront then self:bringToFront() end
end

-- Keep the transparent outer corners transparent while painting only the CRT
-- aperture black. This also supplies the pillarbox bars around the 4:3 image.
function NESConsoleWindow:prerender()
    AppWindowBase.prerender(self)
    if self.viewpointScreenOnly then return end
    local sx = self.width / (self.baseW or BASE_W)
    local sy = self.height / (self.baseH or BASE_H)
    self:drawRect(
        math.floor((self.viewX or VIEW_X) * sx),
        math.floor((self.viewY or VIEW_Y) * sy),
        math.floor((self.viewW or VIEW_W) * sx),
        math.floor((self.viewH or VIEW_H) * sy),
        1, 0, 0, 0
    )
end

-- Returns the rectangle (x, y, w, h) where the emulator picture should be
-- drawn, scaled to match the current window size. When `g_letterbox` is
-- enabled, the rect is shrunk to the app's preferred aspect ratio and
-- centered inside the screen-hole (black bars fill the unused area thanks
-- to the panel's solid background).
function NESConsoleWindow:getAppViewport()
    local bw = self.baseW or BASE_W
    local bh = self.baseH or BASE_H
    local vx = self.viewX or VIEW_X
    local vy = self.viewY or VIEW_Y
    local vw = self.viewW or VIEW_W
    local vh = self.viewH or VIEW_H
    local sx = self.width  / bw
    local sy = self.height / bh
    local hx = math.floor(vx * sx)
    local hy = math.floor(vy * sy)
    local hw = math.floor(vw * sx)
    local hh = math.floor(vh * sy)

    if not g_letterbox then
        return hx, hy, hw, hh
    end

    -- Resolve the desired display aspect from the app's meta. Prefer an
    -- explicit `displayAspect` field, otherwise derive from the virtual
    -- canvas dimensions, otherwise fall back to the screen-hole's own ratio
    -- (effectively no letterbox).
    local meta = (type(self.AppModule) == "table") and self.AppModule.meta or nil
    local desired = nil
    if type(meta) == "table" then
        local da = tonumber(meta.displayAspect)
        if da and da > 0 then
            desired = da
        else
            local mw = tonumber(meta.width)
            local mh = tonumber(meta.height)
            if mw and mh and mw > 0 and mh > 0 then
                desired = mw / mh
            end
        end
    end
    if not desired or desired <= 0 then
        return hx, hy, hw, hh
    end

    local holeRatio = (hh > 0) and (hw / hh) or desired
    local aw, ah = hw, hh
    if desired < holeRatio then
        -- App is taller than the hole's ratio: pillarbox (shrink width).
        aw = math.floor(hh * desired + 0.5)
    else
        -- App is wider: letterbox (shrink height).
        ah = math.floor(hw / desired + 0.5)
    end
    if aw < 1 then aw = 1 end
    if ah < 1 then ah = 1 end
    local ax = hx + math.floor((hw - aw) / 2)
    local ay = hy + math.floor((hh - ah) / 2)
    return ax, ay, aw, ah
end

-- Calibration: left-click four screen corners to print recalibrated coords to console.
-- Forgets the four-corner clicks, ready to start a fresh calibration.
function NESConsoleWindow:_clearCalibrationClicks()
    self.calibrationClicks = {}
end

-- Records one click. After four clicks, computes the bounding rectangle
-- and prints the values you can paste back into VIEW_X/Y/W/H above.
function NESConsoleWindow:_recordCalibrationClick(x, y)
    local px = math.floor(tonumber(x) or 0)
    local py = math.floor(tonumber(y) or 0)
    local clicks = self.calibrationClicks or {}

    if #clicks >= 4 or isShiftDown() then
        clicks = {}
        self.calibrationClicks = clicks
    end

    clicks[#clicks + 1] = { x = px, y = py }
    local label = CORNER_LABELS[#clicks] or ("P" .. tostring(#clicks))
    print(string.format("[KBM][NESConsoleWindow] %s=(%d,%d)", label, px, py))

    if #clicks == 4 then
        local minX, maxX = clicks[1].x, clicks[1].x
        local minY, maxY = clicks[1].y, clicks[1].y
        for i = 2, 4 do
            local c = clicks[i]
            if c.x < minX then minX = c.x end
            if c.x > maxX then maxX = c.x end
            if c.y < minY then minY = c.y end
            if c.y > maxY then maxY = c.y end
        end
        local w = (maxX - minX) + 1
        local h = (maxY - minY) + 1
        local ratio = (h > 0) and (w / h) or 0
        local sx = (self.width  > 0) and (BASE_W / self.width)  or 1
        local sy = (self.height > 0) and (BASE_H / self.height) or 1
        local baseX = math.floor(minX * sx + 0.5)
        local baseY = math.floor(minY * sy + 0.5)
        local baseW = math.floor(w * sx + 0.5)
        local baseH = math.floor(h * sy + 0.5)
        print(string.format(
            "[KBM][NESConsoleWindow] viewport(panel) x=%d y=%d w=%d h=%d ratio=%.3f target=%.3f",
            minX, minY, w, h, round3(ratio), round3(TARGET_RATIO)
        ))
        print(string.format(
            "[KBM][NESConsoleWindow] viewport(BASE)  VIEW_X=%d VIEW_Y=%d VIEW_W=%d VIEW_H=%d  (window %dx%d)",
            baseX, baseY, baseW, baseH, self.width, self.height
        ))
    end
end

-- Left-click: brings window to front and (in DEBUG) records a calibration corner.
function NESConsoleWindow:onMouseDown(x, y)
    self:_ensureTopAndFocus()
    if CALIBRATION_DEBUG then
        self:_recordCalibrationClick(x, y)
    end
    return true
end

-- Right-click: clears the four-corner calibration in progress (DEBUG only).
function NESConsoleWindow:onRightMouseDown(_x, _y)
    if CALIBRATION_DEBUG then
        self:_clearCalibrationClicks()
    end
    return true
end

function NESConsoleWindow:onMouseUp(_x, _y) return true end
function NESConsoleWindow:onMouseUpOutside(_x, _y) return true end

-- Adjusts the persistent scale by `delta`, clamps to the allowed range, and
-- re-applies the resulting size + center position to this open window. Called
-- by Page Up / Page Down to let players dial in their preferred TV size at
-- runtime. Returns true if the window actually changed size.
function NESConsoleWindow:_applyScaleDelta(delta)
    local newScale = math.max(SCALE_MIN, math.min(SCALE_MAX, g_scale + delta))
    if math.abs(newScale - g_scale) < 1e-6 then return false end
    g_scale = newScale
    local ww, wh, sw, sh = computeWindowSize(g_scale, self.baseW, self.baseH)
    local nx = math.floor((sw - ww) / 2)
    local ny = math.floor((sh - wh) / 2)
    self.width  = ww
    self.height = wh
    self.x      = nx
    self.y      = ny
    if self.setX then pcall(self.setX, self, nx) end
    if self.setY then pcall(self.setY, self, ny) end
    if self.setWidth  then pcall(self.setWidth,  self, ww) end
    if self.setHeight then pcall(self.setHeight, self, wh) end
    if _G.print then
        print(string.format("[KBM][NESConsoleWindow] scale=%.2f size=%dx%d", g_scale, ww, wh))
    end
    return true
end

-- Swallow every key-down before PZ's global hotkeys see it.
function NESConsoleWindow:onKeyDown(_key)
    return true
end

-- Translate key presses: ESC closes; Page Up/Down resize; everything else
-- goes to the embedded app.
function NESConsoleWindow:onKeyPress(key)
    if key == Keyboard.KEY_E and _G.isKeyDown
        and (_G.isKeyDown(Keyboard.KEY_LMENU)
            or _G.isKeyDown(Keyboard.KEY_RMENU)) then
        self:releaseToWorld()
        return true
    end
    if key == Keyboard.KEY_ESCAPE then
        self:close()
        return true
    end
    if self.args and self.args.viewpointMirror and key == Keyboard.KEY_F9 then
        self.viewpointScreenOnly = not self.viewpointScreenOnly
        print("[KBM][NESConsoleWindow] 3D TV screen only=" .. tostring(self.viewpointScreenOnly))
        return true
    end
    if key == Keyboard.KEY_PRIOR then    -- Page Up: enlarge
        self:_applyScaleDelta(SCALE_STEP)
        return true
    elseif key == Keyboard.KEY_NEXT then -- Page Down: shrink
        self:_applyScaleDelta(-SCALE_STEP)
        return true
    elseif key == Keyboard.KEY_HOME then -- Home: toggle letterbox
        g_letterbox = not g_letterbox
        if _G.print then
            print("[KBM][NESConsoleWindow] letterbox=" .. tostring(g_letterbox))
        end
        return true
    end
    if self.app then
        AppHost.onKey(self.app, self, key)
    end
    return true
end

-- Render override: do the standard app + overlay render via base, then
-- (in DEBUG) draw the calibration debug dots on top.
function NESConsoleWindow:render()
    if self.viewpointScreenOnly then return end
    AppWindowBase.render(self)

    if CALIBRATION_DEBUG and not self._loggedRender then
        self._loggedRender = true
        local ax, ay, aw, ah = self:getAppViewport()
        print(string.format(
            "[KBM][NESConsoleWindow] render: panel=(%d,%d %dx%d) appRect=(%d,%d %dx%d) baseView=(%d,%d %dx%d)",
            self.x or 0, self.y or 0, self.width or 0, self.height or 0,
            ax, ay, aw, ah, VIEW_X, VIEW_Y, VIEW_W, VIEW_H
        ))
    end

    if CALIBRATION_DEBUG then
        local clicks = self.calibrationClicks or {}
        for i = 1, #clicks do
            local click = clicks[i]
            self:drawRect(click.x - 2, click.y - 2, 5, 5, 1, 1, 0.1, 0.1)
            self:drawText(CORNER_LABELS[i] or tostring(i), click.x + 6, click.y - 6, 1, 0.3, 0.3, 1, UIFont.Small)
        end
    end
end

function NESConsoleWindow:close()
    if self.args and self.args.viewpointMirror then
        local probe = type(KBMViewpointScreenProbe) == "table" and KBMViewpointScreenProbe
            or (type(com) == "table" and type(com.knoxboxprobe) == "table"
                and com.knoxboxprobe.KBMViewpointScreenProbe)
        local method = self.args.viewpointSource == "nes" and "setNesMirror" or "setDoomMirror"
        if probe and probe[method] then pcall(probe[method], false) end
    end
    AppWindowBase.close(self)
    if self.args and self.args.onClosed then self.args.onClosed(self) end
end

return NESConsoleWindow
