# Yes Steve Model — Fabric 1.21.11 移植

把 [YesSteveModel](https://github.com/YesSteveModel/YesSteveModel)（YSM 3.0）移植到 **Fabric 1.21.11** 的源码，
附带可用的 **Windows / Linux 原生库**。客户端与服务端都能装，服务端装了才能让玩家之间互相看到对方的模型。

> 这是社区移植，不是官方发布。上游主线是 Forge / NeoForge，本仓库只针对 Fabric 1.21.11。

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
PORTING-REPORT.md                  移植报告：改了什么、怎么验证、踩了哪些坑
NOTICE.md                          第三方组件与许可证清单
```

## 已知限制

- 只支持 Fabric 1.21.11；上游的 Forge / NeoForge 版本不在本仓库范围内。
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
