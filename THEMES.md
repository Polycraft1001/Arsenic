# Arsenic themes (AI guide)

This file is written so you can hand it to an AI assistant along with a short description ("a dark teal theme inspired by lagoons") and get back a theme that works.

A theme is an **addon**: a single `.java` file in `~/Arsenic/addons/` that registers a `Theme` when it loads. You don't need to rebuild the client. For the addon system itself, see [ADDONS.md](ADDONS.md).

---

## Quick start (for humans)

1. Give this file to your AI and describe the theme you want (colours, mood, name).
2. Save the answer as `~/Arsenic/addons/<ClassName>.java`. The file name must match the class name. `.addon folder` opens the folder.
3. Run `.addon reload` in chat, or restart the game.
4. Open the ClickGUI, go to the **GUI** tab, then **Accent colour**, and click the new swatch. Your choice is saved.

Prompt you can use:

> Using THEMES.md, create an Arsenic theme called **Lagoon**: deep teal and cyan, calm, underwater feel. Give me the complete Java file.

---

## Rules for the AI (follow these unless the user asks otherwise)

1. Output **one complete Java file** and say which file name to save it under. Nothing else is needed.
2. **No `package` line.** The public class name is the file name: letters and digits only, starting with a letter, ending in `Theme` (e.g. `LagoonTheme`).
3. The class `extends Module` and has `@ModuleInfo(name = "<ClassName>", description = "...", category = ModuleCategory.CLIENT)`. The module name has to be unique among all modules.
4. Build the theme **in the constructor** and register it with the `register(...)` helper, copied **unchanged** from the template below.
5. Theme name: short (1–12 characters) and unique. It must **not** be one of the built-in names, compared case-insensitively: `Ocean`, `Classic`, `Void`, `Specter`, `Ember`, `Jade`, `Obsidian`, `Sakura`, `Toxin`, `Midnight`.
6. Write colours as `0xAARRGGBB` int literals or `new Color(r, g, b, a).getRGB()`. Opaque colours **must start with `0xFF`**: `0x3B82F6` has alpha 00 and is invisible.
7. `darkerColor` is always `new Color(MAIN).darker().getRGB()`, as in the built-in themes.
8. Always set the full dark surface palette from the template, tinted toward the theme's hue. Setting only the main colour isn't enough.
9. Choose a background shader (`setBgShader`) that fits the mood (see the table below).
10. Java 8 only: no `var`, records, text blocks, `List.of` or switch expressions.
11. Don't call `setLogoPath`, and don't add event listeners, properties, commands or mixins unless the user asks for them.

---

## How theming works (platform facts)

- **`mainColor` is the most important value.** The modern UI (ClickGUI, HUD, menus) generates a full Material 3 colour scheme from this one seed colour, light mode included. A saturated mid-tone works best. Grey, near-black or near-white seeds give a washed-out, almost monochrome scheme. `Obsidian` does this on purpose.
- **Gradient colour** (7-argument constructor with `true`): a second colour, used for the theme's swatch and for gradient accents. Without it, the gradient colour falls back to `mainColor`.
- **The other fields** colour the older components that don't use the Material 3 renderer yet (the configs screen, some lists and buttons). Their names describe what they colour.
- **Automatic contrast:** `textPrimary`, `configsCardBorder` and `configsHoverBorder` are corrected against the surfaces when they're read, so text and borders stay readable even if the values you pick are slightly off.
- `textSecondary` and `textMuted` are **currently ignored**: the client draws all legacy text with `textPrimary`.
- **Background shader:** used behind the menu when the GUI tab's **Background** option follows the theme. The user can override it there.
- **Load order:** addons are compiled before the theme manager and the configs are loaded. So a theme registered in the constructor is already in the list when the saved `currentTheme` is restored, and the user's choice is kept across restarts.
- **Swatch row:** all themes are drawn as swatches on one line, and that line **doesn't wrap**. Keep the number of custom themes small (2–3).
- **Disabled addons are still compiled and loaded,** so the theme shows up even if the addon is switched off in the Addon Manager. To remove a theme, delete its file and restart.
- **`.addon reload`** creates a new instance of the addon. The `register` helper replaces the previous copy of the theme and keeps it selected if it was selected. If you **rename** a theme, the old name stays in the list until the next restart.

---

## Template (complete file)

Save as `~/Arsenic/addons/LagoonTheme.java`.

```java
import arsenic.gui.click.GuiStyle;
import arsenic.gui.themes.Theme;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;

import java.awt.Color;
import java.util.Collection;
import java.util.List;

// An Arsenic theme. Save as ~/Arsenic/addons/LagoonTheme.java, run ".addon reload",
// then pick the new swatch in ClickGUI > GUI > Accent colour.
@ModuleInfo(name = "LagoonTheme", description = "Adds the Lagoon theme (teal)", category = ModuleCategory.CLIENT)
public class LagoonTheme extends Module {

    private static final int MAIN = 0xFF14B8A6;      // seed of the whole Material 3 scheme
    private static final int GRADIENT = 0xFF0E7490;  // second colour of the swatch / gradients

    private final Theme theme;

    public LagoonTheme() {
        theme = new Theme("Lagoon", MAIN, new Color(MAIN).darker().getRGB(),
                0xFFEFFFFC,   // white: near-white tinted toward the hue
                0xFF04120F,   // black: near-black tinted toward the hue
                true, GRADIENT);

        // Dark surfaces, all tinted toward the hue (see "Colour recipe")
        theme.setClickGuiBackground(0xDD04100E);
        theme.setClickGuiSeparator(new Color(20, 184, 166, 38).getRGB());
        theme.setConfigsBackground(0xFF030D0B);
        theme.setConfigsCard(0xFF0A1C19);
        theme.setConfigsCardBorder(0xFF173A34);
        theme.setConfigsHoverBackground(0xFF0E2420);
        theme.setConfigsHoverBorder(0xFF235248);
        theme.setSeparator(0xFF173A34);
        theme.setModuleBackground(new Color(10, 28, 25, 160).getRGB());
        theme.setEnumBackground(new Color(5, 14, 12, 255).getRGB());
        theme.setFolderBackground(new Color(5, 14, 12, 180).getRGB());
        theme.setButtonBackground(new Color(12, 30, 27, 234).getRGB());
        theme.setTextPrimary(0xFFEFFFFC);

        theme.setBgShader(GuiStyle.BgShader.CAUSTICS);

        register(theme);
    }

    // Do not change: adds the theme, or swaps in the new copy after ".addon reload".
    private static void register(Theme theme) {
        ThemeManager manager = Arsenic.getArsenic().getThemeManager();
        Collection<Theme> themes = manager.getContents();
        Theme old = manager.getContentByJsonKey(theme.getName());
        if (old == null) {
            themes.add(theme);
            return;
        }
        boolean wasCurrent = manager.getCurrentTheme() == old;
        if (themes instanceof List) {
            List<Theme> list = (List<Theme>) themes;
            list.set(list.indexOf(old), theme);
        } else {
            themes.remove(old);
            themes.add(theme);
        }
        if (wasCurrent)
            manager.setCurrentTheme(theme);
    }
}
```

### Minimal variant

If the user only wants a quick accent colour, the constructor can be shortened. Surfaces then keep the neutral grey defaults. The rest of the file stays exactly as in the template.

```java
    public HoneyTheme() {
        theme = new Theme("Honey", MAIN, new Color(MAIN).darker().getRGB(), 0xFFFFF8EB, 0xFF1A1205);
        theme.setBgShader(GuiStyle.BgShader.FIRESTORM);
        register(theme);
    }
```

(with `MAIN = 0xFFF59E0B`, class `HoneyTheme`, `@ModuleInfo(name = "HoneyTheme", ...)`)

### Optional: switch to the theme with a keybind

Add this method to the class. The addon then works like a button: when it's turned on (from the Addon Manager or with a keybind), it selects the theme and switches itself back off.

```java
    @Override
    protected void onEnable() {
        Arsenic.getArsenic().getThemeManager().setCurrentTheme(theme);
        setEnabledSilently(false);
    }
```

---

## Colour recipe

How to derive the whole palette from one hue. These rules follow the built-in themes (`Void`, `Jade`, `Ember`, etc.):

| Field | Recipe | Lagoon |
|---|---|---|
| `mainColor` | saturated mid-tone of the hue | `0xFF14B8A6` |
| gradient colour | same hue, darker or shifted ~20–40° | `0xFF0E7490` |
| `white` | hue at ~97 % lightness | `0xFFEFFFFC` |
| `black` | hue at ~4–8 % lightness | `0xFF04120F` |
| `configsBackground` | hue at ~4–5 % lightness, opaque | `0xFF030D0B` |
| `clickGuiBackground` | same as above, alpha `DD` | `0xDD04100E` |
| `configsCard` | hue at ~8–12 % lightness | `0xFF0A1C19` |
| `configsCardBorder`, `separator` | hue at ~18–25 % lightness | `0xFF173A34` |
| `configsHoverBackground` | between card and border | `0xFF0E2420` |
| `configsHoverBorder` | a bit brighter than the border | `0xFF235248` |
| `moduleBackground` | card RGB, alpha 160 | `(10, 28, 25, 160)` |
| `enumBackground` | half of the card RGB, alpha 255 | `(5, 14, 12, 255)` |
| `folderBackground` | half of the card RGB, alpha 180 | `(5, 14, 12, 180)` |
| `clickGuiSeparator` | `mainColor` RGB, alpha ~38 | `(20, 184, 166, 38)` |
| `textPrimary` | same as `white` | `0xFFEFFFFC` |

---

## Background shaders (`GuiStyle.BgShader`)

| Value | Used by | Suggested mood |
|---|---|---|
| `AURORA` | default when none is set | soft, neutral |
| `STARFIELD` | Ocean, Obsidian | night, space, minimal |
| `NEBULA` | Void | purple, cosmic |
| `FIRESTORM` | Classic, Ember | red, orange, fire |
| `CAUSTICS` | Jade | water, green, teal |
| `SYNTHWAVE` | Sakura | pink, retro, neon |
| `ZIPPYZAPS` | Specter, Toxin | electric, acid |
| `CHROME` | none | metallic, silver |

---

## Built-in themes (for reference)

| Name | `mainColor` | Gradient | Shader |
|---|---|---|---|
| Ocean | `0xFF3B82F6` | `0xFF1D4ED8` | STARFIELD |
| Classic | `0xFFDD425E` | none | FIRESTORM |
| Void | `0xFF7C3AED` | none | NEBULA |
| Specter | `0xFF38BDF8` | none | ZIPPYZAPS |
| Ember | `0xFFEA580C` | none | FIRESTORM |
| Jade | `0xFF10B981` | none | CAUSTICS |
| Obsidian | `0xFFE2E2E2` | none | STARFIELD |
| Sakura | `0xFFF472B6` | none | SYNTHWAVE |
| Toxin | `0xFF84CC16` | none | ZIPPYZAPS |

A new theme should look clearly different from these. Don't reuse a seed colour that's already taken.

---

## API reference

**`arsenic.gui.themes.Theme`**

| Member | Default | Notes |
|---|---|---|
| `new Theme(name, main, darker, white, black)` | | no gradient |
| `new Theme(name, main, darker, white, black, true, gradientColor)` | | with gradient |
| `setClickGuiBackground(int)` | `0xDD0C0C0C` | |
| `setClickGuiSeparator(int)` | `(0, 0, 0, 68)` | |
| `setModuleBackground(int)` | `(5, 5, 5, 160)` | |
| `setModuleHover(int)` | `(255, 255, 255, 15)` | overlay, keep it light and transparent |
| `setScrollbarTrack(int)` / `setScrollbarThumb(int)` | `(255,255,255,20)` / `(255,255,255,80)` | |
| `setButtonBackground(int)` | `(33, 33, 33, 234)` | |
| `setButtonCircleShadow(int)` / `setButtonCircleHighlight(int)` | `(0,0,0,60)` / `(255,255,255,30)` | |
| `setConfigsBackground(int)` | `0xFF0D0D0F` | |
| `setConfigsCard(int)` | `0xFF111114` | |
| `setConfigsCardBorder(int)` | `0xFF1E1E24` | contrast auto-raised to ≥ 1.8:1 against the card |
| `setSeparator(int)` | `(26, 26, 31)` | |
| `setConfigsHoverBackground(int)` | `0xFF141418` | |
| `setConfigsHoverBorder(int)` | `0xFF2A2A35` | contrast auto-raised to ≥ 2.8:1 |
| `setEnumBackground(int)` | `(26, 25, 25, 205)` | |
| `setFolderBackground(int)` | `(26, 25, 25, 150)` | |
| `setTextPrimary(int)` | `0xFFFFFFFF` | contrast auto-raised to ≥ 10:1 |
| `setTextSecondary(int)` / `setTextMuted(int)` | | currently unused |
| `setError(int)` | `0xFFE24B4A` | |
| `setStatus(int)` | `0xFFFFFFAA` | |
| `setBgShader(GuiStyle.BgShader)` | `AURORA` | |
| `setLogoPath(String)` | | don't use: it needs a PNG inside the client jar |

**`arsenic.gui.themes.ThemeManager`** (via `Arsenic.getArsenic().getThemeManager()`)

| Method | Notes |
|---|---|
| `getContents()` | the live list of themes, which the GUI tab reads every frame |
| `getContentByJsonKey(String)` | find a theme by name (case-insensitive) |
| `getCurrentTheme()` | the selected theme |
| `setCurrentTheme(Theme)` | selects a theme and saves the client config |

---

## Pitfalls

- **Missing alpha:** `0x14B8A6` is fully transparent. Write `0xFF14B8A6`.
- **Built-in name:** addons load before the built-in themes are created, and a built-in is skipped when a theme with its name already exists. A theme called `Ocean` would silently replace the real Ocean.
- **Same name in two addons:** the second one replaces the first.
- **Grey or very dark `mainColor`:** the Material 3 scheme comes out dull. Pick a saturated colour unless a monochrome look is what the user wants.
- **Log line at startup:** `Could not load <Name> from themes: java.lang.NullPointerException` is harmless. The theme manager tries to read every listed theme from `~/Arsenic/themes.json`, which has no entry for addon themes.
- **`themes.json`:** if that file ever contains an entry with your theme's name, its values override the addon's colours at startup.
- **Compile errors:** the theme simply doesn't appear. Errors are logged with the `[addon]` prefix (see ADDONS.md).

## Testing checklist

1. `.addon reload`: the new swatch appears in **GUI > Accent colour**.
2. Click it: the menu and HUD recolour, and the theme name appears on the card.
3. Run `.addon reload` again: the theme is still selected and there's no duplicate swatch.
4. Restart the game: the theme is still selected.
