# 官方资料与源码导航

先阅读章节与跑实验，再带着一个具体问题查资料。不把“读过多少页源码”当作学习完成度。以下规范与工具文档固定在 **Java SE/JDK 25**；HotSpot 源码固定到 **jdk-25-ga** 标签，而不是不断变化的 master。厂商补丁构建仍可能有差异。

## 规范：回答“必须是什么”

- [JVMS 25](https://docs.oracle.com/javase/specs/jvms/se25/html/index.html)：第 2 章运行时结构，第 4 章 class 文件，第 5 章加载/链接/初始化，第 6 章指令。对应课程 01～04。
- [JLS 25 第 12 章](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html)：类初始化与程序执行，对应第 03 章。
- [JLS 25 第 17 章](https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html)：线程、锁与 JMM；重点读 17.4 的 happens-before 和 17.5 的 final 字段规则，对应第 09 章。

## 工具与实现指南：回答“如何观察”

- [JDK 25 java 命令与参数](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html)：检查参数的状态、默认值与适用边界，不从旧博客复制启动串。
- [JDK 25 GC 调优指南](https://docs.oracle.com/en/java/javase/25/gctuning/index.html)：G1、ZGC、代际与目标；用于第 06、07 章。
- [JDK 25 故障诊断指南](https://docs.oracle.com/en/java/javase/25/troubleshoot/index.html)：内存、挂起、崩溃与工具，对应第 11、12 章。
- [jcmd 25](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html)、[jfr 25](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jfr.html)、[javap 25](https://docs.oracle.com/en/java/javase/25/docs/specs/man/javap.html)：先用目标进程的 jcmd help 核对命令，再判断开销。

## JDK 25 及其继承的关键变化

| 文档 | 阅读任务 |
| --- | --- |
| [JDK 25 特性清单](https://openjdk.org/projects/jdk/25/) | 区分正式、预览、孵化特性，不把全部新语法当作 JVM 主线 |
| [JEP 519：紧凑对象头](https://openjdk.org/jeps/519) | 为什么产品化不等于默认启用？与 L05 对照 |
| [JEP 521：分代 Shenandoah](https://openjdk.org/jeps/521) | 为什么产品化不等于默认分代？检查自己的构建能力 |
| [JEP 490：移除非分代 ZGC](https://openjdk.org/jeps/490) | 这是 JDK 24 的变化，JDK 25 继承；不再练旧选择开关 |
| [JEP 491：虚拟线程与 synchronized](https://openjdk.org/jeps/491) | 这是 JDK 24 的变化；读卸载机制及剩余 pinning 边界 |
| [JEP 514：AOT 命令行](https://openjdk.org/jeps/514) | 阅读训练、生成缓存、运行的条件，区别 Native Image |
| [JEP 515：AOT 方法画像](https://openjdk.org/jeps/515) | 画像从哪里来，如何帮助启动后的 JIT，训练偏差会怎样 |
| [JEP 506：Scoped Values](https://openjdk.org/jeps/506) | JDK 25 正式特性；作为虚拟线程上下文传递的延伸 |
| [JEP 518](https://openjdk.org/jeps/518)、[JEP 520](https://openjdk.org/jeps/520) | JFR 采样、方法计时与追踪演进；结合事件配置和工具文档理解 |

JEP 解释设计动机和变化，不等于完整规范或每个厂商构建的支持承诺。JEP 页面可能在发布后继续补充说明，因此源码问题固定到版本标签。

## HotSpot 源码：回答“这里具体怎么实现”

入口：[OpenJDK jdk-25-ga 源码](https://github.com/openjdk/jdk/tree/jdk-25-ga)。不要一次读完文件；先从实验中的动作找入口函数，画出一条调用链即可。

| 章 | 仓库内源码路径 | 带着什么问题读 |
| --- | --- | --- |
| 03 | src/hotspot/share/classfile/systemDictionary.cpp | 类型查找与加载器关系如何记录？ |
| 03 | src/hotspot/share/oops/instanceKlass.cpp | initialize_impl 如何处理父类、初始化锁与失败？ |
| 04 | src/hotspot/share/oops/markWord.hpp | 传统与紧凑头的信息如何编码？不要脱离配置背位图 |
| 04 | src/hotspot/share/gc/shared/threadLocalAllocBuffer.cpp | TLAB 的分配边界与 refill 如何处理？ |
| 06 | src/hotspot/share/gc/g1/g1BarrierSet.cpp | 引用改写如何与屏障维护联系？ |
| 06～07 | src/hotspot/share/gc/g1/g1ConcurrentMark.cpp | SATB 队列如何参与并发标记？ |
| 07 | src/hotspot/share/gc/g1/g1CollectedHeap.cpp | 从日志的分配失败/回收入口找到路径 |
| 07 | src/hotspot/share/gc/z/ | 区分 load/store barrier 与代际实现，再跟踪一个引用访问 |
| 08 | src/hotspot/share/opto/escape.cpp、macro.cpp | 连接图分析与对象分配消除分别在哪里？ |
| 09 | src/hotspot/share/runtime/synchronizer.cpp | monitor 的进入、退出与竞争入口 |
| 10 | src/java.base/share/classes/java/lang/VirtualThread.java | park、调度、挂载/卸载如何衔接？ |
| 10 | src/hotspot/share/runtime/continuation.cpp | Java 层虚拟线程如何连接到 VM 栈保存/恢复？ |

例如，在自行下载的对应版本源码根目录执行：

~~~text
rg -n "initialize_impl|link_class_impl" src/hotspot/share/oops/instanceKlass.cpp
rg -n "scalar_replace|eliminate_allocate" src/hotspot/share/opto
rg -n "SATB|satb" src/hotspot/share/gc/g1
~~~

## 书籍与进阶工具的使用边界

经典 JVM 书适合建立概念，但 CMS、永久代、偏向锁、JDK 8 GC 默认项与早期虚拟线程结论都要回到 25 核对。不要用书的出版年份替代技术结论验证。

主线不需安装第三方库。后续需要正式基准时用 JMH，研究并发合法结果时用 JCStress，看堆根路径可用 MAT，看字段偏移再选支持当前构建的 JOL。每引入一个工具，先写清要回答的问题和它不能回答的问题。
