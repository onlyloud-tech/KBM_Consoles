-- File: 42/media/lua/client/KBMGB/System/KBM_NESRadioPatch.lua
-- B42 variant: integrates NES cartridges with the vanilla media-slot UI.
-- UI on the NES Console. When the player drops a cart into the console and
-- presses play, this patch opens NESConsoleWindow with the right App.
--
-- TV proximity rules (mirrors B41 NESRadioPatch behavior):
--   "connected" - a TV in range is wired to this NES   -> normal display
--   "tv"        - a TV is in range but not connected   -> noSignal=true
--   "none"      - no TV in range                       -> no window opened
--
-- B42 routes Add/Remove media through ISDeviceMediaAction (single TimedAction
-- with isRemove flag). performTogglePlayMedia still lives on ISRadioAction.

require "TimedActions/ISDeviceMediaAction"
require "RadioCom/ISRadioAction"
require "RadioCom/RadioWindowModules/RWMMedia"

local RomMediaCatalog  = require("KBMGB/System/RomMediaCatalog")
local NESConsoleWindow = require("KBMGB/UI/NESConsoleWindow")
local NesApp           = require("KBMGB/Games/CRT/NesApp")
local DoomApp          = require("KBMGB/Games/CRT/DoomApp")
local NESConnectionState = require("KBMGB/System/NESConnectionState")
local CartMediaTools   = require("KBMGB/System/KBM_CartMediaTools")
local VTUtils          = require("KBMGB/System/VTUtils")

local DEVICE_FULL_TYPE = "KBM.NESConsole"
local NES_TV_RANGE     = 3
local KBM_PATCH_KEY    = "KBM_NESConsolePatch"

local activeWindow = nil
local mediaVerifyPatched = false
local setLoadedCart

local function log(msg)
    print("[KBM][NESRadioPatch B42] " .. tostring(msg))
end

local function isNESConsole(device)
    if not device then return false end
    if device.getFullType and device:getFullType() == DEVICE_FULL_TYPE then
        return true
    end
    local placedItem = device.getItem and device:getItem() or nil
    if placedItem and placedItem.getFullType
        and placedItem:getFullType() == DEVICE_FULL_TYPE then return true end

    -- Dropping a Radio creates an invisible IsoRadio beside its world item.
    -- The radio window and timed actions use that IsoRadio as their device.
    local md = device.getModData and device:getModData() or nil
    local itemId = md and md.RadioItemID
    local square = itemId and device.getSquare and device:getSquare() or nil
    local objects = square and square.getWorldObjects and square:getWorldObjects() or nil
    if not objects then return false end
    for i = 0, objects:size() - 1 do
        local world = objects:get(i)
        local item = world and world.getItem and world:getItem() or nil
        if item and item.getID and tostring(item:getID()) == tostring(itemId)
            and item.getFullType and item:getFullType() == DEVICE_FULL_TYPE then
            return true
        end
    end
    return false
end

local function consoleItemId(device)
    if not device then return nil end
    local item = device.getItem and device:getItem() or nil
    if item and item.getID then return tostring(item:getID()) end
    local md = device.getModData and device:getModData() or nil
    if md and md.RadioItemID ~= nil then return tostring(md.RadioItemID) end
    if device.getFullType and device:getFullType() == DEVICE_FULL_TYPE
        and device.getID then return tostring(device:getID()) end
    return nil
end

local function isSupportedCart(fullType)
    return CartMediaTools.isSupportedCart(fullType)
end

local function ensureCartMediaReady(item)
    return CartMediaTools.ensureCartMediaReady(item, log)
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

local function patchRWMMediaVerifier()
    if mediaVerifyPatched or not RWMMedia or not RWMMedia.verifyItem then return end
    mediaVerifyPatched = true
    local originalVerifyItem = RWMMedia.verifyItem
    local originalCreateChildren = RWMMedia.createChildren
    local originalAddMediaAux = RWMMedia.addMediaAux

    local function isNESMediaPanel(panel)
        return panel and panel.device and isNESConsole(panel.device)
    end

    local function verifyNESMediaItem(panel, item)
        if originalVerifyItem(panel, item) then return true end
        if isNESMediaPanel(panel) and item and item.getFullType and isSupportedCart(item:getFullType()) then
            ensureCartMediaReady(item)
            return true
        end
        return false
    end

    function RWMMedia:verifyItem(item)
        return verifyNESMediaItem(self, item)
    end

    function RWMMedia:createChildren()
        originalCreateChildren(self)
        if self.itemDropBox and isNESMediaPanel(self) then
            self.itemDropBox.onVerifyItem = verifyNESMediaItem
        end
    end

    function RWMMedia:addMediaAux(item)
        if not isNESMediaPanel(self) or not item or not item.getFullType or not isSupportedCart(item:getFullType()) then
            return originalAddMediaAux(self, item)
        end

        if item:getWorldItem() and not luautils.walkAdj(self.player, item:getWorldItem():getSquare(), true) then
            return
        end

        ISInventoryPaneContextMenu.transferIfNeeded(self.player, item)
        if not self:doWalkTo() then return end

        ensureCartMediaReady(item)

        if _G.isClient and isClient() and _G.sendClientCommand then
            sendClientCommand(self.player, "KBM_NESMedia", "Insert", {
                deviceId = self.device and self.device.getID and tostring(self.device:getID()) or nil,
                itemId = item.getID and tostring(item:getID()) or nil,
                itemFullType = item:getFullType(),
            })
            setLoadedCart(self.device, item:getFullType())
            log("requested server cart insert: " .. tostring(item:getFullType()))
            return
        end

        return originalAddMediaAux(self, item)
    end
end

function setLoadedCart(device, fullType)
    local root = getDeviceRoot(device)
    if not root then return end
    root.cartFullType = fullType or ""
    if device.transmitModData then pcall(device.transmitModData, device) end
end

local function getLoadedCart(device, deviceData)
    local root = getDeviceRoot(device)
    if not root then return nil end
    local ft = root.cartFullType
    if ft and ft ~= "" and isSupportedCart(ft) then return ft end

    -- Vanilla owns the actual media slot. Recover our cartridge identity from
    -- its recorded-media id when modData is absent (old saves, pre-hook
    -- insertions, or an insertion callback that completed on another side).
    local data = deviceData
    if not data and device and device.getDeviceData then
        local ok, value = pcall(device.getDeviceData, device)
        if ok then data = value end
    end
    if not data or not data.hasMedia or not data:hasMedia() or not data.getMediaData then
        return nil
    end
    local okMedia, media = pcall(data.getMediaData, data)
    if not okMedia or not media or not media.getId then return nil end
    local okId, mediaId = pcall(media.getId, media)
    if not okId or mediaId == nil then return nil end
    local wanted = tostring(mediaId)
    for _, entry in ipairs(RomMediaCatalog.entries or {}) do
        if (entry.appId == "nes" or entry.appId == "doom")
            and tostring(entry.recordedMediaId or "") == wanted then
            setLoadedCart(device, entry.itemType)
            log("recovered loaded cart from media id " .. wanted .. ": " .. tostring(entry.itemType))
            return entry.itemType
        end
    end
    log("inserted media id is not an NES cartridge: " .. wanted)
    return nil
end

-- Scans tiles around the player. Returns "connected" / "tv" / "none".
local function nearbyTVStatus(playerObj, console)
    if not playerObj or not playerObj.getCurrentSquare then return "none" end
    local sq = playerObj:getCurrentSquare()
    if not sq then return "none" end
    local cell = sq.getCell and sq:getCell() or nil
    if not cell then return "none" end
    local px, py, pz = sq:getX(), sq:getY(), sq:getZ()
    local foundTV = false
    for dx = -NES_TV_RANGE, NES_TV_RANGE do
        for dy = -NES_TV_RANGE, NES_TV_RANGE do
            local s = cell:getGridSquare(px + dx, py + dy, pz)
            if s and s.getObjects then
                local objs = s:getObjects()
                if objs then
                    for i = 0, objs:size() - 1 do
                        local o = objs:get(i)
                        if VTUtils.isTelevisionObject(o) then
                            if NESConnectionState.isNESConnected(o, console) then return "connected", o end
                            foundTV = true
                        end
                    end
                end
            end
        end
    end
    return foundTV and "tv" or "none", nil
end

local function viewpointProbe()
    if type(KBMViewpointScreenProbe) == "table" then return KBMViewpointScreenProbe end
    if type(com) == "table" and type(com.knoxboxprobe) == "table" then
        return com.knoxboxprobe.KBMViewpointScreenProbe
    end
end

local function closeActiveWindow()
    if not activeWindow then return end
    local win = activeWindow
    activeWindow = nil
    if win.setCapture then pcall(win.setCapture, win, false) end
    if win.removeFromUIManager then pcall(win.removeFromUIManager, win) end
    if win.close then pcall(win.close, win) end
end

local function openWindowFor(console, cartFullType)
    closeActiveWindow()
    local entry = RomMediaCatalog.getByItemType(cartFullType)
    if not entry then
        log("no rom entry for " .. tostring(cartFullType))
        return
    end

    local playerObj = _G.getSpecificPlayer and getSpecificPlayer(0) or nil
    local status, tv = nearbyTVStatus(playerObj, console)
    if status == "none" then
        -- Cart "plays" on the device but no display is shown.
        log("no TV in range; suppressing window")
        return
    end
    local noSignal = (status ~= "connected")

    local AppModule
    local args
    if entry.appId == "nes" and entry.rom then
        AppModule = NesApp
        args = {
            rom          = entry.rom,
            softwareKey  = entry.softwareKey,
            itemFullType = cartFullType,
            radioDevice  = true,
            noSignal     = noSignal,
        }
    elseif entry.appId == "doom" and entry.wadId then
        AppModule = DoomApp
        args = {
            wadId        = entry.wadId,
            softwareKey  = entry.softwareKey,
            itemFullType = cartFullType,
            radioDevice  = true,
            noSignal     = noSignal,
        }
    else
        log("unhandled cartridge " .. tostring(cartFullType))
        return
    end

    local mirror = (entry.appId == "doom" or entry.appId == "nes")
        and not noSignal and VTUtils.isKnoxBox3DTV(tv)
    local probe = mirror and viewpointProbe() or nil
    local mirrorMethod = entry.appId == "doom" and "setDoomMirror" or "setNesMirror"
    if probe and probe[mirrorMethod] then
        args.viewpointMirror = true
        args.viewpointSource = entry.appId
    end
    args.console = console
    args.tv = tv
    args.playerObj = playerObj
    args.onClosed = function(win)
        if activeWindow == win then activeWindow = nil end
    end

    local ok, win = pcall(NESConsoleWindow.new, NESConsoleWindow, nil, AppModule, args)
    if not ok or not win then
        log("NESConsoleWindow.new failed: " .. tostring(win))
        return
    end

    pcall(win.initialise, win)
    if win.setAlwaysOnTop  then pcall(win.setAlwaysOnTop, win, true)  end
    if win.setWantKeyEvents then pcall(win.setWantKeyEvents, win, true) end
    pcall(win.addToUIManager, win)
    if win.bringToFront    then pcall(win.bringToFront, win)            end
    if win.setCapture      then pcall(win.setCapture, win, true)        end
    activeWindow = win
    if args.viewpointMirror then
        local ok, err = pcall(probe[mirrorMethod], true)
        if not ok then log("3D TV mirror failed: " .. tostring(err)) end
    end
    log("opened NES window app=" .. tostring(entry.appId) .. " cart=" .. tostring(cartFullType))
end

------------------------------------------------------------
-- ISDeviceMediaAction:invoke   (B42 add/remove cart)
------------------------------------------------------------
local _origInvoke = ISDeviceMediaAction.invoke
local _origIsValid = ISDeviceMediaAction.isValid

function ISDeviceMediaAction:isValid()
    if self.isRemove then
        return _origIsValid(self)
    end

    local item = self.secondaryItem
    local device = getDeviceFromData(self.deviceData)
    if item and device and isNESConsole(device) and isSupportedCart(item:getFullType()) then
        ensureCartMediaReady(item)
        return self.deviceData and not self.deviceData:hasMedia()
    end

    return _origIsValid(self)
end

function ISDeviceMediaAction:invoke()
    local item       = self.secondaryItem
    local isRemove   = self.isRemove
    local deviceData = self.deviceData
    local device     = getDeviceFromData(deviceData)

    local wasOurs = nil
    if isRemove and device and isNESConsole(device) then
        local ft = getLoadedCart(device)
        wasOurs = isSupportedCart(ft)
    end

    if item and device and isNESConsole(device) then
        ensureCartMediaReady(item)
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
    if not isSupportedCart(fullType) then
        log("unsupported cart inserted: " .. tostring(fullType))
        return
    end
    if not device or not isNESConsole(device) then return end

    setLoadedCart(device, fullType)
    log("cart inserted: " .. fullType)
end

------------------------------------------------------------
-- ISRadioAction:performTogglePlayMedia   (still B42)
------------------------------------------------------------
local _origTogglePlay = ISRadioAction.performTogglePlayMedia

patchRWMMediaVerifier()

function ISRadioAction:performTogglePlayMedia()
    local deviceData = self.deviceData
    local device     = self.device or getDeviceFromData(deviceData)

    local wasPlaying = deviceData
        and deviceData.isPlayingMedia
        and deviceData:isPlayingMedia()

    _origTogglePlay(self)

    if not device or not isNESConsole(device) then return end

    local fullType = getLoadedCart(device, deviceData)
    if not isSupportedCart(fullType) then
        log("loaded cart not supported: " .. tostring(fullType))
        return
    end

    -- Drive window open/close from intent rather than the post-call playing
    -- flag. In MP, _origTogglePlay only queues a server toggle, so
    -- deviceData:isPlayingMedia() does not flip on this client immediately.
    -- Use activeWindow + wasPlaying to decide which direction the user
    -- toggled, so the custom UI still appears in MP.
    local intendedPlay = not wasPlaying and activeWindow == nil
    local intendedStop = wasPlaying or activeWindow ~= nil

    if intendedPlay then
        openWindowFor(device, fullType)
    elseif intendedStop then
        closeActiveWindow()
        log("playback stopped")
    end
end

local function onWorldContextMenu(playerNum, context, worldObjects, test)
    if test or not activeWindow or not activeWindow.hiddenForMovement then return end
    local player = _G.getSpecificPlayer and getSpecificPlayer(playerNum) or nil
    if not player or player ~= activeWindow.playerObj then return end
    local args = activeWindow.args or {}
    local wanted = consoleItemId(args.console)
    local tv = args.tv
    local tvId = tv and tv.getModData and tv:getModData().RadioItemID or nil
    for _, object in ipairs(worldObjects or {}) do
        local sameConsole = isNESConsole(object)
            and wanted ~= nil and consoleItemId(object) == wanted
        local sameTV = object == tv
        if not sameTV and tvId ~= nil then
            local item = object and object.getItem and object:getItem() or nil
            sameTV = item and item.getID and tostring(item:getID()) == tostring(tvId)
        end
        if sameConsole or sameTV then
            context:addOption("Continue Playing", activeWindow, function(win)
                win:resumePlaying()
            end)
            return
        end
    end
end

Events.OnFillWorldObjectContextMenu.Add(onWorldContextMenu)

-- A dropped NES is usually selected from the floor inventory pane. That pane
-- has its own context-menu event and passes the linked inventory item, not the
-- IsoRadio object used by the device-options window.
local function onInventoryContextMenu(playerNum, context, items)
    if not activeWindow or not activeWindow.hiddenForMovement then return end
    local player = _G.getSpecificPlayer and getSpecificPlayer(playerNum) or nil
    if not player or player ~= activeWindow.playerObj then return end
    local wanted = consoleItemId(activeWindow.args and activeWindow.args.console)
    if not wanted then return end
    for _, entry in ipairs(items or {}) do
        local item = entry.items and entry.items[1] or entry
        if isNESConsole(item) and consoleItemId(item) == wanted then
            context:addOption("Continue Playing", activeWindow, function(win)
                win:resumePlaying()
            end)
            return
        end
    end
end

Events.OnFillInventoryObjectContextMenu.Add(onInventoryContextMenu)

local function keyDown(key)
    if Keyboard and Keyboard.isKeyDown then return Keyboard.isKeyDown(key) end
    return _G.isKeyDown and _G.isKeyDown(key) or false
end

-- Wait for the E that released control to be lifted before accepting another
-- Alt+E. Both the focused panel and this global event can see that keypress.
local function armResumeKey()
    if activeWindow and activeWindow.hiddenForMovement
        and not activeWindow.resumeKeyArmed
        and VTUtils.nowMs() - (activeWindow.releasedAtMs or 0) >= 250
        and not keyDown(Keyboard.KEY_E) then
        activeWindow.resumeKeyArmed = true
    end
end

local function nearObject(player, object)
    local square = object and object.getSquare and object:getSquare() or nil
    local current = player and player.getCurrentSquare and player:getCurrentSquare() or nil
    if not square or not current or square:getZ() ~= current:getZ() then return false end
    return math.abs(square:getX() - current:getX()) <= NES_TV_RANGE
        and math.abs(square:getY() - current:getY()) <= NES_TV_RANGE
end

local function onResumeKey(key)
    local win = activeWindow
    if key ~= Keyboard.KEY_E or not win or not win.hiddenForMovement
        or not win.resumeKeyArmed then return end
    if VTUtils.nowMs() - (win.releasedAtMs or 0) < 250 then return end
    if not (keyDown(Keyboard.KEY_LMENU) or keyDown(Keyboard.KEY_RMENU)) then return end
    local args = win.args or {}
    if nearObject(win.playerObj, args.console) or nearObject(win.playerObj, args.tv) then
        win:resumePlaying()
    end
end

Events.OnTick.Add(armResumeKey)
Events.OnKeyPressed.Add(onResumeKey)
