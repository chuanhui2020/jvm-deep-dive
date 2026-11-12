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

G1 和分代 ZGC 的深入学习见 [收集器专题](docs/gc/README.md)：包含完整周期、屏障与引用修正、固定版本源码调用链、真实日志片段、持续 cycles 负载、两份独立 Pod 观察配置和 24 个专题追问。第 06、07 章需结合本专题学习。

## 快速开始：Minikube Pod

**主要实验环境是 Minikube 中的 Linux Pod。** 源码、构建、运行、JVM 工具与资源限制都围绕单个实验 Pod 组织。每个项目独立 Maven 构建、独立镜像、独立 Job；根 POM 只用于聚合开发验证。宿主机准备 Minikube、kubectl 及运行中的容器/虚拟机驱动即可；以下以 Docker Linux 容器驱动为例，JDK 25 和 Maven 在镜像中提供。

从仓库根目录执行，命令可用于 PowerShell 或 Bash，无需仓库 PowerShell 启动器：

```sh
minikube start -p jvm-lab --driver=docker --cpus=2 --memory=4096
minikube -p jvm-lab image build -t jvm-lab-env:local labs/env
kubectl --context jvm-lab apply -k k8s/minikube
kubectl --context jvm-lab -n jvm-labs wait --for=condition=complete job/jvm-minikube-smoke --timeout=180s
kubectl --context jvm-lab -n jvm-labs logs job/jvm-minikube-smoke -c lab
```

这是专用 profile `jvm-lab` 和 namespace `jvm-labs`；显式指定 context，避免操作到其他集群。首次构建需要联网下载镜像、Maven 和测试依赖。已有同名 profile 时按原配置启动，保留实际资源记录。

完成环境检查后，选择一个实验，例如 G1 疏散失败：

```sh
minikube -p jvm-lab image build -t jvm-lab-g1-evacuation:local labs/g1-evacuation
kubectl --context jvm-lab -n jvm-labs apply -f labs/g1-evacuation/k8s/job-fault.yaml
kubectl --context jvm-lab -n jvm-labs logs -f job/g1-evacuation-fault -c lab --pod-running-timeout=120s
```

正式实验默认等待 10 秒开始，完成后保留 JVM 300 秒。日志跟随可能持续等待；Ctrl+C 仅停止跟随，不会停止 Pod。用同一个镜像顺序运行 fault/fixed，导出证据后再清理。完整的 22 项入口、资源基线、JFR/GC/dump 导出与故障判断见 [Minikube Pod 主手册](docs/PODS.md)。

## 本机开发与辅助验证

修改源码时可以使用完整 JDK 25，在项目目录执行 `./mvnw verify`；Windows 使用 `./mvnw.cmd verify`。每个项目产物为自己的 `target/lab.jar`，运行时没有第三方依赖，JUnit 仅用于测试。根目录 `./mvnw verify` 一次验证全部项目。

`scripts/*.ps1` 保留为 Windows 本机调试和回归工具，使用说明见 [辅助工具说明](scripts/README.md)。它们不会创建 CPU/memory cgroup 限制，本机验证不代替 Pod 观察。正常学习路径不要求运行这些脚本。

## 仓库布局

- labs/<实验名>/：独立 Maven 项目，包含源码、测试、镜像与 Kubernetes Job。
- pom.xml：只负责一次构建全部项目。
- docs/：章节内容、实验步骤、面试验收与官方链接。
- k8s/minikube/：学习 namespace 与环境 smoke Job。
- scripts/：可选的 Windows 本机开发和回归工具。
- labs/*/target/：每个项目的 class、测试报告和可执行 JAR；不提交。
- build/：本地验证日志、JFR 与 heap dump；不提交。

本机测试结果与适用边界见 [验证记录](docs/VALIDATION.md)。

先从第 01 章开始。每章最后的验收题答不清时，回到该章实验改变一个条件，形成自己的解释。

## GC 异常与面试排障

新增 [GC 异常专题](docs/gc-failures/README.md)：8 个独立 Maven 项目，使实验总数达到 22 个。覆盖分配抖动、静态缓存保留、G1 疏散失败/大对象、ZGC Allocation Stall、加载器泄漏、容器内存和 CPU 配额。每项有 fault/fixed 对照、独立镜像与 Pod 清单，附 24 个面试追问及 [真实验证快照](docs/gc-failures/RESULTS.md)。

```sh
kubectl --context jvm-lab -n jvm-labs apply -f labs/g1-evacuation/k8s/job-fixed.yaml
kubectl --context jvm-lab -n jvm-labs logs -f job/g1-evacuation-fixed -c lab --pod-running-timeout=120s
```

容器 OOMKilled 和 CPU throttling 尚待真实集群验证；本机已复现的 JVM 信号与容器待观察项分别记录。
