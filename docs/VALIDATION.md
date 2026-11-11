# 实验验证记录

## 环境与范围

验证日期：2026-11-11。Windows 64 位，Oracle HotSpot 25.0.5.0.2+1-LTS-2，使用 javac --release 25，生成 class 主版本 69。源码无第三方依赖，不使用预览特性。

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

生成的 class、agent、验证日志、JFR 与 dump 统一放在已忽略的 build/ 中。重新运行得到的动态数值可能不同，应保留自己的记录。
