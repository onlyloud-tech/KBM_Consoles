-- File: media/lua/shared/KBMGB/System/RomMediaCatalog.lua
-- Per-ROM media gating catalog for emulator content.
-- Maps ROM filenames to physical cartridge items so emulator apps can ask
-- "is this ROM allowed to run? does the player actually own the cart?"
-- Runtime source of truth:
--   This shared file is required by both B42 client radio patches
--   (KBM_NESRadioPatch / KBM_GameBoyRadioPatch).

local RomMediaCatalog = {}

local function normalizeTypeToken(value)
    return tostring(value or "")
        :gsub("^%s+", "")
        :gsub("%s+$", "")
        :lower()
end

local function shortTypeToken(value)
    local token = normalizeTypeToken(value)
    return token:gsub("^[^%.]+%.", "")
end

RomMediaCatalog.entries = {
    {
        softwareKey = "nes_240p_test_suite",
        appId = "nes",
        rarity = "uncommon",
        rom = "240p-test-suite_240pee.nes",
        itemType = "KBM.NES240pTestSuiteCartridge",
        label = "NES Cartridge - 240p Test Suite",
        recordedMediaId = "KBM_NES_240P_TEST_SUITE",
        aliases = { "240ptest", "240p" },
    },
    {
        softwareKey = "nes_thwaite",
        appId = "nes",
        rarity = "uncommon",
        rom = "thwaite_thwaite128.nes",
        itemType = "KBM.NESThwaiteCartridge",
        label = "NES Cartridge - Thwaite",
        recordedMediaId = "KBM_NES_THWAITE",
        aliases = { "thwaite" },
    },
    {
        softwareKey = "nes_codemaster",
        appId = "nes",
        rarity = "uncommon",
        rom = "codemaster_codemaster.nes",
        itemType = "KBM.NESCodeMasterCartridge",
        label = "NES Cartridge - CodeMaster",
        recordedMediaId = "KBM_NES_CODEMASTER",
        aliases = { "codemaster" },
    },
    {
        softwareKey = "nes_concentration_room",
        appId = "nes",
        rarity = "rare",
        rom = "concentration-room_croom.nes",
        itemType = "KBM.NESConcentrationRoomCartridge",
        label = "NES Cartridge - Concentration Room",
        recordedMediaId = "KBM_NES_CONCENTRATION_ROOM",
        aliases = { "croom", "concentrationroom" },
    },
    {
        softwareKey = "nes_cybercoaster",
        appId = "nes",
        rarity = "uncommon",
        rom = "cybercoaster_cybercoaster.nes",
        itemType = "KBM.NESCybercoasterCartridge",
        label = "NES Cartridge - Cybercoaster",
        recordedMediaId = "KBM_NES_CYBERCOASTER",
        aliases = { "cybercoaster" },
    },
    {
        softwareKey = "nes_falling",
        appId = "nes",
        rarity = "uncommon",
        rom = "falling_falling.nes",
        itemType = "KBM.NESFallingCartridge",
        label = "NES Cartridge - Falling",
        recordedMediaId = "KBM_NES_FALLING",
        aliases = { "falling" },
    },
    {
        softwareKey = "nes_galaxy_patrol",
        appId = "nes",
        rarity = "uncommon",
        rom = "galaxy-patrol_galaxypatrol.nes",
        itemType = "KBM.NESGalaxyPatrolCartridge",
        label = "NES Cartridge - Galaxy Patrol",
        recordedMediaId = "KBM_NES_GALAXY_PATROL",
        aliases = { "galaxypatrol" },
    },
    {
        softwareKey = "nes_jeznes",
        appId = "nes",
        rarity = "uncommon",
        rom = "jeznes_jeznes.nes",
        itemType = "KBM.NESJezNESCartridge",
        label = "NES Cartridge - JezNES",
        recordedMediaId = "KBM_NES_JEZNES",
        aliases = { "jeznes" },
    },
    {
        softwareKey = "nes_starter_kit",
        appId = "nes",
        rarity = "uncommon",
        rom = "nes-starter-kit_adventure.nes",
        itemType = "KBM.NESStarterKitCartridge",
        label = "NES Cartridge - NES Starter Kit Adventure",
        recordedMediaId = "KBM_NES_STARTER_KIT",
        aliases = { "starterkit", "adventure" },
    },
    {
        softwareKey = "nes_nesgame_6502",
        appId = "nes",
        rarity = "uncommon",
        rom = "nesgame6502_game.nes",
        itemType = "KBM.NESGame6502Cartridge",
        label = "NES Cartridge - 6502 NES Game",
        recordedMediaId = "KBM_NES_NESGAME_6502",
        aliases = { "nesgame", "6502game" },
    },
    {
        softwareKey = "nes_opennes_snake",
        appId = "nes",
        rarity = "uncommon",
        rom = "opennes-snake_snake.nes",
        itemType = "KBM.NESOpenNESSnakeCartridge",
        label = "NES Cartridge - openNES Snake",
        recordedMediaId = "KBM_NES_OPENNES_SNAKE",
        aliases = { "opennessnake", "snake" },
    },
    {
        softwareKey = "nes_rhde",
        appId = "nes",
        rarity = "rare",
        rom = "rhde-furniture-fight_rhde.nes",
        itemType = "KBM.NESRHDECartridge",
        label = "NES Cartridge - RHDE: Furniture Fight",
        recordedMediaId = "KBM_NES_RHDE",
        aliases = { "rhde", "furniturefight" },
    },
    {
        softwareKey = "nes_roborun",
        appId = "nes",
        rarity = "uncommon",
        rom = "roborun_roborun-1.1.nes",
        itemType = "KBM.NESRoboRunCartridge",
        label = "NES Cartridge - RoboRun NES",
        recordedMediaId = "KBM_NES_ROBORUN",
        aliases = { "roborun" },
    },
    {
        softwareKey = "nes_zap_ruder",
        appId = "nes",
        rarity = "rare",
        rom = "zap-ruder_ruder.nes",
        itemType = "KBM.NESZapRuderCartridge",
        label = "NES Cartridge - Zap Ruder",
        recordedMediaId = "KBM_NES_ZAP_RUDER",
        aliases = { "zapruder", "ruder" },
    },
    {
        softwareKey = "nes_table_tennis",
        appId = "nes",
        rarity = "uncommon",
        rom = "8bit-table-tennis_table-tennis.nes",
        itemType = "KBM.NESTableTennisCartridge",
        label = "NES Cartridge - 8-Bit Table Tennis",
        recordedMediaId = "KBM_NES_TABLE_TENNIS",
        aliases = { "tabletennis", "pingpong" },
    },
    {
        softwareKey = "doom_doom1",
        appId = "doom",
        rarity = "rare",
        wadId = "doom1",
        rom = "DOOM1.WAD",
        itemType = "KBM.DoomSharewareCartridge",
        label = "Doom Cartridge - DOOM Shareware",
        recordedMediaId = "KBM_NES_DOOM_SHAREWARE",
        aliases = { "doom", "doom1", "doomshareware" },
    },
    {
        softwareKey = "doom_freedoom1",
        appId = "doom",
        rarity = "rare",
        wadId = "freedoom1",
        rom = "freedoom1.wad",
        itemType = "KBM.FreedoomPhase1Cartridge",
        label = "Doom Cartridge - Freedoom: Phase 1",
        recordedMediaId = "KBM_NES_FREEDOOM1",
        aliases = { "freedoom1", "freedoomphase1" },
    },
    {
        softwareKey = "doom_freedoom2",
        appId = "doom",
        rarity = "rare",
        wadId = "freedoom2",
        rom = "freedoom2.wad",
        itemType = "KBM.FreedoomPhase2Cartridge",
        label = "Doom Cartridge - Freedoom: Phase 2",
        recordedMediaId = "KBM_NES_FREEDOOM2",
        aliases = { "freedoom2", "freedoomphase2" },
    },
    {
        softwareKey = "doom_freedm",
        appId = "doom",
        rarity = "uncommon",
        wadId = "freedm",
        rom = "freedm.wad",
        itemType = "KBM.FreeDMCartridge",
        label = "Doom Cartridge - FreeDM",
        recordedMediaId = "KBM_NES_FREEDM",
        aliases = { "freedm" },
    },
    {
        softwareKey = "doom_hacx",
        appId = "doom",
        rarity = "rare",
        wadId = "hacx",
        rom = "hacx.wad",
        itemType = "KBM.HacXCartridge",
        label = "Doom Cartridge - HacX: Twitch 'n Kill",
        recordedMediaId = "KBM_NES_HACX",
        aliases = { "hacx" },
    },
    {
        softwareKey = "gb_2048",
        appId = "gb",
        rarity = "uncommon",
        rom = "2048gb_2048.gb",
        itemType = "KBM.GB2048Cartridge",
        label = "Game Boy Cartridge - 2048gb",
        recordedMediaId = "KBM_GB_2048",
        aliases = { "2048gb", "2048" },
    },
    {
        softwareKey = "gb_aevilia",
        appId = "gb",
        rarity = "uncommon",
        rom = "aevilia_aevilia.gbc",
        itemType = "KBM.GBAeviliaCartridge",
        label = "Game Boy Cartridge - Aevilia",
        recordedMediaId = "KBM_GB_Aevilia",
        aliases = { "aevilia" },
    },
    {
        softwareKey = "gb_airplanz",
        appId = "gb",
        rarity = "uncommon",
        rom = "airplanz_AIRPLANZ.gb",
        itemType = "KBM.GBAirplanzCartridge",
        label = "Game Boy Cartridge - AIRPLANZ",
        recordedMediaId = "KBM_GB_Airplanz",
        aliases = { "airplanz" },
    },
    {
        softwareKey = "gb_big2small",
        appId = "gb",
        rarity = "uncommon",
        rom = "big2small_big2small.gb",
        itemType = "KBM.GBBig2smallCartridge",
        label = "Game Boy Cartridge - Big2Small",
        recordedMediaId = "KBM_GB_Big2small",
        aliases = { "big2small" },
    },
    {
        softwareKey = "gb_bitbang",
        appId = "gb",
        rarity = "uncommon",
        rom = "bit_bang_bit_bang.gb",
        itemType = "KBM.GBBitbangCartridge",
        label = "Game Boy Cartridge - Bit Bang",
        recordedMediaId = "KBM_GB_Bitbang",
        aliases = { "bitbang", "bit_bang" },
    },



    {
        softwareKey = "gb_dawnwillcome",
        appId = "gb",
        rarity = "uncommon",
        rom = "dawn-will-come_DawnWillCome.gb",
        itemType = "KBM.GBDawnwillcomeCartridge",
        label = "Game Boy Cartridge - Dawn Will Come",
        recordedMediaId = "KBM_GB_Dawnwillcome",
        aliases = { "dawnwillcome" },
    },


    {
        softwareKey = "gb_gbcorp",
        appId = "gb",
        rarity = "uncommon",
        rom = "gb-corp_gbcorp.gb",
        itemType = "KBM.GBGbcorpCartridge",
        label = "Game Boy Cartridge - GB Corp.",
        recordedMediaId = "KBM_GB_Gbcorp",
        aliases = { "gbcorp" },
    },

    {
        softwareKey = "gb_geometrix",
        appId = "gb",
        rarity = "uncommon",
        rom = "geometrix_geometrix.gbc",
        itemType = "KBM.GBGeometrixCartridge",
        label = "Game Boy Cartridge - Geometrix",
        recordedMediaId = "KBM_GB_Geometrix",
        aliases = { "geometrix" },
    },

    {
        softwareKey = "gb_jpgame",
        appId = "gb",
        rarity = "uncommon",
        rom = "jp_jp.gb",
        itemType = "KBM.GBJpgameCartridge",
        label = "Game Boy Cartridge - JP",
        recordedMediaId = "KBM_GB_Jpgame",
        aliases = { "jpgame", "jp" },
    },
    {
        softwareKey = "gb_libbet",
        appId = "gb",
        rarity = "uncommon",
        rom = "libbet_libbet.gb",
        itemType = "KBM.GBLibbetCartridge",
        label = "Game Boy Cartridge - Libbet and the Magic Floor",
        recordedMediaId = "KBM_GB_Libbet",
        aliases = { "libbet" },
    },

    {
        softwareKey = "gb_maxpirate",
        appId = "gb",
        rarity = "uncommon",
        rom = "maxpirate_maxpirate.gb",
        itemType = "KBM.GBMaxpirateCartridge",
        label = "Game Boy Cartridge - Max Pirate",
        recordedMediaId = "KBM_GB_Maxpirate",
        aliases = { "maxpirate" },
    },
    {
        softwareKey = "gb_maxpirateeb",
        appId = "gb",
        rarity = "uncommon",
        rom = "maxpirateeb_maxpirateeb.gb",
        itemType = "KBM.GBMaxpirateebCartridge",
        label = "Game Boy Cartridge - Max Pirate Extra Boom",
        recordedMediaId = "KBM_GB_Maxpirateeb",
        aliases = { "maxpirateeb" },
    },


    {
        softwareKey = "gb_postbot",
        appId = "gb",
        rarity = "uncommon",
        rom = "postbot_PostBot.gb",
        itemType = "KBM.GBPostbotCartridge",
        label = "Game Boy Cartridge - PostBot",
        recordedMediaId = "KBM_GB_Postbot",
        aliases = { "postbot" },
    },
    {
        softwareKey = "gb_rebound",
        appId = "gb",
        rarity = "uncommon",
        rom = "rebound_Rebound.gbc",
        itemType = "KBM.GBReboundCartridge",
        label = "Game Boy Cartridge - Rebound",
        recordedMediaId = "KBM_GB_Rebound",
        aliases = { "rebound" },
    },


    {
        softwareKey = "gb_waternet",
        appId = "gb",
        rarity = "uncommon",
        rom = "waternet_waternet.gbc",
        itemType = "KBM.GBWaternetCartridge",
        label = "Game Boy Cartridge - Waternet",
        recordedMediaId = "KBM_GB_Waternet",
        aliases = { "waternet" },
    },

    {
        softwareKey = "gb_adjustris",
        appId = "gb",
        rarity = "uncommon",
        rom = "adjustris_adjustris.gb",
        itemType = "KBM.GBAdjustrisCartridge",
        label = "Game Boy Cartridge - Adjustris",
        recordedMediaId = "KBM_GB_Adjustris",
        aliases = { "adjustris" },
    },

    {
        softwareKey = "gb_squishy_the_turtle",
        appId = "gb",
        rarity = "uncommon",
        rom = "squishy-the-turtle_squishy-magfest.gb",
        itemType = "KBM.GBSquishyTheTurtleCartridge",
        label = "Game Boy Cartridge - Squishy the Turtle",
        recordedMediaId = "KBM_GB_SquishyTheTurtle",
        aliases = { "squishy-the-turtle" },
    },

    {
        softwareKey = "gb_square_fall",
        appId = "gb",
        rarity = "uncommon",
        rom = "square-fall_square_fall_v01.gb",
        itemType = "KBM.GBSquareFallCartridge",
        label = "Game Boy Cartridge - Square Fall",
        recordedMediaId = "KBM_GB_SquareFall",
        aliases = { "square-fall" },
    },

    {
        softwareKey = "gb_sushi_nights",
        appId = "gb",
        rarity = "uncommon",
        rom = "sushi-nights_SushiNights.gb",
        itemType = "KBM.GBSushiNightsCartridge",
        label = "Game Boy Cartridge - Sushi Nights",
        recordedMediaId = "KBM_GB_SushiNights",
        aliases = { "sushi-nights" },
    },

    {
        softwareKey = "gb_flooder",
        appId = "gb",
        rarity = "uncommon",
        rom = "flooder_flooder.gb",
        itemType = "KBM.GBFlooderCartridge",
        label = "Game Boy Cartridge - Flooder",
        recordedMediaId = "KBM_GB_Flooder",
        aliases = { "flooder" },
    },

    {
        softwareKey = "gb_gb_pong_clone",
        appId = "gb",
        rarity = "uncommon",
        rom = "gb-pong-clone_gb-pong.gb",
        itemType = "KBM.GBPongCartridge",
        label = "Game Boy Cartridge - GB Pong",
        recordedMediaId = "KBM_GB_Pong",
        aliases = { "gb-pong-clone" },
    },

    {
        softwareKey = "gb_tobutobugirl",
        appId = "gb",
        rarity = "uncommon",
        rom = "tobutobugirl_tobu.gb",
        itemType = "KBM.GBTobuTobuGirlCartridge",
        label = "Game Boy Cartridge - Tobu Tobu Girl",
        recordedMediaId = "KBM_GB_TobuTobuGirl",
        aliases = { "tobutobugirl" },
    },

    {
        softwareKey = "gb_tobutobugirldeluxe",
        appId = "gb",
        rarity = "uncommon",
        rom = "tobutobugirldeluxe_tobudx.gb",
        itemType = "KBM.GBTobuTobuGirlDeluxeCartridge",
        label = "Game Boy Cartridge - Tobu Tobu Girl Deluxe",
        recordedMediaId = "KBM_GB_TobuTobuGirlDeluxe",
        aliases = { "tobutobugirldeluxe" },
    },

    {
        softwareKey = "gb_snake",
        appId = "gb",
        rarity = "uncommon",
        rom = "snake_snake.gb",
        itemType = "KBM.GBSnakeCartridge",
        label = "Game Boy Cartridge - Snake",
        recordedMediaId = "KBM_GB_Snake",
        aliases = { "snake" },
    },

}

-- Looks up a registered ROM by app id (e.g. "NES") + ROM filename.
function RomMediaCatalog.getByAppAndRom(appId, romName)
    local wantApp = tostring(appId or "")
    local wantRom = tostring(romName or "")
    if wantApp == "" or wantRom == "" then return nil end
    local wantRomLower = wantRom:lower()
    for _, entry in ipairs(RomMediaCatalog.entries) do
        if entry.appId == wantApp and tostring(entry.rom or ""):lower() == wantRomLower then
            return entry
        end
    end
    return nil
end

-- Returns the SoftwareMediaRegistry key bound to a given ROM, if any.
function RomMediaCatalog.getSoftwareKey(appId, romName)
    local entry = RomMediaCatalog.getByAppAndRom(appId, romName)
    return entry and entry.softwareKey or nil
end

-- Reverse lookup: given a cartridge item type, return the catalog entry.
function RomMediaCatalog.getByItemType(itemType)
    local wantItemType = normalizeTypeToken(itemType)
    if wantItemType == "" then return nil end
    local wantShortType = shortTypeToken(wantItemType)

    for _, entry in ipairs(RomMediaCatalog.entries) do
        local entryType = normalizeTypeToken(entry.itemType)
        if entryType == wantItemType then
            return entry
        end

        -- Be tolerant of module-prefix differences (e.g. KBM.FooCartridge vs
        -- another module prefix) while keeping strict cartridge-name matching.
        if entryType ~= "" and shortTypeToken(entryType) == wantShortType then
            return entry
        end

        if type(entry.itemTypeAliases) == "table" then
            for _, alias in ipairs(entry.itemTypeAliases) do
                local aliasType = normalizeTypeToken(alias)
                if aliasType == wantItemType or shortTypeToken(aliasType) == wantShortType then
                    return entry
                end
            end
        end
    end
    return nil
end

-- Returns the list of every cartridge item type known to the catalog.
function RomMediaCatalog.getItemTypes()
    local out = {}
    for _, entry in ipairs(RomMediaCatalog.entries) do
        out[#out + 1] = entry.itemType
    end
    return out
end

return RomMediaCatalog
