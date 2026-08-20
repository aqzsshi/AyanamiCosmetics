package ru.ayanami.cosmetics;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-pack slot wrapper: checks override packs in priority order, then falls back to the real server pack.
 */
public class ResourcePackOverride implements IResourcePack {

    private final List<IResourcePack> overridePacks;
    private final List<String> overrideNames;
    private final IResourcePack basePack;

    public ResourcePackOverride(List<IResourcePack> overridePacks, List<String> overrideNames, IResourcePack basePack) {
        this.overridePacks = overridePacks == null
                ? Collections.<IResourcePack>emptyList()
                : new ArrayList<IResourcePack>(overridePacks);
        this.overrideNames = overrideNames == null
                ? Collections.<String>emptyList()
                : new ArrayList<String>(overrideNames);
        this.basePack = basePack;
    }

    /** Legacy single-pack constructor. */
    public ResourcePackOverride(IResourcePack overridePack, IResourcePack basePack, String overrideName) {
        this(
                Collections.singletonList(overridePack),
                Collections.singletonList(overrideName == null ? "override" : overrideName),
                basePack
        );
    }

    public List<IResourcePack> getOverridePacks() {
        return Collections.unmodifiableList(overridePacks);
    }

    public IResourcePack getOverridePack() {
        return overridePacks.isEmpty() ? null : overridePacks.get(0);
    }

    public IResourcePack getBasePack() {
        return basePack;
    }

    public String getOverrideName() {
        if (overrideNames.isEmpty()) {
            return "override";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < overrideNames.size(); i++) {
            if (i > 0) {
                sb.append('+');
            }
            sb.append(overrideNames.get(i));
        }
        return sb.toString();
    }

    public List<String> getOverrideNames() {
        return Collections.unmodifiableList(overrideNames);
    }

    @Override
    public InputStream getInputStream(ResourceLocation location) throws IOException {
        for (int i = 0; i < overridePacks.size(); i++) {
            IResourcePack pack = overridePacks.get(i);
            if (pack.resourceExists(location)) {
                return pack.getInputStream(location);
            }
        }
        return basePack.getInputStream(location);
    }

    @Override
    public boolean resourceExists(ResourceLocation location) {
        for (int i = 0; i < overridePacks.size(); i++) {
            if (overridePacks.get(i).resourceExists(location)) {
                return true;
            }
        }
        return basePack.resourceExists(location);
    }

    @Override
    public Set<String> getResourceDomains() {
        Set<String> domains = new HashSet<String>();
        domains.addAll(basePack.getResourceDomains());
        for (int i = 0; i < overridePacks.size(); i++) {
            domains.addAll(overridePacks.get(i).getResourceDomains());
        }
        return domains;
    }

    @Nullable
    @Override
    public <T extends IMetadataSection> T getPackMetadata(MetadataSerializer metadataSerializer, String metadataSectionName) throws IOException {
        for (int i = 0; i < overridePacks.size(); i++) {
            try {
                T meta = overridePacks.get(i).getPackMetadata(metadataSerializer, metadataSectionName);
                if (meta != null) {
                    return meta;
                }
            } catch (Exception ignored) {
            }
        }
        return basePack.getPackMetadata(metadataSerializer, metadataSectionName);
    }

    @Override
    public BufferedImage getPackImage() throws IOException {
        for (int i = 0; i < overridePacks.size(); i++) {
            try {
                return overridePacks.get(i).getPackImage();
            } catch (Exception ignored) {
            }
        }
        return basePack.getPackImage();
    }

    @Override
    public String getPackName() {
        return "AyanamiCosmetics:" + getOverrideName() + "+" + basePack.getPackName();
    }
}
