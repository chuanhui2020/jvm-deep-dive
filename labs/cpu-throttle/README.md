# cpu-throttle：CPU 配额、GC 与调度延迟

独立 Maven 项目，JDK 25，无父 POM、兄弟模块和运行时第三方依赖。自带 Maven Wrapper；JUnit 仅用于隔离 JVM 测试。

在当前目录运行，PowerShell 使用 `./mvnw.cmd`：

```sh
./mvnw verify
java -Xms128m -Xmx128m -XX:+UseG1GC -XX:ActiveProcessorCount=1 -jar target/lab.jar fault 30 2
java -Xms128m -Xmx128m -XX:+UseG1GC -XX:ActiveProcessorCount=1 -jar target/lab.jar fixed 30 2
docker build -t jvm-lab-cpu-throttle:local .
```

故障程序有界运行，预期 OOM 可能被教学代码捕获以保留诊断窗口；退出码 0 不证明没有异常。实际 GC 事件必须由日志确认。镜像默认写日志到 `/data`，需挂载可写目录；Pod 清单已配置 emptyDir。`LAB_START_DELAY_SECONDS` 默认 10，结束保留 300 秒。

`k8s/job-fault.yaml` 和 `job-fixed.yaml` 分别部署对照；`job.yaml` 是 fault 的同名快捷副本，不要重复部署两份同名 Job。大多数资源上限 1 CPU/512Mi，CPU 限流场景的 fault 刻意改为 100m。

完整参数、预期信号、真实观察、修复原因与面试追问见 [GC 异常实验](../../docs/gc-failures/README.md)。容器故障以实际终止原因和 cgroup 证据为准，本地 JVM 完成不等于验证了 OOMKilled 或 CPU 限流。
