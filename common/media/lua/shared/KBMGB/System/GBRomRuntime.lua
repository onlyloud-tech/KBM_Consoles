-- Runtime catalog for user-supplied Game Boy ROMs.
-- The manifest contains filenames and labels only; ROM binaries stay local.
local Manifest = require "KBMGB/System/GBRomManifest"

local GBRomRuntime = {
    initialized = false,
    entries = {},
    byItemType = {},
}

local function bridge()
    local direct = rawget(_G, "KBMGameBoy")
    if type(direct) == "table" then return direct end

    local com = rawget(_G, "com")
    local kbm = com and com.knoxboxgb
    if type(kbm) == "table" and type(kbm.KBMGameBoy) == "table" then
        return kbm.KBMGameBoy
    end
    return nil
end

local function readInstalledRoms()
    local api = bridge()
    if not api or type(api.listRoms) ~= "function" then
        return nil, "KBMGameBoy.listRoms unavailable"
    end

    local ok, value = pcall(function() return api.listRoms() end)
    if not ok or type(value) ~= "string" then
        return nil, "KBMGameBoy.listRoms failed: " .. tostring(value)
    end

    local files = {}
    for name in value:gmatch("([^|\n]+)") do
        if name ~= "" then files[name:lower()] = name end
    end
    return files
end

local function registerItem(entry, title)
    local fullType = "KBM.GBRom_" .. entry.id
    -- These items are loaded by the normal script pipeline, which initializes
    -- their type, media properties and save registry. cloneItemType in B42.21
    -- only inserts an empty Item and must not be used here.
    local manager = getScriptManager()
    local item = manager and manager:FindItem(fullType)
    if not item or not item:getItemType() then
        return nil, "missing or invalid script definition: " .. fullType
    end

    local ok, err = pcall(function()
        item:setDisplayName("Game Boy Cartridge - " .. title)
        item:DoParam("Hidden = false")
    end)
    if not ok then return nil, tostring(err) end
    return fullType
end

function GBRomRuntime.initialize()
    if GBRomRuntime.initialized then return true end

    local manager = getScriptManager()
    for _, entry in ipairs(Manifest.entries or {}) do
        local item = manager and manager:FindItem("KBM.GBRom_" .. entry.id)
        if item then item:DoParam("Hidden = true") end
    end

    local installed, scanError = readInstalledRoms()
    if not installed then
        print("[KBM][GBRomRuntime] scan deferred: " .. tostring(scanError))
        return false
    end

    for _, entry in ipairs(Manifest.entries or {}) do
        local found
        for _, alias in ipairs(entry.aliases or {}) do
            found = installed[alias:lower()]
            if found then break end
        end

        if found then
            local title = found:gsub("%.[Gg][Bb][Cc]?$", "")
            local fullType, registerError = registerItem(entry, title)
            if fullType then
                local runtimeEntry = {
                    appId = "gb",
                    id = entry.id,
                    title = title,
                    rarity = entry.rarity or "uncommon",
                    rom = found,
                    itemType = fullType,
                }
                GBRomRuntime.entries[#GBRomRuntime.entries + 1] = runtimeEntry
                GBRomRuntime.byItemType[fullType] = runtimeEntry
            else
                print("[KBM][GBRomRuntime] item registration skipped for "
                    .. tostring(found) .. ": " .. tostring(registerError))
            end
        end
    end

    GBRomRuntime.initialized = true
    print("[KBM][GBRomRuntime] detected and registered "
        .. tostring(#GBRomRuntime.entries) .. " local ROM cartridges")
    return true
end

function GBRomRuntime.getByItemType(fullType)
    return GBRomRuntime.byItemType[fullType]
end

function GBRomRuntime.getEntries()
    return GBRomRuntime.entries
end

-- Bind a real recording so vanilla accepts the cartridge in the media slot.
-- RequiresMediaIndex is not a recognized Item parameter in B42.21.
function KBMGB_OnCreateBYOR(item)
    local id = item:getFullType():match("^KBM%.GBRom_([a-f0-9]+)$")
    if not id then return end
    local radio = getZomboidRadio()
    local media = radio and radio:getRecordedMedia()
    local data = media and media:getMediaData("KBM_GB_BYOR_" .. id)
    if data then
        item:setRecordedMediaData(data)
    else
        print("[KBM][GBRomRuntime] missing recording for " .. item:getFullType())
    end
end

return GBRomRuntime
