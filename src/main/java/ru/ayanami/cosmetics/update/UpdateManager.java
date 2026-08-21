package ru.ayanami.cosmetics.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.ayanami.cosmetics.TweakOs;
import ru.ayanami.cosmetics.catalog.CatalogManager;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Free GitHub-based auto updater for base_pack.zip and catalog.zip.
 * Reads version.json from a configurable URL (default: raw GitHub path).
 */
public final class UpdateManager {

    /** Override later via config if needed. */
    public static String VERSION_URL =
            "https://raw.githubusercontent.com/aqzsshi/AyanamiCosmetics/main/update/version.json";

    private static String localBaseVersion = "0";
    private static String localCatalogVersion = "0";
    private static String lastStatus = "Idle";

    private UpdateManager() {
    }

    public static String getLastStatus() {
        return lastStatus;
    }

    public static void loadLocalVersions() {
        File file = new File(CatalogManager.getModConfigDir(), "local_version.json");
        if (!file.isFile()) {
            return;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            if (json.has("base_pack")) {
                localBaseVersion = json.get("base_pack").getAsString();
            }
            if (json.has("catalog")) {
                localCatalogVersion = json.get("catalog").getAsString();
            }
        } catch (Exception e) {
            TweakOs.LOGGER.warn("[TweakOs] Failed to read local_version.json: {}", e.toString());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void saveLocalVersions() {
        File file = new File(CatalogManager.getModConfigDir(), "local_version.json");
        try {
            FileOutputStream out = new FileOutputStream(file);
            String json = "{\n  \"base_pack\": \"" + localBaseVersion + "\",\n  \"catalog\": \"" + localCatalogVersion + "\"\n}\n";
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Exception e) {
            TweakOs.LOGGER.warn("[TweakOs] Failed to save local_version.json: {}", e.toString());
        }
    }

    /**
     * Runs on a background thread — never call from client render thread without care.
     */
    public static void checkAndUpdateAsync(final Runnable onDone) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    checkAndUpdate();
                } finally {
                    if (onDone != null) {
                        net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(onDone);
                    }
                }
            }
        }, "TweakOs-Updater");
        t.setDaemon(true);
        t.start();
    }

    public static synchronized void checkAndUpdate() {
        loadLocalVersions();
        lastStatus = "Checking...";
        TweakOs.LOGGER.info("[TweakOs] Checking updates: {}", VERSION_URL);
        try {
            String raw = downloadString(VERSION_URL);
            if (raw == null || raw.trim().isEmpty()) {
                lastStatus = "No version.json (ok if not set up yet)";
                return;
            }
            JsonObject json = new JsonParser().parse(raw).getAsJsonObject();
            boolean changed = false;

            if (json.has("base_pack") && json.has("base_pack_url")) {
                String remote = json.get("base_pack").getAsString();
                if (!remote.equals(localBaseVersion)) {
                    lastStatus = "Downloading base pack...";
                    File target = CatalogManager.getBasePackFile();
                    if (downloadFile(json.get("base_pack_url").getAsString(), target)) {
                        localBaseVersion = remote;
                        changed = true;
                        TweakOs.LOGGER.info("[TweakOs] Updated base_pack to {}", remote);
                    }
                }
            }

            if (json.has("catalog") && json.has("catalog_url")) {
                String remote = json.get("catalog").getAsString();
                if (!remote.equals(localCatalogVersion)) {
                    lastStatus = "Downloading catalog...";
                    File zip = new File(CatalogManager.getModConfigDir(), "catalog_download.zip");
                    if (downloadFile(json.get("catalog_url").getAsString(), zip)) {
                        unzipTo(zip, CatalogManager.getCatalogDir());
                        localCatalogVersion = remote;
                        changed = true;
                        TweakOs.LOGGER.info("[TweakOs] Updated catalog to {}", remote);
                    }
                }
            }

            if (changed) {
                saveLocalVersions();
                CatalogManager.reload();
                lastStatus = "Updated";
            } else {
                lastStatus = "Up to date";
            }
        } catch (Exception e) {
            lastStatus = "Update failed";
            TweakOs.LOGGER.warn("[TweakOs] Update check failed: {}", e.toString());
        }
    }

    private static String downloadString(String urlString) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "TweakOs/" + TweakOs.VERSION);
        conn.connect();
        int code = conn.getResponseCode();
        if (code != 200) {
            TweakOs.LOGGER.warn("[TweakOs] HTTP {} for {}", code, urlString);
            return null;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        reader.close();
        conn.disconnect();
        return sb.toString();
    }

    private static boolean downloadFile(String urlString, File target) {
        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            if (target.getParentFile() != null) {
                target.getParentFile().mkdirs();
            }
            conn = (HttpURLConnection) new URL(urlString).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "TweakOs/" + TweakOs.VERSION);
            conn.connect();
            if (conn.getResponseCode() != 200) {
                return false;
            }
            in = new BufferedInputStream(conn.getInputStream());
            out = new FileOutputStream(target);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            return true;
        } catch (Exception e) {
            TweakOs.LOGGER.warn("[TweakOs] Download failed {}: {}", urlString, e.toString());
            return false;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Exception ignored) {
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void unzipTo(File zipFile, File destDir) throws Exception {
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
        ZipEntry entry;
        byte[] buf = new byte[8192];
        while ((entry = zis.getNextEntry()) != null) {
            File outFile = new File(destDir, entry.getName());
            if (entry.isDirectory()) {
                outFile.mkdirs();
            } else {
                if (outFile.getParentFile() != null) {
                    outFile.getParentFile().mkdirs();
                }
                FileOutputStream fos = new FileOutputStream(outFile);
                int n;
                while ((n = zis.read(buf)) >= 0) {
                    fos.write(buf, 0, n);
                }
                fos.close();
            }
            zis.closeEntry();
        }
        zis.close();
    }
}
