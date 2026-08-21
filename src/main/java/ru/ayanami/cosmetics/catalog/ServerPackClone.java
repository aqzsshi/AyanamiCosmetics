package ru.ayanami.cosmetics.catalog;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.resources.IResourcePack;
import ru.ayanami.cosmetics.ResourcePackManager;
import ru.ayanami.cosmetics.ResourcePackOverride;
import ru.ayanami.cosmetics.TweakOS;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Maintains config/tweakos/work_pack as a writable clone of the server resource pack.
 * Cosmetics edits are applied only to this clone; the real server pack stays untouched.
 */
public final class ServerPackClone {

    private static boolean clonedOnce;
    private static String lastSourceName = "";

    private ServerPackClone() {
    }

    public static File getWorkPackDir() {
        return CatalogManager.getWorkPackDir();
    }

    public static boolean hasClone() {
        File assets = new File(getWorkPackDir(), "assets");
        return assets.isDirectory();
    }

    public static synchronized boolean ensureClonedFromServer() {
        CatalogManager.ensureWorkPackMeta();
        IResourcePack server = resolveRealServerPack();
        if (server == null) {
            TweakOS.LOGGER.info("[TweakOS] No server pack to clone yet — using empty work_pack");
            return hasClone();
        }
        String name = server.getPackName();
        if (clonedOnce && name.equals(lastSourceName) && hasClone()) {
            return true;
        }
        return forceCloneFromServer();
    }

    public static synchronized boolean forceCloneFromServer() {
        CatalogManager.ensureWorkPackMeta();
        IResourcePack server = resolveRealServerPack();
        if (server == null) {
            return false;
        }
        File dest = getWorkPackDir();
        try {
            clearAssets(dest);
            if (server instanceof FolderResourcePack) {
                File src = findFolderSource(server);
                if (src != null && src.isDirectory()) {
                    copyDirectory(src, dest);
                }
            } else if (server instanceof FileResourcePack) {
                File zip = findZipSource(server);
                if (zip != null && zip.isFile()) {
                    unzip(zip, dest);
                } else {
                    // Fallback: stream known domains is hard; try reflection for file field.
                    TweakOS.LOGGER.warn("[TweakOS] Could not locate server pack zip file for clone");
                    return false;
                }
            } else {
                TweakOS.LOGGER.warn("[TweakOS] Unsupported server pack type: {}", server.getClass().getName());
                return false;
            }
            CatalogManager.ensureWorkPackMeta();
            clonedOnce = true;
            lastSourceName = server.getPackName();
            TweakOS.LOGGER.info("[TweakOS] Cloned server pack into work_pack ({})", lastSourceName);
            ResourcePackManager.ensureOverridePackInStack();
            return true;
        } catch (Exception e) {
            TweakOS.LOGGER.warn("[TweakOS] Server pack clone failed: {}", e.toString());
            return false;
        }
    }

    private static IResourcePack resolveRealServerPack() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getResourcePackRepository() == null) {
            return null;
        }
        IResourcePack pack = mc.getResourcePackRepository().getServerResourcePack();
        if (pack instanceof ResourcePackOverride) {
            return ((ResourcePackOverride) pack).getBasePack();
        }
        return pack;
    }

    private static File findFolderSource(IResourcePack pack) {
        try {
            java.lang.reflect.Field[] fields = pack.getClass().getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                fields[i].setAccessible(true);
                Object v = fields[i].get(pack);
                if (v instanceof File && ((File) v).isDirectory()) {
                    return (File) v;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static File findZipSource(IResourcePack pack) {
        try {
            java.lang.reflect.Field[] fields = pack.getClass().getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                fields[i].setAccessible(true);
                Object v = fields[i].get(pack);
                if (v instanceof File && ((File) v).isFile()) {
                    return (File) v;
                }
                if (v instanceof ZipFile) {
                    // cannot get path easily
                }
            }
            // FileResourcePack in 1.12 stores resourcePackFile via AbstractResourcePack
            Class<?> c = pack.getClass();
            while (c != null) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField("resourcePackFile");
                    f.setAccessible(true);
                    Object v = f.get(pack);
                    if (v instanceof File) {
                        return (File) v;
                    }
                } catch (NoSuchFieldException ignored) {
                }
                c = c.getSuperclass();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void clearAssets(File dest) {
        File assets = new File(dest, "assets");
        if (assets.isDirectory()) {
            deleteRecursive(assets);
        }
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (int i = 0; i < kids.length; i++) {
                    deleteRecursive(kids[i]);
                }
            }
        }
        f.delete();
    }

    private static void copyDirectory(File src, File dest) throws Exception {
        if (!dest.exists()) {
            dest.mkdirs();
        }
        File[] kids = src.listFiles();
        if (kids == null) {
            return;
        }
        for (int i = 0; i < kids.length; i++) {
            File child = kids[i];
            File target = new File(dest, child.getName());
            if (child.isDirectory()) {
                copyDirectory(child, target);
            } else {
                copyFile(child, target);
            }
        }
    }

    private static void unzip(File zip, File dest) throws Exception {
        ZipFile zf = new ZipFile(zip);
        try {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry entry = en.nextElement();
                String name = entry.getName();
                if (name.contains("..")) {
                    continue;
                }
                File out = new File(dest, name);
                if (entry.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                if (out.getParentFile() != null) {
                    out.getParentFile().mkdirs();
                }
                InputStream in = zf.getInputStream(entry);
                OutputStream os = new FileOutputStream(out);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    os.write(buf, 0, n);
                }
                os.close();
                in.close();
            }
        } finally {
            zf.close();
        }
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

    public static void resetCloneFlag() {
        clonedOnce = false;
        lastSourceName = "";
    }
}
