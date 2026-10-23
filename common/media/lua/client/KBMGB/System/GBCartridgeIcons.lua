-- Dedicated cropped PNGs: inventory rendering must never sample a UV sheet.
-- Stable per-title choice; no RNG, save mutation, or rarity coupling.
require "ISUI/ISInventoryPane"
require "KBMGB/System/GBRomRuntime"

local textures = {}
local function variantFor(item)
    local id = item:getFullType():match("^KBM%.GBRom_([a-f0-9]+)$")
    if id then return (tonumber(id:sub(-6), 16) % 8) + 1 end
    -- Homebrew and adapter icons follow their existing script/model variant.
    -- Refreshing also corrects cartridges already present in a saved inventory.
    if not item:getFullType():match("^KBM%.") then return nil end
    local script = item:getScriptItem()
    local icon = script and script:getIcon()
    local variant = icon and icon:match("^gb_cart_00([1-8])$")
    return variant and tonumber(variant) or nil
end

local function apply(item)
    if not item then return end
    local variant = variantFor(item)
    if not variant then return end
    local texture = textures[variant]
    if not texture then
        texture = getTexture(string.format("media/textures/Item_gb_cart_%03d.png", variant))
        if not texture then return end
        textures[variant] = texture
    end
    item:setTexture(texture)
end

-- Creation covers newly spawned cartridges. Refresh covers loaded saves,
-- loot containers and transferred items, without an every-tick inventory scan.
local originalCreate = KBMGB_OnCreateBYOR
function KBMGB_OnCreateBYOR(item)
    originalCreate(item)
    apply(item)
end

local originalRefresh = ISInventoryPane.refreshContainer
function ISInventoryPane:refreshContainer(...)
    if self.inventory then
        local items = self.inventory:getItems()
        for i = 0, items:size() - 1 do apply(items:get(i)) end
    end
    return originalRefresh(self, ...)
end

return { apply = apply, variantFor = variantFor }
