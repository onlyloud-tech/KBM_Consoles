-- Gameplay tiers, independent of cartridge artwork and real-world value.
local Rarity = {}
Rarity.tiers = {
    { id = "common", weight = 65 },
    { id = "uncommon", weight = 25 },
    { id = "rare", weight = 8 },
    { id = "ultraRare", weight = 2 },
}
Rarity.handhelds = {
    { itemType = "KBM.NESConsole", rarity = "common" },
    { itemType = "KBM.GameBoyHandheldClassic", rarity = "common" },
    { itemType = "KBM.GameBoyHandheldGrey", rarity = "common" },
    { itemType = "KBM.GameBoyHandheldGreen", rarity = "uncommon" },
    { itemType = "KBM.GameBoyHandheldRed", rarity = "uncommon" },
    { itemType = "KBM.GameBoyHandheldYellow", rarity = "uncommon" },
    { itemType = "KBM.GameBoyHandheldBlue", rarity = "rare" },
    { itemType = "KBM.GameBoyHandheldPink", rarity = "rare" },
    { itemType = "KBM.GameBoyHandheldClear", rarity = "ultraRare" },
}
Rarity.chances = {
    electronics = { cartridge = 20, handheld = 12 },
    bedroom = { cartridge = 18, handheld = 12 },
}
-- Absolute per-eligible-container percentages. Only Common handhelds gain
-- probability; non-Common rates retain the previous 8%/5% gate times tier share.
Rarity.handheldTierChances = {
    electronics = { common = 9.2, uncommon = 2.0, rare = 0.64, ultraRare = 0.16 },
    bedroom = { common = 10.25, uncommon = 1.25, rare = 0.4, ultraRare = 0.1 },
}
function Rarity.rollHandheldTier(location, random)
    local chances = Rarity.handheldTierChances[location]
    if not chances then return nil end
    local roll = random(10000)
    local threshold = 0
    for _, tier in ipairs(Rarity.tiers) do
        threshold = threshold + math.floor((chances[tier.id] or 0) * 100 + 0.5)
        if roll < threshold then return tier.id end
    end
end
Rarity.secondCartridgeChance = 25
function Rarity.roll(percent, random)
    return random(100) < percent
end
function Rarity.pick(entries, random, excluded)
    local buckets = {}
    for _, tier in ipairs(Rarity.tiers) do buckets[tier.id] = {} end
    for _, entry in ipairs(entries) do
        if not excluded or not excluded[entry.itemType] then
            local bucket = buckets[entry.rarity] or buckets.uncommon
            bucket[#bucket + 1] = entry
        end
    end
    local total = 0
    for _, tier in ipairs(Rarity.tiers) do
        if #buckets[tier.id] > 0 then total = total + tier.weight end
    end
    if total == 0 then return nil end
    local roll = random(total)
    for _, tier in ipairs(Rarity.tiers) do
        local bucket = buckets[tier.id]
        if #bucket > 0 then
            if roll < tier.weight then return bucket[random(#bucket) + 1] end
            roll = roll - tier.weight
        end
    end
end
return Rarity
