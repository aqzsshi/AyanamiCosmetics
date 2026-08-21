package ru.ayanami.cosmetics.catalog;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Loads catalog preview.png files as DynamicTextures for the wardrobe grid.
 */
public final class PreviewTextureCache {

    private static final Map<String, ResourceLocation> CACHE = new HashMap<String, ResourceLocation>();
    private static final Map<String, DynamicTexture> TEX = new HashMap<String, DynamicTexture>();

    private PreviewTextureCache() {
    }

    @Nullable
    public static ResourceLocation getOrLoad(@Nullable File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        String key = file.getAbsolutePath() + "#" + file.lastModified();
        ResourceLocation existing = CACHE.get(key);
        if (existing != null) {
            return existing;
        }
        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                return null;
            }
            DynamicTexture dyn = new DynamicTexture(img);
            ResourceLocation loc = Minecraft.getMinecraft().getTextureManager()
                    .getDynamicTextureLocation("tweakos_preview_" + CACHE.size(), dyn);
            CACHE.put(key, loc);
            TEX.put(key, dyn);
            // Cap cache size
            if (CACHE.size() > 64) {
                Iterator<String> it = CACHE.keySet().iterator();
                if (it.hasNext()) {
                    String old = it.next();
                    CACHE.remove(old);
                    TEX.remove(old);
                }
            }
            return loc;
        } catch (Exception e) {
            return null;
        }
    }

    public static void draw(@Nullable ResourceLocation loc, int x, int y, int w, int h) {
        if (loc == null) {
            return;
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(loc);
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, w, h, w, h);
        GlStateManager.disableBlend();
    }
}
