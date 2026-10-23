-- GameBoyCartridgeAutoAttach.lua
-- Registers a tick loop that auto-attaches GB ROM metadata to GB cartridge
-- items in the player's inventory. The actual logic lives in the shared
-- CartridgeAutoAttach factory; this file just configures it for GB carts.

local CartridgeAutoAttach = require("KBMGB/System/CartridgeAutoAttach")

CartridgeAutoAttach.register({
    appIds = { "gb" },
    logTag = "GameBoyCartridgeAutoAttach",
})