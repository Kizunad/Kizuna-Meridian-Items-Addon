# 第三方说明

本项目原创代码与文档采用 PolyForm Noncommercial 1.0.0；此选择不改变依赖或第三方内容的原始许可，也不将 MIT 许可扩大到本项目全部内容。

当前通过 Gradle 接入上游 API，不复制上游源码、生成类或模型资源。运行库不内嵌下列运行依赖，宿主负责提供；构建工具的 Wrapper 随源码仓库分发。

| 项目 | 许可 | 当前使用方式 |
|---|---|---|
| [Kizuna's Meridian Mod Framework](https://github.com/Kizunad/Kizuna-Meridian-Mod-Framework) | PolyForm Noncommercial 1.0.0 | `0.1.0-SNAPSHOT` 本地 server composite build；原项目标准许可正文保存于 `LICENSE`；协议通过 import 引用 |
| [Backpack and Storage 3D](https://github.com/Kizunad/backpack-and-storage-3D) | MIT，Copyright (c) 2026 Kizunad | `0.1.0-SNAPSHOT` 本地 server JAR；调用真实物品、容器和库存公开 API；许可保存于 `LICENSE-BS3D` |
| [Kizuna Inventory UI Framework](https://github.com/Kizunad/Kizuna-Inventory-UI-Framework) | MIT | `0.1.0-SNAPSHOT` 本地 server JAR，仅编译依赖；运行时由 BS3D 宿主提供 |
| [Protocol Buffers](https://github.com/protocolbuffers/protobuf) | BSD 3-Clause | Java 运行库及 protoc `4.36.2`；只分发本项目 schema 的生成类，运行库不内嵌 |
| [Fabric Loader](https://github.com/FabricMC/fabric-loader) / [Fabric API](https://github.com/FabricMC/fabric) | Apache-2.0 | `0.16.10` / `0.92.3+1.20.1`，开发及宿主运行依赖，不内嵌 |
| [Fabric Loom](https://github.com/FabricMC/fabric-loom) / [Yarn](https://github.com/FabricMC/yarn) | MIT / CC0-1.0 | Loom `1.6.12`、Yarn `1.20.1+build.10`，构建与映射使用，不分发映射或 Minecraft 类 |
| [Gradle](https://github.com/gradle/gradle) | Apache-2.0 及其附带组件许可 | Wrapper `8.8` 随源码分发；原发行包许可与声明保存于 `LICENSE-GRADLE` / `NOTICE-GRADLE` |
| [Protobuf Gradle Plugin](https://github.com/google/protobuf-gradle-plugin) | Apache-2.0 | `0.9.6`，仅构建插件，不内嵌 |
| Minecraft | Mojang/Microsoft 专有许可及 EULA | `1.20.1`，仅由开发环境和游戏宿主提供，不分发游戏代码 |

仅调用 BS3D API 的本项目原创适配代码仍遵循项目许可；实际包含的第三方代码或衍生内容保留对应原始许可与版权头。本地 SNAPSHOT 尚不代表稳定的二进制兼容承诺；嵌入运行依赖的下游分发者还需保留其完整许可和声明。
