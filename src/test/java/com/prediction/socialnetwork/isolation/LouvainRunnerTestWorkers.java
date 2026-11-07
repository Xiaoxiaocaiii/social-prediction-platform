package com.prediction.socialnetwork.isolation;

public final class LouvainRunnerTestWorkers {

    private LouvainRunnerTestWorkers() {
    }

    public static final class Wait {
        private Wait() {
        }

        public static void main(String[] arguments) throws InterruptedException {
            Thread.sleep(60_000);
        }
    }

    public static final class Exit {
        private Exit() {
        }

        public static void main(String[] arguments) {
            System.exit(23);
        }
    }
}
