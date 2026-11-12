# 官方资料与源码导航

先阅读章节与跑实验，再带着一个具体问题查资料。不把“读过多少页源码”当作学习完成度。以下规范与工具文档固定在 **Java SE/JDK 25**；HotSpot 源码固定到 **jdk-25-ga** 标签，而不是不断变化的 master。厂商补丁构建仍可能有差异。

## 规范：回答“必须是什么”

- [JVMS 25](https://docs.oracle.com/javase/specs/jvms/se25/html/index.html)：第 2 章运行时结构，第 4 章 class 文件，第 5 章加载/链接/初始化，第 6 章指令。对应课程 01～04。
- [JLS 25 第 12 章](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html)：类初始化与程序执行，对应第 03 章。
- [JLS 25 第 17 章](https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html)：线程、锁与 JMM；重点读 17.4 的 happens-before 和 17.5 的 final 字段规则，对应第 09 章。

## 基础详解的依据与导航

[基础详解目录](foundations/README.md)提供覆盖关系与面试自测。主题组织参考 [JavaGuide JVM 入门](https://javaguide.cn/java/jvm/jvm-intro.html)，技术结论以本节官方资料为准，例子和图表独立编写。

| 详解 | 优先核对的正式依据 | 要避免混淆的层次 |
| --- | --- | --- |
| F01 | JVMS 25 §2.6、§4、§6；JLS 25 §16、§17 | 逻辑栈帧、编译器输出、JIT 后的物理布局 |
| F02 | JVMS 25 §2.5；JLS 25 §4、§12.5；JDK 25 java/NMT 文档 | 方法区、Metaspace、Class 镜像和进程内存 |
| F03 | JVMS 25 §5；JLS 25 §12、§13；ClassLoader 25 API | 默认值、ConstantValue、普通静态赋值和类型身份 |
| F04 | java.lang.ref 25；System/Reference/Cleaner API；JLS 25 §12.6～12.7 | 强可达、回收条件、队列通知与及时资源释放 |
| F05 | JDK 25 GC 指南；jdk-25-ga 的 G1 实现；JEP 490/521 | GC 算法、分代策略、具体收集器及临界比较 |
| F06 | JDK 25 java/jcmd/诊断文档；JLS 25 §17；JEP 491 | 参数存在、实际生效、目标指标改善 |

正文链接指向相应章节。JVMS 不指定 GC 算法或物理对象布局；这些内容不能伪装成规范强制要求。文档中涉及 JDK 25 的版本结论不延伸为 JDK 26 或厂商补丁的默认承诺。

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

## G1 / 分代 ZGC 专题源码导航

[专题目录](gc/README.md)按问题连接设计、实现、日志与面试，所有源码链接固定到 `jdk-25-ga`。

| 主线 | 源码入口 | 需要区分的职责 |
| --- | --- | --- |
| G1 字段写入 | g1BarrierSet.inline.hpp / g1BarrierSet.cpp / g1ConcurrentRefine.cpp | SATB 旧值与来源卡、脏卡队列/refinement |
| G1 暂停与对象复制 | g1CollectedHeap.cpp / g1YoungCollector.cpp / g1ParScanThreadState.cpp | CSet、外部根、转发竞争、失败处理 |
| G1 并发周期与策略 | g1ConcurrentMarkThread.cpp / g1ConcurrentMark.cpp / g1Policy.cpp / g1IHOPControl.cpp | 存活信息、候选收益、时间和空间预算 |
| ZGC 调度与代周期 | zDirector.cpp / zDriver.cpp / zGeneration.cpp | Minor/Major、Young/Old 的启动和阶段协调 |
| ZGC 颜色与屏障 | zAddress.hpp / zBarrier.inline.hpp / zBarrier.cpp / zStoreBarrierBuffer.cpp | 编码引用、有效地址、p 与 prev、自修复 |
| ZGC 跨代和搬迁 | zRemembered.cpp / zRememberedSet.hpp / zRelocate.cpp / zForwarding.cpp | 字段位图、页生命周期和有效转发映射 |

[JEP 439](https://openjdk.org/jeps/439)解释分代 ZGC 的设计；[JEP 522](https://openjdk.org/jeps/522)交付在 **JDK 26**，只作 G1 后续双卡表改进对照，不能当成本项目 25 的实现。官方网页和 JEP 会演进，源码级结论需与标签核对。

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

## GC 异常取证对应入口

[故障专题](gc-failures/README.md)把 JDK 25 故障文档和八个可执行实验对照：

- [JDK 25 内存排障](https://docs.oracle.com/en/java/javase/25/troubleshoot/troubleshooting-memory-leaks.html)：堆、直接内存和元空间错误的分类。
- [G1 25 调优](https://docs.oracle.com/en/java/javase/25/gctuning/garbage-first-garbage-collector-tuning.html)：Evacuation Failure、大对象和 Full 的取证入口。
- [固定 jdk-25-ga ZPageAllocator](https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/z/zPageAllocator.cpp)：分配等待实现，配合 ZGC 专题调用链阅读。
- [Linux cgroup v2](https://docs.kernel.org/admin-guide/cgroup-v2.html)及 [Kubernetes 资源限制](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/)：cpu.stat、memory.events、预算与容器状态；它们属于操作系统/编排语义，不是 JVMS 规定。
