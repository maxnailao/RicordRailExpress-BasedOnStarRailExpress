# RicordRailExpress - An Improved Version Based on StarRailExpress
**[English]** [[简体中文]](README.zh.md)

## Translation Tips

Since the developer's native language is not English, the English version of the README will lag behind the Chinese version in updates. Please read the Chinese README whenever possible.

We also welcome players and developers who provide translations for us! (Except for Simplified Chinese translations.)

## Introduction
RicordRailExpress is an improved version based on StarRailExpress, featuring more original game modes and original roles. The roles aim for balance and fun, and are mostly relatively simple ones.
### Roles
It contains many original roles and modifiers; some of them are the original or improved versions of StarRailExpress's vanilla roles and modifiers.

Roles are mainly divided into the following categories: realistic roles (the majority), roles with paranormal abilities (mostly killer-faction roles), roles based on vanilla Minecraft mobs, fun/meme roles, and fan-made roles from other games and anime.

Some representative ones are listed below:

Realistic roles: Blind Man, Mute Girl, Intellectually Disabled Patient, etc.

Roles with paranormal abilities: Shadow, Phantom, Specter, Apparition, Rewind Killer, Martyr, etc.

Roles based on vanilla Minecraft mobs: Iron Golem, Wizard, Pillager, etc.

Fun/meme roles: Zhuimu, Pigegade, Jiahao, etc. (names come from Chinese community memes)

Fan-made roles from games/anime: Wraith (from Apex Legends), Niko (from CS:GO)

For players who have never touched the Harpy Train DLC series, this DLC is a relatively easy one to get into.



## Please Note!
This Wathe addon is **highly likely** incompatible with any other Wathe addons.

Due to the `Trainmurdermystery` copyright license being ARR, and we have rewritten many Wathe functionalities, publishing it is very difficult even if we wanted to. Hence, this companion mod.

We have rewritten the original `TrainMuderMystery` and switched to `Mojang Mappings`. Some code inevitably uses content from the original. Any similarities are purely coincidental.

However, since we still need Wathe's base decorative blocks, this mod requires Wathe as a prerequisite, even though it cannot execute any of its functions.

This mod completely blocks the original Wathe runtime and operates using this mod's logic instead.

For convenience, we used the `trainmurdermystery` namespace and our own, instead of `wathe` (changing IDs would make map migration a bit troublesome).

Some parts still use `TMM`, as renaming files would require a lot of changes, which is cumbersome.

## Disclaimer

This mod is an addon for the Wathe mod and improves many of its features. It incorporates functionalities and content from `Harpymodloader`, `StupidExpress`, `Noellesroles`, and `Harpy Simple Roles`. Some of the new roles reference roles from the `KinsWathe` mod.

This mod is completely open-source, free, and non-commercial. We use the same `GNU General Public License v3.0 only (GPL-3.0-only)` as the upstream `Noellesroles`.

### What You Can Do (Granted permissions are also required by the StarRailExpress open-source license)
#### Freedom to Use

You may run the program for any purpose, whether personal, academic, or commercial.

#### Freedom to Copy

You may make exact copies (verbatim distribution) of the program's source code or binary versions.

#### Freedom to Modify

You may modify the program to create your own version. Upon modification, you become the copyright holder of the changes.

#### Freedom to Distribute

You may distribute the software (original or modified) to the public through any medium.

#### Commercial Use

You may use `GPL-3.0-only` software for commercial services (e.g., providing software as a service, selling devices containing the software), provided you comply with the obligations below.

### Your Obligations (Core Conditions)
#### Must Open Source (Strong Copyleft)

If you distribute (including binary or source code forms) any work containing `GPL-3.0-only` code (including modified versions or programs integrated via static/dynamic linking), you must provide complete, corresponding source code under the same `GPL-3.0-only` license.

**Note**: Internal use without distribution does not require open-sourcing.

#### License Must Persist

Your modified or derivative work must be licensed entirely under `GPL-3.0-only`; no other license may be used (including not changing to `GPL-2.0` or any non-GPL license). This is the meaning of "`only`"—the version is locked to `GPL-3.0`.

#### No Additional Restrictions

You cannot impose any "further restrictions" on top of `GPL-3.0-only`, such as requiring licensing fees or restricting users from exercising the rights granted by the GPL. Any such additional terms are void.

#### Retain Copyright and Disclaimer Notices

You must not delete or modify copyright notices, license statements, or disclaimers in the source code file headers, regardless of the distribution form.

You must include a complete copy of the `GPL-3.0-only` license with the software.

#### Provide Installation Information (for User Products)

If you install `GPL-3.0-only` software in binary form on a user product (e.g., routers, set-top boxes, or other hardware) for distribution, you must provide installation information to ensure users can install modified versions themselves (no hardware locking allowed).

#### No Linking with Closed Source

You cannot statically or dynamically link `GPL-3.0-only` code with closed-source code for distribution, unless the closed-source portion also complies with `GPL-3.0-only` or qualifies for the license's "system library" exception. Any form of linking that results in distribution constitutes a derivative work and must be fully open-sourced.

## Compatibility
This map is theoretically incompatible with any Wathe addons. It disables Wathe's registration and initialization events, so nothing besides Wathe's resources and data will work.

Since it's still unclear how to disable the tags in Wathe's `data` folder—which cause errors due to missing Wathe item and block registrations—and in order to stay compatible with Wathe-based maps, we have additionally registered Wathe's items and blocks in this mod.

Please note, these items and blocks likely lack their original functionality, so try to avoid using them!

## Development
Before you start, **PLEASE** read [CreateExtention.md](./CreateExtention.md)

## DLC Features
### Roles
We built upon `StarRailExpress`, containing a large amount of that mod's original roles, roles adapted from them, and their modifiers, plus many of our own original roles and modifiers. You cannot install the aforementioned mods alongside this one.
### Items, Entities, Blocks
We have added more items, entities, and blocks to the train. You can view them in the in-game inventory.
### Features
We have added many new commands to the train, such as:
- `/tmm:money` Money management
- `/tmm:switchmap` Switch maps
- `/tmm:game` Game utility commands
- ...

We have also added voting and asynchronous copying to the train, and fixed issues in the original train such as network packets and data component packets.

Preliminary, albeit non-rigorous, tests show a significant reduction in packet count and network load.

## Maps
Stored in `<world save>/train_maps`

Saved as JSON files.

### Map Tools
You can use the `Map Tools` in the in-game `Admin tab` to modify the configuration file quickly through a UI.

Before modifying with the in-game map tools, use `/sre:area_manager create_new` to create a brand new map, or use `/tmm:switchmap load <map ID>` to load a map and modify it as a base.

You can view the map list with `/tmm:switchmap list`.

After finishing your modifications, type `/sre:area_manager save <map name>` to save the map (no file extension needed).

For subfolders, you can achieve this by adding quotes. For example:

```mcfunction
/sre:area_manager save "new/my/map"
```

If the file already exists, an error will be reported. You can append `force` to the command to overwrite and save.

For example:

```mcfunction
/sre:area_manager save "new/my/map" force
```

You can use `/sre:area_manager remove <map ID>` to delete a map.

For other configuration entries, you can use `/sre:area_manager set/get` to set/get/remove them (with tab completion).

Map modifications are not saved automatically; they are only kept temporarily in memory. To save them, please be sure to follow the process described above.

### File Content Format

```json
{
  "spawnPos": { // Spawn point (where players return after the game ends). New players joining will be teleported to the vanilla world spawn instead of here.
    "x": 0,
    "y": 0,
    "z": 0,
    "yaw": 90.0,
    "pitch": 0.0
  },
  "spectatorSpawnPos": { // Spectator spawn point (where players who join after the game starts are placed as spectators)
    "x": 0,
    "y": 20,
    "z": 0,
    "yaw": -90.0,
    "pitch": 15.0
  },
  "readyArea": { // Ready area. Players must be inside it to be counted as participating in the game.
    "minX": -100,
    "minY": -10,
    "minZ": -100,
    "maxX": 100,
    "maxY": 10,
    "maxZ": 100
  },
  "playAreaOffset": { // Unused.
    "x": 0,
    "y": 0,
    "z": 200
  },
  "playArea": { // Play area. Should be at least as large as the paste area.
    "minX": 0,
    "minY": 20,
    "minZ": 0,
    "maxX": 100,
    "maxY": 30,
    "maxZ": 100
  },
  "sceneArea": { // Scene background area.
    "minX": 0,
    "minY": 40,
    "minZ": 0,
    "maxX": 100,
    "maxY": 50,
    "maxZ": 100
  },
  "sceneScroll": "X", // Background scroll direction. Supports X, Y, Z, NONE (no scroll).
  "resetPasteArea": { // Paste area. The map will be pasted here.
    "minX": 0,
    "minY": 20,
    "minZ": 0,
    "maxX": 100,
    "maxY": 30,
    "maxZ": 100
  },
  "resetTemplateArea": { // Template area. The map will be copied from here.
    "minX": 0,
    "minY": 0,
    "minZ": 0,
    "maxX": 100,
    "maxY": 10,
    "maxZ": 100
  },
  "roomCount": 2, // Number of rooms.
  "roomPositions": { // Room positions.
    "1": { // Room 1 position.
      "x": 0.0,
      "y": 20.0,
      "z": 50.0
    },
    "2": { // Room 2 position.
      "x": 50.0,
      "y": 25.0,
      "z": 0.0
    }
  },
  "disabledTasks": ["BREATHE"], // Disabled tasks.
  "disabledRoles": ["noellesroles:pilot"], // Disabled roles (full ID or role path both work).
  "canSwim": true, // Whether players are allowed to enter water deeper than 2 blocks. Setting to false will kill players who enter water 2 blocks deep.
  "canJump": true, // Whether players are allowed to jump.
  "haveOutsideSound": true, // Whether outdoor/indoor sound effects are enabled.
  "noReset": false, // Whether to skip resetting (the reset phase will be skipped).
  "mustCopy": false // Whether to force a copy-based reset (the enableAutoTranReset setting will be ignored).
}
```
When using, remove the comments (lines starting with `//`).

## Map Voting
Lobby map voting. `/tmm:votemap <seconds>` triggers the voting UI.

Voting config file location: `<world save>/train_vote_maps.json`

Voting config file format (comments are not supported; the comments below are only for clarification):

```json
{
  "id": "my/space_station_alpha", // Map id, matching the id of the map configuration mentioned above. Subfolders are also supported, and they do not need extra quotes.
  "displayName": "Space Station · Alpha", // Display name. Translation keys are supported.
  "mincount": 2, // Minimum player count. The map is only shown when this number is reached. Set to a negative value to ignore.
  "maxcount": 8, // Maximum player count. The map is only shown when the player count (at the moment voting starts) does not exceed this number. Set to a negative value to ignore.
  "canSelect": true, // Whether it can appear as a voting option.
  "description": "An abandoned orbital space station; the repair crew needs to restart the core reactor.", // Description. Translation keys are supported.
  "color": "0xFF3A86FF", // Color of the displayed card.
  "gameModes": ["repairmode", "murder"] // Supported game mode ID paths (e.g. the path of the game mode sre:murder is murder).
}
```


## For Development

- **[docs/创建模组.md](docs/创建模组.md)** — Creating an addon mod from scratch: environment setup, project scaffolding, dependency configuration, build commands.
- **[docs/api.md](docs/api.md)** — Complete developer API reference: role registration, event system, skill system, shop system, CCA components, HUD rendering, game modes, replay system, and more.
- **[CreateExtention.md](CreateExtention.md)** — Quick reference: registering roles, items, entities, shops, network packets, GUIs and Mixins in the existing codebase.

> **Important:** Do NOT import Wathe libraries — they will cause crashes (uninitialized state).
