# 以 Minikube Pod 为核心的 JVM 实验

主流程是：**读机制 → 预测 → 节点内构建独立镜像 → 部署单个 Job → 工作期观测 → 导出证据 → 改一个条件重放**。宿主机只需 Minikube、kubectl 和可用驱动；镜像中提供完整 JDK 25 与 Maven。PowerShell 脚本是可选的本机开发工具。

所有示例从仓库根目录执行。使用专用 profile/context `jvm-lab`、namespace `jvm-labs`，命令显式选择目标，不依赖终端的当前集群。

## 1. 创建学习环境

以下使用 Docker 驱动，先确认 Docker Desktop 使用 Linux 容器且引擎已启动。若使用其他 Minikube 驱动，按自己的平台配置；不要把 Docker CLI 已安装等同于引擎可用。[Minikube 安装与启动](https://minikube.sigs.k8s.io/docs/start/)

```sh
docker info
minikube start -p jvm-lab --driver=docker --cpus=2 --memory=4096
minikube -p jvm-lab status
kubectl --context jvm-lab get nodes -o wide
```

2 CPU/4096MiB 是单次顺序学习的建议节点预算，不是 Pod 的资源上限，也不保证可同时运行全部压力实验。已有同名 profile 时先查看原配置，使用 `minikube start -p jvm-lab` 恢复；不要为了匹配建议值删除已有集群。记录 Minikube、Kubernetes、驱动和实际节点资源。

### 首次 smoke：确认 Pod 内环境

```sh
minikube -p jvm-lab image build -t jvm-lab-env:local labs/env
kubectl --context jvm-lab apply -k k8s/minikube
kubectl --context jvm-lab -n jvm-labs wait --for=condition=complete job/jvm-minikube-smoke --timeout=180s
kubectl --context jvm-lab -n jvm-labs logs job/jvm-minikube-smoke -c lab
```

`apply -k` 只创建 namespace 和环境 smoke，不会批量启动 22 项实验。smoke 复用 env 镜像，将开始延迟和结束保留均设为零；核对 runtime=25、processors、maxHeapMiB、flags。Job Complete 只证明环境程序正常结束，不能证明 GC 故障复现。

smoke 超时先查看 Pod/Job 状态，可能是镜像构建/拉取、调度或启动失败。镜像构建耗时不包含在 kubectl wait 的计时里。修改源码重建后，先保存日志、删除旧 smoke Job，再 apply -k；完成的 Job 不会因镜像标签变化自动重跑。

## 2. 固定镜像和资源

每个 `labs/<实验>/` 都是独立 Maven 项目，自带 Wrapper、pom.xml、测试、Dockerfile 与 Job。根 POM 不作为 parent，复制单个项目仍可独立构建。镜像多阶段构建在 JDK 25 内执行本项目 `verify`，运行镜像保留 jcmd/jfr/javap；不要求宿主机安装 JDK。

```sh
minikube -p jvm-lab image build -t jvm-lab-gc:local labs/gc
minikube -p jvm-lab image ls
```

`image build` 直接使用 Minikube 节点的容器运行时，单节点流程不再需要宿主机 docker build 后额外 image load。首次构建需要联网下载基础镜像、Maven 和测试依赖。[Minikube image](https://minikube.sigs.k8s.io/docs/commands/image/)

| 层次 | 学习基线 | 必须分清的边界 |
| --- | --- | --- |
| Minikube 节点 | 建议 2 CPU / 4096MiB | 还需给 Kubernetes 与镜像构建留空间 |
| 普通实验容器 request | 250m CPU / 128Mi | 不等于 CPU 上限或实际使用量 |
| 普通实验容器 limit | 1 CPU / 512Mi | 是容器总预算，不是 Java 堆大小 |
| JVM 堆 | 依据清单为 32/64/128MiB | 还需考虑元空间、线程栈、native 和工具开销 |
| CPU 对照例外 | request 同为 100m；limit 为 100m / 1 | 故意只改变配额，工作负载相同 |

清单不写 namespace，通过 `-n jvm-labs` 选择。配置统一非 root、只读根文件系统、可写 `/tmp` 与 `/data`；Job 不重启、不自动重试，默认总截止时间 1200 秒。不要并行跑 fault/fixed 后当作公平对照。

`:local` 是方便本地学习的可变标签。同一组对照构建一次后复用该镜像；保存实际 Pod 的 imageID 和 JDK 构建。若两组之间修改源码/重建，必须记录，不把它们混作只改一个参数的对照。远程集群另行推送镜像并修改清单，不使用节点本地标签。

## 3. 选择一个实验

下表是 22 个项目的入口。基础项目用 job.yaml；故障项目选择 job-fault.yaml 或 job-fixed.yaml。故障项目的 job.yaml 是 fault 快捷副本，不是第三种实验；不要递归 apply 整个 labs/。

| 项目 | 内容 | 主 Job | 清单 |
| --- | --- | --- | --- |
| [env](../labs/env/README.md) | 运行时、参数与资源 | `jvm-env` | [部署](../labs/env/k8s/job.yaml) |
| [bytecode](../labs/bytecode/README.md) | 字节码与 monitor | `jvm-bytecode` | [部署](../labs/bytecode/k8s/job.yaml) |
| [init](../labs/init/README.md) | 类初始化 | `jvm-init` | [部署](../labs/init/k8s/job.yaml) |
| [loader](../labs/loader/README.md) | 类加载器身份 | `jvm-loader` | [部署](../labs/loader/k8s/job.yaml) |
| [layout](../labs/layout/README.md) | 对象浅大小 | `jvm-layout` | [部署](../labs/layout/k8s/job.yaml) |
| [references](../labs/references/README.md) | 根可达性与弱引用 | `jvm-references` | [部署](../labs/references/k8s/job.yaml) |
| [gc](../labs/gc/README.md) | 分配压力及 G1/ZGC 周期 | `jvm-gc` | [部署](../labs/gc/k8s/job.yaml) |
| [jit](../labs/jit/README.md) | 逃逸分析与分配 | `jvm-jit` | [部署](../labs/jit/k8s/job.yaml) |
| [jmm](../labs/jmm/README.md) | 可见性与复合操作 | `jvm-jmm` | [部署](../labs/jmm/k8s/job.yaml) |
| [virtual](../labs/virtual/README.md) | 虚拟线程与 monitor | `jvm-virtual` | [部署](../labs/virtual/k8s/job.yaml) |
| [heap-oom](../labs/heap-oom/README.md) | Java 堆 OOM | `jvm-heap-oom` | [部署](../labs/heap-oom/k8s/job.yaml) |
| [direct](../labs/direct/README.md) | 直接内存 OOM | `jvm-direct` | [部署](../labs/direct/k8s/job.yaml) |
| [stack](../labs/stack/README.md) | 栈溢出 | `jvm-stack` | [部署](../labs/stack/k8s/job.yaml) |
| [diagnose](../labs/diagnose/README.md) | CPU、死锁与内存保留 | `jvm-diagnose` | [部署](../labs/diagnose/k8s/job.yaml) |
| [gc-churn](../labs/gc-churn/README.md) | 高分配率与低回收后占用 | `gc-churn-fault / gc-churn-fixed` | [部署](../labs/gc-churn/k8s/job-fault.yaml) |
| [gc-retention](../labs/gc-retention/README.md) | 静态保留链与堆 OOM | `gc-retention-fault / gc-retention-fixed` | [部署](../labs/gc-retention/k8s/job-fault.yaml) |
| [g1-evacuation](../labs/g1-evacuation/README.md) | 高存活率、疏散失败与 Full GC | `g1-evacuation-fault / g1-evacuation-fixed` | [部署](../labs/g1-evacuation/k8s/job-fault.yaml) |
| [g1-humongous](../labs/g1-humongous/README.md) | 大对象连续区域与分段对照 | `g1-humongous-fault / g1-humongous-fixed` | [部署](../labs/g1-humongous/k8s/job-fault.yaml) |
| [zgc-stall](../labs/zgc-stall/README.md) | 分代 ZGC 分配等待 | `zgc-stall-fault / zgc-stall-fixed` | [部署](../labs/zgc-stall/k8s/job-fault.yaml) |
| [metaspace-leak](../labs/metaspace-leak/README.md) | 加载器保留与 Metaspace OOM | `metaspace-leak-fault / metaspace-leak-fixed` | [部署](../labs/metaspace-leak/k8s/job-fault.yaml) |
| [container-budget](../labs/container-budget/README.md) | 堆外预算与容器 OOMKilled | `container-budget-fault / container-budget-fixed` | [部署](../labs/container-budget/k8s/job-fault.yaml) |
| [cpu-throttle](../labs/cpu-throttle/README.md) | CPU 配额、GC 与调度延迟 | `cpu-throttle-fault / cpu-throttle-fixed` | [部署](../labs/cpu-throttle/k8s/job-fault.yaml) |

GC 另有 `labs/gc/k8s/job-g1-cycles.yaml` 和 `job-zgc-cycles.yaml`，对应 `jvm-g1-cycles` / `jvm-zgc-cycles`，工作 120 秒。细节见 [周期实验](gc/EXPERIMENTS.md)。

以 G1 疏散失败为例：

```sh
minikube -p jvm-lab image build -t jvm-lab-g1-evacuation:local labs/g1-evacuation
kubectl --context jvm-lab -n jvm-labs apply -f labs/g1-evacuation/k8s/job-fault.yaml
kubectl --context jvm-lab -n jvm-labs get pods -l job-name=g1-evacuation-fault
kubectl --context jvm-lab -n jvm-labs logs -f job/g1-evacuation-fault -c lab --pod-running-timeout=120s
```

普通镜像 ENTRYPOINT 直接启动 Java，JVM 是 PID 1。PodLauncher 在同一个 JVM 中先等待 10 秒，再运行实验，正常结束后保留 300 秒；日志跟随不会立刻返回，Ctrl+C 只退出日志跟随。

`LAB_START_DELAY_SECONDS` 和 `LAB_HOLD_SECONDS` 可独立设为 0～3600 秒。需要人工 attach 的短故障，可先把开始延迟改成 30 秒。保留窗口不会重放工作负载，不保证 main 局部变量仍可达；某些 OOM 实验还会为诊断清理对象。自动 dump 与稍后手工查询不是同一个瞬间。

## 4. 在工作期取证

另开终端，在开始延迟内录制；以下仍以同一故障 Job 为例：

```sh
kubectl --context jvm-lab -n jvm-labs exec job/g1-evacuation-fault -c lab -- jcmd 1 VM.version
kubectl --context jvm-lab -n jvm-labs exec job/g1-evacuation-fault -c lab -- jcmd 1 VM.flags
kubectl --context jvm-lab -n jvm-labs exec job/g1-evacuation-fault -c lab -- jcmd 1 JFR.start name=lab settings=profile filename=/data/observe.jfr
kubectl --context jvm-lab -n jvm-labs exec job/g1-evacuation-fault -c lab -- jcmd 1 GC.heap_info
kubectl --context jvm-lab -n jvm-labs exec job/g1-evacuation-fault -c lab -- jcmd 1 VM.native_memory summary
```

确认实际工作结束后，在 JVM 仍存活时导出 JFR 快照：

```sh
kubectl --context jvm-lab -n jvm-labs exec job/g1-evacuation-fault -c lab -- jcmd 1 JFR.dump name=lab filename=/data/snapshot.jfr
kubectl --context jvm-lab -n jvm-labs exec job/g1-evacuation-fault -c lab -- jfr summary /data/snapshot.jfr
```

10 秒窗口可能不足以逐条手工执行，优先增大开始延迟或使用完整 JAVA_TOOL_OPTIONS 配置的启动录制。进入 hold 后才开始 JFR 不能补回故障过程。GC 日志默认写文件的项目见各自 Dockerfile；普通基础项目不一定生成 `/data/gc.log`。

## 5. 导出后再清理

先在本机创建本轮证据目录，例如 `build/g1-fault/`。保存集群和配置快照：

```sh
minikube version
kubectl --context jvm-lab version -o yaml
kubectl --context jvm-lab get nodes -o yaml > build/g1-fault/nodes.yaml
kubectl --context jvm-lab -n jvm-labs get job g1-evacuation-fault -o yaml > build/g1-fault/job.yaml
kubectl --context jvm-lab -n jvm-labs get pods -l job-name=g1-evacuation-fault -o json > build/g1-fault/pods.json
kubectl --context jvm-lab -n jvm-labs logs job/g1-evacuation-fault -c lab > build/g1-fault/console.log
kubectl --context jvm-lab -n jvm-labs get events --sort-by=.metadata.creationTimestamp > build/g1-fault/events.txt
```

再用 `get pods` 查到实际 Pod 名，替换下列 POD_NAME 占位符；保持容器仍在运行：

```sh
kubectl --context jvm-lab -n jvm-labs get pods -l job-name=g1-evacuation-fault
kubectl --context jvm-lab -n jvm-labs cp POD_NAME:/data build/g1-fault/data -c lab
```

kubectl cp 需要容器中有 tar（本项目完整 JDK 镜像的操作系统工具需在实际镜像检查）。不要在旧版 PowerShell 中用二进制 tar 流配合 `>` 代替 cp，避免文本重定向损坏 dump/JFR。[kubectl cp](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_cp/)

`/data` 默认是 emptyDir。主容器终止后无法再通过它 exec/cp，删除 Pod 后数据丢失。捕获预期 Java OOM 的教学实验通常会进入 hold；OS OOMKilled 不会。容器预算故障应选择 [证据容器清单](gc-failures/05-container-budget.md)，通过仍活着的 evidence 容器导出，或自行准备 PVC。文件未写盘时不能保证恢复。

每轮至少记录：节点/Pod 资源、实际 imageID、运行时构建、完整 JVM/应用参数、开始和结束时间、GC/JFR/输出、容器终止状态及更改的唯一条件。heap dump、直方图、debug 日志和显式 GC 都可能改变时序，注明采集影响。

## 6. 修复对照与重新运行

fault 导出后只删除这个 Job，释放其 CPU/内存，再运行 fixed：

```sh
kubectl --context jvm-lab -n jvm-labs delete job g1-evacuation-fault
kubectl --context jvm-lab -n jvm-labs apply -f labs/g1-evacuation/k8s/job-fixed.yaml
kubectl --context jvm-lab -n jvm-labs logs -f job/g1-evacuation-fixed -c lab --pod-running-timeout=120s
```

Job 模板通常不可原地改；更改 args、env 或 resources 后，保存原记录再删除/重建该 Job。JAVA_TOOL_OPTIONS 会覆盖镜像中的整条设置，必须保留该实验前提：layout 的 agent、virtual 的调度器、direct 的上限、收集器及 NMT 等。只选一个收集器。不要直接套用别的实验完整参数。

退出码零或 Job Complete 不等于没有故障；捕获 OOM 可正常结束。reason=OOMKilled 需结合容器状态与 cgroup 证据，137 单独不足以归因。fixed 仍可能有合理 GC；CPU 实验要结合实际分配工作量，不只看 heartbeat。详见 [故障专题](gc-failures/README.md)。

## 7. 常见阻塞与验证边界

| 现象 | 先检查什么 |
| --- | --- |
| Docker API pipe / daemon 连接失败 | Linux 容器引擎是否运行；此时 Minikube Docker 驱动也不能启动 |
| context 不存在 | 是否成功创建名为 jvm-lab 的 profile；不要改命令指向不相关集群 |
| ImagePullBackOff | 是否在同一个 profile 构建、tag 是否一致、节点能否下载基础镜像 |
| Pending | 节点可分配资源、已有压力 Job、事件中的调度原因 |
| Completed 后不能 exec | 观察窗口已过；下轮调大 hold，当前只能取容器日志/状态或共享证据卷 |
| Job 仍 Running，但主容器已经结束 | 是否使用普通 evidence 第二容器；按容器名检查 terminated reason |

当前仓库已验证本机 JDK 与静态清单；当前环境 Docker 引擎不可连接，尚未完成 Minikube smoke、镜像构建或真实 Pod 故障观察。将主流程改为 Pod 不会把历史 Windows 日志变成容器实测。[验证记录](VALIDATION.md)会分别记录这两类证据。

需要修改源码或快速本机对照时，才使用 [Windows 辅助工具](../scripts/README.md)或各项目 Maven Wrapper。它们不替代以上流程。
