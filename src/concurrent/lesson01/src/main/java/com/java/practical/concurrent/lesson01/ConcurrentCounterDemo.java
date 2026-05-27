package com.java.practical.concurrent.lesson01;

import org.openjdk.jol.info.ClassLayout;

/**
 * 并发编程实战之一：从零搭建多线程测试环境
 * 
 * 本Demo展示如何搭建一个标准的多线程测试环境，
 * 并演示一个经典的线程安全问题：计数器并发累加
 */
public class ConcurrentCounterDemo {
    
    // 共享计数器
    private static int counter = 0;
    
    // 线程安全的计数器
    private static final Object lock = new Object();
    private static int safeCounter = 0;
    
    /**
     * 不安全的计数器累加方法
     */
    public static void unsafeIncrement() {
        counter++;
    }
    
    /**
     * 安全的计数器累加方法（使用synchronized）
     */
    public static void safeIncrement() {
        synchronized (lock) {
            safeCounter++;
        }
    }
    
    /**
     * 打印对象内存布局（使用JOL工具）
     */
    public static void printObjectLayout() {
        Object obj = new Object();
        System.out.println("Object内存布局:");
        System.out.println(ClassLayout.parseInstance(obj).toPrintable());
        
        System.out.println("\nString内存布局:");
        String str = "Hello";
        System.out.println(ClassLayout.parseInstance(str).toPrintable());
    }
    
    /**
     * 模拟并发累加测试
     */
    public static void runConcurrentTest() throws InterruptedException {
        final int threadCount = 10;
        final int incrementPerThread = 1000;
        
        // 重置计数器
        counter = 0;
        safeCounter = 0;
        
        Thread[] threads = new Thread[threadCount];
        
        // 创建并启动线程
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementPerThread; j++) {
                    unsafeIncrement();
                    safeIncrement();
                }
            });
            threads[i].start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }
        
        // 输出结果
        System.out.println("========================================");
        System.out.println("并发测试结果:");
        System.out.println("线程数: " + threadCount);
        System.out.println("每个线程累加次数: " + incrementPerThread);
        System.out.println("预期总累加次数: " + (threadCount * incrementPerThread));
        System.out.println("----------------------------------------");
        System.out.println("不安全计数器结果: " + counter);
        System.out.println("安全计数器结果: " + safeCounter);
        System.out.println("========================================");
        
        // 分析结果
        if (counter == threadCount * incrementPerThread) {
            System.out.println("✅ 不安全计数器幸运地得到了正确结果");
        } else {
            System.out.println("❌ 不安全计数器出现线程安全问题，少了 " + 
                (threadCount * incrementPerThread - counter) + " 次累加");
        }
        
        if (safeCounter == threadCount * incrementPerThread) {
            System.out.println("✅ 安全计数器始终得到正确结果");
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Java并发编程实战 - Lesson 01");
        System.out.println("从零搭建多线程测试环境\n");
        
        // 1. 打印对象内存布局
        printObjectLayout();
        
        System.out.println("\n" + "=".repeat(50) + "\n");
        
        // 2. 运行并发测试
        runConcurrentTest();
        
        System.out.println("\n" + "=".repeat(50));
        System.out.println("总结：");
        System.out.println("1. 多线程环境下，简单的 counter++ 操作不是线程安全的");
        System.out.println("2. synchronized 可以保证操作的原子性");
        System.out.println("3. 后续课程将深入分析为什么会出现这个问题");
    }
}
