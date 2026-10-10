package net.networkcore.paper.watchdog.service;

import net.networkcore.paper.NetworkCorePaperPlugin;
import net.networkcore.paper.watchdog.model.WatchdogCategory;
import net.networkcore.paper.watchdog.model.WatchdogIncident;
import net.networkcore.paper.watchdog.model.WatchdogProfile;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WatchdogService implements AutoCloseable {

    private final NetworkCorePaperPlugin plugin;
    private final WatchdogDatabase database;
    private final Map<UUID, WatchdogProfile> profiles = new ConcurrentHashMap<>();
    private final Set<UUID> alertSubscribers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> teleportGraceUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> velocityGraceUntil = new ConcurrentHashMap<>();

    public WatchdogService(NetworkCorePaperPlugin plugin) {
        this.plugin = plugin;
        this.database = new WatchdogDatabase(plugin);
        this.database.open();
    }

    public WatchdogProfile profile(Player player) {
        WatchdogProfile profile = profiles.computeIfAbsent(
                player.getUniqueId(),
                id -> database.loadProfile(id, player.getName())
        );
        if (!player.getName().equals(profile.playerName())) {
            profile.playerName(player.getName());
            database.saveProfile(profile);
        }
        return profile;
    }

    public Optional<WatchdogProfile> profile(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            return Optional.of(profile(online));
        }
        Optional<UUID> playerId = database.findUuidByName(playerName);
        if (playerId.isEmpty()) {
            return Optional.empty();
        }
        WatchdogProfile profile = profiles.computeIfAbsent(
                playerId.get(),
                id -> database.loadProfile(id, playerName)
        );
        return Optional.of(profile);
    }

    public List<WatchdogIncident> history(UUID playerId, int limit) {
        return database.recentIncidents(playerId, limit);
    }

    public WatchdogIncident record(Player player, WatchdogCategory category, String checkName, double points, String details) {
        WatchdogProfile profile = profile(player);
        profile.addVl(category, points);
        database.saveProfile(profile);
        WatchdogIncident incident = database.insertIncident(
                player.getUniqueId(),
                player.getName(),
                category,
                checkName,
                points,
                details == null ? "" : details,
                plugin.configuration().serverId()
        );
        if (points >= 3.0 || profile.totalVl() >= 12.0) {
            sendAlert(player, incident, profile);
        }
        return incident;
    }

    public boolean toggleAlerts(Player player) {
        if (alertSubscribers.remove(player.getUniqueId())) {
            return false;
        }
        alertSubscribers.add(player.getUniqueId());
        return true;
    }

    public boolean alertsEnabled(Player player) {
        return alertSubscribers.contains(player.getUniqueId());
    }

    public void grantTeleportGrace(Player player, long milliseconds) {
        teleportGraceUntil.put(player.getUniqueId(), System.currentTimeMillis() + Math.max(0L, milliseconds));
    }

    public void grantVelocityGrace(Player player, long milliseconds) {
        velocityGraceUntil.put(player.getUniqueId(), System.currentTimeMillis() + Math.max(0L, milliseconds));
    }

    public boolean hasMovementGrace(Player player) {
        long now = System.currentTimeMillis();
        return teleportGraceUntil.getOrDefault(player.getUniqueId(), 0L) > now
                || velocityGraceUntil.getOrDefault(player.getUniqueId(), 0L) > now;
    }

    public void clearTransientState(Player player) {
        teleportGraceUntil.remove(player.getUniqueId());
        velocityGraceUntil.remove(player.getUniqueId());
        alertSubscribers.remove(player.getUniqueId());
    }

    public String platform(Player player) {
        return isBedrock(player) ? "Bedrock" : "Java";
    }

    public boolean isBedrock(Player player) {
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null
                && Bukkit.getPluginManager().getPlugin("Floodgate") == null) {
            return false;
        }
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = apiClass.getMethod("getInstance");
            Object api = getInstance.invoke(null);
            Method isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            Object result = isFloodgatePlayer.invoke(api, player.getUniqueId());
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public String riskLevel(WatchdogProfile profile) {
        double total = profile.totalVl();
        if (total >= 25.0) {
            return "HIGH";
        }
        if (total >= 10.0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private void sendAlert(Player suspect, WatchdogIncident incident, WatchdogProfile profile) {
        String message = ChatColor.DARK_AQUA + "[Watchdog] "
                + ChatColor.YELLOW + suspect.getName()
                + ChatColor.GRAY + " "
                + incident.category().name().toLowerCase(Locale.ROOT)
                + "/" + incident.checkName()
                + " +" + format(incident.points())
                + " VL total=" + format(profile.totalVl());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!alertSubscribers.contains(player.getUniqueId())) {
                continue;
            }
            if (!player.hasPermission("networkcore.watchdog.alerts")
                    && !player.hasPermission("networkcore.watchdog.staff")) {
                continue;
            }
            player.sendMessage(message);
        }
    }

    public String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    public void close() {
        for (WatchdogProfile profile : profiles.values()) {
            database.saveProfile(profile);
        }
        database.close();
        profiles.clear();
        alertSubscribers.clear();
        teleportGraceUntil.clear();
        velocityGraceUntil.clear();
    }
}
