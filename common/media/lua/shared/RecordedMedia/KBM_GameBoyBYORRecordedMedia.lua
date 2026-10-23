-- Metadata only: no ROM data or random recorded-media spawning.
RecMedia = RecMedia or {}
local Manifest = require "KBMGB/System/GBRomManifest"
for _, entry in ipairs(Manifest.entries) do
    RecMedia["KBM_GB_BYOR_" .. entry.id] = {
        itemDisplayName = "Game Boy Cartridge - " .. entry.title,
        title = entry.title,
        subtitle = "Game Boy Cartridge",
        author = "",
        extra = "Requires your local ROM file.",
        spawning = 0,
        category = "VHS",
        lines = { { text = "Game Boy cartridge", r = 0.55, g = 0.8, b = 0.55, codes = "KBM-GB" } },
    }
end
