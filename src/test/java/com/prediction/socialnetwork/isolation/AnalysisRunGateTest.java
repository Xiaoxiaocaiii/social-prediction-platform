package com.prediction.socialnetwork.isolation;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.prediction.socialnetwork.network.CliqueEnumerationLimits;
import com.prediction.socialnetwork.network.CliqueEnumerationStatus;
import com.prediction.socialnetwork.network.CliqueEnumerationWorkerMain;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisRunGateTest {

    @Test
    void refusesConcurrentAnalysisAndReleasesGateExactlyOnce() {
        AnalysisRunGate gate = new AnalysisRunGate();

        AnalysisRunGate.Lease first = gate.tryAcquire();
        assertNotNull(first);
        assertNull(gate.tryAcquire());

        first.close();
        first.close();

        AnalysisRunGate.Lease next = gate.tryAcquire();
        assertNotNull(next);
        next.close();
    }

    @Test
    void cliqueAndLouvainRunnersShareTheProcessWideGate() {
        AnalysisRunGate.Lease lease = AnalysisRunGate.shared().tryAcquire();
        assertNotNull(lease);
        try {
            Path javaw = Path.of(System.getProperty("java.home"), "bin", "javaw.exe");
            String classPath = System.getProperty("java.class.path", "unused");
            WindowsCliqueEnumerationRunner cliqueRunner =
                    new WindowsCliqueEnumerationRunner(
                            javaw,
                            classPath,
                            WindowsJobLimits.DEFAULT,
                            CliqueEnumerationWorkerMain.class.getName());
            WindowsLouvainAnalysisRunner louvainRunner =
                    new WindowsLouvainAnalysisRunner(
                            javaw, classPath, WindowsJobLimits.DEFAULT);

            assertEquals(
                    CliqueEnumerationStatus.BUSY,
                    cliqueRunner.runForControlledVerification(
                                    List.of(),
                                    List.of(),
                                    CliqueEnumerationLimits.DEFAULT,
                                    () -> false)
                            .status());
            assertEquals(
                    LouvainWorkerRunResult.Status.BUSY,
                    louvainRunner.runForControlledVerification(
                                    List.of(), List.of(), () -> false)
                            .status());
        } finally {
            lease.close();
        }
    }
}
