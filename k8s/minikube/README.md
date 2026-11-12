# Minikube 基础入口

从仓库根目录开始，完整步骤见 [Minikube Pod 主手册](../../docs/PODS.md)。此目录只部署学习 namespace 和短 smoke Job，不会批量启动压力实验。

```sh
minikube start -p jvm-lab --driver=docker --cpus=2 --memory=4096
minikube -p jvm-lab image build -t jvm-lab-env:local labs/env
kubectl --context jvm-lab apply -k k8s/minikube
kubectl --context jvm-lab -n jvm-labs wait --for=condition=complete job/jvm-minikube-smoke --timeout=180s
kubectl --context jvm-lab -n jvm-labs logs job/jvm-minikube-smoke -c lab
```

Docker 驱动需要 Linux 容器引擎已运行；首次构建在节点下载 JDK 25、Maven 和测试依赖。宿主机无需 JDK、Maven 或 PowerShell 启动脚本。以上资源是学习建议，记录自己的实际配置；已有同名 profile 时不要为套用资源值而删除它。

smoke 验证环境镜像能在 Pod 中启动并结束，不证明 GC 故障已出现。其 delay/hold 为零；正式实验仍用各项目的 `labs/<实验>/k8s/job*.yaml`，默认有观察窗口。

修改 env 源码并重新构建后，已有完成 Job 不会自动重跑。保存日志后只删除本 smoke 再创建：

```sh
kubectl --context jvm-lab -n jvm-labs delete job jvm-minikube-smoke
kubectl --context jvm-lab apply -k k8s/minikube
```
