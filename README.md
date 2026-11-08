# Shadow Cosmetics

Client-side cosmetics for **Minecraft 1.21.11** (Fabric, Java 21): animated 3D capes, hats, wings and a halo, online cape search, PNG import and a touch-friendly menu that also works on Android launchers.

*Türkçe özet:* Minecraft 1.21.11 için istemci taraflı kozmetik modu. Animasyonlu 3D pelerinler, şapkalar, kanatlar, hale; çevrimiçi pelerin arama, PNG içe aktarma ve dokunmatik dostu menü. Arayüz Türkçe ve İngilizce.

## Features
- **Cosmetics:** 6 animated cape styles, 5 hats, wings and halo. A master switch, per-item toggles, an animation switch and an accent colour that recolours every cosmetic.
- **Online cape search:** public cape gallery plus Wikimedia Commons, with *Popular* capes, previous/next browsing and one-tap *Download & equip*.
- **Custom PNG capes:** any 64x32 or 64x64 cape atlas is rendered on the animated 3D cape model.
- **My capes:** every downloaded or imported cape is kept in a library. Equip it again with one tap, delete it with a confirm tap.
- **Device file browser:** hidden files are skipped, PNG sizes are shown and invalid sizes are greyed out.
- **Everything is remembered:** all settings are saved to `config/shadowcosmetics/settings.properties` and restored on launch (older `active-cape.txt` files are migrated).
- **Scrolling UI:** mouse wheel or drag. A press that does not move is a tap, so dragging over a button never presses it by accident.
- **Languages:** English and Türkçe (`en_us`, `tr_tr`). Error messages are translated too.

## How to use
1. Press **Right Shift** (rebindable) to open the menu.
2. Search for a cape or press *Popular*, pick a result with *Previous / Next* and press *Download & equip*.
3. Or use *Browse device folder* / the path field to import a PNG.
4. Press **F5** to see the cape on your player.
5. Toggle *PNG cape* off, or *Use built-in cape*, to go back to the animated built-in capes.

## Texture format
- PNG only, maximum 8 MB.
- Cape atlas must be exactly **64x32** or **64x64** pixels. Other images, JPEG, WebP and 3D model files are rejected.

## Search sources and rights
- Search uses the public mc-api cape gallery and Wikimedia Commons. It is not a web-wide crawler and may not contain every creator's cape.
- Results depend on those services being online and may be rate limited.
- Check the creator's terms and the source's licence before using or redistributing a texture. The mod does not bypass access controls or download private assets.

## Android file access
The in-game browser reads folders exposed to the Minecraft process. It starts in `/storage/emulated/0/Download` when accessible. If nothing is visible, grant the launcher file/media permission or paste the full path into the import field.

## Visibility
Cosmetics are **local only**: they render on your own player. The mod sends no cosmetic data to servers and does not show your cape to other players.

## Build
Requires JDK 21.

```
./gradlew build
```

The mod JAR is written to `build/libs/`. Unit tests (PNG header parsing, file naming, colour maths, settings storage) run with `./gradlew test`.
GitHub Actions builds every push and pull request and uploads `ShadowCosmetics-1.21.11.jar` as an artifact.

## Project layout
| File | Purpose |
| --- | --- |
| `CosmeticsScreen`, `CosmeticFilePickerScreen`, `ScrollableScreen` | Menu, file browser and the shared touch-friendly frame |
| `CosmeticState`, `CosmeticSettings` | Options and their persistence |
| `CosmeticLibrary`, `CapeFormat` | Search, download, import, library and PNG validation |
| `CosmeticTextureManager` | Loads the selected PNG as a dynamic texture and cape model |
| `ShadowCosmeticFeatureRenderer`, `Colors` | Rendering of capes, hats, wings and halo |
| `Lang`, `CosmeticException` | Translated messages and errors |

## License
MIT, see [LICENSE](LICENSE).
