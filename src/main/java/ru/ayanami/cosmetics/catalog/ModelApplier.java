package ru.ayanami.cosmetics.catalog;

import ru.ayanami.cosmetics.ResourcePackManager;
import ru.ayanami.cosmetics.TweakOS;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Applies catalog model + selected color texture into the work_pack (server RP clone).
 */
public final class ModelApplier {

    private ModelApplier() {
    }

    public static boolean apply(CatalogManager.CatalogEntry entry) {
        return apply(entry, entry == null ? null : entry.selectedVariantId);
    }

    public static boolean apply(CatalogManager.CatalogEntry entry, @Nullable String variantId) {
        if (entry == null || entry.replacePath == null || entry.replacePath.trim().isEmpty()) {
            TweakOS.LOGGER.warn("[TweakOS] Catalog entry missing replacePath");
            return false;
        }
        CatalogManager.ensureWorkPackMeta();
        ServerPackClone.ensureClonedFromServer();

        if (variantId != null && !variantId.isEmpty()) {
            entry.selectedVariantId = variantId;
        }
        CatalogManager.ColorVariant variant = entry.getSelectedVariant();

        try {
            File work = CatalogManager.getWorkPackDir();

            if (entry.modelFile != null && entry.modelFile.isFile()) {
                File dest = new File(work, normalizePath(entry.replacePath));
                if (dest.getParentFile() != null) {
                    dest.getParentFile().mkdirs();
                }
                copyFile(entry.modelFile, dest);
            }

            if (variant != null) {
                File texSrc = variant.resolveTexture(entry.folder);
                String texDestPath = entry.replaceTexture;
                if (texDestPath == null || texDestPath.trim().isEmpty()) {
                    // Default: store under tweakos catalog assets; also copy next to model if replaceTexture empty
                    texDestPath = "assets/tweakos_catalog/" + entry.id + "/" + variant.id + ".png";
                }
                if (texSrc != null && texSrc.isFile()) {
                    File texDest = new File(work, normalizePath(texDestPath));
                    if (texDest.getParentFile() != null) {
                        texDest.getParentFile().mkdirs();
                    }
                    copyFile(texSrc, texDest);
                }
            }

            // Copy any loose pngs from entry root except preview
            File[] files = entry.folder.listFiles();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    File f = files[i];
                    String n = f.getName().toLowerCase();
                    if (f.isFile() && n.endsWith(".png") && !"preview.png".equals(n) && !"pack.png".equals(n)) {
                        File texDest = new File(work, "assets/tweakos_catalog/" + entry.id + "/" + f.getName());
                        if (texDest.getParentFile() != null) {
                            texDest.getParentFile().mkdirs();
                        }
                        copyFile(f, texDest);
                    }
                }
            }

            ResourcePackManager.ensureOverridePackInStack();
            ResourcePackManager.reloadResourcesFromGui();
            TweakOS.LOGGER.info("[TweakOS] Applied {} variant {} -> {}", entry.id,
                    variant == null ? "-" : variant.id, entry.replacePath);
            return true;
        } catch (Exception e) {
            TweakOS.LOGGER.warn("[TweakOS] Failed to apply {}: {}", entry.id, e.toString());
            return false;
        }
    }

    private static String normalizePath(String path) {
        String p = path.replace('\\', '/');
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        return p;
    }

    private static void copyFile(File from, File to) throws Exception {
        InputStream in = new FileInputStream(from);
        OutputStream out = new FileOutputStream(to);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        in.close();
        out.close();
    }
}
