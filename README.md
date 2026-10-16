# Lengbanlist-Extensions

Lengbanlist 的官方 **Lua 扩展**仓库。每个 `<扩展名>.lua` 就是一个可以直接丢进服务端的扩展，
同名子目录里是它自己的配置。

## 怎么装

把想要的扩展的 `.lua` 脚本复制到：

```
plugins/Lengbanlist/extensions/<扩展名>.lua
```

然后 `/lban ext reload`（或重启服务端）。删掉脚本就是卸载。

```
plugins/Lengbanlist/extensions/
├── tp.lua             ← 本仓库的 tp.lua
├── staffchat.lua      ← 本仓库的 staffchat.lua
├── tp/
│   └── config.yml     ← 本仓库的 tp/config.yml
└── staffchat/
    └── config.yml
```

配置目录可以不复制——加载时会自动生成一份只含 `enabled` 的。但脚本读的键（各扩展的
README 里列了）得自己补进 `config.yml`，插件猜不出来。

扩展的开关是 `extensions/<扩展名>/config.yml` 里的 `enabled`，**核心的 config.yml 里没有对应的
键**——一个功能只有一个开关来源，别去核心配置里找。

## 管理命令

| 命令 | 作用 |
| --- | --- |
| `/lban ext list` | 装了哪些、开着没有、哪个加载失败了 |
| `/lban ext reload` | 改完脚本重跑一遍 |
| `/lban ext enable <扩展名>` | 打开（写扩展自己的 config.yml） |
| `/lban ext disable <扩展名>` | 关掉 |

权限：`lengbanlist.reload`。

## 官方扩展

| 扩展 | 命令 | 权限 | 说明 |
| --- | --- | --- | --- |
| `tp` | `/lban tp <玩家名>` | `lengbanlist.admin` | 传送到玩家身边 |
| `staffchat` | `/sc <内容>` | `lengbanlist.staffchat` | 管理员频道 |

这两个功能原先写在核心 jar 里，现在搬成了 Lua 扩展：**命令、权限、补全、`/lban help` 里的位置
都和以前一模一样**，老用户的权限配置与肌肉记忆都不用改。

> 核心 jar 里也打包了这两个扩展，只在 `plugins/Lengbanlist/extensions/` 目录**整个不存在**时
> 释放一次（升级后老功能不会凭空消失）。这个仓库里的副本与 jar 里的一致，
> 想改就改这里的那份、或者复制一份改名当作新扩展。

## 自己写一个

```lua
leng.name("我的扩展")
leng.version("1.0.0")
leng.api("1.0")                     -- 需要的脚本 API 版本，核心不满足就不加载

leng.command{
    name = "hello",                 -- 命令名
    parent = "lban",                -- 省略 = 顶层命令；写 lban = /lban hello
    permission = "lengbanlist.admin",
    usage = "/lban hello <名字>",
    description = "打个招呼",

    run = function(ctx)
        ctx.reply("§a你好，" .. (ctx.args[1] or "陌生人"))
        return true
    end,

    complete = function(ctx)
        return leng.online_players(ctx.args[#ctx.args] or "")
    end,
}
```

事件订阅（只支持同步事件）：

```lua
leng.on("player_join", function(ctx)
    ctx.player:send("§e欢迎回来，" .. ctx.player.name)
end)
```

完整的脚本 API（`leng` 表上的每个函数、`ctx` 的字段、事件列表与沙箱边界）见核心仓库的
`docs/LuaExtensions.md`。

## 注意

- 脚本跑在**主线程**上。单次调用超过 5 秒或指令预算耗尽（死循环）会被强制停用并写日志，
  所以循环一定要留出口。
- 沙箱里没有文件系统、没有网络、没有 Java 反射、没有协程；处罚写操作也不对脚本开放——
  处罚闭环留在核心，脚本最多是"自己崩"。
- Lua 版本是 **5.2**（LuaJ）：`table.unpack`、`goto` 都有，但没有整除 `//` 和位运算符。
