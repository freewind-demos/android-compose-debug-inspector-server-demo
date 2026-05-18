# android-compose-debug-inspector-server-demo

这个 Demo 演示一件事：在 Android Compose 应用里内嵌一个本地 HTTP server，把当前页面已注册组件的结构化信息导出给外界，同时接受外部动作命令回放到正在运行的 UI。

它不是系统级通用抓取器。这个版本走的是更稳的最小方案：页面里的关键组件主动注册自己的 `id`、文本、颜色、位置、大小、状态、动作能力，再由 app 内 server 暴露出去。

## 快速开始

### 环境要求

- JDK 17
- Android SDK
- `adb`

### 运行

```bash
cd /Users/peng.li/workspace/freewind-demos/android-compose-debug-inspector-server-demo

# 首次补 wrapper jar
gradle wrapper

# 编译 apk
./android-compile-build.fish

# 编译 + 启动模拟器/真机
./android-emulator.fish
```

app 启动后，执行：

```bash
adb forward tcp:8765 tcp:8765
curl http://127.0.0.1:8765/snapshot
```

也可直接浏览器打开：

```bash
open http://127.0.0.1:8765/
```

## 注意事项

- 当前 server 默认监听 `127.0.0.1:8765`
- 推荐仅用于 debug 环境
- 这个 Demo 只导出“已注册组件”，不是自动穷举 Compose 全树
- `swipe` 在 Demo 里简化成“按 delta 调整 slider 值”，用于演示远程动作协议

## 教程

1. 关键概念

你要的能力其实分两层：

- 看：导出当前 UI 节点快照
- 控：接收动作命令并落到页面组件

这版 Demo 两层都在 app 内完成，便于你先把链路跑通。

2. Demo 原理

项目按以下分层组织：

- `domain/store`
  - `DebugInspectorStore`
  - 只放当前快照、动作日志
- `domain/handler`
  - `DebugInspectorHandler`
  - 编排“发布快照”“执行动作”
- `infra/system`
  - `DebugInspectorActionBus`
  - 收口组件动作注册与分发
- `infra/persistence`
  - `DebugInspectorHttpApi`
  - 起本地 HTTP server，暴露 `/snapshot` `/logs` `/action`
- `Entry`
  - `MainActivity`
  - 启动 server，渲染 Compose 页面

3. 关键代码解读

`MainActivity.kt` 里有一个 `DebugNodeRegistry`，页面组件通过 `rememberInspectableModifier(...)` 主动上报：

- `id`
- `type`
- `text`
- `role`
- `backgroundColor`
- `contentColor`
- `bounds`
- `value`
- `extra`

这样 `handler.publishSnapshot(...)` 就能持续拿到一份最新页面快照。

每个可操作组件同时向 `DebugInspectorActionBus` 注册动作处理器。比如：

- `primary_button` 支持 `click`
- `counter_card` 支持 `click` / `doubleClick`
- `volume_slider` 支持 `swipe` / `setValue`
- `input_field` 支持 `input`
- `toggle_switch` 支持 `click` / `toggle`

`DebugInspectorHttpApi` 用最小 HTTP 实现直接处理：

- `GET /`
- `GET /snapshot`
- `GET /logs`
- `POST /action`

## 示例

读取快照：

```bash
curl http://127.0.0.1:8765/snapshot
```

点击按钮：

```bash
curl -X POST http://127.0.0.1:8765/action \
  -H 'Content-Type: application/json' \
  -d '{"action":"click","targetId":"primary_button"}'
```

双击计数卡片：

```bash
curl -X POST http://127.0.0.1:8765/action \
  -H 'Content-Type: application/json' \
  -d '{"action":"doubleClick","targetId":"counter_card"}'
```

输入文本：

```bash
curl -X POST http://127.0.0.1:8765/action \
  -H 'Content-Type: application/json' \
  -d '{"action":"input","targetId":"input_field","text":"hello ai"}'
```

滑动 slider：

```bash
curl -X POST http://127.0.0.1:8765/action \
  -H 'Content-Type: application/json' \
  -d '{"action":"swipe","targetId":"volume_slider","dx":0.25,"dy":0}'
```

## 后续可扩展

- 接入 View 树自动遍历
- 接入 Compose `Semantics`
- 导出截图
- WebSocket 推送实时快照
- 动作回执带截图与前后 diff
