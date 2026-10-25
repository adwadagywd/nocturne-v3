# Nocturne - Fabric 1.21.4 utility client

Open the ClickGUI with RIGHT SHIFT (rebind in Controls -> Nocturne).
Left click = toggle module / change setting, right click = expand settings / collapse panel / previous mode.
Drag the panel headers to move them. Config is saved to .minecraft/config/nocturne.json.

Build:  ./gradlew build   ->  build/libs/nocturne-1.1.0.jar (delete older nocturne-*.jar from mods/ first)  (drop in mods/ together with Fabric API)
No gradle wrapper included: run `gradle wrapper --gradle-version 8.12` once, or copy gradlew + gradle/ from the
official Fabric template (https://fabricmc.net/develop/template/). Needs JDK 21.

Modules: Hitboxes, AimAssist, TriggerBot, KillAura, FakePlayer, Tracers, ESP, Nametags, XRay, Fullbright, AutoSprint, Freecam, TargetHud, HUD.

KillAura rotation modes: Silent (server-side only, camera never moves; Turn speed / Jitter / GCD fix), Smooth, Snap, None.
Raytrace = only attack when the rotation actually sent to the server hits the target's hitbox.
Theme colours: src/main/java/dev/nocturne/gui/Theme.java  (+ Accent slider in the HUD module).
Add a module: extend Module, add(...) settings, register it in ModuleManager.init().

FakePlayer: client-side dummy with totems (pops, regen, knockback, death/respawn). Hits are simulated locally, nothing goes to the server.
Freecam: camera is moved inside Camera.update and the mouse is redirected to it (no lag, no stuck controls after respawn).
