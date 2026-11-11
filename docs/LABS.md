# 实验手册：先预测，再解释

所有命令从仓库根目录执行，使用完整 **JDK 25**。源码位于 labs/<实验名>/src/main/java/dev/jvm/labs/，各项目输出位于自身 target/，诊断证据放在 build/。实验尽量只改变一个关键条件；字节数、编译阈值、GC 次数和耗时以你的运行记录为准。

启动器有 14 个入口；L07/L08 复用 GC 负载，L12 包含堆与直接内存两个对照。GC/JIT 观测不是规范保证，自动验证不对具体时延或 GC 次数断言。

## 启动与工具路径

~~~powershell
./scripts/run-lab.ps1 env
./scripts/verify-labs.ps1
~~~

脚本优先使用 JAVA_HOME；否则使用 PATH 上 javac 所在的完整 JDK 的工具。原生命令中的 java、javap、jcmd、jfr 也必须指向同一 JDK 25，不能让 IDE 或另一套 PATH 混入旧工具。

本机 PowerShell 默认禁止执行脚本。若你的环境同样如此，可用下方原生命令；组织策略允许运行本地脚本时，也可只对本次 PowerShell 进程使用：

~~~powershell
powershell -NoProfile -ExecutionPolicy RemoteSigned -File ./scripts/run-lab.ps1 env
powershell -NoProfile -ExecutionPolicy RemoteSigned -File ./scripts/verify-labs.ps1
~~~

这不会修改系统或用户的持久执行策略。启动器每次通过独立项目的 Maven POM 打包，避免修改源码后误跑旧 class；并把 java.io.tmpdir 指向 build/tmp，集中保存临时文件。本机默认临时目录不可用时，JFR 也因此可以正常录制。

## L01：确认你究竟运行了什么

**对应第 01 章；源码 EnvironmentDemo.java。**

~~~powershell
./scripts/run-lab.ps1 env -JvmArgs @('-Xmx32m')
./scripts/run-lab.ps1 env -JvmArgs @('-Xmx128m')
~~~

**预测：** 哪些输出会变，哪些不会？记录 runtime、vm、vendor、processors、maxHeapMiB、flags。启动器默认 Xmx64m，自定义参数放在默认参数之后。

**观察：** 最大堆随参数改变，CPU 数不由 Xmx 决定；maxMemory 是堆容量视角，不是 RSS。版本应为 25。

**变体：** 用 IDE 启动同一类，核对实际工具链；不要仅凭项目语言级别认定运行时正确。

**能证明：** 当前进程报告的版本与参数。**不能证明：** 堆已经全部提交或驻留。

## L02：亲手追踪操作数栈与 monitor

**对应第 02 章；源码 BytecodeDemo.java。**

~~~powershell
./scripts/run-lab.ps1 bytecode
javap -classpath labs/bytecode/target/classes -p -c -v dev.jvm.labs.BytecodeDemo
~~~

**预测：** compute(3,4) 输出 14；列出 iload_0→iload_1→iadd→istore_2→iload_2→iconst_2→imul→ireturn 每一步的栈内容。

**观察：** major version 为 69；value 字段有 ACC_VOLATILE；同步方法标记 ACC_SYNCHRONIZED；同步块存在 monitorenter、正常与异常退出用的 monitorexit。使用 -p 才能看到 private 字段。

**变体：** 将同步块内部改成抛异常，重新看异常表；添加接口调用、静态调用后找相应 invoke 指令。

**结论边界：** javap 展示 class 字节码，不展示 JIT 最终机器码。volatile 的内存语义不能仅靠数指令判断。

## L03：静态初始化为什么没按直觉发生

**对应第 03 章；源码 InitializationDemo.java。**

~~~powershell
./scripts/run-lab.ps1 init
./scripts/run-lab.ps1 init -JvmArgs @('-Xlog:class+init=info')
~~~

**先写出完整顺序，再运行：**

~~~text
constant=7
after passive use
Target initialized
runtime=9
Parent initialized
inherited=11
~~~

读取编译期常量、Class.forName(..., false, loader) 不初始化 Target；读取 runtime 会初始化。Child.value 实际使用 Parent 声明的字段，因此没有 Child initialized。

**变体：** 将 CONSTANT 的初始化改为 Integer.parseInt("7")，仍为 final，但已不是编译期常量；再比较输出和使用方字节码。另建一个静态初始化抛异常的类，比较第一次主动使用与后续使用的异常。

**验收：** 解释“final”“常量表达式”“初始化”三者的区别，而不是背“静态字段一定触发”。

## L04：两个一模一样的 class 为什么不是一个类型

**对应第 03 章；源码 ClassIdentityDemo.java。**

~~~powershell
./scripts/run-lab.ps1 loader
~~~

**预测与观察：** sameName=true、sameClass=false、parentString=true，转换触发 ClassCastException。两个加载器只对 Payload 使用自定义定义策略，其余类仍父委派。

**解释：** Payload 的字节完全相同，但 defining loader 不同；String 都由父链找到同一类型。定义者与发起者不能混淆。

**变体：** 第二次也复用 first 加载器，观察同一个 Class；再解释为什么不能随意对子类依赖的所有核心类使用子优先。

**延伸：** 为热部署画出“存活线程→上下文加载器→旧业务 Class”的路径。这个实验验证类身份，不直接验证类卸载。

## L05：对象头与浅大小，别再死记 12 字节

**对应第 04 章；源码 ObjectLayoutDemo.java、SizeAgent.java。**

~~~powershell
./scripts/run-lab.ps1 layout
./scripts/run-lab.ps1 layout -JvmArgs @('-XX:+UseCompactObjectHeaders')
~~~

Java agent 的 Instrumentation.getObjectSize 测量对象的实现相关浅大小，不递归追踪引用；不是内存地址或字段偏移查看器。

**本机一次观测（字节）：**

| 对象 | 默认传统头 | 紧凑头 |
| --- | --- | --- |
| Empty | 16 | 8 |
| OneInt | 16 | 16 |
| Reference（内部引用 1 KiB 数组） | 16 | 16 |
| byte[0] | 16 | 16 |
| byte[1] | 24 | 16 |
| byte[1024] | 1040 | 1040 |

**解释：** 头变小后，对齐可能吃掉部分收益，所以不是每个对象都固定节省 4 或 8 字节。Reference 的输出不包含引用对象的大小。

**变体：** 只在传统头模式下增加 -XX:ObjectAlignmentInBytes=16，再估算和测量；先检查压缩类指针与普通指针的实际状态。

**边界：** 本机 64 位、8 字节对齐、小堆下的结果不能直接推广到所有平台。要看字段偏移时再引入并固定支持该构建的 JOL 版本，主线不依赖它。

## L06：循环引用与弱引用队列

**对应第 05 章；源码 ReachabilityDemo.java。**

~~~powershell
./scripts/run-lab.ps1 references -JvmArgs @('-Xlog:gc')
~~~

**预测：** 两个 Node 互相引用，方法返回后只留下一个弱引用，是否仍能回收？通常得到 cleared=true、enqueued=true。

**解释：** GC 按根可达性，而非局部引用计数判断存活；ReferenceQueue 通知与弱引用清空是相关但不同的动作。System.gc 是请求，程序给出有限等待窗口，不把超时当作 GC 语义错误。

**变体：** 加一个 static Node 字段保留 first，预测 cleared=false；清除此强引用再请求回收。不要把 weak.get() 返回值长时间保存在局部强引用里，否则改变了实验。

**边界：** “某次没有被回收”不是“永远不能被回收”的证据。

## L07：分配量大，为什么不必泄漏

**对应第 06 章；源码 AllocationPressureDemo.java。**

~~~powershell
./scripts/run-lab.ps1 gc -JvmArgs @('-Xms64m','-Xmx64m','-XX:+UseG1GC','-Xlog:gc*,safepoint') -LabArgs @('60000','4')
~~~

参数是“分配次数、每个数组的 KiB”。环形数组仅保留约 4 MiB 的 payload，累积分配约 234 MiB，旧槽不断被覆盖。

**预测：** 总分配可以超过 Xmx 而不 OOM；瞬时占用呈波动。观察日志中的 before→after、暂停原因、年轻代/老年代 region 变化，不能把 before 的峰值直接叫泄漏。

**变体：** 次数增加十倍，保持存活环大小；再单独扩大存活环，比较回收后的基线。一次只变一个条件。

**边界：** 打印的 allocatedMiB 不包含对象头与 JVM 自身分配；程序不足以保证出现 mixed GC 或 Full GC，也不能用于证明 SATB 正确性。

## L08：G1 与分代 ZGC；humongous 对照

**对应第 07 章；继续使用同一个负载。**

~~~powershell
./scripts/run-lab.ps1 gc -JvmArgs @('-Xms64m','-Xmx64m','-XX:+UseG1GC','-Xlog:gc*') -LabArgs @('60000','4')
./scripts/run-lab.ps1 gc -JvmArgs @('-Xms64m','-Xmx64m','-XX:+UseZGC','-Xlog:gc*') -LabArgs @('60000','4')
./scripts/run-lab.ps1 gc -JvmArgs @('-Xms64m','-Xmx64m','-XX:+UseG1GC','-Xlog:gc+heap=info') -LabArgs @('200','1024')
~~~

**观察：** 前两次识别收集器与阶段名称。第三次在本机小堆的 region 条件下出现 Humongous regions，数组虽然只有 1 MiB payload，但对象还包括头。

**提问：** 为什么 ZGC 的并发回收前后占用可能不按直觉单调下降？程序在并发分配，两个数字未必对应无业务变化的同一时刻。

**边界：** 64 MiB 是为了快速制造压力，不是生产建议。这个程序运行很短、没有服务请求和 P99，不能宣布哪个收集器“更快”。需要另外用真实负载、足够堆和预热验证。

**可选：** 根据 JDK25.md 探测分代 Shenandoah；不支持时保留错误，不安装额外发行版也可完成主线。

## L09：new 为什么没有对应的分配量

**对应第 08 章；源码 EscapeAnalysisDemo.java。**

~~~powershell
./scripts/run-lab.ps1 jit
./scripts/run-lab.ps1 jit -JvmArgs @('-XX:-DoEscapeAnalysis')
./scripts/run-lab.ps1 jit -JvmArgs @('-XX:+PrintCompilation')
~~~

ThreadMXBean 统计当前平台线程的分配字节。local 创建对象但仅在方法内部读字段；escaping 把对象发布到静态 volatile 字段，迫使它逃逸。

**预测：** 默认下 local 分配通常远小于 escaping；关闭逃逸分析后差距通常缩小。本机一次默认观测约 0.2 MB 对 48 MB；关闭后两者约 48 MB。这是示例，不是精确断言。

**变体：** 增加预热次数，再比较 localBytes；添加返回对象并由外部保留的路径，判断内联后是否仍会逃逸。解释 JIT 尚未完成时为什么计数可能不为零。

**边界：** 结果支持“某些分配被消除”，不能证明“真实对象被放在机器栈上”。这里也不是性能基准；关闭 EA 会影响多项优化，不能仅凭耗时归因。

## L10：volatile 可见性与复合操作原子性

**对应第 09 章；源码 MemoryModelDemo.java。**

~~~powershell
./scripts/run-lab.ps1 jmm
~~~

第一部分写 payload=42 后写 volatile ready；读线程看到 ready 后验证 payload。第二部分将 ++ 显式展开为读、加、写，用 CyclicBarrier 强制两个线程读到同一旧值再写。100 轮后 volatileRmw=100，而两个线程使用 AtomicInteger 的结果为 200。

**预测：** volatile 读写都可见，为什么仍丢 100 次更新？请画出每轮的交错图。这里的屏障只负责稳定构造时序，不把复合操作变成一个原子动作。

**变体：** 用同一把锁包围完整的读改写，并相应移除锁内部的双线程屏障；得到 200。若把 await 保留在排他的锁内部，会制造新的死锁。

**边界：** 移除 volatile 后，不能用一次未复现来证明安全。需要 JMM 推理或专门的并发压力工具，如 JCStress，不能只加 Thread.sleep 当作同步。

## L11：一个 carrier、十二个独立 monitor

**对应第 10 章；源码 VirtualThreadsDemo.java。**

~~~powershell
./scripts/run-lab.ps1 virtual
~~~

启动器把 scheduler parallelism 与 maxPoolSize 都限制为 1。12 个虚拟线程各持有自己的 monitor，先在锁内等所有任务到达，再 sleep。

**预测：** 若持锁阻塞会固定唯一 carrier，第一个任务将无法等齐其他任务。JDK 25 下应得到 virtualTasks=12 completed=12；本机一次约 215 ms，但不对耗时设通过阈值。

**为什么有解释力：** 不依靠增加 carrier 补偿；所有任务必须进入各自锁中的等待点，才能共同越过 CountDownLatch。

**变体：** 去掉 barrier 后改用共享锁，只保留 sleep，观察业务互斥导致串行。不要只改共享锁却保留锁内全员等待，否则第一个任务等其他任务、其他任务等这把锁，是应用死锁。

**边界：** 只验证 monitor 内的这类可卸载等待，不证明所有 JNI/本地阻塞都不会 pin，也不代表 CPU 计算会得到十二倍加速。

## L12：堆 OOM 与直接内存 OOM

**对应第 11 章；源码 HeapOomDemo.java、DirectMemoryDemo.java。**

~~~powershell
./scripts/run-lab.ps1 heap-oom
./scripts/run-lab.ps1 direct
./scripts/run-lab.ps1 direct -JvmArgs @('-Xmx128m')
~~~

heap-oom 用 32 MiB 堆强引用保留 1 MiB 数组，触发 Java heap space。direct 的堆仍有空间，但 MaxDirectMemorySize=16m，保留 16 个 1 MiB buffer 后触发 Cannot reserve ... direct buffer memory；扩大 Xmx 不会覆盖这个显式上限。

**提问：** buffer 包装对象在哪里？原生数据在哪里？有空闲堆为什么还会失败？为什么 heap dump 中壳很小，却可能保留大块本地内存？

**变体：** 仅改 MaxDirectMemorySize=8m，预测 retained buffer 数量变化。可加 HeapDumpOnOutOfMemoryError 生成堆证据：

~~~powershell
./scripts/run-lab.ps1 heap-oom -JvmArgs @('-XX:+HeapDumpOnOutOfMemoryError','-XX:HeapDumpPath=build/heap-oom.hprof')
~~~

**边界：** 示例捕获 Error 仅为打印信息；堆 OOM 可能由 G1 的大对象分配限制提前触发，保留块数不等于 Xmx/1MiB。dump 文件不提交，重复运行需换文件名或自行管理旧文件。

## L13：Xss 改变了什么

**对应第 11 章；源码 StackDepthDemo.java。**

~~~powershell
./scripts/run-lab.ps1 stack -JvmArgs @('-Xss256k')
./scripts/run-lab.ps1 stack -JvmArgs @('-Xss512k')
~~~

**预测：** 增大平台线程栈一般能容纳更深递归，但深度不会固定翻倍。解释器/JIT 帧、编译时机、保护页和平台均影响结果。

**观察：** 捕获 StackOverflowError 并打印 depth。加 Xmx 不能从机制上解决无限递归。

**变体：** 在递归方法中增加局部计算、改用迭代；观察不能只凭源码局部变量数算真实机器帧大小。

**边界：** 本实验使用 main 平台线程。不能直接将结果推广为虚拟线程的栈预算模型；错误后的恢复也仅用于教学。

## L14：不用看源码，先用证据定位

**对应第 12 章；源码 DiagnosticsDemo.java。** 模式 cpu、deadlock、retention，第二参数为 1～300 秒。retention 最多保留 24 MiB，便于本地观察；不是真正无限增长泄漏。

终端 A 启动一种模式，复制程序打印的 PID；终端 B 只操作该 PID。PowerShell 的 PID 是保留变量，请使用 labPid。

~~~powershell
# 终端 A，每次只启动一种
./scripts/run-lab.ps1 diagnose -LabArgs @('cpu','30')
./scripts/run-lab.ps1 diagnose -LabArgs @('deadlock','30')
./scripts/run-lab.ps1 diagnose -JvmArgs @('-XX:NativeMemoryTracking=summary') -LabArgs @('retention','30')

# 终端 B，将 12345 换成目标进程打印的 PID
$labPid = 12345
jcmd $labPid help
jcmd $labPid VM.version
jcmd $labPid VM.flags
jcmd $labPid Thread.print -l
jcmd $labPid GC.heap_info
jcmd $labPid VM.native_memory summary
~~~

**死锁观察：** 两个平台线程互相等待 monitor，Thread.print 应提供等待关系；程序结束前也会报告 deadlocked=2。仅仅看到 WAITING 不足以认定死锁。

**CPU 观察：** 多次线程采样应反复指向 burn；线程栈出现某方法不自动说明该方法占满 CPU，需连接 CPU 样本或线程消耗。

**保留观察：** 静态集合形成根路径；用堆证据看 byte[] 与保留者。需要更长窗口时将 30 改为 120。

~~~powershell
# 目标必须仍在运行；NMT 需要启动时启用
jcmd $labPid VM.native_memory baseline
jcmd $labPid VM.native_memory summary.diff
jcmd $labPid GC.heap_dump build/retention.hprof
~~~

heap dump、类直方图等命令可能有明显开销或触发 GC；在生产上先查 help 与影响，实验中可直接观察。可使用 Eclipse MAT 查看 dominator tree 和 paths to GC roots；这是可选 GUI 工具，不是编译依赖。NMT 不能替代对象引用图或 RSS 分析。

**JFR 分配与 CPU 采样：**

~~~powershell
./scripts/run-lab.ps1 diagnose -JvmArgs @('-XX:StartFlightRecording=filename=build/cpu.jfr,settings=profile,dumponexit=true') -LabArgs @('cpu','5')
jfr summary build/cpu.jfr
jfr print --events jdk.ExecutionSample build/cpu.jfr
~~~

对 deadlock/retention 重复记录，找 monitor、分配等事件。事件是否被启用、阈值和采样窗口会影响结果；没有事件不能证明从未发生。

**验收：** 用五句证据链完成结论：症状、候选原因、关键证据、排除项、修复后同负载验证。不要把源码中的模式名称当作诊断过程。

## 原生命令（Linux/macOS）

以下命令使用每个项目自带的 Maven Wrapper，需要完整 JDK 25：

~~~bash
./mvnw verify
cd labs/layout
./mvnw verify
java -Xmx64m -javaagent:target/lab.jar -jar target/lab.jar
java -Xmx64m -XX:+UseCompactObjectHeaders -javaagent:target/lab.jar -jar target/lab.jar
cd ../virtual
./mvnw verify
java -Xmx64m -Djdk.virtualThreadScheduler.parallelism=1 -Djdk.virtualThreadScheduler.maxPoolSize=1 -jar target/lab.jar
~~~

**手工运行时保留实验前提：** 堆 OOM 用 -Xmx32m；直接内存用 -Xmx64m -XX:MaxDirectMemorySize=16m；栈深度用 -Xss256k；虚拟线程使用上面的两个调度器参数；layout 必须带 javaagent。同一个 layout JAR 同时含 Main-Class 与 Premain-Class，无需单独构建 agent。

每个实验的项目目录都有独立 README、Dockerfile 和 k8s/job.yaml；容器观察步骤见 [Pod 观察手册](PODS.md)。

## 每次实验的记录模板

~~~text
问题与预测：
JDK 构建、VM、操作系统：
完整命令（含所有参数）：
原始输出或证据路径：
机制解释（因果链）：
只改变了什么变量：
反例与结论边界：
对应的面试题，两分钟回答：
~~~
