package com.ivomens.mcheli.bench;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The machine a benchmark ran on, for its report: operating system, processor, memory and Java. The
 * processor name comes from OSHI, which Minecraft ships for its crash reports; where that fails the
 * report says "unknown" rather than failing.
 */
public final class SystemInfo {

    private static final double GIB = 1024.0 * 1024.0 * 1024.0;

    private SystemInfo() {
    }

    /** Ordered name to value pairs; every value is a plain string. */
    public static Map<String, String> describe() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " ("
                + System.getProperty("os.arch") + ")");
        info.put("cpu", cpuName());
        info.put("cpuThreads", String.valueOf(Runtime.getRuntime().availableProcessors()));
        info.put("ramGiB", gib(totalMemory()));
        info.put("javaMaxHeapGiB", gib(Runtime.getRuntime().maxMemory()));
        info.put("java", System.getProperty("java.vendor") + " " + System.getProperty("java.version"));
        info.put("jvmArguments", String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments()
                .stream().filter(argument -> argument.startsWith("-X")).toList()));
        return info;
    }

    private static String cpuName() {
        try {
            String name = new oshi.SystemInfo().getHardware().getProcessor().getProcessorIdentifier().getName();
            return name == null || name.isBlank() ? "unknown" : name.trim();
        } catch (Throwable e) {
            // OSHI needs native access the platform may refuse
            return "unknown";
        }
    }

    private static long totalMemory() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            return os.getTotalMemorySize();
        }
        return -1;
    }

    private static String gib(long bytes) {
        return bytes < 0 ? "unknown" : String.format(Locale.ROOT, "%.1f", bytes / GIB);
    }
}
