-- File: media/lua/client/KBMGB/System/NESConnectionState.lua
-- Per-TV memory: whether an NES is physically wired into this TV.
--
-- State is stored on the TV item when it is a placed inventory item. The
-- invisible IsoRadio used for its device menu can be recreated after a load.
-- Built-in world TVs keep their state on the world object.

local NESConnectionState = {}

local ROOT_KEY = "KBM_TV_State"

local function linkedTVItem(obj)
    local item = obj and obj.getItem and obj:getItem() or nil
    if item then return item end
    local md = obj and obj.getModData and obj:getModData() or nil
    local itemId = md and md.RadioItemID
    local square = itemId and obj.getSquare and obj:getSquare() or nil
    local worlds = square and square.getWorldObjects and square:getWorldObjects() or nil
    if not worlds then return nil end
    for i = 0, worlds:size() - 1 do
        local world = worlds:get(i)
        local linked = world and world.getItem and world:getItem() or nil
        if linked and linked.getID and tostring(linked:getID()) == tostring(itemId) then
            return linked
        end
    end
    return nil
end

-- A placed TV's inventory item survives reloads; its IsoRadio may not.
local function getRoot(obj, create)
    if not obj or not obj.getModData then return nil end
    local owner = linkedTVItem(obj) or obj
    local md = owner:getModData()
    if not md then return nil end
    local old = owner ~= obj and obj:getModData()[ROOT_KEY] or nil
    if old and not md[ROOT_KEY] then md[ROOT_KEY] = old end
    if create and not md[ROOT_KEY] then md[ROOT_KEY] = {} end
    return md[ROOT_KEY]
end

-- Sends the TV's mod data to other clients (multiplayer sync).
local function transmit(obj)
    if obj and obj.transmitModData then
        pcall(obj.transmitModData, obj)
    end
end

-- Marks the TV as having (or not having) an NES console wired into it.
local function consoleId(console)
    if not console then return nil end
    -- Floor radios use an IsoRadio proxy; its RadioItemID is the ID that was
    -- saved when the player connected the inventory console to the TV.
    local md = console.getModData and console:getModData() or nil
    if md and md.RadioItemID ~= nil then return tostring(md.RadioItemID) end
    if not console.getID then return nil end
    local ok, id = pcall(console.getID, console)
    return ok and id ~= nil and tostring(id) or nil
end

function NESConnectionState.setNESConnected(obj, connected, console)
    local st = getRoot(obj, true)
    if not st then return end
    st.nesConnected = connected and true or false
    st.nesConsoleId = connected and consoleId(console) or nil
    local owner = linkedTVItem(obj)
    if owner and owner ~= obj and obj.getModData then
        obj:getModData()[ROOT_KEY] = st
    end
    transmit(obj)
end

-- True if an NES console is wired into this TV.
function NESConnectionState.isNESConnected(obj, console)
    local st = getRoot(obj, false)
    if not st then return false end
    if not st.nesConnected then return false end
    if not console then return true end
    local id = consoleId(console)
    return id ~= nil and st.nesConsoleId ~= nil and tostring(st.nesConsoleId) == id
end

return NESConnectionState
