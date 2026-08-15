# 移动端移植方案

## 目标

- 保留 Electron 桌面端现有功能。
- 新增 iOS 和 Android 移动端构建目标。
- iOS 使用原生音频播放，暂不实现 PiP 歌词。
- Android 使用原生音频播放、后台播放和系统悬浮歌词。
- Vue 层继续负责页面、歌词解析、动画和播放状态展示。

## 技术边界

```text
Vue 3 / Pinia / Naive UI
        |
        +-- Electron adapter
        +-- Capacitor adapter
              +-- iOS native audio
              +-- Android native audio
              +-- Android overlay lyric
```

## 当前实现状态

- Capacitor Android/iOS 工程和 GitHub Actions 原生构建已接入。
- Android 使用 `MediaPlayer`、前台媒体服务、`MediaSession` 和系统悬浮歌词。
- iOS 使用 `AVPlayer`、后台音频、锁屏媒体控制和 `MPNowPlayingInfoCenter`。
- iOS PiP 歌词明确不实现；页面内歌词和流体动画继续由 Vue 渲染。

## 第一阶段范围

1. 抽象播放器平台接口，隔离 Electron IPC 和 Web Audio 实现。
2. 建立 Capacitor 构建入口和移动端环境判断。
3. 定义原生音频插件接口：播放、暂停、Seek、音量、队列、媒体事件。
4. 定义后台播放和锁屏媒体控制接口。
5. 定义 Android 悬浮歌词接口和权限状态接口。
6. iOS 只接入原生音频接口，不包含 PiP。

## 后续增强

- 原生 Crossfade、EQ 和 Automix。
- Android 悬浮歌词样式、拖拽和更多交互。
- 本地文件选择、下载和移动端缓存。
- iOS 文件访问和本地音乐管理。
- 真机验证、应用签名和商店构建。

## 暂不迁移

- Electron 窗口管理、托盘、任务栏歌词、全局快捷键。
- MPV 播放引擎。
- Electron 自动更新。
- iOS PiP 歌词。

## 验收标准

- Web/Electron 现有播放行为不回归。
- Capacitor Web 构建不依赖 Node/Electron API。
- iOS 前台原生音频可播放、暂停、Seek 和切歌。
- Android 前台原生音频可播放，并能进入后台继续播放。
- Android 悬浮歌词权限被拒绝时，应用仍可正常播放。
- lint、类型检查和构建命令通过；无法在当前环境完成的真机验证必须单独记录。
