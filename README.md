# DDNetCraft Solo — Minecraft 1.21.1 Fabric

A **client-side, singleplayer** 2D DDRace-inspired mode inside Minecraft.
Uses Fabric API, a narrow client movement mixin, a JSON map, hook, Pistol,
Grenade, Laser, freeze/kill/finish zones and a 2D HUD minimap.

* Press **G** to toggle mode, **Q** to hook, **J** to fire, **1–3** to select a weapon, **R** to restart.
* Commands: `/ddnet on`, `/ddnet off`, `/ddnet load training`, `/ddnet restart`, `/ddnet reloadconfig`.
* Works in local singleplayer worlds only. Uses no modded packets or server-side networking.
* Physics and map are client-side virtualizations; the integrated vanilla server
  can still correct movement against actual world collision.
* Full documentation in [README_RU.md](README_RU.md).

Download the compiled JAR from **Actions → Build Fabric mod → Artifacts →
DDNetCraft-Solo-1.21.1** after a successful run.
