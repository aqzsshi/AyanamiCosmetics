package ru.ayanami.cosmetics.catalog;

import ru.ayanami.cosmetics.TweakOs;
import ru.ayanami.cosmetics.ResourcePackManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Applies a catalog entry into the local override_pack by copying model.json
 * (and optional textures) to the replacePath so the game keeps original resource names.
 */
public final class ModelApplier {

    private ModelApplier() {
    }

    public static boolean apply(CatalogManager.CatalogEntry entry) {
        if (entry == null || entry.replacePath == null || entry.replacePath.trim().isEmpty()) {
            TweakOs.LOGGER.warn("[TweakOs] Catalog entry missing replacePath");
            return false;
        }
        if (entry.modelFile == null || !entry.modelFile.isFile()) {
            TweakOs.LOGGER.warn("[TweakOs] Catalog entry {} has no model.json — placeholder apply skipped", entry.id);
            // Still create a marker so workflow is testable.
            return writePlaceholder(entry);
        }
        try {
            File dest = new File(CatalogManager.getOverridePackDir(), normalizePath(entry.replacePath));
            if (dest.getParentFile() != null) {
                dest.getParentFile().mkdirs();
            }
            copyFile(entry.modelFile, dest);

            // Copy sibling png/json textures if present.
            File[] files = entry.folder.listFiles();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    File f = files[i];
                    String n = f.getName().toLowerCase();
                    if (f.isFile() && (n.endsWith(".png") || n.endsWith(".mcmeta")) && !"pack.png".equals(n)) {
                        File texDest = new File(CatalogManager.getOverridePackDir(), "assets/ayanami_catalog/" + entry.id + "/" + f.getName());
                        if (texDest.getParentFile() != null) {
                            texDest.getParentFile().mkdirs();
                        }
                        copyFile(f, texDest);
                    }
                }
            }

            // Ensure override folder pack is in active stack.
            String overrideName = CatalogManager.getOverridePackDir().getName();
            // Use absolute folder via installing as resource pack path name "override_pack" under config — ResourcePackManager looks in resourcepacks.
            // So we also mirror/link by adding config override as a selectable pack via a stub in resourcepacks OR load FolderResourcePack directly.
            ResourcePackManager.ensureOverridePackInStack();
            ResourcePackManager.reloadResourcesFromGui();
            TweakOs.LOGGER.info("[TweakOs] Applied catalog model {} -> {}", entry.id, entry.replacePath);
            return true;
        } catch (Exception e) {
            TweakOs.LOGGER.warn("[TweakOs] Failed to apply {}: {}", entry.id, e.toString());
            return false;
        }
    }

    private static boolean writePlaceholder(CatalogManager.CatalogEntry entry) {
        try {
            File dest = new File(CatalogManager.getOverridePackDir(), normalizePath(entry.replacePath));
            if (dest.getParentFile() != null) {
                dest.getParentFile().mkdirs();
            }
            // Do not overwrite real models with junk if replace path exists and no model — skip.
            if (dest.exists()) {
                return false;
            }
            FileOutputStream out = new FileOutputStream(dest);
            String json = "{\n  \"parent\": \"item/handheld\",\n  \"textures\": {\n    \"layer0\": \"items/diamond_sword\"\n  }\n}\n";
            out.write(json.getBytes("UTF-8"));
            out.close();
            ResourcePackManager.ensureOverridePackInStack();
            ResourcePackManager.reloadResourcesFromGui();
            return true;
        } catch (Exception e) {
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
