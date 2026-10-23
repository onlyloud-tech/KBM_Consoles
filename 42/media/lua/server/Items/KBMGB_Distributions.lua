require "Items/ProceduralDistributions"
local Runtime = require "KBMGB/System/GBRomRuntime"
local Catalog = require "KBMGB/System/RomMediaCatalog"
local Rarity = require "KBMGB/System/GBLootRarity"
local available = {}
local logged = 0
local markers = {
    ["KBM.GBCartridgeLootElectronics"] = { kind = "cartridge", location = "electronics" },
    ["KBM.GBCartridgeLootBedroom"] = { kind = "cartridge", location = "bedroom" },
    ["KBM.GBHandheldLootElectronics"] = { kind = "handheld", location = "electronics" },
    ["KBM.GBHandheldLootBedroom"] = { kind = "handheld", location = "bedroom" },
    -- Resolve any old internal markers left by the previous test build.
    ["KBM.GBCartridgeLootRoll"] = { kind = "cartridge", location = "electronics" },
    ["KBM.GBHandheldLootRoll"] = { kind = "handheld", location = "electronics" },
}
-- Furniture variants have separate vanilla loot lists. Keep explicit groups
-- so household/school containers cannot inherit the higher electronics rate.
local lootLists = {
    { name = "ElectronicStoreMisc", suffix = "Electronics" },
    { name = "ElectronicStoreComputers", suffix = "Electronics" },
    { name = "CrateElectronics", suffix = "Electronics" },
    { name = "BedroomDresser", suffix = "Bedroom" },
    { name = "BedroomDresserChild", suffix = "Bedroom" },
    { name = "BedroomDresserClassy", suffix = "Bedroom" },
    { name = "BedroomDresserRedneck", suffix = "Bedroom" },
    { name = "BedroomSidetable", suffix = "Bedroom" },
    { name = "BedroomSidetableChild", suffix = "Bedroom" },
    { name = "BedroomSidetableClassy", suffix = "Bedroom" },
    { name = "BedroomSidetableRedneck", suffix = "Bedroom" },
    { name = "WardrobeChild", suffix = "Bedroom" },
    { name = "LivingRoomShelf", suffix = "Bedroom" },
    { name = "LivingRoomShelfClassy", suffix = "Bedroom" },
    { name = "LivingRoomShelfRedneck", suffix = "Bedroom" },
    { name = "LivingRoomShelfNoTapes", suffix = "Bedroom" },
    { name = "LivingRoomSideTable", suffix = "Bedroom" },
    { name = "LivingRoomSideTableClassy", suffix = "Bedroom" },
    { name = "LivingRoomSideTableRedneck", suffix = "Bedroom" },
    { name = "LivingRoomSideTableNoRemote", suffix = "Bedroom" },
    { name = "OfficeDeskHome", suffix = "Bedroom" },
    { name = "OfficeDeskHomeClassy", suffix = "Bedroom" },
    { name = "ClosetShelfGeneric", suffix = "Bedroom" },
    { name = "SchoolLockers", suffix = "Bedroom" },
    { name = "SchoolLockersBad", suffix = "Bedroom" },
}

local function addMarker(listName, itemType)
    local list = ProceduralDistributions.list[listName]
    if not list or not list.items then
        print("[KBMGB] missing procedural list: " .. listName)
        return
    end
    -- Eligibility marker, not a real cartridge roll. The explicit gate below
    -- controls abundance; zero weight prevents markers consuming capacity.
    for i = 1, #list.items, 2 do
        if list.items[i] == itemType then list.items[i + 1] = 10000; return end
    end
    table.insert(list.items, itemType)
    table.insert(list.items, 10000)
end
local function addLoot()
    Runtime.initialize()
    available = {}
    local seen = {}
    local function include(entry)
        if not seen[entry.itemType] then
            seen[entry.itemType] = true
            available[#available + 1] = entry
        end
    end
    for _, entry in ipairs(Catalog.entries) do
        if entry.appId == "gb" or entry.appId == "nes" or entry.appId == "doom" then include(entry) end
    end
    for _, entry in ipairs(Runtime.getEntries()) do include(entry) end
    for _, target in ipairs(lootLists) do
        local name = target.name
        local list = ProceduralDistributions.list[name]
        -- Remove earlier category entries if a distribution merge repeats.
        if list and list.items then
            for i = #list.items - 1, 1, -2 do
                if markers[list.items[i]] then table.remove(list.items, i + 1); table.remove(list.items, i) end
            end
        end
        local suffix = target.suffix
        if #available > 0 then addMarker(name, "KBM.GBCartridgeLoot" .. suffix) end
        addMarker(name, "KBM.GBHandheldLoot" .. suffix)
    end
    print("[KBMGB] console loot gates ready: cartridges=" .. #available .. "; electronics=20%/12%; household-school=18%/12%; caps=2 carts/1 device; lists=25")
end
Events.OnPreDistributionMerge.Add(addLoot)
local function resolveLoot(roomName, containerType, container)
    -- Viewpoint can invoke OnFillContainer with an ItemPickerContainer while
    -- rolling loot. Only an actual inventory container holds spawned items.
    if not container or not instanceof(container, "ItemContainer") then return end
    local pending = {}
    local items = container:getItems()
    for i = items:size() - 1, 0, -1 do
        local item = items:get(i)
        local marker = markers[item:getFullType()]
        if marker then
            -- Collapse all repeated vanilla rolls into one gate per category.
            local old = pending[marker.kind]
            if not old or marker.location == "bedroom" then pending[marker.kind] = marker.location end
            container:Remove(item)
        end
    end
    -- A shared vanilla list must never turn a kitchen fill into GB loot.
    -- Remove internal markers above, but do not roll either category here.
    if tostring(roomName):lower():find("kitchen", 1, true) then return end
    local function describeContainer()
        local square = container.getSourceGrid and container:getSourceGrid()
        local coordinates = square and (tostring(square:getX()) .. "," .. tostring(square:getY())
            .. "," .. tostring(square:getZ())) or "unknown"
        local parent = container.getParent and container:getParent()
        local objectIndex = parent and parent.getObjectIndex and parent:getObjectIndex() or "unknown"
        return " xyz=" .. coordinates .. " objectIndex=" .. tostring(objectIndex)
    end
    local function spawn(kind, choices, excluded)
        local entry = Rarity.pick(choices, ZombRand, excluded)
        if not entry then return false end
        local item = container:AddItem(entry.itemType)
        if not item then print("[KBMGB] loot item could not be added: " .. entry.itemType); return false end
        local present = false
        local current = container:getItems()
        for i = 0, current:size() - 1 do
            if current:get(i) == item then present = true; break end
        end
        if not present then
            print("[KBMGB] loot insertion returned an item absent from the container: " .. entry.itemType
                .. describeContainer())
            return false
        end
        if container.setDrawDirty then container:setDrawDirty(true) end
        if excluded then excluded[entry.itemType] = true end
        if logged < 40 then
            logged = logged + 1
            print("[KBMGB] loot spawned " .. entry.itemType .. " rarity=" .. tostring(entry.rarity)
                .. " room=" .. tostring(roomName) .. " container=" .. tostring(containerType)
                .. describeContainer() .. " present=true")
        end
        return true
    end
    local location = pending.cartridge
    if location and Rarity.roll(Rarity.chances[location].cartridge, ZombRand) then
        local excluded = {}
        if spawn("cartridge", available, excluded) and Rarity.roll(Rarity.secondCartridgeChance, ZombRand) then
            spawn("cartridge", available, excluded)
        end
    end
    location = pending.handheld
    local tier = location and Rarity.rollHandheldTier(location, ZombRand)
    if tier then
        local choices = {}
        for _, entry in ipairs(Rarity.handhelds) do
            if entry.rarity == tier then choices[#choices + 1] = entry end
        end
        spawn("handheld", choices)
    end
end
Events.OnFillContainer.Add(resolveLoot)
