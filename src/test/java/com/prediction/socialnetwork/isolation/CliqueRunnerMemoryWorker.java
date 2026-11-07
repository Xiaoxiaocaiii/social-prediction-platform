package com.prediction.socialnetwork.isolation;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class CliqueRunnerMemoryWorker {

    private CliqueRunnerMemoryWorker() {
    }

    public static void main(String[] arguments) {
        List<ByteBuffer> committedChunks = new ArrayList<>();
        while (true) {
            ByteBuffer chunk = ByteBuffer.allocateDirect(
                    Math.toIntExact(2 * WindowsJobLimits.MEBIBYTE));
            chunk.put(0, (byte) 1);
            committedChunks.add(chunk);
        }
    }
}
