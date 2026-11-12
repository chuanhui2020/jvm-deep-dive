# CPU 限流怎样放大 GC 压力

**导航：** [学习路线](../README.md) · [故障顺序](README.md#学习顺序) · [第 12 章](../CURRICULUM.md#第-12-章生产诊断与综合推理)

本实验用相同负载对比 CPU quota。**真实 cgroup throttling 尚未在本环境验证；本机运行只验证程序能执行，不能代替 Pod 结果。**

## 1. 控制变量

两组均为 128MiB G1 堆、`-XX:ActiveProcessorCount=1`、两个分配线程、32KiB 数组和每线程 64 槽环形保留；工作 30 秒，同时有每 10ms 尝试唤醒一次的 heartbeat。mode 只用于记录，应用负载相同。

| 条件 | fault | fixed |
| --- | --- | --- |
| CPU request | 100m | 100m |
| CPU limit | 100m | 1 CPU |
| 主容器 memory limit | 512Mi | 512Mi |
| JVM/工作线程配置 | 相同 | 相同 |

request 用于调度和竞争权重等，limit 才是本组故意改变的配额限制；100m 不表示一条慢速物理核心。CPU quota 可以在一个周期内很快消耗完，然后在剩余周期被限制；周期和实际控制器行为必须查看环境。ActiveProcessorCount 固定 JVM 对处理器数量的判断，避免同时改变 JVM ergonomics；它不为容器授予 CPU。[Kubernetes 资源管理](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/)

## 2. 因果链与其他解释

应用和 GC 都需要 CPU。配额耗尽可能推迟并发回收、拉长停顿的实际经过时间、减少应用吞吐并放大排队。堆空间充足不能代替 CPU 预算；不过“延迟高”也可能来自锁、I/O、节点争抢、JIT 或采样开销，不能看到 GC 日志就归因 GC。

先看同一时间窗的请求量/吞吐、GC 周期、CPU 使用、quota 和 throttled 增量，再解释并发进度与分配是否失衡。低吞吐反而可能降低分配速率，因此受限组并不保证 GC 次数更多；应观察服务目标和时间相关性，不预设次数方向。

## 3. 重现和取证

```powershell
minikube -p jvm-lab image build -t jvm-lab-cpu-throttle:local labs/cpu-throttle
kubectl --context jvm-lab -n jvm-labs apply -f labs/cpu-throttle/k8s/job-fault.yaml
$labPod = kubectl --context jvm-lab -n jvm-labs get pod -l job-name=cpu-throttle-fault -o jsonpath='{.items[0].metadata.name}'
kubectl --context jvm-lab -n jvm-labs logs -f $labPod -c lab
kubectl --context jvm-lab -n jvm-labs exec $labPod -c lab -- cat /sys/fs/cgroup/cpu.max
kubectl --context jvm-lab -n jvm-labs exec $labPod -c lab -- cat /sys/fs/cgroup/cpu.stat
kubectl --context jvm-lab -n jvm-labs exec $labPod -c lab -- jcmd 1 VM.flags
kubectl --context jvm-lab -n jvm-labs exec $labPod -c lab -- jcmd 1 JFR.start name=cpu settings=profile duration=20s filename=/data/cpu.jfr
```

程序会尝试打印 cgroup v2 cpu.stat 的前后快照。路径未挂载或在 Windows 时会标记不可用；此时不应伪造 throttling 结论。对相同 cgroup、相同窗口计算：

```text
delta_nr_throttled = end.nr_throttled - start.nr_throttled
delta_nr_periods   = end.nr_periods   - start.nr_periods
throttled_period_ratio = delta_nr_throttled / delta_nr_periods
delta_throttled_usec = end.throttled_usec - start.throttled_usec
```

分母为零不算比值。受限周期比例是“有过限流的周期占比”，不是请求超时率，也不是 CPU 利用率。throttled_usec 不能未经解释就转成业务损失时间；层级、并行线程与内核统计口径会影响理解。还需检查祖先 cgroup 和节点 CPU 竞争，不能只看容器 limit。[Linux cgroup v2 CPU 控制器](https://docs.kernel.org/admin-guide/cgroup-v2.html)

## 4. 正确解读 heartbeat 与 JFR

程序同时输出 `allocatedBlocks`、`allocatedPayloadMiB`、`elapsedMillis`、`payloadMiBPerSecond`。计数由各分配线程在结束时汇总，经过时间覆盖线程启动到全部 join 完成；payload 不含对象头、环引用、诊断与 JVM 自身分配。因此可辅助判断限流时完成的分配工作量，不能当作真实业务吞吐。不同 CPU 预算下 GC 次数应结合这个工作量解释。

程序输出的是 heartbeat 样本的额外唤醒延迟分位数，即睡眠实际经过时间减去目标 10ms，最低取零。**它不是业务 P99，也不是 GC pause P99**。它会同时受到 OS 调度、GC、CPU quota 和运行环境影响；受限期间拿不到 CPU 时样本也会稀疏，不应据此推算真实请求分布。

JFR 中执行采样和线程状态有助于发现热点、锁及停顿，但采样空白本身不能证明 throttling。必须把 cgroup 计数、GC/safepoint 时间、吞吐和 heartbeat 对齐；避免同时对一组开高开销录制、另一组不开。[JDK 25 排障指南](https://docs.oracle.com/en/java/javase/25/troubleshoot/index.html)

## 5. 修复与回归

导出 fault 日志后应用 `job-fixed.yaml`，两组尽量顺序运行在同类节点，固定镜像 digest、JDK 构建、堆、线程及请求负载。记录 CPU limit 放宽后是否减少限流，延迟与吞吐是否改善。一次观察有波动时重复有限轮次，不把演示当成严谨性能排名。

业务措施包括放宽 CPU limit、设置合理 request、扩容和削峰、减少分配与无效计算、控制并发。增加 GC 线程不能创造 CPU；可能更快耗尽额度。换低停顿收集器也不能消除配额不足，反而可能对并发 CPU 余量有不同需求。这里仅改变 limit，便于判断这一因素；后续调优再一次改一个变量。

## 6. 面试追问

**E22：GC 日志中的 real 时间长、CPU 时间不高，能确定被限流吗？** 先列 OS 调度、锁/等待及 quota 等候选；用同时间窗 cpu.stat 增量、cpu.max 和节点情况区分，单条日志不足以下结论。

**E23：增加并发 GC 线程后延迟更差，为什么？** 配额固定时更多线程会竞争同一预算，还可能挤压应用线程；检查吞吐、CPU 额度、存活集与并发回收进度，不直接按核数套公式。

**E24：本实验 heartbeat P99 降低，就能说接口 P99 达标吗？** 不能。真实接口需要固定到达模型、请求计时与排队测量；heartbeat 只是调度延迟的辅助信号，必须解释采样范围和遗漏。

---

[上一步：容器内存预算](05-container-budget.md) · [学习路线](../README.md) · [返回本章验收](../CURRICULUM.md#第-12-章生产诊断与综合推理) · [下一步：综合案例](../INTERVIEW.md#综合案例)
