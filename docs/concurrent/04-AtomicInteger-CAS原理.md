# 并发编程实战之四：AtomicInteger底层CAS原理拆解

## 一、场景引入：为什么 i++ 在多线程下不安全？

我们先来看一个面试必问的问题：下面这段代码输出的 counter 是多少？

```java
public class CounterTest {
    private static int counter = 0;
    
    public static void main(String[] args) throws Exception {
        Thread t1 = new Thread(() -> { for (int i = 0; i < 10000; i++) counter++; });
        Thread t2 = new Thread(() -> { for (int i = 0; i < 10000; i++) counter++; });
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        System.out.println("counter = " + counter); // 输出？
    }
}
```

**答案：不确定，通常小于 20000，可能是 19372、18765……**

原因很简单：`counter++` 不是原子操作。它在字节码层面被拆成了三步：

```
getfield  counter   // 1. 从内存读取 counter
iconst_1            // 2. 压入常量 1
iadd                // 3. 加法
putfield  counter   // 4. 写回内存
```

两个线程同时执行这三步时，读写交错导致丢失更新。

**解决方案**：synchronized 或 AtomicInteger。今天重点拆解 AtomicInteger 背后的核心机制 —— **CAS（Compare And Swap）**。

学完本文你将掌握：

1. CAS 的 CPU 指令级实现原理
2. AtomicInteger 源码逐行剖析
3. ABA 问题的产生与两种解决方案
4. synchronized / AtomicInteger / LongAdder 性能对比
5. CAS 的优点、局限与适用场景

## 二、CAS 原理：CPU 级别的原子操作

### 2.1 什么是 CAS？

CAS 全称 **Compare And Swap**（比较并交换），是一条 CPU 原子指令。它的语义是：

```
CAS(内存地址, 预期值, 新值):
    if 内存地址当前值 == 预期值:
        将内存地址当前值更新为新值
        return true
    else:
        return false
```

整个「比较+交换」过程，在 CPU 层面由一条指令（如 x86 的 `cmpxchg`）完成，**不可被中断**。

### 2.2 CAS 在 CPU 层面的实现

以 x86 架构为例，CAS 对应的汇编指令是 `cmpxchg`：

```asm
; cmpxchg 指令格式
lock cmpxchg [内存地址], 新值

; 伪代码等效：
; if (rax == [内存地址]) {
;     [内存地址] = 新值
;     ZF = 1
; } else {
;     rax = [内存地址]
;     ZF = 0
; }
```

**关键点**：`lock` 前缀锁定了总线/缓存行，保证多核 CPU 下的原子性。

### 2.3 CAS + 自旋 = 无锁并发

CAS 本身只做一次比较并交换。要实现类似 `i++` 的效果，需要在 CAS 外面包一层**自旋循环**：

```java
// AtomicInteger.incrementAndGet() 的等效逻辑
public final int incrementAndGet() {
    int v;
    do {
        v = get();   // 1. 读取当前值
        // 2. 尝试 CAS(v, v+1)
    } while (!compareAndSet(v, v + 1));  // 3. 失败则重试
    return v + 1;
}
```

**这就是"无锁"的本质**：不是没有锁，而是用 CAS 自旋替代了操作系统的互斥锁。不会让线程进入 BLOCKED 状态，避免了上下文切换的开销。

## 三、AtomicInteger 源码逐行剖析

### 3.1 类的核心结构

```java
public class AtomicInteger extends Number implements java.io.Serializable {
    
    // Unsafe 实例 —— JVM 后门，提供 CAS 等底层操作
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    
    // value 字段在内存中的偏移量
    private static final long valueOffset;
    
    // 实际存储的 int 值，volatile 保证可见性
    private volatile int value;
    
    static {
        try {
            valueOffset = unsafe.objectFieldOffset(
                AtomicInteger.class.getDeclaredField("value"));
        } catch (Exception ex) { throw new Error(ex); }
    }
}
```

三个核心成员：

| 成员 | 作用 |
|------|------|
| `value` (volatile) | 存储实际 int 值，volatile 保证多线程可见性 |
| `unsafe` (Unsafe) | JDK 内部类，提供 CAS 等 native 方法，绕过 JVM 安全检查 |
| `valueOffset` (long) | value 字段在 AtomicInteger 对象中的内存偏移量 |

### 3.2 关键方法源码

**compareAndSet（核心方法）**：

```java
public final boolean compareAndSet(int expect, int update) {
    return unsafe.compareAndSwapInt(this, valueOffset, expect, update);
}
```

`unsafe.compareAndSwapInt` 是一个 native 方法，最终调用 CPU 的 `cmpxchg` 指令。

**incrementAndGet / getAndIncrement**：

```java
// 自增并返回新值
public final int incrementAndGet() {
    return unsafe.getAndAddInt(this, valueOffset, 1) + 1;
}

// 返回旧值并自增
public final int getAndIncrement() {
    return unsafe.getAndAddInt(this, valueOffset, 1);
}
```

底层都是委托给 `Unsafe.getAndAddInt`：

```java
// Unsafe.java (JDK 8)
public final int getAndAddInt(Object o, long offset, int delta) {
    int v;
    do {
        v = getIntVolatile(o, offset);    // 读取 volatile 值
    } while (!compareAndSwapInt(o, offset, v, v + delta));  // CAS 自旋
    return v;
}
```

这就是经典的自旋 CAS 模式：**读 → 比较 → 失败重试**。

### 3.3 lazySet —— 性能优化的"特例"

```java
public final void lazySet(int newValue) {
    unsafe.putOrderedInt(this, valueOffset, newValue);
}
```

`putOrderedInt` 只保证**有序性**，不保证**立即可见性**。它比 volatile 写入开销更低，适用于不需要立即被其他线程看到的场景（如清空一个计数器）。

## 四、ABA 问题

### 4.1 什么是 ABA 问题？

假设共享变量初始值为 A：

1. 线程1 读取到 A
2. 线程2 将 A 改为 B，再将 B 改回 A
3. 线程1 执行 CAS，发现值还是 A → **CAS 成功**

**问题**：线程1 不知道值曾经被改为 B，这可能导致逻辑错误。

经典场景：**栈的 ABA 问题**：

```
初始栈: top → A → B → C

线程1：准备 pop（记录 top=A, next=B，然后 CAS(top, A→B)）
线程2：连续操作 pop(A), pop(B), push(A)
      结果：top → A → C  （B 已被移除，但 A 重新入栈）

线程1 执行 CAS：发现 top 还是 A → 成功将 top 设为 B
结果：B 被重新链接到栈上，但 B 已经被释放/重用，导致内存错乱！
```

### 4.2 解决方案一：AtomicStampedReference（版本号）

给变量加一个版本号（stamp），每次修改版本号 +1。CAS 时同时比较值和版本号：

```java
// 初始：value=100, stamp=0
AtomicStampedReference<Integer> ref = new AtomicStampedReference<>(100, 0);

// CAS 同时检查值 和 版本号
int[] stampHolder = new int[1];
Integer value = ref.get(stampHolder);
int stamp = stampHolder[0];

// 值相等 + 版本号相等 → 才执行更新
ref.compareAndSet(value, 200, stamp, stamp + 1);
```

线程B 执行 ABA (100→300→100) 后，版本号从 0 变到了 2。线程A 用版本号 0 执行 CAS → **失败**，成功检测到 ABA。

### 4.3 解决方案二：AtomicMarkableReference（布尔标记）

与 AtomicStampedReference 类似，但只用一个 boolean 标记替代版本号，适用于只关心"是否被修改过"的场景：

```java
AtomicMarkableReference<Integer> ref = new AtomicMarkableReference<>(100, false);

// 修改时标记为 true
ref.compareAndSet(100, 300, false, true);
ref.compareAndSet(300, 100, true, true);

// 用 false 标记去做 CAS → 失败
ref.compareAndSet(100, 200, false, false); // 失败！
```

### 4.4 选择建议

| 方案 | 适用场景 |
|------|----------|
| AtomicInteger / AtomicReference | 不需要关心中间状态变化的计数器或引用更新 |
| AtomicStampedReference | 需要精确控制版本，如数据结构（栈、链表）中的节点替换 |
| AtomicMarkableReference | 只需要知道"是否被修改过"，如一次性初始化标记 |

## 五、实战 Demo

完整可运行代码位于 `src/concurrent/lesson04/`，包含 7 个场景：

### 运行方式

```bash
cd src/concurrent/lesson04
mvn clean compile exec:java -Dexec.mainClass="com.java.practical.concurrent.lesson04.AtomicIntegerCASDemo"
```

或运行全部单元测试：

```bash
mvn clean test
```

### Demo 场景概览

| 场景 | 演示内容 | 核心要点 |
|------|----------|----------|
| 场景1 | AtomicInteger 基本用法 | get/incrementAndGet/compareAndSet |
| 场景2 | CAS 底层调用链 | 反射获取 Unsafe，模拟 CPU 指令级 CAS |
| 场景3 | ABA 问题产生 | 双线程模拟 ABA 100→300→100 |
| 场景4 | AtomicStampedReference | 版本号机制解决 ABA |
| 场景5 | 性能对比 | synchronized vs AtomicInteger vs LongAdder |
| 场景6 | Unsafe 直接内存 | 堆外内存分配/读写/释放 |
| 场景7 | CAS 自旋分析 | 多线程争用下的自旋次数 |

## 六、常见面试题

### Q1：什么是 CAS？底层是如何实现的？

**答**：CAS（Compare And Swap）是一条 CPU 原子指令，用于实现无锁并发。

**底层实现**：
- x86 架构使用 `lock cmpxchg` 指令
- `lock` 前缀锁定总线/缓存行，保证原子性
- JVM 通过 `Unsafe.compareAndSwapInt` native 方法调用 CPU 指令

### Q2：AtomicInteger 如何保证线程安全？

**答**：两层保障：

1. **volatile value**：保证内存可见性，任何线程读到的一定是最新值
2. **CAS 自旋**：修改值时通过 CPU 原子指令 compareAndSwap 完成，失败则重试

两者结合，既避免了 synchronized 的上下文切换开销，又保证了线程安全。

### Q3：CAS 有哪些缺点？

**答**：

1. **ABA 问题**：值被改为 B 又改回 A 无法感知。解决：AtomicStampedReference 版本号机制
2. **自旋开销**：高争用场景下 CAS 不断失败重试，CPU 空转。解决：JDK 8 LongAdder 分段累加
3. **只能保证单个变量的原子性**：无法像 synchronized 一样保护代码块

### Q4：synchronized 和 AtomicInteger 怎么选？

**答**：

| 场景 | 推荐 |
|------|------|
| 低争用、单变量原子操作 | AtomicInteger（性能更好） |
| 高争用、频繁计数 | LongAdder（JDK 8+，分段降低争用） |
| 多步骤复合操作 | synchronized / Lock（CAS 无法保护多个操作） |
| 需要等待/通知 | synchronized / Lock（CAS 不支持） |

### Q5：LongAdder 为什么比 AtomicInteger 快？

**答**：LongAdder 采用**分段累加**（Striped64）：

- AtomicInteger 在高并发下所有线程竞争同一个 value，CAS 失败率极高
- LongAdder 内部维护一个 base + Cell[] 数组，将竞争分散到多个 Cell
- 最终 sum() 时汇总 base + 所有 Cell 的值

**代价**：sum() 不是瞬时快照，可能拿到中间状态（最终一致性而非强一致性）。

### Q6：CAS 是真正的"无锁"吗？

**答**：严格来说不是。

- **操作系统层面**：CAS 不涉及互斥锁（mutex），线程不会进入 BLOCKED 状态
- **CPU 层面**：`lock` 前缀会锁定总线/缓存行，本质上也是一种"锁"，只是粒度极小（一个指令周期）
- **应用层面**：通常称为"无锁编程"（lock-free），因为开发者不需要显式使用 synchronized 或 Lock

### Q7：AtomicReference 和 AtomicStampedReference 有什么区别？

**答**：

| | AtomicReference | AtomicStampedReference |
|------|------|------|
| 比较维度 | 仅比较引用值 | 同时比较引用值和版本号 |
| ABA 防护 | 无法防护 | 通过 stamp 版本号防护 |
| 内部实现 | 单 CAS | 内部封装了一个 Pair(引用, stamp) |
| 适用场景 | 简单引用更新 | 需要防止 ABA 的数据结构操作 |

## 七、总结

1. **CAS 是无锁并发的基石**：一条 CPU 指令完成比较和交换，全程原子
2. **AtomicInteger = volatile + CAS 自旋**：读用 volatile 保证可见性，写用 CAS 保证原子性
3. **ABA 是 CAS 的经典陷阱**：值回到原始值不等于没被修改过。AtomicStampedReference 通过版本号解决
4. **synchronized ≠ 万能，AtomicInteger ≠ 万能**：
   - 低争用计数 → AtomicInteger
   - 高争用计数 → LongAdder
   - 多步骤复合操作 → synchronized / Lock
5. **JDK 8 LongAdder 是并发计数的最优解**：分段累加降低竞争，适用高并发统计场景

**配置口诀**：

> CAS 比较再交换，CPU 指令保原子；
> volatile 保障可见性，自旋重试写新值；
> ABA 陷阱要当心，版本号来标记它；
> 高争用下 LongAdder，分段累加性能佳。

**学习建议**：把 Demo 中每个场景跑一遍，特别关注场景2（反射 Unsafe）和场景3（ABA 问题）。当你能解释清楚 `incrementAndGet()` 的完整调用链，并画出 CAS 自旋流程图时，恭喜你已经掌握了 Java 无锁编程的核心。

## 八、源码地址

完整可运行代码已开源：
[GitHub: Java-Practical-Programming/src/concurrent/lesson04](https://github.com/yourname/Java-Practical-Programming/tree/main/src/concurrent/lesson04)

---

**下期预告**：《并发编程实战之五：ThreadLocal内存泄漏场景与解决方案》

我们将用代码证明：
- ThreadLocal 的底层数据存储结构
- 为什么 key 是弱引用但 value 是强引用
- 4 种真实的内存泄漏场景复现
- 线程池场景下的 ThreadLocal 正确清理姿势

**关注「Java实战编程馆」，每天上午11点更新一篇深度技术文，源码完全开源！**
