package org.leng.ext.punishpolicy;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.leng.api.DurationParser;
import org.leng.api.DurationPolicyHook;
import org.leng.api.ExtensionContext;
import org.leng.api.LengbanlistExtensionPlugin;
import org.leng.api.PunishmentDecisionHook;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * 处罚判定策略：权重免疫 + 自动时长升级。
 *
 * <p>只注册两个钩子——不注册命令、不建表、不写核心数据。钩子缺席时核心会回落到
 * 内置行为（免疫 = 一律放行，升级 = 按未撤销警告数），所以卸载本扩展不会让服务器
 * 失去处罚能力，只是回到"没有免疫、没有累犯加重"。
 *
 * <p>开关在这份插件自己的 {@code config.yml} 里：{@code features.immunity} 与
 * {@code features.escalation}，核心配置里没有对应的键。
 */
public final class PunishPolicyExtension extends LengbanlistExtensionPlugin {

    @Override
    public String id() {
        return "punishpolicy";
    }

    @Override
    public Set<String> features() {
        return Set.of("immunity", "escalation");
    }

    @Override
    protected void onExtensionEnable(ExtensionContext context) {
        context.hooks().register(PunishmentDecisionHook.class, new Immunity(context));
        context.hooks().register(DurationPolicyHook.class, new Escalation(context));
    }

    // ------------------------------------------------------------ 权重免疫

    /**
     * 只回答"权重是多少"。"谁能罚谁"的判断与"策略缺席一律放行"的兜底都在核心的
     * {@code PunishmentGate}，本类不重复实现。
     */
    private static final class Immunity implements PunishmentDecisionHook {

        private final ExtensionContext context;
        private Boolean luckPermsPresent;

        private Immunity(ExtensionContext context) {
            this.context = context;
        }

        @Override
        public int operatorWeight(CommandSender staff) {
            if (!(staff instanceof Player player)) {
                return Integer.MAX_VALUE;
            }
            if (player.isOp()) {
                return Integer.MAX_VALUE;
            }
            return resolveWeight(player);
        }

        @Override
        public int webOperatorWeight() {
            return config().getInt("web-operator-weight", Integer.MAX_VALUE);
        }

        @Override
        public int playerWeight(String targetName) {
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                // 离线玩家不享受免疫
                return Integer.MIN_VALUE;
            }
            return resolveWeight(target);
        }

        @Override
        public int ipWeight(String ip) {
            int highest = Integer.MIN_VALUE;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getAddress() == null || online.getAddress().getAddress() == null) {
                    continue;
                }
                String playerIp = online.getAddress().getAddress().getHostAddress();
                if (playerIp == null || !playerIp.equals(ip)) {
                    continue;
                }
                int weight = resolveWeight(online);
                if (weight > highest) {
                    highest = weight;
                }
            }
            return highest;
        }

        private int resolveWeight(Player player) {
            if (isLuckPermsPresent()) {
                int luckPermsWeight = getLuckPermsWeight(player);
                if (luckPermsWeight != -1) {
                    return luckPermsWeight;
                }
            }
            for (int i = 99; i >= 1; i--) {
                if (player.hasPermission("lengbanlist.weight." + i)) {
                    return i;
                }
            }
            return config().getInt("default-weight", 0);
        }

        private boolean isLuckPermsPresent() {
            if (luckPermsPresent == null) {
                luckPermsPresent = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
            }
            return luckPermsPresent;
        }

        /**
         * 用反射读 LuckPerms，避免把 LuckPerms 变成编译期依赖。
         * 任何一步拿不到（版本变化、API 调整）都返回 -1，回落到权限节点与默认权重。
         */
        private int getLuckPermsWeight(Player player) {
            try {
                Object api = Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
                Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
                Object user = userManager.getClass().getMethod("getUser", UUID.class)
                        .invoke(userManager, player.getUniqueId());
                if (user == null) {
                    return -1;
                }
                Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
                if (cachedData == null) {
                    return -1;
                }
                Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
                if (metaData == null) {
                    return -1;
                }
                Object primaryGroup = metaData.getClass().getMethod("getPrimaryGroup").invoke(metaData);
                if (primaryGroup == null) {
                    return -1;
                }
                Object groupManager = api.getClass().getMethod("getGroupManager").invoke(api);
                Object group = groupManager.getClass().getMethod("getGroup", String.class)
                        .invoke(groupManager, primaryGroup);
                if (group == null) {
                    return -1;
                }
                Object weight = group.getClass().getMethod("getWeight").invoke(group);
                if (weight instanceof OptionalInt optionalWeight && optionalWeight.isPresent()) {
                    return optionalWeight.getAsInt();
                }
                return -1;
            } catch (Exception e) {
                return -1;
            }
        }

        private FileConfiguration config() {
            return context.config().raw();
        }
    }

    // ------------------------------------------------------------ 累犯升级

    /** 只回答"第 N 次该判多久"。历史次数由核心的 {@code BanService} 提供。 */
    private static final class Escalation implements DurationPolicyHook {

        private final ExtensionContext context;

        private Escalation(ExtensionContext context) {
            this.context = context;
        }

        @Override
        public Decision ban(String target) {
            int count = context.services().bans().countBanHistory(target);
            return new Decision(tierDuration(count), count);
        }

        @Override
        public Decision ipBan(String ip) {
            int count = context.services().bans().countIpBanHistory(ip);
            return new Decision(tierDuration(count), count);
        }

        @Override
        public Decision mute(String target) {
            // 与核心旧行为一致：禁言时长恒按警告数，不走累犯分级
            return Decision.of(legacyWarnBased(target));
        }

        private long tierDuration(int count) {
            List<String> tiers = config().getStringList("tiers");
            if (tiers.isEmpty()) {
                return DurationParser.daysToMillis(7);
            }
            int index = Math.max(0, Math.min(count, tiers.size() - 1));
            long duration = DurationParser.parseMillis(tiers.get(index));
            return duration > 0 ? duration : DurationParser.daysToMillis(7);
        }

        /** 0 次 1 天 / 1 次 3 天 / 2 次 7 天 / 3 次 14 天 / 4 次 30 天 / 5 次及以上永久。 */
        private long legacyWarnBased(String target) {
            int warnCount = Math.max(0, context.services().warnings().countActiveWarnings(target));
            long duration = switch (warnCount) {
                case 0 -> DurationParser.daysToMillis(1);
                case 1 -> DurationParser.daysToMillis(3);
                case 2 -> DurationParser.daysToMillis(7);
                case 3 -> DurationParser.daysToMillis(14);
                case 4 -> DurationParser.daysToMillis(30);
                default -> Long.MAX_VALUE;
            };
            return Math.max(duration, DurationParser.daysToMillis(1));
        }

        private FileConfiguration config() {
            return context.config().raw();
        }
    }
}
