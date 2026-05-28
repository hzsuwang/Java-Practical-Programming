package com.java.practical.concurrent.lesson03;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 并发编程实战之三：线程池的7个致命参数，90%的线上故障源于此
 *
 * 本Demo逐一演示 ThreadPoolExecutor 七个核心参数的正确用法与常见踩坑场景，
 * 覆盖：核心线程数、最大线程数、存活时间、时间单位、工作队列、
 *       线程工厂、拒绝策略 七大参数的完整实战。
 *
 * @author Java实战编程馆
 */
public class ThreadPoolFatalParamsDemo {

    // ============ 通用工具方法 ============

    private static void printSeparator(String title) {
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("  " + title);
        System.out.println("=".repeat(60));
    }

    private static void sleepSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ============ 场景1：核心线程数 与 最大线程数 ============

    /**
     * 场景1：理解 corePoolSize vs maximumPoolSize
     *
     * 核心规则：
     * 1. 任务到来时，如果当前线程数 < corePoolSize，创建新线程（即使有空闲线程）
     * 2. 如果当前线程数 >= corePoolSize，任务进入 workQueue
     * 3. 如果 workQueue 满了 且 当前线程数 < maximumPoolSize，创建新线程
     * 4. 如果 workQueue 满了 且 当前线程数 >= maximumPoolSize，执行拒绝策略
     */
    private static void scene1_coreAndMax() {
        printSeparator("场景1：corePoolSize=2, maxPoolSize=4, 队列容量=3");

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2,    // corePoolSize
                4,    // maximumPoolSize
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(3),   // 队列容量 = 3
                new NamedThreadFactory("Scene1"),
                new ThreadPoolExecutor.AbortPolicy()
        );

        System.out.println("初始状态: poolSize=0, active=0, queueSize=0");

        // 提交 8 个任务（> core + queue = 5 但 <= max + queue = 7，第8个触发拒绝）
        for (int i = 1; i <= 8; i++) {
            final int taskId = i;
            try {
                executor.execute(() -> {
                    System.out.println("  [任务" + taskId + "] 执行线程: " +
                            Thread.currentThread().getName());
                    sleepSeconds(1); // 每个任务执行1秒
                });
                System.out.printf("提交任务%d → poolSize=%d, active=%d, queueSize=%d%n",
                        taskId, executor.getPoolSize(),
                        executor.getActiveCount(),
                        executor.getQueue().size());
            } catch (RejectedExecutionException e) {
                System.out.printf("提交任务%d → ❌ 被拒绝! (poolSize=%d, queueSize=%d)%n",
                        taskId, executor.getPoolSize(), executor.getQueue().size());
            }
        }

        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\n关键结论:");
        System.out.println("  任务1-2: 直接创建核心线程（< corePoolSize）");
        System.out.println("  任务3-5: 进入队列（core线程已满，队列未满）");
        System.out.println("  任务6-7: 队列满了，创建额外线程（< maxPoolSize）");
        System.out.println("  任务8:   队列满 + 线程满 → 触发拒绝策略");
    }

    // ============ 场景2：存活时间 ============

    /**
     * 场景2：keepAliveTime 的作用
     *
     * 超过 corePoolSize 的线程在空闲超过 keepAliveTime 后会被回收。
     */
    private static void scene2_keepAlive() {
        printSeparator("场景2：keepAliveTime = 2秒（额外线程空闲2秒后回收）");

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,    // corePoolSize = 1
                3,    // maxPoolSize = 3
                2, TimeUnit.SECONDS,  // 空闲2秒回收
                new LinkedBlockingQueue<>(2),
                new NamedThreadFactory("Scene2"),
                new ThreadPoolExecutor.AbortPolicy()
        );

        // 先提交5个任务，触发创建3个线程（1 core + 2 extra）
        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            executor.execute(() -> {
                System.out.println("  [任务" + taskId + "] 执行: " +
                        Thread.currentThread().getName());
                sleepSeconds(1);
            });
        }

        sleepSeconds(1); // 等待任务执行完毕
        System.out.println("\n任务全部完成后: poolSize=" + executor.getPoolSize());

        // 等待 keepAliveTime 到期
        System.out.println("等待 3 秒（keepAliveTime=2秒）...");
        sleepSeconds(3);
        System.out.println("3秒后: poolSize=" + executor.getPoolSize() +
                "（额外线程已回收，只剩核心线程）");

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\n关键结论:");
        System.out.println("  keepAliveTime 只对超过 corePoolSize 的线程生效");
        System.out.println("  核心线程默认永不回收（可设置 allowCoreThreadTimeOut(true) 改变）");
    }

    // ============ 场景3：工作队列类型对比 ============

    /**
     * 场景3：三种常见工作队列对比
     *
     * 1. SynchronousQueue       - 不存储任务，直接交给线程
     * 2. LinkedBlockingQueue    - 无界队列（默认 Integer.MAX_VALUE）
     * 3. ArrayBlockingQueue     - 有界队列
     */
    private static void scene3_queueTypes() {
        printSeparator("场景3：三种工作队列类型对比");

        // 队列A: SynchronousQueue（无容量）
        System.out.println("--- 队列A: SynchronousQueue（无缓冲） ---");
        ThreadPoolExecutor syncPool = new ThreadPoolExecutor(
                1, 5, 60, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new NamedThreadFactory("Sync"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        System.out.println("SynchronousQueue: 每个任务必须立即有线程处理，否则创建新线程或拒绝");
        System.out.println("  适合: 瞬时高并发、任务执行快（如Netty的IO线程池）");

        // 队列B: LinkedBlockingQueue（无界）
        System.out.println("\n--- 队列B: LinkedBlockingQueue（无界） ---");
        System.out.println("LinkedBlockingQueue(): 队列无限长，任务堆积在内存中");
        System.out.println("  陷阱: maxPoolSize 形同虚设（队列永远不满，永远不会创建额外线程）");
        System.out.println("  OOM风险: 高（任务堆积可导致内存溢出）");

        // 队列C: ArrayBlockingQueue（有界）
        System.out.println("\n--- 队列C: ArrayBlockingQueue(10)（有界） ---");
        System.out.println("ArrayBlockingQueue(10): 固定容量，满了才触发扩容或拒绝");
        System.out.println("  推荐: 生产环境必须用有界队列，配合合理的拒绝策略");

        syncPool.shutdown();
        try {
            syncPool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ============ 场景4：线程工厂 ============

    /**
     * 场景4：自定义线程工厂（命名 + 守护线程 + 异常处理）
     */
    private static void scene4_threadFactory() {
        printSeparator("场景4：自定义线程工厂最佳实践");

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 4, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("order-processor-" + t.getId()); // 有意义的名称
                    t.setDaemon(false);                        // 非守护线程
                    t.setUncaughtExceptionHandler((th, ex) ->   // 异常处理器
                            System.err.println("线程 " + th.getName() +
                                    " 抛出未捕获异常: " + ex.getMessage()));
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        executor.execute(() -> {
            System.out.println("  执行线程: " + Thread.currentThread().getName());
            System.out.println("  是否守护线程: " + Thread.currentThread().isDaemon());
        });

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\n关键结论:");
        System.out.println("  1. 线程必须有业务含义的名称（方便排查 jstack）");
        System.out.println("  2. 线程池线程应为非守护线程（防止JVM意外退出）");
        System.out.println("  3. 必须设置 UncaughtExceptionHandler");
    }

    // ============ 场景5：四种拒绝策略对比 ============

    /**
     * 场景5：四种拒绝策略对比演示
     */
    private static void scene5_rejectionPolicies() {
        printSeparator("场景5：四种拒绝策略对比");

        // 策略A: AbortPolicy（默认 —— 抛异常）
        System.out.println("--- 策略A: AbortPolicy（抛出异常） ---");
        testRejectionPolicy(new ThreadPoolExecutor.AbortPolicy());
        System.out.println("  适合: 必须感知到任务丢失的场景");

        // 策略B: CallerRunsPolicy（调用者线程执行）
        System.out.println("\n--- 策略B: CallerRunsPolicy（调用者执行） ---");
        testRejectionPolicy(new ThreadPoolExecutor.CallerRunsPolicy());
        System.out.println("  适合: 流量削峰，让调用者承担部分任务实现反压");

        // 策略C: DiscardPolicy（静默丢弃）
        System.out.println("\n--- 策略C: DiscardPolicy（静默丢弃） ---");
        testRejectionPolicy(new ThreadPoolExecutor.DiscardPolicy());
        System.out.println("  陷阱: 任务静默丢失，完全无感知！生产环境慎用");

        // 策略D: DiscardOldestPolicy（丢弃最老的任务）
        System.out.println("\n--- 策略D: DiscardOldestPolicy（丢弃队列头部） ---");
        testRejectionPolicy(new ThreadPoolExecutor.DiscardOldestPolicy());
        System.out.println("  适合: 优先处理最新任务（如实时消息系统）");
    }

    private static void testRejectionPolicy(RejectedExecutionHandler policy) {
        String policyName = policy.getClass().getSimpleName();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new NamedThreadFactory("Policy"),
                policy
        );

        // 提交3个任务（超出 max + queue = 2）
        for (int i = 1; i <= 3; i++) {
            final int taskId = i;
            try {
                executor.execute(() -> {
                    System.out.println("    [任务" + taskId + "] 执行: " +
                            Thread.currentThread().getName());
                    sleepSeconds(2);
                });
                System.out.println("    任务" + taskId + " 提交成功");
            } catch (RejectedExecutionException e) {
                System.out.println("    任务" + taskId + " 被拒绝: " + e.getMessage());
            }
        }

        sleepSeconds(3); // 等任务执行完
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ============ 场景6：经典线上故障模拟 ============

    /**
     * 场景6：模拟线上故障 —— 无界队列导致 OOM
     *
     * 常见错误：使用 Executors.newFixedThreadPool() 或
     * LinkedBlockingQueue() 无参构造，默认容量为 Integer.MAX_VALUE。
     * 高并发下任务无限堆积 → OOM。
     */
    private static void scene6_oomSimulation() {
        printSeparator("场景6：经典线上故障 —— 无界队列导致内存溢出（模拟）");

        // 错误做法：无界队列
        ThreadPoolExecutor badPool = new ThreadPoolExecutor(
                5, 10, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),  // ⚠️ 无界！默认 Integer.MAX_VALUE
                new NamedThreadFactory("Bad"),
                new ThreadPoolExecutor.AbortPolicy()
        );

        System.out.println("错误配置: new LinkedBlockingQueue<>() 无参构造");
        System.out.println("  队列容量: Integer.MAX_VALUE (2,147,483,647)");
        System.out.println("  后果: 任务无限堆积 → 内存溢出 (OOM)");

        badPool.shutdown();

        // 正确做法：有界队列
        System.out.println("\n正确配置: new LinkedBlockingQueue<>(1000) 或 ArrayBlockingQueue<>(1000)");
        System.out.println("  队列容量: 1000");
        System.out.println("  后果: 队列满后触发拒绝策略，系统可控");

        System.out.println("\n《阿里巴巴Java开发手册》强制规定:");
        System.out.println("  【强制】线程池不允许使用 Executors 去创建，而是通过");
        System.out.println("  ThreadPoolExecutor 的方式，明确指定有界队列和拒绝策略。");
    }

    // ============ 场景7：线程池监控 ============

    /**
     * 场景7：线程池监控指标采集
     */
    private static void scene7_monitoring() {
        printSeparator("场景7：线程池关键监控指标");

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 4, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                new NamedThreadFactory("Monitor"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // 提交一些任务
        for (int i = 0; i < 6; i++) {
            executor.execute(() -> sleepSeconds(1));
        }

        sleepSeconds(1);

        System.out.println("线程池实时指标:");
        System.out.println("  getCorePoolSize()    = " + executor.getCorePoolSize() + "  (核心线程数)");
        System.out.println("  getMaximumPoolSize() = " + executor.getMaximumPoolSize() + "  (最大线程数)");
        System.out.println("  getPoolSize()        = " + executor.getPoolSize() + "  (当前线程数)");
        System.out.println("  getActiveCount()     = " + executor.getActiveCount() + "  (活跃线程数)");
        System.out.println("  getQueue().size()    = " + executor.getQueue().size() + "  (队列中等待任务数)");
        System.out.println("  getCompletedTaskCount() = " + executor.getCompletedTaskCount() + "  (已完成任务数)");
        System.out.println("  getTaskCount()       = " + executor.getTaskCount() + "  (总任务数)");
        System.out.println("  getLargestPoolSize() = " + executor.getLargestPoolSize() + "  (历史最大线程数)");

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\n关键结论:");
        System.out.println("  生产环境必须接入线程池监控（如 Micrometer + Prometheus）");
        System.out.println("  告警阈值建议: 队列使用率 > 80% 或 拒绝计数 > 0");
    }

    // ============ 综合演示：7个参数一站式展示 ============

    /**
     * 综合Demo：7个参数逐一拆解
     */
    private static void comprehensiveDemo() {
        printSeparator("综合演示：ThreadPoolExecutor 7个参数完整示例");

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2,                              // 参数1: corePoolSize
                4,                              // 参数2: maximumPoolSize
                30, TimeUnit.SECONDS,           // 参数3: keepAliveTime, 参数4: unit
                new ArrayBlockingQueue<>(3),    // 参数5: workQueue（有界队列）
                new NamedThreadFactory("Demo"), // 参数6: threadFactory
                new ThreadPoolExecutor.CallerRunsPolicy()  // 参数7: rejectedExecutionHandler
        );

        System.out.println("参数详解:");
        System.out.println("  1. corePoolSize     = 2  → 核心线程数，常驻线程");
        System.out.println("  2. maximumPoolSize  = 4  → 最大线程数，峰值线程上限");
        System.out.println("  3. keepAliveTime    = 30 → 额外线程空闲存活时间");
        System.out.println("  4. unit             = SECONDS → 时间单位");
        System.out.println("  5. workQueue        = ArrayBlockingQueue(3) → 有界阻塞队列");
        System.out.println("  6. threadFactory    = NamedThreadFactory → 自定义线程工厂");
        System.out.println("  7. handler          = CallerRunsPolicy → 拒绝策略");

        System.out.println("\n线程池工作流程:");
        System.out.println("  提交任务 →");
        System.out.println("    ├─ 当前线程 < corePoolSize? → 创建新线程执行");
        System.out.println("    ├─ 队列未满? → 加入队列等待");
        System.out.println("    ├─ 当前线程 < maxPoolSize? → 创建新线程执行");
        System.out.println("    └─ 否则 → 执行拒绝策略");

        executor.shutdown();
    }

    // ============ main ============

    public static void main(String[] args) {
        System.out.println("Java并发编程实战 - Lesson 03");
        System.out.println("线程池的7个致命参数，90%的线上故障源于此");
        System.out.println("JVM: " + System.getProperty("java.version"));
        System.out.println();

        // 逐一运行七个场景
        scene1_coreAndMax();
        scene2_keepAlive();
        scene3_queueTypes();
        scene4_threadFactory();
        scene5_rejectionPolicies();
        scene6_oomSimulation();
        scene7_monitoring();

        comprehensiveDemo();

        printSeparator("总结");
        System.out.println("7个参数记忆口诀:");
        System.out.println("  核心最大管线程，存活时间控回收");
        System.out.println("  队列有界防OOM，工厂命名助排查");
        System.out.println("  拒绝策略要慎重，生产监控不能少");
    }

    // ============ 自定义线程工厂 ============

    static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(1);
        private final String prefix;

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + counter.getAndIncrement());
            t.setDaemon(false);
            t.setUncaughtExceptionHandler((th, ex) ->
                    System.err.println("[" + th.getName() + "] 未捕获异常: " + ex.getMessage()));
            return t;
        }
    }
}
