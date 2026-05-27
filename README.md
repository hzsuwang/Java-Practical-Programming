# Java-Practical-Programming

「Java实战编程馆」头条号配套源码仓库

## 关于作者
- 头条号：Java实战编程馆
- 定位：专注Java实战经验分享，前大厂架构师
- 更新：每周更新3篇深度技术文，源码完全开源

## 系列目录

### 1. Java并发编程实战30讲
| 篇序 | 标题 | 状态 | 源码 |
|------|------|------|------|
| 01 | [从零搭建多线程测试环境](docs/concurrent/01-环境搭建.md) | ✅ | [src/concurrent/lesson01](src/concurrent/lesson01) |
| 02 | synchronized锁升级全过程代码演示 | 📝 | [src/concurrent/lesson02](src/concurrent/lesson02) |
| 03 | volatile可见性证明与指令重排问题 | 📝 | [src/concurrent/lesson03](src/concurrent/lesson03) |
| 04 | AtomicInteger底层CAS原理拆解 | 📝 | [src/concurrent/lesson04](src/concurrent/lesson04) |
| 05 | ThreadLocal内存泄漏场景与解决方案 | 📝 | [src/concurrent/lesson05](src/concurrent/lesson05) |
| 06 | CountDownLatch在项目中的3个真实应用 | 📝 | [src/concurrent/lesson06](src/concurrent/lesson06) |
| 07 | CyclicBarrier与CountDownLatch的5个区别 | 📝 | [src/concurrent/lesson07](src/concurrent/lesson07) |
| 08 | Semaphore限流实战：控制接口QPS | 📝 | [src/concurrent/lesson08](src/concurrent/lesson08) |
| 09 | Exchanger线程间数据交换的2个场景 | 📝 | [src/concurrent/lesson09](src/concurrent/lesson09) |
| 10 | 第一阶段总结与常见面试题 | 📝 | [src/concurrent/lesson10](src/concurrent/lesson10) |

### 2. Spring源码深读20篇
| 篇序 | 标题 | 状态 | 源码 |
|------|------|------|------|
| 01 | Spring启动流程核心节点追踪 | 📝 | [src/spring/lesson01](src/spring/lesson01) |
| 02 | Bean生命周期完整流程图解 | 📝 | [src/spring/lesson02](src/spring/lesson02) |
| 03 | 依赖注入的3种方式对比 | 📝 | [src/spring/lesson03](src/spring/lesson03) |
| 04 | AOP动态代理实现原理 | 📝 | [src/spring/lesson04](src/spring/lesson04) |
| 05 | 事务管理源码解析 | 📝 | [src/spring/lesson05](src/spring/lesson05) |

### 3. 线上问题排查手册（15篇）
| 篇序 | 标题 | 状态 | 源码 |
|------|------|------|------|
| 01 | OOM问题排查全流程 | 📝 | [src/troubleshooting/lesson01](src/troubleshooting/lesson01) |
| 02 | CPU 100%问题定位方法 | 📝 | [src/troubleshooting/lesson02](src/troubleshooting/lesson02) |
| 03 | 线程死锁检测与解决 | 📝 | [src/troubleshooting/lesson03](src/troubleshooting/lesson03) |
| 04 | 慢SQL优化实战 | 📝 | [src/troubleshooting/lesson04](src/troubleshooting/lesson04) |
| 05 | 接口超时问题排查 | 📝 | [src/troubleshooting/lesson05](src/troubleshooting/lesson05) |

### 4. AI编程工具实测（10篇）
| 篇序 | 标题 | 状态 | 源码 |
|------|------|------|------|
| 01 | Cursor vs Claude Code对比实测 | 📝 | [src/ai-tools/lesson01](src/ai-tools/lesson01) |
| 02 | GitHub Copilot在Java项目中的效率提升 | 📝 | [src/ai-tools/lesson02](src/ai-tools/lesson02) |
| 03 | 用AI工具重构遗留代码实战 | 📝 | [src/ai-tools/lesson03](src/ai-tools/lesson03) |
| 04 | 大模型辅助代码审查效果评估 | 📝 | [src/ai-tools/lesson04](src/ai-tools/lesson04) |
| 05 | 自动化测试用例生成工具对比 | 📝 | [src/ai-tools/lesson05](src/ai-tools/lesson05) |

## 使用说明
1. 每篇文章对应一个独立的源码目录
2. 所有Demo均可直接运行（需JDK 8+）
3. 文章Markdown文件在`docs/`目录下
4. 配图资源在`images/`目录下

## 技术栈
- Java 8+
- Spring Boot 2.7+
- Maven 3.6+
- JUnit 5

## 贡献指南
欢迎提交Issue和PR，共同完善Java实战内容。

## 联系作者
- 头条号：[Java实战编程馆](https://www.toutiao.com/c/user/your_id/)
- 邮箱：java.practical@example.com

## License
MIT License