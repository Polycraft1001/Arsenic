package arsenic.gui.themes.style;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Draws a theme's custom background (IMAGE, COLOR, GRADIENT; NONE draws nothing) over the whole window, in
 * window pixels, so it fits every screen and GUI scale. The image is reloaded when its file changes.
 */
public final class ThemeBackground {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static ResourceLocation texture;
    private static String loadedKey, failedKey;
    private static int imgW = 1, imgH = 1;

    private ThemeBackground() {}

    public static void draw(ThemeLayout l) {
        int w = mc.displayWidth, h = mc.displayHeight;
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, w, h, 0.0D, -1.0D, 1.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        try {
            switch (l.background) {
                case COLOR:
                    quad(w, h, l.bgColor, l.bgColor);
                    break;
                case GRADIENT:
                    quad(w, h, l.bgTop, l.bgBottom);
                    break;
                case IMAGE:
                    if (image(l, w, h)) {
                        if (l.dim > 0.001f) {
                            int dim = ((int) (l.dim * 255f) & 0xFF) << 24;
                            quad(w, h, dim, dim);
                        }
                    } else {
                        quad(w, h, 0xC0000000, 0xC0000000);   // keep the GUI readable without the picture
                    }
                    break;
                default:
                    break;
            }
        } catch (RuntimeException e) {
            System.err.println("[Arsenic] Theme background failed: " + e);
        } finally {
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
    }

    private static boolean image(ThemeLayout l, int w, int h) {
        File f = l.image;
        if (f == null)
            return false;
        String key = f.getAbsolutePath() + ':' + f.lastModified() + ':' + f.length();
        if (!key.equals(loadedKey)) {
            if (key.equals(failedKey))
                return false;
            try {
                BufferedImage img = ImageIO.read(f);
                if (img == null)
                    throw new IllegalArgumentException("not a PNG or JPG image");
                if (texture != null)
                    mc.getTextureManager().deleteTexture(texture);
                texture = mc.getTextureManager().getDynamicTextureLocation("arsenic_theme_bg", new DynamicTexture(img));
                imgW = Math.max(1, img.getWidth());
                imgH = Math.max(1, img.getHeight());
                loadedKey = key;
            } catch (Exception e) {
                failedKey = key;
                System.err.println("[Arsenic] Could not load the background image " + f + ": " + e);
                return false;
            }
        }

        float u1 = 0f, v1 = 0f, u2 = 1f, v2 = 1f;
        if (l.fit == ThemeLayout.Fit.COVER) {
            float screen = w / (float) h, pic = imgW / (float) imgH;
            if (pic > screen) {
                float visible = screen / pic;
                u1 = (1f - visible) / 2f;
                u2 = 1f - u1;
            } else {
                float visible = pic / screen;
                v1 = (1f - visible) / 2f;
                v2 = 1f - v1;
            }
        }

        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color(1f, 1f, 1f, 1f);
        mc.getTextureManager().bindTexture(texture);
        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(0, h, 0).tex(u1, v2).endVertex();
        wr.pos(w, h, 0).tex(u2, v2).endVertex();
        wr.pos(w, 0, 0).tex(u2, v1).endVertex();
        wr.pos(0, 0, 0).tex(u1, v1).endVertex();
        t.draw();
        return true;
    }

    private static void quad(int w, int h, int top, int bottom) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(w, 0, 0).color(top >> 16 & 255, top >> 8 & 255, top & 255, top >>> 24).endVertex();
        wr.pos(0, 0, 0).color(top >> 16 & 255, top >> 8 & 255, top & 255, top >>> 24).endVertex();
        wr.pos(0, h, 0).color(bottom >> 16 & 255, bottom >> 8 & 255, bottom & 255, bottom >>> 24).endVertex();
        wr.pos(w, h, 0).color(bottom >> 16 & 255, bottom >> 8 & 255, bottom & 255, bottom >>> 24).endVertex();
        t.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }
}
