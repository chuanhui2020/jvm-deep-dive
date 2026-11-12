# GC 异常的真实验证快照

日期：2026-11-12。环境：Windows、HotSpot JDK `25.0.5.0.2+1-LTS-2`；在同一宿主机顺序运行。原始 result.json、stdout/stderr、轮转 GC 日志与部分 OOM dump 保存在未提交的 build/gc-failure-validation/20261112-115037/。下表是一次观察，不是固定期望值、测试阈值或收集器性能排名。

## 1. 运行参数和范围

六个 JVM 场景分别执行 fault/fixed，使用 [场景配置](../../scripts/gc-faults.json)中该模式的完整参数，通过以下入口保存证据：

```powershell
./scripts/run-gc-fault.ps1 -Lab g1-evacuation -Mode fault -SkipBuild
./scripts/run-gc-fault.ps1 -Lab g1-evacuation -Mode fixed -SkipBuild
```

将 Lab 换为 gc-churn、gc-retention、g1-humongous、zgc-stall、metaspace-leak 可运行另外五组。额外两项仅作安全的小负载本机验证：container-budget 使用 fixed 8MiB；cpu-throttle 使用 fixed 1秒/1线程。它们没有 cgroup 资源约束，不能当作容器故障结果。

## 2. 保留日志内的信号计数

Full 列为“完成摘要数 / 其中显式 System.gc 数”；Evac 为完成 Young 摘要按 GC ID 去重；Stall 为 Allocation Stall 单次事件行，不包括统计表中的 Allocation Stalls。全部进程完成且退出码为 0；预期 OOM 由实验捕获，退出码为零不表示健康。

| 场景 | 模式 | Full / 显式 | Evac | Stall | 捕获 Java OOM |
| --- | --- | --- | --- | --- | --- |
| gc-churn | fault | 191 / 0 | 640 | 0 | 否 |
| gc-churn | fixed | 0 / 0 | 0 | 0 | 否 |
| gc-retention | fault | 3 / 0 | 5 | 0 | 是 |
| gc-retention | fixed | 0 / 0 | 1 | 0 | 否 |
| g1-evacuation | fault | 4 / 0 | 4 | 0 | 是 |
| g1-evacuation | fixed | 0 / 0 | 0 | 0 | 否 |
| g1-humongous | fault | 3 / 1 | 0 | 0 | 否 |
| g1-humongous | fixed | 1 / 1 | 0 | 0 | 否 |
| zgc-stall | fault | 0 / 0 | 0 | 1831 | 否 |
| zgc-stall | fixed | 0 / 0 | 0 | 0 | 否 |
| metaspace-leak | fault | 5 / 1 | 0 | 0 | 是 |
| metaspace-leak | fixed | 4 / 0 | 0 | 0 | 否 |
| container-budget | fixed | 0 / 0 | 0 | 0 | 否 |
| cpu-throttle | fixed | 0 / 0 | 0 | 0 | 否 |

## 3. 怎样解释这些结果

- gc-churn 的引用集合有界，fault 仍有大量疏散失败与 Full，说明极端分配和小堆可以造成退化，不能从 Full 推导泄漏；fixed 复用缓冲区后这些信号消失。
- gc-retention 的 fault 最终 Java heap space；fixed 限制缓存容量后没有 OOM，但还有一次疏散失败，说明治理无界引用不保证任意小堆与分配负载都平稳。
- g1-evacuation 的 fault 触发 Evacuation Failure、压缩 Full 和 Java OOM；fixed 降低存活集/突发量后没有这些信号。教学参数故意苛刻，不作为生产配置。
- g1-humongous 两组都有一次布置阶段显式 System.gc。fault 额外出现两次 Full，fixed 没有额外 Full；两组请求均成功，没有制造出 OOM。连续区域布局解释是结合负载与日志的推断，没有导出每个对象的区域地址图。
- zgc-stall 的 fault 观察到 1831 个 Allocation Stall 事件，fixed 为零。更早仅限速而仍保留 16MiB 的一次运行还有 499 个事件；最终同时降到 4MiB，不能把改善全部归于限速。
- metaspace-leak fault 捕获 Metaspace OOM；fixed 定义 12000 个类后缓存剩 32 个加载器，累计卸载 9728 个类。fixed 没有循环显式 GC，仍有 4 次 Full；元数据阈值回收是合理行为，修复目标是可卸载和无持续无界保留，不是 Full 必须为零。

## 4. 真实日志摘录

以下只摘取必要信号；每行属于各自的一次 JVM 运行，不能跨文件按 GC ID 拼成同一时间线。

### g1-evacuation

```text
[0.085s][info ][gc             ] GC(0) Pause Young (Normal) (G1 Evacuation Pause) (Evacuation Failure: Allocation) 40M->39M(64M) 6.648ms
[0.088s][info ][gc             ] GC(1) Pause Young (Normal) (G1 Evacuation Pause) (Evacuation Failure: Allocation) 48M->47M(64M) 2.945ms
```

### g1-humongous

```text
[0.086s][info ][gc                ] GC(2) Pause Full (System.gc()) 55M->30M(64M) 2.958ms
[0.094s][info ][gc                ] GC(6) Pause Full (G1 Compaction Pause) 30M->30M(64M) 2.773ms
```

### zgc-stall

```text
[3.405s][info ][gc             ] Allocation Stall (allocator-1) 8.801ms
[3.405s][info ][gc             ] Allocation Stall (allocator-0) 8.740ms
```

### metaspace-leak

```text
message=Metaspace
```

## 5. 构建和未验证边界

根 Maven verify：22 个独立项目，46 项测试通过。测试验证受限 fixed 负载与非法参数，不断言故障事件数量。容器 OOMKilled 与 CPU throttling 需要实际 Kubernetes/cgroup，本轮未实测；Docker 引擎和集群 API 均不可连接。详见 [容器预算](05-container-budget.md)与 [CPU 限流](06-cpu-quota.md)。

轮转计数只代表保留窗口。不同机器、JDK 构建、日志级别和诊断预留会改变时序；这份快照的作用是证明这些 JVM 信号确实出现，并展示如何避免把计数误当因果结论。
