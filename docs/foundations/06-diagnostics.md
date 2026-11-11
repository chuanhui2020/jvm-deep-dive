# F06：参数、故障与面试综合推理

对应主课程 08～12。本篇把前面的区域、执行、加载和 GC 知识连接到参数选择和故障判断。面试中先解释证据为什么能区分候选原因，再提出改动。

## 1. 调参前先定义问题

“应用慢”可能是吞吐下降、平均延迟升高、P99 变差或启动变慢。每个目标对应的测量窗口与资源代价不同。没有负载、版本、限制和目标的参数清单，不是一份可以复现的调优方案。

记录以下条件：JDK 厂商和完整构建、VM、收集器、完整参数、CPU/内存限额、负载数据、预热方法、持续时间、采样配置。Pod 中还要记录 requests/limits；CPU 配额影响编译、GC 和业务线程竞争。

```powershell
./scripts/run-lab.ps1 env
java --version
java -XX:+PrintFlagsFinal -version 2>&1 | Select-String 'UseG1GC|UseZGC|NewRatio|SurvivorRatio|MaxTenuringThreshold|MetaspaceSize|UseTLAB'
```

PrintFlagsFinal 反映这条新启动命令的参数结果，**不是另一个正在运行服务的配置**。检查服务应对实际 PID 使用 jcmd VM.flags/VM.command_line；先确认自己的权限和目标进程。

## 2. 常用参数的准确含义

下表用于理解，不表示应一次全部设置。参数是否被某个收集器采用、默认值和限制应查 [JDK 25 java 手册](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html)并核对目标构建。

| 参数 | 主要作用 | 常见误区 |
| --- | --- | --- |
| `-Xms`、`-Xmx` | Java 堆初始/最小与最大边界 | 等同进程 RSS 或容器所有内存 |
| `-Xss` | Java 线程栈大小相关配置 | 越大越好；能修复无限递归 |
| `-XX:MaxMetaspaceSize` | 类元数据内存上限约束 | 能修复保留旧加载器的根因 |
| `-XX:MetaspaceSize` | 元数据 GC 初始高水位相关阈值 | 固定预分配的元空间大小 |
| `-XX:MaxDirectMemorySize` | 直接缓冲区内存的显式上限 | 覆盖任意本地库的一切 malloc |
| `-XX:NewRatio`、`-Xmn` | 适用收集器的代容量配置 | 对所有收集器有相同含义 |
| `-XX:SurvivorRatio` | 适用策略中 Eden 与单个 Survivor 的比例 | Eden 与两个 Survivor 总和的比例 |
| `-XX:MaxTenuringThreshold` | 晋升年龄策略的上限配置 | 所有对象固定达到该次数才晋升 |
| `-XX:TargetSurvivorRatio` | Survivor 目标使用率相关策略 | 保证 Survivor 永远不超过某百分比 |
| `-XX:MaxGCPauseMillis` | 适用收集器的停顿目标 | 最大业务响应时间保证 |
| `-XX:+UseTLAB` | 启用线程本地堆分配缓冲 | 在栈上分配所有新对象 |
| `-XX:NativeMemoryTracking=summary` | 启动时启用 NMT 摘要统计 | 完整涵盖任何本地库分配且无开销 |
| `-XX:+HeapDumpOnOutOfMemoryError` | 在相应 OOM 情况下生成堆证据 | 容器杀进程时必然能获得 dump |
| `-XX:+DisableExplicitGC` | 忽略显式 GC 请求的相关控制 | 禁止 GC，或可不分析就作为优化默认项 |

### 代比例的算术与适用条件

仅在适用、固定容量的教学模型中，若老年代:年轻代为 2:1，则年轻代约占二者总和的三分之一；若年轻代内部 Eden:单个 Survivor 为 8:1，两个 Survivor 等大，则三者比例为 8:1:1。

这只是比例关系的推导。对齐、自适应策略、收集器布局和可用目标空间都会影响实际容量。G1 会按目标与预测调整年轻 region 数，盲目固定年轻代可能限制其调节能力。ZGC 不应套传统 Eden/Survivor 的调参配方。[JDK 25 G1 调优](https://docs.oracle.com/en/java/javase/25/gctuning/garbage-first-garbage-collector-tuning.html)

## 3. 教程中的旧参数如何处理

| 旧材料中的主题 | 本项目对 JDK 25 的处理 |
| --- | --- |
| PermSize、MaxPermSize | 永久代历史背景；不作为当前启动参数 |
| CMS、ParNew 组合 | 学习历史并发标记方案；不提供已移除的启用命令 |
| UseBiasedLocking、固定偏向锁升级图 | 不用旧的偏向锁配置解释当前锁实现 |
| AggressiveOpts、UseFastAccessorMethods | 不复制到 JDK 25 的参数清单；先查存在性 |
| ZGenerational 选择分代/非分代 | JDK 25 ZGC 已只保留分代模式，使用 UseZGC |
| PretenureSizeThreshold 让大对象都直接进老年代 | 适用范围依收集器；不能作为 G1 humongous 的通用开关 |
| TLAB 占 Eden 的固定百分比 | 不用“预留固定百分比给所有线程”解释实际 TLAB 管理 |
| 大页、优先级等系统参数 | 同时依赖 OS 权限、配置和负载；不作为 JVM 入门通用优化 |

区分“参数存在”“被当前收集器采用”“在这个负载下有效”三个判断。PrintFlagsFinal 出现一个字段，不证明调整它一定有性能效果。版本边界可参阅 [JDK25 校准表](../JDK25.md)，CMS 移除的依据见 [JEP 363](https://openjdk.org/jeps/363)。

## 4. 内存故障的分流

| 症状或消息 | 首先检查 | 可用证据与实验 | 为什么不能先加 Xmx |
| --- | --- | --- | --- |
| Java heap space | 存活集、堆预算、分配压力 | GC 日志、heap dump；heap-oom | 要区分正常容量与保留泄漏 |
| Direct buffer memory | 直接缓冲上限和保留者 | buffer pool、NMT、堆中包装对象；direct | 可能是显式直接内存上限 |
| Metaspace | 类数量、加载器与动态生成 | 类加载/卸载趋势、加载器根路径 | 旧加载器保留链不会随加堆消失 |
| StackOverflowError | 递归、调用深度、栈配置 | 栈转储；stack | 问题不是 Java 堆容量 |
| unable to create native thread | 线程数、系统限制、本地预算 | 线程统计、OS 配额、NMT | 加堆还可能挤压本地余量 |
| 容器 OOMKilled | 终止原因和容器内存预算 | Pod 事件、容器统计、进程 RSS | 进程可能来不及抛 Java OOM |

OOM 消息是分流线索，不是完整根因证明。还要确认它属于哪个进程和哪个时间窗口，并保留首次故障。GC overhead limit exceeded 等情形也依收集器与配置，不应概括成所有 OOM 的同一触发机制。

```powershell
./scripts/run-lab.ps1 heap-oom
./scripts/run-lab.ps1 direct
./scripts/run-lab.ps1 stack -JvmArgs @('-Xss256k')
./scripts/run-lab.ps1 stack -JvmArgs @('-Xss512k')
```

这三个实验会捕获预期 Error 以打印教学输出；不能据此认为生产代码捕获 OOM 后可以安全继续服务。

## 5. CPU 高、GC 频繁、延迟高如何区分

**CPU 高：** 对齐进程与线程 CPU 变化、线程采样和 JFR execution sample。业务循环、重试、编译、GC 都可能占 CPU。线程栈中的方法名只是瞬时位置，重复样本与消耗量才帮助定位。

**GC 频繁：** 同时看分配率、存活集、暂停与 CPU。若回收后占用稳定且很低，优先检查无效分配和预算；若回收后基线持续上升，再调查保留路径。不能仅凭次数多就宣布泄漏。

**P99 高：** 对齐 GC/safepoint、锁、排队和下游耗时。即使 GC 暂停没有增长，也可能因并发 GC 与业务争用 CPU 间接影响延迟。JVM 单项正常不代表端到端请求正常。

```powershell
./scripts/run-lab.ps1 diagnose -JvmArgs @('-XX:NativeMemoryTracking=summary','-XX:StartFlightRecording=filename=build/interview-cpu.jfr,settings=profile,dumponexit=true') -LabArgs @('cpu','30')
# 另一终端，替换为实际 PID
$labPid = 12345
jcmd $labPid Thread.print -l
jcmd $labPid GC.heap_info
jcmd $labPid VM.native_memory summary
```

日志、JFR 和 NMT 各有观察范围；命令成本与采样限制查 [JDK 25 故障诊断工具](https://docs.oracle.com/en/java/javase/25/troubleshoot/diagnostic-tools.html)。

## 6. JIT 与“对象都在栈上”的追问

解释分配减少时，应描述逃逸分析、内联、标量替换和分配消除的关系。对象是否逃逸取决于优化器能看见的使用方式，不能仅按源码“有没有 return”判断。

比较默认与关闭逃逸分析前后的分配计数时，还需确认预热、编译状态和结果消费。某次 localBytes 非零不证明该优化永远无效，近零也不证明对象被实际搬到机器栈。

正式基准应有独立进程、预热、参数控制和抗死代码消除措施。本项目 jit 实验用于解释机制，不应据其一次耗时评价生产吞吐。继续学习见 [主课程第 08 章](../CURRICULUM.md#第-08-章jit逃逸分析去优化与-aot)。

## 7. JMM、锁与虚拟线程的面试边界

**运行时内存区域与 JMM 是两张不同的图。** 前者解释状态和数据组织；后者规定线程之间允许看到哪些行为。volatile 发布需要建立相应 happens-before 链，不能说成“把所有数据刷到主存就万事大吉”。[JLS 25 §17](https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html)

`volatile++` 不原子；synchronized 能保护临界区复合不变量，但前提是所有相关访问遵守同一同步协议。CAS 不自动提供跨多个字段的事务，也不消除 ABA 或高竞争的重试成本。

### volatile 发布的具体推演

假设写线程先执行 `payload = 42`，再执行 `ready = true`；ready 是 volatile。读线程观察到对应的发布后，再读 payload。需要讲出的链条是：

```text
写 payload → 程序顺序 → 写 volatile ready
                              ↓ 同步关系
读 payload ← 程序顺序 ← 读 volatile ready
```

利用 happens-before 的传递性，才能推导发布前普通字段写入的可见性。把 ready 去掉 volatile 后，循环中插入 Thread.sleep 并不能重新建立这条同步链；一次运行看似正常不能证明安全。

双重检查锁的常见实现需要对实例引用使用 volatile 来支持正确发布，但 volatile 不会把后续所有业务字段操作自动变成事务。final 字段有构造期初始化安全规则，同样不意味着引用对象的可变状态永久线程安全；还要避免错误的 this 逃逸。

### monitor、sleep 和 wait 的区别

| 行为 | 是否要求先持有目标 monitor | 对该 monitor 的影响 |
| --- | --- | --- |
| 进入 synchronized 块/方法 | 进入时尝试获得 | 同一时刻仅一个线程持有；可重入 |
| `Thread.sleep` | 不要求 | 不因 sleep 而释放已经持有的 monitor |
| `Object.wait` | 要求持有该对象 monitor | 等待期间释放该 monitor，返回前重新获得 |
| `notify/notifyAll` | 要求持有该对象 monitor | 不会立即把锁交给被通知者；仍需等锁可用 |

wait 返回后应在循环中重新检查条件，不能把通知当作条件一定成立的保证。死锁、饥饿、锁竞争和正常条件等待是不同现象，线程状态不能单独替代等待关系分析。[JLS 25 §17.2、§17.3](https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.2)

JDK 25 已包含 monitor 相关虚拟线程卸载改进。持锁等待可以让出 carrier，不代表同一锁的互斥消失，也不代表本地调用等剩余 pinning 都消失。CPU 密集型任务不会因虚拟线程数量增加而获得更多 CPU 核。[JEP 491](https://openjdk.org/jeps/491)

```powershell
./scripts/run-lab.ps1 jmm
./scripts/run-lab.ps1 virtual
```

virtual 实验用唯一 carrier 和每任务独立 monitor 验证协调能完成；把 monitor 改为共享锁却保留锁内全员等待，会制造应用层死锁，这是不同问题。

## 8. 面试时按四层回答

以“堆只用一半，容器为什么被杀”为例：

1. **定义现象**：确认是容器 OOMKilled，而非应用抛 Java heap space；对齐 used/committed、RSS 和容器统计口径。
2. **给出机制**：堆外还有元数据、线程、代码缓存、直接内存与其他本地开销，Xmx 不能限制全部进程内存。
3. **提出证据**：比较线程数、buffer pool、NMT 类别和 OS/容器数据，必要时关联实际业务保留路径。
4. **验证改动**：调整明确的资源生命周期或预算，重放相同负载，同时检查吞吐、延迟与资源峰值。

这四层比背“加大 Xmx、换 ZGC”更可评估。证据不够时，明确假设和下一项能区分候选原因的检查。

**面试自测与参考要点：**

1. **Xms=Xmx 一定更快吗？** 减少堆伸缩可能有收益，但启动、提交/触页、总内存预算和负载仍需验证，不能做无条件结论。
2. **把年轻代调大会怎样？** 分配缓冲空间可能增加，回收频率和每次存活处理成本可能变化；实际效果依收集器、存活率与目标。
3. **为什么关闭显式 GC 也要评估？** 它改变请求处理方式，可能与直接缓冲等资源压力下的路径产生交互；先定位调用者和触发原因。
4. **死锁与线程数多怎样区分？** 死锁需要等待环或诊断证据；大量 WAITING 也可能是正常空闲线程或下游等待。
5. **怎样宣称一次调优成功？** 在可比负载与资源边界下，目标指标改善且代价可接受，多轮结果支持结论；保留反例与回退条件。

继续完成 [48 个主课程追问与五个案例](../INTERVIEW.md)，并用 [进度表](../PROGRESS.md)记录自己的证据。
