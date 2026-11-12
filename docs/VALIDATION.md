# 实验验证记录

## 环境与范围

验证日期：2026-11-11。Windows 64 位，Oracle HotSpot 25.0.5.0.2+1-LTS-2，使用 javac --release 25，生成 class 主版本 69。实验生产源码无第三方依赖，不使用预览特性；独立 Maven 项目的测试使用 JUnit Jupiter。

## 已实际执行

- scripts/verify-labs.ps1：**通过**，覆盖全部 14 个启动入口，单个 JVM 有 20 秒超时保护。
- 检查初始化顺序、继承静态字段不初始化子类、不同加载器导致类型不兼容。
- 默认与紧凑头运行均成功；本机 Empty 的浅大小从 16 变为 8 字节。验证不把这组数当作所有平台的固定值。
- JMM 实验稳定得到 volatileRmw=100、atomic=200；volatile 发布后的普通字段由主线程检查，避免子线程异常被忽略。
- 一个 carrier 的虚拟线程实验完成 12 个 monitor 内等待的任务；不对实际耗时作硬性断言。
- 堆 OOM、直接内存 OOM、平台线程栈溢出均在受限配置中捕获并输出预期类型；这是实验处理，不是生产恢复建议。
- 默认与关闭逃逸分析均运行；只校验计数能获取，不将标量替换比例或预热次数写成稳定性断言。
- G1、ZGC 相同负载均可运行；G1 大数组对照实际出现 Humongous regions。
- javap -p -c -v 校验 major version 69、ACC_VOLATILE、ACC_SYNCHRONIZED、monitorenter 和 monitorexit。
- CPU、平台线程死锁、有限内存保留三种诊断模式均完成；死锁模式报告两个死锁线程。
- 对自建目标 JVM 实际执行 jcmd VM.version、Thread.print -l、VM.native_memory summary、GC.heap_info；线程栈定位到 DiagnosticsDemo.burn。
- JFR 实际录制 8 秒，jfr summary 中取得 595 个 jdk.ExecutionSample，样本包含 DiagnosticsDemo.burn。事件数量只记录该次观测。
- 从启动脚本传入 JvmArgs/LabArgs 录制 JFR 成功，验证参数转发与临时目录配置。
- 本地 Markdown 链接、控制字符与文件清单已检查；原有 AGENTS.md 保持不变。

## 环境差异与边界

当前构建不支持 ShenandoahGCMode=generational，记录为不可用，不计为已完成的收集器对照。Linux/macOS 原生命令已提供，但本次未在对应系统执行。

本机默认 java.io.tmpdir 无法供 JFR 创建文件，启动器已将临时目录限定为仓库 build/tmp；不修改持久系统设置。PowerShell 使用进程级 RemoteSigned 执行本地产生的脚本，未修改系统或用户策略。

GC 时序、弱引用通知、JIT 优化比例、对象大小与递归深度都不是跨构建精确常数。自动检查针对确定语义或程序可完成性；观测型实验必须结合日志与版本解释。

## 复验

~~~powershell
powershell -NoProfile -ExecutionPolicy RemoteSigned -File ./scripts/verify-labs.ps1
~~~

每个项目的 class、测试报告和 JAR 放在自身已忽略的 target/ 中；本地验证日志、JFR 与 dump 放在 build/ 中。重新运行得到的动态数值可能不同，应保留自己的记录。

## 独立 Maven 项目与 Pod 配置改造验证（2026-11-11）

- `./mvnw.cmd -B -ntp clean verify`：14 个独立项目构建成功，28 项 JUnit 测试通过，分别覆盖直接入口和同 JVM 的 Pod 观察入口。
- `scripts/verify-labs.ps1`：全部 14 个实验入口的独立可执行 JAR 通过原有语义检查，包含 layout 同 JAR Java agent、紧凑对象头、类加载资源、受限 OOM 与诊断模式。
- 将 layout、diagnose 的 POM、Wrapper 和 src 复制到隔离目录后，分别用自身 Wrapper 执行 verify 成功；不依赖仓库父 POM或兄弟项目。
- 实际运行打包后的 PodLauncher：可以连接同一 JVM 执行 jcmd VM.version；观察等待和参数转发正常，未捕获异常保留非零退出码。
- 14 份 Kubernetes Job YAML 均已解析，检查无自动重启/重试、独立镜像名和资源限制；所有生产依赖均来自本项目，没有兄弟模块依赖。
- Docker CLI 存在，但 Docker Desktop Linux engine 未运行；当前 minikube context 的 API 连接失败。因此尚未实际构建容器镜像或在 Pod 中执行。这些配置不能记为已完成的容器实测。

运行方式、前后观察窗口、证据导出时限与完整参数覆盖见 [Pod 观察手册](PODS.md)。

## 基础详解与面试内容补充验证（2026-11-11）

- 新增 [F01～F06 基础详解](foundations/README.md)，与 12 章主课程、实验手册、48 个原有追问和五个案例衔接；新增 30 个基础口述自测题及参考要点。
- 核对 Java SE 25 的 JVMS/JLS、JDK 25 API/工具/GC 文档与相关 JEP；实现临界比较固定到 OpenJDK `jdk-25-ga`。正文区分规范、实现与单次实验，不将旧参数直接作为当前配置。
- 检查 42 个外部来源页面及其正文引用的章节锚点，均可访问；确认没有把 JDK 25 链接重定向到 JDK 26 后当作本版本依据。
- 检查本地文件与章节链接、代码块闭合、控制字符和 30 题计数。README、主课程、版本校准、资料导航与个人学习表均已接入新内容。
- 在完整 JDK 25 上编译并执行正文的 Account、Config 和 compute 示例，验证对象字段、静态赋值结果；用 javap 检查 compute 的 `stack=2, locals=3` 和 Config 的 ConstantValue/静态初始化结构，与正文推演一致。
- 本轮为文档补充，没有修改实验生产代码、Maven 构建或 Pod 配置，因此没有重复全量构建或新增容器部署实测声明。

## G1 / 分代 ZGC 深入专题验证（2026-11-11）

- 新增 [收集器专题](gc/README.md)：G1 与分代 ZGC 的数据结构、字段写入、完整周期、并发正确性、固定标签源码导航、日志实验及 24 个专题追问/三个白板题。接入主课程 06/07、资料导航、面试和个人进度。
- 代码级结论核对 OpenJDK `jdk-25-ga`。确认 JEP 522 的交付版本是 26，不将双卡表改进写为 25；ZGC 的 Driver 类型位置、Major/Young/Old 配合和写侧 SATB/remembered 职责均结合对应源文件说明。
- `./mvnw.cmd -B -ntp verify`：14 个独立项目全部成功，30 项测试通过。新增测试用隔离 JVM 检查 cycles 模式可经原入口执行并保持有界 payload 配置，以及非法时长在分配前失败。
- cycles 在完整 JDK 25 上分别使用 G1、分代 ZGC 顺序运行约 20 秒，128 MiB 堆、32 MiB 基础 payload、16 KiB 块、每批暂停 1 ms，两次均成功。
- 教学用 G1 固定 IHOP=20 的一次观察：23 个 Concurrent Start、23 次 Remark、23 次 Cleanup、22 个 Prepare Mixed、22 个 Mixed，未观察到 Full GC。分代 ZGC 的一次观察：62 个 Minor、25 个 Major 完成汇总；所有已输出 Allocation Stalls 表项均为零。计数仅描述该次运行，不构成测试断言或性能排名。
- 打包后的 `PodLauncher → AllocationPressureDemo → cycles` 路径在本地运行，验证参数转发、实际 PID 的 jcmd 连接和保留窗口；成功 JFR.dump 并通过 jfr summary 读取录制，观测到 GC 与对象分配采样事件。
- 两份 collector-specific Job 清单已解析，检查独立名称、完整 JVM 参数、cycles 参数、资源和观察窗口。33 个外部来源页面的 44 处引用及本地链接/章节锚点通过检查。
- 本轮未构建或运行 Docker 镜像/Pod，容器侧验证仍未完成；Full GC、Allocation Stall、OOM 和所有重定位退化路径也未由这组运行复现。机制解释与运行证据分别标明，原始日志/JFR 保存在忽略的 build/，摘录和命令见 [专题实验](gc/EXPERIMENTS.md)。

## GC 异常与修复对照验证（2026-11-12）

- 新增 8 个独立 Maven 项目，合计 22 个；新增 6 篇故障详解、专题入口、真实快照及 E01～E24 追问，接入主课程 06/07/11/12、实验、面试和个人掌握表。
- `./mvnw.cmd -B -ntp verify`：22 个项目成功，46 项测试通过；`scripts/verify-labs.ps1` 的全部 22 个打包入口与受限 fixed 检查通过。将 metaspace-leak 的 Wrapper、POM 和 src 复制到仓库外的隔离目录后独立 verify 成功。
- 六组 JVM fault/fixed 均实际运行并保存证据，复现有界分配下的 GC 退化、静态保留堆 OOM、G1 Evacuation Failure/Full、大对象额外 Full、ZGC Allocation Stall 与 Metaspace OOM。精确计数及解释见 [真实结果](gc-failures/RESULTS.md)，不作为跨平台断言。
- 特别记录：缓存 fixed 仍有一次疏散失败；元空间 fixed 仍有合理 Full；ZGC 仅限速仍有等待，降低存活集后本次为零；大对象布置阶段显式 Full 与分配压力 Full 分开统计。
- 解析并核对八组 16 份 fault/fixed Job、8 份 fault 快捷副本及可选证据容器清单：参数与场景目录一致、无自动重试、资源和只读配置明确。CPU 两组 request 均 100m，仅 limit 从 100m 改到 1 CPU。
- 一键取证脚本已验证含空格路径、启动延迟/结束保留、超时杀子进程并保留 result.json，以及拒绝覆盖非空目录。普通结果、日志轮转和 OOM 快照路径分组隔离。
- 检查本地链接和章节锚点、代码块、24 题编号；核对 16 个外部来源页面的 20 处引用可访问，JDK 25 链接未重定向到 26。
- container-budget 只在本机验证 fixed 8MiB；cpu-throttle 只验证 fixed 1 秒/1 线程。不提供 cgroup 限制的本机脚本不证明容器故障。Docker 引擎与 Kubernetes API 不可连接，本轮没有容器镜像构建、OOMKilled 或真实 CPU throttling 实测。

## 第二轮 Review 与改进（2026-11-12）

本轮检查新增八组故障实现、脚本的路径和环境、取证计数、对照指标以及清单/文档的一致性，修复以下四项：

| 问题 | 原影响 | 修改与证据 |
| --- | --- | --- |
| 相对 EvidenceDir 用 .NET 进程目录解析 | Set-Location 后可能把证据写到另一目录 | 改用 PowerShell 路径解析；在切换目录后验证含空格路径、参数内引号及结果位置 |
| JDK 检查/构建和通用回归继承 JVM 环境参数 | 可能在实验前因环境提示失败，或混入另一个收集器 | 三个启动/回归入口统一隔离 JAVA_TOOL_OPTIONS、JDK_JAVA_OPTIONS、_JAVA_OPTIONS；正常及错误返回均恢复终端环境 |
| 日志不存在时计数也为零 | 缺失证据容易被误读为未发生故障 | 增加日志可用标记，缺失时计数为 null；补充进程完成、工作负载标记、超时与时间窗口字段 |
| CPU 实验只有 heartbeat 延迟 | 无法区分同时间窗内完成的实际分配工作量 | 增加线程计数汇总、经过时间和 payload MiB/s；仍不当作业务吞吐或业务 P99 |

验证结果：

- 根 `./mvnw.cmd -B -ntp verify`：22 个独立项目成功，46 项测试通过；CPU 测试补充工作量/时间/速率有效性检查，不断言性能阈值。
- 故意预设 JAVA_TOOL_OPTIONS 为 UseSerialGC 后运行 `scripts/verify-labs.ps1`，22 项入口仍通过，调用终端原变量恢复。新取证回归还同时注入冲突收集器和过小堆，验证三个环境变量都不进入 JDK 检查或目标 JVM。
- 新增可直接运行的 [verify-gc-evidence.ps1](../scripts/verify-gc-evidence.ps1)：检查计数样本、日志禁用、路径/引号、保留窗口、环境隔离与恢复、超时后落盘、非法负载退出和非空目录防覆盖。全部通过，未故意触发内存耗尽。
- 提取后的计数逻辑重新读取上一轮 14 次真实运行的完整保留日志，五项信号计数均与原记录一致。
- 检查本地文件/锚点、代码块、24 题编号与 Pod 资源/参数一致性；git diff --check 通过。

剩余验证边界：仍未取得真实 Kubernetes/cgroup 运行证据，也未在 Linux/macOS 实测 PowerShell 启动路径。自动化检查只证明已列出的本机行为，不能据此宣布所有部署环境已验证。
