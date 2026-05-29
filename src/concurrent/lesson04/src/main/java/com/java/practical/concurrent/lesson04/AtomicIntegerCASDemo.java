package com.java.practical.concurrent.lesson04;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * AtomicInteger 底层 CAS 原理拆解
 * 
 * 演示场景：
 *   1. AtomicInteger 基本用法
 *   2. CAS 底层 Unsafe.compareAndSwapInt 调用链
 *   3. ABA 问题的产生与解决
 *   4. synchronized vs AtomicInteger 性能对比
 *   5. AtomicStampedReference 解决 ABA
 * 
 * @author Java实战编程馆
 */
public class AtomicIntegerCASDemo {

    // ==================== 场景1：AtomicInteger 基本用法 ====================

    static void scenario1_BasicUsage() {
        System.out.println("========== 场景1：AtomicInteger 基本用法 ==========");

        AtomicInteger counter = new AtomicInteger(0);

        // 基础操作
        System.out.println("初始值: " + counter.get());

        // 自增并返回新值
        int newVal = counter.incrementAndGet();
        System.out.println("incrementAndGet(): " + newVal + " (预期: 1)");

        // 返回旧值并自增
        int oldVal = counter.getAndIncrement();
        System.out.println("getAndIncrement() 返回旧值: " + oldVal + ", 新值: " + counter.get());

        // CAS 操作
        boolean success = counter.compareAndSet(2, 100);
        System.out.println("compareAndSet(2, 100): " + success + " → 当前值: " + counter.get());

        success = counter.compareAndSet(2, 200);
        System.out.println("compareAndSet(2, 200): " + success + " → 当前值: " + counter.get());

        System.out.println();
    }

    // ==================== 场景2：剖析 AtomicInteger 底层调用链 ====================

    static void scenario2_CASCallChain() throws Exception {
        System.out.println("========== 场景2：CAS 底层调用链剖析 ==========");

        AtomicInteger atomic = new AtomicInteger(42);

        // 步骤1：通过反射获取 unsafe 字段
        Field unsafeField = AtomicInteger.class.getDeclaredField("unsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);

        // 步骤2：通过反射获取 valueOffset
        Field valueField = AtomicInteger.class.getDeclaredField("valueOffset");
        valueField.setAccessible(true);
        long valueOffset = valueField.getLong(atomic);

        System.out.println("[调试信息]");
        System.out.println("  Unsafe 实例: " + unsafe);
        System.out.println("  value 偏移量: " + valueOffset);
        System.out.println("  当前值: " + atomic.get());

        // 步骤3：模拟 CAS 的 CPU 指令级行为
        // 内存中 value 当前是 42
        // 预期值是 42 → CAS 将 42 改为 100
        boolean casSuccess = unsafe.compareAndSwapInt(atomic, valueOffset, 42, 100);
        System.out.println("  CAS(42→100): " + casSuccess + " → 新值: " + atomic.get());

        // 预期值不匹配 → CAS 失败
        casSuccess = unsafe.compareAndSwapInt(atomic, valueOffset, 42, 200);
        System.out.println("  CAS(42→200): " + casSuccess + " → 新值(不变): " + atomic.get());

        System.out.println();

        // ====== 关键：AtomicInteger.incrementAndGet() 的等效实现 ======
        System.out.println("[incrementAndGet() 等效实现]");
        atomic.set(0);
        int v;
        do {
            v = atomic.get();  // 获取当前值
            // unsafe.compareAndSwapInt(this, valueOffset, v, v + 1)
            // 如果当前值 == v，则更新为 v+1
        } while (!unsafe.compareAndSwapInt(atomic, valueOffset, v, v + 1));
        System.out.println("  CAS 自旋结果: " + atomic.get() + " (预期: 1)");
        System.out.println();
    }

    // ==================== 场景3：ABA 问题的产生 ====================

    static void scenario3_ABAProblem() throws Exception {
        System.out.println("========== 场景3：ABA 问题的产生 ==========");

        AtomicInteger shared = new AtomicInteger(100);

        CountDownLatch latch = new CountDownLatch(2);

        // 线程A：执行 CAS 操作，但中间经历了 ABA
        Thread threadA = new Thread(() -> {
            try {
                // 让线程B先执行完一轮 ABA 操作
                Thread.sleep(100);
                
                int expected = 100;
                int newValue = 200;
                boolean result = shared.compareAndSet(expected, newValue);
                System.out.println("[线程A] CAS(100 → 200): " + result);
                System.out.println("[线程A] 当前值: " + shared.get());
                if (result) {
                    System.out.println("[线程A] ⚠️ CAS成功，但无法感知中间发生过 100→300→100 的变化！");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        }, "Thread-A");

        // 线程B：执行 ABA 操作（100→300→100）
        Thread threadB = new Thread(() -> {
            try {
                // 第一步：100 → 300
                shared.compareAndSet(100, 300);
                System.out.println("[线程B] 第一步 CAS(100→300): " + shared.get());
                
                Thread.sleep(50);
                
                // 第二步：300 → 100（回到原始值）
                shared.compareAndSet(300, 100);
                System.out.println("[线程B] 第二步 CAS(300→100): " + shared.get());
                System.out.println("[线程B] 完成了 ABA 操作: 100 → 300 → 100");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        }, "Thread-B");

        threadB.start();
        Thread.sleep(10);
        threadA.start();

        latch.await();
        System.out.println();
    }

    // ==================== 场景4：AtomicStampedReference 解决 ABA ====================

    static void scenario4_SolveABAWithStamp() throws Exception {
        System.out.println("========== 场景4：AtomicStampedReference 解决 ABA ==========");

        // 初始值 100，版本号 0
        AtomicStampedReference<Integer> ref = new AtomicStampedReference<>(100, 0);

        CountDownLatch latch = new CountDownLatch(2);

        Thread threadA = new Thread(() -> {
            try {
                int[] stampHolder = new int[1];
                int expectedValue = ref.get(stampHolder);
                int expectedStamp = stampHolder[0];
                System.out.println("[线程A] 记录: value=" + expectedValue + ", stamp=" + expectedStamp);

                // 休眠，让线程B执行 ABA
                Thread.sleep(100);

                boolean result = ref.compareAndSet(
                    expectedValue, 200, expectedStamp, expectedStamp + 1
                );
                System.out.println("[线程A] CAS(100→200, stamp:" + expectedStamp + "→" + (expectedStamp+1) + "): " + result);
                if (!result) {
                    int[] currentStamp = new int[1];
                    Integer currentValue = ref.get(currentStamp);
                    System.out.println("[线程A] CAS失败！当前: value=" + currentValue + ", stamp=" + currentStamp[0]);
                    System.out.println("[线程A] ✅ 成功检测到 ABA 变化！");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        }, "Thread-A");

        Thread threadB = new Thread(() -> {
            try {
                int[] stamp = new int[0];

                // 100 → 300 (stamp: 0→1)
                boolean step1 = ref.compareAndSet(100, 300, 0, 1);
                System.out.println("[线程B] 第一步 CAS(100→300, stamp:0→1): " + step1);

                Thread.sleep(50);

                // 300 → 100 (stamp: 1→2) —— 值回到100，但 stamp 已经是 2 了！
                boolean step2 = ref.compareAndSet(300, 100, 1, 2);
                System.out.println("[线程B] 第二步 CAS(300→100, stamp:1→2): " + step2);
                System.out.println("[线程B] ABA操作完成，值回到100，但stamp已变为2");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        }, "Thread-B");

        threadB.start();
        Thread.sleep(10);
        threadA.start();

        latch.await();
        System.out.println();
    }

    // ==================== 场景5：synchronized vs AtomicInteger 高并发性能对比 ====================

    static void scenario5_PerformanceComparison() throws Exception {
        System.out.println("========== 场景5：synchronized vs AtomicInteger 高并发性能对比 ==========");
        System.out.println("（每个方案 10 个线程，每个线程自增 100000 次）");

        final int THREADS = 10;
        final int PER_THREAD = 100_000;
        final int TOTAL = THREADS * PER_THREAD;

        // ----- 方案A：synchronized -----
        long startA = System.currentTimeMillis();
        final int[] syncCounter = {0};
        final Object lock = new Object();
        Thread[] syncThreads = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            syncThreads[i] = new Thread(() -> {
                for (int j = 0; j < PER_THREAD; j++) {
                    synchronized (lock) {
                        syncCounter[0]++;
                    }
                }
            });
        }
        for (Thread t : syncThreads) t.start();
        for (Thread t : syncThreads) t.join();
        long timeA = System.currentTimeMillis() - startA;
        System.out.printf("  synchronized:     %dms (结果: %d, 预期: %d)%n", timeA, syncCounter[0], TOTAL);

        // ----- 方案B：AtomicInteger CAS -----
        long startB = System.currentTimeMillis();
        AtomicInteger atomicCounter = new AtomicInteger(0);
        Thread[] atomicThreads = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            atomicThreads[i] = new Thread(() -> {
                for (int j = 0; j < PER_THREAD; j++) {
                    atomicCounter.incrementAndGet();
                }
            });
        }
        for (Thread t : atomicThreads) t.start();
        for (Thread t : atomicThreads) t.join();
        long timeB = System.currentTimeMillis() - startB;
        System.out.printf("  AtomicInteger:    %dms (结果: %d, 预期: %d)%n", timeB, atomicCounter.get(), TOTAL);

        // ----- 方案C：LongAdder（JDK8）-----
        long startC = System.currentTimeMillis();
        java.util.concurrent.atomic.LongAdder adder = new java.util.concurrent.atomic.LongAdder();
        Thread[] adderThreads = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            adderThreads[i] = new Thread(() -> {
                for (int j = 0; j < PER_THREAD; j++) {
                    adder.increment();
                }
            });
        }
        for (Thread t : adderThreads) t.start();
        for (Thread t : adderThreads) t.join();
        long timeC = System.currentTimeMillis() - startC;
        System.out.printf("  LongAdder:        %dms (结果: %d, 预期: %d)%n", timeC, adder.sum(), TOTAL);

        System.out.printf("  AtomicInteger 比 synchronized 快约 %.1f 倍%n", (double) timeA / timeB);
        System.out.printf("  LongAdder 比 synchronized 快约 %.1f 倍%n", (double) timeA / timeC);
        System.out.println();
    }

    // ==================== 场景6：Unsafe 直接内存操作风险演示 ====================

    static void scenario6_UnsafeMemoryRisk() throws Exception {
        System.out.println("========== 场景6：Unsafe 直接内存操作（仅供学习，生产禁用） ==========");

        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Unsafe unsafe = (Unsafe) theUnsafe.get(null);

        // Unsafe 提供了类似 C 语言的指针操作
        // putOrderedInt 不保证内存可见性，仅保证有序性
        // 这是 AtomicInteger.lazySet() 的底层实现

        long address = unsafe.allocateMemory(16); // 分配16字节堆外内存
        unsafe.putInt(address, 0x12345678);
        int value = unsafe.getInt(address);
        System.out.println("  堆外内存地址: " + Long.toHexString(address));
        System.out.println("  写入值: 0x" + Integer.toHexString(value));
        System.out.println("  ⚠️ Unsafe 操作绕过了 JVM 的所有安全检查，生产环境禁止使用！");
        unsafe.freeMemory(address);
        System.out.println("  已释放堆外内存");
        System.out.println();
    }

    // ==================== 场景7：CAS 自旋次数与性能关系 ====================

    static void scenario7_CASSpinAnalysis() throws Exception {
        System.out.println("========== 场景7：CAS 自旋次数分析 ==========");

        AtomicInteger counter = new AtomicInteger(0);
        final int TOTAL_OPS = 100_000;
        int threadCount = 8;
        CountDownLatch latch = new CountDownLatch(threadCount);

        long start = System.currentTimeMillis();
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < TOTAL_OPS / threadCount; j++) {
                    counter.incrementAndGet();
                }
                latch.countDown();
            }).start();
        }
        latch.await();
        long time = System.currentTimeMillis() - start;

        System.out.println("  " + threadCount + " 个线程, 总计 " + TOTAL_OPS + " 次自增");
        System.out.println("  耗时: " + time + "ms");
        System.out.println("  结果: " + counter.get() + " (预期: " + TOTAL_OPS + ")");
        System.out.printf("  平均每秒完成: %.0f 次 CAS 操作%n", TOTAL_OPS * 1000.0 / time);
        System.out.println();
        System.out.println("[分析]");
        System.out.println("  CAS 自旋次数 = 失败次数。争用越激烈，自旋越多。");
        System.out.println("  极端情况下，CAS 自旋可能超过 synchronized 的上下文切换开销。");
        System.out.println("  JDK 8 LongAdder 通过分段累加（Cell）降低争用，适合高并发计数场景。");
        System.out.println();
    }

    // ==================== main ====================

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║  AtomicInteger 底层 CAS 原理拆解               ║");
        System.out.println("║  7 个场景完整演示                             ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println();

        scenario1_BasicUsage();
        scenario2_CASCallChain();
        scenario3_ABAProblem();
        scenario4_SolveABAWithStamp();
        scenario5_PerformanceComparison();
        scenario6_UnsafeMemoryRisk();
        scenario7_CASSpinAnalysis();

        System.out.println("========================================");
        System.out.println("  全部 7 个场景演示完毕");
        System.out.println("========================================");
    }
}
