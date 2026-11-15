package arsenic.gui.themes.style;

import arsenic.gui.click.GuiStyle;
import arsenic.gui.themes.Theme;
import arsenic.utils.java.FileUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.Color;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Reads theme files from {@code ~/Arsenic/themes/*.json}. The format is documented in THEMES.md. */
public final class ThemeLoader {

    private ThemeLoader() {}

    public static File folder() {
        return new File(FileUtils.getArsenicFolderDirAsFile(), "themes");
    }

    /** Changes whenever a theme file is added, removed or saved. */
    public static long stamp() {
        File[] files = files();
        if (files == null)
            return 0L;
        long stamp = files.length;
        for (File f : files)
            stamp = stamp * 31L + f.lastModified() + f.length() + f.getName().hashCode();
        return stamp;
    }

    /** Every valid theme file; broken files are skipped with a message in the log. */
    public static List<Theme> loadAll() {
        List<Theme> out = new ArrayList<>();
        File dir = folder();
        if (!dir.isDirectory() && !dir.mkdirs())
            return out;
        File[] files = files();
        if (files == null)
            return out;
        for (File f : files) {
            try {
                String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                JsonObject o = new JsonParser().parse(text).getAsJsonObject();
                String fileName = f.getName();
                out.add(parse(o, fileName.substring(0, fileName.length() - 5)));
            } catch (Exception e) {
                System.err.println("[Arsenic] Skipped theme file " + f.getName() + ": " + e);
            }
        }
        return out;
    }

    private static File[] files() {
        File[] files = folder().listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files != null)
            Arrays.sort(files, Comparator.comparing(File::getName));
        return files;
    }

    /** Builds a theme from one file's JSON and registers its style. */
    public static Theme parse(JsonObject o, String fallbackName) {
        String name = o.has("name") ? o.get("name").getAsString().trim() : fallbackName;
        if (name.isEmpty())
            name = fallbackName;

        JsonObject c = o.has("colors") && o.get("colors").isJsonObject() ? o.getAsJsonObject("colors") : null;
        if (c == null || !c.has("main"))
            throw new IllegalArgumentException("\"colors.main\" is required");

        int main = color(c.get("main"));
        int dark = c.has("dark") ? color(c.get("dark")) : new Color(main).darker().getRGB();
        int white = c.has("white") ? color(c.get("white")) : 0xFFFFFFFF;
        int black = c.has("black") ? color(c.get("black")) : 0xFF0A0A0A;
        boolean gradient = c.has("gradient");
        int gradientColor = gradient ? color(c.get("gradient")) : main;

        Theme theme = new Theme(name, main, dark, white, black, gradient, gradientColor);

        // optional colours of the older (non-M3) components
        if (c.has("clickGuiBackground")) theme.setClickGuiBackground(color(c.get("clickGuiBackground")));
        if (c.has("moduleBackground")) theme.setModuleBackground(color(c.get("moduleBackground")));
        if (c.has("configsBackground")) theme.setConfigsBackground(color(c.get("configsBackground")));
        if (c.has("configsCard")) theme.setConfigsCard(color(c.get("configsCard")));
        if (c.has("configsCardBorder")) theme.setConfigsCardBorder(color(c.get("configsCardBorder")));
        if (c.has("textPrimary")) theme.setTextPrimary(color(c.get("textPrimary")));

        if (o.has("background")) {
            String bg = o.get("background").getAsString().trim().toUpperCase(Locale.ROOT);
            try {
                theme.setBgShader(GuiStyle.BgShader.valueOf(bg));
            } catch (IllegalArgumentException e) {
                System.err.println("[Arsenic] Theme " + name + ": unknown background \"" + bg + "\"");
            }
        }

        JsonObject style = o.has("style") && o.get("style").isJsonObject() ? o.getAsJsonObject("style") : null;
        ThemeStyle.set(theme, ThemeStyle.fromJson(style));
        return theme;
    }

    /** "#RRGGBB", "#AARRGGBB", "0xAARRGGBB" or a plain number. */
    public static int color(JsonElement e) {
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber())
            return (int) e.getAsLong();
        String raw = e.getAsString().trim();
        String s = raw;
        if (s.startsWith("#"))
            s = s.substring(1);
        else if (s.toLowerCase(Locale.ROOT).startsWith("0x"))
            s = s.substring(2);
        if (s.length() == 6)
            return 0xFF000000 | Integer.parseInt(s, 16);
        if (s.length() == 8)
            return (int) Long.parseLong(s, 16);
        throw new IllegalArgumentException("bad colour \"" + raw + "\" (use #RRGGBB or #AARRGGBB)");
    }
}
