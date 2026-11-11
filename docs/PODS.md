# 在 Pod 中独立观察 JVM 实验

每个 `labs/<实验名>/` 是完整且独立的 Maven 项目。根 POM 仅聚合构建；没有父 POM、兄弟模块或私有依赖。需要 JDK 25；每个项目的 Wrapper 固定使用 Maven 3.9.16。运行时只需本项目的 `target/lab.jar`，JUnit 仅用于测试。

## 项目与运行参数

| 项目 | 实验 | 额外 JVM 前提 | 实验参数 |
| --- | --- | --- | --- |
| env | 容器内版本、CPU、堆 | `-Xmx64m` | 无 |
| bytecode | 字节码与 monitor | `-Xmx64m` | 无 |
| init | 类初始化 | `-Xmx64m` | 无 |
| loader | 类加载器身份 | `-Xmx64m` | 无 |
| layout | 对象浅大小 | `-Xmx64m -javaagent:/app/lab.jar` | 无 |
| references | 循环引用、弱引用 | `-Xmx64m` | 无 |
| gc | 分配压力、G1/ZGC | `-Xmx64m -XX:+UseG1GC -Xlog:gc*,safepoint` | 默认 `60000 4` |
| jit | 逃逸分析 | `-Xmx64m` | 无 |
| jmm | 发布与原子性 | `-Xmx64m` | 无 |
| virtual | 单 carrier 的 monitor | `-Xmx64m -Djdk.virtualThreadScheduler.parallelism=1 -Djdk.virtualThreadScheduler.maxPoolSize=1` | 无 |
| heap-oom | Java 堆 OOM | `-Xms32m -Xmx32m` | 无 |
| direct | 直接内存 OOM | `-Xmx64m -XX:MaxDirectMemorySize=16m` | 无 |
| stack | 栈溢出 | `-Xmx64m -Xss256k` | 无 |
| diagnose | CPU、死锁、保留 | `-Xmx64m` | 镜像默认 `cpu 300`，支持 `deadlock 300`、`retention 300` |

镜像内已设置表中的前提，另加 `-XX:NativeMemoryTracking=summary -Djava.io.tmpdir=/tmp`。本地直接运行 JAR 时需自行提供这些 JVM 参数，layout 的 agent 路径改为 `target/lab.jar`。

## 单独构建和运行

从项目目录执行；Windows 用 `./mvnw.cmd` 替换 `./mvnw`：

```sh
cd labs/gc
./mvnw test
./mvnw clean verify
java -Xms64m -Xmx64m -XX:+UseG1GC '-Xlog:gc*' -jar target/lab.jar 60000 4
docker build -t jvm-lab-gc:local .
```

Docker 构建上下文是这个实验目录。多阶段构建会在 JDK 25 中独立执行 Maven verify，运行阶段保留完整 JDK，使用非 root 用户。`eclipse-temurin:25-jdk` 是可更新的标签；做严格对照时应记录实际 JDK 构建及镜像 digest，并固定两组使用相同 digest。

## 创建实验 Pod

短实验使用 Job，不配置服务端口和健康探针。Job 设置 `restartPolicy: Never` 与 `backoffLimit: 0`，避免失败自动重试改变观察结果；完成后不自动清理，便于保留日志。[Kubernetes Job 文档](https://kubernetes.io/docs/concepts/workloads/controllers/job/)

本地 minikube（需要 Docker 与集群已启动）：

```sh
minikube image load jvm-lab-gc:local
kubectl apply -f k8s/job.yaml
kubectl get pods -l lab=gc
kubectl logs -f job/jvm-gc
```

远程集群先将镜像推送到集群可访问的仓库，再把清单的 image 替换成对应地址。例如在项目目录：

```sh
docker tag jvm-lab-gc:local registry.example.com/jvm-lab-gc:v1
docker push registry.example.com/jvm-lab-gc:v1
kubectl set image --local -f k8s/job.yaml lab=registry.example.com/jvm-lab-gc:v1 -o yaml | kubectl apply -f -
```

`registry.example.com` 是占位符。私有仓库需要在目标 namespace 配置自己的 imagePullSecrets，不要把凭据提交到仓库。默认清单用当前 namespace，每个实验的 Job 名不同，可并行运行。

## 观察窗口与 JVM 生命周期

镜像 ENTRYPOINT 直接启动 Java，JVM 是 PID 1。`PodLauncher` 和实验在同一 JVM 内：

1. 打印 `phase=before pid=1`，默认等待 10 秒，便于提前连接 JFR。
2. 在 main 平台线程上调用原实验，传递容器 args。
3. 成功后打印 `phase=after` 并保留 JVM 300 秒，最后退出。

`LAB_START_DELAY_SECONDS` 与 `LAB_HOLD_SECONDS` 可在 Job 的 env 中独立设为 0～3600 秒；本地镜像运行时也可用 `docker run --rm -e LAB_HOLD_SECONDS=0 ...`。普通 `java -jar target/lab.jar` 直接执行原实验，不等待。

**保留窗口只保留 JVM，不重放已结束的工作负载，也不保证保留 main 局部变量引用。** 观察 GC/JIT/初始化的瞬时行为，应在 10 秒开始窗口内开启录制，或者启动时使用 JAVA_TOOL_OPTIONS。观察持续负载可用 diagnose，默认实际工作 300 秒，之后才进入保留窗口。PodLauncher 的反射调用会增加少量类与栈帧；做精确局部分析时可通过清单的 command 覆盖为 `['java', '-jar', '/app/lab.jar']`，去掉观察包装。注意 stack 实验的绝对递归深度会受到启动路径影响。

未捕获异常立即让容器失败，不进入成功保留阶段。heap-oom/direct/stack 实验本来就会捕获预期 Error 并打印，所以成功完成 Job 是正常现象。Kubernetes 的 OOMKilled 表示容器预算问题，与 Java 实验捕获的 OOM 不同。默认容器上限 512Mi、堆 64Mi（堆 OOM 项为 32Mi），留出堆外、线程、NMT 与 JFR 空间。各 Job 可分别改 CPU/内存预算，必须记录限制对 GC 和 JIT 的影响。清单有 1200 秒总截止时间；延长观察窗口时同步增加 activeDeadlineSeconds。

## jcmd、JFR 与证据导出

以下命令从仓库根目录执行，以 diagnose 为例，先构建、导入并创建对应 Job。采样时先从日志确认仍在实际工作阶段。

```sh
kubectl logs job/jvm-diagnose
kubectl exec job/jvm-diagnose -- jcmd 1 VM.version
kubectl exec job/jvm-diagnose -- jcmd 1 VM.flags
kubectl exec job/jvm-diagnose -- jcmd 1 Thread.print -l
kubectl exec job/jvm-diagnose -- jcmd 1 GC.heap_info
kubectl exec job/jvm-diagnose -- jcmd 1 VM.native_memory summary
kubectl exec job/jvm-diagnose -- jcmd 1 JFR.start name=lab settings=profile duration=30s filename=/data/observe.jfr
```

等待录制结束后，在容器结束前复制出来：

```sh
# PowerShell：Pod 变量不要使用保留的 $PID
$labPod = kubectl get pod -l job-name=jvm-diagnose -o jsonpath='{.items[0].metadata.name}'
New-Item -ItemType Directory -Path build -Force
kubectl exec $labPod -- jfr summary /data/observe.jfr
kubectl cp "${labPod}:/data/observe.jfr" build/observe.jfr
kubectl logs job/jvm-diagnose > build/pod-diagnose.log
```

Linux/macOS 对应的复制命令：

```sh
lab_pod=$(kubectl get pod -l job-name=jvm-diagnose -o jsonpath='{.items[0].metadata.name}')
mkdir -p build
kubectl cp "$lab_pod:/data/observe.jfr" build/observe.jfr
```

清单将 `/tmp` 和 `/data` 挂为可写 emptyDir，根文件系统只读。运行用户一致，便于 attach。emptyDir 不是持久化存储；容器结束后不能再 exec/cp，删除 Pod 后数据也会消失。请在保留窗口结束前导出；需要长期保存时将 `/data` 改为自己的 PVC。

## 覆盖参数并重复实验

在具体项目的 `k8s/job.yaml` 修改 args 和 env 后重新创建 Job：

```yaml
args: ["60000", "4"]
env:
  - name: JAVA_TOOL_OPTIONS
    value: "-Xms64m -Xmx64m -XX:+UseZGC -Xlog:gc* -XX:NativeMemoryTracking=summary -Djava.io.tmpdir=/tmp -XX:StartFlightRecording=filename=/data/gc.jfr,settings=profile,dumponexit=true"
  - name: LAB_START_DELAY_SECONDS
    value: "10"
  - name: LAB_HOLD_SECONDS
    value: "300"
```

JAVA_TOOL_OPTIONS 会覆盖镜像原值，所以需写全该实验的 JVM 前提，尤其 layout 的 javaagent、direct 的直接内存上限和 virtual 的调度器参数。上例选择 ZGC 时不应同时保留 UseG1GC。JFR 使用 dumponexit 时，若要提前导出可先执行 `jcmd 1 JFR.dump filename=/data/snapshot.jfr`。固定文件名在同一 Job 中只使用一次。

Job 模板通常不可原地修改。导出证据后，只删除本次实验的 Job，再应用修改后的清单：

```sh
kubectl delete job jvm-gc
kubectl apply -f labs/gc/k8s/job.yaml
```

不要把 Job 的完成状态当作学习验收；仍应解释实验的参数、机制、观察结果与边界。
