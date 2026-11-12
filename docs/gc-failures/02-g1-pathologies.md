# G1：疏散失败、Full GC 与大对象连续空间

实验默认按 [Minikube Pod 主流程](../PODS.md)执行；先创建学习环境，再构建单个镜像。日志跟随退出后，先导出证据再删除 fault Job。

本篇把 [G1 实现详解](../gc/G1.md)中的目的空间、CSet、转发和 humongous 规则连到实际故障。两个独立项目分别是 [g1-evacuation](../../labs/g1-evacuation/README.md)和 [g1-humongous](../../labs/g1-humongous/README.md)。

## 1. 高存活率让疏散需要的空间增加

g1-evacuation 的 fault 默认保留约 46 MiB 基础数组，再反复建立最多约 10 MiB 突发数组，使用 64 MiB G1 堆。每块 128 KiB，意图研究普通对象疏散，而非每块都走 humongous 分配。

payload 并不等于 region 消耗：对象头、对齐、区域分配碎片、PLAB、诊断预留和 VM 对象都占空间。高存活对象既需要保留本身，也需要在适用的疏散路径上取得合法目的地。

教学配置还刻意使用 `G1ReservePercent=0`、`MaxTenuringThreshold=1`、关闭 adaptive IHOP 并把固定阈值设为 90。它组合了低余量、快速晋升和晚启动标记，用于放大失败路径，不能当生产配置复制。

```sh
minikube -p jvm-lab image build -t jvm-lab-g1-evacuation:local labs/g1-evacuation
kubectl --context jvm-lab -n jvm-labs apply -f labs/g1-evacuation/k8s/job-fault.yaml
kubectl --context jvm-lab -n jvm-labs logs -f job/g1-evacuation-fault -c lab --pod-running-timeout=120s
# 工作结束并导出 fault 证据后，再清理并运行 fixed
kubectl --context jvm-lab -n jvm-labs delete job g1-evacuation-fault
kubectl --context jvm-lab -n jvm-labs apply -f labs/g1-evacuation/k8s/job-fixed.yaml
kubectl --context jvm-lab -n jvm-labs logs -f job/g1-evacuation-fixed -c lab --pod-running-timeout=120s
```

fixed 在同样教学 JVM 配置下，把基础 payload 降到 16 MiB，突发量降到 2 MiB。它展示“降低存活集和峰值”的修复；生产还应另做恢复合理默认策略的对照，而不是认为危险的教学参数值得保留。

## 2. 怎样证明发生了 allocation 疏散失败

从日志完成摘要中寻找类似形式，而不是只搜 `Evacuation` 这个单词：

```text
GC(n) Pause Young (...) (G1 Evacuation Pause)
      (Evacuation Failure: Allocation) before->after(capacity) duration
```

本机记录实际包含 Allocation 原因，随后出现 `Pause Full (G1 Compaction Pause)`，最终触发 Java heap space。对应数量和真实摘录见 [RESULTS](RESULTS.md)。

注意三种不同证据：

| 证据 | 能说明什么 | 不能直接说明什么 |
| --- | --- | --- |
| Young 完成摘要中标出 Allocation failure | 该次疏散出现对应失败 | 所有堆 OOM 都由同一个原因造成 |
| Restore/Process Evacuation Failed Regions 阶段行 | 某些失败区域的处理工作 | 一行就是一次独立 GC |
| Pause Full 完成摘要 | 确有 Full GC | 必然有业务泄漏、或所有 Full 都是同一 cause |

脚本对完成摘要按 GC ID 去重；不能拿 worker 的 Min/Max/Sum 行数作为失败次数。G1 的 allocation 与 pinned 原因也必须区分；本实验没有 JNI 固定区域负载，不声称验证了 pinning 路径。

## 3. 失败后为什么不能直接清空源 region

一部分对象可能已复制，另一部分未取得目的空间。实现必须使对象和引用保持一致，可能需要自转发、保留失败区域并安排后续处理；否则会释放仍要使用的对象。

Full GC 有单独的全堆处理/整理路径，不是“把 mixed 的候选数量改成全部”这么简单。阅读入口见 [G1YoungCollector](https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/g1/g1YoungCollector.cpp)和 [G1FullCollector](https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/g1/g1FullCollector.cpp)。

## 4. 按时间轴排障，不先猜参数

1. 确认 GC cause、Young/Mixed/Full 的先后，区分并发标记是否开始、是否完成或被中止。
2. 查失败前的存活量、晋升和突发分配；CSet 中活对象多，Object Copy 和目的空间压力可能同时增加。
3. 查 IHOP 预测是否有足够时间，CPU 是否让并发工作变慢；调大并发线程还可能与业务争抢 CPU。
4. 查 reserve 和总堆余量，以及堆外/容器预算，不能在 512Mi 容器中盲目把 Xmx 撑满。
5. 再设计改动：降低峰值/保留、合理扩大预算、恢复默认启发式或在证据支持下调整起点。一次改多个参数只能证明组合效果，不能给每项单独归因。

官方建议从 cause、回收是否及时和空间压力出发，而不是提供一套适用于所有负载的固定参数。[JDK 25 G1 调优](https://docs.oracle.com/en/java/javase/25/gctuning/garbage-first-garbage-collector-tuning.html)

### 诊断清理的边界

本实验 OOM catch 会丢弃突发数组，并清掉基础数组尾部最多约 4 MiB，再释放诊断预留，以便日志输出/hold。故障时自动生成的 dump 与清理后的对象图不同；不要拿 hold 时较低占用说故障未发生。

本机开发复现还暴露了取证风险：OOM 后第一次解析某个字符串常量或动态拼接路径，也可能再次失败。故障判断分支和部分输出提前准备，目的是尽量保留证据，不是让任意 OOM 都可恢复。

**E07：为什么堆尚有空闲也会疏散失败？** 需要考虑目的空间、区域形态、存活率、保留策略和不可移动条件；总 free 不是一个适用于所有路径的分配保证。

**E08：提高 G1ReservePercent 是否等于凭空增加内存？** 不是，它影响空闲缓冲等策略取舍，可能改变可用于业务的预算和触发时机；仍需解决过高的存活/分配压力。

**E09：Full GC 后又很快 Full，先做什么？** 对齐 cause 和回收收益。真正存活集超过预算、保留泄漏、标记过晚、连续大对象等需要不同证据，不能先统一关闭显式 GC 或换收集器。

## 5. 大对象：有很多空闲，但需要连续区域

g1-humongous 强制 1 MiB region。先创建 48 个 payload 为 600 KiB 的数组，再清掉相间引用，保留其余数组，显式请求一次 GC。随后 fault 请求一个 payload 20 MiB 的连续 byte 数组。

600 KiB 数组大于半个 1 MiB region 的阈值，每个按 humongous 路径占用区域。相间释放制造空间形态压力；大请求则需要足够连续 region。这个结构不能简化成只把 Java 堆 used 从容量中相减。

```text
教学布局直觉，具体地址需另外取证：
[活][洞][活][洞] ... [活][洞][尚未使用的尾部]
总空闲可以大于请求，但某个连续区间可能不足
```

在 pinned `jdk-25-ga` 中，humongous 的比较是对象总大小严格大于 half-region 阈值。数组头和对齐也要计入，不能只比 payload。[G1CollectedHeap.hpp](https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/g1/g1CollectedHeap.hpp)

```sh
minikube -p jvm-lab image build -t jvm-lab-g1-humongous:local labs/g1-humongous
kubectl --context jvm-lab -n jvm-labs apply -f labs/g1-humongous/k8s/job-fault.yaml
kubectl --context jvm-lab -n jvm-labs logs -f job/g1-humongous-fault -c lab --pod-running-timeout=120s
# 工作结束并导出 fault 证据后，再清理并运行 fixed
kubectl --context jvm-lab -n jvm-labs delete job g1-humongous-fault
kubectl --context jvm-lab -n jvm-labs apply -f labs/g1-humongous/k8s/job-fixed.yaml
kubectl --context jvm-lab -n jvm-labs logs -f job/g1-humongous-fixed -c lab --pod-running-timeout=120s
```

fixed 保留同样的初始锚数组和总请求 payload，将新请求分成 256 KiB 数组。它改变内存布局/访问接口，生产使用前要确认调用方是否允许分段，不是对所有需要单一连续数组的算法都能透明替换。

## 6. 显式准备 GC 与额外 Full 必须分开

两组都调用 System.gc 来形成可观察的准备阶段。因此 fixed 出现一次 `Pause Full (System.gc())` 是实验设计的一部分，不能说“修复无效，仍然 Full”。

本机观察：fault 共 3 个 Full 完成摘要，其中 1 个是显式准备；fixed 共 1 个且就是准备阶段。两组请求都成功，故障点是大对象连续分配导致的额外全堆处理，并非一定要 OOM 才算复现。

准确地说，锚数组结构、humongous 统计、低于容量的 used 和请求阶段新增的 Full，共同支持连续空间压力的解释。若需要证明具体 region 地址排列，还需 `gc+region` 日志等进一步证据；不能把教学布局示意图冒充真实内存地址图。

### 下一步的对照

- 同样 fault 请求，只改 `G1HeapRegionSize`，检查 600 KiB 是否仍被分类为 humongous、尾部浪费和暂停成本怎样改变。
- 同样 region 和总 payload，改变请求的分段大小，检查新分配路径、额外 Full 与对象数量的取舍。
- 结合分配样本定位现实中的大数组来源，再判断流式处理、分段或生命周期缩短是否适用。

不要承诺大对象“永远不移动”或“只能 Full 才能回收”。G1 对部分 primitive 数组有 eager reclaim 等处理，也有最后手段的相关路径；须结合具体版本。[JDK 25 G1](https://docs.oracle.com/en/java/javase/25/gctuning/garbage-first-g1-garbage-collector1.html)

**E10：堆 used 很低，为什么大数组仍可能导致 Full？** 连续 region、对象总大小、已有保留区域和分配路径都影响；用 region 而非只用 payload 做预算。

**E11：看到 System.gc 的 Full，就能解释本例所有 Full 吗？** 不能。按标记和 cause 分开准备阶段与请求阶段，不能把一次显式调用归因给后续所有事件。

**E12：分段修复后能否宣称算法更快？** 只能说明这组分配形态下减少了额外处理；还要考虑索引访问、复制次数、对象数量和真实负载指标。
