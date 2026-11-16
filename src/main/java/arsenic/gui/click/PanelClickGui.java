package arsenic.gui.click;

import arsenic.gui.click.impl.ModuleCategoryComponent;
import arsenic.gui.click.impl.SearchComponent;
import arsenic.gui.design.Backdrop;
import arsenic.gui.themes.ThemeManager;
import arsenic.gui.themes.style.ThemeLayout;
import arsenic.gui.themes.style.ThemeStyle;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.font.VanillaFontRenderer;
import arsenic.utils.java.FileUtils;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.render.DrawUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The PANELS layout: one draggable column per category, Meteor style. Left click toggles a module, right click
 * opens its settings in the classic window, right click on a title folds the column, typing searches.
 * Positions are saved in ~/Arsenic/panels.json.
 */
public class PanelClickGui extends GuiScreen {

    /** True while the classic window was opened from a panel; the next ClickGUI key press clears it. */
    public static boolean windowBypass;
    private static GuiScreen lastCustom;
    private static PanelClickGui instance;
    private static final Map<String, float[]> saved = new HashMap<>();
    private static boolean stateLoaded;

    private static final class Panel {
        final String key, title;
        final ModuleCategory category;   // null = addons
        float x, y, scroll, maxScroll, bodyY1, bodyY2;
        boolean open = true;

        Panel(String key, String title, ModuleCategory category) {
            this.key = key;
            this.title = title;
            this.category = category;
        }
    }

    private final List<Panel> panels = new ArrayList<>();
    private Panel dragging;
    private float dragDX, dragDY;
    private String search = "";
    private FontRendererExtension<?> fr;
    private Module hovered;

    // ---- opening ----

    /**
     * Opens what the current theme asks for: an addon's screen, or the panels.
     * @return false when the classic window should open instead
     */
    public static boolean openForTheme() {
        if (windowBypass)
            return false;
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen custom = null;
        try {
            Supplier<? extends GuiScreen> s = ThemeLayout.screen();
            if (s != null)
                custom = s.get();
        } catch (Throwable t) {
            System.err.println("[Arsenic] The theme's custom ClickGUI screen failed: " + t);
        }
        if (custom != null && !(custom instanceof ClickGuiScreen)) {
            lastCustom = custom;
            mc.displayGuiScreen(custom);
            return true;
        }
        if (ThemeLayout.current().type == ThemeLayout.Type.PANELS) {
            if (instance == null)
                instance = new PanelClickGui();
            mc.displayGuiScreen(instance);
            return true;
        }
        return false;
    }

    /** The panels or an addon's ClickGUI screen. */
    public static boolean isOurs(GuiScreen screen) {
        return screen instanceof PanelClickGui || (screen != null && screen == lastCustom);
    }

    // ---- panels ----

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        loadState();
        remember();
        panels.clear();
        ThemeLayout l = ThemeLayout.current();
        float x = 6f, y = 6f;
        for (ModuleCategory c : ModuleCategory.values()) {
            if (c == ModuleCategory.SEARCH || c.getContents().isEmpty())
                continue;
            add(new Panel(c.name(), c.getName(), c), l, x, y);
            x += l.panelWidth + l.panelGap;
            if (x + l.panelWidth > width - 6f) {
                x = 6f;
                y += 160f;
            }
        }
        if (!addonModules().isEmpty())
            add(new Panel("ADDONS", "Addons", null), l, x, y);
    }

    private void add(Panel p, ThemeLayout l, float x, float y) {
        float[] s = saved.get(p.key);
        if (s != null) {
            p.x = s[0];
            p.y = s[1];
            p.open = s[2] > 0.5f;
        } else {
            p.x = x;
            p.y = y;
        }
        panels.add(p);
    }

    private List<Module> addonModules() {
        List<Module> out = new ArrayList<>();
        for (Module m : Arsenic.getArsenic().getModuleManager().getModules())
            if (m.isAddon())
                out.add(m);
        return out;
    }

    private List<Module> modules(Panel p) {
        Collection<Module> src = p.category != null ? p.category.getContents() : addonModules();
        String q = search.toLowerCase(Locale.ROOT);
        List<Module> out = new ArrayList<>();
        for (Module m : src) {
            if (!tierShown(m))
                continue;
            if (!q.isEmpty() && !m.getName().toLowerCase(Locale.ROOT).contains(q))
                continue;
            out.add(m);
        }
        out.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    private static boolean tierShown(Module m) {
        try {
            return GuiStyle.get().isTierShown(m.getTier());
        } catch (RuntimeException e) {
            return true;
        }
    }

    // ---- drawing ----

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        try {
            Arsenic.getArsenic().getThemeManager().pollCustomThemes();
        } catch (RuntimeException ignored) {
        }
        ThemeLayout l = ThemeLayout.current();
        if (l.type != ThemeLayout.Type.PANELS && ThemeLayout.screen() == null) {
            // the theme changed to one that uses the classic window
            mc.displayGuiScreen(Arsenic.getArsenic().getClickGuiScreen());
            return;
        }

        Backdrop.draw(width, height);
        setupFont(l);
        hovered = null;
        try {
            if (dragging != null) {
                dragging.x = mouseX - dragDX;
                dragging.y = mouseY - dragDY;
            }
            for (Panel p : panels)
                drawPanel(p, l, mouseX, mouseY);
            drawFooter(l);
        } finally {
            fr.resetScale();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
        Backdrop.overlay();
    }

    private void setupFont(ThemeLayout l) {
        FontRendererExtension<?> f = null;
        boolean vanilla = "VANILLA".equals(String.valueOf(ThemeStyle.current().font));
        if (!vanilla) {
            try {
                f = Arsenic.getInstance().getFonts().Inter.getFontRendererExtension();
            } catch (RuntimeException e) {
                f = null;
            }
        }
        if (f == null)
            f = VanillaFontRenderer.of(mc.fontRendererObj).getFontRendererExtension();
        fr = f;
        fr.setScale(1f);
        float h = fr.getHeight("Ag");
        fr.setScale(h > 0.5f ? l.textSize / h : 1f);
    }

    private void drawPanel(Panel p, ThemeLayout l, int mouseX, int mouseY) {
        float w = l.panelWidth, hh = l.headerHeight, rh = l.rowHeight, r = l.roundness;
        p.x = Math.max(20f - w, Math.min(width - 20f, p.x));
        p.y = Math.max(0f, Math.min(height - hh, p.y));
        float x1 = p.x, y1 = p.y, x2 = x1 + w;

        List<Module> mods = p.open ? modules(p) : Collections.<Module>emptyList();
        float avail = l.maxHeight > 0f ? l.maxHeight : height - y1 - hh - 14f;
        avail = Math.max(rh, avail);
        float full = mods.size() * rh;
        float bodyH = Math.min(full, avail);
        p.maxScroll = Math.max(0f, full - bodyH);
        p.scroll = Math.max(0f, Math.min(p.maxScroll, p.scroll));
        p.bodyY1 = y1 + hh;
        p.bodyY2 = p.bodyY1 + bodyH;

        int main = ThemeManager.getMainColor();
        int header = l.headerColor != null ? l.headerColor : main | 0xFF000000;
        int accent = l.enabledColor != null ? l.enabledColor : main | 0xFF000000;
        ThemeLayout.PanelPainter painter = ThemeLayout.painter();

        // body
        if (bodyH > 0f) {
            boolean done = false;
            if (painter != null) {
                try {
                    done = painter.panelBackground(x1, p.bodyY1, x2, p.bodyY2);
                } catch (Throwable t) {
                    ThemeLayout.disablePainter();
                }
            }
            if (!done)
                rect(x1, p.bodyY1, x2, p.bodyY2, r, l.rowColor, false, true);
        }

        // header
        boolean headerHover = inside(mouseX, mouseY, x1, y1, x2, y1 + hh);
        boolean headerDone = false;
        if (painter != null) {
            try {
                headerDone = painter.header(x1, y1, x2, y1 + hh, p.title, p.open, headerHover);
            } catch (Throwable t) {
                ThemeLayout.disablePainter();
            }
        }
        if (!headerDone) {
            rect(x1, y1, x2, y1 + hh, r, header, true, bodyH <= 0f);
            if (headerHover)
                DrawUtils.drawRect(x1, y1, x2, y1 + hh, 0x14FFFFFF);
            float pad = 5f;
            text(p.title, alignX(l.textAlign, x1, x2, pad, p.title), y1 + hh / 2f, l.headerText, l.textShadow);
            if (l.collapsible) {
                String sign = p.open ? "-" : "+";
                text(sign, x2 - pad - fr.getWidth(sign), y1 + hh / 2f, l.headerText, l.textShadow);
            }
        }

        // rows
        if (bodyH > 0f) {
            scissor(x1, p.bodyY1, x2, p.bodyY2);
            float y = p.bodyY1 - p.scroll;
            boolean overBody = inside(mouseX, mouseY, x1, p.bodyY1, x2, p.bodyY2);
            for (Module m : mods) {
                float ry1 = y, ry2 = y + rh;
                y += rh;
                if (ry2 < p.bodyY1 || ry1 > p.bodyY2)
                    continue;
                boolean hover = overBody && mouseY >= ry1 && mouseY < ry2;
                if (hover)
                    hovered = m;
                boolean done = false;
                if (painter != null) {
                    try {
                        done = painter.row(x1, ry1, x2, ry2, m, hover);
                    } catch (Throwable t) {
                        ThemeLayout.disablePainter();
                    }
                }
                if (done)
                    continue;
                boolean on = m.isEnabled();
                float pad = 5f;
                int color = l.text;
                if (on) {
                    switch (l.enabledStyle) {
                        case FILL:
                            DrawUtils.drawRect(x1, ry1, x2, ry2, l.enabledColor != null ? accent : (main & 0xFFFFFF) | 0xB0000000);
                            color = l.headerText;
                            break;
                        case BAR:
                            DrawUtils.drawRect(x1, ry1, x1 + 2f, ry2, accent);
                            pad += 2f;
                            color = 0xFFFFFFFF;
                            break;
                        default:
                            color = accent;
                            break;
                    }
                }
                if (hover)
                    DrawUtils.drawRect(x1, ry1, x2, ry2, l.rowHover);
                text(m.getName(), alignX(l.textAlign, x1, x2, pad, m.getName()), (ry1 + ry2) / 2f, color, l.textShadow);
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            if (p.maxScroll > 0f) {
                float thumbH = Math.max(6f, bodyH * bodyH / full);
                float thumbY = p.bodyY1 + (bodyH - thumbH) * (p.scroll / p.maxScroll);
                DrawUtils.drawRect(x2 - 1.5f, thumbY, x2 - 0.5f, thumbY + thumbH, 0x80FFFFFF);
            }
        }

        // outline
        if (l.outline > 0f) {
            int oc = l.outlineColor != null ? l.outlineColor : header;
            float y2 = bodyH > 0f ? p.bodyY2 : y1 + hh, o = l.outline;
            if (r > 0.01f) {
                DrawUtils.drawRoundedOutline(x1, y1, x2, y2, r, o, oc);
            } else {
                DrawUtils.drawRect(x1 - o, y1 - o, x2 + o, y1, oc);
                DrawUtils.drawRect(x1 - o, y2, x2 + o, y2 + o, oc);
                DrawUtils.drawRect(x1 - o, y1, x1, y2, oc);
                DrawUtils.drawRect(x2, y1, x2 + o, y2, oc);
            }
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawFooter(ThemeLayout l) {
        String hint = hovered != null && hovered.getDescription() != null && !hovered.getDescription().isEmpty()
                ? hovered.getName() + ": " + hovered.getDescription()
                : "Left click: toggle   Right click: settings   Right click a title: fold   Type: search   Home: reset";
        text(hint, 4f, height - 7f, 0xFFDDDDDD, true);
        if (!search.isEmpty()) {
            String s = "Search: " + search;
            float sw = fr.getWidth(s) + 12f;
            float sx = (width - sw) / 2f;
            DrawUtils.drawRect(sx, height - 30f, sx + sw, height - 16f, 0xC0000000);
            text(s, sx + 6f, height - 23f, 0xFFFFFFFF, true);
        }
    }

    /** A rectangle with rounded corners on the top, the bottom, both or neither. */
    private void rect(float x1, float y1, float x2, float y2, float r, int color, boolean roundTop, boolean roundBottom) {
        r = Math.min(r, Math.min(x2 - x1, y2 - y1) / 2f);
        if (r <= 0.01f || (!roundTop && !roundBottom)) {
            DrawUtils.drawRect(x1, y1, x2, y2, color);
        } else if (roundTop && roundBottom) {
            DrawUtils.drawRoundedRect(x1, y1, x2, y2, r, color);
        } else {
            // draw a taller rounded shape and cut off the side that must stay square
            scissor(x1, y1, x2, y2);
            if (roundTop)
                DrawUtils.drawRoundedRect(x1, y1, x2, y2 + r, r, color);
            else
                DrawUtils.drawRoundedRect(x1, y1 - r, x2, y2, r, color);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void scissor(float x1, float y1, float x2, float y2) {
        int f = new ScaledResolution(mc).getScaleFactor();
        int sx = (int) Math.floor(x1 * f);
        int sy = (int) Math.floor(mc.displayHeight - y2 * f);
        int sw = (int) Math.ceil((x2 - x1) * f);
        int sh = (int) Math.ceil((y2 - y1) * f);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(sx, sy, Math.max(0, sw), Math.max(0, sh));
    }

    private float alignX(ThemeLayout.TextAlign a, float x1, float x2, float pad, String s) {
        float w = fr.getWidth(s);
        switch (a) {
            case LEFT:
                return x1 + pad;
            case RIGHT:
                return x2 - pad - w;
            default:
                return (x1 + x2 - w) / 2f;
        }
    }

    @SuppressWarnings("unchecked")
    private void text(String s, float x, float cy, int color, boolean shadow) {
        if (shadow)
            fr.drawStringWithShadow(s, x, cy, color, fr.CENTREY);
        else
            fr.drawString(s, x, cy, color, fr.CENTREY);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static boolean inside(float mx, float my, float x1, float y1, float x2, float y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    // ---- input ----

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        ThemeLayout l = ThemeLayout.current();
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i);
            float x2 = p.x + l.panelWidth;
            if (inside(mouseX, mouseY, p.x, p.y, x2, p.y + l.headerHeight)) {
                toFront(p);
                if (button == 0) {
                    dragging = p;
                    dragDX = mouseX - p.x;
                    dragDY = mouseY - p.y;
                } else if (button == 1 && l.collapsible) {
                    p.open = !p.open;
                    p.scroll = 0f;
                    SoundUtils.tick();
                }
                return;
            }
            if (p.open && inside(mouseX, mouseY, p.x, p.bodyY1, x2, p.bodyY2)) {
                toFront(p);
                List<Module> mods = modules(p);
                int index = (int) ((mouseY - p.bodyY1 + p.scroll) / l.rowHeight);
                if (index >= 0 && index < mods.size()) {
                    Module m = mods.get(index);
                    if (button == 0) {
                        m.toggle();
                        SoundUtils.tick();
                    } else if (button == 1) {
                        openSettings(m);
                    }
                }
                return;
            }
        }
    }

    private void toFront(Panel p) {
        panels.remove(p);
        panels.add(p);
    }

    /** Opens the classic window on this module (through its search box), where every setting can be edited. */
    private void openSettings(Module m) {
        remember();
        saveState();
        windowBypass = true;
        GuiScreen gui = Arsenic.getArsenic().getClickGuiScreen();
        mc.displayGuiScreen(gui);
        if (!(gui instanceof ClickGuiScreen))
            return;
        try {
            java.lang.reflect.Field f = ClickGuiScreen.class.getDeclaredField("searchComponent");
            f.setAccessible(true);
            Object sc = f.get(gui);
            if (sc instanceof SearchComponent) {
                ((SearchComponent) sc).setQuery(m.getName());
                ((ClickGuiScreen) gui).setCmcc((ModuleCategoryComponent) sc);
            }
        } catch (Throwable t) {
            System.err.println("[Arsenic] Could not open " + m.getName() + " in the window: " + t);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (dragging != null) {
            dragging = null;
            remember();
            saveState();
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0)
            return;
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        ThemeLayout l = ThemeLayout.current();
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i);
            if (inside(mx, my, p.x, p.y, p.x + l.panelWidth, Math.max(p.bodyY2, p.y + l.headerHeight))) {
                p.scroll -= Integer.signum(wheel) * l.rowHeight * 2f;
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (!search.isEmpty())
                search = "";
            else
                mc.displayGuiScreen(null);
            return;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (!search.isEmpty())
                search = search.substring(0, search.length() - 1);
            return;
        }
        if (keyCode == Keyboard.KEY_HOME) {
            saved.clear();
            saveState();
            initGui();
            return;
        }
        int guiKey = 0;
        try {
            guiKey = GuiStyle.get().getClickGuiKey();
        } catch (RuntimeException ignored) {
        }
        if (keyCode == guiKey)
            return;
        if (typedChar >= 32 && typedChar != 127 && search.length() < 24)
            search += typedChar;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        dragging = null;
        remember();
        saveState();
        try {
            Arsenic.getArsenic().getConfigManager().saveConfig();
        } catch (RuntimeException e) {
            System.err.println("[Arsenic] Could not save the config: " + e);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ---- panel positions ----

    private static File stateFile() {
        return new File(FileUtils.getArsenicFolderDirAsFile(), "panels.json");
    }

    private void remember() {
        for (Panel p : panels)
            saved.put(p.key, new float[]{p.x, p.y, p.open ? 1f : 0f});
    }

    private static void loadState() {
        if (stateLoaded)
            return;
        stateLoaded = true;
        File f = stateFile();
        if (!f.isFile())
            return;
        try {
            JsonObject o = new JsonParser().parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                JsonObject p = e.getValue().getAsJsonObject();
                saved.put(e.getKey(), new float[]{p.get("x").getAsFloat(), p.get("y").getAsFloat(),
                        p.get("open").getAsBoolean() ? 1f : 0f});
            }
        } catch (Exception e) {
            System.err.println("[Arsenic] Could not read panels.json: " + e);
        }
    }

    private static void saveState() {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, float[]> e : saved.entrySet()) {
            JsonObject p = new JsonObject();
            p.addProperty("x", e.getValue()[0]);
            p.addProperty("y", e.getValue()[1]);
            p.addProperty("open", e.getValue()[2] > 0.5f);
            o.add(e.getKey(), p);
        }
        try {
            Files.write(stateFile().toPath(), o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("[Arsenic] Could not save panels.json: " + e);
        }
    }
}
