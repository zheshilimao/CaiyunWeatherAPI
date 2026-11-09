# 彩云天气 · Android（CaiyunWeatherAPI）

> **2.3.3**（versionCode `233`）· Min SDK 23 · Target SDK 36 · WebView + JS 桥接

基于彩云天气 v2.6 API 的 Android 天气应用。原生端只负责定位、配置与网络请求，界面和天气动效全部由 `app/src/main/assets/weather.html` 渲染，因此可以像网页一样快速迭代。

---

## 演示

| 方式 | 地址 |
|---|---|
| 在线（GitHub Pages） | `https://zheshilimao.github.io/CaiyunWeatherAPI/` |
| 直接链接 | `https://zheshilimao.github.io/CaiyunWeatherAPI/demo.html` |
| 本地 | 双击 `docs/demo.html`（单文件、内置数据、无需联网） |

演示页直接复用工程里真实的页面与云层渲染代码，只把原生桥接替换为内置数据，包含 4 个场景：

| 场景 | 时间 | 天气 |
|---|---|---|
| 早 · 小雨 | 08:00 | 小雨 |
| 午 · 阴 | 12:00 | 阴 |
| 后 · 多云 | 16:00 | 多云 |
| 晚 · 晴 | 21:00 | 晴 |

支持手动切换、6 秒自动轮播、键盘 ← →，以及实时调整云密度 / 云速 / 透明度。

---

## 功能

### 天气页面

- 大字实况温度、天气现象、今日最高 / 最低
- 实时指标：体感、风向 / 风力、湿度、AQI、日出日落、能见度与总云量
- **逐时预报**：气温曲线 + 湿度 + 降水柱（Chart.js，未来 48 小时）
- **未来 48 小时天气**：三天高低温区间条，今天带实时温度标记
- **降雨提示**：未来 24 / 48 小时降雨时段、附近是否正在下雨、是否建议带伞
- 紫外线徽标（优先实时，其次当日预报）
- 下拉刷新；自动定位或手动经纬度

### 天气动效

- 12 套天气主题（晴 / 晴夜 / 多云 / 阴 / 雾 / 小雨 / 中雨 / 大雨 / 雷暴 / 雪 / 霾 / 浮尘），背景与面板配色同步切换
- **云层**：离屏 canvas sprite 缓存，每朵云只渲染一次；16 点噪声轮廓 + 贝塞尔平滑，保留撕裂毛边与平底融化感。
- **云量按天气分级**：晴 < 多云 < 阴 < 小雨 < 中雨 < 大雨 < 雷暴；密度滑杆为超线性放大（1.3 次方），拖动时变化直观
- 云朵在屏幕内均匀铺开，密度调高时屏内数量同步增加，不会"只在屏幕外排队"
- **时段驱动暖色**：黎明 / 黄昏偏金，正午偏白，夜间转冷蓝；顶部纤维撕裂与高光强度随时段变化
- **夜间星空分级**：晴 > 多云 > 阴，星星绘制在云层之后，从云缝中透出
- 雨幕、雪花、雾 / 尘 / 浮光粒子、日月光晕

### 显示设置面板

右上角齿轮：

- **未配置 API Key** → 直接进入原生设置
- **已配置** → 打开显示面板（与页面同屏，无需下滑）

面板分 4 区、共 16 项：

| 分区 | 项目 |
|---|---|
| 1. 面板与整体 | 面板不透明度、整体效果强度 |
| 2. 云层 | 显示云层、密度、速度、不透明度 |
| 3. 天光与环境 | 日 / 月光晕、光晕强度、环境粒子、环境密度 |
| 4. 降水动态 | 显示雨幕、雨密度、雨速度、显示雪花、雪密度、雪速度 |

布局：手机 2 列、窄屏 1 列、≥900px 4 列；底部固定「⚙ 设置」「恢复默认」。偏好保存在 `localStorage`（`wx_fx_prefs_v2`）。

---

## 工程结构

```
CaiyunWeatherAPI/
├── build.gradle / settings.gradle / gradle.properties     # Gradle 9.6.0 / AGP 9.4.0
├── gradlew / gradlew.bat / gradle/wrapper/*               # Gradle Wrapper
├── docs/demo.html                                         # 完整页面演示（GitHub Pages）
├── index.html                                             # 早期静态示意页
└── app/
    ├── build.gradle
    ├── proguard-rules.pro                                 # R8 混淆规则
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/
        │   ├── weather.html                               # 天气页面（WebView 渲染）
        │   └── chart.umd.min.js                           # 内置 Chart.js
        ├── java/com/api/caiyunweather/
        │   ├── AppConfig.java        # 配置存储（API Key / 位置）
        │   ├── ConfigBridge.java     # H5 读取配置 / 打开设置 的 JS 桥
        │   ├── MainActivity.java     # 定位 + 下拉刷新 + 生命周期
        │   ├── WeatherApiBridge.java # 彩云接口请求，注入为 window.NativeWeather
        │   └── SettingsActivity.java # API Key / 定位设置页
        └── res/                     # 主题、文案、启动图标
```

---

## 接口

彩云 v2.6 官方格式：

```
GET https://api.caiyunapp.com/v2.6/{API_KEY}/{lon},{lat}/weather?dailysteps=3&hourlysteps=48&lang=zh_CN
```

- API 地址固定，不是设置项；
- **API Key 不内置**（`AppConfig.DEFAULT_API_KEY = ""`），首次使用请在 App「设置」中填写（彩云官方控制台获取）；
- 结果通过 `window.onNativeWeatherResult` 回调给 H5；失败走 `window.onNativeWeatherError`。

### 首次运行

1. 页面提示「尚未填写 API Key」；
2. 点击右上角齿轮 → **直接进入原生设置**；
3. 填写 API Key 与定位方式后保存，返回即可加载天气；
4. 之后再次点击齿轮，才会打开「显示设置」面板。

---

## 构建运行

工程自带 Gradle Wrapper，无需本机安装 Gradle：

```bash
cd CaiyunWeatherAPI
# Windows
gradlew.bat assembleDebug
# macOS / Linux
./gradlew assembleDebug

# Release 包（R8 压缩 + 资源收缩）
gradlew.bat assembleRelease
```

或用 Android Studio 打开工程直接 Run（首次打开会自动下载 Gradle 9.6.0）。

### 构建环境要求

| 项 | 版本 |
|---|---|
| Gradle | 9.6.0（由 `gradle/wrapper/gradle-wrapper.properties` 指定） |
| Android Gradle Plugin | 9.4.0 |
| JDK | 17（`compileOptions` 使用 Java 17 语法） |
| Compile SDK / Target SDK | 36 |
| Min SDK | 23（Android 6.0） |

---

## 性能与离线优化

- **Chart.js 已内置**：`app/src/main/assets/chart.umd.min.js`，不再从 CDN 加载，断网可用、无额外网络与缓存占用；
- **天气数据缓存 30 分钟**：位置未变且缓存未过期时直接复用本地缓存，不重复请求；下拉刷新强制联网；
- **云朵离屏缓存**：每朵云只绘制一次 sprite，之后仅做位置变换；
- **渲染省电**：动画上限 30fps、像素比上限 1.5、光晕离屏缓存、打开显示面板时暂停渲染、切后台自动停止；
- **无后台自动刷新**：仅下拉手动刷新，避免无感消耗 API 调用量。

---

## 注意事项

- 自动定位关闭时必须填写手动经纬度，否则无法请求天气；
- 首次安装（或清除数据后）会请求一次完整数据，之后按缓存策略复用；
- 修改显示设置后存储占用变小，是 WebView 缓存压缩，不是数据丢失；
- **API Key 相当于密码**：请勿提交到公开仓库，也不要截图外泄；
- 本工程不包含正式上架签名与隐私合规文件，公开发布前请自行补充。
