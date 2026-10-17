# Kizuna's Meridian Items Addon

面向 Meridian 经脉玩法的 Item Framework，提供物品 Registry 与桥接能力，连接 Meridian 玩法模组和基于 BS3D 的物品模组。

## 项目边界

- 提供通用物品登记与玩法关联能力，具体物品内容由附属模组提供。
- 不添加、注册或分发物品模型及其他视觉/音效资源。
- 不维护独立的身体、经脉、真元或容器库存状态。
- Meridian 负责权威经脉玩法；BS3D 负责真实物品身份、容器库存与物品转移。本模组通过两者已核实的公开接口连接玩法与物品。
- 不接受客户端物品 NBT 来创建或修改服务端物品，不建立旁路库存事务或网络协议。

已实现第一版服务端库：不可变绑定目录、可信事件触发、BS3D 个人库存消费候选、联合事务编排及幂等回执。具体效果由附属实现，可通过适配器调用 Meridian 权威命令。

当前产物供 Fabric 宿主嵌入，尚不是放入 `mods/` 就会自动工作的独立模组。宿主必须提供真实事件、授权及物品与 Meridian 的联合持久化适配；本仓库不提供分开保存两侧的默认实现，也未完成游戏联调。

## 构建与接入

Java 17、Minecraft 1.20.1、Gradle 8.8。默认使用同级 Meridian 源码工程，以及已经构建的 BS3D / Inventory UI Framework 服务端 JAR。

```bash
./gradlew assemble
```

[接口、示例与事务要求](docs/wiki/Home.md) · [协作约定](AGENTS.md) · [GitHub 仓库](https://github.com/Kizunad/Kizuna-Meridian-Items-Addon)

## 许可

本项目原创代码与文档采用 Meridian Framework 使用的 [PolyForm Noncommercial 1.0.0](LICENSE)。BS3D 原有部分保留 [MIT License](LICENSE-BS3D) 及版权声明。两者按来源适用，不构成可任选的双重许可；公开仓库仍遵守非商业用途限制。见 [NOTICE](NOTICE) 和[第三方说明](THIRD_PARTY_NOTICES.md)。

本项目通过依赖接入 Meridian 和 BS3D，不复制两者源码、生成类或模型资源。
