# staffchat — 管理员频道

```
/sc <内容>
```

| 项 | 值 |
| --- | --- |
| 扩展名 / 功能键 | `staffchat` |
| 命令 | `/sc <内容>`（顶层命令） |
| 权限 | `lengbanlist.staffchat` |
| 开关 | 本目录 `config.yml` 的 `enabled` |

把消息发给所有拥有 `lengbanlist.staffchat` 权限的在线玩家，并在控制台留一份记录。
发送者名在控制台执行时显示为 `§cConsole`。

目录名是 `staffchat`、命令名是 `sc`：**扩展名与命令名本来就可以不同**。权限节点沿用了
搬迁前的 `lengbanlist.staffchat`，老用户在 LuckPerms 里配好的权限一个字都不用改。
