package org.leng.ext.vanish;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.leng.api.Cancellable;
import org.leng.api.CommandSpec;
import org.leng.api.ExtensionContext;
import org.leng.api.LengbanlistExtensionPlugin;
import org.leng.api.VanishService;
import org.leng.models.Model;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理员隐身。
 *
 * <p>原本是核心里的 {@code VanishManager} + {@code VanishListener}，搬到扩展后
 * 核心只通过 {@link VanishService} 读取状态：查询、举报目标列表、箱子菜单都
 * 不再依赖本扩展，扩展没装时它们按"没人隐身"处理。
 *
 * <p>隐身状态是内存态，重启/重载后不保留——与搬迁前一致。扩展被
 * {@code /lban ext disable} 关掉时不会走 Bukkit 的 {@code onDisable}，所以这里
 * 用一秒一次的看门狗把残留的隐身状态还原，避免玩家一直看不见自己。
 */
public final class VanishExtension extends LengbanlistExtensionPlugin {

    private static final String PERMISSION_USE = "lengbanlist.vanish";
    private static final String PERMISSION_SEE = "lengbanlist.vanish.see";

    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    private ExtensionContext context;
    private Cancellable watchdog;

    @Override
    public String id() {
        return "vanish";
    }

    @Override
    public String requiredApi() {
        return "[2.3,3.0)";
    }

    @Override
    public Set<String> features() {
        return Set.of("vanish");
    }

    @Override
    protected void onExtensionEnable(ExtensionContext context) {
        this.context = context;
        context.commands().register(CommandSpec.sub(
                "vanish", "lban", "vanish", PERMISSION_USE,
                "/lban vanish", "隐身/现身", (sender, command, label, args) -> toggle(sender)));
        getServer().getPluginManager().registerEvents(new VanishListener(), this);
        context.hooks().register(VanishService.class, new Service());
        watchdog = context.scheduler().runSyncRepeating(this::watchdog, 20L, 20L);
    }

    @Override
    protected void onExtensionDisable() {
        if (watchdog != null) {
            watchdog.cancel();
            watchdog = null;
        }
        restoreAll();
        context = null;
    }

    private boolean toggle(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix() + "§c此命令只能由玩家执行。");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_USE)) {
            sender.sendMessage(prefix() + "§c不是你的工作喵！");
            return true;
        }

        boolean nowVanished = toggle(player);
        Model model = context.messages().current();
        String message = model != null
                ? model.onVanish(nowVanished)
                : (nowVanished ? "§a隐身模式已开启" : "§c隐身模式已关闭");
        sender.sendMessage(prefix() + message);
        context.services().audit().log(
                nowVanished ? "开启隐身" : "关闭隐身", sender.getName(), player.getName(), "");
        return true;
    }

    /** 关掉总开关后把残留的隐身状态清掉；正常启用期间这只是一次空检查。 */
    private void watchdog() {
        ExtensionContext current = context;
        if (current == null) {
            return;
        }
        if (!current.isFeatureActive("vanish") && !vanished.isEmpty()) {
            restoreAll();
        }
    }

    private boolean isVanished(Player player) {
        return player != null && vanished.contains(player.getUniqueId());
    }

    private boolean canSee(Player viewer) {
        return viewer != null && viewer.hasPermission(PERMISSION_SEE);
    }

    private boolean toggle(Player player) {
        if (isVanished(player)) {
            unvanish(player);
            return false;
        }
        vanish(player);
        return true;
    }

    private void vanish(Player player) {
        vanished.add(player.getUniqueId());
        setListed(player, false);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(player.getUniqueId()) || canSee(viewer)) {
                continue;
            }
            hide(viewer, player);
        }
    }

    private void unvanish(Player player) {
        vanished.remove(player.getUniqueId());
        setListed(player, true);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            show(viewer, player);
        }
    }

    private void handleJoin(Player joiner) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(joiner.getUniqueId())) {
                continue;
            }
            if (isVanished(online) && !canSee(joiner)) {
                hide(joiner, online);
            }
        }
        if (isVanished(joiner)) {
            vanish(joiner);
        }
    }

    private void restoreAll() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!isVanished(online)) {
                continue;
            }
            setListed(online, true);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.getUniqueId().equals(online.getUniqueId())) {
                    continue;
                }
                show(viewer, online);
            }
        }
        vanished.clear();
    }

    private void hide(Player viewer, Player target) {
        ExtensionContext current = context;
        if (current == null) {
            return;
        }
        current.scheduler().runSync(viewer, () -> {
            if (viewer.isOnline() && target.isOnline()) {
                viewer.hidePlayer(this, target);
            }
        });
    }

    private void show(Player viewer, Player target) {
        ExtensionContext current = context;
        if (current == null) {
            return;
        }
        current.scheduler().runSync(viewer, () -> {
            if (viewer.isOnline() && target.isOnline()) {
                viewer.showPlayer(this, target);
            }
        });
    }

    private String prefix() {
        ExtensionContext current = context;
        return current == null ? "" : current.messages().prefix();
    }

    // ------------------------------------------------------------ 列表可见性

    /**
     * 1.19+ 的 {@code Player#setListed} 不在 Spigot 1.17.1 的编译目标里，
     * 与核心搬迁前一样用反射，拿不到就跳过。
     */
    private static final class SetListedResolver {
        static final Method METHOD = resolve();

        private static Method resolve() {
            try {
                return Player.class.getMethod("setListed", boolean.class);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }
    }

    private void setListed(Player player, boolean listed) {
        Method method = SetListedResolver.METHOD;
        ExtensionContext current = context;
        if (method == null || current == null) {
            return;
        }
        current.scheduler().runSync(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            try {
                method.invoke(player, listed);
            } catch (Exception ignored) {
                // 服务端没有这个能力就只做实体隐藏，不影响主流程
            }
        });
    }

    // ------------------------------------------------------------ 事件与门面

    private final class VanishListener implements Listener {

        @EventHandler(priority = EventPriority.MONITOR)
        public void onJoin(PlayerJoinEvent event) {
            ExtensionContext current = context;
            if (current == null || !current.isFeatureActive("vanish")) {
                return;
            }
            Player player = event.getPlayer();
            handleJoin(player);
            if (isVanished(player) && current.config().getBoolean("hide-join-message", true)) {
                event.setJoinMessage(null);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent event) {
            ExtensionContext current = context;
            if (current != null && isVanished(event.getPlayer())
                    && current.config().getBoolean("hide-quit-message", true)) {
                event.setQuitMessage(null);
            }
        }
    }

    /** 核心只读这几项，不碰状态机。 */
    private final class Service implements VanishService {

        @Override
        public boolean isVanished(Player player) {
            return VanishExtension.this.isVanished(player);
        }

        @Override
        public boolean canSee(Player viewer) {
            return VanishExtension.this.canSee(viewer);
        }

        @Override
        public int count() {
            return vanished.size();
        }

        @Override
        public boolean toggle(Player player) {
            return VanishExtension.this.toggle(player);
        }

        @Override
        public void restoreAll() {
            VanishExtension.this.restoreAll();
        }
    }
}
