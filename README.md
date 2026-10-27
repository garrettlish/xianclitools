# 朝夕（xiancli-tools）

> 原名「方寸」。因为六个工具里只有足迹、出行与「方寸」的语义相关，其余的时间、金钱、文字、饮品都落不进去，故改名为「朝夕」——早晚即一日，覆盖全部工具，也与首页的「今天」卡片同频。
> 代码目录名、包名（`com.example.xiancli_tools`）和备份格式标识（`"app": "fangcun"`）均保持不变，前者避免重装丢数据，后者保证历史备份仍可导入。

一个图文并茂的 Android 工具集 App。首页展示「今天」概览与工具列表，目前包含「计时」「备忘」「账本」「足迹」「饮记」「出行」六个工具，「回顾」通过首页顶部的今日概览卡进入，「数据备份」作为全局操作放在「工具列表」标题右侧：

- **首页**：顶部「今天」卡片（一行五格：支出 / 专注 / 待办 / 足迹 / 饮记，点进回顾页），下面是工具卡片列表（圆形图标 + 名称 + 描述）。
- **计时**：分「计时 / 明细 / 统计」三个标签——计时页有 10 分钟 / 25 分钟 / 50 分钟 / 自定义时长四张图文卡片，点击即开始倒计时，定时进行中显示倒计时卡片可取消，右下角是通知方式；明细页按区间列出每次专注（按天分组，标题带当天总时长，区分「已完成 / 中断」，可删除）；统计页出总时长、完成率、中断次数、近 7 天趋势与时段分布。
- **备忘**：页面顶部用「记事 / 明细 / 统计」三个标签分开——记事页按类型（提醒、待办、日志、心情、灵感、其他）快速记事，可编辑、删除；「提醒」「待办」可单独设置提醒时间，「心情」可选 1~5 级；明细页顶部是未完成待办清单（可直接勾掉），下面按日 / 周 / 月 / 年 / 自定义区间分组列出记录；统计页出汇总、心情日历、趋势与类型占比。
- **账本**：同样分「记账 / 明细 / 统计」三个标签——记账页按类型（餐饮、交通、酒店、门票、购物、娱乐、医疗、其他）快速记账；明细页按区间列出账目（按天分组，标题带当天合计，可删除）；统计页出汇总、支出趋势柱状图与分类占比环形图。
- **足迹**：分「记录 / 明细 / 统计」三个标签——记录页步行 / 骑车 / 开车轨迹记录；明细页按区间列出轨迹（按天分组，可点开地图查看单条或全部，可删除）；统计页出总里程 / 总时长与按交通方式占比。
- **饮记**：分「记录 / 明细 / 统计」三个标签——记录页是三类一排的色块网格（默认「水 / 咖啡 / 茶 / 饮料 / 维C」），点一下记一次、格内显示今天已喝几次，长按可改名改色或删除，格子下方是本周/本月的记录热力图；明细页按区间列出每次记录（按天分组、标题带当天次数，可补记任意时间、可删除，年视图先按月索引再下钻）；统计页出总次数、各类次数色卡、堆叠柱图与热力图，并可按类别筛选。
- **出行**：按日期区间把账本、足迹、备忘框成一次行程，显示这趟的花费、里程与分类构成；会根据酒店 / 门票的消费自动认出候选行程，确认后一键保存。
- **回顾**：日 / 周 / 月三个视角，把六个工具的数据并排摆出来——花销构成、专注时长、里程、记录条数、未完成待办、饮记次数，并生成一句话总结。
- **数据备份**：一键把备忘、账本、足迹、专注记录、饮记、出行导出成一个 zip 压缩包（内含一份 JSON，体积约为裸 JSON 的四分之一）；一键导入（可选「合并」或「覆盖」），老版本导出的裸 `.json` 备份同样能导入。
- **通知设置**：震动开关 + 铃声选择（系统铃声、通知音及手机中的音乐），并支持「试听当前提醒」。
- **提醒**：备忘到点弹通知，带「完成」「稍后 10 分钟」两个动作，不用打开 App 就能处理；手机重启后所有闹钟会自动恢复。
- **默认行为**：震动 + 系统默认铃声。
- **标签记忆**：计时 / 备忘 / 账本 / 足迹 / 饮记的三个标签会记住上次停留的位置——在足迹的「明细」里返回首页，逛完别的工具再进来，还是「明细」，不会重置回第一个标签；转屏与进程重建后同样保持。
- **界面风格**：暖白纸底（`#FAF7F2`）+ 白卡片 + 一套压饱和的暖色强调色（琥珀 / 藕紫 / 咖褐 / 苔绿 / 水蓝），亮暗两套配色都关闭了动态取色，保证各工具的颜色跨页一致。五个工具的三个模式标签（记录 / 明细 / 统计）左侧各带一枚同款小图标，换工具不用重新认位置。
- **亮暗切换**：首页「备份」右侧是一枚圆形按钮，亮底显示月亮、暗底显示太阳（图标表示「点了会变成什么」），点一下整套配色连同状态栏图标一起翻转，选择会记住；没点过时跟随系统。

> **统计默认区间**：备忘 / 账本 / 足迹 / 饮记的明细与统计打开时默认选中「月」，即本月 1 日到月底（而不是只统计今天）；计时默认选中「周」，因为一天通常只有一两次计时。所有工具都可选「日 / 周 / 月 / 年 / 自定义」。
>
> **自定义区间**（各工具报表通用）：选「自定义」后分别点开始、结束日期即可；默认是最近 1 周（含今天），开始晚于结束时自动对调。趋势图按跨度自动选粒度：≤1 天按小时、≤2 周按天、≤17 周按周、更长按月。

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

## 六、数据备份（导出 / 导入）

首页点标题右侧的「备份」进入，页面顶部显示当前备忘 / 账本 / 足迹 / 专注 / 饮记 / 出行的条数。

- **一键导出全部数据**：弹出系统文件选择器（默认文件名 `朝夕备份-yyyyMMdd-HHmm.zip`，默认存到「下载」），把六类数据写进同一个 zip。走 SAF，不需要任何存储权限，也不会上传。
- **一键导入数据**：选中一个备份文件后弹出确认框，两种方式：
  - **合并导入**：保留现有数据，只补充文件里 id 不存在的新记录（同一次导入里重复 id 只保留第一条），并提示「新增 N 条，跳过 M 条」。
  - **覆盖导入**：先清空现有数据，再写入文件内容。

备份文件是一个 zip，里面只有一个 `backup.json`，内容如下（`schema` 为版本号，导入时会校验，非本 App 的备份或更高版本会被拒绝并提示）。读写都是流式的——逐条序列化 / 逐条解析，同一时刻只有一条记录在内存里，所以几年份的足迹也能导出导入：

```json
{
  "app": "fangcun",
  "schema": 4,
  "exportedAt": 1792750728208,
  "counts": { "notes": 1, "ledger": 1, "tracks": 0, "timers": 1, "drinkCategories": 2, "drinkRecords": 3, "trips": 0 },
  "notes":  [ { "id": 1792750651589, "category": "TODO", "content": "…", "time": 1792750637539, "remindAt": 1792754237539, "done": false, "mood": 0, "updatedAt": 1792750637539, "deletedAt": 0 } ],
  "ledger": [ { "id": 1792750682256, "category": "FOOD", "amount": 3550, "note": "", "time": 1792750667818, "updatedAt": 1792750667818, "deletedAt": 0 } ],
  "tracks": [ { "id": 1, "mode": "BIKE", "start": 0, "end": 0, "distance": 1234.5, "points": [[31.23, 121.47, 0]], "updatedAt": 0, "deletedAt": 0 } ],
  "timers": [ { "id": 1792750000000, "label": "25 分钟", "planned": 1500000, "start": 1792750000000, "end": 1792751500000, "completed": true, "updatedAt": 1792751500000, "deletedAt": 0 } ],
  "drinkCategories": [ { "id": 1792750000001, "name": "水", "color": "#2F86D6", "created": 1792750000000, "updatedAt": 1792750000000, "deletedAt": 0 } ],
  "drinkRecords":    [ { "id": 1792750000003, "categoryId": 1792750000001, "time": 1792751000000, "updatedAt": 1792751000000, "deletedAt": 0 } ],
  "trips":  [ { "id": 1792750000002, "name": "西安三天", "start": 1792748400000, "end": 1792756800000, "note": "", "updatedAt": 1792756800000, "deletedAt": 0 } ]
}
```

> `amount` 单位是分；轨迹 `points` 每项是 `[纬度, 经度, 毫秒时间戳]`；`remindAt` 为 0 表示不提醒；`mood` 为 0 表示没记心情等级；饮记的 `color` 是 `#RRGGBB`，`drinkRecords.categoryId` 指向某个类别 id。
> `updatedAt` 是最后修改时间、`deletedAt` 非 0 表示删除墓碑，多设备合并按 `updatedAt` 取新（见第九节）。
> `"app": "fangcun"` 是改名前的格式标识，**故意保留不改**：历史备份里写的就是这个值，改了会让新旧备份分成两套格式。
> **旧版本兼容**：schema 1 的备份缺的字段取默认值（不提醒 / 未完成 / 无心情）；schema 3 才有的 `updatedAt` / `deletedAt` 在读更旧的备份时回退到记录自身时间、视为未删除；schema 4 用 `drinkCategories` / `drinkRecords` 取代了下线的「习惯」，旧文件里的 `habits` 数组会被静默忽略，其余数据照常导入。导出则一律写 schema 4。
> 本地存储文件与备份文件共用 `data/JsonCodec.kt` 里的同一套字段，避免两边格式漂移。
> 轨迹坐标只保留 6 位小数（约 0.11 米，低于 GPS 的 3–10 米精度），单点从约 88 字节缩到约 34 字节。
> 读取支持两种来源：zip（看 `PK` 魔数识别）和裸 JSON（老版本备份、局域网直传的 HTTP 正文），自动区分。

---

## 七、功能测试要点

| 功能 | 测试方法 |
| --- | --- |
| 首页 | 打开 App，顶部有「今天」卡片（今日支出 / 专注 / 待办 / 足迹 / 饮记），下面依次是「计时」「备忘」「账本」「足迹」「饮记」「出行」；右上角是「备份」按钮（点击进入备份页）与其右侧的亮暗切换钮 |
| 亮暗切换 | 点首页「备份」右边的圆形按钮：亮底显示月亮，点后整套界面转暗、按钮变成太阳，状态栏与导航栏图标同时由深转浅；杀掉进程重进仍是上次选的模式 |
| 今日概览 | 记一笔账、跑一次计时、留一条待办、录一段轨迹或点一次饮记后回首页，「今天」卡片里对应的数应跟着变（一行五格：支出 / 专注 / 待办 / 足迹 / 饮记，都只报今天）；点卡片进回顾页 |
| 标签切换 | 计时 / 备忘 / 账本 / 足迹 / 饮记五页顶部各有三个标签（计时页是「计时·明细·统计」，其余是「记事 / 记账 / 记录」「明细」「统计」），每个标签文字左侧带一枚小图标（加号 / 列表 / 柱图），切换时互不干扰，各自的滚动位置会保留 |
| 标签记忆 | 在任一工具的「明细」标签下返回首页，去看别的工具再进来，仍停在「明细」而不是重置回第一个标签；转屏后同样保持 |
| 统计默认区间 | 分别打开备忘 / 账本 / 足迹 / 饮记的「明细」或「统计」标签，默认选中的是「月」，汇总标题显示「yyyy年M月」、图表覆盖整月；计时页默认是「周」，覆盖本周一到周日 |
| 明细分组 | 「明细」标签里切换「日 / 周 / 月 / 年 / 自定义」，记录按自然日分组成卡片：标题依次是「今天」「昨天」「10月24日 星期六」，并带当天条数 / 当天合计 / 当天里程 |
| 快速记账 | 账本页「记账」标签填写金额、选择类型（可加备注、改时间），点「保存」后可在「明细」标签里看到 |
| 记账报告 | 「统计」标签里切换「日 / 周 / 月 / 年 / 自定义」，查看汇总、支出趋势柱状图与分类占比环形图 |
| 备忘报告 | 「统计」标签里切换「日 / 周 / 月 / 年 / 自定义」，查看条数、记录趋势与类型占比；「明细」标签里点一条记录会跳回「记事」标签进入编辑 |
| 自定义区间 | 明细 / 统计两个报表标签切到「自定义」：默认最近 7 天（含今天）；点开始 / 结束日期改范围，汇总标题与图表随之变化；把开始日期改到结束之后会自动对调 |
| 足迹报告 | 「统计」标签里切换「日 / 周 / 月 / 年 / 自定义」，查看总里程、总时长与按交通方式占比；「明细」标签里可点开地图查看单条或区间内全部轨迹 |
| 删除确认 | 备忘 / 账本 / 足迹 / 饮记「明细」行右侧的删除图标：点击弹出确认框，点「取消」数据保留，点「删除」才真正删除并提示「已删除」 |
| 设置定时 | 点任一时长卡片，底部出现 Snackbar，页面上方出现倒计时卡片 |
| 自定义时长 | 点「自定义」卡片，填写时/分/秒后点「开始」，按所填时长倒计时 |
| 取消定时 | 在倒计时卡片点「取消」 |
| 到点提醒 | 可先用「通知设置 → 试听当前提醒」验证铃声+震动；正式到点会循环响铃震动，通知栏点「停止」结束 |
| 震动开关 | 通知设置里开关震动，试听时验证效果 |
| 铃声选择 | 通知设置 → 提示铃声 → 在系统选择器中挑一首，返回后试听 |
| 计时明细 | 跑一次计时（可中途取消）后切到计时页「明细」，能看到按天分组的记录：标题是当天总时长，行内是「起–止时间」与「已完成 / 中断」，右侧显示实际专注时长，可删除 |
| 备忘提醒 | 记事页选「提醒」或「待办」，点「设置提醒时间」选一个几分钟后的时间 → 保存 → 到点应收到通知，通知上带「完成」「稍后 10 分钟」；点「完成」后该条在明细里显示「已完成」且不再提醒 |
| 待办勾选 | 明细页顶部「待办清单」里勾一条，内容出现删除线、计数减一；切到较大区间仍能看到已完成记录 |
| 心情日历 | 记几条「心情」（选 1~5 级）→ 统计页「心情日历」里对应日期按深浅上色，顶部显示「N 天有记录 · 平均 x.x 分」；左右箭头可翻月 |
| 专注记录 | 跑一次 10 分钟计时（可中途取消）→ 计时页「统计」里总时长、完成率、中断次数与趋势柱应出现；取消的只计实际时长。切换「日 / 周 / 月 / 年 / 自定义」，汇总与图表随之变化 |
| 快速记录 | 饮记页「记录」标签点一下「水」，格子里的今日次数 +1、底部提示「已记录 水 · HH:mm」，几秒内点提示里的「撤销」可撤回；长按类别可改名 / 改色 / 删除，右上角「+」可加类别 |
| 饮记明细 | 饮记「明细」标签切换「日 / 周 / 月 / 年 / 自定义」，记录按天分组、标题带当天次数；年视图先按月索引再点进去看当月；「补记一条」可改类别与时间；行右侧删除图标会先弹确认框 |
| 饮记统计 | 饮记「统计」标签查看总次数、各类次数色卡、堆叠柱图与热力图；点类别胶囊可只看某几类 |
| 出行 | 账本里记一笔「酒店」→ 出行页出现「识别到 1 次可能的出行」，点「保存」生成行程，页面显示该区间的花费 / 里程 / 账目数；也可手动「新建出行」选起止日期 |
| 回顾 | 首页点「今天」卡片 → 切「日 / 周 / 月」，四个指标、钱花在哪、待办、饮记都应和明细对得上；顶部一句话总结随区间变化 |
| 重启恢复 | 设一个 10 分钟后的计时与一条备忘提醒，然后 `adb reboot`；开机后计时闹钟与提醒应自动恢复（若计时已过点，会补一条「完成」记录） |
| 通知权限 | 首次启动会申请「通知」权限，务必允许，否则到点可能看不到通知 |
| 精确闹钟 | 若定时页顶部出现黄色提示条，点「去开启」允许精确闹钟，定时更准时 |
| 导出备份 | 数据备份 → 一键导出全部数据 → 在系统选择器里保存，检查「下载」里生成了 `朝夕备份-*.zip` |
| 导入备份 | 先 `adb shell pm clear com.example.xiancli_tools` 清空数据，再导入该文件选「覆盖导入」，六类条数应恢复 |
| 旧备份兼容 | 用 schema 1 的旧备份文件导入，应成功导入且新字段为空（不提醒、未完成、无心情），不报错；老版本导出的裸 `.json` 也应能导入 |
| 导入容错 | 选一个别的 JSON（如其他 App 的导出文件）导入，应提示「导入失败：不是「朝夕」的备份文件」且本地数据不变 |

> App 需要的权限：通知（POST_NOTIFICATIONS）、震动（VIBRATE）、开机自启（RECEIVE_BOOT_COMPLETED，用于重启后恢复闹钟）、精确闹钟（USE_EXACT_ALARM / SCHEDULE_EXACT_ALARM）、前台服务（FOREGROUND_SERVICE + SPECIAL_USE）、定位（ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION，用于足迹记录）。

---

## 八、常见问题

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

**Q：备忘提醒没弹出来？**
先确认三件事：通知权限是否给了（Android 13+ 需手动允许）；是否开着「勿扰模式」；该提醒时间是否已经过去（记事页保存时，过去的时间会被丢弃，不会排成永远不响的闹钟）。

**Q：手机关机重启后，之前设的定时和提醒还在吗？**
在。`BootReceiver` 会在开机和 App 升级后把计时闹钟与备忘提醒全部重挂一遍；如果重启期间计时已经走完，会补记一条「完成」的专注记录，但不会再响铃打扰。

**Q：点错了「喝了一杯」怎么办？**
记录页连点会生成多条记录，多出来的那条可以在「明细」里删掉；刚点完的几秒内也可以直接点底部提示里的「撤销」。

**Q：出行里的花费和账本对不上？**
出行是按日期区间现算的：起止日各含当天，只统计区间内的账目、轨迹和备忘（心情记录不计入备忘条数）。把行程的起止日期调对即可。

**Q：铃声里找不到自己的音乐？**
系统的铃声选择器会列出铃声/通知音，部分设备也支持浏览本地音乐；如未列出，属于 ROM 限制，可先点「试听」验证，或选择系统铃声。

---

## 九、项目结构

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
    │   │   │   ├── SettingsRepository.kt     # 通知偏好 + 进行中的计时状态
    │   │   │   ├── RingtoneResolver.kt       # 铃声 URI / 名称解析
    │   │   │   ├── DateRange.kt              # 报表区间（日/周/月/年/自定义）+ 趋势分桶
    │   │   │   ├── NoteModels.kt             # 备忘类型 / 记录 / 心情等级 / 周期统计
    │   │   │   ├── NoteRepository.kt         # 备忘持久化（JSON）
    │   │   │   ├── LedgerModels.kt           # 记账类型 / 记录 / 周期统计
    │   │   │   ├── LedgerRepository.kt       # 记账记录持久化（JSON）
    │   │   │   ├── TrackModels.kt            # 轨迹 / 交通方式 / 里程统计
    │   │   │   ├── TrackRepository.kt        # 轨迹持久化（JSON）；摘要（不含点阵）与按 id 取点阵分开读
    │   │   │   ├── TimerSessionModels.kt     # 专注记录 / 周期汇总 / 趋势与时段分布
    │   │   │   ├── TimerSessionRepository.kt # 专注记录持久化（JSON）
    │   │   │   ├── DrinkModels.kt           # 饮品类别 / 记录 / 周期统计 / 默认类别
    │   │   │   ├── DrinkRepository.kt       # 饮记持久化（类别、记录两个 JSON）
    │   │   │   ├── TripModels.kt             # 出行 / 区间聚合 / 候选行程识别
    │   │   │   ├── TripRepository.kt         # 出行持久化（JSON）
    │   │   │   ├── ReviewModels.kt           # 回顾：跨工具汇总与一句话总结
    │   │   │   ├── JsonCodec.kt              # 六类数据的统一 JSON 编解码（轨迹坐标存 6 位小数）
    │   │   │   ├── JsonStream.kt             # 流式 JSON 读取：逐条取原文，不建整棵树
    │   │   │   ├── BackupCodec.kt            # 备份文件格式（schema 校验）与合并规则
    │   │   │   ├── BackupContainer.kt        # 备份容器：zip 打包 / 裸 JSON 兼容，读写都流式
    │   │   │   └── BackupRepository.kt       # 备份导出/导入（SAF Uri 读写）
    │   │   ├── timer/
    │   │   │   ├── NotificationHelper.kt   # 通知渠道（定时 / 备忘）与通知构建
    │   │   │   ├── TimerScheduler.kt       # AlarmManager 精确定时调度
    │   │   │   ├── TimerSessionRecorder.kt # 计时的开始与结算（落一条专注记录）
    │   │   │   ├── TimerAlarmReceiver.kt   # 定时到点广播接收
    │   │   │   └── AlarmRingingService.kt  # 前台服务：响铃 + 震动
    │   │   ├── reminder/
    │   │   │   ├── ReminderScheduler.kt    # 备忘单次提醒
    │   │   │   ├── ReminderReceiver.kt     # 到点发通知，处理「完成 / 稍后」
    │   │   │   ├── ReminderRestorer.kt     # 开机后重挂计时与所有提醒
    │   │   │   └── BootReceiver.kt         # 开机 / 升级广播接收
    │   │   └── ui/
    │   │       ├── AppRoot.kt              # 页面导航 + 通知权限申请
    │   │       ├── HomeScreen.kt           # 首页：品牌字标 + 今日概览 + 工具卡
    │   │       ├── TimerScreen.kt          # 计时页（计时 / 明细 / 统计，含自定义时长弹窗）
    │   │       ├── NoteScreen.kt           # 备忘页（记事 / 明细 / 统计）
    │   │       ├── LedgerScreen.kt         # 账本页（快速记账 + 明细 + 周期图表报告）
    │   │       ├── TrackScreen.kt          # 足迹页（记录 / 明细 / 统计）
    │   │       ├── DrinkScreen.kt          # 饮记页（记录网格 / 明细 / 统计，含类别编辑与补记）
    │   │       ├── TripScreen.kt           # 出行页（行程聚合 + 候选识别）
    │   │       ├── ReviewScreen.kt         # 回顾页（日 / 周 / 月跨工具汇总）
    │   │       ├── LedgerStyle.kt          # 支出类型图标与配色
    │   │       ├── NoteStyle.kt            # 备忘类型图标与配色
    │   │       ├── BackupScreen.kt         # 数据备份页（导出/导入 + 合并/覆盖确认）
    │   │       ├── SettingsScreen.kt       # 通知设置页
    │   │       ├── components/
    │   │       │   ├── IconBadge.kt        # 圆形图标徽章
    │   │       │   ├── SegmentedTabs.kt    # 分段标签（工具页「记事/明细/统计」与报表周期共用）
    │   │       │   ├── Common.kt           # 白卡 / 指标块 / 报表空态
    │   │       │   ├── PeriodPicker.kt     # 自定义起止日期选择
    │   │       │   ├── MoodCalendar.kt     # 心情日历热力图
    │   │       │   ├── DrinkCharts.kt      # 饮记：类别次数卡 / 堆叠柱图 / 周期热力图
    │   │       │   └── Charts.kt           # 环形图 / 柱状图 / 堆叠条形图（Canvas 手绘）
    │   │       └── theme/                  # Compose 主题（Palette.kt 暖色盘 + Theme.kt 亮暗配色）
    │   ├── keepRules/                # R8 keep 规则
    │   ├── res/drawable/             # 矢量图标（工具图标 + 品牌标记 ic_brand_mark）
    │   └── res/mipmap-*/             # 启动图标「晨昏圆」（自适应 + 主题图标 + 旧版位图）
    ├── test/                         # 单元测试（含 Drink / Timer / Trip / Review 分析）
    └── androidTest/                  # 仪器测试
```

---

## 十、技术栈

- Kotlin + Jetpack Compose（Material 3）
- `AlarmManager` 精确定时 + `BroadcastReceiver`（计时与备忘提醒共用同一套调度）
- 开机广播（`BOOT_COMPLETED`）恢复所有闹钟
- 前台服务（`specialUse` 类型）播放铃声与震动
- 带动作按钮的通知（完成 / 稍后 10 分钟）
- `SharedPreferences` 存储设置与进行中的计时状态
- 所有数据本地 JSON 存储，无网络、无账号
- Gradle Kotlin DSL + Version Catalog（`gradle/libs.versions.toml`）
