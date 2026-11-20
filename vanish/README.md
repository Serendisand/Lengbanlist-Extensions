# Lengbanlist-Ext-Vanish

管理员隐身：对其他玩家完全不可见，连进服/退服提示一起隐藏。

## 安装

1. 把 `Lengbanlist-Ext-Vanish-<版本>.jar` 放进服务端的 `plugins/`（与 Lengbanlist 同级）；
2. 重启服务端；
3. 配置在 `plugins/Lengbanlist-Ext-Vanish/config.yml`。

需要核心契约 **2.3+**（`VanishService`、`Scheduler#runSync(Entity, ...)` 与
`Services#audit()` 是这一版新增的能力）。

## 命令与权限

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/lban vanish` | `lengbanlist.vanish` | 切换自己的隐身状态 |
| — | `lengbanlist.vanish.see` | 能看见隐身玩家 |

命令、权限与搬迁前完全一致，老用户的 LuckPerms 配置不用改。

## 配置

```yaml
enabled: true              # 总开关
hide-join-message: true    # 隐身时隐藏自己的进服提示
hide-quit-message: true    # 隐身时隐藏自己的退服提示
```

旧核心 `config.yml` 里的 `features.vanish` 会在第一次启动时自动迁移到本配置，
之后以本配置为准。

## 行为说明

- 隐身状态是**内存态**：重启或重载后不保留，与搬迁前一致；
- 核心的 `/lban check`、举报目标列表与箱子菜单会通过 `VanishService` 读取隐身状态，
  扩展没装时它们按"没人隐身"处理；
- 用 `/lban ext disable` 关掉本扩展时，一秒一次的看门狗会把残留的隐身状态还原，
  不会让玩家一直看不见自己。

## 卸载

删掉 jar 即可。核心的查询、举报与 GUI 会自动隐藏相关入口，不会报错。
