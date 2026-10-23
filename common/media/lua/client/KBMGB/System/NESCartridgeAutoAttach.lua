-- NESCartridgeAutoAttach.lua
-- Registers a tick loop that auto-attaches NES/DOOM ROM metadata to the
-- corresponding cartridge items in the player's inventory. The actual
-- logic lives in the shared CartridgeAutoAttach factory; this file just
-- configures it for NES + DOOM carts.

local CartridgeAutoAttach = require("KBMGB/System/CartridgeAutoAttach")

CartridgeAutoAttach.register({
    appIds = { "nes", "doom" },
    logTag = "NESCartridgeAutoAttach",
})
