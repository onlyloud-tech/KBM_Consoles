-- The movie mod owns the render tick when present. Otherwise consoles drive
-- the same screen renderer for NES and Doom; no movie modules are loaded.
require "RadioCom/ISRadioWindow"

local function radioFor(square, item)
    local objects = square and square.getObjects and square:getObjects() or nil
    if not objects then return nil end
    for i = 0, objects:size() - 1 do
        local object = objects:get(i)
        local data = object and object.getModData and object:getModData() or nil
        if data and data.RadioItemID == item:getID() then return object end
    end
    return nil
end

Events.OnFillWorldObjectContextMenu.Add(function(playerNum, context, worldObjects, test)
    if test or _G.KBMViewpointTVPatchLoaded then return end
    for _, object in ipairs(worldObjects or {}) do
        local square = object and object.getSquare and object:getSquare() or nil
        local worlds = square and square.getWorldObjects and square:getWorldObjects() or nil
        if worlds then
            for i = 0, worlds:size() - 1 do
                local world = worlds:get(i)
                local item = world and world.getItem and world:getItem() or nil
                if item and item:getFullType() == "KBM.ScreenTV" then
                    local radio = radioFor(square, item)
                    if radio then
                        context:addOption("Device Options (KnoxBox TV)",
                            getSpecificPlayer(playerNum), function(player)
                                ISRadioWindow.activate(player, radio)
                            end)
                    end
                    return
                end
            end
        end
    end
end)

Events.OnRenderTick.Add(function()
    if _G.KBMViewpointTVPatchLoaded then return end
    local probe = type(KBMViewpointScreenProbe) == "table" and KBMViewpointScreenProbe
        or (type(com) == "table" and type(com.knoxboxprobe) == "table"
            and com.knoxboxprobe.KBMViewpointScreenProbe)
    if probe and probe.tick then probe.tick() end
end)
