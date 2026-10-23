-- KBM_CartMediaTools.lua
-- Shared helpers for making KBM cartridge items acceptable to PZ's vanilla
-- recorded-media device actions in both SP and MP.

local RomMediaCatalog = require("KBMGB/System/RomMediaCatalog")

local CartMediaTools = {}

local function normalizeItemId(itemId)
    if itemId == nil then return nil end
    local numeric = tonumber(itemId)
    if numeric then return tostring(math.floor(numeric)) end
    return tostring(itemId)
end

function CartMediaTools.findItemWithIdRecursive(container, itemId)
    if not container or itemId == nil then return nil end

    if container.getItemWithID then
        local ok, item = pcall(container.getItemWithID, container, tonumber(itemId) or itemId)
        if ok and item then return item end
    end

    local items = container.getItems and container:getItems() or nil
    if not items then return nil end

    local itemIdString = normalizeItemId(itemId)
    for i = 0, items:size() - 1 do
        local item = items:get(i)
        if item then
            if item.getID then
                local ok, id = pcall(item.getID, item)
                if ok and normalizeItemId(id) == itemIdString then return item end
            end

            local nested = item.getInventory and item:getInventory() or nil
            local found = CartMediaTools.findItemWithIdRecursive(nested, itemId)
            if found then return found end
        end
    end

    return nil
end

function CartMediaTools.findItemWithFullTypeRecursive(container, fullType)
    if not container or not fullType then return nil end

    local items = container.getItems and container:getItems() or nil
    if not items then return nil end

    local targetFullType = tostring(fullType)
    for i = 0, items:size() - 1 do
        local item = items:get(i)
        if item then
            if item.getFullType and item:getFullType() == targetFullType then
                return item
            end

            local nested = item.getInventory and item:getInventory() or nil
            local found = CartMediaTools.findItemWithFullTypeRecursive(nested, targetFullType)
            if found then return found end
        end
    end

    return nil
end

function CartMediaTools.findSupportedCartRecursive(container, fullType)
    if not container then return nil end

    local items = container.getItems and container:getItems() or nil
    if not items then return nil end

    local targetFullType = fullType and tostring(fullType) or nil
    for i = 0, items:size() - 1 do
        local item = items:get(i)
        if item then
            local itemFullType = item.getFullType and item:getFullType() or nil
            if itemFullType and CartMediaTools.isSupportedCart(itemFullType)
                and (not targetFullType or itemFullType == targetFullType) then
                return item
            end

            local nested = item.getInventory and item:getInventory() or nil
            local found = CartMediaTools.findSupportedCartRecursive(nested, targetFullType)
            if found then return found end
        end
    end

    return nil
end

function CartMediaTools.isSupportedCart(fullType)
    if not fullType then return false end
    local entry = RomMediaCatalog.getByItemType(fullType)
    return entry ~= nil and (entry.appId == "nes" or entry.appId == "doom")
end

local function findRecordedMediaData(item, fullType)
    local entry = RomMediaCatalog.getByItemType(fullType)
    local mediaId = entry and entry.recordedMediaId or nil
    if not mediaId then return nil, "no mediaId mapping" end

    local radio = getZomboidRadio and getZomboidRadio() or nil
    local recordedMedia = radio and radio.getRecordedMedia and radio:getRecordedMedia() or nil
    if not recordedMedia or not recordedMedia.getAllMediaForCategory then
        return nil, "recorded media object unavailable"
    end

    local categories = {}
    local seen = {}
    local function addCategory(category)
        local key = tostring(category or "")
        if key ~= "" and not seen[key] then
            seen[key] = true
            categories[#categories + 1] = key
        end
    end

    local scriptItem = item and item.getScriptItem and item:getScriptItem() or nil
    local category = scriptItem and scriptItem.getRecordedMediaCat and scriptItem:getRecordedMediaCat() or nil
    addCategory(category)
    addCategory("VHS")
    addCategory("Retail-VHS")
    addCategory("Home-VHS")

    for _, mediaCategory in ipairs(categories) do
        local ok, list = pcall(recordedMedia.getAllMediaForCategory, recordedMedia, mediaCategory)
        if ok and list then
            for i = 0, list:size() - 1 do
                local media = list:get(i)
                if media then
                    local okId, id = pcall(media.getId, media)
                    if okId and tostring(id) == tostring(mediaId) then
                        return media, nil
                    end
                end
            end
        end
    end

    return nil, "mediaId not found: " .. tostring(mediaId)
end

function CartMediaTools.ensureCartMediaReady(item, logFn)
    if not item or not item.getFullType then return false end
    local fullType = item:getFullType()
    if not CartMediaTools.isSupportedCart(fullType) then return false end

    if item.getMediaType and item:getMediaType() ~= 1 and item.setMediaType then
        pcall(item.setMediaType, item, 1)
    end

    if item.isRecordedMedia and not item:isRecordedMedia() then
        local mediaData, reason = findRecordedMediaData(item, fullType)
        if mediaData and item.setRecordedMediaData then
            pcall(item.setRecordedMediaData, item, mediaData)
        elseif reason and logFn then
            logFn("cart media attach pending fullType=" .. tostring(fullType) .. " reason=" .. tostring(reason))
        end
    end

    return item.getMediaType and item:getMediaType() == 1 and item.isRecordedMedia and item:isRecordedMedia()
end

return CartMediaTools
