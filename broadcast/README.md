# Lengbanlist-Ext-Broadcast

封禁人数广播：定时循环、`/lban a` 手动触发、Web 面板触发。

## 安装

1. 把 `Lengbanlist-Ext-Broadcast-<版本>.jar` 放进服务端的 `plugins/`（与 Lengbanlist 同级）；
2. 重启服务端；
3. 配置在 `plugins/Lengbanlist-Ext-Broadcast/config.yml`。

需要核心契约 **2.3+**（`BroadcastService`、`Scheduler#runSync(Entity, ...)` 与
`Services#audit()` 是这一版新增的能力）。

## 命令与权限

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/lban a` | `lengbanlist.broadcast` | 立即广播一次 |
| `/lban toggle` | `lengbanlist.toggle` | 开关自动广播 |

Web 面板的「广播封禁人数」按钮在扩展安装后继续可用；扩展没装时接口返回
`503 广播扩展未安装`。

## 配置

```yaml
enabled: true              # 总开关
auto-broadcast: true       # 是否循环广播（旧核心的 opensendtime）
interval-minutes: 5        # 间隔分钟，最少 1
mode: random               # random / orderly
messages: [...]            # 消息列表，可用 %s / %i / %t
default-message: "..."     # messages 为空时使用
```

占位符：`%s` 封禁玩家数、`%i` 封禁 IP 数、`%t` 总数。

旧核心 `config.yml` 的 `features.broadcast` 会自动迁移到本配置；
旧的 `plugins/Lengbanlist/broadcast.yml` 会在第一次启用时导入一次，
之后以本扩展的配置为准（用 `migrated-from-core` 标记保证不重复覆盖）。

## 行为说明

- 广播任务每秒检查一次是否到点，间隔与消息都从配置现读——
  直接改文件即可生效，不需要重启；
- 在线玩家为空时跳过，不刷屏；
- 消息带可点击的「查看封禁列表」按钮，与搬迁前一致。

## 卸载

删掉 jar 即可。核心的箱子菜单与 Web 面板会自动隐藏/拒绝对应入口。
