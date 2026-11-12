# 图册与缩放查看器

共 22 张图，均为可放大的 SVG。长标签已换行，复杂流程改为纵向大图。

- **在 GitHub 阅读：** 点击正文图片或“打开大图”，查看 SVG 原图；可使用浏览器缩放。
- **本地阅读：** 下载仓库后，在浏览器打开 [viewer.html](viewer.html)，可缩放、拖动、切换图片；无需安装依赖或启动服务。
- 查看器默认适应宽度。长图可滚轮移动，或按住拖动；“原尺寸”恢复 100%，“适应整图”查看全貌。

## 全部图解

| 图解 | 正文 | 大图 |
| --- | --- | --- |
| 类的生命周期与按需解析 | [阅读正文](../../foundations/03-class-loading.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/class-lifecycle.svg) |
| 缓存加载器阻止类卸载 | [阅读正文](../../gc-failures/04-metaspace.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/class-loader-retention.svg) |
| G1：年轻收集、并发标记与 Mixed | [阅读正文](../../gc/G1.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/g1-collection-cycle.svg) |
| G1 连续区域压力的教学布局 | [阅读正文](../../gc-failures/02-g1-pathologies.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/g1-contiguous-regions.svg) |
| G1：从 CSet 外找到入边 | [阅读正文](../../gc/G1.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/g1-external-cset-edges.svg) |
| 抽象堆：回收前 | [阅读正文](../../foundations/05-gc.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/gc-before.svg) |
| 标记整理：集中存活对象 | [阅读正文](../../foundations/05-gc.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/gc-compaction.svg) |
| 复制：搬迁存活对象并修正引用 | [阅读正文](../../foundations/05-gc.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/gc-copying.svg) |
| 标记清除：保留原位置 | [阅读正文](../../foundations/05-gc.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/gc-mark-sweep.svg) |
| Java 执行路径与去优化 | [阅读正文](../../CURRICULUM.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/jvm-execution-overview.svg) |
| 当前线程的逻辑调用栈 | [阅读正文](../../foundations/01-execution.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/logical-call-stack.svg) |
| 引用、对象与类型元数据 | [阅读正文](../../foundations/02-memory.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/memory-reference-lifetimes.svg) |
| 老年代到年轻代的引用 | [阅读正文](../../foundations/05-gc.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/old-to-young-reference.svg) |
| 保留泄漏与孤立环 | [阅读正文](../../foundations/04-reachability.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/reachable-retention-and-cycle.svg) |
| 根可达的保留链与不可达的孤立环 | [阅读正文](../../CURRICULUM.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/root-reachability.svg) |
| 从源码到解释执行与 JIT | [阅读正文](../../foundations/01-execution.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/source-to-execution.svg) |
| 静态缓存导致的保留链 | [阅读正文](../../gc-failures/01-allocation-retention.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/static-cache-retention.svg) |
| ThreadLocal 的强弱引用关系 | [阅读正文](../../foundations/04-reachability.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/thread-local-references.svg) |
| volatile 发布的 happens-before 链 | [阅读正文](../../foundations/06-diagnostics.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/volatile-happens-before.svg) |
| ZGC：用 CAS 避免覆盖并发更新 | [阅读正文](../../gc/GENERATIONAL_ZGC.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/zgc-cas-self-healing.svg) |
| 分代 ZGC：Major 的代际协调 | [阅读正文](../../gc/GENERATIONAL_ZGC.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/zgc-major-cycle.svg) |
| ZGC 编码引用到可用地址 | [阅读正文](../../gc/GENERATIONAL_ZGC.md) | [打开 SVG](https://raw.githubusercontent.com/chuanhui2020/jvm-deep-dive/main/docs/assets/diagrams/zgc-reference-decoding.svg) |
