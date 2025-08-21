// File: ThreadSwitchBenchmark.java
// JDK 21+
//
// 기본 실행(인자 없이): both 5000 50  → Virtual → Platform 순으로 같은 조건 비교

// javac benchmark/ThreadSwitchBenchmark.java
// java benchmark.ThreadSwitchBenchmark both 20000 100

// 사용 예:
//   java ThreadSwitchBenchmark                          # both 5000 50
//   java ThreadSwitchBenchmark both 20000 100           # 두 모드 비교
//   java ThreadSwitchBenchmark virtual 20000 100        # Virtual만
//   java ThreadSwitchBenchmark platform 20000 100 8     # Platform만(풀 사이즈 지정)



import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.*;

public class ThreadSwitchBenchmark {
    static class Result {
        final String mode;
        final int tasks;
        final int blockMillis;
        final int platformPoolSize; // virtual은 0으로 표기
        final Duration wall;
        final long usedMemoryBytes;

        Result(String mode, int tasks, int blockMillis, int platformPoolSize, Duration wall, long usedMemoryBytes) {
            this.mode = mode;
            this.tasks = tasks;
            this.blockMillis = blockMillis;
            this.platformPoolSize = platformPoolSize;
            this.wall = wall;
            this.usedMemoryBytes = usedMemoryBytes;
        }

        void print() {
            System.out.printf(
                    Locale.US,
                    """
                    ========== %s ==========
                    Tasks               : %,d
                    Per-task block      : %d ms (simulated blocking)
                    %s
                    Wall-clock          : %.3f s
                    Throughput          : %.1f tasks/s
                    Used heap (approx.) : %,.1f MB
                    =================================
                    """,
                    mode.toUpperCase(),
                    tasks,
                    blockMillis,
                    mode.equals("platform")
                            ? String.format("Platform pool size : %d%n", platformPoolSize)
                            : "",
                    wall.toMillis() / 1000.0,
                    tasks / Math.max(0.001, (wall.toMillis() / 1000.0)),
                    usedMemoryBytes / (1024.0 * 1024.0)
            );
        }
    }

    public static void main(String[] args) throws Exception {
        // 인자 파싱 (기본 both 5000 50)
        String mode = (args.length > 0) ? args[0].trim().toLowerCase(Locale.ROOT) : "both";
        int tasks = (args.length > 1) ? parseIntSafe(args[1], 5000) : 5000;
        int blockMillis = (args.length > 2) ? parseIntSafe(args[2], 50) : 50;
        Integer platformPoolOverride = (args.length > 3) ? parseIntSafe(args[3], -1) : -1;

        if (!mode.equals("virtual") && !mode.equals("platform") && !mode.equals("both")) {
            System.out.println("mode must be 'both' | 'virtual' | 'platform'");
            return;
        }

        System.out.printf("Mode=%s, Tasks=%d, Block=%dms%s%n",
                mode, tasks, blockMillis,
                platformPoolOverride != -1 ? (", PlatformPool=" + platformPoolOverride) : "");

        // 간단 워밍업: JIT 예열 & 초기화 비용 완화
        warmup();

        if (mode.equals("both")) {
            // 순서 고정: Virtual → Platform (캐시/예열 편향 줄이려면 둘 다 두 번 돌려 평균내도 됨)
            Result vr = runVirtual(tasks, blockMillis);
            vr.print();

            Result pr = runPlatform(tasks, blockMillis, platformPoolOverride);
            pr.print();

            printComparison(vr, pr);
        } else if (mode.equals("virtual")) {
            Result r = runVirtual(tasks, blockMillis);
            r.print();
        } else { // platform
            Result r = runPlatform(tasks, blockMillis, platformPoolOverride);
            r.print();
        }

        // macOS에서 컨텍스트 스위칭 관찰 팁
        System.out.println("""
            Tips:
              macOS: 터미널에서
                top -stats pid,command,csw -o csw -l 1
              Instruments(System Trace/Thread States)로 스케줄링 이벤트/컨텍스트 스위치 패턴을 보면 차이가 잘 드러납니다.
            Linux:
                perf stat -e cs,cpu-migrations,task-clock -r 3 -- java ThreadSwitchBenchmark <mode> <tasks> <blockMillis>
            """);
    }

    private static void printComparison(Result v, Result p) {
        System.out.println("************ SUMMARY (Side-by-side) ************");
        System.out.printf(Locale.US, "Tasks             : %,d%n", v.tasks);
        System.out.printf(Locale.US, "Per-task block    : %d ms%n", v.blockMillis);
        System.out.printf(Locale.US, "Platform pool     : %d%n", p.platformPoolSize);
        System.out.printf(Locale.US, "Virtual  wall     : %.3f s%n", v.wall.toMillis() / 1000.0);
        System.out.printf(Locale.US, "Platform wall     : %.3f s%n", p.wall.toMillis() / 1000.0);
        System.out.printf(Locale.US, "Speedup (Plat/VT) : %.2f x%n",
                (p.wall.toNanos() / (double) Math.max(1L, v.wall.toNanos())));
        System.out.println("************************************************");
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private static void warmup() throws InterruptedException {
        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch latch = new CountDownLatch(1000);
            for (int i = 0; i < 1000; i++) {
                exec.submit(() -> {
                    try { Thread.sleep(5); } catch (InterruptedException ignored) {}
                    latch.countDown();
                });
            }
            latch.await();
        }
    }

    private static Result runPlatform(int tasks, int blockMillis, int poolOverride) throws InterruptedException {
        int poolSize = (poolOverride != -1) ? Math.max(1, poolOverride)
                : Math.max(2, Runtime.getRuntime().availableProcessors()); // 기본: 코어 수
        ExecutorService exec = Executors.newFixedThreadPool(poolSize);     // OS Thread 풀
        try {
            return runCommon("platform", exec, tasks, blockMillis, poolSize);
        } finally {
            shutdown(exec);
        }
    }

    private static Result runVirtual(int tasks, int blockMillis) throws InterruptedException {
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            return runCommon("virtual", exec, tasks, blockMillis, 0);
        }
    }

    private static Result runCommon(String mode, ExecutorService exec, int tasks, int blockMillis, int platformPoolSize)
            throws InterruptedException {

        // 메모리 스냅샷(대략치)
        gcQuiet();
        long beforeUsed = usedHeap();

        CountDownLatch latch = new CountDownLatch(tasks);
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger done = new AtomicInteger(); // 진행률 카운터

        // 진행률: 10% 단위 또는 최소 1000개
        int progressStep = Math.max(1000, Math.max(1, tasks / 10));

        Instant t0 = Instant.now();

        for (int i = 0; i < tasks; i++) {
            exec.submit(() -> {
                try {
                    // 블로킹 I/O 유사 (전환 유도)
                    Thread.sleep(blockMillis);
                } catch (InterruptedException e) {
                    failures.incrementAndGet();
                    Thread.currentThread().interrupt();
                } finally {
                    int d = done.incrementAndGet();
                    if (d % progressStep == 0 || d == tasks) {
                        System.out.printf("[%s] Progress: %,d / %,d%n", mode, d, tasks);
                    }
                    latch.countDown();
                }
            });
        }

        latch.await();
        Duration wall = Duration.between(t0, Instant.now());

        // 메모리 스냅샷(대략치)
        gcQuiet();
        long afterUsed = usedHeap();

        if (failures.get() > 0) {
            System.out.println("Failures: " + failures.get());
        }

        return new Result(mode, tasks, blockMillis, platformPoolSize, wall, Math.max(0, afterUsed - beforeUsed));
    }

    private static void shutdown(ExecutorService exec) {
        exec.shutdown();
        try {
            if (!exec.awaitTermination(30, TimeUnit.SECONDS)) {
                exec.shutdownNow();
            }
        } catch (InterruptedException e) {
            exec.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private static void gcQuiet() throws InterruptedException {
        System.gc();
        Thread.sleep(50);
    }
}
