-- File: 42/media/lua/client/KBMGB/System/KBM_GameBoyRadioPatch.lua
-- B42 variant: integrates GB cartridges with the vanilla "media slot" UI on
-- GB handhelds. When the player drops a GB cart into a handheld and presses
-- play, this patch opens the GameBoyWindow with that ROM and the shell
-- artwork that matches the chosen handheld colour.
--
-- B42 routes Add/Remove media through ISDeviceMediaAction (single TimedAction
-- with isRemove flag). performTogglePlayMedia still lives on ISRadioAction.

require "TimedActions/ISDeviceMediaAction"
require "RadioCom/ISRadioAction"

local RomMediaCatalog = require("KBMGB/System/RomMediaCatalog")
local GBRomRuntime    = require("KBMGB/System/GBRomRuntime")
local GameBoyApp      = require("KBMGB/Games/CRT/GameBoyApp")
local GameBoyWindow   = require("KBMGB/UI/GameBoyWindow")
local VTUtils         = require("KBMGB/System/VTUtils")

local KBM_PATCH_KEY = "KBM_GameBoyPatch"

-- Maps handheld item fullType -> shell variant key in GameBoyWindow.SHELL_VARIANTS
local DEVICE_VARIANTS = {
    ["KBM.GameBoyHandheldClassic"] = "classic",
    ["KBM.GameBoyHandheldGreen"]   = "green",
    ["KBM.GameBoyHandheldGrey"]    = "grey",
    ["KBM.GameBoyHandheldRed"]     = "red",
    ["KBM.GameBoyHandheldYellow"]  = "yellow",
    ["KBM.GameBoyHandheldBlue"]    = "blue",
    ["KBM.GameBoyHandheldPink"]    = "pink",
    ["KBM.GameBoyHandheldClear"]   = "clear",
}

local activeWindow = nil

-- Register only cartridges for ROM files present in this local library.
GBRomRuntime.initialize()

local function log(msg)
    print("[KBM][GameBoyRadioPatch B42] " .. tostring(msg))
end

local function variantForDevice(device)
    if not device or not device.getFullType then return nil end
    return DEVICE_VARIANTS[device:getFullType()]
end

local function isGBHandheld(device)
    return variantForDevice(device) ~= nil
end

local function isGBCart(fullType)
    if not fullType then return false end
    local entry = RomMediaCatalog.getByItemType(fullType)
    if entry and entry.appId == "gb" then return true end
    return GBRomRuntime.getByItemType(fullType) ~= nil
end

local function getDeviceFromData(deviceData)
    if not deviceData then return nil end
    if deviceData.getParent then
        local ok, parent = pcall(deviceData.getParent, deviceData)
        if ok and parent then return parent end
    end
    return nil
end

local function getDeviceRoot(device)
    if not device or not device.getModData then return nil end
    local md = device:getModData()
    if not md then return nil end
    if not md[KBM_PATCH_KEY] then md[KBM_PATCH_KEY] = {} end
    return md[KBM_PATCH_KEY]
end

local function setLoadedCart(device, fullType)
    local root = getDeviceRoot(device)
    if not root then return end
    root.cartFullType = fullType or ""
    if device.transmitModData then pcall(device.transmitModData, device) end
end

local function getLoadedCart(device)
    local root = getDeviceRoot(device)
    if not root then return nil end
    local ft = root.cartFullType
    if not ft or ft == "" then return nil end
    return ft
end

local function closeActiveWindow()
    if not activeWindow then return end
    local win = activeWindow
    activeWindow = nil
    if win.setCapture then pcall(win.setCapture, win, false) end
    if win.removeFromUIManager then pcall(win.removeFromUIManager, win) end
    if win.close then pcall(win.close, win) end
end

local function openWindowFor(device, cartFullType)
    closeActiveWindow()
    local entry = RomMediaCatalog.getByItemType(cartFullType)
        or GBRomRuntime.getByItemType(cartFullType)
    if not entry or entry.appId ~= "gb" or not entry.rom then
        log("no rom entry for " .. tostring(cartFullType))
        return
    end

    local ok, win = pcall(GameBoyWindow.new, GameBoyWindow, nil, GameBoyApp, {
        rom          = entry.rom,
        softwareKey  = entry.softwareKey,
        itemFullType = cartFullType,
        radioDevice  = true,
        shellVariant = variantForDevice(device),
        onClosed = function(closed)
            if activeWindow == closed then activeWindow = nil end
        end,
    })
    if not ok or not win then
        log("GameBoyWindow.new failed: " .. tostring(win))
        return
    end

    pcall(win.initialise, win)
    if win.setAlwaysOnTop  then pcall(win.setAlwaysOnTop, win, true)  end
    if win.setWantKeyEvents then pcall(win.setWantKeyEvents, win, true) end
    pcall(win.addToUIManager, win)
    if win.bringToFront    then pcall(win.bringToFront, win)            end
    if win.setCapture      then pcall(win.setCapture, win, true)        end
    if win.startHeldPose   then pcall(win.startHeldPose, win, device)   end
    activeWindow = win
    log("opened GB window rom=" .. tostring(entry.rom))
end

local function keyDown(key)
    if Keyboard and Keyboard.isKeyDown then return Keyboard.isKeyDown(key) end
    return _G.isKeyDown and _G.isKeyDown(key) or false
end

-- The focused window and this global listener can see the same Alt+E press.
-- Arm resume only after E has been released and the first press has passed.
local function armResumeKey()
    local win = activeWindow
    if win and win.hiddenForMovement and not win._closing
        and not win.resumeKeyArmed
        and VTUtils.nowMs() - (win.releasedAtMs or 0) >= 250
        and not keyDown(Keyboard.KEY_E) then
        win.resumeKeyArmed = true
    end
end

local function onResumeKey(key)
    local win = activeWindow
    if key ~= Keyboard.KEY_E or not win or not win.hiddenForMovement
        or win._closing or not win.resumeKeyArmed then return end
    if VTUtils.nowMs() - (win.releasedAtMs or 0) < 250 then return end
    if keyDown(Keyboard.KEY_LMENU) or keyDown(Keyboard.KEY_RMENU) then
        win:resumePlaying()
    end
end

Events.OnTick.Add(armResumeKey)
Events.OnKeyPressed.Add(onResumeKey)

------------------------------------------------------------
-- ISDeviceMediaAction:invoke   (B42 add/remove cart)
------------------------------------------------------------
local _origInvoke = ISDeviceMediaAction.invoke

function ISDeviceMediaAction:invoke()
    local item       = self.secondaryItem
    local isRemove   = self.isRemove
    local deviceData = self.deviceData
    local device     = getDeviceFromData(deviceData)

    local wasOurs = nil
    if isRemove and device and isGBHandheld(device) then
        local ft = getLoadedCart(device)
        wasOurs = isGBCart(ft)
    end

    _origInvoke(self)

    if not deviceData then return end

    if isRemove then
        if wasOurs and device then
            setLoadedCart(device, "")
            closeActiveWindow()
            log("cart ejected")
        end
        return
    end

    if not item or not item.getFullType then return end
    local fullType = item:getFullType()
    if not isGBCart(fullType) then return end
    if not device or not isGBHandheld(device) then return end

    setLoadedCart(device, fullType)
    log("cart inserted: " .. fullType)
end

------------------------------------------------------------
-- ISRadioAction:performTogglePlayMedia   (still B42)
------------------------------------------------------------
local _origTogglePlay = ISRadioAction.performTogglePlayMedia

function ISRadioAction:performTogglePlayMedia()
    local deviceData = self.deviceData
    local device     = self.device or getDeviceFromData(deviceData)

    local wasPlaying = deviceData
        and deviceData.isPlayingMedia
        and deviceData:isPlayingMedia()

    _origTogglePlay(self)

    if not device or not isGBHandheld(device) then return end

    local fullType = getLoadedCart(device)
    if not isGBCart(fullType) then return end

    local nowPlaying = deviceData
        and deviceData.isPlayingMedia
        and deviceData:isPlayingMedia()

    if nowPlaying and not wasPlaying then
        openWindowFor(device, fullType)
    elseif not nowPlaying and wasPlaying then
        closeActiveWindow()
        log("playback stopped")
    end
end
