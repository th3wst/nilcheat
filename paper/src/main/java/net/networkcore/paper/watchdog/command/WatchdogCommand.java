package net.networkcore.paper.watchdog.command;

import net.networkcore.paper.NetworkCorePaperPlugin;
import net.networkcore.paper.watchdog.model.WatchdogCategory;
import net.networkcore.paper.watchdog.model.WatchdogIncident;
import net.networkcore.paper.watchdog.model.WatchdogProfile;
import net.networkcore.paper.watchdog.service.WatchdogService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class WatchdogCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private final NetworkCorePaperPlugin plugin;
    private final WatchdogService service;

    public WatchdogCommand(NetworkCorePaperPlugin plugin, WatchdogService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("networkcore.watchdog.staff")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "alerts" -> handleAlerts(sender);
            case "inspect" -> handleInspect(sender, args);
            case "history" -> handleHistory(sender, args);
            case "test" -> handleTest(sender, args);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    private boolean handleAlerts(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command is player-only.");
            return true;
        }
        boolean enabled = service.toggleAlerts(player);
        sender.sendMessage(
                ChatColor.DARK_AQUA + "[Watchdog] "
                        + ChatColor.GRAY + "Live alerts "
                        + (enabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled")
                        + ChatColor.GRAY + "."
        );
        return true;
    }

    private boolean handleInspect(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /watchdog inspect <player>");
            return true;
        }
        Optional<WatchdogProfile> optionalProfile = service.profile(args[1]);
        if (optionalProfile.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No Watchdog profile found for " + args[1] + ".");
            return true;
        }
        WatchdogProfile profile = optionalProfile.get();
        Player online = Bukkit.getPlayer(profile.playerId());
        sender.sendMessage(ChatColor.DARK_AQUA + "----- Nexora Watchdog -----");
        sender.sendMessage(ChatColor.GRAY + "Player: " + ChatColor.WHITE + profile.playerName());
        if (online != null) {
            sender.sendMessage(
                    ChatColor.GRAY + "Platform: " + ChatColor.WHITE + service.platform(online)
                            + ChatColor.GRAY + " | Ping: "
                            + ChatColor.WHITE + online.getPing() + "ms"
            );
            sender.sendMessage(ChatColor.GRAY + "Server: " + ChatColor.WHITE + plugin.configuration().serverId());
        } else {
            sender.sendMessage(ChatColor.GRAY + "Platform: " + ChatColor.WHITE + "offline");
            sender.sendMessage(ChatColor.GRAY + "Server: " + ChatColor.WHITE + "offline");
        }
        sender.sendMessage(
                ChatColor.GRAY + "Risk: "
                        + riskColor(service.riskLevel(profile))
                        + service.riskLevel(profile)
                        + ChatColor.GRAY + " | Total VL: "
                        + ChatColor.WHITE + service.format(profile.totalVl())
        );
        sender.sendMessage(
                ChatColor.GRAY + "Movement: " + ChatColor.WHITE + service.format(profile.vl(WatchdogCategory.MOVEMENT))
                        + ChatColor.GRAY + " | Combat: " + ChatColor.WHITE + service.format(profile.vl(WatchdogCategory.COMBAT))
        );
        sender.sendMessage(
                ChatColor.GRAY + "Inventory: " + ChatColor.WHITE + service.format(profile.vl(WatchdogCategory.INVENTORY))
                        + ChatColor.GRAY + " | Economy: " + ChatColor.WHITE + service.format(profile.vl(WatchdogCategory.ECONOMY))
                        + ChatColor.GRAY + " | Exploit: " + ChatColor.WHITE + service.format(profile.vl(WatchdogCategory.EXPLOIT))
        );
        List<WatchdogIncident> incidents = service.history(profile.playerId(), 10);
        sender.sendMessage(ChatColor.GRAY + "Persisted incidents: " + ChatColor.WHITE + incidents.size() + ChatColor.DARK_GRAY + " shown");
        if (!incidents.isEmpty()) {
            WatchdogIncident last = incidents.get(0);
            sender.sendMessage(
                    ChatColor.GRAY + "Last: "
                            + ChatColor.WHITE + last.category().name().toLowerCase(Locale.ROOT)
                            + "/" + last.checkName()
                            + ChatColor.GRAY + " +" + service.format(last.points())
                            + " " + TIME_FORMAT.format(last.createdAt())
            );
        }
        return true;
    }

    private boolean handleHistory(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /watchdog history <player>");
            return true;
        }
        Optional<WatchdogProfile> optionalProfile = service.profile(args[1]);
        if (optionalProfile.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No Watchdog profile found for " + args[1] + ".");
            return true;
        }
        WatchdogProfile profile = optionalProfile.get();
        List<WatchdogIncident> incidents = service.history(profile.playerId(), 20);
        sender.sendMessage(ChatColor.DARK_AQUA + "----- Watchdog History: " + profile.playerName() + " -----");
        if (incidents.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No persisted incidents.");
            return true;
        }
        for (WatchdogIncident incident : incidents) {
            sender.sendMessage(
                    ChatColor.GRAY + TIME_FORMAT.format(incident.createdAt())
                            + " " + ChatColor.YELLOW + incident.category().name().toLowerCase(Locale.ROOT)
                            + ChatColor.GRAY + "/" + incident.checkName()
                            + " " + ChatColor.RED + "+" + service.format(incident.points())
                            + ChatColor.GRAY + " " + incident.serverId()
                            + (incident.details().isBlank() ? "" : " | " + incident.details())
            );
        }
        return true;
    }

    private boolean handleTest(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /watchdog test <player> <movement|combat|inventory|economy|exploit>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player must be online for a synthetic test.");
            return true;
        }
        WatchdogCategory category;
        try {
            category = WatchdogCategory.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(ChatColor.RED + "Unknown category.");
            return true;
        }
        WatchdogIncident incident = service.record(
                target,
                category,
                "synthetic-test",
                3.0,
                "staff=" + sender.getName()
        );
        sender.sendMessage(
                ChatColor.DARK_AQUA + "[Watchdog] "
                        + ChatColor.GREEN + "Injected "
                        + incident.category().name().toLowerCase(Locale.ROOT)
                        + " incident for " + target.getName() + "."
        );
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_AQUA + "Nexora Watchdog v0.6");
        sender.sendMessage(ChatColor.GRAY + "/watchdog alerts");
        sender.sendMessage(ChatColor.GRAY + "/watchdog inspect <player>");
        sender.sendMessage(ChatColor.GRAY + "/watchdog history <player>");
        sender.sendMessage(ChatColor.GRAY + "/watchdog test <player> <movement|combat|inventory|economy|exploit>");
    }

    private ChatColor riskColor(String risk) {
        return switch (risk) {
            case "HIGH" -> ChatColor.RED;
            case "MEDIUM" -> ChatColor.YELLOW;
            default -> ChatColor.GREEN;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("alerts", "inspect", "history", "test"), args[0]);
        }
        if (args.length == 2
                && (args[0].equalsIgnoreCase("inspect")
                || args[0].equalsIgnoreCase("history")
                || args[0].equalsIgnoreCase("test"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("test")) {
            return filter(List.of("movement", "combat", "inventory", "economy", "exploit"), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
