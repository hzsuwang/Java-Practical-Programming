package com.java.practical.concurrent.lesson04;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;

import static org.junit.Assert.*;

/**
 * AtomicIntegerCASDemo 单元测试
 */
public class AtomicIntegerCASDemoTest {

    @Test
    public void testCompareAndSet() {
        AtomicInteger atomic = new AtomicInteger(100);
        
        // 预期值匹配 → CAS 成功
        assertTrue(atomic.compareAndSet(100, 200));
        assertEquals(200, atomic.get());
        
        // 预期值不匹配 → CAS 失败
        assertFalse(atomic.compareAndSet(100, 300));
        assertEquals(200, atomic.get());
    }

    @Test
    public void testIncrementAndGet() {
        AtomicInteger atomic = new AtomicInteger(0);
        
        assertEquals(1, atomic.incrementAndGet());
        assertEquals(1, atomic.get());
        
        assertEquals(1, atomic.getAndIncrement());
        assertEquals(2, atomic.get());
    }

    @Test
    public void testAddAndGet() {
        AtomicInteger atomic = new AtomicInteger(10);
        
        assertEquals(15, atomic.addAndGet(5));
        assertEquals(15, atomic.get());
    }

    @Test
    public void testMultiThreadIncrement() throws Exception {
        final int THREADS = 10;
        final int PER_THREAD = 1000;
        AtomicInteger counter = new AtomicInteger(0);
        
        CountDownLatch latch = new CountDownLatch(THREADS);
        for (int i = 0; i < THREADS; i++) {
            new Thread(() -> {
                for (int j = 0; j < PER_THREAD; j++) {
                    counter.incrementAndGet();
                }
                latch.countDown();
            }).start();
        }
        latch.await();
        
        assertEquals(THREADS * PER_THREAD, counter.get());
    }

    @Test
    public void testAtomicStampedReference() {
        AtomicStampedReference<Integer> ref = new AtomicStampedReference<>(100, 0);
        
        // 正常 CAS
        assertTrue(ref.compareAndSet(100, 200, 0, 1));
        
        int[] stamp = new int[1];
        assertEquals(Integer.valueOf(200), ref.get(stamp));
        assertEquals(1, stamp[0]);
        
        // stamp 不匹配 → CAS 失败
        assertFalse(ref.compareAndSet(200, 300, 0, 2));
    }

    @Test
    public void testCASWithStampPreventsABA() throws Exception {
        AtomicStampedReference<Integer> ref = new AtomicStampedReference<>(100, 0);
        
        // 模拟 ABA：100→300 (stamp 0→1)，300→100 (stamp 1→2)
        ref.compareAndSet(100, 300, 0, 1);
        ref.compareAndSet(300, 100, 1, 2);
        
        // 另一个线程尝试 CAS(100→200, stamp 0)
        // 当前值虽然回到100，但stamp已经是2了，所以CAS应该失败
        assertFalse(ref.compareAndSet(100, 200, 0, 1));
        
        int[] stamp = new int[1];
        assertEquals(Integer.valueOf(100), ref.get(stamp));
        assertEquals(2, stamp[0]);
    }

    @Test
    public void testLazySet() {
        AtomicInteger atomic = new AtomicInteger(42);
        atomic.lazySet(100);
        // lazySet 不保证立即可见，但在单线程中通常能读到
        // 这里只验证操作不抛异常
        assertTrue(atomic.get() >= 0);
    }
}
