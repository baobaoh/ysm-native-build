# Yes Steve Model — Fabric 1.21.11 移植

> ## ⚠️ 实验性项目 · 已放弃维护
>
> **本项目是一次技术验证，不是可长期使用的模组。原作者已停止维护，不会再有任何更新、修复或支持。**
>
> - 这是**实验性项目**：目的是验证「YSM 3.0 能否跑在 Fabric 1.21.11 上」「加密 `.ysm` 能否在纯 Java 里解开」，验证已完成。
> - 项目**已放弃**：下面的已知问题**不会修**，收到的 issue / PR 也**不会处理**。
> - 仓库保留只为留下记录和给别人参考，**不建议日常使用**。
> - 如果你要一个能用的 YSM，请用官方版，或去看别人维护的移植。
>
> 最后的构建见 [Releases](../../releases) 里的 `v3.0-dev-fabric-mc1.21.11-final`。

把 [YesSteveModel](https://github.com/YesSteveModel/YesSteveModel)（YSM 3.0）移植到 **Fabric 1.21.11** 的源码，
附带可用的 **Windows / Linux 原生库**。客户端与服务端都能装，服务端装了才能让玩家之间互相看到对方的模型。

> 这是社区移植，不是官方发布。本仓库只针对 Fabric 1.21.11。


## 版本与来源

本仓库不是从零写的，也不是从上游 Forge 主线改的，而是从 **YSM 3.0-dev 的 Fabric 移植版** 反向移植回来：

```
YesSteveModel/YesSteveModel（官方，Apache-2.0，Forge / NeoForge 主线）
        └── LINGYAN2021/YesSteveModel-Fabric #port/fabric-26.1.2   ← 基线：YSM 3.0-dev 的 Fabric 版，目标 MC 26.1.2
                └── 本仓库 #main                                    ← 反向移植到 MC 1.21.11
```

| 项 | 基线（`port/fabric-26.1.2`） | 本移植 |
|---|---|---|
| mod 版本 | `3.0-dev-fabric+mc26.1.2` | `3.0-dev-fabric+mc1.21.11` |
| Minecraft | 26.1.2 | **1.21.11** |
| Fabric Loader | 0.19.5 | 0.19.3（要求 ≥ 0.19.3） |
| Fabric API | 0.155.2+26.1.2 | 0.141.6+1.21.11 |
| Loom | 1.17-SNAPSHOT | 1.17.19 |
| 映射 | mojmap | mojmap（loom 分层） |
| 原生库源码 | 上游 `YesSteveModel-Native@dev` | 钉在 `ca32a15f79`（Java 绑定与之一一对应） |

注意区分版本线：官方**已发布**的 `ysm-2.6.5-fabric+mc1.21.1-release.jar` 属于 2.6.5 / MC 1.21.1 那条旧线，
与本仓库的 `3.0-dev` 不是一回事，存档/模型目录布局也不通用。

## 已验证的能力

| 能力 | 状态 |
|---|---|
| 客户端加载并渲染 YSM 模型（Bedrock 几何 + 动画） | 可用 |
| 多人互看：别的玩家显示成他自己选的模型 | 双客户端实测通过 |
| 服务端下发：客户端本地没有的模型自动从服务器拉取 | 实测通过 |
| 模型选择持久化：换模型后掉线重进仍是同一个 | 实测通过 |
| Linux 服务端原生库（`libysm.so`，glibc ≥ 2.35） | 随包提供 |

## 环境要求

- Minecraft **1.21.11**
- Fabric Loader **≥ 0.19.3**
- Fabric API（在 `0.141.6+1.21.11` 上验证）
- **Java 21+**

## 安装

把构建出的 `ysm-3.0-dev-fabric+mc1.21.11.jar` 放进 `mods/`。

- **单机 / 客户端**：放进客户端 `mods/` 即可。
- **多人**：客户端和服务端都要放，且两边版本必须一致。服务端缺少本模组时，客户端会提示
  「服务端 YSM 模组版本与客户端不同」，实际含义是**握手信道没建立**（服务端很可能因为原生库加载失败而降级了，
  看服务端日志有没有 `YSM native library unavailable`）。

## 模型放哪里

服务端或客户端的 `<游戏目录>/ysm/custom/<模型名>/`（内含 `ysm.json` 与几何/动画/贴图）。
服务端扫描到的模型会自动发布给客户端，客户端本地没有时按需下载。

## 命令

| 命令 | 说明 |
|---|---|
| `/ysm model set <目标> <模型路径>[#贴图][@force]` | 给玩家换模型；`@force` 跳过授权检查 |
| `/ysm model reload` | 重载模型目录 |
| `/ysm model errors` | 列出加载失败的模型 |
| `/ysm model disable` | 禁用模型功能 |
| `/ysm auth add/remove/all/clear` | 管理授权模型列表 |
| `/ysm play <目标> <动画>` | 播放模型动画（需要 OP） |
| `/ysm molang execute <目标> <表达式>` | 执行 Molang 表达式（需要 OP） |
| `/ysm ping` | 打印版本并触发握手 |
| `/ysm export` | 导出当前模型 |

## 构建

```sh
./gradlew build          # Linux / macOS
gradlew.bat build        # Windows
```

产物：`build/libs/ysm-3.0-dev-fabric+mc1.21.11.jar`。

首次构建会下载 Gradle、JDK 工具链与依赖，留 5–15 分钟。原生库已随仓库提供，直接构建即可得到可运行的 jar。

**想自己重编原生库**（改 C++ 侧、或要 arm64 / macOS 版本）见 [PORTING-REPORT.md](PORTING-REPORT.md)
第 6 节「Linux 原生库的编译与取证」——里面写了钉死的上游提交、要补的 6 个 JNI 入口和 GCC 的四个坑。

本仓库同时保存了当时编译 `libysm.so` 用的 GitHub Actions 流程
（`.github/workflows/build-native.yml`，钉死提交 `ca32a15f79`），可以照着复现或改成别的目标平台。

## 原生库

| 平台 | 文件 | 说明 |
|---|---|---|
| Windows x64 | `src/main/resources/META-INF/native/ysm.dll` | 上游提供 |
| Linux x86-64 | `src/main/resources/META-INF/native/libysm.so` | 本项目编译，需要 **glibc ≥ 2.35** |
| macOS / Linux arm64 | — | 需自行编译，见 PORTING-REPORT.md |

`libysm.so` 用 `-march=x86-64-v3` 编译，**宿主 CPU 需支持 AVX2 / FMA**；不支持时启动会报 `Illegal instruction`。

## 目录结构

```
src/main/java/com/elfmcys/ysm/     主代码（渲染、模型、网络、命令、capability）
src/main/resources/assets/ysm/     内置模型与语言文件
src/main/resources/META-INF/native/  原生库
src/main/proto/                    protobuf 定义（模型资产传输协议）
gradle/native.gradle               原生库路径解析（run 环境注入）
.github/workflows/build-native.yml 重编 libysm.so 用的 CI（Linux / glibc 2.35）
PORTING-REPORT.md                  移植报告：改了什么、怎么验证、踩了哪些坑
NOTICE.md                          第三方组件与许可证清单
```

## 项目状态：已放弃

> **本项目已停止维护。** 下面是放弃时的真实状态，不做美化。
>
> **能用的部分**：客户端加载并渲染 YSM 模型、多人互看、服务端下发、模型选择持久化、加密 `.ysm` 包的解析
> （纯 Java 实现，见下文），这些都在实机上跑通并留了截图与日志。
>
> **没做完 / 有毛病的部分**：
> - **第一人称手臂位置偏左上**，不像原版那样从右下伸向准星。根因在多平台矩阵换算，定位到一半停了。
> - **模型预览界面（GUI）部分按钮无响应**，部分图标显示为灰块（缺贴图占位）。
> - **v3 导出的模型仍有边界情况**：本项目自己写的「从解密后的 v3 容器还原成文件夹」这段（`V3ModelExporter`）
>   在大肥鱼、特莉波卡两个模型上验证通过，但没做到对任意 v3 包都正确 —— 方块旋转、X 轴镜像、贴图编码这几个
>   点各修过一轮，仍有未覆盖的模型。
> - **多人/服务端路径只测过联机专用服**，单机存档（集成服务器）路径下出现过一次无法复现的卡死。
>
> **为什么放弃**：验证目标（YSM 3.0 跑在 1.21.11 + 纯 Java 解 v3）已经达成，继续打磨的边际收益低于重做一遍。
> 想做能日常用的版本，建议基于 2.6.5 线的成熟移植，而不是这个 3.0-dev 实验体。

## 已知限制

- 只支持 Fabric 1.21.11；上游的 Forge / NeoForge 版本不在本仓库范围内。
- **加密的旧版 `.ysm` 包（YSGP v3）已支持**：扫描到这种文件时会用纯 Java 解密（XChaCha20 + MT19937 + CityHash
  + 去混淆 zstd）→ 反序列化 → 还原成临时文件夹 → 走正常编译流程。实现来自
  [OpenYSM-Updated](https://github.com/IzumiiKonata/OpenYSM-Updated)（MIT），见 `NOTICE.md`。
  **注意**：转换出来的模型 id 会带 `.ysm` 后缀，`/ysm model set @s 大肥鱼V1.1.ysm` 要写全名。
- 服务端下发模型时会把整只模型的音频分块一并传输（首个版本如此，未做按需懒加载）。
- 客户端远端模型缓存没有容量上限与淘汰策略。


## 许可证与署名

本项目遵循上游的 **Apache License 2.0**（见 [LICENSE](LICENSE)），移植方的修改声明与逐条合规说明见
[LICENSE.md](LICENSE.md)，修改内容见 [PORTING-REPORT.md](PORTING-REPORT.md)。

**内置模型资产不属于 Apache-2.0**：`assets/ysm/builtin/**/ysm.json` 的 `metadata.license` 是每个模型各自的
许可证声明，`metadata.authors` 保留作者归属。

第三方组件的来源与许可证见 [NOTICE.md](NOTICE.md)。

### 致谢

- 上游作者：YS Group、TartaricAcid、TomatoPuddin、AryochiL 及 YesSteveModel 全体贡献者
- 移植发起：Mika_Le Malin
- 移植实现：DeepSeek
