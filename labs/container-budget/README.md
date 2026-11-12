# container-budget：堆外预算与容器 OOMKilled

## 主入口：Minikube Pod

先按 [Pod 主手册](../../docs/PODS.md)创建 profile `jvm-lab` 和 namespace `jvm-labs`。然后从本实验目录执行；构建与 JDK 在 Minikube 节点完成，宿主机不需要 PowerShell 启动器：

```sh
minikube -p jvm-lab image build -t jvm-lab-container-budget:local .
kubectl --context jvm-lab -n jvm-labs apply -f k8s/job-fault.yaml
kubectl --context jvm-lab -n jvm-labs logs -f job/container-budget-fault -c lab --pod-running-timeout=120s
```

先记录实际资源、版本和参数，在工作期录制，在保留窗口导出，最后只删除本次 Job。Ctrl+C 退出日志跟随不会停止实验。

修复对照使用同一个镜像，先导出 fault 证据，再删除本次 fault Job 释放资源并执行：

```sh
kubectl --context jvm-lab -n jvm-labs delete job container-budget-fault
kubectl --context jvm-lab -n jvm-labs apply -f k8s/job-fixed.yaml
kubectl --context jvm-lab -n jvm-labs logs -f job/container-budget-fixed -c lab --pod-running-timeout=120s
```

## 本机开发（可选）

独立 Maven 项目，JDK 25，无父 POM、兄弟模块和运行时第三方依赖。自带 Maven Wrapper；JUnit 仅用于隔离 JVM 测试。

在当前目录运行，PowerShell 使用 `./mvnw.cmd`：

```sh
./mvnw verify
java -Xms64m -Xmx64m -XX:+UseG1GC -XX:MaxDirectMemorySize=768m -jar target/lab.jar fault
java -Xms64m -Xmx64m -XX:+UseG1GC -XX:MaxDirectMemorySize=768m -jar target/lab.jar fixed
docker build -t jvm-lab-container-budget:local .
```

故障程序有界运行，预期 OOM 可能被教学代码捕获以保留诊断窗口；退出码 0 不证明没有异常。实际 GC 事件必须由日志确认。镜像默认写日志到 `/data`，需挂载可写目录；Pod 清单已配置 emptyDir。`LAB_START_DELAY_SECONDS` 默认 10，结束保留 300 秒。

`k8s/job-fault.yaml` 和 `job-fixed.yaml` 分别部署对照；`job.yaml` 是 fault 的同名快捷副本，不要重复部署两份同名 Job。大多数资源上限 1 CPU/512Mi，CPU 限流场景的 fault 刻意改为 100m。

完整参数、预期信号、真实观察、修复原因与面试追问见 [GC 异常实验](../../docs/gc-failures/README.md)。容器故障以实际终止原因和 cgroup 证据为准，本地 JVM 完成不等于验证了 OOMKilled 或 CPU 限流。
