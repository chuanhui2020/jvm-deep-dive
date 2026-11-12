# gc：GC 与分配压力

## 主入口：Minikube Pod

先按 [Pod 主手册](../../docs/PODS.md)创建 profile `jvm-lab` 和 namespace `jvm-labs`。然后从本实验目录执行；构建与 JDK 在 Minikube 节点完成，宿主机不需要 PowerShell 启动器：

```sh
minikube -p jvm-lab image build -t jvm-lab-gc:local .
kubectl --context jvm-lab -n jvm-labs apply -f k8s/job.yaml
kubectl --context jvm-lab -n jvm-labs logs -f job/jvm-gc -c lab --pod-running-timeout=120s
```

先记录实际资源、版本和参数，在工作期录制，在保留窗口导出，最后只删除本次 Job。Ctrl+C 退出日志跟随不会停止实验。

## 本机开发（可选）

这是独立 Maven 项目，无仓库父 POM、兄弟项目或运行时第三方依赖。需要完整 JDK 25；首次构建需要下载 Maven 和测试依赖。JUnit 仅用于测试。

从当前目录构建和运行（PowerShell 用 `./mvnw.cmd` 替换 `./mvnw`）：

```sh
./mvnw test
./mvnw verify
java -Xmx64m -jar target/lab.jar
docker build -t jvm-lab-gc:local .
```

## 持续回收周期模式

原来的短负载仍使用 `迭代次数 数组KiB`。新增模式从当前目录运行：

```sh
./mvnw verify
java -Xms128m -Xmx128m -XX:+UseG1GC -jar target/lab.jar cycles 20 32 16 1
java -Xms128m -Xmx128m -XX:+UseZGC -jar target/lab.jar cycles 20 32 16 1
```

cycles 参数依次为持续秒数、基础保留 payload MiB、数组 KiB、每批 sleep 毫秒。程序保持有界 Holder/数组图并不断更换引用，基础目标不包含对象头、额外 hot 数组或 VM 开销。普通 JAR 在工作结束后退出；Pod 入口保留观察窗口时，这个图仍由静态字段保留。

两份独立观察清单分别为 `k8s/job-g1-cycles.yaml` 和 `k8s/job-zgc-cycles.yaml`。完整的周期解释、源码导航、真实日志和对照条件见 [GC 深入专题](../../docs/gc/README.md)与[实验手册](../../docs/gc/EXPERIMENTS.md)。

镜像启动同一 JVM 内的 `PodLauncher`，默认等待 10 秒开始，实验结束后保留 300 秒。可用 `LAB_START_DELAY_SECONDS=0` 和 `LAB_HOLD_SECONDS=0` 关闭窗口。诊断镜像默认 `cpu 300`，可覆盖容器 args；普通 JAR 不增加等待窗口。实验失败时保留非零退出码。

远程集群需先推送镜像，并修改清单中的 image。

完整证据采集、参数覆盖与清理步骤见仓库的 [Pod 观察手册](../../docs/PODS.md)；实验机制见 [实验手册](../../docs/LABS.md)。复制本目录到仓库之外仍可独立构建、打包和生成镜像，说明文档的相对链接除外。
