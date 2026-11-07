# Shadow Cosmetics — Online Cape Search, Custom Upload & 3D Preview

Minecraft 1.21.11 Fabric client-side source project (Java 21).

## Added in this update
- Online cape search against the public mc-api cape gallery and Wikimedia Commons PNG files.
- Search result paging (previous/next) and selected-result download.
- Only cape-atlas PNGs with dimensions 64x32 or 64x64 are accepted for applying.
- Downloaded/imported PNGs are equipped as a real dynamic Minecraft texture on the existing 3D animated cape model.
- Existing cape sway/flutter animation is retained for custom textures.
- Android-friendly in-game file browser: Browse phone → open folders → tap a PNG. It starts at `/storage/emulated/0/Download` when accessible, with `/sdcard/Download` and the launcher home folder as fallbacks.
- Custom cape selection is saved in `config/shadowcosmetics/active-cape.txt` and reloaded on next launch.
- The existing original 3D crown, horns, halo and wings remain available.

## How to use
1. Open the mod menu with Right Shift (or the configured keybind).
2. Search for a cape name, e.g. `Müge cape`.
3. Use Previous/Next Result and Download selected.
4. Or use Browse phone and choose a local cape PNG.
5. The selected cape is equipped automatically. Use F5 to see the 3D cape on your player.
6. Toggle Custom PNG Cape off to return to the built-in animated cape designs.

## Texture format
- PNG only, maximum 8 MB.
- Cape atlas dimensions must be exactly 64x32 or 64x64 pixels.
- Arbitrary pictures, JPEG, WebP and full 3D model files are not cape atlases and are rejected/not applied.

## Search sources and rights
- The online search uses the public mc-api cape gallery and Wikimedia Commons; it is not a Google-wide crawler and may not contain every creator cape.
- Results depend on those services being online and may be rate-limited.
- Check the original creator's terms and the source's licence before using or redistributing a texture. The mod does not bypass access controls or download private assets.

## Android file access
The in-game browser reads paths exposed to the Minecraft process. On Android, access depends on the launcher and Android storage permissions. If the Download folder is not visible, grant the launcher file/media permission or use the path import field where supported.

## Visibility
Cosmetics are currently local-only. They render on your own player; this project does not send cosmetic data to Minecraft servers or automatically show custom capes to other players.

## Build
Requires JDK 21 and Gradle. Run `gradle build` in this folder. Output: `build/libs/`.

## Test status
Source update only. The environment used to prepare this archive did not have Gradle installed, so the project has not been compiled or runtime-tested here. Verify with a Gradle build on a network-enabled machine before installing in Minecraft 1.21.11.
