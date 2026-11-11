# init：类初始化

这是独立 Maven 项目，无仓库父 POM、兄弟项目或运行时第三方依赖。需要完整 JDK 25；首次构建需要下载 Maven 和测试依赖。JUnit 仅用于测试。

从当前目录构建和运行（PowerShell 用 `./mvnw.cmd` 替换 `./mvnw`）：

```sh
./mvnw test
./mvnw verify
java -Xmx64m -jar target/lab.jar
docker build -t jvm-lab-init:local .
```

镜像启动同一 JVM 内的 `PodLauncher`，默认等待 10 秒开始，实验结束后保留 300 秒。可用 `LAB_START_DELAY_SECONDS=0` 和 `LAB_HOLD_SECONDS=0` 关闭窗口。诊断镜像默认 `cpu 300`，可覆盖容器 args；普通 JAR 不增加等待窗口。实验失败时保留非零退出码。

本地集群需先导入镜像，远程集群需修改 `k8s/job.yaml` 的 image 为已推送的仓库地址。

```sh
minikube image load jvm-lab-init:local
kubectl apply -f k8s/job.yaml
kubectl logs -f job/jvm-init
kubectl exec job/jvm-init -- jcmd 1 VM.flags
```

完整证据采集、参数覆盖与清理步骤见仓库的 [Pod 观察手册](../../docs/PODS.md)；实验机制见 [实验手册](../../docs/LABS.md)。复制本目录到仓库之外仍可独立构建、打包和生成镜像，说明文档的相对链接除外。
