# G1 与分代 ZGC：从正确性到诊断

本专题是第 06、07 章的深入必读内容。先完成 [GC 基础](../foundations/05-gc.md)，再按下表推进。目标是独立推演一次回收、解释并发时为什么不会丢对象，并从实际日志连接到实现入口。

| 顺序 | 内容 | 学完应能回答 |
| --- | --- | --- |
| 1 | [G1 完整详解](G1.md) | CSet 怎样选，跨区域引用怎样找，SATB 与疏散各保证什么 |
| 2 | [分代 ZGC 完整详解](GENERATIONAL_ZGC.md) | 字段引用颜色怎样解释，屏障怎样配合，搬迁时为何还能访问 |
| 3 | [实验与日志证据](EXPERIMENTS.md) | 持续负载如何运行，怎样识别周期、暂停、晋升与分配等待 |
| 4 | [对照与面试追问](INTERVIEW.md) | 改变存活率、引用密度或 CPU 预算后，成本会怎样变化 |

## 版本和依据

本轮核对日期为 2026-11-11。语义边界仍是 [JVMS 25](https://docs.oracle.com/javase/specs/jvms/se25/html/index.html)，但规范不指定这两种 GC 的算法、数据结构或暂停策略。实现说明固定到 [OpenJDK jdk-25-ga](https://github.com/openjdk/jdk/tree/jdk-25-ga/src/hotspot/share/gc)，运行观察单独记录厂商和完整补丁版本。

| 资料 | 本专题怎样使用 |
| --- | --- |
| [JDK 25 G1 指南](https://docs.oracle.com/en/java/javase/25/gctuning/garbage-first-g1-garbage-collector1.html)与[调优指南](https://docs.oracle.com/en/java/javase/25/gctuning/garbage-first-garbage-collector-tuning.html) | 周期、日志、启发式目标和排查方向 |
| [JEP 439](https://openjdk.org/jeps/439) | 分代 ZGC 的设计动机、屏障职责和记忆集；再用 25 源码核对实现 |
| [JEP 490](https://openjdk.org/jeps/490) | JDK 24 移除非分代 ZGC，25 继承这一状态 |
| [JEP 522](https://openjdk.org/jeps/522) | **JDK 26** 的 G1 双卡表改进，仅作版本边界对照 |
| 正文中的 `jdk-25-ga` 文件链接 | 直接核对类名、函数名、调用关系和临界条件 |

不要把会继续演进的 JEP 或网页当成所有版本共享的源码。尤其 G1 的卡表、脏卡队列与 refinement 机制，应阅读 25 标签；分代 ZGC 的颜色编码也不沿用早期非分代 ZGC 的高位地址图。

## 需要一直分开的五组概念

| 概念 | 区别 |
| --- | --- |
| 标记与搬迁 | 前者确定要保留谁，后者改变物理位置并维持引用正确 |
| 根引用与堆内引用 | 根的协调方式与对象字段访问屏障不完全相同 |
| 目标对象与引用字段位置 | 找到一个对象，不等于找到所有需要更新的引用槽 |
| STW 与应用延迟 | 并发 GC 的 CPU、分配等待、排队仍可能影响请求 |
| 规范、设计、代码、实测 | 用不同证据回答不同层次的问题 |

## 验收标准

1. 为同一条 `holder.ref = newObject` 分别画出 G1 与分代 ZGC 的处理过程。
2. 用时间轴说明标记开始、应用修改引用、标记结束和搬迁之间的关系。
3. 解释两个 GC 的“对象已经移动，但还有旧引用”如何处理，指出应用是否同时运行。
4. 读出一组真实日志中的 GC cause、周期、代、并发阶段与暂停；不把教学示意行当实测。
5. 为一次 Full GC 或 Allocation Stall 提出能区分候选原因的下一项检查。
6. 完成专题追问并指向相关源文件，而不是只复述 SATB、着色指针等名称。

本项目不把修改业务引用的 Java 压力程序称为“手写 GC”。新增 cycles 负载用于观察真实 HotSpot 的行为；它不能直接展示每一次屏障或替代对实现正确性的分析。
