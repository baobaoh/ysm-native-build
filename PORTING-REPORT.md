# Yes Steve Model — MC 1.21.11 / Fabric 移植验证报告

- 产物：`build/libs/ysm-3.0-dev-fabric+mc1.21.11.jar`（约 29 MB，含 `META-INF/native/ysm.dll` Windows x64）
- 基线：`LINGYAN2021/YesSteveModel-Fabric@port/fabric-26.1.2`（Apache-2.0）
- 目标：MC 1.21.11 + Fabric Loader 0.19.3 + Fabric API 0.141.6 + JDK 21，mojmap（`fabric-loom-remap` 1.17.19）

## 一、已修好的关键问题

### 1. 所有 native 几何都画不出来（根因）
`NativeRenderer.setupVertexConsumer` 在非 Iris 格式分支里先调了一次
`accessor.ysm$reserve(vertexCount)` 并把返回值丢掉，随后改走 FALLBACK：
BufferBuilder 的写指针被推进了一个「空洞」、`vertices` 计数与实际不符，整批四边形数据错乱。

修法：
- 删掉那次裸 `reserve`；
- 恢复 1.21.11 的 VANILLA 分支（**1.21.11 的实体顶点格式常量叫 `DefaultVertexFormat.NEW_ENTITY`，没有 `ENTITY`**）：
  `format == DefaultVertexFormat.NEW_ENTITY → VertexFormatType.VANILLA`。
  native 侧 `VanillaVertex` 36 字节（pos/color/uv/overlay/light/normal）与 NEW_ENTITY 布局一致。

效果：世界模型、第一人称手臂、界面预览全部开始正常绘制（此前完全不可见）。

### 2. 世界画面多出一个悬浮的重复模型
`YsmPreviewRenderer.renderToTexture`（GUI 的 PiP 预览）在**没有打开任何界面**时也在渲染，
几何被刷进世界帧。修法：入口加 `if (Minecraft.getInstance().screen == null) return;`。

### 3. 第一人称手臂
- 原实现照抄原版 `ItemInHandRenderer.renderPlayerArm` 的常量变换链，那套常量依赖原版手臂模型的
  pivot（±5,2 像素），对 YSM 的骨骼几何不成立（z 被推到相机前 3.4 格、被地形挡住）。
- 改为**包围盒驱动**：从 native 输出的顶点算包围盒（`FallbackVertexWriter.updateArmBounds`），
  **只钉住第一帧**（`if (armBoundsValid) return;`）——早先每帧重算，挥动时中心逐帧跳变、手臂被甩出视锥；
  按 `targetSize=0.22` 归一化缩放，平移到右下 `(±0.12, -0.24, -0.42)`，
  末尾 `translate(-centerX,-centerY,-centerZ)` 把几何对到手上；挥动旋转放在平移之后（绕手臂锚点，
  原先放在平移之前等于绕相机原点转，中段会被甩出画面）。
- **朝向**：`arm.json` 的骨骼从肩（y=29.8）垂到手掌（y=18.3），模型空间里沿 **-Y 下垂**；
  相机空间里 +Y 才是上，所以不旋转就是「一根竖着的袖子」。现在加 `XP +90°` 把 -Y 掰到 -Z（正前方），
  再 `ZP ∓35°` 绕视线滚转，让手臂在屏幕上斜着躺（从右下斜指准星），手臂才像原版那样朝前伸。
  注意位置必须跟着一起改：相机空间里 NDC_y = y / (|z|·tan(fov/2))，fov 70 时
  `y=-0.58, z=-0.55` 已经算到 NDC -1.5（屏幕外），转向朝前后会整段消失。
- **换模型后重算**：包围盒只在第一帧锁存，换模型（`/ysm model set`、资源重载）后旧尺寸不再适用，
  手臂会突然变大或跑到画面中间；现在用 `NativeRenderer.resetArmBounds()` 在几何模型对象变化时重锁。
- 可用 `/ysmclient armpose x= y= z= sx= ...` 现场微调。
- **18:00 追加：手臂「有的机器有、有的机器没有」的真因**。`submitCustomGeometry` 拿到的 `poseStack`
  上叠着 `ItemInHandRenderer` 的基矩阵（含随视角变化的旋转与平移），旧代码直接往里写相机坐标：
  `final = R*(target - t) + t`。`t` 换台机器/换个朝向就变，于是同一份 jar 在开发机上手臂可见、
  在玩家机上跑到屏幕外（`finalT` 实测 x、z 反号）。现在改为**反解**：用基矩阵旋转的逆把
  「目标点 − 基平移」变回局部坐标，再扣掉手臂自身支点平移在最终平移里的那份贡献，
  于是 `finalT` 恒等于 `target`，跨机器一致（诊断 `[YSM-ARM] layout` 可直接核对）。
  当前默认 `target = (∓0.51, 0.035, -0.60)`（`-Z` 为前方），可用 `-Dysm.armTarget=x,y,z` 覆盖。


### 4. 界面 / 按键
- 1.21.11 里 `Screen.renderWithTooltipAndSubtitles` 会先画背景再调 `render()`，
  界面里再调 `renderBackground` 必抛 `Can only blur once per frame` —— 三个 Screen 已删除该调用。
- `PlayerModelScreenKey` / `DebugAnimationKey` / `ExtraPlayerConfigKey` 误带了 `needAlt=true`
  （移植 Forge 的 KeyModifier 时抄错），裸按 Y 无反应；已改为只比键位。
- GUI 方法批量回退：`text→drawString`、`centeredText→drawCenteredString`、`horizontalLine→hLine`、
  `verticalLine→vLine`、`textWithWordWrap→drawWordWrap`、`extractRenderState→renderWidget`、
  `extractContents→renderContents`。
- `GuiRenderer` 的 PiP 渲染器表在 1.21.11 是 ImmutableMap：mixin 改为 `@Shadow @Final @Mutable`
  整体替换成 HashMap 再注册。
- Fabric 网络：`PayloadTypeRegistry.serverboundPlay/clientboundPlay → playC2S/playS2C`；
  keymapping 包 → `client.keybinding.v1.KeyBindingHelper`。

### 4b. 配置改动不落盘（移植时丢掉的保存路径）
- 基线的 `YsmConfigSpec` 只有 `load(Path)`，而 `load` 是「读完立刻把当前值写回去」；
  游戏内改的选项（免责声明已读、界面开关、音量、额外玩家渲染位置）只活在内存里，
  重启后回到旧值 —— 玩家体感就是「我勾了，下次还是弹免责声明」。
- 补上 `save()` / `save(Path)` 并把写盘从 `load` 里提出来，`ClientConfig` / `ServerConfig`
  各保留自己的 spec 实例；免责声明接受、配置界面关闭、每个复选框与音量滑条、
  额外玩家渲染界面关闭都会立刻落盘。
- 验证：探针点「已读」→ 关 → 落到 `PlayerModelScreen`，`run/client_a/config/ysm-client.json`
  里 `"DisclaimerShow"` 从 `true` 变 `false`；再点一次「阻止渲染自身玩家模型」
  `DisableSelfModel=true`，点回去 `false`，两次都写进文件。

### 4c. 攻击 / 挖掘完全没有动画（兼容层桩写错）
- `client/animation/predicate/SwingPredicate.java` 先问 Iron's Spellbooks 兼容层：
  `PlayState p = IronsSpellBooksCompat.playAnimation(event, entity); if (p != null) return p;`
- 移植时把这个兼容桩写成 `return PlayState.CONTINUE;`，而契约是「未安装 → 返回 `null`（不介入）」。
  `CONTINUE` 恒非 null，于是 `swing` 整段逻辑被永久短路 —— 表现就是挥臂、持物、使用物品
  **全都没有动画**（挖掘/攻击的手部动作一起没了）。
- 改为 `return null;` 并写明契约注释。验证：日志出现 `play swing_hand (found=true)` 与 `start:`；
  模型区域裁剪后与静止帧的像素差 791 / 878 / 1109（共 24000 px）→ 手臂确实在动。

### 4d. 自定义命令参数类型 → 所有玩家进服即被踢（踩坑记录）
- 起因：`/ysm model set` 的模型路径含 `/`，Brigadier 的 `StringArgumentType.string()`
  按字符白名单（字母/数字/`_-.+`）解析，玩家在聊天框敲 `wine_fox/01_taisho_maid`
  只会吃到 `wine_fox`，剩下报「参数后应有空格分隔，但发现了尾随数据」。
- 错误修法：自写 `GreedyPathArgument` 并登记进 `BuiltInRegistries.COMMAND_ARGUMENT_TYPE`。
  结果任何玩家进服立刻被踢：
  `IllegalArgumentException: Unrecognized argument type ... at ArgumentTypeInfos.byClass`
  → 客户端显示「无效的玩家数据 / Invalid player data」。
- 机制：`ArgumentTypeInfos.BY_CLASS` 是 `private static final Map`，**只在 `bootstrap(Registry)` 里填充**。
  mod 在 `onInitialize` 阶段往注册表 register 的条目不会进这张表，命令树下发时 `byClass` 找不到，
  整棵树序列化失败 → 玩家被服务端踢出。
- 正确修法：**不要新增参数类型**，用 vanilla `StringArgumentType.greedyString()`，
  把可选项改成后缀写法：`/ysm model set <targets> <path>[#texture][@force]`。

### 5. 环境/工程
- 沙箱下 `Files.isWritable()` 恒 false，导致 loom 的 zipfs 只读 → 用 `--patch-module jdk.zipfs=` 打补丁绕过。
- 恢复 protobuf + quickbuf 代码生成（`gradle/schema.gradle`），29 个 proto → 31 个 Java。
- 内置模型索引 `generateBuiltinModelIndex` 恢复（靠 native 算 hash，生成 `assets/ysm/builtin-index.json`）。
- 修回被截断的 `wine_fox/18_wedding/animations/main.animation.json`（155648B → 444796B），
  内置模型目录从 `models=26, errors=1` 变成 `models=27, errors=0`。

## 二、无人值守截图回环的验证结果

探针 `com.elfmcys.ysm.dev.RenderProbe`（仅开发环境注册）：进存档 → 清空主手 → 逐项截图 → 自动退出。
命令：`mc_gradle runClient -Pysm.probeWorld=ysmtest -Pysm.probeCommand="ysm model set @s wine_fox/01_taisho_maid"`

| 项 | 结果 |
| --- | --- |
| 客户端启动 / 51 个 mod / Mixin `JAVA_21` | 通过 |
| native 库加载 | 通过（AVX2 路径，`Native runtime initialized`） |
| 内置模型目录 | `models=27, packs=2, errors=0` |
| 世界模型（第三人称） | 酒狐模型正确站在地面、比例正确、无重影 |
| 第一人称手臂 | 红白袖子 + 白袖口，位于右下、朝前伸（`XP +90°`），换模型后尺寸自动重算 |
| 模型选择界面 PiP 预览 | 参考实体正确显示在左侧预览框 |
| `ExtraPlayerConfigScreen` / `ConfigScreen` | 正常打开与渲染，无崩溃 |
| 编译 / 打包 / 测试 | `mc_gradle build` BUILD SUCCESSFUL |
| 骨骼动画推进 | 连续三帧 `ysm-tp` / `ysm-tp2` / `ysm-tp3`（间隔 15 tick）像素内容均不同；native 顶点 bbox 逐帧变化 |
| 挥手动画（`swing`） | 探针 tick 60 触发 `player.swing`，挥到中段时手臂仍留在画面右侧，无异常 |
| 界面点选 | 探针在模型界面内构造 `MouseButtonEvent` 点中缩略图，选中框转移、无异常 |
| 命令树 | `/ysm model errors` → 「The last model scan had no errors.」；`/ysm model reload` → 「Reloading model catalog...」；未补全的 `/ysmclient molang` 正确报参数缺失（说明命令已注册） |
| 专用服务端启动 | `mc_gradle runServer`：native 库在 SERVER 环境初始化成功、`Published server model catalog: models=27, packs=2, errors=0`、`Done (2.697s)!` |
| 动画轮盘（Z 键界面） | `roulette entries = 8`，八分区（耶 / 握手 / 跳舞 / NO / YES / 拍手 / 握手 / 撒欢大舞）正常绘制，「锁定：关」与选中扇区高亮都正确 |
| 免责声明流程 | 探针点「已读」复选框 → 点关闭 → `screen=PlayerModelScreen`、`DisclaimerShow=false`，且 `ysm-client.json` 已落盘 |
| 配置界面开关 | 点「阻止渲染自身玩家模型」→ `DisableSelfModel=true`，点回 → `false`，两次都即时写入配置文件 |
| 攻击/挖掘动画 | 修复兼容桩后日志 `play swing_hand (found=true)`；挥臂三帧与静止帧像素差 791 / 878 / 1109（共 24000 px） |
| 联机互看（双客户端） | 专用服 + 两台客户端（`ysm_a` / `ysm_b`）：A 执行 `/ysm model set @s wine_fox/01_taisho_maid` 后，B 侧日志 `other=ysm_a model=wine_fox/01_taisho_maid`，截图 `run/client_b/screenshots/ysm-mp-b-1/2` 里能看到 A 的狐狸模型站在石台上；反向 A 看 B 显示默认模型（`ysm-mp-a-1`） |
| `/ysm model set` 客户端解析 | 模型路径含 `/`，原参数用 Brigadier `string()` 会被字符白名单截断（`参数后应有空格分隔，但发现了尾随数据`）；改用 `greedyString()` 后玩家正常敲命令即可生效 |
| 服务器模型下发（客户端本地没有的模型） | 服务端 `run/server/ysm/custom/servertest/remote_fox` 放一份改过 `ysm.json` 的模型（服务端 `models=28`，客户端只有 27 个内置）→ 玩家执行 `/ysm model set @s servertest/remote_fox` 后，客户端从服务器拉取并渲染成功，截图 `run/client_a/screenshots/ysm-mp-set-2` 里是第三人称下的狐狸模型；`run/client_a/ysm/cache/remote/v0/chunks/` 落盘 8 个分块（3 个 blob + 5 个音频 stream） |
| 模型选择重连保持 | 换模型 → 退出客户端 → 清空 `run/client_a/ysm/cache/remote` → 重新进服：日志 `check self=ysm_a model=servertest/remote_fox`，且 8 个分块被重新下载并正常渲染（截图 `ysm-mp-check-2`）——存档持久化（`EntityCapabilityMixin` 写 `ysm:model_id`）与重新拉取两条路都通 |

### 6. Linux 原生库（`libysm.so`）的编译与取证

原版 jar 只带 `ysm.dll`，Linux 服务端加载失败后 `NativeLibUtil` 会把整个 mod 降级
（`YSM native library unavailable, mod features are disabled`）：不解析模型、不发布模型目录、不建握手信道，
表现就是「双方都看到原版 Steve，60 秒后客户端提示版本不同」。

- **目标环境**：用 `sysprobe` 探针模组实测出租服 —— `Ubuntu 22.04`、`glibc 2.35`、`x86_64`、Docker 容器
  （`/proc/1/cgroup` 含 `docker-…`）、JDK 22。宿主机的 `5.19.0-1.el7.elrepo` 只是内核，与编译目标无关。
- **本机 WSL2 不可用**：启用 `VirtualMachinePlatform` 时报 `错误: 14098`，DISM 真实错误 `0x80073712
  ERROR_SXS_COMPONENT_STORE_CORRUPT`，CBS 点名缺 `Microsoft-Windows-SecConfig-Deployment 10.0.26100.7019`
  清单；系统被「优化」过（假 WSUS 策略 + 组件库缺件），ISO 又没有「家庭中文版」SKU 可以原地修复。
  → 改走 **GitHub Actions**（ubuntu-22.04 runner，glibc 2.35 与目标一致）。
- **上游 native 源码分支问题**：`dev` HEAD 已做 checkpoint 重构，JNI 签名与我们这份 Java 绑定整体不同
  （`nBake` 返回 `J` 而 Java 要 `ByteBuffer`、`nRender` 返回 `I` 而 Java 要 `Z`、`OpusDecoder` 换成 `nFeed/nEndInput`）。
  → 钉死到提交 **`ca32a15f795809a94973b746a1d5b46ab53d8dc5`**（30 条签名与 Java 一一对应）。
- **该提交缺的 6 个入口**，用 workflow 里的 heredoc 注入 `modules/lib/src/compat/legacy_entries.cc`：
  `NativeHeapBuffer;nWrap(JI)Ljava/nio/ByteBuffer;` 与 `OpusDecoder` 的 `nCreate()J` / `nInit(JLjava/nio/ByteBuffer;)Z`
  / `nDecode(JLjava/nio/ByteBuffer;)I` / `nReset(J)V` / `nDestroy(J)V`。
- **GCC 16 的四个坑**（都体现在 `.github/workflows/build-native.yml` 里）：
  1. `dav1d` 的 CMake 补丁漏了 `-D_GNU_SOURCE` → `src/lib.c:100: 'RTLD_DEFAULT' undeclared`；用 `CFLAGS`/`CPPFLAGS` 补。
  2. `<format>`/`<mdspan>` 需要 GCC 16（12/13/14/15 都没有），走 `ppa:ubuntu-toolchain-r/test` 并用
     `update-alternatives --install ... 200 && --set` 真正切换。
  3. 上游按 AVX2/FMA 写 `__attribute__((target("avx2,fma")))` + `always_inline`，而
     `conan/cmake/targets/linux-x64.cmake` 基线是 `-march=x86-64` → `target specific option mismatch`
     （`fmaintrin.h:55`）。把该行 sed 成 `-march=x86-64-v3`（目标服 Xeon Platinum 8124M 支持 AVX2/FMA）。
  4. 上游用 clang + libc++，大量 `.h` 靠 libc++ 的隐式包含拿 `std::memcpy`/`std::span`/`std::optional`，
     GCC + libstdc++ 下报 `'memcpy' is not a member of 'std'`（`math/pose_stack.h:33`）→ 在 `CXXFLAGS` 里
     `-include cstring -include cmath -include limits -include span ...` 一次性预包含。
- **产物校验**：`libysm.so` 16,259,008 B，ELF64 DYN，`JNI_OnLoad`/`JNI_OnUnload` 为唯一导出符号，
  `DT_NEEDED` 只有 `libm.so.6 / libc.so.6 / ld-linux-x86-64.so.2`（libstdc++ 与 libgcc 已静态链接），
  `GLIBC` 符号上限 `2.35`；从 `.rodata` 的「签名 / 方法名 / 类名」三连串还原出 **36 个 JNI 入口，与 Java 端 36 个
  native 声明逐一匹配**（含 `image/Image$Native` 这一嵌套类）。
- 已放进 `src/main/resources/META-INF/native/libysm.so`；`NativeLibUtil` 在 GNU/Linux 上把
  `/META-INF/native/libysm.so` 解压到 `~/.ysm/unstable/libysm-core-<版本>.so` 再 `System.load`。

## 三、已知限制

- jar 内带 **Windows x64** 的 `ysm.dll` 与 **Linux x86-64（glibc ≥ 2.35）** 的 `libysm.so`；
  macOS 与 Linux arm64 仍需自行用 `YesSteveModel-Native` 编译。
- `libysm.so` 用 `-march=x86-64-v3` 编译（绕开 GCC 的 target-attribute 内联错误），因此**宿主机 CPU 必须支持
  AVX2/FMA**；不支持时会在加载阶段报 `Illegal instruction`。
- **Iris/OptiFine 光影**：顶点路径按 Iris 格式（56/55/54 字节）自动分派，但本地没有 Iris 环境，
  未做实际光影测试；未装光影时走原生 NEW_ENTITY 路径。
- GUI 预览里原本会画的「地面方块」在 1.21.11 没有对应 API（`BlockModelResolver` 不存在），已省略。
- 多人同步已完成双客户端实测（见上表），但只测了「服务器下发 + 玩家 capability 同步」这条主链路；
  `auth` 授权模型（`SESSION_AUTHORIZED`）与会话鉴权没有走完整流程。
- 模型下发带上整只模型的音频分块（`stream-*`），因此下载量包含模型自带的音效/语音；大模型（十几 MB 音频）首次
  拉取会明显变慢。后续可改成「先下几何与贴图、音频按需再拉」。
- 客户端缓存 `ysm/cache/remote` 未做容量上限与淘汰（服务端模型更新会换 hash，旧分块会留在本地）。
- `GameRendererProjectionMixin` 在 1.21.11 无对应 API（`ProjectionMatrixBuffer` 已拆分），
  保留为空壳；投影矩阵改由 `ProjectionMatrixBufferMixin`（`PerspectiveProjectionMatrixBuffer#getBuffer`）抓取。

## 四、键位与命令

| 键 | 功能 |
| --- | --- |
| Y | 打开玩家模型界面 |
| Z | 动画轮盘 |
| L | 锁定轮盘 |
| B | 动画调试屏 |
| P | 额外玩家渲染配置 |

- 服务端命令：`/ysm model|auth|export|play_animation|molang|ping`
- 客户端命令：`/ysmclient molang|watch|debug|armpose`
- 设置模型：`/ysm model set <targets> <path>[#texture][@force]`
  （路径用 `greedyString`，所以贴图与「跳过鉴权」写成后缀；例：
  `/ysm model set @s wine_fox/01_taisho_maid`、`/ysm model set @a wine_fox/01_taisho_maid#alt`）


### 5. 玩家服掉线 `Received unknown packet id 80` 的定位结论
- 现象：玩家客户端（69 个 mod）进纯 Fabric 服（只有 fabric-api + 本 mod）后数秒被踢，
  客户端报 `Internal Exception: io.netty.handler.codec.DecoderException: Received unknown packet id 80`，
  服务端日志 `X lost connection: <同一句>`（这句是客户端发来的断开原因，说明异常发生在**客户端解码**）。
- 对照实验：只装本 mod 的开发客户端连同一台服，11 秒全程正常 → **与本 mod 无关**。
- 真因：`[机械动力飞越版] create-fly-1.21.11-6.0.9-5.jar`（mod id = `create`，version 6.0.9-5）
  用 MixinExtras 的 `@WrapOperation` 改写 `GameProtocols` 的注册调用：
  `GameProtocolsMixin.addS2CPacket/addC2SPacket` 把 `com.zurrtum.create.AllPackets.C2S/S2C`
  里的自定义包注册进协议表。客户端装了、服务端没装 → 客户端的包 id 表多出若干项，
  服务端按 vanilla 表发的包在客户端被解析成别的 id，最终出现「unknown packet id」并断开。
- 修法：要么服务端装同一份 `create`（它 `environment: *`，可直接扔进服务端 mods），
  要么客户端把它（及依赖它的 create 系列附加）移出 mods。`graceful-packets` 只能掩盖症状。
