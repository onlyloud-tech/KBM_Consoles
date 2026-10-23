-- SoftwareMediaRegistry.lua - KnoxBox Consoles build
-- Keeps media gating scoped to cartridges provided by this console pack.

local SoftwareMediaRegistry = {}
local okRomCatalog, RomMediaCatalog = pcall(require, "KBMGB/System/RomMediaCatalog")

SoftwareMediaRegistry.registry = {}

local function findByAlias(key)
    key = tostring(key or "")
    if key == "" then return nil, nil end

    for regKey, entry in pairs(SoftwareMediaRegistry.registry) do
        if tostring(regKey) == key then
            return entry, regKey
        end
        if type(entry.aliases) == "table" then
            for _, alias in ipairs(entry.aliases) do
                if tostring(alias) == key then
                    return entry, regKey
                end
            end
        end
    end

    return nil, nil
end

function SoftwareMediaRegistry.get(appId, softwareKeyOverride)
    local key = softwareKeyOverride or appId
    if not key or key == "" then return nil end

    local entry = SoftwareMediaRegistry.registry[key]
    if entry then return entry end

    local aliasEntry = findByAlias(key)
    return aliasEntry
end

function SoftwareMediaRegistry.getItemType(appId, softwareKeyOverride)
    local entry = SoftwareMediaRegistry.get(appId, softwareKeyOverride)
    return entry and entry.itemType or nil
end

function SoftwareMediaRegistry.getLabel(appId, softwareKeyOverride)
    local entry = SoftwareMediaRegistry.get(appId, softwareKeyOverride)
    return entry and entry.label or nil
end

function SoftwareMediaRegistry.hasMedia(appId, player, softwareKeyOverride)
    local entry = SoftwareMediaRegistry.get(appId, softwareKeyOverride)
    if not entry then return true end
    if not player or not player.getInventory then return false end

    local inv = player:getInventory()
    if not inv then return false end

    local item = inv:getFirstTypeRecurse(entry.itemType)
    return item ~= nil
end

function SoftwareMediaRegistry.register(key, entry)
    if type(key) ~= "string" or key == "" then return false end
    if type(entry) ~= "table" or not entry.itemType then return false end
    SoftwareMediaRegistry.registry[key] = entry
    return true
end

if okRomCatalog and RomMediaCatalog and type(RomMediaCatalog.entries) == "table" then
    for _, romEntry in ipairs(RomMediaCatalog.entries) do
        if romEntry.appId == "gb" or romEntry.appId == "nes" then
            SoftwareMediaRegistry.register(romEntry.softwareKey, {
                label = romEntry.label,
                itemType = romEntry.itemType,
                kind = "game",
                appId = romEntry.appId,
                rom = romEntry.rom,
                aliases = romEntry.aliases,
            })
        end
    end
end

return SoftwareMediaRegistry
