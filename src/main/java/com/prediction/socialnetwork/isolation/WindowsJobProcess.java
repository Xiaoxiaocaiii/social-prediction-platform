package com.prediction.socialnetwork.isolation;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class WindowsJobProcess implements AutoCloseable {

    private static final int CREATE_SUSPENDED = 0x00000004;
    private static final int CREATE_NO_WINDOW = 0x08000000;
    private static final int JOB_OBJECT_EXTENDED_LIMIT_INFORMATION = 9;
    private static final int JOB_OBJECT_BASIC_ACCOUNTING_INFORMATION = 1;
    private static final int JOB_OBJECT_BASIC_PROCESS_ID_LIST = 3;
    private static final int JOB_OBJECT_CPU_RATE_CONTROL_INFORMATION = 15;
    private static final int JOB_OBJECT_LIMIT_ACTIVE_PROCESS = 0x00000008;
    private static final int JOB_OBJECT_LIMIT_PROCESS_MEMORY = 0x00000100;
    private static final int JOB_OBJECT_LIMIT_JOB_MEMORY = 0x00000200;
    private static final int JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x00002000;
    private static final int JOB_OBJECT_CPU_RATE_CONTROL_ENABLE = 0x00000001;
    private static final int JOB_OBJECT_CPU_RATE_CONTROL_HARD_CAP = 0x00000004;
    private static final int WAIT_OBJECT_0 = 0;
    private static final int WAIT_TIMEOUT = 258;
    private static final int JOB_INFO_EXTENDED_LIMIT_SIZE_X64 = 144;
    private static final int JOB_INFO_CPU_RATE_SIZE = 8;
    private static final int JOB_INFO_BASIC_ACCOUNTING_SIZE = 48;
    private static final int EXIT_CODE_TIMEOUT = 0xE001;
    private static final int EXIT_CODE_CANCELLED = 0xE002;

    private static volatile JobApi jobApi;

    private final WinNT.HANDLE job;
    private final WinNT.HANDLE process;
    private final int processId;
    private boolean closed;

    private WindowsJobProcess(WinNT.HANDLE job, WinNT.HANDLE process, int processId) {
        this.job = job;
        this.process = process;
        this.processId = processId;
    }

    public static WindowsJobProcess start(
            Path executable,
            List<String> arguments,
            WindowsJobLimits limits) {
        requireSupportedPlatform();
        WindowsResourcePreflight.requireMinimumAvailableMemory();
        return startWithLimits(executable, arguments, limits);
    }

    static WindowsJobProcess startForControlledVerification(
            Path executable,
            List<String> arguments,
            WindowsJobLimits limits) {
        requireSupportedPlatform();
        return startWithLimits(executable, arguments, limits);
    }

    private static WindowsJobProcess startWithLimits(
            Path executable,
            List<String> arguments,
            WindowsJobLimits limits) {
        Objects.requireNonNull(executable, "executable");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(limits, "limits");
        if (!executable.isAbsolute()) {
            throw new IllegalArgumentException("Executable path must be absolute");
        }

        WinNT.HANDLE job = jobApi().CreateJobObjectW(null, null);
        if (isNullHandle(job)) {
            throw win32Exception("CreateJobObjectW");
        }

        WinBase.PROCESS_INFORMATION processInfo = new WinBase.PROCESS_INFORMATION();
        boolean processCreated = false;
        boolean processAssigned = false;
        try {
            configureLimits(job, limits);
            verifyLimits(job, limits);

            WinBase.STARTUPINFO startupInfo = new WinBase.STARTUPINFO();
            startupInfo.cb = new WinDef.DWORD(startupInfo.size());
            startupInfo.write();

            String commandLine = buildCommandLine(executable, arguments);
            char[] mutableCommandLine = (commandLine + '\0').toCharArray();
            boolean created = Kernel32.INSTANCE.CreateProcessW(
                    executable.toString(),
                    mutableCommandLine,
                    null,
                    null,
                    false,
                    new WinDef.DWORD(CREATE_SUSPENDED | CREATE_NO_WINDOW),
                    null,
                    null,
                    startupInfo,
                    processInfo);
            processInfo.read();
            if (!created) {
                throw win32Exception("CreateProcessW");
            }
            processCreated = true;

            if (!jobApi().AssignProcessToJobObject(job, processInfo.hProcess)) {
                throw win32Exception("AssignProcessToJobObject");
            }
            processAssigned = true;
            verifyLimits(job, limits);
            if (jobApi().ResumeThread(processInfo.hThread) == -1) {
                throw win32Exception("ResumeThread");
            }

            Kernel32.INSTANCE.CloseHandle(processInfo.hThread);
            int processId = processInfo.dwProcessId.intValue();
            return new WindowsJobProcess(job, processInfo.hProcess, processId);
        } catch (RuntimeException exception) {
            if (processCreated) {
                if (processAssigned) {
                    jobApi().TerminateJobObject(job, EXIT_CODE_CANCELLED);
                } else {
                    Kernel32.INSTANCE.TerminateProcess(processInfo.hProcess, EXIT_CODE_CANCELLED);
                }
                Kernel32.INSTANCE.WaitForSingleObject(processInfo.hProcess, 5_000);
                Kernel32.INSTANCE.CloseHandle(processInfo.hThread);
                Kernel32.INSTANCE.CloseHandle(processInfo.hProcess);
            }
            Kernel32.INSTANCE.CloseHandle(job);
            throw exception;
        }
    }

    public int processId() {
        return processId;
    }

    public boolean isRunning() {
        ensureOpen();
        return activeProcessCount() > 0;
    }

    public int activeProcessCount() {
        ensureOpen();
        Memory information = new Memory(JOB_INFO_BASIC_ACCOUNTING_SIZE);
        if (!jobApi().QueryInformationJobObject(
                job,
                JOB_OBJECT_BASIC_ACCOUNTING_INFORMATION,
                information,
                JOB_INFO_BASIC_ACCOUNTING_SIZE,
                new IntByReference())) {
            throw win32Exception("QueryInformationJobObject(accounting)");
        }
        return information.getInt(40);
    }

    public List<Long> activeProcessIds() {
        ensureOpen();
        int capacity = 64;
        Memory information = new Memory(8L + capacity * Long.BYTES);
        if (!jobApi().QueryInformationJobObject(
                job,
                JOB_OBJECT_BASIC_PROCESS_ID_LIST,
                information,
                (int) information.size(),
                new IntByReference())) {
            throw win32Exception("QueryInformationJobObject(process ids)");
        }
        int assignedProcesses = information.getInt(0);
        int processIdsInList = information.getInt(4);
        if (assignedProcesses != processIdsInList || processIdsInList > capacity) {
            throw new IllegalStateException("Job Object process list is incomplete");
        }
        List<Long> processIds = new ArrayList<>(processIdsInList);
        for (int index = 0; index < processIdsInList; index++) {
            processIds.add(information.getLong(8L + index * Long.BYTES));
        }
        return List.copyOf(processIds);
    }

    public long peakJobMemoryBytes() {
        ensureOpen();
        Memory information = queryExtendedLimits(job);
        return information.getLong(136);
    }

    public long totalCpuTime100Nanoseconds() {
        ensureOpen();
        Memory information = new Memory(JOB_INFO_BASIC_ACCOUNTING_SIZE);
        if (!jobApi().QueryInformationJobObject(
                job,
                JOB_OBJECT_BASIC_ACCOUNTING_INFORMATION,
                information,
                JOB_INFO_BASIC_ACCOUNTING_SIZE,
                new IntByReference())) {
            throw win32Exception("QueryInformationJobObject(accounting)");
        }
        return information.getLong(0) + information.getLong(8);
    }

    public LimitSnapshot limitSnapshot() {
        ensureOpen();
        Memory extended = queryExtendedLimits(job);
        Memory cpu = new Memory(JOB_INFO_CPU_RATE_SIZE);
        if (!jobApi().QueryInformationJobObject(
                job,
                JOB_OBJECT_CPU_RATE_CONTROL_INFORMATION,
                cpu,
                JOB_INFO_CPU_RATE_SIZE,
                new IntByReference())) {
            throw win32Exception("QueryInformationJobObject(cpu)");
        }
        return new LimitSnapshot(
                extended.getLong(112),
                extended.getLong(120),
                extended.getInt(40),
                cpu.getInt(0),
                cpu.getInt(4));
    }

    public Completion await(Duration timeout) throws InterruptedException {
        ensureOpen();
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout cannot be negative");
        }

        long deadline = System.nanoTime() + timeout.toNanos();
        int waitResult = Kernel32.INSTANCE.WaitForSingleObject(
                process, remainingMillis(deadline));
        if (waitResult == WAIT_TIMEOUT) {
            terminateAndWait(EXIT_CODE_TIMEOUT, Duration.ofSeconds(5));
            return new Completion(CompletionStatus.TIME_LIMIT, exitCode());
        }
        if (waitResult != WAIT_OBJECT_0) {
            throw win32Exception("WaitForSingleObject");
        }

        if (!waitForEmptyJob(deadline)) {
            terminateAndWait(EXIT_CODE_TIMEOUT, Duration.ofSeconds(5));
            return new Completion(CompletionStatus.TIME_LIMIT, exitCode());
        }
        return new Completion(CompletionStatus.EXITED, exitCode());
    }

    public void terminate() throws InterruptedException {
        terminateAndWait(EXIT_CODE_CANCELLED, Duration.ofSeconds(5));
    }

    private void terminateAndWait(int exitCode, Duration timeout) throws InterruptedException {
        ensureOpen();
        if (activeProcessCount() == 0) {
            return;
        }
        if (!jobApi().TerminateJobObject(job, exitCode)) {
            throw win32Exception("TerminateJobObject");
        }
        if (!waitForEmptyJob(System.nanoTime() + timeout.toNanos())) {
            throw new IllegalStateException("Job Object processes did not exit after termination");
        }
    }

    private boolean waitForEmptyJob(long deadline) throws InterruptedException {
        while (activeProcessCount() > 0) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            Thread.sleep(20);
        }
        return true;
    }

    private int exitCode() {
        IntByReference exitCode = new IntByReference();
        if (!Kernel32.INSTANCE.GetExitCodeProcess(process, exitCode)) {
            throw win32Exception("GetExitCodeProcess");
        }
        return exitCode.getValue();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        RuntimeException cleanupFailure = null;
        try {
            if (activeProcessCount() > 0) {
                try {
                    terminateAndWait(EXIT_CODE_CANCELLED, Duration.ofSeconds(5));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    cleanupFailure = new IllegalStateException(
                            "Interrupted while waiting for Job Object cleanup", exception);
                } catch (RuntimeException exception) {
                    cleanupFailure = exception;
                }
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(process);
            Kernel32.INSTANCE.CloseHandle(job);
            closed = true;
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Job process is already closed");
        }
    }

    private static void configureLimits(WinNT.HANDLE job, WindowsJobLimits limits) {
        Memory extended = new Memory(JOB_INFO_EXTENDED_LIMIT_SIZE_X64);
        int flags = JOB_OBJECT_LIMIT_ACTIVE_PROCESS
                | JOB_OBJECT_LIMIT_PROCESS_MEMORY
                | JOB_OBJECT_LIMIT_JOB_MEMORY
                | JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        extended.setInt(16, flags);
        extended.setInt(40, limits.maxActiveProcesses());
        extended.setLong(112, limits.processMemoryBytes());
        extended.setLong(120, limits.jobMemoryBytes());
        if (!jobApi().SetInformationJobObject(
                job,
                JOB_OBJECT_EXTENDED_LIMIT_INFORMATION,
                extended,
                JOB_INFO_EXTENDED_LIMIT_SIZE_X64)) {
            throw win32Exception("SetInformationJobObject(memory/process)");
        }

        Memory cpu = new Memory(JOB_INFO_CPU_RATE_SIZE);
        cpu.setInt(0, JOB_OBJECT_CPU_RATE_CONTROL_ENABLE | JOB_OBJECT_CPU_RATE_CONTROL_HARD_CAP);
        cpu.setInt(4, limits.cpuRatePer10k());
        if (!jobApi().SetInformationJobObject(
                job,
                JOB_OBJECT_CPU_RATE_CONTROL_INFORMATION,
                cpu,
                JOB_INFO_CPU_RATE_SIZE)) {
            throw win32Exception("SetInformationJobObject(cpu)");
        }
    }

    private static void verifyLimits(WinNT.HANDLE job, WindowsJobLimits expected) {
        Memory extended = queryExtendedLimits(job);
        int flags = extended.getInt(16);
        int requiredFlags = JOB_OBJECT_LIMIT_ACTIVE_PROCESS
                | JOB_OBJECT_LIMIT_PROCESS_MEMORY
                | JOB_OBJECT_LIMIT_JOB_MEMORY
                | JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        if ((flags & requiredFlags) != requiredFlags
                || extended.getInt(40) != expected.maxActiveProcesses()
                || extended.getLong(112) != expected.processMemoryBytes()
                || extended.getLong(120) != expected.jobMemoryBytes()) {
            throw new IllegalStateException("Windows did not retain the requested Job limits");
        }

        Memory cpu = new Memory(JOB_INFO_CPU_RATE_SIZE);
        if (!jobApi().QueryInformationJobObject(
                job,
                JOB_OBJECT_CPU_RATE_CONTROL_INFORMATION,
                cpu,
                JOB_INFO_CPU_RATE_SIZE,
                new IntByReference())) {
            throw win32Exception("QueryInformationJobObject(cpu)");
        }
        int cpuFlags = JOB_OBJECT_CPU_RATE_CONTROL_ENABLE | JOB_OBJECT_CPU_RATE_CONTROL_HARD_CAP;
        if ((cpu.getInt(0) & cpuFlags) != cpuFlags
                || cpu.getInt(4) != expected.cpuRatePer10k()) {
            throw new IllegalStateException("Windows did not retain the requested CPU hard cap");
        }
    }

    private static Memory queryExtendedLimits(WinNT.HANDLE job) {
        Memory information = new Memory(JOB_INFO_EXTENDED_LIMIT_SIZE_X64);
        if (!jobApi().QueryInformationJobObject(
                job,
                JOB_OBJECT_EXTENDED_LIMIT_INFORMATION,
                information,
                JOB_INFO_EXTENDED_LIMIT_SIZE_X64,
                new IntByReference())) {
            throw win32Exception("QueryInformationJobObject(memory/process)");
        }
        return information;
    }

    private static String buildCommandLine(Path executable, List<String> arguments) {
        List<String> parts = new ArrayList<>(arguments.size() + 1);
        parts.add(executable.toString());
        for (String argument : arguments) {
            parts.add(Objects.requireNonNull(argument, "argument"));
        }
        return parts.stream().map(WindowsJobProcess::quoteArgument).reduce((a, b) -> a + " " + b)
                .orElseThrow();
    }

    private static String quoteArgument(String argument) {
        if (!argument.isEmpty()
                && argument.chars().noneMatch(character -> Character.isWhitespace(character)
                        || character == '"')) {
            return argument;
        }

        StringBuilder quoted = new StringBuilder("\"");
        int backslashes = 0;
        for (char character : argument.toCharArray()) {
            if (character == '\\') {
                backslashes++;
            } else if (character == '"') {
                quoted.append("\\".repeat(backslashes * 2 + 1)).append('"');
                backslashes = 0;
            } else {
                quoted.append("\\".repeat(backslashes)).append(character);
                backslashes = 0;
            }
        }
        quoted.append("\\".repeat(backslashes * 2)).append('"');
        return quoted.toString();
    }

    private static int remainingMillis(long deadline) {
        long remainingNanos = Math.max(0, deadline - System.nanoTime());
        long millis = (remainingNanos + 999_999) / 1_000_000;
        return (int) Math.min(Integer.MAX_VALUE, millis);
    }

    private static boolean isNullHandle(WinNT.HANDLE handle) {
        return handle == null || handle.getPointer() == null;
    }

    private static void requireSupportedPlatform() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!osName.contains("windows") || Native.POINTER_SIZE != 8) {
            throw new UnsupportedOperationException(
                    "Windows Job Object isolation requires 64-bit Windows");
        }
    }

    private static JobApi jobApi() {
        JobApi current = jobApi;
        if (current != null) {
            return current;
        }
        synchronized (WindowsJobProcess.class) {
            if (jobApi == null) {
                jobApi = Native.load("kernel32", JobApi.class, W32APIOptions.UNICODE_OPTIONS);
            }
            return jobApi;
        }
    }

    private static IllegalStateException win32Exception(String operation) {
        return new IllegalStateException(
                operation + " failed with Win32 error " + Kernel32.INSTANCE.GetLastError());
    }

    public enum CompletionStatus {
        EXITED,
        TIME_LIMIT
    }

    public record Completion(CompletionStatus status, int exitCode) {
    }

    public record LimitSnapshot(
            long processMemoryBytes,
            long jobMemoryBytes,
            int maxActiveProcesses,
            int cpuControlFlags,
            int cpuRatePer10k) {
    }

    private interface JobApi extends StdCallLibrary {
        WinNT.HANDLE CreateJobObjectW(Pointer securityAttributes, String name);

        boolean SetInformationJobObject(
                WinNT.HANDLE job, int informationClass, Pointer information, int informationLength);

        boolean QueryInformationJobObject(
                WinNT.HANDLE job,
                int informationClass,
                Pointer information,
                int informationLength,
                IntByReference returnLength);

        boolean AssignProcessToJobObject(WinNT.HANDLE job, WinNT.HANDLE process);

        boolean TerminateJobObject(WinNT.HANDLE job, int exitCode);

        int ResumeThread(WinNT.HANDLE thread);
    }
}
