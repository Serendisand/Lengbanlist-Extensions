package org.leng.ext.broadcast;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.leng.api.BroadcastService;
import org.leng.api.Cancellable;
import org.leng.api.CommandSpec;
import org.leng.api.ExtensionConfig;
import org.leng.api.ExtensionContext;
import org.leng.api.LengbanlistExtensionPlugin;
import org.leng.models.Model;

import java.io.File;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 封禁人数广播。
 *
 * <p>定时广播与 {@code /lban a}、{@code /lban toggle} 都搬到了这里；核心只通过
 * {@link BroadcastService} 触发广播与读取开关，箱子菜单与 Web 面板在扩展缺席时
 * 直接隐藏/拒绝对应入口。
 *
 * <p>旧核心的 {@code plugins/Lengbanlist/broadcast.yml} 会在第一次启用时导入一次，
 * 导入后写一个 {@code migrated-from-core} 标记——之后管理员改扩展自己的配置，
 * 不会被再次覆盖。
 */
public final class BroadcastExtension extends LengbanlistExtensionPlugin {

    private static final String HOVER_TITLE = "§a绳§b之§c于§d法§e！";
    private static final String BUTTON_TEXT = "§f【§b点§c击§d查§e看§f】";
    private static final String BUTTON_HOVER = "§a看看封禁列表§bawa";

    private final AtomicInteger orderlyCursor = new AtomicInteger();

    private ExtensionContext context;
    private Cancellable task;
    private long nextBroadcastAt;
    private long configLastModified;
    private boolean warnedNoTemplate;
    private boolean warnedUnknownMode;

    @Override
    public String id() {
        return "broadcast";
    }

    @Override
    public String requiredApi() {
        return "[2.3,3.0)";
    }

    @Override
    protected void onExtensionEnable(ExtensionContext context) {
        this.context = context;
        migrateLegacyConfig(context);
        context.commands().register(CommandSpec.sub(
                "a", "lban", "broadcast", "lengbanlist.broadcast",
                "/lban a", "广播封禁人数", (sender, command, label, args) -> {
                    broadcast(sender);
                    return true;
                }));
        context.commands().register(CommandSpec.sub(
                "toggle", "lban", "broadcast", "lengbanlist.toggle",
                "/lban toggle", "开关自动广播", (sender, command, label, args) -> toggle(sender)));
        context.hooks().register(BroadcastService.class, new Service());
        configLastModified = context.config().file().lastModified();
        nextBroadcastAt = System.currentTimeMillis() + 10_000L;
        task = context.scheduler().runSyncRepeating(this::tick, 20L, 20L);
    }

    @Override
    protected void onExtensionDisable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        context = null;
    }

    // ------------------------------------------------------------ 定时广播

    private void tick() {
        ExtensionContext current = context;
        if (current == null) {
            return;
        }
        maybeReloadConfig(current);
        if (!current.isFeatureActive("broadcast") || !autoBroadcastEnabled()) {
            nextBroadcastAt = System.currentTimeMillis() + intervalMillis(current);
            return;
        }
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextBroadcastAt) {
            return;
        }
        nextBroadcastAt = now + intervalMillis(current);
        try {
            broadcast(null);
        } catch (Exception e) {
            current.logger().warning("广播任务执行出错: " + e.getMessage());
        }
    }

    /** 管理员直接改 config.yml 或执行 {@code /lban reload} 时，下一次扫描就能看到新值。 */
    private void maybeReloadConfig(ExtensionContext current) {
        ExtensionConfig config = current.config();
        File file = config.file();
        long modified = file.lastModified();
        if (modified != configLastModified) {
            configLastModified = modified;
            config.reload();
        }
    }

    private long intervalMillis(ExtensionContext current) {
        int minutes = Math.max(1, current.config().getInt("interval-minutes", 5));
        return minutes * 60_000L;
    }

    private boolean autoBroadcastEnabled() {
        ExtensionContext current = context;
        return current != null && current.config().getBoolean("auto-broadcast", true);
    }

    private void setAutoBroadcastEnabled(boolean enabled) {
        ExtensionContext current = context;
        if (current == null) {
            return;
        }
        ExtensionConfig config = current.config();
        config.set("auto-broadcast", enabled);
        config.save();
        configLastModified = config.file().lastModified();
        nextBroadcastAt = System.currentTimeMillis() + 10_000L;
    }

    // ------------------------------------------------------------ 命令

    private boolean toggle(CommandSender sender) {
        ExtensionContext current = context;
        if (current == null) {
            return true;
        }
        if (!sender.hasPermission("lengbanlist.toggle")) {
            sender.sendMessage(prefix() + "§c不是你的工作喵！");
            return true;
        }
        boolean enabled = !autoBroadcastEnabled();
        setAutoBroadcastEnabled(enabled);
        Model model = current.messages().current();
        String message = model != null
                ? model.toggleBroadcast(enabled)
                : (enabled ? "§a已开启自动广播" : "§c已关闭自动广播");
        sender.sendMessage(prefix() + message);
        current.services().audit().log(enabled ? "开启广播" : "关闭广播", sender.getName(), "", "");
        return true;
    }

    /**
     * 立即广播一次。
     *
     * @param sender 触发者；Web 面板传 {@code null}，只发给在线玩家
     */
    private boolean broadcast(CommandSender sender) {
        ExtensionContext current = context;
        if (current == null || !current.isFeatureActive("broadcast")) {
            return false;
        }
        String template = pickTemplate(current);
        if (template == null || template.isEmpty()) {
            if (!warnedNoTemplate) {
                warnedNoTemplate = true;
                current.logger().warning("广播消息为空：请在扩展配置里设置 messages 或 default-message，定时广播已跳过。");
            }
            if (sender != null) {
                sender.sendMessage(prefix() + "§c广播消息未配置，请在本扩展的 config.yml 中设置 default-message。");
            }
            return false;
        }

        String rendered = render(current, template);
        for (Player player : Bukkit.getOnlinePlayers()) {
            current.scheduler().runSync(player, () -> send(player, rendered));
        }
        if (sender != null) {
            sender.sendMessage(prefix() + "§a已广播当前封禁人数。");
        }
        return true;
    }

    private String pickTemplate(ExtensionContext current) {
        List<String> messages = current.config().getStringList("messages");
        String defaultMessage = current.config().getString("default-message", "");
        String mode = current.config().getString("mode", "random");
        if (!"random".equalsIgnoreCase(mode) && !"orderly".equalsIgnoreCase(mode)
                && !warnedUnknownMode) {
            warnedUnknownMode = true;
            current.logger().warning("config.yml 的 mode 只支持 random / orderly，已按 random 处理。");
        }
        return pickTemplate(messages, defaultMessage, mode, orderlyCursor,
                () -> messages == null || messages.isEmpty()
                        ? 0
                        : ThreadLocalRandom.current().nextInt(messages.size()));
    }

    /**
     * 纯选择逻辑，单独抽出来是为了能不经 Bukkit 直接测。
     *
     * @param random 返回随机下标；越界（含负数）由 {@link Math#floorMod(int, int)} 收回来
     */
    static String pickTemplate(List<String> messages, String defaultMessage, String mode,
                               AtomicInteger cursor, java.util.function.IntSupplier random) {
        if (messages == null || messages.isEmpty()) {
            return defaultMessage;
        }
        if ("orderly".equalsIgnoreCase(mode)) {
            return messages.get(Math.floorMod(cursor.getAndIncrement(), messages.size()));
        }
        return messages.get(Math.floorMod(random.getAsInt(), messages.size()));
    }

    private String render(ExtensionContext current, String template) {
        int banCount = current.services().bans().activeBanCount();
        int banIpCount = current.services().bans().activeIpBanCount();
        return template
                .replace("%s", String.valueOf(banCount))
                .replace("%i", String.valueOf(banIpCount))
                .replace("%t", String.valueOf(banCount + banIpCount));
    }

    private void send(Player player, String message) {
        TextComponent mainMessage = new TextComponent(
                ChatColor.translateAlternateColorCodes('&', prefix() + " " + message));
        mainMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(HOVER_TITLE).create()));

        TextComponent clickable = new TextComponent(BUTTON_TEXT);
        clickable.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lban list"));
        clickable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(BUTTON_HOVER).create()));

        player.spigot().sendMessage(mainMessage, clickable);
    }

    private String prefix() {
        ExtensionContext current = context;
        return current == null ? "" : current.messages().prefix();
    }

    // ------------------------------------------------------------ 旧配置迁移

    /**
     * 把核心旧的 {@code plugins/Lengbanlist/broadcast.yml} 导入一次。
     *
     * <p>用 {@code migrated-from-core} 标记保证只做一次：管理员之后在扩展配置里
     * 改的值不会被旧文件再次盖回去。
     */
    private void migrateLegacyConfig(ExtensionContext context) {
        ExtensionConfig config = context.config();
        if (config.getBoolean("migrated-from-core", false)) {
            return;
        }
        File pluginsDir = getDataFolder().getParentFile();
        if (pluginsDir == null) {
            return;
        }
        File legacyFile = new File(new File(pluginsDir, "Lengbanlist"), "broadcast.yml");
        if (!legacyFile.isFile()) {
            return;
        }
        try {
            YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
            if (legacy.contains("opensendtime")) {
                config.set("auto-broadcast", legacy.getBoolean("opensendtime", true));
            }
            if (legacy.contains("sendtime")) {
                config.set("interval-minutes", Math.max(1, legacy.getInt("sendtime", 5)));
            }
            if (legacy.contains("mode")) {
                config.set("mode", legacy.getString("mode", "random"));
            }
            if (legacy.contains("messages")) {
                config.set("messages", legacy.getStringList("messages"));
            }
            if (legacy.contains("default-message")) {
                config.set("default-message", legacy.getString("default-message", ""));
            }
            config.set("migrated-from-core", true);
            config.save();
            context.logger().info("已从核心 broadcast.yml 导入广播设置，之后以本扩展的配置为准。");
        } catch (Exception e) {
            // 导入失败最多是沿用默认配置，绝不能让扩展加载失败
            context.logger().warning("导入旧广播配置失败: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ 对外服务

    private final class Service implements BroadcastService {

        @Override
        public boolean isEnabled() {
            return BroadcastExtension.this.autoBroadcastEnabled();
        }

        @Override
        public void setEnabled(boolean enabled) {
            BroadcastExtension.this.setAutoBroadcastEnabled(enabled);
        }

        @Override
        public boolean broadcastNow(CommandSender sender) {
            return broadcast(sender);
        }
    }
}
