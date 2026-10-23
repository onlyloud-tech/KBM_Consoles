-- KBM_NESMediaServer.lua
-- Server-authoritative NES Console media insertion for MP. Vanilla's
-- ISDeviceMediaAction can lose the secondary item when queued from the custom
-- inventory-open radio window, so the client sends item ids and the server
-- resolves both inventory items recursively before mutating deviceData.

local CartMediaTools = require("KBMGB/System/KBM_CartMediaTools")

local MODULE = "KBM_NESMedia"
local DEVICE_FULL_TYPE = "KBM.NESConsole"
local KBM_PATCH_KEY = "KBM_NESConsolePatch"
local PATCH_VERSION = "2026-05-06.3"

local function log(msg)
    print("[KBM][NESMediaServer] " .. tostring(msg))
end

local function setLoadedCart(device, fullType)
    if not device or not device.getModData then return end
    local modData = device:getModData()
    if not modData[KBM_PATCH_KEY] then modData[KBM_PATCH_KEY] = {} end
    modData[KBM_PATCH_KEY].cartFullType = fullType or ""
    if device.transmitModData then pcall(device.transmitModData, device) end
end

local function getPlayerInventory(player)
    return player and player.getInventory and player:getInventory() or nil
end

local function itemIdEquals(item, itemId)
    if not item or not item.getID or itemId == nil then return false end
    local ok, id = pcall(item.getID, item)
    if not ok then return false end
    local expected = tonumber(itemId)
    local actual = tonumber(id)
    if expected and actual then return math.floor(expected) == math.floor(actual) end
    return tostring(id) == tostring(itemId)
end

local function itemFullTypeEquals(item, fullType)
    return item and item.getFullType and fullType and item:getFullType() == tostring(fullType)
end

local function getHandItems(player)
    local items = {}
    if player and player.getPrimaryHandItem then
        local ok, item = pcall(player.getPrimaryHandItem, player)
        if ok and item then items[#items + 1] = item end
    end
    if player and player.getSecondaryHandItem then
        local ok, item = pcall(player.getSecondaryHandItem, player)
        if ok and item then items[#items + 1] = item end
    end
    return items
end

local function findPlayerItemById(player, inventory, itemId)
    local item = CartMediaTools.findItemWithIdRecursive(inventory, itemId)
    if item then return item end

    for _, handItem in ipairs(getHandItems(player)) do
        if itemIdEquals(handItem, itemId) then return handItem end
    end

    return nil
end

local function findPlayerItemByFullType(player, inventory, fullType)
    local item = CartMediaTools.findItemWithFullTypeRecursive(inventory, fullType)
    if item then return item end

    for _, handItem in ipairs(getHandItems(player)) do
        if itemFullTypeEquals(handItem, fullType) then return handItem end
    end

    return nil
end

local function describeItem(item)
    if not item then return "nil" end
    local fullType = item.getFullType and item:getFullType() or "?"
    local id = item.getID and item:getID() or "?"
    return tostring(fullType) .. "#" .. tostring(id)
end

local function collectInventorySample(container, out, depth)
    if not container or #out >= 20 then return end
    local items = container.getItems and container:getItems() or nil
    if not items then return end

    for i = 0, items:size() - 1 do
        if #out >= 20 then return end
        local item = items:get(i)
        if item then
            out[#out + 1] = string.rep("/", depth or 0) .. describeItem(item)
            local nested = item.getInventory and item:getInventory() or nil
            collectInventorySample(nested, out, (depth or 0) + 1)
        end
    end
end

local function logPlayerItemState(player, inventory)
    local sample = {}
    collectInventorySample(inventory, sample, 0)
    local hands = getHandItems(player)
    log("player item state hands=" .. describeItem(hands[1]) .. "," .. describeItem(hands[2])
        .. " inventorySample=" .. table.concat(sample, "; "))
end

local function onInsert(player, args)
    args = type(args) == "table" and args or {}
    log("insert request v=" .. PATCH_VERSION
        .. " deviceId=" .. tostring(args.deviceId)
        .. " itemId=" .. tostring(args.itemId)
        .. " itemFullType=" .. tostring(args.itemFullType))

    local inventory = getPlayerInventory(player)
    if not inventory then
        log("insert rejected: missing player inventory")
        return
    end

    local console = findPlayerItemById(player, inventory, args.deviceId)
        or findPlayerItemByFullType(player, inventory, DEVICE_FULL_TYPE)
    if not console or not console.getFullType or console:getFullType() ~= DEVICE_FULL_TYPE then
        log("insert rejected: NES console not found id=" .. tostring(args.deviceId))
        logPlayerItemState(player, inventory)
        return
    end

    local cart = findPlayerItemById(player, inventory, args.itemId)
        or CartMediaTools.findSupportedCartRecursive(inventory, args.itemFullType)
    if not cart or not cart.getFullType or not CartMediaTools.isSupportedCart(cart:getFullType()) then
        log("insert rejected: supported cart not found id=" .. tostring(args.itemId))
        return
    end

    local deviceData = console.getDeviceData and console:getDeviceData() or nil
    if not deviceData then
        log("insert rejected: console has no deviceData")
        return
    end

    CartMediaTools.ensureCartMediaReady(cart, log)

    if not cart.getMediaType or cart:getMediaType() ~= deviceData:getMediaType() then
        log("insert rejected: media type mismatch cart=" .. tostring(cart.getMediaType and cart:getMediaType() or nil)
            .. " device=" .. tostring(deviceData:getMediaType()))
        return
    end

    if deviceData:hasMedia() then
        log("insert rejected: console already has media")
        return
    end

    deviceData:addMediaItem(cart)
    setLoadedCart(console, cart:getFullType())
    log("cart inserted: " .. tostring(cart:getFullType())
        .. " deviceId=" .. tostring(args.deviceId)
        .. " itemId=" .. tostring(args.itemId))
end

local function onClientCommand(module, command, player, args)
    if module ~= MODULE then return end
    if command == "Insert" then
        onInsert(player, args)
    end
end

if Events and Events.OnClientCommand then
    Events.OnClientCommand.Add(onClientCommand)
end