package arsenic.gui.themes.style;

import arsenic.gui.themes.Theme;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiScreen;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * How a theme lays out the ClickGUI and what it draws behind it. Read from the "layout" and "background"
 * keys of a theme file (see THEMES-LAYOUT.md). Built-in themes use the defaults: the classic window and
 * the theme's background shader.
 */
public final class ThemeLayout {

    public enum Type { WINDOW, PANELS }
    public enum TextAlign { LEFT, CENTER, RIGHT }
    public enum EnabledStyle { TEXT, FILL, BAR }
    public enum Background { SHADER, IMAGE, COLOR, GRADIENT, NONE }
    public enum Fit { COVER, STRETCH }

    // ---- layout ----
    public Type type = Type.WINDOW;
    public float panelWidth = 110f;
    public float headerHeight = 16f;
    public float rowHeight = 13f;
    public float panelGap = 4f;
    /** Corner radius in GUI pixels (not a multiplier like style.roundness). */
    public float roundness = 0f;
    public float outline = 0f;
    public float textSize = 9f;
    /** 0 = a panel may reach the bottom of the screen; above that its rows scroll. */
    public float maxHeight = 0f;
    public TextAlign textAlign = TextAlign.CENTER;
    public EnabledStyle enabledStyle = EnabledStyle.TEXT;
    public boolean collapsible = true;
    public boolean textShadow = true;
    /** null = the theme's main colour. */
    public Integer headerColor, enabledColor, outlineColor;
    public int headerText = 0xFFFFFFFF;
    public int rowColor = 0xB0101010;
    public int rowHover = 0x22FFFFFF;
    public int text = 0xFFDDDDDD;

    // ---- background ----
    public Background background = Background.SHADER;
    public File image;
    public Fit fit = Fit.COVER;
    public float dim = 0.35f;
    public boolean blur = true;
    public int bgColor = 0xC0000000;
    public int bgTop = 0xC0101018;
    public int bgBottom = 0xE0000000;

    /** Lets an addon draw parts of the PANELS layout itself. Return true when you drew the part. */
    public interface PanelPainter {
        /** The body behind a panel's rows. */
        default boolean panelBackground(float x1, float y1, float x2, float y2) { return false; }

        /** A panel's title bar, title text and fold sign included. */
        default boolean header(float x1, float y1, float x2, float y2, String title, boolean open, boolean hover) { return false; }

        /** One module row, its text included. */
        default boolean row(float x1, float y1, float x2, float y2, Module module, boolean hover) { return false; }
    }

    private static final ThemeLayout DEFAULT = new ThemeLayout();
    private static final Map<Theme, ThemeLayout> LAYOUTS = Collections.synchronizedMap(new WeakHashMap<Theme, ThemeLayout>());
    /** Keyed by lower-case theme name so they survive a reload of the theme files. */
    private static final Map<String, Supplier<? extends GuiScreen>> SCREENS = new HashMap<>();
    private static final Map<String, PanelPainter> PAINTERS = new HashMap<>();

    public static void set(Theme theme, ThemeLayout layout) {
        if (theme == null)
            return;
        if (layout == null) {
            LAYOUTS.remove(theme);
            return;
        }
        layout.sanitize();
        LAYOUTS.put(theme, layout);
    }

    public static ThemeLayout get(Theme theme) {
        return theme == null ? null : LAYOUTS.get(theme);
    }

    /** The current theme's layout; never null. Do not modify the shared default. */
    public static ThemeLayout current() {
        try {
            Theme t = Arsenic.getArsenic().getThemeManager().getCurrentTheme();
            ThemeLayout l = t == null ? null : LAYOUTS.get(t);
            return l != null ? l : DEFAULT;
        } catch (RuntimeException e) {
            return DEFAULT;
        }
    }

    private static String currentName() {
        try {
            Theme t = Arsenic.getArsenic().getThemeManager().getCurrentTheme();
            return t == null ? null : t.getName().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** An addon can replace the whole ClickGUI of a theme with its own screen. null removes it. */
    public static synchronized void setScreen(String themeName, Supplier<? extends GuiScreen> screen) {
        String key = themeName.toLowerCase(Locale.ROOT);
        if (screen == null) SCREENS.remove(key); else SCREENS.put(key, screen);
    }

    public static synchronized Supplier<? extends GuiScreen> screen() {
        String n = currentName();
        return n == null ? null : SCREENS.get(n);
    }

    public static synchronized void setPainter(String themeName, PanelPainter painter) {
        String key = themeName.toLowerCase(Locale.ROOT);
        if (painter == null) PAINTERS.remove(key); else PAINTERS.put(key, painter);
    }

    public static synchronized PanelPainter painter() {
        String n = currentName();
        return n == null ? null : PAINTERS.get(n);
    }

    /** Called when the painter threw: it stays off until the addon sets it again. */
    public static synchronized void disablePainter() {
        String n = currentName();
        if (n != null && PAINTERS.remove(n) != null)
            System.err.println("[Arsenic] The panel painter of theme \"" + n + "\" crashed and was turned off");
    }

    public void sanitize() {
        if (type == null) type = Type.WINDOW;
        if (textAlign == null) textAlign = TextAlign.CENTER;
        if (enabledStyle == null) enabledStyle = EnabledStyle.TEXT;
        if (background == null) background = Background.SHADER;
        if (fit == null) fit = Fit.COVER;
        panelWidth = clamp(panelWidth, 60f, 240f);
        headerHeight = clamp(headerHeight, 10f, 40f);
        rowHeight = clamp(rowHeight, 8f, 30f);
        panelGap = clamp(panelGap, 0f, 40f);
        roundness = clamp(roundness, 0f, 12f);
        outline = clamp(outline, 0f, 3f);
        textSize = clamp(textSize, 5f, 16f);
        maxHeight = clamp(maxHeight, 0f, 1000f);
        dim = clamp(dim, 0f, 1f);
    }

    private static float clamp(float v, float min, float max) {
        if (Float.isNaN(v))
            return min;
        return Math.max(min, Math.min(max, v));
    }

    // ---- JSON ----

    public static ThemeLayout fromJson(JsonObject layout, JsonElement background, String theme) {
        ThemeLayout l = new ThemeLayout();
        if (layout != null) {
            l.type = enumOf(layout, "type", Type.class, l.type, theme);
            JsonObject p = layout.has("panel") && layout.get("panel").isJsonObject() ? layout.getAsJsonObject("panel") : null;
            if (p != null) {
                l.panelWidth = num(p, "width", l.panelWidth, theme);
                l.headerHeight = num(p, "headerHeight", l.headerHeight, theme);
                l.rowHeight = num(p, "rowHeight", l.rowHeight, theme);
                l.panelGap = num(p, "gap", l.panelGap, theme);
                l.roundness = num(p, "roundness", l.roundness, theme);
                l.outline = num(p, "outline", l.outline, theme);
                l.textSize = num(p, "textSize", l.textSize, theme);
                l.maxHeight = num(p, "maxHeight", l.maxHeight, theme);
                l.textAlign = enumOf(p, "textAlign", TextAlign.class, l.textAlign, theme);
                l.enabledStyle = enumOf(p, "enabled", EnabledStyle.class, l.enabledStyle, theme);
                l.collapsible = bool(p, "collapsible", l.collapsible, theme);
                l.textShadow = bool(p, "textShadow", l.textShadow, theme);
                l.headerColor = color(p, "headerColor", null, theme);
                l.enabledColor = color(p, "enabledColor", null, theme);
                l.outlineColor = color(p, "outlineColor", null, theme);
                l.headerText = color(p, "headerText", l.headerText, theme);
                l.rowColor = color(p, "rowColor", l.rowColor, theme);
                l.rowHover = color(p, "rowHover", l.rowHover, theme);
                l.text = color(p, "text", l.text, theme);
            }
        }
        if (background != null && background.isJsonObject()) {
            JsonObject b = background.getAsJsonObject();
            l.background = enumOf(b, "type", Background.class, l.background, theme);
            l.dim = num(b, "dim", l.dim, theme);
            l.blur = bool(b, "blur", l.blur, theme);
            l.fit = enumOf(b, "fit", Fit.class, l.fit, theme);
            l.bgColor = color(b, "color", l.bgColor, theme);
            l.bgTop = color(b, "top", l.bgTop, theme);
            l.bgBottom = color(b, "bottom", l.bgBottom, theme);
            if (b.has("file")) {
                try {
                    String raw = b.get("file").getAsString().trim();
                    File f = new File(raw);
                    if (!f.isAbsolute())
                        f = new File(ThemeLoader.folder(), raw);
                    l.image = f;
                } catch (RuntimeException e) {
                    bad(theme, "file", String.valueOf(b.get("file")));
                }
            }
            if (l.background == Background.IMAGE && (l.image == null || !l.image.isFile())) {
                System.err.println("[Arsenic] Theme \"" + theme + "\": background image "
                        + (l.image == null ? "(no \"file\" key)" : l.image.getPath()) + " not found, using the shader");
                l.background = Background.SHADER;
            }
        }
        l.sanitize();
        return l;
    }

    private static void bad(String theme, String key, String raw) {
        System.err.println("[Arsenic] Theme \"" + theme + "\" layout: bad value for \"" + key + "\" (" + raw + "), using the default");
    }

    private static float num(JsonObject o, String key, float def, String theme) {
        if (!o.has(key))
            return def;
        try {
            return o.get(key).getAsFloat();
        } catch (RuntimeException e) {
            bad(theme, key, String.valueOf(o.get(key)));
            return def;
        }
    }

    private static boolean bool(JsonObject o, String key, boolean def, String theme) {
        if (!o.has(key))
            return def;
        try {
            return o.get(key).getAsBoolean();
        } catch (RuntimeException e) {
            bad(theme, key, String.valueOf(o.get(key)));
            return def;
        }
    }

    private static Integer color(JsonObject o, String key, Integer def, String theme) {
        if (!o.has(key))
            return def;
        try {
            return ThemeLoader.color(o.get(key));
        } catch (RuntimeException e) {
            bad(theme, key, String.valueOf(o.get(key)));
            return def;
        }
    }

    private static <E extends Enum<E>> E enumOf(JsonObject o, String key, Class<E> type, E def, String theme) {
        if (!o.has(key))
            return def;
        String raw = String.valueOf(o.get(key));
        try {
            raw = o.get(key).getAsString();
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            bad(theme, key, raw);
            return def;
        }
    }
}
