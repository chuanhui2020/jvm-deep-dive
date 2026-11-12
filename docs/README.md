# 学习路线：从这里开始

按 **01 → 12 章** 推进。每章固定走一遍：**章节目标 → 配套详解 → Pod 实验 → 本章验收 → 下一章**。

章号决定学习顺序；Fxx 是详解编号，Lxx 是实验编号。按下表进入对应材料即可。

## 0. 先准备环境

1. 阅读 [JDK 25 基线](JDK25.md)，了解规范与实现的边界。
2. 完成 [Minikube 环境检查](PODS.md#1-创建学习环境)，确认环境 Pod 可以运行。
3. 从第 01 章开始；记录自己的 [学习进度](PROGRESS.md)。

## 第一阶段：执行、内存与对象（01～05）

| 章：先读目标 | 再读详解 | 接着做实验 |
| --- | --- | --- |
| [01 运行全景](CURRICULUM.md#第-01-章建立-jvm-与进程的全景模型) | [F01 §1：执行全景](foundations/01-execution.md#1-jvmhotspot-和-jdk-分别负责什么) | [L01 环境](LABS.md#l01确认你究竟运行了什么) |
| [02 字节码与栈帧](CURRICULUM.md#第-02-章class-文件字节码栈帧与调用) | [F01 §2～5：class、栈帧与字节码](foundations/01-execution.md#2-class-文件里有什么) | [L02 字节码](LABS.md#l02亲手追踪操作数栈与-monitor) |
| [03 类加载与初始化](CURRICULUM.md#第-03-章类加载初始化与类身份) | [F03：类加载与初始化](foundations/03-class-loading.md) | [L03 初始化](LABS.md#l03静态初始化为什么没按直觉发生)、[L04 加载器](LABS.md#l04两个一模一样的-class-为什么不是一个类型) |
| [04 内存与对象布局](CURRICULUM.md#第-04-章内存区域对象布局与分配路径) | [F02：内存、对象与分配](foundations/02-memory.md) | [L05 对象布局](LABS.md#l05对象头与浅大小别再死记-12-字节) |
| [05 可达性与引用](CURRICULUM.md#第-05-章可达性引用与资源生命周期) | [F04：可达性与引用](foundations/04-reachability.md) | [L06 引用](LABS.md#l06循环引用与弱引用队列) |

## 第二阶段：GC 机制与收集器（06～07）

| 章：先读目标 | 再读详解 | 接着做实验 |
| --- | --- | --- |
| [06 GC 基础](CURRICULUM.md#第-06-章gc-共性分代屏障与停顿) | [F05：算法、分代与跨代引用](foundations/05-gc.md) | [L07 分配压力](LABS.md#l07分配量大为什么不必泄漏) |
| [07 G1 与分代 ZGC](CURRICULUM.md#第-07-章g1分代-zgc-与选择依据) | [按收集器专题顺序学习](gc/README.md#阅读顺序) | [L08 收集器对照](LABS.md#l08g1-与分代-zgchumongous-对照) |

**第 07 章的顺序：** G1 原理 → G1 周期实验 → 分代 ZGC 原理 → ZGC 周期实验 → 专题追问。故障退化在第 11 章集中练习。

## 第三阶段：优化与并发（08～10）

| 章：先读目标 | 再读详解 | 接着做实验 |
| --- | --- | --- |
| [08 JIT 与 AOT](CURRICULUM.md#第-08-章jit逃逸分析去优化与-aot) | [F06 §6：JIT](foundations/06-diagnostics.md#6-jit-与对象都在栈上的追问) | [L09 逃逸分析](LABS.md#l09new-为什么没有对应的分配量) |
| [09 JMM 与锁](CURRICULUM.md#第-09-章jmm发布锁与竞争) | [F06 §7：JMM 与锁](foundations/06-diagnostics.md#7-jmm锁与虚拟线程的面试边界) | [L10 JMM](LABS.md#l10volatile-可见性与复合操作原子性) |
| [10 虚拟线程](CURRICULUM.md#第-10-章虚拟线程调度与-jdk-25-monitor) | [F06：虚拟线程小节](foundations/06-diagnostics.md#虚拟线程的版本边界) | [L11 虚拟线程](LABS.md#l11一个-carrier十二个独立-monitor) |

## 第四阶段：故障与生产诊断（11～12）

| 章：先读目标 | 再读详解 | 接着做实验 |
| --- | --- | --- |
| [11 内存故障](CURRICULUM.md#第-11-章oom栈溢出与内存预算) | [F06 §4：故障分流](foundations/06-diagnostics.md#4-内存故障的分流) | [L12 堆与直接内存](LABS.md#l12堆-oom-与直接内存-oom)、[L13 栈](LABS.md#l13xss-改变了什么) → [故障步骤 1～5：七个场景](gc-failures/README.md#学习顺序) |
| [12 生产诊断](CURRICULUM.md#第-12-章生产诊断与综合推理) | [F06 §5：证据分流](foundations/06-diagnostics.md#5-cpu-高gc-频繁延迟高如何区分) → [§8：综合回答](foundations/06-diagnostics.md#8-面试时按四层回答) | [L14 综合诊断](LABS.md#l14不用看源码先用证据定位) → [CPU 配额对照](gc-failures/06-cpu-quota.md) → [五个综合案例](INTERVIEW.md#综合案例) |

## 每章怎样算完成

回到该章的“验收”，解释一次正常结果和一个变体；保存参数、日志及结论边界，再点击章末“下一章”。

## 其余文档什么时候看

| 文档 | 使用时机 |
| --- | --- |
| [PODS.md](PODS.md) | 运行、观察和导出证据时查 |
| [LABS.md](LABS.md) | 跟随当前章节进入指定 Lxx 实验 |
| [INTERVIEW.md](INTERVIEW.md) | 学完当前章后自测；最后做综合案例 |
| [PROGRESS.md](PROGRESS.md) | 每章验收后记录掌握度 |
| [REFERENCES.md](REFERENCES.md) | 深挖规范与源码时查 |
| [VALIDATION.md](VALIDATION.md) | 核对工程验证范围与限制时查 |

**开始学习：** [第 01 章：运行全景](CURRICULUM.md#第-01-章建立-jvm-与进程的全景模型)。
