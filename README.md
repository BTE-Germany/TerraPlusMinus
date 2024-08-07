![](https://i.imgur.com/XKVkhH1.png)

# TerraPlusMinus
[![Build Terra+-](https://github.com/Build-the-Earth-Germany/terraplusminus/actions/workflows/maven.yml/badge.svg)](https://github.com/Build-the-Earth-Germany/terraplusminus/actions/workflows/maven.yml)
[![GitHub license](https://badgen.net/github/license/Build-the-Earth-Germany/terraplusminus)](https://github.com/Build-the-Earth-Germany/terraplusminus/blob/master/LICENSE)
![Latest Release](https://img.shields.io/github/v/release/BTE-Germany/TerraPlusMinus)
[![Discord](https://img.shields.io/discord/692825222373703772.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/GkSxGTYaAJ)
![https://github.com/buildtheearth](https://go.buildtheearth.net/community-shield)

* Supported Minecraft-Versions: **Paper 1.20.6 - 1.21**

* *Legacy version (for older Minecraft-Versions Spigot 1.18 - 1.20.4): [Legacy-Version 1.3.3](https://github.com/BTE-Germany/TerraPlusMinus/releases/tag/v1.3.3)*



<!-- TABLE OF CONTENTS -->
# Table of Contents
<details open="open">
  <summary>Table of Contents</summary>
  <ol>
    <li><a href="#features">Features</a></li>
    <li><a href="#images">Images</a></li>
    <li><a href="#commands-and-permissions">Commands and Permissions</a></li>
    <li><a href="#installation">Installation</a></li>
    <li><a href="#config">Config</a></li>
    <li><a href="#dependencies">Dependencies</a></li>
  </ol>
</details>


# Features

TerraPlusMinus is a plugin which generates the real world terrain and outlines in the projection of [BuildTheEarth](https://en.wikipedia.org/wiki/Build_the_Earth).

- customize your surface blocks
- offset x,y and/or z world generation and commands
- option to disable different biomes
- option to disable tree generation
- lidar is supported in the same way as in [Terra++](https://github.com/BuildTheEarth/terraplusplus)
- automatic datapack installation
- set coordinate bounds to prevent players from teleporting to areas, which are being worked on by other build teams
- custom tree generation
- link your servers to generate different height sections ([preview](https://youtu.be/oqROhmaSxgY?si=Hl8zi3lAVEWfAGHy))

# Images

World generation up to 2032 meters above sea level:

![](https://i.imgur.com/DE4aAhk.jpg)

Biome generation (including sand in deserts):

![](https://cdn.discordapp.com/attachments/1023664488735576165/1096055054248718447/2023-04-13_14.48.58.png?width=1329&height=702)

Extended Render Distance with [Distant Horizons](https://www.curseforge.com/minecraft/mc-mods/distant-horizons):

![](https://media.discordapp.net/attachments/795314415816933427/950796277971554324/2022-03-08_17.42.16.png?width=1329&height=702)

Custom Tree Generation:

![](https://media.discordapp.net/attachments/1023664488735576165/1096052591185625139/2023-04-13_14.37.00.png?width=1329&height=702)
![](https://media.discordapp.net/attachments/1023664488735576165/1096052591877701732/2023-04-13_14.38.31.png?width=1329&height=702)
![](https://media.discordapp.net/attachments/1023664488735576165/1096052592997564466/2023-04-13_14.41.07.png?width=1330&height=702)

# Commands and Permissions

`/tpll <latitudes> <longitudes> [height]` - Permission node: `t+-.tpll`

`/where` - Permission node: `t+-.where`

`/offset` - Permission node: `t+-.offset`

Permission node: `t+-.admin` - Bypasses the coordinate bounds of tpll

# Installation

1. Download the latest build
   of [Terra+- here](https://github.com/Build-the-Earth-Germany/terraplusminus/actions/workflows/maven.yml) and add it
   to your plugin folder
2. Add these lines at the end of your `bukkit.yml` and replace "world" with the name of the server's main world name

```
worlds:
  world:
    generator: Terraplusminus
```

3. Add `--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED` to your jvm arguments in the start file. It should look
   like this:

Windows `start.bat` with:

```
@ECHO OFF
java -jar --add-exports=java.desktop/sun.awt.image=ALL-UNNAMED server-executable-name.jar
pause
```

Or Linux/Mac `start.sh` with:

```
#!/bin/sh
cd "$(dirname "$0")"
exec java -jar --add-exports=java.desktop/sun.awt.image=ALL-UNNAMED server-executable-name.jar
```

4. Start your server

*Now your world is from -64 to 320, if you need more height, go to step 6.*

5. (Optional) If you only plan to use Minecraft Vanilla heights from -64 to 320, but e.g. your city is on height 500 you can set `y: -300` in the `terrain_offset` category in the config.yml to offset your section which fits into the world and restart your server.

6. (Optional) Use a datapack to expand your world height. You can set `height_datapack` in config.yml to`true` and restart your server. It will automaticly copy a datapack (with maximum world height possibly with a datapack) into your world folder.

# Config

Standard-Config:
```
# The prefix is written in front of every message that is sent to the chat by the plugin.
prefix: '§2§lT+- §8» '

# If disabled, the plugin will log every fetched data to the console
reduced_console_messages: true

# If this option is enabled, the plugin will copy a datapack with the name 'world-height-datapack.zip' to the world directory, which expands the world to the maximum possibly with a datapack 2032.
height_datapack: false

# If enabled, it will show the height of the player in the actionbar.
height_in_actionbar: false



# Tpll ------------------------------------------------
# Set bounds so that players can only tpll within these limits. They will get a message that the area is being worked on by another build team.
# The option is turned off when all values are 0.0
# -----------------------------------------------------
min_latitude: 0.0               # Example: 46.94694079137405
max_latitude: 0.0               #          55.337721930180116
min_longitude: 0.0              #          1.9049932813372725
max_longitude: 0.0              #          15.665992332846406

# Passthrough tpll to other bukkit plugins. It will not passthrough when it's empty. Type in the name of your plugin. E.g. Your plugin name is vanillatpll you set passthrough_tpll: 'vanillatpll'
passthrough_tpll: ''



# Generation -------------------------------------------
# Offset your section which fits into the world.
terrain_offset:
  x: 0
  y: 0
  z: 0

# Linked worlds ---------------------------------------
# If the height limit in this world/server is not enough, other worlds/servers can be linked to generate higher or lower sections
linked_worlds:
  enabled: false
  method: 'SERVER'                         # 'SERVER' or 'MULTIVERSE'
  # if method = MULTIVERSE -> world_name, y-offset
  worlds:
    - name: another_world/server          # e.g. this world/server has a datapack to extend height to 2032. it covers the height section (-2032) - (-1) m a.s.l. it has a y-offset of -2032.
      offset: 2032
    - name: current_world/server                 # do not change! e.g. this world/server has a datapack to extend height to 2032. it covers the height section 0 - 2032 m a.s.l.
      offset: 0
    - name: another_world/server                 # e.g. this world/server has a datapack to extend height to 2032. it covers the height section 2033 - 4064 m a.s.l. it has a y-offset of 2032
      offset: -2032


# If disabled, tree generation is turned off.
generate_trees: true

# The biomes will be generated with https://en.wikipedia.org/wiki/K%C3%B6ppen_climate_classification.
# If turned off, everything will be plains biome.
different_biomes: true

# Customize the material, the blocks will be generated with.
surface_material: GRASS_BLOCK
building_outlines_material: BRICKS
road_material: GRAY_CONCRETE_POWDER
path_material: MOSS_BLOCK
# -----------------------------------------------------

# NOTE: Do not change
config_version: 1.4
```

# Dependencies

TerraMinusMinus - [Terra--](https://github.com/SmylerMC/terraminusminus) developed by [@SmylerMC](https://github.com/SmylerMC)

