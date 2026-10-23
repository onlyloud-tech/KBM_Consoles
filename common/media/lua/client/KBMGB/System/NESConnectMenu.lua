-- File: media/lua/client/KBM/TV/KBM_NESConnectMenu.lua
-- Adds NES device and TV connection options for carried and placed consoles.

local NESConnectionState = require("KBMGB/System/NESConnectionState")
local VTUtils = require("KBMGB/System/VTUtils")

require "RadioCom/ISRadioWindow"

local NES_CONSOLE_TYPE = "KBM.NESConsole"
local TV_SCAN_RANGE    = 3  -- tiles; matches KBM_NESRadioPatch NES_TV_RANGE

local function isNESConsoleItem(item)
    return item
        and item.getFullType
        and item:getFullType() == NES_CONSOLE_TYPE
end

local function findPlacedNESConsole(worldObjects)
    for _, object in ipairs(worldObjects or {}) do
        local item = object and object.getItem and object:getItem() or nil
        if isNESConsoleItem(item) then return item end
        local data = object and object.getModData and object:getModData() or nil
        local id = data and data.RadioItemID
        local square = id and object.getSquare and object:getSquare() or nil
        local worlds = square and square.getWorldObjects and square:getWorldObjects() or nil
        if worlds then
            for i = 0, worlds:size() - 1 do
                local world = worlds:get(i)
                local linked = world and world.getItem and world:getItem() or nil
                if isNESConsoleItem(linked) and linked.getID
                    and tostring(linked:getID()) == tostring(id) then return linked end
            end
        end
    end
    return nil
end

-- Returns the first NES Console item in `items`, or nil if none selected.
local function findNESConsoleItem(items)
    for _, entry in ipairs(items) do
        local item = entry.items and entry.items[1] or entry
        if isNESConsoleItem(item) then
            return item
        end
    end
    return nil
end

-- Scans tiles within range of the player. Returns the closest TV IsoObject,
-- or nil if none found.
local function findClosestTV(playerObj)
    if not playerObj or not playerObj.getCurrentSquare then return nil end
    local sq = playerObj:getCurrentSquare()
    if not sq then return nil end
    local cell = sq.getCell and sq:getCell() or nil
    if not cell then return nil end
    local px, py, pz = sq:getX(), sq:getY(), sq:getZ()
    local best, bestDist
    for dx = -TV_SCAN_RANGE, TV_SCAN_RANGE do
        for dy = -TV_SCAN_RANGE, TV_SCAN_RANGE do
            local s = cell:getGridSquare(px + dx, py + dy, pz)
            if s and s.getObjects then
                local objs = s:getObjects()
                if objs then
                    for i = 0, objs:size() - 1 do
                        local o = objs:get(i)
                        if VTUtils.isTelevisionObject(o) then
                            local d = dx * dx + dy * dy
                            if not bestDist or d < bestDist then
                                best = o
                                bestDist = d
                            end
                        end
                    end
                end
            end
        end
    end
    return best
end

local function onConnect(args)
    if not args or not args.tv or not args.nesItem then return end
    NESConnectionState.setNESConnected(args.tv, true, args.nesItem)
    print("[KBM][NESConnect] console connected to nearby TV")
end

local function onDisconnect(args)
    if not args or not args.tv then return end
    NESConnectionState.setNESConnected(args.tv, false, args.nesItem)
    print("[KBM][NESConnect] console disconnected from TV")
end

local function placedRadioFor(item)
    local world = item and item.getWorldItem and item:getWorldItem() or nil
    local square = world and world.getSquare and world:getSquare() or nil
    local objects = square and square.getObjects and square:getObjects() or nil
    if not objects then return nil end
    local id = tostring(item:getID())
    local deviceData = item.getDeviceData and item:getDeviceData() or nil
    for i = 0, objects:size() - 1 do
        local object = objects:get(i)
        local data = object and object.getModData and object:getModData() or nil
        if instanceof(object, "IsoRadio") and data and data.RadioItemID ~= nil
            and tostring(data.RadioItemID) == id then return object end
        if instanceof(object, "IsoRadio") and deviceData
            and object.getDeviceData and object:getDeviceData() == deviceData then
            object:getModData().RadioItemID = item:getID()
            object:transmitModData()
            return object
        end
    end
    if not deviceData then return nil end
    -- Vanilla creates this invisible device when a Radio is dropped. Recover
    -- it if a loaded save contains the world item but lost its proxy.
    local radio = IsoRadio.new(getCell(), square, nil)
    radio:setDeviceData(deviceData)
    radio:getModData().RadioItemID = item:getID()
    square:AddSpecialObject(radio, objects:size())
    radio:transmitCompleteItemToClients()
    radio:transmitModData()
    triggerEvent("OnObjectAdded", radio)
    square:RecalcProperties()
    square:RecalcAllWithNeighbours(true)
    print("[KBM][NESConnect] restored placed NES radio device")
    return radio
end

local function onOpenConsole(args)
    if not args or not args.playerObj or not isNESConsoleItem(args.nesItem) then return end
    local item = args.nesItem
    if item.getWorldItem and item:getWorldItem() then
        local radio = placedRadioFor(item)
        if not radio then
            print("[KBM][NESConnect] placed NES has no device data")
            return
        end
        ISRadioWindow.activate(args.playerObj, radio)
    end
end

local function addNESOptions(playerNum, context, nesItem)
    if not nesItem then return end
    local playerObj = getSpecificPlayer and getSpecificPlayer(playerNum) or nil
    if not playerObj then return end

    local floorItem = nesItem.getWorldItem and nesItem:getWorldItem() or nil
    local label = getText("IGUI_DeviceOptions")
    if floorItem and not context:getOptionFromName(label) then
        context:addOption(label, { playerObj = playerObj, nesItem = nesItem }, onOpenConsole)
    end

    local tvObj = findClosestTV(playerObj)
    if not tvObj then return end  -- no TV nearby; don't clutter the menu

    if NESConnectionState.isNESConnected(tvObj, nesItem) then
        context:addOption("Disconnect NES Console from TV", { tv = tvObj, nesItem = nesItem }, onDisconnect)
    else
        context:addOption("Connect NES Console to TV", { tv = tvObj, nesItem = nesItem }, onConnect)
    end
end

local function onFillInventoryObjectContextMenu(playerNum, context, items)
    addNESOptions(playerNum, context, findNESConsoleItem(items))
end

local function onFillWorldObjectContextMenu(playerNum, context, worldObjects, test)
    if test then return end
    addNESOptions(playerNum, context, findPlacedNESConsole(worldObjects))
end

Events.OnFillInventoryObjectContextMenu.Add(onFillInventoryObjectContextMenu)
Events.OnFillWorldObjectContextMenu.Add(onFillWorldObjectContextMenu)
