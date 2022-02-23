![](https://i.imgur.com/XKVkhH1.png)

# TerraPlusMinus
[![Build Terra+-](https://github.com/Build-the-Earth-Germany/terraplusminus/actions/workflows/maven.yml/badge.svg)](https://github.com/Build-the-Earth-Germany/terraplusminus/actions/workflows/maven.yml)
[![GitHub license](https://badgen.net/github/license/Build-the-Earth-Germany/terraplusminus)](https://github.com/Build-the-Earth-Germany/terraplusminus/blob/master/LICENSE)
[![Discord](https://img.shields.io/discord/692825222373703772.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/GkSxGTYaAJ)

`❌ Don't use this for building. World expansion is experimental.`

TerraPlusMinus is a plugin for 1.18.1 which generates the real world height and outlines in the projection of [BuildTheEarth](https://en.wikipedia.org/wiki/Build_the_Earth).
Optionally it can expand the world height to 1967 ([more stable Paper Version for height expansion](https://github.com/Build-the-Earth-Germany/PaperPlusMinus/releases)). 

![](https://i.imgur.com/AAJGVF5.png)

World generation up to 1967 meters above sea level

![](https://i.imgur.com/DE4aAhk.jpg)

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
4. Start your server 
5. (Optional) To activate height expansion set **nms** in the config.yml to `true`
6. (Optional) Restart your server

# 

> The NMSInjection Part is from the plugin https://github.com/Hex27/TerraformGenerator and can be found there. Credits go to [@Hex27](https://github.com/Hex27).
