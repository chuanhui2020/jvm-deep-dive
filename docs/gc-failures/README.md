# GC 异常实验：从故障信号到修复对照

**导航：** [学习路线](../README.md) · [第 11 章](../CURRICULUM.md#第-11-章oom栈溢出与内存预算) · [第 12 章](../CURRICULUM.md#第-12-章生产诊断与综合推理)

这组实验补全“会讲 GC 机制”到“能排查异常”的实践。每个场景都是独立 Maven 项目，自带 Wrapper、源码、测试、Dockerfile 和 fault/fixed Job。它们不是生产调优模板，也不保证所有机器出现完全相同的周期数。

本轮基线：Java SE/JDK 25、HotSpot；实现解释固定 `jdk-25-ga`。核对日期为 2026-11-12。规范不要求使用 G1/ZGC，也不规定本文的 GC 调度、日志或参数。[JVMS 25](https://docs.oracle.com/javase/specs/jvms/se25/html/index.html)

## 学习顺序

第 11 章完成步骤 1～5，第 12 章完成步骤 6。每篇按 **故障预测 → fault → 证据 → fixed → 面试追问** 推进。

| 步骤 | 阅读 | 实验 | 所在主章 |
| --- | --- | --- | --- |
| 1 | [分配与保留](01-allocation-retention.md) | `gc-churn / gc-retention` | 11 |
| 2 | [G1 退化](02-g1-pathologies.md) | `g1-evacuation / g1-humongous` | 11 |
| 3 | [ZGC 等待](03-zgc-stall.md) | `zgc-stall` | 11 |
| 4 | [加载器与元空间](04-metaspace.md) | `metaspace-leak` | 11 |
| 5 | [容器内存预算](05-container-budget.md) | `container-budget` | 11 |
| 6 | [CPU 配额](06-cpu-quota.md) | `cpu-throttle` | 12 |

完成第 11 章的前 5 步后，回本章验收，再进入第 12 章。日志快照 [RESULTS](RESULTS.md) 在核对事件时按需查阅。

## 1. 先看八个场景与证据范围

| 实验项目 | fault 制造什么 | fixed 改变什么 | 详细手册 |
| --- | --- | --- | --- |
| [gc-churn](../../labs/gc-churn/README.md) | 大量短命分配，根集合有界 | 复用固定缓冲区，减少分配 | [分配与保留](01-allocation-retention.md) |
| [gc-retention](../../labs/gc-retention/README.md) | 静态集合不断保留数组，最终堆 OOM | 容量限制与移除旧引用 | [分配与保留](01-allocation-retention.md) |
| [g1-evacuation](../../labs/g1-evacuation/README.md) | 高存活、突发分配与紧张的疏散预算 | 降低保留量和突发量 | [G1 退化](02-g1-pathologies.md) |
| [g1-humongous](../../labs/g1-humongous/README.md) | 保留相间大数组，再申请连续大数组 | 将同等请求 payload 分段 | [G1 退化](02-g1-pathologies.md) |
| [zgc-stall](../../labs/zgc-stall/README.md) | 小堆内多线程高速分配与保留 | 入口节流加较小存活集 | [ZGC 等待](03-zgc-stall.md) |
| [metaspace-leak](../../labs/metaspace-leak/README.md) | 静态缓存保留大量定义加载器 | 释放过期加载器引用 | [元空间](04-metaspace.md) |
| [container-budget](../../labs/container-budget/README.md) | 堆小，但触碰并保留大量直接内存页 | 限制直接内存保留量 | [容器预算](05-container-budget.md) |
| [cpu-throttle](../../labs/cpu-throttle/README.md) | 相同分配负载放入 100m CPU 配额 | 仅放宽 CPU limit 到 1 核 | [CPU 限流](06-cpu-quota.md) |

真实结果见 [验证快照](RESULTS.md)。本机 JVM 结果与 Pod 结果分别记录：当前没有可连接的 Docker 引擎或 Kubernetes API，因此 **OOMKilled 和真实 cgroup CPU throttling 尚未实测**；相应完整负载、清单和取证步骤已提供。

## Minikube 主流程

先按 [Pod 主手册](../PODS.md)完成学习 namespace 和环境 smoke。然后在 Minikube 节点构建本实验镜像，选择 fault Job，在工作期观察 GC/JFR，导出后再运行 fixed。宿主机无需 PowerShell 启动器：

```sh
minikube -p jvm-lab image build -t jvm-lab-g1-evacuation:local labs/g1-evacuation
kubectl --context jvm-lab -n jvm-labs apply -f labs/g1-evacuation/k8s/job-fault.yaml
kubectl --context jvm-lab -n jvm-labs logs -f job/g1-evacuation-fault -c lab --pod-running-timeout=120s
```

各场景的本机脚本示例只用于开发验证；Pod 对照以各自 job-fault.yaml/job-fixed.yaml 的参数和实际 Pod 状态为准。

## 2. 一个场景应该交付什么

1. 明确症状：哪个进程、哪种限制、哪个时间窗口。
2. 保存版本、完整 JVM 参数、程序参数和资源配置。
3. 用日志、对象图或 cgroup/容器状态证明现象，而非只看退出码。
4. 提出至少两个候选原因，并说明下一项证据怎样排除其中一个。
5. 做 fault/fixed 对照，解释改变了哪些条件，哪些结果仍不能保证。
6. 记录新的资源代价与回退条件，再回答每篇的面试追问。

“GC 次数多”“Full GC”“内存泄漏”“Java OOM”“OOMKilled”不是同义词。一个 fault 可能没有 OOM，也可能被教学代码捕获后正常退出；一个 fixed 也可能有合理的 GC，不能用次数必须为零验收。

## 3. 本机辅助取证（可选）

从仓库根目录执行：

```powershell
./scripts/run-gc-fault.ps1 -Lab g1-evacuation -Mode fault
./scripts/run-gc-fault.ps1 -Lab g1-evacuation -Mode fixed
```

脚本按 [场景目录](../../scripts/gc-faults.json)构建本项目并启动一个受超时限制的子 JVM。默认在 `build/faults/<场景>-<模式>-<时间>/` 保存：

| 文件 | 作用 |
| --- | --- |
| result.json | 完整参数、退出状态与从保留日志提取的信号计数 |
| stdout.log / stderr.log | 程序的模式、PID、进度和异常输出 |
| jvm.log 与轮转文件 | GC/类卸载日志，包含时间、级别、tag、GC ID |
| oom.hprof（若 JVM 成功产生） | OOM 当时的 Java 堆证据，不是全部本地内存 |
| tmp/ | 当前运行的 JVM 临时目录 |

目录必须新建或为空，避免旧 dump/轮转文件混入另一组结果。脚本隔离 JAVA_TOOL_OPTIONS、JDK_JAVA_OPTIONS、_JAVA_OPTIONS；自定义 JVM 参数显式通过 `-JvmArgs` 追加。

隔离覆盖 JDK 检查、Maven 构建和实际实验，返回或抛错时恢复调用终端的原环境；通用 run-lab 与 verify-labs 采用相同规则，避免系统预设收集器混入对照。相对 EvidenceDir 按 PowerShell 当前目录解析，支持含空格路径。[JDK 25 启动选项](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html)

result.json 的 `completed` 只说明子进程在超时前退出；`workloadCompleted` 表示程序输出了完成标记，`exitCode` 和 `caughtOom` 仍需分别检查。`timedOut`、开始时间和经过毫秒用于核对窗口。`gcLogEvidenceAvailable=false` 时事件计数为 null，表示日志证据不可用；有 GC 日志且某个事件未观察到，才记录零。即使日志存在，用户覆盖日志级别/选择器或轮转也会缩小证据范围，不能据零推断绝无对应事件。

构建后可执行可重复的取证回归，无需 Pester，也不触发故意耗尽内存的负载：

```powershell
./scripts/verify-gc-evidence.ps1
```

该检查覆盖完成行与开始行计数口径、ZGC 统计表和事件的区分、无日志、相对路径/引号、环境隔离及恢复、观察窗口、超时和防覆盖。验证证据仍写入忽略的 build/。

```powershell
# 先构建后可跳过重复构建；这里 LabArgs 不包含 mode，脚本会自动补上
./scripts/run-gc-fault.ps1 -Lab zgc-stall -Mode fixed -SkipBuild -LabArgs @('5','4','2','64')
./scripts/run-gc-fault.ps1 -Lab gc-retention -Mode fault -JvmArgs @('-XX:StartFlightRecording=filename=record.jfr,settings=profile,dumponexit=true')
```

程序的工作目录是该次证据目录，因而上述相对 `record.jfr` 也保存在其中。脚本不设置操作系统容器限制；在 Windows 上运行 container-budget 不能证明 OOMKilled，运行 cpu-throttle 不能证明 CFS 限流。

### 为 jcmd 留出时间

```powershell
./scripts/run-gc-fault.ps1 -Lab gc-retention -Mode fault -StartDelaySeconds 10 -HoldSeconds 60 -TimeoutSeconds 120
```

脚本立即打印实际 PID，另开终端连接同一个 JVM：

```powershell
$labPid = 12345
jcmd $labPid VM.version
jcmd $labPid VM.flags
jcmd $labPid JFR.start name=fault settings=profile filename=live.jfr
jcmd $labPid GC.heap_info
```

相对文件路径由目标 JVM 的工作目录解释。工作结束后的 hold 只保留 JVM 和剩余对象图，不继续重放故障压力。未捕获错误或 OS 杀进程不会进入 hold。

## 4. 统一 Pod 基线和明确例外

大多数清单为 request 250m/128Mi，limit 1 CPU/512Mi；使用非 root UID、只读根文件系统、可写 `/tmp`/`/data`，`restartPolicy: Never`、`backoffLimit: 0`。JVM 堆按实验为 32/64/128 MiB，它与 Pod 总预算不是一回事。

CPU 限流对照故意保持两组 request 都为 100m，只把 limit 从 100m 改为 1 CPU，应用负载不变。可选证据 sidecar 另有小额资源预算，主容器上限不变，见 [容器实验](05-container-budget.md)。

以单个实验为例：

```powershell
minikube -p jvm-lab image build -t jvm-lab-g1-evacuation:local labs/g1-evacuation
kubectl --context jvm-lab -n jvm-labs apply -f labs/g1-evacuation/k8s/job-fault.yaml
kubectl --context jvm-lab -n jvm-labs logs -f job/g1-evacuation-fault -c lab
```

远程集群先推送镜像，再修改清单 image。只应用本次要运行的具体清单，避免递归应用所有故障场景造成节点资源竞争。`job.yaml` 是 fault 的同名快捷副本；不是第三种实验。

导出后再删除该 Job；Job 模板通常不能原地改。若需多轮对照，修改资源或参数前先保存原清单、Pod 状态和证据目录。

## 5. 故障取证的成本与状态

- heap dump/直方图、显式 System.gc、debug 日志和 JFR 都可能改变执行。记录触发时机，必要时分开运行采样与低开销对照。
- 示例使用 4 MiB 诊断预留，并提前准备部分报告路径。预期 OOM 后会释放这块预留；某些实验还会清理少量对象/加载器以便输出。它不是任意 OOM 后可靠恢复服务的方法。
- `HeapDumpOnOutOfMemoryError` 生成的快照通常先于 catch 中的诊断清理。分析后续 hold 的堆图时，要区分它与故障瞬间。
- result.json 中 evacuationFailureMentions 是文字提及数，不能当 GC 次数。evacuationFailureCollections 从完成摘要的 GC ID 去重；Full 的 start/end 也不能算两次。
- 计数只覆盖仍保留的日志文件。轮转删除早期内容、进程被杀或日志未落盘时，应标明证据窗口，不作完整总量承诺。

JVM OOM 分类参考 [JDK 25 排障文档](https://docs.oracle.com/en/java/javase/25/troubleshoot/troubleshooting-memory-leaks.html)；工具影响和命令能力查 [jcmd 25](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html)。

## 6. 学习验收

本专题共有 E01～E24 二十四个追问。至少完成一次无泄漏压力、一次保留 OOM、一次 G1 退化、一次 ZGC 等待与一次加载器根路径分析；容器环境可用后再补 OOMKilled 和 CPU 限流的真实记录。

每个答案按“症状 → 候选 → 证据 → 修复 → 同条件回归”口述。没有复现某个事件时，说明参数和环境，并用可区分候选原因的证据继续推进，不给未出现的事件贴上已验证标签。
