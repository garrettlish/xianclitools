# xiancli 工具集（xiancli-tools）

一个图文并茂的 Android 工具集 App。首页展示工具列表，目前包含「定时器」和「手机统计」两个工具：

- **首页**：工具卡片列表（圆形图标 + 名称 + 描述）。
- **定时器**：5 分钟 / 10 分钟 / 30 分钟 / 1 小时 / 自定义时长五个图文卡片，点击即开始倒计时；右上角齿轮按钮进入通知设置；定时进行中显示倒计时卡片，可取消。
- **手机统计**：图表展示应用数量（总数 / 第三方 / 系统 / 可启动）、应用分类分布（环形图）、内部存储与运行内存占用。
- **通知设置**：震动开关 + 铃声选择（系统铃声、通知音及手机中的音乐），并支持「试听当前提醒」。
- **默认行为**：震动 + 系统默认铃声。

---

## 一、环境要求

| 项目 | 要求 |
| --- | --- |
| 操作系统 | macOS |
| 开发工具 | Android Studio（本项目用其内置 JDK 构建） |
| JDK | Android Studio 自带 JBR（推荐），或自行安装 JDK 17 |
| Android SDK | 需 `platforms;android-37.0` 与 `build-tools;36.0.0` |
| 手机 | Android 7.0（API 24）及以上 |

> 本机 SDK 路径：`/Users/chengcheng/Library/Android/sdk`（写在 `local.properties` 里）。
> 本项目 `minSdk = 24`，`targetSdk / compileSdk = 37`。

### 关于 Java

如果你在终端执行 `java -version` 报 “Unable to locate a Java Runtime”，这是正常的：系统没装独立 JDK。二选一即可：

- **方案 A（推荐，用 Android Studio 内置 JDK）**：
  ```bash
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```
- **方案 B（自己装一个）**：
  ```bash
  brew install --cask temurin
  ```

后续所有 `./gradlew` 命令都需要先设置 `JAVA_HOME`。

---

## 二、用 Android Studio 运行与测试（最简单）

1. 打开 Android Studio，选择 **Open**，指向本目录：`~/AndroidStudioProjects/xianclitools`。
2. 首次打开会自动 **Gradle Sync**（需要联网下载依赖，耐心等待）。
3. 顶部选好设备（模拟器或真机）后，点击绿色 **Run ▶** 按钮即可安装并启动。

这是最省事的方式，模拟器/真机/权限授权都由 IDE 处理。

---

## 三、命令行生成 APK

先进入项目目录并设置 JDK：

```bash
cd ~/AndroidStudioProjects/xianclitools
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

### 1. Debug APK（已用调试证书签名，可直接安装）

```bash
./gradlew assembleDebug
```

产物：`build/outputs/apk/debug/xiancli-tools-debug.apk`

### 2. Release APK（未签名）

```bash
./gradlew assembleRelease
```

产物：`build/outputs/apk/release/xiancli-tools-release-unsigned.apk`

> Release 包默认没有签名，无法直接安装，需要签名后才能装到手机。见下一节。

### 3. 清理构建

```bash
./gradlew clean
```

---

## 四、给 Release APK 签名

### 方式一：Android Studio 图形界面（推荐）

菜单 **Build → Generate Signed App Bundle / APK…**，选择 **APK**，按向导新建或选择 keystore，即可生成带签名的 release APK。

### 方式二：命令行

**第 1 步：生成签名密钥（只需一次，请妥善保管 `.jks` 和密码）**

```bash
keytool -genkeypair -v \
  -keystore ~/xiancli-release.jks \
  -alias xiancli \
  -keyalg RSA -keysize 2048 -validity 10000
```

**第 2 步：对齐 + 签名**

```bash
BT=~/Library/Android/sdk/build-tools/36.0.0
cd ~/AndroidStudioProjects/xianclitools

# 4 字节对齐
"$BT/zipalign" -v -p 4 \
  build/outputs/apk/release/xiancli-tools-release-unsigned.apk \
  xiancli-tools-release-aligned.apk

# 签名（会提示输入 keystore 密码）
"$BT/apksigner" sign \
  --ks ~/xiancli-release.jks \
  --out xiancli-tools-release-signed.apk \
  xiancli-tools-release-aligned.apk

# 校验签名
"$BT/apksigner" verify --print-certs xiancli-tools-release-signed.apk
```

产物：`xiancli-tools-release-signed.apk`，可直接安装分发。

---

## 五、安装到手机并测试

### 1. 真机（USB，最常用）

1. 手机打开 **设置 → 关于手机**，连点「版本号」7 次开启开发者模式。
2. **设置 → 开发者选项**，打开 **USB 调试**。
3. 用数据线连接 Mac，手机上弹出「允许 USB 调试」时点允许。
4. 确认设备已连接：
   ```bash
   ~/Library/Android/sdk/platform-tools/adb devices
   ```
   能看到设备序列号即成功（首次为 `unauthorized` 时在手机上确认授权）。
5. 安装并启动：
   ```bash
   ADB=~/Library/Android/sdk/platform-tools/adb

   # 安装 debug 包（-r 覆盖安装）
   "$ADB" install -r build/outputs/apk/debug/xiancli-tools-debug.apk

   # 启动 App
   "$ADB" shell am start -n com.example.xiancli_tools/.MainActivity
   ```

> 建议把 `adb` 加进 PATH，省去每次写全路径：
> ```bash
> echo 'export PATH=$PATH:~/Library/Android/sdk/platform-tools' >> ~/.zshrc
> source ~/.zshrc
> ```

### 2. 真机（无线调试，Android 11+）

1. 手机 **开发者选项 → 无线调试 → 使用配对码配对设备**，会显示一组 `IP:配对端口` 和 6 位配对码。
2. Mac 上：
   ```bash
   adb pair 192.168.x.x:配对端口      # 输入手机上显示的 6 位码
   adb connect 192.168.x.x:连接端口   # 注意：连接端口和配对端口不同，见无线调试页
   adb devices                        # 确认已 connected
   ```
3. 之后 `adb install -r <apk>` 即可。

### 3. 模拟器

- **推荐**：Android Studio 菜单 **Device Manager → Create Device**，选个系统镜像创建 AVD，然后点 Run。
- 本机当前还没有创建任何 AVD（`emulator -list-avds` 为空），用上面的图形界面创建即可。

### 4. 查看运行日志

```bash
ADB=~/Library/Android/sdk/platform-tools/adb

# 全部日志
"$ADB" logcat

# 只看本 App 的日志（按 tag/PID 过滤）
"$ADB" logcat --pid=$("$ADB" shell pidof -s com.example.xiancli_tools)
```

### 5. 卸载

```bash
"$ADB" uninstall com.example.xiancli_tools
```

---

## 六、功能测试要点

| 功能 | 测试方法 |
| --- | --- |
| 首页 | 打开 App，看到工具卡片「定时器」「手机统计」，点击进入对应页面 |
| 设置定时 | 点任一时长卡片，底部出现 Snackbar，页面上方出现倒计时卡片 |
| 自定义时长 | 点「自定义」卡片，填写时/分/秒后点「开始」，按所填时长倒计时 |
| 取消定时 | 在倒计时卡片点「取消」 |
| 到点提醒 | 可先用「通知设置 → 试听当前提醒」验证铃声+震动；正式到点会循环响铃震动，通知栏点「停止」结束 |
| 震动开关 | 通知设置里开关震动，试听时验证效果 |
| 铃声选择 | 通知设置 → 提示铃声 → 在系统选择器中挑一首，返回后试听 |
| 手机统计 | 首页点「手机统计」，查看应用数量卡片、分类环形图、存储与内存条形图 |
| 通知权限 | 首次启动会申请「通知」权限，务必允许，否则到点可能看不到通知 |
| 精确闹钟 | 若定时页顶部出现黄色提示条，点「去开启」允许精确闹钟，定时更准时 |

> App 需要的权限：通知（POST_NOTIFICATIONS）、震动（VIBRATE）、精确闹钟（USE_EXACT_ALARM / SCHEDULE_EXACT_ALARM）、前台服务（FOREGROUND_SERVICE + SPECIAL_USE）、应用列表（QUERY_ALL_PACKAGES，用于统计已安装应用）。
> `QUERY_ALL_PACKAGES` 属于敏感权限，若上架 Google Play 需说明用途；仅自用/内部分发无影响。

---

## 七、常见问题

**Q：`./gradlew` 报找不到 Java？**
先执行第三节的 `export JAVA_HOME=...`。也可在 Android Studio 中 **Settings → Build → Build Tools → Gradle → Gradle JDK** 选择内置 JBR。

**Q：构建时报找不到 SDK / 平台 37？**
用 Android Studio 打开项目让它自动 Sync 安装；或 **Settings → Languages & Frameworks → Android SDK** 勾选 `Android 37` 与 `Android SDK Build-Tools 36`。

**Q：`adb: command not found`？**
用全路径 `~/Library/Android/sdk/platform-tools/adb`，或把它加入 PATH（见第五节）。

**Q：`xiancli-tools-release-unsigned.apk` 装不上？**
未签名不能安装，按第四节签名，或直接用 `xiancli-tools-debug.apk`。

**Q：定时到了没响？**
1) 允许通知权限；2) 定时页若提示未开启精确闹钟，去开启；3) 部分国产 ROM（小米/华为/OPPO/vivo）需在系统设置里给本 App 开「自启动 / 后台运行 / 省电白名单」，否则后台可能被杀。

**Q：铃声里找不到自己的音乐？**
系统的铃声选择器会列出铃声/通知音，部分设备也支持浏览本地音乐；如未列出，属于 ROM 限制，可先点「试听」验证，或选择系统铃声。

---

## 八、项目结构

```text
xianclitools/                         # 根目录即应用模块（单模块扁平结构）
├── build.gradle.kts                  # 应用模块构建脚本（android + dependencies）
├── settings.gradle.kts               # 项目名与仓库配置（无 include）
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/libs.versions.toml         # 依赖版本目录
├── local.properties                  # 本机 SDK 路径（勿提交）
└── src/
    ├── main/
    │   ├── AndroidManifest.xml       # 权限、Receiver、Service 声明
    │   ├── java/com/example/xiancli_tools/
    │   │   ├── MainActivity.kt       # 入口 Activity
    │   │   ├── data/
    │   │   │   ├── SettingsRepository.kt     # 通知偏好持久化
    │   │   │   ├── RingtoneResolver.kt       # 铃声 URI / 名称解析
    │   │   │   └── PhoneStatsRepository.kt   # 应用数量/分类/存储/内存采集
    │   │   ├── timer/
    │   │   │   ├── NotificationHelper.kt   # 通知渠道与响铃通知
    │   │   │   ├── TimerScheduler.kt       # AlarmManager 精确定时调度
    │   │   │   ├── TimerAlarmReceiver.kt   # 定时到点广播接收
    │   │   │   └── AlarmRingingService.kt  # 前台服务：响铃 + 震动
    │   │   └── ui/
    │   │       ├── AppRoot.kt              # 页面导航 + 通知权限申请
    │   │       ├── HomeScreen.kt           # 首页工具列表
    │   │       ├── TimerScreen.kt          # 定时器页（含自定义时长弹窗）
    │   │       ├── PhoneStatsScreen.kt     # 手机统计页（图表）
    │   │       ├── SettingsScreen.kt       # 通知设置页
    │   │       ├── components/
    │   │       │   ├── IconBadge.kt        # 圆形图标徽章
    │   │       │   └── Charts.kt           # 环形图 / 堆叠条形图（Canvas 手绘）
    │   │       └── theme/                  # Compose 主题
    │   ├── keepRules/                # R8 keep 规则
    │   └── res/drawable/             # 矢量图标（定时器/统计/存储/内存等）
    ├── test/                         # 单元测试
    └── androidTest/                  # 仪器测试
```

---

## 九、技术栈

- Kotlin + Jetpack Compose（Material 3）
- `AlarmManager` 精确定时 + `BroadcastReceiver`
- 前台服务（`specialUse` 类型）播放铃声与震动
- `SharedPreferences` 存储设置
- Gradle Kotlin DSL + Version Catalog（`gradle/libs.versions.toml`）
