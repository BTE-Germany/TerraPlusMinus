![](https://i.imgur.com/XKVkhH1.png)

# TerraPlusMinus
[![Build Terra+-](https://github.com/Build-the-Earth-Germany/terraplusminus/actions/workflows/maven.yml/badge.svg)](https://github.com/Build-the-Earth-Germany/terraplusminus/actions/workflows/maven.yml)
[![GitHub license](https://badgen.net/github/license/Build-the-Earth-Germany/terraplusminus)](https://github.com/Build-the-Earth-Germany/terraplusminus/blob/master/LICENSE)
[![Discord](https://img.shields.io/discord/692825222373703772.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/GkSxGTYaAJ)
![https://github.com/buildtheearth](https://go.buildtheearth.net/community-shield)

Tested Minecraft-Versions: **Paper 1.18 - 1.19**

Recommended Minecraft Version for BuildTheEarth: **Paper 1.18.2** 

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

- choose your own blocks for outlines
- choose the height section you want to fit between -64 and 320 (or more)
- choose if you want to use different biomes or just plains
- choose if you want to generate trees
- lidar is supported in the same way as in [Terra++](https://github.com/BuildTheEarth/terraplusplus)
- automatic datapack installation
- set coordinate bounds to prevent players from teleporting to areas, which are being worked on by other build teams

# Images

![](https://media.discordapp.net/attachments/795327112767602738/950790467908431982/2022-03-08_17.19.31.png?width=1329&height=702)

World generation up to 1960 meters above sea level:

![](https://i.imgur.com/DE4aAhk.jpg)

Biome generation (including sand in deserts):

![](https://images-ext-2.discordapp.net/external/7sN83KI6YZM39ovU1RS5XUScVhjOIqqUiiftCLfO3Kc/https/i.imgur.com/OxNGJ8w.jpg?width=1329&height=702)

Customization:

![](https://cdn.discordapp.com/attachments/784314470712344626/981183662269808650/2022-05-31_15.13.05.png)

Extended Render Distance with [Distant Horizons](https://www.curseforge.com/minecraft/mc-mods/distant-horizons):

![](https://media.discordapp.net/attachments/795314415816933427/950796277971554324/2022-03-08_17.42.16.png?width=1329&height=702)

# Commands and Permissions

`/tpll <latitudes> <longitudes>` - Permission node: `t+-.tpll`

`/where` - Permission node: `t+-.where`

# Installation 

1. Download the latest build of [Terra+- here](https://github.com/Build-the-Earth-Germany/terraplusminus/actions/workflows/maven.yml) and add it to your plugin folder
2. Add these lines at the end of your `bukkit.yml` and replace "world" with the name of the server's main world name

```
worlds:
  world:
    generator: TerraPlusMinus
```

3. Add `--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED` to your jvm arguments in the start file. It should look like this:

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
5. Download [osm.json5](https://github.com/BuildTheEarth/terraplusplus/blob/35615cfe037b933a2b0e24271ba4759d5f94f5eb/src/main/resources/net/buildtheearth/terraplusplus/dataset/osm/osm.json5) and put it in `.\terraplusplus\config\`. 
6. Restart your server

7. If you only plan to use Minecraft Vanilla heights from -64 to 320, but e.g. your city is on height 500 you can set `moveTerrain: -300` in the config.yml to offset your section which fits into the world.

*Now your world is from -64 to 320, if you need more height, go to step 8.*

8. (Optional) Use a datapack to expand your world height. You can set `height-datapack` in config.yml to`true` and restart your server. It will automaticly copy a datapack (with maximum world height possibly with a datapack) into your world folder.

# Config

Standard-Config:
```
# The prefix is written in front of every message that is sent to the chat by the plugin.
prefix: '§2§lT+- §8» '

# If this option is enabled, the plugin will copy a datapack with the name 'world-height-datapack.zip' to the world directory, which expands the world to the maximum possibly with a datapack 2016 (real 1960).
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

# Passthrough tpll to other bukkit plugins.
passthrough_tpll: false



# Generation -------------------------------------------
# Offset your section which fits into the world.
terrain_offset: 0

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
config_version: 1.0
```
  
# Dependencies

TerraMinusMinus - [Terra--](https://github.com/SmylerMC/terraminusminus) developed by [@SmylerMC](https://github.com/SmylerMC)

