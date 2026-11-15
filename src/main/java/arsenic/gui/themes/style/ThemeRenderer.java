package arsenic.gui.themes.style;

import arsenic.utils.font.FontRendererExtension;

/**
 * Lets a theme draw GUI and HUD pieces itself. Register one with
 * {@link ThemeStyle#setRenderer(arsenic.gui.themes.Theme, ThemeRenderer)}.
 * <p>
 * Every method is optional. Return {@code true} (or a width, for {@link #switchControl}) when you drew
 * the piece; return {@code false} / a negative number to let the client draw its default. All
 * coordinates are GUI units; colours are ARGB ints. Radii you receive already include the theme's
 * {@link ThemeStyle} (roundness, control shape).
 * <p>
 * If a method throws, the renderer is turned off for the session and the defaults come back, so a
 * bug cannot lock you out of the ClickGUI. Leave the GL colour at white when you return.
 */
public interface ThemeRenderer {

    /**
     * Panels, cards and windows of the ClickGUI (and everything that uses the M3 glass surface).
     * @param elevation 0 for a flat card, 1 for the floating window
     */
    default boolean surface(float x1, float y1, float x2, float y2, float radius, int fill, float elevation) {
        return false;
    }

    /** Background card of a HUD element (watermark, coordinates, keybinds, potions, scoreboard...). */
    default boolean hudCard(float x1, float y1, float x2, float y2, float radius, int alpha) {
        return false;
    }

    /**
     * On/off switch. {@code pct} 0..1 is the on-state animation, {@code hover} 0..1.
     * @return the width you used, or a negative number to draw the default switch
     */
    default float switchControl(float x1, float y1, float height, float pct, float hover) {
        return -1f;
    }

    /** Single-value slider: track from {@code x1} to {@code x1 + len} centred on {@code cy}. */
    default boolean slider(float x1, float cy, float len, float percent, float active) {
        return false;
    }

    /** Text button. {@code filled} = primary button, otherwise outlined. */
    default boolean button(FontRendererExtension<?> fr, String label,
                           float x1, float y1, float x2, float y2, float hover, boolean filled) {
        return false;
    }

    /** Checkbox centred on (cx, cy); {@code size} is the half-size of the box. */
    default boolean checkbox(float cx, float cy, float size, float pct, float hover) {
        return false;
    }

    /** Scrollbar; the track's right edge is {@code x}. */
    default boolean scrollbar(float x, float y, float trackHeight, float thumbY, float thumbHeight, float pct) {
        return false;
    }

    /** Return true to draw the HUD module list yourself with {@link #arrayListRow}. */
    default boolean replacesArrayList() {
        return false;
    }

    /**
     * One row of the HUD module list. Only called when {@link #replacesArrayList()} is true.
     * <p>
     * Rows are drawn outside the HUD text batch, so you may push, move and pop the GL matrix (pop
     * what you push). Rows you draw get no blur or bloom pass.
     */
    default void arrayListRow(ArrayListRow row) {
    }

    /** Everything about one module list row. The box is already laid out and animated. */
    final class ArrayListRow {
        /** 0 for the first (top) row. */
        public final int index;
        public final String name;
        /** Module info (mode, value...), or null. */
        public final String info;
        public final float x1, y1, x2, y2;
        /** The HUD accent colour for this row (theme or rainbow). */
        public final int color;
        /** 0..1 while the row fades in or out. */
        public final float opacity;
        /** The HUD Opacity setting for backgrounds, already multiplied by {@link #opacity} (0..255). */
        public final int backgroundAlpha;
        public final FontRendererExtension<?> nameFont;
        public final FontRendererExtension<?> infoFont;

        public ArrayListRow(int index, String name, String info, float x1, float y1, float x2, float y2,
                            int color, float opacity, int backgroundAlpha,
                            FontRendererExtension<?> nameFont, FontRendererExtension<?> infoFont) {
            this.index = index;
            this.name = name;
            this.info = info;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.color = color;
            this.opacity = opacity;
            this.backgroundAlpha = backgroundAlpha;
            this.nameFont = nameFont;
            this.infoFont = infoFont;
        }
    }
}
