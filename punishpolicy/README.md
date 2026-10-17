# Lengbanlist-Ext-PunishPolicy

权重免疫 + 累犯升级。以两个钩子插进核心的处罚判定，不注册命令、不建表、不碰核心数据。

## 安装

1. 把 `Lengbanlist-Ext-PunishPolicy-<版本>.jar` 放进服务端的 `plugins/`（与 Lengbanlist 同级）；
2. 重启服务端；
3. 配置在 `plugins/Lengbanlist-Ext-PunishPolicy/config.yml`。

需要核心 **2.1.6+**：用到的 `ExtensionContext#isFeatureActive`、`BanService#countBanHistory`
与 `DurationParser` 都是这一版契约里的能力。

## 开关

```yaml
enabled: true          # 总开关
# 只关掉其中一个功能时：
# features:
#   immunity: false
#   escalation: false
```

两个功能可以分开关。核心的 `config.yml` 里没有对应的键——一个功能只有一个开关来源。

## 权重免疫

目标玩家权重高于操作者时，该操作者的封禁 / 踢出会被拒绝。权重按这个顺序取：

1. LuckPerms 的主组权重（反射读取，没装 LuckPerms 就跳过）；
2. `lengbanlist.weight.N` 权限，N 取 1–99 里最大的；
3. `default-weight`（默认 0）。

控制台与 OP 恒为最高权重。Web 面板操作者看 `web-operator-weight`。

## 累犯升级

`/ban <玩家> auto <理由>` 时，按该目标的历史封禁次数取 `tiers` 里对应的一档：

| 历史封禁次数 | 默认时长 |
| --- | --- |
| 0 | 7 天 |
| 1 | 30 天 |
| 2 | 90 天 |
| 3 | 180 天 |
| 4 次及以上 | 365 天 |

IP 封禁同理。**禁言是刻意的例外**：恒按未撤销警告数计算（0→1 天、1→3 天、2→7 天、
3→14 天、4→30 天、5 次及以上永久），与核心旧行为一致。

## 从核心内置版迁移

- 旧核心 `config.yml` 里的 `features.immunity` / `features.escalation` 会在扩展第一次启动时
  自动迁移到本配置，核心那个键之后就可以删了；
- 行为参数需要手动搬：`immunity.default-weight` → `default-weight`，
  `escalation.tiers` → `tiers`，`web.operator-weight` → `web-operator-weight`。

## 卸载

删掉 jar 即可。核心会回落到内置行为：没有免疫（谁都能罚），`auto` 时长按警告数计算。
不会让服务器失去处罚能力，也不会留下半截状态。
