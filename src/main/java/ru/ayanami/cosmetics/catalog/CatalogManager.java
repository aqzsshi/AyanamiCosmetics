package ru.ayanami.cosmetics.catalog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.ayanami.cosmetics.TweakOS;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Catalog under config/tweakos/catalog/&lt;id&gt;/
 * <pre>
 * meta.json
 * preview.png          — default menu render
 * model.json           — optional model override
 * textures/&lt;id&gt;.png    — color variants
 * previews/&lt;id&gt;.png    — optional per-color renders
 * </pre>
 */
public final class CatalogManager {

    public static final String CAT_HATS = "hats";
    public static final String CAT_HAND = "hand";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<CatalogEntry> ENTRIES = new ArrayList<CatalogEntry>();

    private CatalogManager() {
    }

    public static File getModConfigDir() {
        return new File(net.minecraft.client.Minecraft.getMinecraft().mcDataDir, "config/tweakos");
    }

    public static File getCatalogDir() {
        return new File(getModConfigDir(), "catalog");
    }

    /** Working copy of server RP + local model edits. */
    public static File getWorkPackDir() {
        return new File(getModConfigDir(), "work_pack");
    }

    /** Alias used by older code paths. */
    public static File getOverridePackDir() {
        return getWorkPackDir();
    }

    public static File getBasePackFile() {
        return new File(getModConfigDir(), "base_pack.zip");
    }

    public static void initFolders() {
        File root = getModConfigDir();
        if (!root.exists()) {
            root.mkdirs();
        }
        File catalog = getCatalogDir();
        if (!catalog.exists()) {
            catalog.mkdirs();
        }
        ensureWorkPackMeta();
        ensureExampleEntries();
        reload();
    }

    public static void ensureWorkPackMeta() {
        File override = getWorkPackDir();
        if (!override.exists()) {
            override.mkdirs();
        }
        File meta = new File(override, "pack.mcmeta");
        if (!meta.isFile()) {
            try {
                FileWriter w = new FileWriter(meta);
                w.write("{\"pack\":{\"pack_format\":3,\"description\":\"TweakOS work pack (server RP clone + local models)\"}}");
                w.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void ensureExampleEntries() {
        writeExample(
                "demo_cap_blue",
                "Demo Cap",
                CAT_HATS,
                "assets/minecraft/models/item/leather_helmet.json",
                "#4A7CFF",
                new String[] {"blue", "green", "orange", "pink"},
                new String[] {"#4A7CFF", "#5BD67A", "#FF9A4A", "#FF7AB8"}
        );
        writeExample(
                "demo_hand_orb",
                "Demo Hand Orb",
                CAT_HAND,
                "assets/minecraft/models/item/end_crystal.json",
                "#7AB8FF",
                new String[] {"cyan", "magenta"},
                new String[] {"#4AD6FF", "#FF4AD6"}
        );
    }

    private static void writeExample(String id, String name, String category, String replacePath,
                                     String defaultColor, String[] variantIds, String[] colors) {
        File example = new File(getCatalogDir(), id);
        File meta = new File(example, "meta.json");
        if (meta.exists()) {
            return;
        }
        example.mkdirs();
        new File(example, "textures").mkdirs();
        new File(example, "previews").mkdirs();
        try {
            FileWriter w = new FileWriter(meta);
            w.write("{\n");
            w.write("  \"id\": \"" + id + "\",\n");
            w.write("  \"name\": \"" + name + "\",\n");
            w.write("  \"category\": \"" + category + "\",\n");
            w.write("  \"replacePath\": \"" + replacePath + "\",\n");
            w.write("  \"replaceTexture\": \"\",\n");
            w.write("  \"defaultVariant\": \"" + variantIds[0] + "\",\n");
            w.write("  \"description\": \"Example. Drop preview.png and textures/<variant>.png here.\",\n");
            w.write("  \"variants\": [\n");
            for (int i = 0; i < variantIds.length; i++) {
                w.write("    {\"id\": \"" + variantIds[i] + "\", \"name\": \"" + pretty(variantIds[i])
                        + "\", \"color\": \"" + colors[i] + "\", \"texture\": \"textures/" + variantIds[i]
                        + ".png\", \"preview\": \"previews/" + variantIds[i] + ".png\"}");
                w.write(i + 1 < variantIds.length ? ",\n" : "\n");
            }
            w.write("  ]\n");
            w.write("}\n");
            w.close();
        } catch (Exception e) {
            TweakOS.LOGGER.warn("[TweakOS] Could not create example {}: {}", id, e.toString());
        }
    }

    private static String pretty(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static synchronized void reload() {
        ENTRIES.clear();
        File catalog = getCatalogDir();
        File[] folders = catalog.listFiles();
        if (folders == null) {
            return;
        }
        for (int i = 0; i < folders.length; i++) {
            File folder = folders[i];
            if (!folder.isDirectory()) {
                continue;
            }
            File metaFile = new File(folder, "meta.json");
            if (!metaFile.isFile()) {
                continue;
            }
            CatalogEntry entry = readEntry(folder, metaFile);
            if (entry != null) {
                ENTRIES.add(entry);
            }
        }
        Collections.sort(ENTRIES, new Comparator<CatalogEntry>() {
            @Override
            public int compare(CatalogEntry a, CatalogEntry b) {
                int c = a.category.compareToIgnoreCase(b.category);
                if (c != 0) {
                    return c;
                }
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        TweakOS.LOGGER.info("[TweakOS] Catalog loaded: {} entries", ENTRIES.size());
    }

    @Nullable
    private static CatalogEntry readEntry(File folder, File metaFile) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(metaFile), StandardCharsets.UTF_8));
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            CatalogEntry entry = new CatalogEntry();
            entry.id = json.has("id") ? json.get("id").getAsString() : folder.getName();
            entry.name = json.has("name") ? json.get("name").getAsString() : folder.getName();
            entry.category = json.has("category") ? json.get("category").getAsString().toLowerCase(Locale.ROOT) : "hats";
            // Migrate old category names
            if ("hat".equals(entry.category) || "head".equals(entry.category)) {
                entry.category = CAT_HATS;
            }
            if ("arms".equals(entry.category) || "weapons".equals(entry.category) || "hand_cosmetics".equals(entry.category)) {
                entry.category = CAT_HAND;
            }
            entry.replacePath = json.has("replacePath") ? json.get("replacePath").getAsString() : "";
            entry.replaceTexture = json.has("replaceTexture") ? json.get("replaceTexture").getAsString() : "";
            entry.iconItem = json.has("iconItem") ? json.get("iconItem").getAsString() : "minecraft:paper";
            entry.description = json.has("description") ? json.get("description").getAsString() : "";
            entry.defaultVariant = json.has("defaultVariant") ? json.get("defaultVariant").getAsString() : "";
            entry.folder = folder;
            entry.modelFile = new File(folder, "model.json");
            entry.previewFile = new File(folder, "preview.png");
            entry.variants = new ArrayList<ColorVariant>();
            if (json.has("variants") && json.get("variants").isJsonArray()) {
                JsonArray arr = json.getAsJsonArray("variants");
                for (int i = 0; i < arr.size(); i++) {
                    JsonElement el = arr.get(i);
                    if (!el.isJsonObject()) {
                        continue;
                    }
                    JsonObject v = el.getAsJsonObject();
                    ColorVariant cv = new ColorVariant();
                    cv.id = v.has("id") ? v.get("id").getAsString() : ("v" + i);
                    cv.name = v.has("name") ? v.get("name").getAsString() : cv.id;
                    cv.colorHex = v.has("color") ? v.get("color").getAsString() : "#888888";
                    cv.textureRel = v.has("texture") ? v.get("texture").getAsString() : ("textures/" + cv.id + ".png");
                    cv.previewRel = v.has("preview") ? v.get("preview").getAsString() : ("previews/" + cv.id + ".png");
                    entry.variants.add(cv);
                }
            }
            if (entry.variants.isEmpty()) {
                ColorVariant one = new ColorVariant();
                one.id = "default";
                one.name = "Default";
                one.colorHex = "#4A7CFF";
                one.textureRel = "texture.png";
                one.previewRel = "preview.png";
                entry.variants.add(one);
                entry.defaultVariant = "default";
            }
            if (entry.defaultVariant == null || entry.defaultVariant.isEmpty()) {
                entry.defaultVariant = entry.variants.get(0).id;
            }
            entry.selectedVariantId = entry.defaultVariant;
            return entry;
        } catch (Exception e) {
            TweakOS.LOGGER.warn("[TweakOS] Bad catalog meta in {}: {}", folder.getName(), e.toString());
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static List<CatalogEntry> getEntries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    public static List<CatalogEntry> getEntriesByCategory(String category) {
        if (category == null || "all".equalsIgnoreCase(category)) {
            return getEntries();
        }
        List<CatalogEntry> list = new ArrayList<CatalogEntry>();
        for (int i = 0; i < ENTRIES.size(); i++) {
            CatalogEntry e = ENTRIES.get(i);
            if (e.category.equalsIgnoreCase(category)) {
                list.add(e);
            }
        }
        return list;
    }

    public static List<String> listCategories() {
        List<String> cats = new ArrayList<String>();
        cats.add(CAT_HATS);
        cats.add(CAT_HAND);
        return cats;
    }

    /** Create a new catalog folder from the Add Model wizard. */
    public static boolean createUserEntry(String id, String name, String category, String replacePath,
                                          @Nullable File modelSrc, @Nullable File textureSrc, @Nullable File previewSrc,
                                          String colorHex) {
        if (id == null || id.trim().isEmpty() || replacePath == null || replacePath.trim().isEmpty()) {
            return false;
        }
        String safeId = id.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        File folder = new File(getCatalogDir(), safeId);
        if (folder.exists()) {
            return false;
        }
        folder.mkdirs();
        new File(folder, "textures").mkdirs();
        new File(folder, "previews").mkdirs();
        try {
            FileWriter w = new FileWriter(new File(folder, "meta.json"));
            w.write("{\n");
            w.write("  \"id\": \"" + safeId + "\",\n");
            w.write("  \"name\": " + GSON.toJson(name) + ",\n");
            w.write("  \"category\": \"" + category + "\",\n");
            w.write("  \"replacePath\": " + GSON.toJson(replacePath) + ",\n");
            w.write("  \"defaultVariant\": \"default\",\n");
            w.write("  \"variants\": [\n");
            w.write("    {\"id\": \"default\", \"name\": \"Default\", \"color\": \"" + colorHex
                    + "\", \"texture\": \"textures/default.png\", \"preview\": \"preview.png\"}\n");
            w.write("  ]\n");
            w.write("}\n");
            w.close();
            if (modelSrc != null && modelSrc.isFile()) {
                copyFile(modelSrc, new File(folder, "model.json"));
            }
            if (textureSrc != null && textureSrc.isFile()) {
                copyFile(textureSrc, new File(folder, "textures/default.png"));
            }
            if (previewSrc != null && previewSrc.isFile()) {
                copyFile(previewSrc, new File(folder, "preview.png"));
            }
            reload();
            return true;
        } catch (Exception e) {
            TweakOS.LOGGER.warn("[TweakOS] Failed to create catalog entry: {}", e.toString());
            return false;
        }
    }

    private static void copyFile(File from, File to) throws Exception {
        FileInputStream in = new FileInputStream(from);
        java.io.FileOutputStream out = new java.io.FileOutputStream(to);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        in.close();
        out.close();
    }

    public static final class ColorVariant {
        public String id;
        public String name;
        public String colorHex;
        public String textureRel;
        public String previewRel;

        public int parseColorArgb() {
            try {
                String h = colorHex == null ? "#888888" : colorHex.trim();
                if (h.startsWith("#")) {
                    h = h.substring(1);
                }
                int rgb = (int) Long.parseLong(h, 16);
                if (h.length() <= 6) {
                    return 0xFF000000 | rgb;
                }
                return rgb;
            } catch (Exception e) {
                return 0xFF888888;
            }
        }

        public File resolveTexture(File folder) {
            return new File(folder, textureRel.replace('\\', '/'));
        }

        public File resolvePreview(File folder) {
            File f = new File(folder, previewRel.replace('\\', '/'));
            if (f.isFile()) {
                return f;
            }
            File fallback = new File(folder, "preview.png");
            return fallback.isFile() ? fallback : f;
        }
    }

    public static final class CatalogEntry {
        public String id;
        public String name;
        public String category;
        public String replacePath;
        public String replaceTexture;
        public String iconItem;
        public String description;
        public String defaultVariant;
        public String selectedVariantId;
        public File folder;
        public File modelFile;
        public File previewFile;
        public List<ColorVariant> variants = new ArrayList<ColorVariant>();

        @Nullable
        public ColorVariant getSelectedVariant() {
            return getVariant(selectedVariantId);
        }

        @Nullable
        public ColorVariant getVariant(String vid) {
            if (variants == null) {
                return null;
            }
            for (int i = 0; i < variants.size(); i++) {
                if (variants.get(i).id.equalsIgnoreCase(vid)) {
                    return variants.get(i);
                }
            }
            return variants.isEmpty() ? null : variants.get(0);
        }

        public File resolvePreviewFile() {
            ColorVariant v = getSelectedVariant();
            if (v != null) {
                File f = v.resolvePreview(folder);
                if (f != null && f.isFile()) {
                    return f;
                }
            }
            if (previewFile != null && previewFile.isFile()) {
                return previewFile;
            }
            return null;
        }
    }
}
