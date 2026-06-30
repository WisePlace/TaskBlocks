<div align="center">

# TaskBlocks

**In-game automation scripting for Minecraft**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-green?style=flat-square)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.3+-blue?style=flat-square)](https://fabricmc.net)

*Create, manage and run automation scripts directly in Minecraft — no mods knowledge required.*

</div>

---

## What is TaskBlocks?

TaskBlocks is a **Fabric client-side mod** for Minecraft 1.21.11 that lets you write simple automation scripts using `.tbs` files. Scripts are stored locally, loaded automatically, and can be started with a keybind or from the in-game menu.

No programming knowledge needed — scripts are plain text files with simple actions like `left_click`, `pause(1, s)` or `say(Hello!, local)`.

---

## Features

- 🎮 **In-game script manager** — Press `J` to open the menu, manage and run your scripts
- 📝 **Simple `.tbs` format** — Human-readable script files, easy to write and share
- ⌨️ **Keybind support** — Assign a start/stop key to each script
- 🔄 **Hot reload** — Edit scripts and reload without restarting the game
- 📂 **Open in editor** — Open any script directly in your text editor from the menu
- 📊 **Variable system** — Capture game data (health, position, items...) into variables
- 🖥️ **HUD overlay** — See the running script name and elapsed time on screen
- 🐛 **Debug mode** — Per-script debug flag that shows errors and warnings in chat
- 40+ built-in actions across 7 categories

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
