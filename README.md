# JVM 深入学习 · JDK 25

基于 **JDK 25 / HotSpot**，以 **Minikube Pod** 为主要实验环境。

12 章原理与面试要点，22 个独立 Maven 实验，重点覆盖 G1、分代 ZGC、GC 故障与 JVM 诊断。每个实验可独立构建镜像、部署和观察。

## 内容导航

- [章节课程](docs/CURRICULUM.md) · [基础详解](docs/foundations/README.md)
- [G1 / 分代 ZGC](docs/gc/README.md) · [GC 异常实验](docs/gc-failures/README.md)
- [Pod 部署与观察](docs/PODS.md) · [实验手册](docs/LABS.md)
- [面试追问](docs/INTERVIEW.md) · [官方资料](docs/REFERENCES.md) · [验证记录](docs/VALIDATION.md)

## 快速开始

先启动 Docker 的 Linux 容器引擎，在仓库根目录执行：

```sh
minikube start -p jvm-lab --driver=docker --cpus=2 --memory=4096
minikube -p jvm-lab image build -t jvm-lab-env:local labs/env
kubectl --context jvm-lab apply -k k8s/minikube
kubectl --context jvm-lab -n jvm-labs wait --for=condition=complete job/jvm-minikube-smoke --timeout=180s
kubectl --context jvm-lab -n jvm-labs logs job/jvm-minikube-smoke -c lab
```

完成环境检查后，按 [Pod 手册](docs/PODS.md)选择实验、采集日志和 JFR。

## 本机开发

需要完整 **JDK 25**。各项目位于 `labs/<实验名>/`；根目录可验证全部项目：

```sh
./mvnw test
./mvnw verify
```

Windows 使用 `./mvnw.cmd`。PowerShell 脚本仅作 [本机辅助工具](scripts/README.md)。
