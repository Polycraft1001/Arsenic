# Themes

A theme changes the **colours** of Arsenic and, since theme styles, the **shapes and layout** of the ClickGUI
and the HUD: corner roundness, control shape (pill / rounded / square), outlines, shadows, glass, glow,
animation speed, spacing, font, HUD cards and the module list.

There are two ways to make one:

| | Theme file (`.json`) | Addon (`.java`) |
|---|---|---|
| What you write | One JSON file | A Java class (see [ADDONS.md](ADDONS.md)) |
| Colours | Yes | Yes |
| Style (shapes, layout, HUD list) | Yes | Yes |
| Draw pieces yourself (`ThemeRenderer`) | No | Yes |
| Reloads | About once a second while the ClickGUI is open | On start or `.addon reload` |
| Good for | Everyone, and AI-generated themes | Custom drawing |

This file is written so that you can give it to an AI and ask for a theme. The section
[Rules for AI](#rules-for-ai) is the short version.

---

## Quick start

1. Open the Arsenic folder in your home folder and go into `themes` (it is created on the first start):
   - macOS / Linux: `~/Arsenic/themes/`
   - Windows: `C:\Users\<name>\Arsenic\themes\`
2. Create a file `midnight-lime.json` (plain text, UTF-8) with:

```json
{
  "name": "Midnight Lime",
  "colors": {
    "main": "#B6FF3B"
  }
}
```

3. Start the game (or, if it is already running, open the ClickGUI: theme files are checked about once a second
   while it is open).
4. Pick **Midnight Lime** where you pick the built-in themes, or type `.theme load Midnight Lime`.

Everything you leave out keeps the default, so the file above is a valid theme. Add a `"style"` object to change
shapes and layout (see below).

`~/Arsenic/themes/` (folder of theme files) is not the same thing as `~/Arsenic/themes.json` (a file the client
uses for itself). Do not put your themes in `themes.json`.

---

## Rules for AI

If you are an AI asked to create an Arsenic theme, follow these rules:

1. Output **one JSON object**, strict JSON: no comments, no trailing commas, double quotes only.
2. `"name"` and `"colors"."main"` are required. Everything else is optional.
3. The name must be unique and must not be a built-in theme name:
   `Ocean`, `Classic`, `Void`, `Specter`, `Ember`, `Jade`, `Obsidian`, `Sakura`, `Toxin` (case does not matter).
4. Use **only the keys documented in this file**, with the exact spelling, case and nesting. Unknown keys are
   ignored without any message, so a typo silently does nothing.
5. Write colours as **strings**: `"#RRGGBB"` or `"#AARRGGBB"`. Never write colours as plain numbers.
6. Keep every number inside its range (tables below). Values outside are clamped.
7. Write enum values in **UPPER CASE** exactly as listed (`"SQUARE"`, `"PANEL"`, `"STARFIELD"`...).
8. `main` is the accent of the whole client (buttons, switches, sliders, HUD). Pick a colour that is clearly
   visible on a **dark** background: medium to high brightness, not near black, not near grey.
9. Arsenic is a dark client. Background colours (`black`, `clickGuiBackground`, `moduleBackground`,
   `configs*`) should stay dark. Light themes are not supported.
10. Make the style match the idea: a "pixel / terminal" theme wants `roundness` 0 and `controlShape` `SQUARE`;
    a "soft / glassy" theme wants high `roundness`, `glass` and `shadow`; a "flat / minimal" theme wants
    `glass`, `shadow` and `edgeGlow` at 0.
11. Give the file name to save it as: lowercase, dashes, ending in `.json` (example: `deep-ocean.json`).

### Prompt template

```text
Here is THEMES.md, the theme format of the Arsenic client: <paste this file>

Create one theme file.
Idea: <colours, mood and shapes, for example: "deep ocean, teal accent with a purple gradient,
very round, lots of glass, module list on the left with no background">

Reply with the file name and the JSON only.
```

---

## File format

Full file with **every key and its default value** (except `gradient`, which has no default: leave it out for a
theme without gradient):

```json
{
  "name": "My Theme",
  "background": "AURORA",
  "colors": {
    "main": "#7C5CFF",
    "dark": "#5640B2",
    "white": "#FFFFFFFF",
    "black": "#FF0A0A0A",
    "gradient": "#FF5CA8",
    "clickGuiBackground": "#DD0C0C0C",
    "moduleBackground": "#A0050505",
    "configsBackground": "#FF0D0D0F",
    "configsCard": "#FF111114",
    "configsCardBorder": "#FF1E1E24",
    "textPrimary": "#FFFFFFFF"
  },
  "style": {
    "roundness": 1,
    "controlShape": "PILL",
    "borderWidth": 1,
    "borderOpacity": 1,
    "shadow": 1,
    "glass": 1,
    "edgeGlow": 1,
    "animationSpeed": 1,
    "spacing": 1,
    "font": "DEFAULT",
    "hud": {
      "roundness": 1,
      "watermarkBar": true,
      "arrayList": {
        "background": "USER",
        "bar": "RIGHT",
        "barWidth": 2,
        "rowHeight": 13.5,
        "rowGap": 1.5,
        "align": "RIGHT"
      }
    }
  }
}
```

### Top-level keys

| Key | Type | Required | Meaning |
|---|---|---|---|
| `name` | string | yes* | Name shown in the theme list. *If missing or empty, the file name without `.json` is used. |
| `colors` | object | yes | Colours, see below. Must contain `main`. |
| `background` | string | no | Animated ClickGUI background, see below. Default `AURORA`. |
| `style` | object | no | Shapes and layout, see below. Missing = the client's usual look. |

### `colors`

Colour formats:

- `"#RRGGBB"` or `"0xRRGGBB"`: opaque colour.
- `"#AARRGGBB"` or `"0xAARRGGBB"`: colour with alpha (`00` = invisible, `FF` = opaque).
- A plain JSON number is read as a raw ARGB int: `16711680` is `0x00FF0000`, i.e. red with alpha 0, **invisible**.
  Use strings.
- Short forms like `"#F00"` are not supported: the whole file is skipped.

| Key | Default | Where it shows |
|---|---|---|
| `main` | required | The accent colour. The modern (M3) palette of the ClickGUI is built from it: buttons, switches, sliders, selection, focus, and the HUD accent. |
| `dark` | `main` darkened (×0.7) | Darker shade of the accent, mostly in the older components. |
| `white` | `#FFFFFFFF` | Light colour of the older components. Also the colour that animated theme colours fade to when there is no `gradient`. |
| `black` | `#FF0A0A0A` | Dark colour of the older components. |
| `gradient` | none | Second accent colour. **Writing this key turns gradients on.** Animated theme colours fade from `main` to it. |
| `clickGuiBackground` | `#DD0C0C0C` | Background of the older ClickGUI parts. |
| `moduleBackground` | `#A0050505` | Background of modules in the older ClickGUI parts. |
| `configsBackground` | `#FF0D0D0F` | Background of the Configs screen. |
| `configsCard` | `#FF111114` | Cards of the Configs screen. |
| `configsCardBorder` | `#FF1E1E24` | Card outlines of the Configs screen. Adjusted automatically if it has too little contrast. |
| `textPrimary` | `#FFFFFFFF` | Main text colour. Adjusted automatically if it has too little contrast with the background. |

Most of the modern ClickGUI takes its colours from `main` only. The other colours mostly affect older
components and the Configs screen, so do not expect every one of them to change something on every screen.

### `background`

The animated shader behind the ClickGUI. If you chose a fixed background in the GUI settings, that choice may win
over the theme's.

| Value | Used by the built-in themes |
|---|---|
| `AURORA` | (default for theme files) |
| `STARFIELD` | Ocean, Obsidian |
| `SYNTHWAVE` | Sakura |
| `CHROME` | - |
| `FIRESTORM` | Classic, Ember |
| `CAUSTICS` | Jade |
| `NEBULA` | Void |
| `ZIPPYZAPS` | Specter, Toxin |

An unknown value is logged and the theme keeps `AURORA`.

### `style`

All numbers can be integers or decimals. Multipliers: `1` = the client's usual look.

| Key | Type | Default | Range | Meaning |
|---|---|---|---|---|
| `roundness` | number | `1` | 0 to 2.5 | Multiplies the corner radius of panels, cards and windows. `0` = square corners. A radius never goes past half the box. |
| `controlShape` | enum | `PILL` | `PILL`, `ROUNDED`, `SQUARE` | Shape of switches, slider tracks and handles, buttons, chips and scrollbars. `PILL` = fully round ends, `ROUNDED` = small corners (a quarter of the height), `SQUARE` = no rounding. |
| `borderWidth` | number | `1` | 0 to 3 | Outline thickness of panels, cards and outlined buttons. `0` = no outline. |
| `borderOpacity` | number | `1` | 0 to 2 | Multiplies outline opacity. |
| `shadow` | number | `1` | 0 to 2 | Multiplies drop shadows. `0` = none. |
| `glass` | number | `1` | 0 to 2 | Multiplies the frosted glass film on panels. `0` = flat panels. |
| `edgeGlow` | number | `1` | 0 to 3 | Multiplies the accent glow on the edges of floating panels. |
| `animationSpeed` | number | `1` | 0.25 to 4 | `2` = animations twice as fast, `0.5` = twice as slow. |
| `spacing` | number | `1` | 0.75 to 1.35 | Multiplies the ClickGUI spacing unit (margins, gaps). Values near the ends of the range can make things tight or overlap. |
| `font` | enum | `DEFAULT` | `DEFAULT`, `VANILLA` | `DEFAULT` keeps the client font setting; `VANILLA` forces the Minecraft font (mostly the HUD, not everywhere in the ClickGUI). |
| `hud` | object | | | HUD settings, see below. |

### `style.hud`

| Key | Type | Default | Range | Meaning |
|---|---|---|---|---|
| `roundness` | number | `1` | 0 to 2.5 | Multiplies the corner radius of HUD cards (watermark, coordinates, keybinds, potions, scoreboard...). |
| `watermarkBar` | boolean | `true` | `true`, `false` | The small accent bar before "Arsenic" in the watermark. |
| `arrayList` | object | | | The module list, see below. |

### `style.hud.arrayList`

| Key | Type | Default | Range | Meaning |
|---|---|---|---|---|
| `background` | enum | `USER` | `USER`, `PANEL`, `RECTANGLE`, `BAR`, `NONE` | `USER` keeps the HUD module's own Background setting. `PANEL`, `RECTANGLE` and `BAR` force that Background mode. `NONE` = no background and no accent bar, just text. |
| `bar` | enum | `RIGHT` | `RIGHT`, `LEFT`, `NONE` | Side of the accent bar. |
| `barWidth` | number | `2` | 0.5 to 6 | Width of the accent bar. Above 2, the text moves away from the bar to keep a gap. |
| `rowHeight` | number | `13.5` | 9 to 24 | Height of one row. |
| `rowGap` | number | `1.5` | 0 to 6 | Gap between rows. Only used with the `PANEL` background, and never more than `rowHeight - 6`. |
| `align` | enum | `RIGHT` | `RIGHT`, `LEFT` | `RIGHT` = rows hug the right side (the usual look). `LEFT` = rows start at the left of a column as wide as the longest row (at least 72), and slide in from the left. |

---

## How values are read

- The game reads every `*.json` file in the `themes` folder, sorted by file name.
- **Broken file** (invalid JSON, missing `colors.main`, bad colour): the file is skipped and the log says
  `[Arsenic] Skipped theme file <file>: <reason>`.
- **Name already used** (built-in theme or another file, case does not matter): the file is skipped and the log
  says `[Arsenic] Theme file "<file>" uses the name of an existing theme and was skipped`.
- **Unknown background**: `[Arsenic] Theme <name>: unknown background "<value>"`, the theme keeps `AURORA`.
- **Bad style value** (wrong type or unknown enum): only that value goes back to its default, the log says
  `[Arsenic] Theme style: bad value for "<key>" (<value>), using the default`.
- **Out of range numbers** are clamped to the range without any message.
- **Unknown keys** are ignored without any message.
- Enum values are trimmed and upper-cased, so `"square"` works, but write `"SQUARE"`.
- After loading, the log says `[Arsenic] Loaded <n> theme file(s) from <folder>`.
- When you save a file while the game runs, the themes are reloaded. If the edited theme is the current one, it stays
  selected. If you delete the current theme's file, the client goes back to `Classic`.

The log is the game output (the launcher console, or `logs/latest.log` in the Minecraft folder).

---

## Examples

### Terminal: square, green, Minecraft font

`terminal.json`

```json
{
  "name": "Terminal",
  "background": "STARFIELD",
  "colors": {
    "main": "#33FF66",
    "dark": "#1A8033",
    "black": "#FF050805",
    "textPrimary": "#D8FFE0"
  },
  "style": {
    "roundness": 0,
    "controlShape": "SQUARE",
    "borderWidth": 1,
    "shadow": 0.4,
    "glass": 0.3,
    "edgeGlow": 0.5,
    "font": "VANILLA",
    "hud": {
      "roundness": 0,
      "watermarkBar": true,
      "arrayList": {
        "background": "RECTANGLE",
        "bar": "LEFT",
        "barWidth": 1.5,
        "rowHeight": 11,
        "align": "RIGHT"
      }
    }
  }
}
```

### Frost: round, lots of glass, gradient

`frost.json`

```json
{
  "name": "Frost",
  "background": "AURORA",
  "colors": {
    "main": "#8FD3FF",
    "gradient": "#C9A8FF"
  },
  "style": {
    "roundness": 1.8,
    "controlShape": "PILL",
    "borderOpacity": 0.6,
    "shadow": 1.4,
    "glass": 2,
    "edgeGlow": 1.5,
    "animationSpeed": 0.8,
    "spacing": 1.1,
    "hud": {
      "roundness": 1.8,
      "arrayList": {
        "background": "PANEL",
        "bar": "NONE",
        "rowHeight": 14,
        "rowGap": 2.5
      }
    }
  }
}
```

### Paper: flat, no outline, list on the left

`paper.json`

```json
{
  "name": "Paper",
  "background": "CHROME",
  "colors": {
    "main": "#F2C14E",
    "white": "#FFF6E0"
  },
  "style": {
    "roundness": 0.6,
    "controlShape": "ROUNDED",
    "borderWidth": 0,
    "shadow": 0,
    "glass": 0,
    "edgeGlow": 0,
    "animationSpeed": 1.5,
    "spacing": 0.9,
    "hud": {
      "roundness": 0.5,
      "watermarkBar": false,
      "arrayList": {
        "background": "NONE",
        "align": "LEFT",
        "rowHeight": 11
      }
    }
  }
}
```

### Neon Arcade: strong glow, thick bar

`neon-arcade.json`

```json
{
  "name": "Neon Arcade",
  "background": "SYNTHWAVE",
  "colors": {
    "main": "#FF3EA5",
    "dark": "#9E1F66",
    "gradient": "#3EE8FF"
  },
  "style": {
    "roundness": 1.2,
    "controlShape": "PILL",
    "borderWidth": 1.5,
    "borderOpacity": 1.6,
    "shadow": 1.2,
    "glass": 0.8,
    "edgeGlow": 3,
    "animationSpeed": 1.25,
    "hud": {
      "roundness": 1,
      "arrayList": {
        "background": "BAR",
        "bar": "RIGHT",
        "barWidth": 3,
        "rowHeight": 13
      }
    }
  }
}
```

---

## Addon themes and `ThemeRenderer`

A theme file can only change values. An addon can also **draw** some pieces itself with a `ThemeRenderer`
(package `arsenic.gui.themes.style`). Read [ADDONS.md](ADDONS.md) first: addons are `.java` files in
`~/Arsenic/addons/`, compiled when the game starts or on `.addon reload` (Java 8, MCP names).

> **Not tested yet.** There is no official "register a theme" API for addons. The example below adds the theme to
> the theme manager's list itself. Read the notes after it.

`~/Arsenic/addons/BlockyTheme.java`:

```java
import arsenic.gui.themes.Theme;
import arsenic.gui.themes.ThemeManager;
import arsenic.gui.themes.style.ThemeRenderer;
import arsenic.gui.themes.style.ThemeStyle;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import net.minecraft.client.renderer.GlStateManager;

@ModuleInfo(name = "BlockyTheme", description = "Adds the Blocky theme", category = ModuleCategory.RENDER, hidden = true)
public class BlockyTheme extends Module {

    private static final String NAME = "Blocky";
    private static final int ACCENT = 0x7CFF4F; // RGB, the alpha is added when drawing

    public BlockyTheme() {
        try {
            register();
        } catch (Throwable t) {
            System.err.println("[BlockyTheme] Could not add the theme: " + t);
        }
    }

    private static void register() {
        ThemeManager themes = Arsenic.getArsenic().getThemeManager();
        // find or add: ".addon reload" runs this constructor again
        Theme theme = themes.getContentByJsonKey(NAME);
        if (theme == null) {
            theme = new Theme(NAME, 0xFF000000 | ACCENT, 0xFF3E9A26, 0xFFFFFFFF, 0xFF0A0A0A);
            themes.getContents().add(theme);
        }

        ThemeStyle style = new ThemeStyle();
        style.roundness = 0f;
        style.controlShape = ThemeStyle.Shape.SQUARE;
        style.glass = 0.4f;
        style.font = ThemeStyle.FontChoice.VANILLA;
        style.hudRoundness = 0f;
        style.listRowHeight = 12f; // the list layout still comes from the style
        ThemeStyle.set(theme, style); // set() clamps every value
        ThemeStyle.setRenderer(theme, new BlockyRenderer());
    }

    /** Square switches and a custom module list. Everything else keeps the default drawing. */
    private static final class BlockyRenderer implements ThemeRenderer {

        @Override
        public float switchControl(float x1, float y1, float height, float pct, float hover) {
            float w = height * 1.8f;
            DrawUtils.drawRect(x1, y1, x1 + w, y1 + height, ColorUtils.withAlpha(0x202020, 230));
            float knob = height - 4f;
            float kx = x1 + 2f + (w - knob - 4f) * pct;
            int knobColor = pct > 0.5f
                    ? ColorUtils.withAlpha(ACCENT, 255)
                    : ColorUtils.withAlpha(0xFFFFFF, 140 + (int) (60 * hover));
            DrawUtils.drawRect(kx, y1 + 2f, kx + knob, y1 + 2f + knob, knobColor);
            GlStateManager.color(1f, 1f, 1f, 1f);
            return w; // the width you used
        }

        @Override
        public boolean replacesArrayList() {
            return true;
        }

        @Override
        public void arrayListRow(ThemeRenderer.ArrayListRow row) {
            int alpha = Math.round(255 * row.opacity);
            if (alpha < 8)
                return; // almost invisible: skip it (text with alpha near 0 can flash opaque)
            int rgb = row.color & 0xFFFFFF;
            DrawUtils.drawRect(row.x1, row.y1, row.x2, row.y2, ColorUtils.withAlpha(0x000000, row.backgroundAlpha));
            DrawUtils.drawRect(row.x1, row.y1, row.x1 + 2f, row.y2, ColorUtils.withAlpha(rgb, alpha));
            float cy = (row.y1 + row.y2) / 2f;
            float x = row.x1 + 4f;
            row.nameFont.drawString(row.name, x, cy, ColorUtils.withAlpha(rgb, alpha), row.nameFont.CENTREY);
            if (row.info != null) {
                x += row.nameFont.getWidth(row.name + " ");
                row.infoFont.drawString(row.info, x, cy, ColorUtils.withAlpha(0xAAAAAA, alpha), row.infoFont.CENTREY);
            }
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
    }
}
```

### `ThemeRenderer` methods

Every method is optional (they all have a default). Return `true` when you drew the piece, `false` to let the client
draw its default. Coordinates are GUI units, colours are ARGB ints. The radius you receive already includes the
theme's style (roundness, control shape). Animation values (`pct`, `hover`, `active`) go from 0 to 1.

| Method | Draws | Return |
|---|---|---|
| `surface(x1, y1, x2, y2, radius, fill, elevation)` | Panels, cards and windows of the ClickGUI (everything that uses the M3 glass surface). `elevation` 0 = flat card, 1 = floating window. | `true` if drawn |
| `hudCard(x1, y1, x2, y2, radius, alpha)` | Background card of a HUD element (watermark, coordinates, keybinds, potions, scoreboard...). | `true` if drawn |
| `switchControl(x1, y1, height, pct, hover)` | On/off switch. `pct` = on-state animation. | The width you used, or a negative number for the default switch |
| `slider(x1, cy, len, percent, active)` | Single-value slider: track from `x1` to `x1 + len`, centred on `cy`. | `true` if drawn |
| `button(fr, label, x1, y1, x2, y2, hover, filled)` | Text button. `filled` = primary button, otherwise outlined. | `true` if drawn |
| `checkbox(cx, cy, size, pct, hover)` | Checkbox centred on (`cx`, `cy`). `size` is the **half**-size of the box. | `true` if drawn |
| `scrollbar(x, y, trackHeight, thumbY, thumbHeight, pct)` | Scrollbar. `x` is the **right** edge of the track. | `true` if drawn |
| `replacesArrayList()` | - | `true` to draw the HUD module list yourself |
| `arrayListRow(row)` | One row of the HUD module list, only when `replacesArrayList()` is `true`. | - |

`ThemeRenderer.ArrayListRow` fields: `index` (0 = top row), `name`, `info` (mode/value, may be `null`),
`x1`, `y1`, `x2`, `y2` (the box, already laid out and animated), `color` (HUD accent of this row, theme or rainbow),
`opacity` (0 to 1 while the row fades in or out), `backgroundAlpha` (the HUD Opacity setting, 0 to 255, already
multiplied by `opacity`), `nameFont`, `infoFont`.

Rows are drawn outside the HUD text batch, so you may push, move and pop the GL matrix (pop what you push). Rows you
draw get no blur or bloom pass. The box is sized for the default text layout: keep your padding close to it or
the text may overflow.

**Safety:** if one of your methods throws, the renderer is turned off for the rest of the session and the defaults
come back (log: `[Arsenic] The renderer of theme "<name>" crashed and was turned off`), so a bug cannot lock you
out of the ClickGUI. Always leave the GL colour at white (`GlStateManager.color(1f, 1f, 1f, 1f)`) when you return.

### Notes on addon themes (untested)

- **Load order.** Addon modules are most likely built before the theme manager loads its themes. The example adds
  its theme to the list right away; the theme manager keeps it (it only adds missing built-in themes and re-reads
  theme files). If `getThemeManager()` is not ready yet, the `try/catch` logs the error and the theme is missing:
  run `.addon reload` once the game is open.
- **Find or add by name.** `.addon reload` runs the constructor again; looking the theme up first avoids a duplicate.
  The style and the renderer are simply replaced.
- **Do not mix with a theme file of the same name.** The file would be skipped (name already used), or the file
  reload would replace the theme object and drop your renderer. Give an addon theme a name no file uses.
- **Selection after a restart** may come back to `Classic` if the addon theme was not in the list when the client
  config was loaded. Pick it again, or use `.theme load Blocky`.
- The client also keeps `~/Arsenic/themes.json` for its own theme data. If an entry with your theme's name ends up
  in it, its saved values may be applied over the colours in your code.
- `ThemeStyle.current()` returns a shared default object when the current theme has no style. Never change it;
  create your own `new ThemeStyle()` and call `ThemeStyle.set(theme, style)`.

---

## Pitfalls

- **Colours as numbers**: `"main": 16711680` is invisible (alpha 0). Write `"#FF0000"`.
- **`gradient` with the same colour as `main`** still turns gradients on, it just looks flat. Leave the key out if
  you do not want a gradient.
- **Typos in keys** (`"roundnes"`, `"arraylist"`, `"Colors"`) are ignored silently. Keys are case-sensitive.
- **Wrong nesting**: `rowHeight` goes in `style.hud.arrayList`, not in `style` or `style.hud`.
- **macOS TextEdit** saves rich text by default: use Format > Make Plain Text, and check that the file really ends
  in `.json` and not `.json.txt` or `.rtf`.
- **Same name as a built-in theme** (`Ocean`, `Classic`, `Void`, `Specter`, `Ember`, `Jade`, `Obsidian`, `Sakura`,
  `Toxin`) or as another file: the file is skipped. Two files with the same name: the first by file name wins.
- **Very dark `main`**: the accents (switches, sliders, selection) become hard to see.
- **`rowGap` does nothing** unless `background` is `PANEL` (or `USER` with the HUD Background setting on Panel).
- **Extreme `spacing`** can make parts of the ClickGUI overlap. Stay between 0.9 and 1.15 if unsure.

---

## Limits

- **Module cards of the ClickGUI** have no renderer hook yet: an addon can change their shape through the style,
  but cannot draw them itself.
- **`font: VANILLA`** is applied mostly to the HUD; parts of the ClickGUI keep the client font.
- **Some overlays** still use fixed corner radii and do not follow `roundness`.
- **Theme files cannot draw new shapes**: they only change values. Custom drawing needs an addon.
- **No official theme registration API** for addons yet (see the notes above).
- **Light themes** are not supported: the client is designed for dark backgrounds. `textPrimary` and
  `configsCardBorder` are adjusted for contrast, the rest is not.
