package com.prediction.socialnetwork.isolation;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef;
import java.util.Locale;

public final class WindowsResourcePreflight {

    private static final long MINIMUM_AVAILABLE_BYTES = 2 * WindowsJobLimits.GIBIBYTE;

    private WindowsResourcePreflight() {
    }

    public static long currentAvailablePhysicalMemoryBytes() {
        requireWindows();
        WinBase.MEMORYSTATUSEX status = new WinBase.MEMORYSTATUSEX();
        status.dwLength = new WinDef.DWORD(status.size());
        status.write();
        if (!Kernel32.INSTANCE.GlobalMemoryStatusEx(status)) {
            throw new IllegalStateException(
                    "GlobalMemoryStatusEx failed with Win32 error " + Kernel32.INSTANCE.GetLastError());
        }
        status.read();
        return status.ullAvailPhys.longValue();
    }

    public static boolean hasRequiredMemory(long availableBytes) {
        return availableBytes >= MINIMUM_AVAILABLE_BYTES;
    }

    public static void requireMinimumAvailableMemory() {
        long availableBytes = currentAvailablePhysicalMemoryBytes();
        if (!hasRequiredMemory(availableBytes)) {
            throw new IllegalStateException(
                    "Analysis refused: available physical memory is below the 2 GiB safety threshold");
        }
    }

    private static void requireWindows() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
            throw new UnsupportedOperationException("Resource preflight requires Windows");
        }
    }
}
