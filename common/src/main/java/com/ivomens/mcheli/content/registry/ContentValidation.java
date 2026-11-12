package com.ivomens.mcheli.content.registry;

import com.google.common.collect.Multimap;
import com.ivomens.mcheli.content.bind.UnsupportedSettings;
import com.ivomens.mcheli.content.pack.AddonManager;
import com.ivomens.mcheli.content.pack.AddonPack;
import com.ivomens.mcheli.content.report.ContentReport;
import com.ivomens.mcheli.content.report.ReportCollector;
import com.ivomens.mcheli.content.source.ContentLoader;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Reads the loaded packs again, as the game read them at start-up, and reports what it finds, without changing
 * any loaded content: {@code /mcheli validate}. Files are read from disk, so an author can fix a file and check it
 * without restarting. Names a file links to (a vehicle's weapons and HUDs) are checked against the content loaded
 * now.
 */
public final class ContentValidation {

    private ContentValidation() {
    }

    /**
     * @param domain the pack to check, or null for every loaded pack (which also reports files that replace
     *               others across packs)
     * @return the report, or empty when no loaded pack has that domain
     */
    public static Optional<ContentReport> run(@Nullable String domain) {
        List<AddonPack> packs = AddonManager.getLoadedAddons().stream()
                .filter(pack -> domain == null || pack.getDomain().equals(domain))
                .toList();
        if (packs.isEmpty() && domain != null) {
            return Optional.empty();
        }
        try (ReportCollector report = ReportCollector.open(domain == null ? "Validation of every loaded pack"
                : "Validation of pack '" + domain + "'")) {
            UnsupportedSettings.resetReports();
            Multimap<ContentType, ContentLoader.PackFile> contents = ContentRegistries.read(packs, report);
            for (ContentType type : ContentRegistries.loadOrder()) {
                ContentRegistries.parse(ContentRegistries.registry(type), contents.get(type), report);
            }
            return Optional.of(report.finish());
        }
    }
}
