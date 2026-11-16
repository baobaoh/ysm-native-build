# 许可证

**Yes Steve Model — Minecraft 1.21.11 / Fabric 移植版**（以下简称"本项目"）

本项目以 **Apache License 2.0** 授权发布。许可证全文见同目录下的 [LICENSE](LICENSE)。

```
Copyright 2024-2026 YS Group, TartaricAcid, TomatoPuddin, AryochiL
Copyright 2026 DeepSeek（移植者）, Mika_Le Malin（移植发起者）
```

---

## 派生关系与修改声明

本项目是 [YesSteveModel](https://github.com/YesSteveModel/YesSteveModel) 的**衍生作品**，将上游从 Minecraft 26.1.2 回移至 Minecraft 1.21.11（Fabric）。

依照 Apache License 2.0 第 4 条：

- **4(a)** 随分发附带许可证全文 —— 见 [LICENSE](LICENSE)。
- **4(b)** 本项目对上游源码作了修改，修改范围包括：渲染管线的 native 顶点路径、第一人称手臂渲染、模型下载与缓存、命令解析、配置持久化、Mixin 适配等。与上游的逐项差异记录在 [PORTING-REPORT.md](PORTING-REPORT.md)。
- **4(c)** 保留上游全部版权、专利、商标与归属声明。
- **4(d)** 若本项目包含 NOTICE 文件，则随分发一并附带 —— 见 [NOTICE.md](NOTICE.md)。

上游作者：YS Group、TartaricAcid、TomatoPuddin、AryochiL。

---

## 不属于 Apache License 2.0 的部分

以下内容**不适用**本项目的 Apache-2.0 授权，各自遵循其原始许可证：

| 范围 | 说明 |
|---|---|
| `assets/ysm/builtin/**` 内置模型资产 | 以各模型 `ysm.json` 中的 `metadata.license` 为准，`metadata.authors` 保留原作者归属 |
| 第三方 Java 源码（Concentus、Gagravarr、GeckoLib、McLib、Mocha 等） | 保留原始许可证，逐项清单与随包许可证位置见 [NOTICE.md](NOTICE.md) |
| 随包分发的第三方依赖（含 native 组件） | 许可证文本位于 `licenses/` 目录 |
| Native 组件（`libysm.so` / `ysm.dll`）的第三方依赖 | 清单由 [YesSteveModel-Native](https://github.com/YesSteveModel/YesSteveModel-Native/blob/dev/THIRD_PARTY_LICENSES.md) 维护 |

---

## 使用须知

- 你可以自由使用、修改、再分发本项目，包括商业用途，条件是保留版权与许可证声明、并标明修改。
- 本项目按"现状"提供，不附带任何明示或暗示的担保。
- "Yes Steve Model"、"YSM" 等名称与标识归其各自权利人所有，本许可证不授予商标使用权。
