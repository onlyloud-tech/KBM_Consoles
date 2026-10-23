-- Bundled WAD catalog for the KnoxBox console Doom runtime.

local Catalog = {}

Catalog.entries = {
    {
        id = "doom1",
        filename = "DOOM1.WAD",
        displayName = "DOOM Shareware",
        author = "id Software",
        genre = "Classic FPS",
        license = "shareware-redistributable",
        description = "The shareware episode Knee-Deep in the Dead.",
        shipped = true,
        startupMode = "immediate_game",
    },
    {
        id = "freedoom1",
        filename = "freedoom1.wad",
        displayName = "Freedoom: Phase 1",
        author = "Freedoom Project",
        genre = "Classic FPS",
        license = "BSD-3-Clause",
        description = "A complete free-content first-person shooter campaign.",
        shipped = true,
        startupMode = "immediate_game",
    },
    {
        id = "freedoom2",
        filename = "freedoom2.wad",
        displayName = "Freedoom: Phase 2",
        author = "Freedoom Project",
        genre = "Classic FPS",
        license = "BSD-3-Clause",
        description = "A complete 32-level free-content campaign compatible with Doom II.",
        shipped = true,
        startupMode = "immediate_game",
    },
    {
        id = "freedm",
        filename = "freedm.wad",
        displayName = "FreeDM",
        author = "Freedoom Project",
        genre = "Arena Deathmatch",
        license = "BSD-3-Clause",
        description = "A free-content collection of deathmatch arenas.",
        shipped = true,
        startupMode = "immediate_game",
    },
    {
        id = "hacx",
        filename = "hacx.wad",
        displayName = "HacX: Twitch 'n Kill",
        author = "Banjo Software",
        genre = "Cyberpunk FPS",
        license = "freeware",
        description = "A cyberpunk-themed standalone total conversion built on the Doom II engine.",
        shipped = true,
        startupMode = "immediate_game",
    },
}
Catalog.byId = {}
for _, entry in ipairs(Catalog.entries) do Catalog.byId[entry.id] = entry end
function Catalog.get(id) return Catalog.byId[id] end
function Catalog.shipped()
    local result = {}
    for _, entry in ipairs(Catalog.entries) do
        if entry.shipped then result[#result + 1] = entry end
    end
    return result
end
function Catalog.default() return Catalog.byId.freedoom1 end
return Catalog
