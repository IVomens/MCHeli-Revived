package com.ivomens.mcheli.content.pack;

import com.google.common.base.Strings;
import com.ivomens.mcheli.util.Ids;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * A resource location qualified by the addon pack it came from ({@code namespace:addonDomain|path}).
 * <p>
 * Vanilla's {@link ResourceLocation} is final in 1.21, so this is a standalone value type exposing the
 * same accessors ({@link #getNamespace()}, {@link #getPath()}); use {@link #asVanillaDomainPath()} where
 * a vanilla location is needed.
 */
public class AddonResourceLocation implements Comparable<AddonResourceLocation> {

    public static final String SHARE_DOMAIN = "<!mcheli_share_domain>";
    public static final char SEPARATOR = '|';
    public static final AddonResourceLocation EMPTY_LOCATION = new AddonResourceLocation();
    private final String namespace;
    private final String path;
    private final String addonDomain;
    private final boolean isEmpty;

    private AddonResourceLocation() {
        this.namespace = "empty";
        this.path = "empty";
        this.addonDomain = "@empty";
        this.isEmpty = true;
    }

    protected AddonResourceLocation(int unused, String... resourceName) {
        this.namespace = resourceName[0];
        this.addonDomain = resourceName[1];
        this.path = resourceName[2];
        this.isEmpty = false;
    }

    public AddonResourceLocation(String resourceName) {
        this(0, parsePath(resourceName));
    }

    /** {@code mcheli:<path>} in the built-in addon. */
    public static AddonResourceLocation builtin(String path) {
        return new AddonResourceLocation(Ids.of(path), AddonManager.BUILTIN_ADDON_DOMAIN);
    }

    public AddonResourceLocation(String resourceName, String addonDomainIn) {
        this(0, parsePath(addonDomainIn, resourceName));
    }

    public AddonResourceLocation(ResourceLocation resourceLocation, String addonDomainIn) {
        this(resourceLocation.toString(), addonDomainIn);
    }

    public AddonResourceLocation(String resourceDomainIn, String addonDomainIn, String resourcePathIn) {
        this(0, parsePath(resourceDomainIn + ":" + addonDomainIn + SEPARATOR + resourcePathIn));
    }

    private static String[] splitObjectName(String toSplit) {
        String[] ret = new String[] { "minecraft", toSplit };
        int i = toSplit.indexOf(':');
        if (i >= 0) {
            ret[1] = toSplit.substring(i + 1);
            if (i > 1) {
                ret[0] = toSplit.substring(0, i);
            }
        }
        ret[0] = ret[0].toLowerCase(Locale.ROOT);
        ret[1] = ret[1].toLowerCase(Locale.ROOT);
        return ret;
    }

    protected static String[] parsePath(String pathIn) {
        String[] spl = splitObjectName(pathIn);
        String[] ret = new String[] { spl[0], null, spl[1] };
        int i = ret[2].indexOf(SEPARATOR);
        if (i >= 0) {
            ret[1] = ret[2].substring(0, i);
            ret[2] = ret[2].substring(i + 1);
        }

        ret[1] = Strings.isNullOrEmpty(ret[1]) ? SHARE_DOMAIN : ret[1].toLowerCase(Locale.ROOT);
        if (ret[0].equals("minecraft")) {
            ret[0] = Ids.MOD_ID;
        } else if (!spl[0].equals(Ids.MOD_ID)) {
            ModLogger.get().warn("Invalid mod domain '{}', replace at '{}'. path:'{}'", ret[0], Ids.MOD_ID, ret[2]);
            ret[0] = Ids.MOD_ID;
        }

        return ret;
    }

    protected static String[] parsePath(String addonDomain, String pathIn) {
        String[] spl = splitObjectName(pathIn);
        return new String[] { spl[0], addonDomain, spl[1] };
    }

    public static AddonResourceLocation share(ResourceLocation location) {
        return share(location.getNamespace(), location.getPath());
    }

    public static AddonResourceLocation share(String modid, String location) {
        return new AddonResourceLocation(modid, SHARE_DOMAIN, location);
    }

    public String getNamespace() {
        return this.namespace;
    }

    public String getPath() {
        return this.path;
    }

    public String getAddonDomain() {
        return this.addonDomain;
    }

    public String getAddonLocation() {
        return this.addonDomain + SEPARATOR + this.path;
    }

    /**
     * The vanilla location {@code namespace:path}. The addon domain cannot be expressed in a 1.21
     * resource location ('|' is not a legal path character), so it is dropped.
     */
    public ResourceLocation asVanillaLocation() {
        return this.asVanillaDomainPath();
    }

    public ResourceLocation asVanillaDomainPath() {
        ResourceLocation rl = ResourceLocation.tryBuild(this.namespace, this.path);
        return rl != null ? rl : ResourceLocation.fromNamespaceAndPath(this.namespace, sanitize(this.path));
    }

    private static String sanitize(String path) {
        StringBuilder sb = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            sb.append(ResourceLocation.validPathChar(c) ? c : '_');
        }
        return sb.toString();
    }

    public boolean isShareDomain() {
        return this.addonDomain.equals(SHARE_DOMAIN);
    }

    public boolean isEmptyLocation() {
        return this.isEmpty || this.equals(EMPTY_LOCATION);
    }

    @Override
    public int hashCode() {
        int base = 31 * this.namespace.hashCode() + this.path.hashCode();
        return 31 * (31 * base + this.addonDomain.hashCode()) + Boolean.hashCode(this.isEmpty);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other instanceof AddonResourceLocation location) {
            return location.namespace.equals(this.namespace) && location.path.equals(this.path)
                    && location.addonDomain.equals(this.addonDomain) && location.isEmpty == this.isEmpty;
        } else {
            return false;
        }
    }

    private boolean equalsVanilla(String ns, String p) {
        return this.namespace.equals(ns) && this.path.equals(p);
    }

    /**
     * Compares namespace and path, treating the shared domain as a wildcard for the addon domain.
     */
    public boolean equalsIgnore(AddonResourceLocation location) {
        if (this == location) {
            return true;
        } else if (location != null && this.equalsVanilla(location.namespace, location.path)) {
            return this.isShareDomain() || location.isShareDomain() || location.addonDomain.equals(this.addonDomain);
        } else {
            return false;
        }
    }

    public boolean equalsIgnore(ResourceLocation location) {
        return location != null && this.equalsVanilla(location.getNamespace(), location.getPath()) && this.isShareDomain();
    }

    @Override
    public int compareTo(@NotNull AddonResourceLocation other) {
        int i = this.namespace.compareTo(other.namespace);
        if (i == 0) {
            i = this.addonDomain.compareTo(other.addonDomain);
        }

        if (i == 0) {
            i = this.path.compareTo(other.path);
        }

        return i;
    }

    @Override
    public @NotNull String toString() {
        return this.namespace + ":" + this.addonDomain + SEPARATOR + this.path;
    }
}
