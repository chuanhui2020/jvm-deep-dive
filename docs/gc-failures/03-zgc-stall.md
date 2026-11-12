# 分代 ZGC：暂停短，不等于没有分配等待

独立项目：[zgc-stall](../../labs/zgc-stall/README.md)。前置：[分代 ZGC 详解](../gc/GENERATIONAL_ZGC.md)。这个实验研究应用在等待可分配空间时的成本，不把它命名为一次 G1 式 Full GC。

## 1. 负载和两个模式

fault 在 32 MiB 堆内由两个平台线程高速分配 64 KiB 数组，并以有界环保留约 16 MiB payload。统一诊断预留、VM 对象和 ZGC 自身分配管理还会消耗空间；16 MiB 不是进程或堆的全部实际使用量。

fixed 把环的 payload 目标减到 4 MiB，并在每次分配后暂停 2 ms，提供更大的容量余量和较低的入口压力。两组保留相同的 32 MiB 堆及单个并发/并行 GC 工作线程设置。

```powershell
./scripts/run-gc-fault.ps1 -Lab zgc-stall -Mode fault
./scripts/run-gc-fault.ps1 -Lab zgc-stall -Mode fixed
```

参数顺序为 `mode seconds liveMiB workers blockKiB`。liveMiB 按线程和块大小划分，最少每线程一槽；因此改变块/线程数时要核算实际环容量，不能只看原输入目标。

## 2. 为什么会等待

ZGC 把大量工作并发进行，但应用仍需要可满足当前请求的空间。若分配太快、保留太多、回收/重定位速度或资源预算不足，应用可能进入等待。更短的 STW 不能凭空提供内存和并发 CPU。

源码从 [zPageAllocator.cpp](https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/z/zPageAllocator.cpp)的分配、停顿和重试路径入手，再回到 zDriver/zDirector 看代周期和调度。普通日志中出现 Allocation Stall，需要确认线程、时间和统计口径。

## 3. 怎样读信号

| 输出 | 正确读法 |
| --- | --- |
| `Allocation Stall (allocator-n) duration` | 某个应用分配线程的等待事件；多线程事件可能重叠 |
| `Allocation Stalls:` 统计表 | 对照该表各列和时点，不把标题本身算成一次等待 |
| Minor/Major 完成汇总 | 区分代周期，结合分配和存活变化 |
| Pause Mark/Relocate Start 等 | 只是相关短暂停，不包含所有应用等待成本 |
| allocatedPayloadMiB | 此程序统计的数组 payload，不含全部 JVM 分配 |

将不同线程的等待毫秒直接相加，不等于墙钟停顿或业务 P99。发生了很多很短的等待，与少量很长的等待，也需要分别衡量。

## 4. 本机对照包含一个没有完全解决的中间结果

最初仅对相同约 16 MiB 保留量增加 2 ms 分配间隔时，仍观察到 499 个等待事件。这说明在该 32 MiB 预算中，入口降速并未完全解决余量问题。

进一步把 payload 目标降至 4 MiB，最终一组对照中 fault 观察到 1831 个事件，fixed 为 0。见 [验证快照](RESULTS.md)。这些是当次 5 秒窗口内从保留日志统计的事件，不是普遍阈值或稳定性断言。

中间结果与最终结果来自不同运行，不能把事件差值当成每个改动独立贡献的精确百分比。它们支持“同时检查压力和存活集”，不支持“sleep 2 ms 能修复所有 ZGC 延迟”。

## 5. 排障顺序与修复选择

1. 对齐业务报警与等待事件，而不是仅查看暂停最大值。
2. 查看正常分配率、突发形态、保留量及是否持续增长。
3. 检查可用空间与硬堆预算，考虑对象/页管理、诊断和并发工作余量。
4. 检查 CPU 限流、并发 worker 与业务竞争；加 GC 线程不保证更快，特别是在只有 1 核的容器里。
5. 分别尝试入口背压/并发限制、减少分配、缩短保留、合理扩大堆/总预算，并保持其他条件可比。
6. 用应用吞吐、请求延迟、CPU、内存与等待趋势共同验收；本程序没有请求链路，不能以其统计替代服务 P99。

大堆若挤压容器本地预算可能转而触发 OOMKilled，因而“加堆”必须同时检查 [容器预算](05-container-budget.md)。

## 6. Pod 中观察

本项目的 fault/fixed Job 默认都是 1 CPU、512MiB 内存上限，Java 堆仍为 32 MiB。先分别顺序运行，导出日志再做下一组，避免两个压力 Pod 同时争抢宿主机资源。

```powershell
kubectl logs -f job/zgc-stall-fault -c lab
kubectl exec job/zgc-stall-fault -c lab -- jcmd 1 VM.flags
kubectl exec job/zgc-stall-fault -c lab -- jcmd 1 GC.heap_info
```

工作结束后的 hold 不再分配，应在工作阶段录制压力。需要更长窗口时修改 seconds 和 Job 总截止时间；不能把没再分配时的低等待量当作修复。

**E13：ZGC 的 STW 都很短，为什么用户仍感觉卡？** 还需检查分配等待、CPU/调度、屏障负担、排队和下游。STW 只是端到端时间的一部分。

**E14：Allocation Stall 就是 Full GC 吗？** 不是。它描述分配等待；要看 ZGC 的请求、代周期和空间路径，不照搬 G1 的事件分类。

**E15：降低分配速率后还有等待，下一步是什么？** 检查真正的存活量、容量和页/搬迁余量，以及 CPU 供给。给出能区分“回收太慢”和“根本没有可回收空间”的下一项证据。
