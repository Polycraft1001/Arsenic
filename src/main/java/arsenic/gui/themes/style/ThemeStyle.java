package arsenic.gui.themes.style;

import arsenic.gui.themes.Theme;
import arsenic.main.Arsenic;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Shape, depth and layout settings a theme can change on top of its colours.
 * <p>
 * Every default below is the value the client drew with before themes could change it, so a theme
 * without a style looks exactly like before. Drawing code reads {@link #current()} each frame.
 * <p>
 * A theme gets a style from its JSON file ({@code "style": {...}}) or from an addon with
 * {@link #set(Theme, ThemeStyle)}. Do not change the object returned by {@link #current()} when the
 * theme has no style of its own: it is the shared default.
 */
public final class ThemeStyle {

    /** Shape of switches, slider tracks and handles, buttons, chips and scrollbars. */
    public enum Shape { PILL, ROUNDED, SQUARE }

    /** DEFAULT keeps the client font setting; VANILLA forces the Minecraft font. */
    public enum FontChoice { DEFAULT, VANILLA }

    /** USER keeps the HUD module's own Background setting. */
    public enum ListBackground { USER, PANEL, RECTANGLE, BAR, NONE }

    public enum Side { RIGHT, LEFT, NONE }

    public enum Align { RIGHT, LEFT }

    private static final ThemeStyle FALLBACK = new ThemeStyle();
    private static final Map<Theme, ThemeStyle> STYLES = new WeakHashMap<>();
    private static final Map<Theme, ThemeRenderer> RENDERERS = new WeakHashMap<>();

    // ---- ClickGUI and shared ----
    /** Multiplies panel and card corner radii. 0 = square corners. Range 0..2.5. */
    public float roundness = 1f;
    public Shape controlShape = Shape.PILL;
    /** Outline thickness of panels, cards and outlined buttons. 0 = no outline. Range 0..3. */
    public float borderWidth = 1f;
    /** Multiplies outline opacity. Range 0..2. */
    public float borderOpacity = 1f;
    /** Multiplies drop shadows. 0 = none. Range 0..2. */
    public float shadow = 1f;
    /** Multiplies the frosted glass film. 0 = flat panels. Range 0..2. */
    public float glass = 1f;
    /** Multiplies the accent edge glow on floating panels. Range 0..3. */
    public float edgeGlow = 1f;
    /** 2 = animations twice as fast, 0.5 = twice as slow. Range 0.25..4. */
    public float animationSpeed = 1f;
    /** Multiplies the ClickGUI spacing unit (margins, gaps). Range 0.75..1.35. */
    public float spacing = 1f;
    public FontChoice font = FontChoice.DEFAULT;

    // ---- HUD ----
    /** Multiplies HUD card corner radii. Range 0..2.5. */
    public float hudRoundness = 1f;
    public ListBackground listBackground = ListBackground.USER;
    public Side listBar = Side.RIGHT;
    /** Accent bar width of the module list. Range 0.5..6. */
    public float listBarWidth = 2f;
    /** Height of one module list row. Range 9..24. */
    public float listRowHeight = 13.5f;
    /** Gap between PANEL rows. Range 0..6. */
    public float listRowGap = 1.5f;
    public Align listAlign = Align.RIGHT;
    /** The small accent bar before "Arsenic" in the watermark. */
    public boolean watermarkBar = true;

    /** Clamps every value into its range and replaces nulls. Called by {@link #set}. */
    public ThemeStyle sanitize() {
        roundness = clamp(roundness, 0f, 2.5f);
        borderWidth = clamp(borderWidth, 0f, 3f);
        borderOpacity = clamp(borderOpacity, 0f, 2f);
        shadow = clamp(shadow, 0f, 2f);
        glass = clamp(glass, 0f, 2f);
        edgeGlow = clamp(edgeGlow, 0f, 3f);
        animationSpeed = clamp(animationSpeed, 0.25f, 4f);
        spacing = clamp(spacing, 0.75f, 1.35f);
        hudRoundness = clamp(hudRoundness, 0f, 2.5f);
        listBarWidth = clamp(listBarWidth, 0.5f, 6f);
        listRowHeight = clamp(listRowHeight, 9f, 24f);
        listRowGap = clamp(listRowGap, 0f, 6f);
        if (controlShape == null) controlShape = Shape.PILL;
        if (font == null) font = FontChoice.DEFAULT;
        if (listBackground == null) listBackground = ListBackground.USER;
        if (listBar == null) listBar = Side.RIGHT;
        if (listAlign == null) listAlign = Align.RIGHT;
        return this;
    }

    // ---- registry ----

    /** The style of the current theme, or the defaults. */
    public static ThemeStyle current() {
        Theme theme = currentTheme();
        ThemeStyle style = theme == null ? null : STYLES.get(theme);
        return style == null ? FALLBACK : style;
    }

    /** The style registered for {@code theme}, or null. */
    public static ThemeStyle get(Theme theme) {
        return theme == null ? null : STYLES.get(theme);
    }

    /** Gives {@code theme} a style; null removes it. */
    public static void set(Theme theme, ThemeStyle style) {
        if (theme == null)
            return;
        if (style == null)
            STYLES.remove(theme);
        else
            STYLES.put(theme, style.sanitize());
    }

    /** The custom renderer of the current theme, or null. */
    public static ThemeRenderer renderer() {
        Theme theme = currentTheme();
        return theme == null ? null : RENDERERS.get(theme);
    }

    /** Gives {@code theme} a custom renderer; null removes it. */
    public static void setRenderer(Theme theme, ThemeRenderer renderer) {
        if (theme == null)
            return;
        if (renderer == null)
            RENDERERS.remove(theme);
        else
            RENDERERS.put(theme, renderer);
    }

    /** Called when a renderer throws: it is turned off so the GUI keeps drawing with the defaults. */
    public static void rendererFailed(Throwable error) {
        Theme theme = currentTheme();
        if (theme != null)
            RENDERERS.remove(theme);
        System.err.println("[Arsenic] The renderer of theme \"" + (theme == null ? "?" : theme.getJsonKey())
                + "\" crashed and was turned off: " + error);
        error.printStackTrace();
    }

    private static Theme currentTheme() {
        try {
            return Arsenic.getArsenic().getThemeManager().getCurrentTheme();
        } catch (Throwable t) {
            return null;
        }
    }

    // ---- geometry helpers used by the drawing code ----

    /** Panel/card radius: {@code base} times {@link #roundness}, never more than half the box. */
    public float panelRadius(float base, float w, float h) {
        return capped(base * roundness, w, h);
    }

    /** HUD card radius: {@code base} times {@link #hudRoundness}, never more than half the box. */
    public float hudRadius(float base, float w, float h) {
        return capped(base * hudRoundness, w, h);
    }

    /** Corner radius of a control {@code h} tall. */
    public float controlRadius(float h) {
        switch (controlShape) {
            case SQUARE: return 0f;
            case ROUNDED: return h * 0.25f;
            default: return h / 2f;
        }
    }

    /** Scales a radius that is round by default (chips, nav items) by the control shape. */
    public float chipRadius(float defaultRadius) {
        switch (controlShape) {
            case SQUARE: return 0f;
            case ROUNDED: return defaultRadius * 0.5f;
            default: return defaultRadius;
        }
    }

    /** {@code alpha} times {@link #borderOpacity}, clamped to 0..255. */
    public int borderAlpha(int alpha) {
        return Math.max(0, Math.min(255, Math.round(alpha * borderOpacity)));
    }

    private static float capped(float r, float w, float h) {
        return Math.max(0f, Math.min(r, Math.min(w, h) / 2f));
    }

    private static float clamp(float v, float min, float max) {
        if (Float.isNaN(v))
            return min;
        return Math.max(min, Math.min(max, v));
    }

    // ---- JSON ----

    /** Reads the {@code "style"} object of a theme file. Unknown or bad values keep their default. */
    public static ThemeStyle fromJson(JsonObject o) {
        ThemeStyle s = new ThemeStyle();
        if (o == null)
            return s;
        s.roundness = num(o, "roundness", s.roundness);
        s.controlShape = enm(o, "controlShape", Shape.class, s.controlShape);
        s.borderWidth = num(o, "borderWidth", s.borderWidth);
        s.borderOpacity = num(o, "borderOpacity", s.borderOpacity);
        s.shadow = num(o, "shadow", s.shadow);
        s.glass = num(o, "glass", s.glass);
        s.edgeGlow = num(o, "edgeGlow", s.edgeGlow);
        s.animationSpeed = num(o, "animationSpeed", s.animationSpeed);
        s.spacing = num(o, "spacing", s.spacing);
        s.font = enm(o, "font", FontChoice.class, s.font);

        JsonObject hud = obj(o, "hud");
        if (hud != null) {
            s.hudRoundness = num(hud, "roundness", s.hudRoundness);
            s.watermarkBar = bool(hud, "watermarkBar", s.watermarkBar);
            JsonObject list = obj(hud, "arrayList");
            if (list != null) {
                s.listBackground = enm(list, "background", ListBackground.class, s.listBackground);
                s.listBar = enm(list, "bar", Side.class, s.listBar);
                s.listBarWidth = num(list, "barWidth", s.listBarWidth);
                s.listRowHeight = num(list, "rowHeight", s.listRowHeight);
                s.listRowGap = num(list, "rowGap", s.listRowGap);
                s.listAlign = enm(list, "align", Align.class, s.listAlign);
            }
        }
        return s.sanitize();
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("roundness", roundness);
        o.addProperty("controlShape", controlShape.name());
        o.addProperty("borderWidth", borderWidth);
        o.addProperty("borderOpacity", borderOpacity);
        o.addProperty("shadow", shadow);
        o.addProperty("glass", glass);
        o.addProperty("edgeGlow", edgeGlow);
        o.addProperty("animationSpeed", animationSpeed);
        o.addProperty("spacing", spacing);
        o.addProperty("font", font.name());
        JsonObject list = new JsonObject();
        list.addProperty("background", listBackground.name());
        list.addProperty("bar", listBar.name());
        list.addProperty("barWidth", listBarWidth);
        list.addProperty("rowHeight", listRowHeight);
        list.addProperty("rowGap", listRowGap);
        list.addProperty("align", listAlign.name());
        JsonObject hud = new JsonObject();
        hud.addProperty("roundness", hudRoundness);
        hud.addProperty("watermarkBar", watermarkBar);
        hud.add("arrayList", list);
        o.add("hud", hud);
        return o;
    }

    private static JsonObject obj(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null;
    }

    private static float num(JsonObject o, String key, float def) {
        try {
            return o.has(key) ? o.get(key).getAsFloat() : def;
        } catch (Exception e) {
            warn(key, o);
            return def;
        }
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        try {
            return o.has(key) ? o.get(key).getAsBoolean() : def;
        } catch (Exception e) {
            warn(key, o);
            return def;
        }
    }

    private static <E extends Enum<E>> E enm(JsonObject o, String key, Class<E> type, E def) {
        try {
            return o.has(key) ? Enum.valueOf(type, o.get(key).getAsString().trim().toUpperCase(Locale.ROOT)) : def;
        } catch (Exception e) {
            warn(key, o);
            return def;
        }
    }

    private static void warn(String key, JsonObject o) {
        System.err.println("[Arsenic] Theme style: bad value for \"" + key + "\" (" + o.get(key) + "), using the default");
    }
}
