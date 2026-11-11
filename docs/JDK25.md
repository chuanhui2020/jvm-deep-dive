# JDK 25 学习基线

## 讨论边界

本仓库的实验面向 **JDK 25 / HotSpot / 64 位**。规范中的“必须如此”、HotSpot 的实现、某次运行的观测值，是三个层次。面试回答先说明层次；GC、对象大小、编译优化和诊断命令尤其不能混用。

源码使用 javac --release 25 编译，class 文件主版本为 **69**。这不代表所有 JDK 25 厂商构建拥有相同收集器、相同补丁或相同默认参数。每份实验记录都应附 java --version、VM 名称、平台及完整启动命令。

## 必须纠正的旧结论

| 旧说法 | JDK 25 的准确表达 | 验证入口 |
| --- | --- | --- |
| JVM 内存就是堆加栈 | 还包括类元数据、Code Cache、线程栈、GC 数据结构、直接内存及其他本地分配 | 第 04、11 章；NMT |
| 方法区就是永久代 | 方法区是规范概念；HotSpot 使用 Metaspace 等实现类元数据存储，永久代早已移除 | 第 04 章 |
| 对象头永远 12 字节 | 取决于模式；25 的紧凑对象头是产品特性，**默认关闭**，可以显式启用 | JEP 519；layout |
| 压缩普通对象指针等于压缩类指针 | 它们描述不同引用；布局还受对象对齐、数组长度等影响 | PrintFlagsFinal；layout |
| ZGC 通过 ZGenerational 选择分代/非分代 | 非分代模式已在 JDK 24 移除；25 使用 -XX:+UseZGC，不再添加旧开关 | JEP 490 |
| Shenandoah 默认已经分代 | JDK 25 将分代模式产品化，默认模式仍为单代；可用性依厂商与平台而异 | JEP 521 |
| synchronized 内阻塞一定 pin 虚拟线程 | JDK 24 的 JEP 491 已解除 monitor 引起的这类 pinning；JNI/本地调用等情况仍需具体诊断 | virtual；JFR |
| 锁必然沿无锁→偏向→轻量→重量升级 | 不应把旧 HotSpot 路径当成 25 的状态机；偏向锁已移除，实现还在演进 | 第 09 章 |
| 逃逸分析就是把对象搬到栈上 | HotSpot 常见结果是标量替换与分配消除，并非普遍的对象栈上分配承诺 | jit |
| 低停顿收集器没有 STW | 并发承担更多工作不等于没有停顿，也不保证业务延迟上限 | 第 06、07 章 |
| CMS 是现代生产首选 | CMS 已移除；只作为历史对比，不提供失效参数练习 | 第 07 章 |
| AOT 就是把 Java 变成无需 JVM 的原生程序 | JDK 25 的 AOT 缓存与训练流程仍服务于 JVM；与 Native Image 区分 | JEP 514、515 |

## 实际检查

~~~powershell
java --version
javac --version
java -XX:+PrintFlagsFinal -version 2>&1 | Select-String 'UseG1GC|UseZGC|UseCompactObjectHeaders|UseCompressedOops|UseCompressedClassPointers|ObjectAlignmentInBytes'
java -XX:+UseZGC -version
java -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational -version
~~~

最后一条是**能力探测**，不是必过步骤；不支持时记录错误，继续 G1/ZGC 主线。不要为了复现文章而随意加入 UnlockExperimentalVMOptions 或过期参数。

## 本仓库的本机验证记录

2026-11-11：Windows 64 位，Oracle HotSpot **25.0.5.0.2+1-LTS-2**。默认探测到 UseG1GC=true、UseCompactObjectHeaders=false、ObjectAlignmentInBytes=8。ZGC 可启动；当前构建不识别 ShenandoahGCMode=generational，因此**没有宣称已验证分代 Shenandoah 实验**。

这些值只描述这台机器，不替代你的环境探测。官方版本依据见 [参考资料](REFERENCES.md)。
