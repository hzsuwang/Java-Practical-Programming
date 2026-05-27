package com.java.practical.concurrent.lesson01;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 并发测试基类示例
 * 演示如何编写多线程单元测试
 */
class ConcurrentCounterDemoTest {
    
    @BeforeEach
    void setUp() {
        // 每个测试前重置静态变量（实际项目中需要更严谨的处理）
        // 这里只是示例
    }
    
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testUnsafeIncrementRaceCondition() throws InterruptedException {
        final int threadCount = 20;
        final int iterations = 100;
        
        Thread[] threads = new Thread[threadCount];
        
        // 重置计数器
        ConcurrentCounterDemoTestHelper.resetCounter();
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < iterations; j++) {
                    ConcurrentCounterDemoTestHelper.unsafeIncrement();
                }
            });
        }
        
        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }
        
        int expected = threadCount * iterations;
        int actual = ConcurrentCounterDemoTestHelper.getUnsafeCounter();
        
        // 断言：不安全计数器很可能不等于预期值（因为竞态条件）
        // 注意：这个测试可能偶尔通过（小概率事件），但大多数时候会失败
        System.out.println("测试结果：预期=" + expected + ", 实际=" + actual);
        
        // 这个断言可能会失败，这正好证明了线程不安全
        // 在实际测试中，我们可能需要运行多次来增加失败概率
        if (actual != expected) {
            System.out.println("✅ 测试成功检测到线程安全问题！");
        } else {
            System.out.println("⚠️  这次运行幸运地得到了正确结果，但问题依然存在");
        }
    }
    
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testSafeIncrement() throws InterruptedException {
        final int threadCount = 20;
        final int iterations = 100;
        
        Thread[] threads = new Thread[threadCount];
        
        // 重置计数器
        ConcurrentCounterDemoTestHelper.resetCounter();
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < iterations; j++) {
                    ConcurrentCounterDemoTestHelper.safeIncrement();
                }
            });
        }
        
        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }
        
        int expected = threadCount * iterations;
        int actual = ConcurrentCounterDemoTestHelper.getSafeCounter();
        
        assertEquals(expected, actual, "安全计数器应该始终得到正确结果");
        System.out.println("✅ 安全计数器测试通过，结果正确：" + actual);
    }
}

/**
 * 测试辅助类，避免静态变量干扰
 */
class ConcurrentCounterDemoTestHelper {
    private static int unsafeCounter = 0;
    private static int safeCounter = 0;
    private static final Object lock = new Object();
    
    public static void unsafeIncrement() {
        unsafeCounter++;
    }
    
    public static void safeIncrement() {
        synchronized (lock) {
            safeCounter++;
        }
    }
    
    public static int getUnsafeCounter() {
        return unsafeCounter;
    }
    
    public static int getSafeCounter() {
        return safeCounter;
    }
    
    public static void resetCounter() {
        unsafeCounter = 0;
        safeCounter = 0;
    }
}
