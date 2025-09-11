Accessory (for AuraSkills)

Accessory is a lightweight accessories system for servers running AuraSkills. You can fully customize the GUI (title, size, locked-frame slots) and configure which items are allowed in each slot — by lore keywords.

🚀 Getting Started

Java requirement

Java 17 for Minecraft 1.21+

Dependencies

AuraSkills (required)

Install

Put Accessory.jar in /plugins

Restart your server

Open the GUI

Use /inv to open the accessories GUI

Place items that match slot rules (by lore keywords)

📦 Commands Command Description Permission /inv Opens the Accessories GUI accessory.inv /accessory reload Reloads config (GUI, frame, slots) accessory.reload ⚙️ Configuration Example

GUI appearance
title: "Accessories", size: 9 # must be multiple of 9 (9–54)

Locked slots
frame: slots: [0, 2, 4, 6, 8]

Accessory slots (lore check)
Accessory: "1": lore: ["[Ring]"] "3": lore: ["[Necklace]"] "5": lore: ["[Bracelet]"] "7": lore: ["[Talisman]"]

Locked slot item
locked-slot-item: type: "BLACK_STAINED_GLASS_PANE" custom-model-data: 12345 hide-tooltip: true name: "<gray>Locked" lore: - "<dark_gray>Slot is locked"

🔑 How It Works

Locked frame slots Configured in frame.slots, show a locked pane item. Players cannot place items here, but can take out non-locked items they accidentally left.

Accessory slots Defined in Accessory.<slot> with lore keywords. An item can only be placed if its lore matches one of the keywords.

📝 Notes

Inventory size must be a multiple of 9 (9–54).

Titles use MiniMessage, so gradients and formatting work on Paper servers.

Only slots defined in Accessory.<slot> accept items — others are blocked.

Works best when combined with plugins that add or modify item lore.
