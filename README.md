# TaskBlocks

**In-game automation scripting for Minecraft**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-green?style=flat-square)](https://minecraft.net) [![Fabric](https://img.shields.io/badge/Fabric-0.19.3+-blue?style=flat-square)](https://fabricmc.net)

*Create, manage and run automation scripts directly in Minecraft — no modding knowledge required.*

---

## Screenshots

<p align="center">
  <img src="docs/images/j-menu.png" alt="The J menu" width="420"/>
  <img src="docs/images/settings-menu.png" alt="Settings menu" width="420"/>
</p>
<p align="center">
  <img src="docs/images/overlay-example.png" alt="HUD overlay while a script runs" width="600"/>
</p>

*(Place your screenshots in `docs/images/` with the file names above — `j-menu.png`, `settings-menu.png`, `overlay-example.png` — and they'll appear here automatically.)*

---

## What is TaskBlocks?

TaskBlocks is a **Fabric client-side mod** for Minecraft 1.21.11 that lets you write and run automation scripts directly in-game using simple `.tbs` files — no external tools, no modding knowledge, no server-side installation.

Scripts are plain text: readable, easy to write by hand, and easy to generate automatically using the built-in **Macro Recorder**.

---

## Features

### Core

- 🎮 **In-game script manager** — press `J` to open the menu, create, run, and manage scripts without leaving the game
- 📝 **Simple `.tbs` format** — human-readable script files, easy to write and share
- ⌨️ **Keybind support**, including modifier combos (`CTRL+K`, etc.) — start/stop any script with its own key
- 🔄 **Hot reload** — edit scripts and reload without restarting the game
- 📂 **Open in editor** — open any script directly in your text editor from the menu, or jump straight to the scripts folder
- 🐛 **Debug mode** — per-script flag that shows errors and warnings in chat

### Scripting language

- 📊 **Variables** — capture game data (health, position, items, speed, enchantments, and more) into variables, generate random numbers, or do arithmetic with `calc()`
- 🔀 **Flow control** — `if` / `else if` / `else`, `loop`, `goto`, infinite `pause`
- 👂 **Event listeners** — background checks (`still`, `teleported`, `inventory_full`, `entity_nearby`, and many more) that fire an action the moment a condition becomes true, even mid-script
- 🧩 **Functions** — define reusable blocks in `[functions]`, call them with real parameters and return values
- 🔗 **Script chaining** — hand off from one script to another with `run_script()`, or `restart` the current one
- 💬 **Chat & prompts** — send messages, or pause the script to prompt the player for text input
- 🖨️ **On-screen text** — `print()` big, styled, fading text directly on screen

### Movement & world

- 🎯 **Precise movement** — walk an exact distance with `move_precise()`, or snap to a block's center or edge with `align()`
- 🚶 **Natural movement** — real key-press physics for anything that should look human
- ⛏️ **Block & item actions** — break, place, and use items, with configurable timeouts
- 🌐 **40+ built-in actions** across 10+ categories — see the [wiki](https://github.com/WisePlace/TaskBlocks/wiki) for the full reference

### Recording

- 🎥 **Macro Recorder (BETA)** — record your own movement, camera, and clicks straight into a working script, complete with a 3-2-1 countdown
- 📹 **Look Recorder** — a lighter recorder for camera movement only, with three detail levels (Detailed / Medium / Low) and clipboard output

### Quality of life

- 🖥️ **HUD overlay** — see the running script's name and elapsed time on screen
- 🌙 **Anti-AFK** — configurable, subtle camera nudges to avoid AFK detection during long listener-driven scripts
- 🔔 **Update checker** — get notified in chat, with a clickable download link, when a new version is released

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft **1.21.11**
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and place it in your `mods/` folder
3. Download `TaskBlocks.jar` from the [latest release](https://github.com/WisePlace/TaskBlocks/releases/latest)
4. Place it in your `mods/` folder
5. Launch the game!

---

## Quick Start

Create a file in `.minecraft/config/TaskBlocks/myscript.tbs`:

```
name=My First Script
author=You
version=1.0
enabled=true
debug=false
start_stop_key=O

[actions]
myHealth=get(health)
say(My health is {myHealth}!, local)
pause(1, s)
sprint_press
forward_press
pause(3, s)
forward_release
sprint_release
say(Done!, local)
end
```

Press `J` in-game to see it appear, or just press `O` to run it directly.

Prefer not to write it by hand? Press `J` then **➕** to create a script through the menu instead — with the option to record your movements straight into it.

---

## Documentation

Full documentation lives on the [wiki](https://github.com/WisePlace/TaskBlocks/wiki):

| Page | What's there |
| --- | --- |
| [Installation](https://github.com/WisePlace/TaskBlocks/wiki/Installation) | Detailed setup steps |
| [Script Format](https://github.com/WisePlace/TaskBlocks/wiki/Script-Format) | How a `.tbs` file is structured |
| [Actions Overview](https://github.com/WisePlace/TaskBlocks/wiki/Actions-Overview) | Every action category, at a glance |
| [Menu & Overlay](https://github.com/WisePlace/TaskBlocks/wiki/Menu-and-Overlay) | Using the in-game interface, Settings, Anti-AFK |
| [Look Recorder](https://github.com/WisePlace/TaskBlocks/wiki/Look-Recorder) | Recording camera movement only |
| [Debug Mode](https://github.com/WisePlace/TaskBlocks/wiki/Debug-Mode) | Troubleshooting your scripts |
| [Slot Reference](https://github.com/WisePlace/TaskBlocks/wiki/Slot-reference) | Inventory slot numbers |

---

## License

See [LICENSE](LICENSE).
