# ![](https://i.imgur.com/XKVkhH1.png)

[![Build Terra+-](https://github.com/Build-the-Earth-Germany/terraplusminus/actions/workflows/maven.yml/badge.svg)](https://github.com/Build-the-Earth-Germany/terraplusminus/actions/workflows/maven.yml)
[![Quality Gate Status](https://sonarqube.app.k8s.bteger.dev/api/project_badges/measure?project=BTE-Germany_TerraPlusMinus_776220d7-94a3-47ab-a1af-0f299952ab48&metric=alert_status&token=sqb_697251798da319e35b695e4fd1da188ff4e3336b)](https://sonarqube.app.k8s.bteger.dev/dashboard?id=BTE-Germany_TerraPlusMinus_776220d7-94a3-47ab-a1af-0f299952ab48)
[![GitHub license](https://badgen.net/github/license/Build-the-Earth-Germany/terraplusminus)](https://github.com/Build-the-Earth-Germany/terraplusminus/blob/master/LICENSE)
![Latest Release](https://img.shields.io/github/v/release/BTE-Germany/TerraPlusMinus)
[![Discord](https://img.shields.io/discord/692825222373703772.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/GkSxGTYaAJ)
![https://github.com/buildtheearth](https://go.buildtheearth.net/community-shield)

* Supported Minecraft-Versions are mentioned on the Releases
  itself: [Releases](https://github.com/BTE-Germany/TerraPlusMinus/releases)

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
![](https://lh3.googleusercontent.com/d/1F2T5Stl91UPopzNSVaJ36DPa51Is-47G=w1000)

Extended Render Distance with [Distant Horizons](https://modrinth.com/mod/distanthorizons):
![](https://lh3.googleusercontent.com/d/1mOFhplVCM_T7Qie66GjHyXuBiSOR_EJk=w1000)

Custom Tree Generation:
![](https://lh3.googleusercontent.com/d/1usu42tN0bnZFAK8UN-fBlONMRVVRqzYg=w1000)
![](https://lh3.googleusercontent.com/d/1hjEWDo73wcRw04pEgQMeZzWDzfxyKn4G=w1000)
![](https://lh3.googleusercontent.com/d/1il5NFR--NKFjcSxDAV9iD_9N7YMOJoeb=w1000)

# Commands and Permissions

`/tpll <latitudes> <longitudes> [height]` - Permission node: `t+-.tpll` + `t+-.tpll.otherWorld` when teleporting from
another world

`/tpll -p <player>` <latitudes> <longitudes> [height] - Permission node:`t+-.forcetpll`

`/where` - Permission node: `t+-.where`

`/offset` - Permission node: `t+-.offset`

`/distortion` - Permission node: `t+-.distortion`

Permission node: `t+-.admin` - Bypasses the coordinate bounds of tpll

Permission node: `t+-.tpll.ungenerated-chunks` - Allows `/tpll` into chunks which are not generated yet. It is inherited
from `t+-.tpll`, so existing setups keep working - deny it explicitly (e.g. for visitors) to prevent players from
generating new chunks. `t+-.admin` and `t+-.forcetpll` bypass this restriction

Permission node: `t+-.autoteleport` - Enables automatic teleportation between linked worlds

Permission node: `t+-.notify.update` - Enables update notifications

# Installation

1. Download the latest release
   of [Terra+- here](https://github.com/BTE-Germany/TerraPlusMinus/releases/latest) and add it
   to your plugin folder
2. Add these lines at the end of your `bukkit.yml` and replace "world" with the name of the server's main world name

```
worlds:
  world:
    generator: Terraplusminus
```

3. Start your server

*Now your world is from -64 to 320, if you need more height, go to step 5.*

4. (Optional) If you only plan to use Minecraft Vanilla heights from -64 to 320, but e.g. your city is on height 500 you can set `y: -300` in the `terrain_offset` category in the config.yml to offset your section which fits into the world and restart your server.

5. (Optional) Use a datapack to expand your world height. You can set `height_datapack` in config.yml to`true` and restart your server. It will automaticly copy a datapack (with maximum world height possibly with a datapack) into your world folder.

# Config

For our config.yml please see [here](./src/main/resources/config.yml)

# Dependencies

TerraMinusMinus - [Terra--](https://github.com/SmylerMC/terraminusminus) developed by [@SmylerMC](https://github.com/SmylerMC)

