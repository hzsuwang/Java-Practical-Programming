package com.java.practical.concurrent.lesson03;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 线程池7个致命参数 JUnit 单元测试
 *
 * 验证核心线程数、最大线程数、队列类型、拒绝策略等核心行为。
 *
 * @author Java实战编程馆
 */
@DisplayName("线程池7个致命参数测试")
class ThreadPoolFatalParamsDemoTest {

    // ============ 参数1&2: corePoolSize 与 maximumPoolSize ============

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("核心线程 vs 最大线程: 任务数 <= core 时仅使用核心线程")
    void testOnlyCoreThreadsUsed() throws InterruptedException {
        AtomicInteger threadCount = new AtomicInteger(0);

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 5, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(10),
                r -> {
                    threadCount.incrementAndGet();
                    return new Thread(r);
                },
                new ThreadPoolExecutor.AbortPolicy()
        );

        for (int i = 0; i < 3; i++) {
            executor.execute(() -> {
                try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
        }

        Thread.sleep(500);

        // 最多使用 core 个线程（2个），queue 能存下就不创建额外线程
        assertTrue(executor.getLargestPoolSize() <= 2,
                "任务数 <= core + queue 时不应创建额外线程");

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("核心线程 vs 最大线程: 任务超出 core+queue 时创建额外线程")
    void testExtraThreadsWhenQueueFull() {
        AtomicInteger threadCount = new AtomicInteger(0);

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 3, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),   // 队列仅容1个
                r -> {
                    threadCount.incrementAndGet();
                    return new Thread(r);
                },
                new ThreadPoolExecutor.AbortPolicy()
        );

        // 提交4个任务：1(core) + 1(queue) + 1(extra) + 1(extra) = poolSize=3
        for (int i = 0; i < 4; i++) {
            try {
                executor.execute(() -> {
                    try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
            } catch (RejectedExecutionException ignored) {
            }
        }

        // 验证创建了超过 core 的线程
        assertTrue(executor.getLargestPoolSize() > 1,
                "队列满后应创建额外线程");

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ============ 参数3&4: keepAliveTime 与 TimeUnit ============

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("存活时间: 额外线程空闲超时后应被回收")
    void testExtraThreadRecycledAfterKeepAlive() throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 3,
                1, TimeUnit.SECONDS,    // 1秒回收
                new ArrayBlockingQueue<>(1),
                new ThreadPoolFatalParamsDemo.NamedThreadFactory("KeepAlive"),
                new ThreadPoolExecutor.AbortPolicy()
        );

        // 触发创建3个线程（1 core + 2 extra）
        for (int i = 0; i < 5; i++) {
            try {
                executor.execute(() -> {
                    try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
            } catch (RejectedExecutionException ignored) {
            }
        }

        Thread.sleep(500);
        int poolSizeBefore = executor.getPoolSize();
        assertTrue(poolSizeBefore >= 2, "应创建了额外线程");

        // 等待 keepAliveTime（1秒）过期
        Thread.sleep(2000);
        int poolSizeAfter = executor.getPoolSize();
        assertEquals(1, poolSizeAfter,
                "额外线程空闲超时应被回收，仅剩核心线程");

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    // ============ 参数5: workQueue ============

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("队列类型: SynchronousQueue 不存储任务，直接提交")
    void testSynchronousQueue() throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 3, 60, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolFatalParamsDemo.NamedThreadFactory("Sync"),
                new ThreadPoolExecutor.AbortPolicy()
        );

        AtomicInteger executedCount = new AtomicInteger(0);

        // 提交4个任务（SynchronousQueue 不缓冲，超出 max=3 直接拒绝）
        int rejected = 0;
        for (int i = 0; i < 4; i++) {
            try {
                executor.execute(() -> {
                    executedCount.incrementAndGet();
                    try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
            } catch (RejectedExecutionException e) {
                rejected++;
            }
        }

        Thread.sleep(1000);
        assertEquals(3, executedCount.get(), "SynchronousQueue 下最多执行 max 个任务");
        assertEquals(1, rejected, "第4个任务应被拒绝");

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    // ============ 参数7: 拒绝策略 ============

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("拒绝策略: AbortPolicy 应抛出 RejectedExecutionException")
    void testAbortPolicyThrowsException() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolFatalParamsDemo.NamedThreadFactory("Abort"),
                new ThreadPoolExecutor.AbortPolicy()
        );

        // 提交3个任务（超出 max + queue = 2）
        executor.execute(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        executor.execute(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        assertThrows(RejectedExecutionException.class, () ->
                executor.execute(() -> {})
        );

        executor.shutdownNow();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("拒绝策略: CallerRunsPolicy 应由调用者线程执行")
    void testCallerRunsPolicy() throws InterruptedException {
        AtomicBoolean taskExecuted = new AtomicBoolean(false);
        String callerThreadName = Thread.currentThread().getName();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolFatalParamsDemo.NamedThreadFactory("CallerRuns"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // 占满线程和队列
        executor.execute(() -> {
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        executor.execute(() -> {
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        // 第3个任务应由调用者（main线程）执行
        executor.execute(() -> {
            taskExecuted.set(true);
            assertEquals(callerThreadName, Thread.currentThread().getName(),
                    "CallerRunsPolicy 应由调用者线程执行");
            System.out.println("  ✓ CallerRuns 验证通过: 任务由 " + Thread.currentThread().getName() + " 执行");
        });

        Thread.sleep(1000);
        assertTrue(taskExecuted.get(), "第3个任务应被执行");

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("拒绝策略: DiscardPolicy 应静默丢弃任务")
    void testDiscardPolicy() throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolFatalParamsDemo.NamedThreadFactory("Discard"),
                new ThreadPoolExecutor.DiscardPolicy()
        );

        // 占满线程和队列
        executor.execute(() -> {
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        executor.execute(() -> {
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        // 第3个任务被静默丢弃（不抛异常）
        try {
            executor.execute(() -> fail("此任务不应被执行"));
        } catch (Exception e) {
            fail("DiscardPolicy 不应抛出异常");
        }

        Thread.sleep(1000);
        assertEquals(2, executor.getCompletedTaskCount(),
                "只应有2个任务完成");

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("拒绝策略: DiscardOldestPolicy 应丢弃队列中最老的任务")
    void testDiscardOldestPolicy() throws InterruptedException {
        AtomicInteger newestTaskExecuted = new AtomicInteger(0);

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolFatalParamsDemo.NamedThreadFactory("DiscardOldest"),
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );

        // 任务1: 在线程中执行
        executor.execute(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        // 任务2: 进入队列
        executor.execute(() -> {
            // 这个任务可能被丢弃
        });

        // 任务3: 触发 DiscardOldestPolicy → 丢弃任务2 → 任务3进入队列
        executor.execute(() -> {
            newestTaskExecuted.incrementAndGet();
        });

        Thread.sleep(2000);
        // 第三个任务（最新）应被执行
        assertEquals(1, newestTaskExecuted.get(), "最新任务应被执行（最老任务被丢弃）");

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    // ============ 线程工厂测试 ============

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("线程工厂: 自定义名称应在 jstack 中可辨识")
    void testCustomThreadFactoryNaming() throws InterruptedException {
        String prefix = "OrderHandler";
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 3, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(10),
                new ThreadPoolFatalParamsDemo.NamedThreadFactory(prefix),
                new ThreadPoolExecutor.AbortPolicy()
        );

        AtomicInteger namedThreadCount = new AtomicInteger(0);

        Runnable nameCheckTask = () -> {
            String name = Thread.currentThread().getName();
            if (name.startsWith(prefix)) {
                namedThreadCount.incrementAndGet();
            }
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        };

        for (int i = 0; i < 3; i++) {
            executor.execute(nameCheckTask);
        }

        Thread.sleep(1000);
        assertEquals(3, namedThreadCount.get(),
                "所有线程名称应以 '" + prefix + "' 开头");

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    // ============ 监控指标测试 ============

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("监控指标: 线程池各项统计值应准确")
    void testMonitoringMetrics() throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 4, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(5),
                new ThreadPoolFatalParamsDemo.NamedThreadFactory("Monitor"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // 提交5个任务（2个执行 + 3个入队）
        for (int i = 0; i < 5; i++) {
            executor.execute(() -> {
                try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
        }

        Thread.sleep(200);
        assertEquals(5, executor.getTaskCount(), "总任务数应为5");
        assertTrue(executor.getActiveCount() <= 2, "活跃线程数 <= core(2)");
        assertTrue(executor.getQueue().size() <= 3, "队列中等待任务 <= 3");

        Thread.sleep(1000);
        assertEquals(5, executor.getCompletedTaskCount(), "已完成任务应为5");

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
