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
