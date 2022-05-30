![](https://i.imgur.com/XKVkhH1.png)

# TerraPlusMinus
[![Build Terra+-](https://github.com/Build-the-Earth-Germany/terraplusminus/actions/workflows/maven.yml/badge.svg)](https://github.com/Build-the-Earth-Germany/terraplusminus/actions/workflows/maven.yml)
[![GitHub license](https://badgen.net/github/license/Build-the-Earth-Germany/terraplusminus)](https://github.com/Build-the-Earth-Germany/terraplusminus/blob/master/LICENSE)
[![Discord](https://img.shields.io/discord/692825222373703772.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/GkSxGTYaAJ)
![https://github.com/buildtheearth](https://go.buildtheearth.net/community-shield)

TerraPlusMinus is a plugin for 1.18.1 which generates the real world height and outlines in the projection of [BuildTheEarth](https://en.wikipedia.org/wiki/Build_the_Earth).
Optionally it can expand the world height to 1967 ([more stable Paper Version for height expansion](https://github.com/Build-the-Earth-Germany/PaperPlusMinus/releases)). 

![](https://media.discordapp.net/attachments/795327112767602738/950790467908431982/2022-03-08_17.19.31.png?width=1329&height=702)

World generation up to 1967 meters above sea level:

![](https://i.imgur.com/DE4aAhk.jpg)

Biome generation (including sand in deserts):

![](https://images-ext-2.discordapp.net/external/7sN83KI6YZM39ovU1RS5XUScVhjOIqqUiiftCLfO3Kc/https/i.imgur.com/OxNGJ8w.jpg?width=1329&height=702)

Extended Render Distance with [Distant Horizons](https://www.curseforge.com/minecraft/mc-mods/distant-horizons):

![](https://media.discordapp.net/attachments/795314415816933427/950796277971554324/2022-03-08_17.42.16.png?width=1329&height=702)

# Dependencies

TerraMinusMinus - [Terra--](https://github.com/SmylerMC/terraminusminus) developed by [@SmylerMC](https://github.com/SmylerMC)

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
4. Download [osm.json5](https://github.com/BuildTheEarth/terraplusplus/blob/35615cfe037b933a2b0e24271ba4759d5f94f5eb/src/main/resources/net/buildtheearth/terraplusplus/dataset/osm/osm.json5) and put it in `.\terraplusplus\config\`. 
5. Start your server 

`❌ Don't use height expansion for building. World expansion is experimental.`
  
6. (Optional) To activate height expansion set **nms** in the config.yml to `true` and restart your server

# Config

Standard-Config:
```
prefix: '§2§lT+- §8» '
nms: false
min-height: -64
max-height: 2032
useBiomes: true
generateTrees: true
```
  
# 

> The NMSInjection Part is from the plugin https://github.com/Hex27/TerraformGenerator and can be found there. Credits go to [@Hex27](https://github.com/Hex27).
