package ru.ayanami.cosmetics;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Composite {@link IResourcePack} used as the server-pack slot entry.
 * <p>
 * Files present in {@code overridePack} win; everything else falls back to {@code basePack}
 * (the real server resource pack). This preserves vanilla ResourceManager behaviour for all
 * resource types (textures, models, blockstates, sounds, lang, fonts, shaders, etc.).
 * <p>
 * Why a wrapper instead of removing/replacing the server pack:
 * - server pack stays loaded and confirmed;
 * - only matching paths are overridden;
 * - vanilla / OptiFine reload paths keep working.
 */
public class ResourcePackOverride implements IResourcePack {

    private final IResourcePack overridePack;
    private final IResourcePack basePack;
    private final String overrideName;

    public ResourcePackOverride(IResourcePack overridePack, IResourcePack basePack, String overrideName) {
        this.overridePack = overridePack;
        this.basePack = basePack;
        this.overrideName = overrideName == null ? "override" : overrideName;
    }

    public IResourcePack getOverridePack() {
        return overridePack;
    }

    public IResourcePack getBasePack() {
        return basePack;
    }

    public String getOverrideName() {
        return overrideName;
    }

    @Override
    public InputStream getInputStream(ResourceLocation location) throws IOException {
        if (overridePack.resourceExists(location)) {
            return overridePack.getInputStream(location);
        }
        return basePack.getInputStream(location);
    }

    @Override
    public boolean resourceExists(ResourceLocation location) {
        return overridePack.resourceExists(location) || basePack.resourceExists(location);
    }

    @Override
    public Set<String> getResourceDomains() {
        Set<String> domains = new HashSet<String>();
        domains.addAll(basePack.getResourceDomains());
        domains.addAll(overridePack.getResourceDomains());
        return domains;
    }

    @Nullable
    @Override
    public <T extends IMetadataSection> T getPackMetadata(MetadataSerializer metadataSerializer, String metadataSectionName) throws IOException {
        try {
            T overrideMeta = overridePack.getPackMetadata(metadataSerializer, metadataSectionName);
            if (overrideMeta != null) {
                return overrideMeta;
            }
        } catch (Exception ignored) {
            // Invalid override mcmeta must not break the server pack.
        }
        return basePack.getPackMetadata(metadataSerializer, metadataSectionName);
    }

    @Override
    public BufferedImage getPackImage() throws IOException {
        try {
            return overridePack.getPackImage();
        } catch (Exception e) {
            return basePack.getPackImage();
        }
    }

    @Override
    public String getPackName() {
        return "AyanamiCosmetics:" + overrideName + "+" + basePack.getPackName();
    }
}
