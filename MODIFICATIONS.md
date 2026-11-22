# 相对上游源码的修改与修复说明

本仓库基于上游 `src.zip`（`huducat586/hicar-android-receiver` 的快照）进行下游定制。
`main` 分支为原始基线，另有两个设备专用分支：

| 分支 | 目标设备 | 说明 |
| --- | --- | --- |
| `main` | — | 原始 `src.zip`，未作修改 |
| `oneplus7-android17` | 一加 7 Pro / Android 17 (ColorOS) | 完整功能整合 + 全部连接修复 + 双指触控 + 界面重排 |
| `byd-dilink5-android10` | 比亚迪秦 PLUS DM-i / DiLink 5 / Android 10 | 在一加分支基础上，改为 16:9 分辨率与 Wi-Fi Direct 建组适配 |

两个下游分支**共享**下列"连接修复 / 功能整合 / 界面 / 双指"改动；仅在热点实现与分辨率档位上不同（见文末"分支差异"）。

---

## 一、连接链路修复（关键，缺一不可）

这几项是从"手机反复停在正准备连接、Wi-Fi 始终连不上"一步步定位并修复的。

### 1. 真实 BSSID 获取（root 补丁）
- **问题**：ColorOS 把软热点的 BSSID 对所有公开 / SystemApi（`getBssid()`、`SoftApInfo` 反射）全部隐藏，只能拿到 `null` 或 `02:00:00:00:00:00`。凭据里 BSSID 为空时，华为手机拿到凭据后不会发起 Wi-Fi 关联，一直卡在"正准备连接"。
- **定位证据**：`tcpdump -i wlan1` 抓不到任何手机的认证帧；hostapd 有 `AP-ENABLED` 但无 `AP-STA-CONNECTED`。
- **修复**：设备授予 root 后，应用通过 `su` 读取软热点接口 `/sys/class/net/<ap>/address` 得到每轮随机的真实 BSSID，填入凭据。
  - 一加分支：`LocalHotspot.rootBssid(owner)` 通过 `/system/bin/su -c` 读取。
  - 车机分支：`P2pHotspot.rootBssid(owner)` 同样逻辑（车机无 root 时静默回退到公开 API）。
- **涉及文件**：`LocalHotspot.java`、`P2pHotspot.java`。

### 2. 凭据只发 SSID 时的死锁（ssidOnly）
- **问题**：原逻辑要求必须先扫描到真实 BSSID 才发凭据，ColorOS 拒绝扫描自身热点，导致凭据永远发不出去。
- **修复**：拿不到 BSSID 时不再等待，直接用 ssidOnly 凭据继续；`LocalApInfo.Events` 增加 `frequency(int)` 回调，BSSID 为 null 时也回传频率避免超时。
- **涉及文件**：`LocalHotspot.java`、`LocalApInfo.java`、`NetworkResource.java`。

### 3. 5GHz 信道掩码错误
- **问题**：`frequencyMask()` 的 5GHz 信道表缺失部分信道，导致能力协商失败。
- **修复**：补齐 9 个 5GHz 信道 `{5180,5200,5220,5240,5745,5765,5785,5805,5825}`。
- **涉及文件**：`NetworkResource.java`。

### 4. RTSP header 解析过于严格
- **问题**：投屏最后一步 HiSight RTSP 的 `OPTIONS` 消息里，华为手机的第一行 header 没有冒号，原 `parse()` 直接抛 `IOException("rtsp header")`，导致会话断开。
- **定位证据**：日志 `HiSight 协议拒绝分类：rtsp header`，此时视频端口、加密、解码器全部已就绪，仅卡在 OPTIONS。
- **修复**：对无冒号 / 非法 header 行改为记录并跳过，而不是抛异常断开。
- **涉及文件**：`HiSightWire.java`（`[RTSP容错]`）。

### 5. 30 秒空闲误判断线
- **问题**：DMSDP 控制连接只要 30 秒没收到手机数据就主动断开；但投屏时视频走独立 TCP、音频走 UDP，控制连接上手机本就不发数据（心跳是车机单向发的），导致投屏稳定跑到 30 秒（约 300 帧）时被误杀。
- **修复**：投屏中（`session.mediaBusy == true`）时，控制连接空闲不再触发断开；断线改由视频会话自身存活和真实 socket 错误判定。
- **涉及文件**：`ReceiverEngine.java`。

### 6. 点击功能就断联（触控坐标越界）
- **问题**：整合 1080P 后，`UiInput.encode()` 的触控坐标边界检查仍硬编码旧的 800×480，点击投屏画面（坐标如 x=1500）即抛 `IOException` → 手机端判定输入异常，关闭整个会话。
- **修复**：改为动态读取 `VideoProfile.width()/height()`。
- **涉及文件**：`UiInput.java`。

### 7. 免输码重连 / 本机蓝牙派生身份
- **问题**：安装 / 重编译后 Hub 身份变化，手机端保存的旧身份失效，首次连接后无法再次连接。
- **修复**：在"设置与诊断"里增加"设置本机蓝牙身份"，保存由本机蓝牙地址派生出的 `wire_id` / `wire_model`；启动时用 `WireIdentity.select()` 选择该稳定身份。删除手机端旧配对记录后重新输码配对即可稳定重连。
- **涉及文件**：`MainActivity.java`、`WireIdentity.java`、`ReceiverEngine.java`、`AutoStart.java`、`LocalIdentityProbe.java`。

---

## 二、功能整合（来自社区功能版）

- **动态分辨率**：`VideoProfile` 提供多档分辨率，通过能力上报与解码器参数全链路生效；首页可切换，下次连接生效。
- **界面风格 / 主题**：`UiTheme`、`ThemeActivity` 的 Material 3 风格与深浅色主题。
- **比例缩放**：`UiScale`，只改变上报 density，不改变线上分辨率。
- **小艺声音与音乐外放**：`AudioSink` 在语音播报时不打断音乐、低音量放大；`MicrophoneControl` 把麦克风启动提前到 SETUP 阶段。
- **图标**：恢复自适应图标资源（`res/mipmap` + `res/drawable`）。

---

## 三、双指触控

- **上游单指协议**：`hisight_input.py` 使用 `>BHBBHHIB`（14 字节）。仅更换 pointer ID、仍每包发送一个触点，无法完整表达同时存在的双指；在荣耀平板上实测只能单指或双击缩放。
- **修复**：
  - `UiInput.Event` 保存完整触点快照，触点数和报文长度随触点集合变化；每个触点包含 ID 和坐标，末尾写入时间与变化触点索引。单指字节格式保持兼容。
  - `InputRouter` 按 pointer ID 记录最多 5 点，校验完整集合和按下/抬起状态；抬起包仍包含被抬起的触点，发送后再移除，取消时逐步释放。
  - `VideoActivity` 用 `SparseIntArray` 把 Android pointerId 映射到空闲的 UIBC ID；按下、抬起和移动均发送完整集合，一轮双指移动只发送一个报文，避免原编号取模复用碰撞。
- **验证**：2026-11-22，HONOR CHG-W60 / Android 17，用户确认修复后的双指缩放和单指均正常；完整协议回归与真实 TCP 加密双指报文检查通过。

---

## 四、界面重排

- 标题与版本合并一行，状态卡片置顶。
- 主操作区改为两列网格（开始 / 停止 / 重新连接 / 试运行自动）。
- 分辨率与比例各占一行；麦克风与开机自启合并到一个卡片；设置类按钮两列网格。
- 功能说明小字从正文（15sp）改为小字（11sp）并精简文案。
- 一屏显示完整，无需大幅滚动。
- 涉及文件：`HomeActivity.java`（`card/row/cell/sizeButtons` 辅助方法）。

### 接收画面自适应全屏（2026-11-22）

- 接收页默认全屏，画面随屏幕宽高铺满，保留全部内容，比例随屏幕调整；普通模式保留等比例显示。
- 连接信息改为画面内浮层，不再挤占视频高度；全屏隐藏系统栏与底部按钮，菜单可切换“退出全屏 / 自适应全屏”。
- 全屏和旋转后重新测量视频视图，并将浮动菜单限制在可见区域。触控继续按实际显示宽高映射到协商分辨率。
- HONOR CHG-W60 实测视频视图占满 3048×2032；退出全屏后回到等比例画面，重新进入后恢复铺满，沿用同一接收会话。

### 平板侧栏比例与文字清晰度

- 原手机比例的视频被直接铺到 3:2 平板后，侧栏图标横向受压；单独调整界面密度未解决这个比例问题。
- 各分辨率档位按接收端屏幕比例计算上报宽度，保留所选档位高度，并同步用于显示服务、HiSight、解码和触控。例如本平板 2K 档位使用 2160×1440，避免将 3120×1440 横向压窄。
- 视频目标码率随像素数调整，限制在 2–12 Mbps；本平板 2K 档位约 6.2 Mbps，改善原 2 Mbps 高分辨率画面中文字的压缩损失。
- 当前平板保留 150% 界面比例。新增长宽比、编码尺寸和码率回归，完整 20 项主机测试通过。HONOR CHG-W60 真机视频输出 2160×1440，全屏截图确认左侧栏更宽、图标恢复接近方形。

---

## 五、分支差异（仅这两处）

| 项目 | `oneplus7-android17` | `byd-dilink5-android10` |
| --- | --- | --- |
| 热点实现 | Android 13+ 软热点 `LocalHotspot`（SDK≥33） | Android 10 走 Wi-Fi Direct `P2pHotspot`（SDK<33） |
| 分辨率档位 | 手机比例：1080P=2340×1080 等 5 档 | 16:9：1080P=1920×1080 / 720P=1280×720 / 480P=854×480 |
| P2P 信道 | 固定 5GHz 149 | 5GHz 149 失败自动降级 2.4GHz 6 |
| versionName | 0.11.1 | 0.11.1-byd |

车机无 root：`byd` 分支的 root BSSID 读取会静默失败并回退到公开 API，主要依赖 Android 10 P2P 公开接口 `getOwner().deviceAddress`。

---

## 六、构建

按 `app/build.sh` 构建（JDK 17 + Android SDK platform 35 / build-tools 35.0.0）。本项目的 Windows 本地构建额外依赖若干 shell 工具 wrapper，仅用于本机编译，不属于应用运行依赖。
