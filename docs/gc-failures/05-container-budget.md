# 堆没有满，容器为什么 OOMKilled？

**导航：** [学习路线](../README.md) · [故障顺序](README.md#学习顺序) · [第 11 章](../CURRICULUM.md#第-11-章oom栈溢出与内存预算)

本实验把 Java 堆、直接内存预算和容器内存上限分开观察。**当前只完成本机小负载验证，未在真实 cgroup 中验证 OOMKilled。** 故障清单必须在独立测试集群运行后才能填写容器结论。

## 1. 三个不同的上限

| 层次 | 本实验配置 | 约束什么 |
| --- | --- | --- |
| Java 堆 | `-Xms64m -Xmx64m` | Java 对象堆；不等于进程 RSS |
| NIO 直接缓冲区 | `-XX:MaxDirectMemorySize=768m` | 本实验通过 ByteBuffer 分配的直接内存；不是所有 native 内存的总上限 |
| 主容器 | memory limit `512Mi` | cgroup 记账的内存；包括堆外、栈、元空间等，具体记账还涉及文件页等 |

fault 每次申请 4 MiB direct buffer，逐页写入并保留引用，目标 640 MiB；fixed 将目标改为 64 MiB。写每个 4096 字节位置用于触碰内存页，避免只看虚拟地址预留。Java 堆中主要是少量 buffer 包装对象，所以堆利用率低也可能先触及容器限制。程序每块暂停 20ms，便于采样。

MaxDirectMemorySize 大于容器 limit 是故意制造预算冲突，并非推荐值。fixed 的意义是控制保留量；64 MiB 也不是任何应用都安全的通用预算。元空间、线程栈、代码缓存、GC 元数据、JFR、NMT 和运行环境仍需余量。[Kubernetes 资源管理](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/)

## 2. 运行和观察

```powershell
minikube -p jvm-lab image build -t jvm-lab-container-budget:local labs/container-budget
kubectl --context jvm-lab -n jvm-labs apply -f labs/container-budget/k8s/job-fault.yaml
$labPod = kubectl --context jvm-lab -n jvm-labs get pod -l job-name=container-budget-fault -o jsonpath='{.items[0].metadata.name}'
kubectl --context jvm-lab -n jvm-labs logs -f $labPod -c lab
kubectl --context jvm-lab -n jvm-labs get pod $labPod -o json
kubectl --context jvm-lab -n jvm-labs describe pod $labPod
```

保存完整 JSON 中 `status.containerStatuses`：按容器名 `lab` 找 `state.terminated.reason`、exitCode、起止时间；本清单不重启，通常查看 state，而非只查看 lastState。预期是 reason=`OOMKilled`，常伴随 exitCode=137。**137 只能说明常见的 SIGKILL 退出编码，不能单独证明 OOMKilled**；人工 kill、超时清理也可能造成相同编码。

在启动延迟或仍存活时采样：

```powershell
kubectl --context jvm-lab -n jvm-labs exec $labPod -c lab -- jcmd 1 GC.heap_info
kubectl --context jvm-lab -n jvm-labs exec $labPod -c lab -- jcmd 1 VM.native_memory summary
kubectl --context jvm-lab -n jvm-labs exec $labPod -c lab -- cat /sys/fs/cgroup/memory.current
kubectl --context jvm-lab -n jvm-labs exec $labPod -c lab -- cat /sys/fs/cgroup/memory.max
kubectl --context jvm-lab -n jvm-labs exec $labPod -c lab -- cat /sys/fs/cgroup/memory.events
```

这里给出典型 cgroup v2 容器路径，实际挂载、命名空间和版本必须检查。`memory.events` 的 oom/oom_kill 增量可补充证据；计数范围与层级有关，不应脱离对应 cgroup 解读。NMT 只跟踪 HotSpot 覆盖的内存，不等于 RSS，也不覆盖一切第三方 native 分配；结合 buffer 的 retainedMiB 输出及容器统计判断。[cgroup v2](https://docs.kernel.org/admin-guide/cgroup-v2.html)、[jcmd 25](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html)

## 3. 区分三种失败

| 证据 | 支持的判断 | 不能推出什么 |
| --- | --- | --- |
| Java heap space + heap dump | Java 堆分配失败 | 不自动证明内存泄漏 |
| Cannot reserve ... direct buffer memory | JVM/NIO 直接内存分配失败 | 不等于容器被内核杀死 |
| terminated.reason=OOMKilled + 对应 cgroup 事件 | 容器内存预算相关杀进程 | 不说明一定是 Java 堆占满 |

程序若捕获 Java OOM 会明确输出“不是 OOMKilled 的证明”。宿主机直接运行 fault 只是在宿主机申请 native 内存，没有 512Mi cgroup 约束，不能替代这个实验。OS 杀进程不会执行 catch/finally、正常 shutdown hook 或保证 JFR dumponexit 成功；HeapDumpOnOutOfMemoryError 也不能保证为 OS kill 生成 dump。

## 4. 主容器被杀后怎样导出日志

普通 Job 的主容器终止后不能再通过它 exec/cp。可选 `job-fault-evidence.yaml` 增加普通证据容器，900 秒内只读共享 `/data`，主容器仍为 512Mi；证据容器另有 64Mi 上限，整个 Pod 总资源因此增加。

```powershell
kubectl --context jvm-lab -n jvm-labs apply -f labs/container-budget/k8s/job-fault-evidence.yaml
$labPod = kubectl --context jvm-lab -n jvm-labs get pod -l job-name=container-budget-fault-evidence -o jsonpath='{.items[0].metadata.name}'
kubectl --context jvm-lab -n jvm-labs get pod $labPod -o json > build/container-budget-status.json
kubectl --context jvm-lab -n jvm-labs logs $labPod -c lab > build/container-budget-console.log
kubectl --context jvm-lab -n jvm-labs exec $labPod -c evidence -- ls -lh /data
kubectl --context jvm-lab -n jvm-labs cp -c evidence "${labPod}:/data" build/container-budget-evidence
```

这是普通第二容器，不是自动随主容器结束的原生 sidecar。主容器已 OOMKilled 时 Pod/Job 仍可能 Running；务必按容器名检查状态。900 秒后证据容器退出，总截止时间为 1200 秒。emptyDir 随 Pod 删除消失；导出前不要删 Job。此方案只能保留已经写入共享卷的内容，不能补回被杀瞬间尚未写盘的数据。

## 5. 修复对照与代价

单独应用 `job-fixed.yaml`，同样 512Mi 上限、同样 JDK 与 JVM 参数，只把保留目标降为 64Mi。记录相同时间窗的 heap、direct retained、memory.current、终止原因。业务对应措施是池容量和并发上限、及时释放引用、背压，以及重新分配总内存预算；增加 Pod limit 只是另一种有成本的选择。

要监控随时间持续增长的 native 保留量及相应业务根引用。一个 direct buffer 暂时不可达不意味着立刻释放 native 内存；不要把高频 System.gc 当作池容量治理。线上也应区分节点压力驱逐、容器 OOM 和 Java OOM。[Kubernetes 内存实验](https://kubernetes.io/docs/tasks/configure-pod-container/assign-memory-resource/)

## 6. 面试追问

**E19：Xmx 只有容器上限的八分之一，为什么仍然 OOMKilled？** 从总内存预算列出候选；用 Java 堆、直接内存、NMT、cgroup 和终止状态逐项收敛，不凭 Xmx 排除进程内存问题。

**E20：退出码 137，没有 heap dump，能确定是堆泄漏吗？** 不能。先查容器 reason、事件与时间线；解释内核 kill 不走 Java OOM 路径，再按存活期监控定位内存来源。

**E21：为什么设置 MaxDirectMemorySize 仍可能超容器上限？** 它只约束特定直接缓冲区分配，其他 native 内存仍消耗预算；本实验还故意将它设置得高于容器总上限。说明修复的资源代价和同限制下的回归方法。

---

[上一步：加载器与元空间](04-metaspace.md) · [学习路线](../README.md) · [返回本章验收](../CURRICULUM.md#第-11-章oom栈溢出与内存预算) · [下一章：12 生产诊断](../CURRICULUM.md#第-12-章生产诊断与综合推理)
