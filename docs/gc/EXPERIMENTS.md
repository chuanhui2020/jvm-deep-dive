# G1 / 分代 ZGC：实验与日志证据

先读 [G1](G1.md)和[分代 ZGC](GENERATIONAL_ZGC.md)，再预测日志。全部命令从仓库根目录执行，要求完整 JDK 25。下文明确区分可复现的步骤、一次本机观察和未保证出现的事件。

## 1. 为什么增加 cycles 模式

原来的 `60000 4` 模式短小，适合验证分配总量可以超过 Xmx；它不保证提供足够的老年代压力或采样窗口。新增入口复用独立的 `labs/gc` 项目和镜像：

```powershell
./mvnw.cmd -f labs/gc/pom.xml verify
New-Item -ItemType Directory -Path build -Force | Out-Null
java -Xms128m -Xmx128m -XX:+UseG1GC -jar labs/gc/target/lab.jar cycles 20 32 16 1
```

本篇示例使用 PowerShell。Linux/macOS 使用 `./mvnw`，并以 `mkdir -p build` 创建证据目录；Java 参数本身保持相同。

| cycles 后的参数 | 含义 | 范围/默认值 |
| --- | --- | --- |
| seconds | 初始化基础对象之后，持续分配循环的时间 | 1～300，默认 20 秒 |
| liveMiB | 基础保留数组的 payload 目标 | 1～512，默认 32 MiB |
| blockKiB | 每个基础/新分配数组的 payload | 1～1024，默认 16 KiB |
| pauseMillis | 每批 128 次循环后的 sleep | 0～1000，默认 1 ms |

liveMiB 不是整个堆的精确存活大小：基础数组按 block 大小向下取整，此外还有 Holder、引用数组、最多 64 个 recent 数组、对象头及 VM 自身开销。配置必须给这些部分和回收工作留余量，参数允许的数值不保证适合当前 Xmx。

源代码：[GcCycleDemo](../../labs/gc/src/main/java/dev/jvm/labs/GcCycleDemo.java)。它执行以下工作：

1. 创建固定数量的 Holder，每个持有一个基础数组，由静态 retainedRoots 保留。
2. 反复给至多 64 个 Holder 的 recent 字段写入新数组，旧值失去这条保留边。
3. 每 256 次循环更换一个基础数组，缓慢改变老存活集的组成，而不是无限扩张集合。
4. 输出累计分配 payload 与进度；保留最终有界对象图，供 Pod 的 hold 窗口检查。

这让长寿命 Holder 有机会成为老对象并持续指向新数组，适合观察跨代跟踪。**Holder 是否已晋升仍由收集器实际行为决定**，Java 代码不能直接指定其所在代。程序不调用 System.gc，不通过固定 GC 次数判断成败。

初始对象创建也可能触发 GC，它发生在循环计时和第一条 cycles 配置输出之前。allocatedMiB 统计本程序计数的数组 payload，不包括全部 JVM 分配；sleep 的实际调度精度也依平台而异。

## 2. G1：观察完整周期

以下为教学配置，刻意降低固定 IHOP 以增加观察标记和 mixed 的机会。它不是生产推荐值，也不用于和默认策略作公平性能排名。

```powershell
java -Xms128m -Xmx128m -XX:+UseG1GC -XX:-G1UseAdaptiveIHOP -XX:InitiatingHeapOccupancyPercent=20 '-Xlog:gc*=debug:file=build/gc-deep-g1.log:uptime,level,tags' -jar labs/gc/target/lab.jar cycles 20 32 16 1
```

先预测：基础数组经多轮年轻收集后会怎样；低 IHOP 为什么可能更早启动标记；更换基础数组后，哪些 old region 才可能获得回收价值。

```powershell
Select-String -Path build/gc-deep-g1.log -Pattern 'Pause Young|Concurrent Mark Cycle|Pause Remark|Pause Cleanup|Pause Full'
Select-String -Path build/gc-deep-g1.log -Pattern 'Merge Heap Roots|Scan Heap Roots|Object Copy|Evacuation Failure|Humongous'
```

阅读顺序：以 GC ID 关联事件，先找 cause/type，再看并发周期与其间穿插的年轻暂停，最后看暂停内部耗时。不要按日志行相邻就假定属于同一 GC，也不要把 start/end 两行算成两次收集。

### 一次本机真实片段

2026-11-11，Windows 64 位，Oracle HotSpot 25.0.5.0.2+1-LTS-2，使用上面的 128 MiB 堆和固定 IHOP=20。下列数值只记录当次观察：

```text
GC(1) Pause Young (Concurrent Start) ... 67M->35M(128M) 2.417ms
GC(2) Concurrent Mark Cycle
GC(2) Pause Remark 39M->39M(128M) 0.862ms
GC(2) Pause Cleanup 39M->39M(128M) 0.045ms
GC(2) Concurrent Mark Cycle 3.286ms
GC(3) Pause Young (Prepare Mixed) ... 76M->35M(128M) 1.632ms
GC(4) Pause Young (Mixed) ... 82M->36M(128M) 1.570ms
```

省略的是统一日志装饰器和部分括号文本，不改变事件顺序。该次汇总出现 23 个 Concurrent Start 年轻暂停、23 次 Remark/23 次 Cleanup、22 个 Prepare Mixed 和 22 个 Mixed；未出现 Full GC。不要把这些计数或耗时写入自动测试。

**解释：** Concurrent Start 自己也是年轻暂停；随后的标记周期产生候选信息；Prepare Mixed 后才出现 mixed。Remark 前后占用相同不代表它没有工作，标记收尾也不等于立即释放全部死对象空间。

**不能推出：** G1 总会按同样次数运行、该机器拥有某个业务 P99、或换到 Pod 后仍有同样暂停。

## 3. 分代 ZGC：读清周期和代

```powershell
java -Xms128m -Xmx128m -XX:+UseZGC '-Xlog:gc*=debug:file=build/gc-deep-zgc.log:uptime,level,tags' -jar labs/gc/target/lab.jar cycles 20 32 16 1
Select-String -Path build/gc-deep-zgc.log -Pattern 'Minor Collection|Major Collection|Pause Mark Start|Pause Mark End|Pause Relocate Start|Allocation Stall'
```

不要添加旧的 ZGenerational 模式选择参数，也不要把 G1 的 IHOP/region 参数带进来。日志中的 `Y:` 与 `O:` 用于区分年轻/老代相关输出；同一个 Major ID 可以包含不同代的工作。

### 一次本机真实片段

与上节相同 JDK、相同堆及 cycles 参数，另起进程顺序运行：

```text
GC(0) Major Collection (Warmup)
GC(0) Y: Pause Mark Start (Major) 0.055ms
GC(0) Y: Pause Mark End 0.021ms
GC(0) Y: Pause Relocate Start 0.016ms
GC(0) O: Pause Mark End 0.014ms
GC(0) O: Pause Relocate Start 0.017ms
GC(0) Major Collection (Warmup) 14M(11%)->26M(20%) 0.005s
```

这不是完整阶段列表，省略了并发和其他日志行。该次约 20 秒负载观察到 62 个 Minor、25 个 Major 的完成汇总行；对应 Allocation Stalls 统计未观察到非零值。

**解释：** Major 中老标记启动与年轻阶段的协调，不能通过“没看到独立 O: Pause Mark Start”否定。周期末占用高于开始也可以是并发分配造成，不能直接宣布 GC 失效。

**未实测声明：** 这组运行没有复现分配等待、OOM 或所有重定位退化路径。文档对它们的讲解来自固定版本实现，不伪造一段失败日志。需要研究时，在自己的预算内改变压力并保存证据；不要求必须制造 OOM 才算学会。

## 4. 一次只改变一个条件

| 对照 | 具体改变 | 要回答的问题 | 不能只看什么 |
| --- | --- | --- | --- |
| 存活集 | liveMiB 由 16 改为 48，其他相同 | 老代占用、复制/标记成本和可用余量怎样变 | 总耗时 |
| 分配节奏 | pauseMillis 由 2 改为 0 | 分配率、并发周期能否赶上、CPU 怎样变 | GC 次数 |
| 块大小 | blockKiB 16 改为 64 | payload 相同但对象数和分配路径怎样变 | 只比 payload MiB |
| 收集器 | 相同堆与应用参数，移除对方专有参数 | 工作阶段、资源成本有何差别 | 用教学 IHOP 配置宣称通用胜负 |
| CPU 预算 | Pod limits 改变，其他固定 | 周期时长和分配余量怎样受影响 | 只比 STW |

cycles 没有业务请求和端到端延迟统计，不是 JMH，也不是生产选型基准。正式比较需代表性负载、相同观测开销、预热、多轮运行，并同时记录吞吐、延迟、CPU 和内存。

## 5. G1 大对象边界对照

```powershell
java -Xms64m -Xmx64m -XX:+UseG1GC -XX:G1HeapRegionSize=1m '-Xlog:gc+heap=info' -jar labs/gc/target/lab.jar 200 1024
```

检查启动时或补充 `-Xlog:gc+init=info` 得到的 region 大小，再解释 Humongous regions。1 MiB payload 加头与对齐后大于一个 1 MiB region，会占用连续多个 region；不要把数组元素总字节数直接当成堆占用。

把 payload 改为半 region 附近时，同样要考虑对象头和对齐。精确临界比较以 [G1 的 25 源码](https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/gc/g1/g1CollectedHeap.hpp)为准，不通过一次“没有出现特定行”否定分配规则。

## 6. JFR、jcmd 与保留对象检查

```powershell
./scripts/run-lab.ps1 gc -JvmArgs @('-Xms128m','-Xmx128m','-XX:+UseG1GC','-XX:NativeMemoryTracking=summary','-XX:StartFlightRecording=filename=build/gc-cycles.jfr,settings=profile,dumponexit=true') -LabArgs @('cycles','30','32','16','1')
# 另一终端，使用 workload 输出的实际 PID
$labPid = 12345
jcmd $labPid VM.flags
jcmd $labPid GC.heap_info
jcmd $labPid VM.native_memory summary
```

运行结束后用 `jfr summary build/gc-cycles.jfr` 看已录事件。需查看分配时先检查 recording settings 是否启用相应事件、采样范围和阈值，不能保证每次分配都有一条事件。

heap dump/直方图可能触发 GC 并改变观测状态，优先另起一组运行用于引用图分析。要看 Holder→retained/recent 的关系，可以在 Pod 的 hold 窗口采集；本程序刻意保留静态根，主循环结束不代表这组对象图已经被释放。

## 7. 两份独立 Pod 观察配置

同一个 GC 实验镜像支持两个不同 Job，分别覆盖完整 JAVA_TOOL_OPTIONS 和 cycles 参数：

```powershell
docker build -t jvm-lab-gc:local labs/gc
minikube image load jvm-lab-gc:local
kubectl apply -f labs/gc/k8s/job-g1-cycles.yaml
kubectl apply -f labs/gc/k8s/job-zgc-cycles.yaml
kubectl logs -f job/jvm-g1-cycles
kubectl logs -f job/jvm-zgc-cycles
```

远程集群需先推送镜像并修改 image，见 [Pod 通用手册](../PODS.md)。两份清单默认实际工作 120 秒、开始等待 10 秒、结束保留 300 秒，JVM 是 PID 1；日志写入 `/data/gc.log` 并按 20M、3 文件配置轮转。

```powershell
kubectl exec job/jvm-g1-cycles -- jcmd 1 VM.flags
kubectl exec job/jvm-zgc-cycles -- jcmd 1 GC.heap_info
kubectl exec job/jvm-zgc-cycles -- jcmd 1 JFR.start name=gc settings=profile
# 采样一段时间后，容器仍运行时主动导出录制
kubectl exec job/jvm-zgc-cycles -- jcmd 1 JFR.dump name=gc filename=/data/gc-snapshot.jfr
$labPod = kubectl get pod -l job-name=jvm-zgc-cycles -o jsonpath='{.items[0].metadata.name}'
kubectl cp "${labPod}:/data" build/pod-zgc-evidence
```

在容器退出前复制目录，包含当前及轮转日志和已导出的 JFR。hold 窗口只保留 JVM/对象图，不继续制造同样的分配负载；过了工作阶段再采样，结果不能当作实际压力阶段。

两份 Job 同时运行用于分别观察，不用于公平性能比较；它们可能竞争同一宿主机 CPU。正式对照应顺序运行并控制资源。清单未设置自动失败重试，避免改变单次实验；修改不可变的 Job 模板前先保存证据，再只删除对应 Job。

本轮完成了本地 JVM 运行与清单解析验证，尚未实际构建镜像或运行这些 Pod；不会把可用配置与容器实测混为一谈。

## 8. 记录和验收

```text
问题与运行前预测：
JDK/VM/平台/镜像 digest：
完整 JVM 参数与 cycles 参数：
CPU、内存与观测开销：
周期与代的证据（GC ID/行号/文件）：
暂停与并发工作分别是什么：
存活量、分配率、晋升或等待的证据：
改变一个条件后发生什么：
此次未观察到或不能证明的行为：
```

完成标准：能从实际记录中讲出一个 G1 周期和一个 ZGC 周期，再回到源码解释入边跟踪与对象搬迁的正确性。不要只凭“命令退出码为 0”勾选掌握。
