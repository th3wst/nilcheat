package net.networkcore.paper.watchdog;

import net.networkcore.paper.NetworkCorePaperPlugin;
import net.networkcore.paper.watchdog.command.WatchdogCommand;
import net.networkcore.paper.watchdog.listener.WatchdogCombatListener;
import net.networkcore.paper.watchdog.listener.WatchdogInventoryListener;
import net.networkcore.paper.watchdog.listener.WatchdogLifecycleListener;
import net.networkcore.paper.watchdog.listener.WatchdogMovementListener;
import net.networkcore.paper.watchdog.service.WatchdogService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;

public final class WatchdogBootstrap {

    private final WatchdogService service;

    private WatchdogBootstrap(WatchdogService service) {
        this.service = service;
    }

    public static WatchdogBootstrap enable(NetworkCorePaperPlugin plugin) {
        plugin.getLogger().info("          _ __     __               __");
        plugin.getLogger().info("   ____  (_) /____/ /_  ___  ____ _/ /_");
        plugin.getLogger().info("  / __ \\/ / / ___/ __ \\/ _ \\/ __ `/ __/");
        plugin.getLogger().info(" / / / / / / /__/ / / /  __/ /_/ / /_");
        plugin.getLogger().info("/_/ /_/_/_/\\___/_/ /_/\\___/\\__,_/\\__/");
        plugin.getLogger().info("");
        plugin.getLogger().info("made by th3wst v0.6");

        WatchdogService service = new WatchdogService(plugin);
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(new WatchdogLifecycleListener(plugin, service), plugin);
        pluginManager.registerEvents(new WatchdogMovementListener(service), plugin);
        pluginManager.registerEvents(new WatchdogCombatListener(service), plugin);
        pluginManager.registerEvents(new WatchdogInventoryListener(service), plugin);

        WatchdogCommand watchdogCommand = new WatchdogCommand(plugin, service);
        PluginCommand command = plugin.getCommand("watchdog");
        if (command == null) {
            service.close();
            throw new IllegalStateException("Watchdog command is missing from plugin.yml.");
        }
        command.setExecutor(watchdogCommand);
        command.setTabCompleter(watchdogCommand);

        plugin.getLogger().info("Watchdog risk engine enabled with SQLite persistence (observe/correlate mode; no automatic bans).");
        return new WatchdogBootstrap(service);
    }

    public WatchdogService service() {
        return service;
    }
}
