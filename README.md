<div align="center">
  
# TaskBlocks

**In-game automation scripting for Minecraft**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-green?style=flat-square)](https://minecraft.net) [![Fabric](https://img.shields.io/badge/Fabric-0.19.3+-blue?style=flat-square)](https://fabricmc.net)

*Create, manage and run automation scripts directly in Minecraft — no modding knowledge required.*

---

## What is TaskBlocks?

TaskBlocks is a **Fabric client-side mod** that lets you write and run automation scripts directly in-game using simple `.tbs` files. No external tools, no modding knowledge, no server-side install.

Scripts are plain text — easy to write by hand, or generate automatically by recording your own movement in-game.

---

## Features

- 🎮 **In-game script manager** — press `J` to create, run, and manage scripts without leaving the game
- ⌨️ **Keybinds** for every script, including modifier combos like `CTRL+K`
- 📊 **Variables, flow control, and functions** — a real little scripting language: `if`/`loop`/`goto`, reusable functions, arithmetic, random numbers
- 👂 **Event listeners** — background checks that react instantly to things like low health, a full inventory, or a nearby mob
- 🎥 **Macro & Look Recorders** — record your own movement and camera into a working script instead of writing one by hand
- 🎯 **Precise and natural movement** — exact positioning when you need it, real physics when you want it to look human
- 🖥️ **HUD overlay**, **anti-AFK**, and an **update checker** — the quality-of-life stuff that makes long-running scripts pleasant to use

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

Prefer not to write it by hand? Press `J` then **➕** to create a script through the menu instead.

---

## Documentation

Full docs live on the [wiki](https://github.com/WisePlace/TaskBlocks/wiki), including the [Script Format](https://github.com/WisePlace/TaskBlocks/wiki/Script-Format), the full [Actions Overview](https://github.com/WisePlace/TaskBlocks/wiki/Actions-Overview), and the [Menu & Overlay](https://github.com/WisePlace/TaskBlocks/wiki/Menu-and-Overlay) guide.

---

## License

See [LICENSE](LICENSE).
