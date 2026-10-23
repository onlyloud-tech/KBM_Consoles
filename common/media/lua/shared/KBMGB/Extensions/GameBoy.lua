local Registry = require "KBMGB/System/ExtensionRegistry"

Registry.registerDevice({
    id = "consoles",
    modId = "KBM_Consoles",
    name = "KnoxBox Consoles",
    capabilities = { "gb", "gbc", "nes", "byor" },
})
