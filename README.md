# JVM 深入学习：JDK 25

面向已有多年 Java 开发经验、准备资深岗位面试的工程师。按章节学习，不绑定每周时长。目标是能从机制推导行为、用实验验证判断、用证据定位生产问题。

**技术基线：JDK 25 + HotSpot；Java SE 规范与 HotSpot 实现分开讨论。** 不把旧版调优口诀当成当前事实。不承诺穷尽所有面试题；以下主线覆盖资深面试的核心机制、常见追问和诊断场景。

## 如何使用

每章按这个顺序完成：**先读原理 → 写下预测 → 运行实验 → 改一个变量 → 解释证据 → 脱稿回答追问**。运行成功不等于理解；必须能解释为什么、适用条件是什么、什么证据会推翻你的结论。

1. 阅读 [版本基线](docs/JDK25.md)，先校准旧知识。
2. 先读 [基础详解与面试推理](docs/foundations/README.md)：从栈帧、内存、类加载到 GC 算法，补齐基础机制。
3. 按 [章节课程](docs/CURRICULUM.md) 推进；每章都配有必读详解、源码与实验手册。
4. 用 [实验手册](docs/LABS.md) 动手，保存自己的预测、日志和反例。
5. 用 [面试追问与案例](docs/INTERVIEW.md) 验收，用 [进度表](docs/PROGRESS.md) 标记薄弱处。
6. 按 [官方资料与源码导航](docs/REFERENCES.md) 深挖，不要求从头通读 HotSpot 源码。

基础详解以 **Java SE 25 正式版 JVMS/JLS + JDK 25 官方实现与工具文档**校准，参考 JavaGuide 入门教程安排主题。包含 6 篇详解和 30 个基础面试自测题，覆盖逐指令栈状态、内存区域、加载器与初始化、根可达性、GC 算法/分代及参数诊断。正文区分规范语义、HotSpot 实现和单次实验观察；历史参数不作为 JDK 25 推荐配置。

## 章节地图

| 章 | 主题 | 必做实验 |
| --- | --- | --- |
| 01 | JVM、规范、进程与观测工具 | env：确认运行时与参数 |
| 02 | class 文件、字节码、栈帧与方法调用 | bytecode：javap 读指令 |
| 03 | 加载、链接、初始化与类身份 | init、loader：初始化时机与不同加载器 |
| 04 | 内存区域、对象布局与分配 | layout：Instrumentation 测浅大小 |
| 05 | 可达性、引用与对象生命周期 | references：循环引用与弱引用队列 |
| 06 | GC 共性：分代、屏障、根与停顿 | gc：分配速率与存活集 |
| 07 | G1、分代 ZGC 与收集器选择 | gc：同负载对比收集器 |
| 08 | JIT、逃逸分析、去优化与 AOT | jit：线程分配字节数对照 |
| 09 | JMM、发布、锁与竞争 | jmm：可见性与复合操作原子性 |
| 10 | 虚拟线程与 JDK 25 的锁行为 | virtual：单 carrier 下独立 monitor |
| 11 | OOM、栈溢出与堆外内存 | heap-oom、direct、stack |
| 12 | 生产诊断、性能证据与综合面试 | diagnose：CPU、死锁、内存保留与 JFR |

## 快速开始（PowerShell）

安装完整 **JDK 25**，将 JAVA_HOME 指向它；未设置时脚本从 PATH 查找。脚本同时检查 java 与 javac 的主版本，拒绝其他版本。实验运行时无第三方库和预览特性；构建使用 Maven，JUnit Jupiter 仅用于测试，首次构建需要联网下载依赖。

~~~powershell
java -version
javac -version
./mvnw.cmd test
./mvnw.cmd verify
./scripts/run-lab.ps1 env
./scripts/run-lab.ps1 init
./scripts/run-lab.ps1 layout
./scripts/verify-labs.ps1
~~~

每个实验都是独立 Maven 项目：`labs/<实验名>/` 内含自己的 `pom.xml`、Maven Wrapper、源码、测试、Dockerfile 和 Pod Job 配置。根 POM 仅聚合构建，不是各项目的 parent；复制一个项目到仓库外也能独立构建。所有项目产物统一命名为各自的 `target/lab.jar`。

例如只构建、运行并制作 GC 实验镜像：

~~~powershell
cd labs/gc
./mvnw.cmd verify
java -Xms64m -Xmx64m -XX:+UseG1GC '-Xlog:gc*' -jar target/lab.jar 60000 4
docker build -t jvm-lab-gc:local .
~~~

回到仓库根目录后仍可使用原来的启动入口：

~~~powershell
./scripts/run-lab.ps1 gc -JvmArgs @('-Xms64m','-Xmx64m','-XX:+UseG1GC','-Xlog:gc*') -LabArgs @('60000','4')
~~~

**部署与观察 Pod：** 见 [Pod 观察手册](docs/PODS.md)。每个项目独立生成镜像、独立创建一个 Job/Pod，可分别设置 CPU、内存、JVM 参数、实验参数并采集 JFR。镜像保留完整 JDK 25，支持在容器中直接执行 jcmd/jfr。

Linux/macOS 可使用 [实验手册中的原生命令](docs/LABS.md#原生命令linuxmacos)，同样需要 JDK 25。课程采用经典 Java 源码组织，便于关注 JVM 机制。

## 仓库布局

- labs/<实验名>/：独立 Maven 项目，包含源码、测试、镜像与 Kubernetes Job。
- pom.xml：只负责一次构建全部项目。
- docs/：章节内容、实验步骤、面试验收与官方链接。
- scripts/：版本检查、编译、运行与语义验证。
- labs/*/target/：每个项目的 class、测试报告和可执行 JAR；不提交。
- build/：本地验证日志、JFR 与 heap dump；不提交。

本机测试结果与适用边界见 [验证记录](docs/VALIDATION.md)。

先从第 01 章开始。每章最后的验收题答不清时，回到该章实验改变一个条件，形成自己的解释。
