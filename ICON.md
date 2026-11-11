# Hotkey Wheel icon

Saved asset: [src/main/resources/assets/hotkey_wheel/icon.png](src/main/resources/assets/hotkey_wheel/icon.png). It is the unmodified transparent PNG produced with the built-in imagegen tool on 2026-10-12, referenced by Fabric metadata and the project README.

Generation prompt:

```text
Use case: logo-brand.
Asset type: square PNG icon for the Minecraft client mod Hotkey Wheel, legible as a small Mod Menu thumbnail.
Primary request: a simplified version of a radial hotkey wheel.
Subject and composition: a single centered eight-sector circular ring with an empty round center, seven dark slate sectors and one warm gold sector slightly popped outward at the upper right. One thin gold selection line runs from the center toward that gold sector. A few very simple tiny voxel block silhouettes float inside the ring sectors, with no square tile backgrounds. Keep the shape bold, clean, and readable at 32 pixels. Generous transparent margin, complete ring visible.
Style: crisp minimal game UI emblem, flat geometric shapes with restrained pixel-art accents. Match the mod palette: dark slate #263038, lighter slate #53616c, warm gold #f1c56c. Actual transparent background.
Constraints: no text, no letters, no numbers, no watermark, no surrounding square, no keyboard, no photorealism, no scene or scenery.
```

## Picker icons

Hotkey Wheel's 39 general-purpose icons, such as the cog, check mark and map pin, are original 16×16 pixel art made for this mod in 1.1.2; no third-party icon font or set was used. `art/draw_glyphs.py` draws each one from simple shapes and writes the GUI sprites to [src/main/resources/assets/hotkey_wheel/textures/gui/sprites/icon/](src/main/resources/assets/hotkey_wheel/textures/gui/sprites/icon/). Every icon uses the same three colours: a light glyph (`#F2F2F2`), a lighter grey for details (`#B0BAC4`) and a dark outline (`#14181C`, 90% opaque) around every lit pixel, so it reads on the wheel's dark sectors and over the world. To change an icon, edit its shapes in the script and run `python3 art/draw_glyphs.py --preview`, which also writes `art/preview.png` at 8× size; do not commit the preview. Resource packs can replace the sprites like any other GUI sprite.
