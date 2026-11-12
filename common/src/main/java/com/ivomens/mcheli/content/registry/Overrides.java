package com.ivomens.mcheli.content.registry;

import com.ivomens.mcheli.content.pack.AddonManager;
import com.ivomens.mcheli.content.report.FileOverride;
import com.ivomens.mcheli.content.report.ReportCollector;
import com.ivomens.mcheli.util.ModLogger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the files of one content type that replace others: files are in load order, and a later file with the
 * same name replaces the earlier one whole ({@link ContentRegistry#fill}). An addon replacing a bundled file is
 * what addons are for, so it is only listed in the report; any other replacement is a warning that names both
 * files, since one of them is silently unused.
 */
final class Overrides {

    private Overrides() {
    }

    static void record(ContentType type, List<? extends LoadedContent<?, ?>> files, ReportCollector report) {
        Map<String, ContentEntry<?>> seen = new HashMap<>();
        for (LoadedContent<?, ?> file : files) {
            ContentEntry<?> entry = file.entry();
            ContentEntry<?> replaced = seen.put(file.name(), entry);
            if (replaced == null) {
                continue;
            }
            FileOverride override = new FileOverride(type.dirName, file.name(), domain(entry), entry.filePath(),
                    domain(replaced), replaced.filePath());
            report.override(override);
            try (ReportCollector.FileScope scope = ReportCollector.about(override.winnerPack(), entry.filePath())) {
                if (isBundled(override.loserPack()) && !override.withinOnePack()) {
                    ModLogger.get().debug(override.describe());
                } else {
                    ModLogger.get().warn(override.describe());
                }
            }
        }
    }

    private static String domain(ContentEntry<?> entry) {
        return entry.location().getAddonDomain();
    }

    private static boolean isBundled(String domain) {
        return AddonManager.BUILTIN_ADDON_DOMAIN.equals(domain);
    }
}
