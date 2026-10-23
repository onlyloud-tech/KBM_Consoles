-- CartridgeAutoAttach.lua
-- Shared factory for cartridge "auto-attach" loops.
--
-- Cartridges (GB / NES / DOOM ROMs) are stored as VHS-tape items under the
-- hood because PZ's only way to bind "recorded media" data to an item is
-- through a tape. Each tick we scan the player's inventory and silently
-- convert any of our cart items into a tape that holds the right ROM
-- metadata, so the emulator can confirm the player actually has it.
--
-- Usage:
--   local CartridgeAutoAttach = require("KBMGB/System/CartridgeAutoAttach")
--   CartridgeAutoAttach.register({
--       appIds  = { "gb" },        -- which RomMediaCatalog appIds to claim
--       logTag  = "GameBoyCartridgeAutoAttach",
--   })

local RomMediaCatalog = require("KBMGB/System/RomMediaCatalog")

local CartridgeAutoAttach = {}

-- Registers one auto-attach loop. Safe to call multiple times for different
-- app-id sets (each call gets its own state tables and event handler).
function CartridgeAutoAttach.register(opts)
    opts = opts or {}
    local appIds = opts.appIds or {}
    local logTag = opts.logTag or "CartridgeAutoAttach"

    -- Set of appIds we care about, for O(1) lookup in the catalog scan.
    local appIdSet = {}
    for _, id in ipairs(appIds) do appIdSet[tostring(id)] = true end

    -- Build a quick lookup: cartridge item type -> recorded-media id.
    local cartMediaIds = {}
    for _, entry in ipairs(RomMediaCatalog.entries or {}) do
        if entry.appId and appIdSet[tostring(entry.appId)]
            and entry.itemType and entry.recordedMediaId then
            cartMediaIds[tostring(entry.itemType)] = tostring(entry.recordedMediaId)
        end
    end

    -- Carts we've already converted (so we don't redo them every tick).
    local attached = {}
    -- Failure messages we've already logged once (to avoid console spam).
    local seenFailure = {}

    -- True if this item type is one of our registered cartridges.
    local function isSupportedCart(fullType)
        return cartMediaIds[tostring(fullType or "")] ~= nil
    end

    -- Looks up the recorded-media entry for this cart by scanning the
    -- radio's media library for our registered media id.
    local function findMediaData(item, fullType)
        local mediaId = cartMediaIds[tostring(fullType or "")]
        if not mediaId then return nil, "no mediaId mapping" end

        local scriptItem = item.getScriptItem and item:getScriptItem() or nil
        local cat = scriptItem and scriptItem.getRecordedMediaCat and scriptItem:getRecordedMediaCat() or nil
        if not cat then return nil, "no recorded media category on script item" end

        local radio = getZomboidRadio and getZomboidRadio() or nil
        local rm = radio and radio.getRecordedMedia and radio:getRecordedMedia() or nil
        if not rm or not rm.getAllMediaForCategory then
            return nil, "recorded media object unavailable"
        end

        local ok, list = pcall(rm.getAllMediaForCategory, rm, cat)
        if not ok or not list then
            return nil, "failed getAllMediaForCategory(" .. tostring(cat) .. ")"
        end

        for i = 0, list:size() - 1 do
            local entry = list:get(i)
            if entry then
                local okId, entryId = pcall(entry.getId, entry)
                if okId and tostring(entryId) == mediaId then
                    return entry, nil
                end
            end
        end

        return nil, "mediaId not found in category list: " .. tostring(mediaId)
    end

    -- Converts one cartridge item into a recorded-media tape and binds the
    -- ROM data to it. Returns true once the cart is fully wired up.
    local function attachCart(item)
        if not item then return false, "nil item" end
        local fullType = item:getFullType()
        if not isSupportedCart(fullType) then return false, "unsupported fullType" end

        if item:getMediaType() == 1 and item:isRecordedMedia() then
            return true, nil
        end

        pcall(item.setMediaType, item, 1)

        local mediaData, reason = findMediaData(item, fullType)
        if mediaData and item.setRecordedMediaData then
            pcall(item.setRecordedMediaData, item, mediaData)
        elseif reason then
            return false, reason
        end

        if item:getMediaType() == 1 and item:isRecordedMedia() then
            return true, nil
        end

        return false, "post-bind item not recorded media"
    end

    -- Every game tick: scan the player's inventory and convert any new
    -- carts that haven't been processed yet. Failures are logged once per
    -- item.
    Events.OnTick.Add(function()
        local player = getPlayer and getPlayer() or nil
        if not player or not player.getInventory then return end

        local inv = player:getInventory()
        if not inv then return end

        local items = inv:getItems()
        if not items then return end

        for i = 0, items:size() - 1 do
            local item = items:get(i)
            if item and isSupportedCart(item:getFullType()) then
                local itemId = tostring(item)
                if not attached[itemId] then
                    local ok, reason = attachCart(item)
                    if ok then
                        attached[itemId] = true
                    elseif reason then
                        local key = tostring(item:getFullType()) .. "|" .. itemId
                        if not seenFailure[key] then
                            seenFailure[key] = true
                            print("[KBM][" .. logTag .. "] attach pending fullType="
                                .. tostring(item:getFullType()) .. " reason=" .. tostring(reason))
                        end
                    end
                end
            end
        end
    end)
end

return CartridgeAutoAttach
