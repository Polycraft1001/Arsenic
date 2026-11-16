# Theme layouts and backgrounds

This completes [THEMES.md](THEMES.md). Besides colours and `style`, a theme file can pick **how the ClickGUI is
laid out** (`layout`) and **what is drawn behind it** (`background`). Both keys are optional; without them a
theme keeps the classic window and its background shader.

## Rules for AI

- Same rules as THEMES.md: one JSON object, no comments, no trailing commas, only documented keys, colours as
  strings (`"#RRGGBB"` or `"#AARRGGBB"`), enum values in UPPER CASE, numbers inside the ranges below.
- `layout` and `background` are top-level keys, next to `name`, `colors` and `style`.
- If you use `"type": "IMAGE"`, tell the user to put the picture next to the JSON in `~/Arsenic/themes/`.
- Theme names should avoid spaces if the user switches themes with `.theme load <name>`.

## `layout`

```json
"layout": {
  "type": "PANELS",
  "panel": {
    "width": 110,
    "headerHeight": 16,
    "rowHeight": 13,
    "gap": 4,
    "roundness": 0,
    "outline": 0,
    "textSize": 9,
    "maxHeight": 0,
    "textAlign": "CENTER",
    "enabled": "TEXT",
    "collapsible": true,
    "textShadow": true,
    "headerColor": "#FF8B3DFF",
    "headerText": "#FFFFFFFF",
    "rowColor": "#B0101010",
    "rowHover": "#22FFFFFF",
    "text": "#FFDDDDDD",
    "enabledColor": "#FFB98CFF",
    "outlineColor": "#FF8B3DFF"
  }
}
```

| Key | Values | Default | What it does |
|---|---|---|---|
| `type` | `WINDOW`, `PANELS` | `WINDOW` | `WINDOW` = Arsenic's classic window. `PANELS` = one draggable column per category (Meteor style). |
| `panel.width` | 60 – 240 | 110 | Column width, in GUI pixels |
| `panel.headerHeight` | 10 – 40 | 16 | Title bar height |
| `panel.rowHeight` | 8 – 30 | 13 | Height of one module row |
| `panel.gap` | 0 – 40 | 4 | Space between columns (first placement only) |
| `panel.roundness` | 0 – 12 | 0 | Corner radius in **pixels** (not a multiplier) |
| `panel.outline` | 0 – 3 | 0 | Border width around each column, 0 = none |
| `panel.textSize` | 5 – 16 | 9 | Text height (9 ≈ vanilla text) |
| `panel.maxHeight` | 0 – 1000 | 0 | Max column height; longer lists scroll. 0 = down to the bottom of the screen |
| `panel.textAlign` | `LEFT`, `CENTER`, `RIGHT` | `CENTER` | Text alignment in titles and rows |
| `panel.enabled` | `TEXT`, `FILL`, `BAR` | `TEXT` | How an enabled module shows: coloured text, filled row, or a bar on the left |
| `panel.collapsible` | true / false | true | Right click on a title folds the column |
| `panel.textShadow` | true / false | true | Text shadow |
| `panel.headerColor` | colour | theme `main` | Title bar colour |
| `panel.headerText` | colour | `#FFFFFFFF` | Title text (and row text with `FILL`) |
| `panel.rowColor` | colour | `#B0101010` | Background behind the rows |
| `panel.rowHover` | colour | `#22FFFFFF` | Overlay on the row under the mouse |
| `panel.text` | colour | `#FFDDDDDD` | Text of disabled modules |
| `panel.enabledColor` | colour | theme `main` | Accent of enabled modules |
| `panel.outlineColor` | colour | header colour | Border colour |

The font follows `style.font` (`DEFAULT` = Inter, `VANILLA` = Minecraft).

### Using the PANELS layout

- **Left click** a module: toggle it. **Right click**: open its settings in the classic window (it opens on the
  search page with the module's name). Escape there closes it; the next ClickGUI key press opens the panels again.
- **Drag** a title to move a column. **Right click** a title to fold it. **Mouse wheel** over a column scrolls it.
- **Type** to filter modules, **Backspace** to erase, **Escape** clears the search, then closes.
- **Home** puts every column back in its default place. Positions are saved in `~/Arsenic/panels.json`.
- Addon modules get their own **Addons** column. Hidden tiers (Blatant switch) are hidden here too.

## `background`

`background` can still be a shader name (`"background": "STARFIELD"`). It can also be an object:

```json
"background": { "type": "IMAGE", "file": "my-wallpaper.png", "fit": "COVER", "dim": 0.4, "blur": true }
```

| Key | Values | Default | What it does |
|---|---|---|---|
| `type` | `SHADER`, `IMAGE`, `COLOR`, `GRADIENT`, `NONE` | `SHADER` | What is drawn behind the GUI |
| `shader` | AURORA, STARFIELD, SYNTHWAVE, CHROME, FIRESTORM, CAUSTICS, NEBULA, ZIPPYZAPS | AURORA | With `SHADER`: which animation |
| `file` | file name or absolute path | – | With `IMAGE`: PNG or JPG, relative to `~/Arsenic/themes/` |
| `fit` | `COVER`, `STRETCH` | `COVER` | `COVER` crops to fill the screen, `STRETCH` deforms |
| `dim` | 0 – 1 | 0.35 | With `IMAGE`: darkens the picture so text stays readable |
| `color` | colour | `#C0000000` | With `COLOR` |
| `top`, `bottom` | colours | `#C0101018`, `#E0000000` | With `GRADIENT`: vertical gradient |
| `blur` | true / false | true | Blurs the game behind (not used with `SHADER`, which always blurs) |

`NONE` draws nothing: the game stays visible (blurred if `blur` is true). The image reloads by itself when the
file changes. A missing image falls back to the shader, with a message in the log.

The background applies to both layouts (the classic window draws it too).

## Example: Meteor-like theme

```json
{
  "name": "MeteorPurple",
  "colors": { "main": "#9147FF" },
  "background": { "type": "NONE", "blur": false },
  "style": { "font": "DEFAULT" },
  "layout": {
    "type": "PANELS",
    "panel": {
      "width": 112,
      "headerHeight": 15,
      "rowHeight": 13,
      "gap": 3,
      "textAlign": "CENTER",
      "enabled": "FILL",
      "headerColor": "#FF8B3DFF",
      "rowColor": "#C0141414",
      "enabledColor": "#995B21B6"
    }
  }
}
```

## Addons

Three hooks, all in `arsenic.gui.themes.style.ThemeLayout`, keyed by theme name (case-insensitive):

- `ThemeLayout.set(theme, layout)`: give a theme a layout (fill a `new ThemeLayout()`'s public fields).
- `ThemeLayout.setPainter("ThemeName", painter)`: a `PanelPainter` draws the panel body, titles and/or rows
  itself (`panelBackground`, `header`, `row`; return `true` when you drew it). A painter that throws is turned
  off and logged.
- `ThemeLayout.setScreen("ThemeName", () -> new MyClickGui())`: **replaces the whole ClickGUI** with your own
  `GuiScreen` while that theme is selected. The ClickGUI key closes it again.

These hooks are untested and may change.

## Known limits

- Settings are not edited inside the panels: right click opens the classic window.
- Opening the ClickGUI from somewhere else than its key (for example a button that returns from the HUD editor)
  may show the classic window.
- Blur and the image are drawn the same way in both layouts; very large images take a moment to load the
  first time.
