![](https://i.imgur.com/XKVkhH1.png)

# TerraPlusMinus
[![Build Terra+-](https://github.com/Build-the-Earth-Germany/terraplusminus/actions/workflows/maven.yml/badge.svg)](https://github.com/Build-the-Earth-Germany/terraplusminus/actions/workflows/maven.yml)
[![GitHub license](https://badgen.net/github/license/Build-the-Earth-Germany/terraplusminus)](https://github.com/Build-the-Earth-Germany/terraplusminus/blob/master/LICENSE)
[![Discord](https://img.shields.io/discord/692825222373703772.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/GkSxGTYaAJ)
![https://github.com/buildtheearth](https://go.buildtheearth.net/community-shield)

Tested Minecraft-Versions: Paper 1.18.1

<!-- TABLE OF CONTENTS -->
# Table of Contents
<details open="open">
  <summary>Table of Contents</summary>
  <ol>
    <li><a href="#features">Features</a></li>
    <li><a href="#images">Images</a></li>
    <li><a href="#commands">Commands</a></li>
    <li><a href="#installation">Installation</a></li>
    <li><a href="#config">Config</a></li>
    <li><a href="#dependencies">Dependencies</a></li>
  </ol>
</details>


# Features

TerraPlusMinus is a plugin for 1.18.1 which generates the real world terrain and outlines in the projection of [BuildTheEarth](https://en.wikipedia.org/wiki/Build_the_Earth).
Optionally it can expand the world height to 1967 ([more stable Paper Version for height expansion](https://github.com/Build-the-Earth-Germany/PaperPlusMinus/releases)). 

- choose your own blocks for outlines
- choose the height section you want to fit between -64 and 320 (or more)
- choose if you want to use different biomes or just plains
- choose if you want to generate trees
- lidar is supported in the same way as in [Terra++](https://github.com/BuildTheEarth/terraplusplus)
- choose if you want height expansion (experimental)

# Images

![](https://media.discordapp.net/attachments/795327112767602738/950790467908431982/2022-03-08_17.19.31.png?width=1329&height=702)

World generation up to 1967 meters above sea level:

![](https://i.imgur.com/DE4aAhk.jpg)

Biome generation (including sand in deserts):

![](https://images-ext-2.discordapp.net/external/7sN83KI6YZM39ovU1RS5XUScVhjOIqqUiiftCLfO3Kc/https/i.imgur.com/OxNGJ8w.jpg?width=1329&height=702)

Customization:

![](https://cdn.discordapp.com/attachments/784314470712344626/981183662269808650/2022-05-31_15.13.05.png)

Extended Render Distance with [Distant Horizons](https://www.curseforge.com/minecraft/mc-mods/distant-horizons):

![](https://media.discordapp.net/attachments/795314415816933427/950796277971554324/2022-03-08_17.42.16.png?width=1329&height=702)

# Commands

/tpll <latitudes> <longitudes> - `terra+-.tpll`
  
# Installation 

1. Download the latest build of [Terra+- here](https://github.com/Build-the-Earth-Germany/terraplusminus/actions/workflows/maven.yml) and add it to your plugin folder
2. Add these lines at the end of your bukkit.yml and replace "world" with the name of the server's main world name

```
worlds:
  world:
    generator: TerraPlusMinus
```

3. Add `--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED` to your jvm arguments in the start.bat
4. Start your server 
5. Download [osm.json5](https://github.com/BuildTheEarth/terraplusplus/blob/35615cfe037b933a2b0e24271ba4759d5f94f5eb/src/main/resources/net/buildtheearth/terraplusplus/dataset/osm/osm.json5) and put it in `.\terraplusplus\config\`. 
6. Restart your server

7. If you only plan to use Minecraft Vanilla heights from -64 to 320, but e.g. your city is on height 500 you can set `moveTerrain: -300` in the config.yml.

`❌ Don't use height expansion for building. World expansion is experimental.`
  
8. (Optional) To activate height expansion set **nms** in the config.yml to `true` and restart your server

# Config

Standard-Config:
```
prefix: '§2§lT+- §8» '
nms: false
min-height: -64
max-height: 2032
useBiomes: true
generateTrees: true
moveTerrain: 0
surface: GRASS_BLOCK
houseOutlines: BRICKS
streets: GRAY_CONCRETE_POWDER
paths: MOSS_BLOCK
```
  
# Dependencies

TerraMinusMinus - [Terra--](https://github.com/SmylerMC/terraminusminus) developed by [@SmylerMC](https://github.com/SmylerMC)

# 

> The NMSInjection Part is from the plugin https://github.com/Hex27/TerraformGenerator and can be found there. Credits go to [@Hex27](https://github.com/Hex27).
