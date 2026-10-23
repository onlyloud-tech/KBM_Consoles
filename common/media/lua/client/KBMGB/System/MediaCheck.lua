-- File: media/lua/client/KBMGB/System/MediaCheck.lua
-- "Do you actually have the cartridge?" check. Used by emulator apps to
-- refuse to start games unless the player is carrying the right physical
-- media item in their inventory.

local MediaCheck = {}

-- Returns the player object that owns this app context, if any.
local function getPlayerObj(ctx)
    if ctx and ctx.playerObj then return ctx.playerObj end
    if _G.getPlayer then return getPlayer() end
    return nil
end

-- Returns the player's inventory, or nil if it can't be reached.
local function getInventory(playerObj)
    if not playerObj or not playerObj.getInventory then return nil end
    return playerObj:getInventory()
end

-- Returns the cartridge item type that triggered this app session, if any.
local function getContextLoadedItemType(ctx)
    if type(ctx) ~= "table" then return nil end
    if type(ctx.itemFullType) == "string" and ctx.itemFullType ~= "" then
        return ctx.itemFullType
    end
    local args = type(ctx.args) == "table" and ctx.args or nil
    if type(args) ~= "table" then return nil end
    if type(args.itemFullType) == "string" and args.itemFullType ~= "" then
        return args.itemFullType
    end
    if type(args.loadedItemType) == "string" and args.loadedItemType ~= "" then
        return args.loadedItemType
    end
    return nil
end

-- True if the player is carrying any item of the given type.
function MediaCheck.playerHasItemType(playerObj, fullType)
    local inv = getInventory(playerObj)
    if not inv or not fullType or fullType == "" then return false, nil end

    local items = inv:getItems()
    if not items then return false, nil end

    for i = 0, items:size() - 1 do
        local item = items:get(i)
        if item and item:getFullType() == fullType then
            return true, item
        end
    end

    return false, nil
end

-- True if the player has the cartridge / disk required to run this app.
-- Used to gate game launches: e.g. the NES app refuses to load Mario
-- unless a Mario cart is in the player's inventory.
function MediaCheck.hasRequiredMedia(ctx, appId, softwareKeyOverride)
    local Registry = require("KBMGB/System/SoftwareMediaRegistry")
    local entry = Registry.get(appId, softwareKeyOverride)

    if not entry then
        return true, nil, nil
    end

    local playerObj = getPlayerObj(ctx)
    local ok, item = MediaCheck.playerHasItemType(playerObj, entry.itemType)

    if ok then
        return true, entry, item
    end

    local loadedItemType = getContextLoadedItemType(ctx)
    if loadedItemType and loadedItemType == entry.itemType then
        return true, entry, nil
    end

    return false, entry, nil
end

return MediaCheck