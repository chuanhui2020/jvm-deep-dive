# Windows 本机辅助工具

项目的主要实验入口是 [Minikube Pod](../docs/PODS.md)。此目录的 PowerShell 用于源码开发、快速本机复现和脚本回归，不负责创建 Minikube 或 Linux cgroup 限制；正常学习不要求运行它们。

| 文件 | 本机用途 |
| --- | --- |
| run-lab.ps1 | 构建并运行指定实验，显式传递 JVM/应用参数 |
| run-gc-fault.ps1 | 运行受超时限制的故障/修复对照，保存本机日志与 dump |
| verify-labs.ps1 | 22 个打包入口的语义与有界负载检查 |
| verify-gc-evidence.ps1 | 取证脚本的路径、环境、计数及超时回归 |
| jdk.ps1 / gc-evidence.ps1 | 内部辅助函数，无需直接执行 |
| labs.json / gc-faults.json | 本机启动和回归参数；Pod 参数以各项目 Job 和镜像为准 |

使用这些工具才需要宿主机完整 JDK 25。PowerShell 参数不会自动修改 Pod；比较 CPU 配额或 OOMKilled 必须运行相应 Kubernetes 清单。

```powershell
./mvnw.cmd verify
./scripts/verify-labs.ps1
./scripts/verify-gc-evidence.ps1
```

Linux/macOS 本机开发可直接使用各项目 Maven Wrapper 和 java；Minikube 主流程在不同终端使用相同的 minikube/kubectl 命令。
