package net.networkcore.paper.watchdog.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class WatchdogProfile {

    private final UUID playerId;
    private String playerName;
    private final EnumMap<WatchdogCategory, Double> categoryVl;

    public WatchdogProfile(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.categoryVl = new EnumMap<>(WatchdogCategory.class);

        for (WatchdogCategory category : WatchdogCategory.values()) {
            categoryVl.put(category, 0.0);
        }
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public void playerName(String playerName) {
        this.playerName = playerName;
    }

    public double vl(WatchdogCategory category) {
        return categoryVl.getOrDefault(category, 0.0);
    }

    public void setVl(WatchdogCategory category, double value) {
        categoryVl.put(category, Math.max(0.0, value));
    }

    public void addVl(WatchdogCategory category, double points) {
        setVl(category, vl(category) + Math.max(0.0, points));
    }

    public double totalVl() {
        double total = 0.0;
        for (double value : categoryVl.values()) {
            total += value;
        }
        return total;
    }

    public Map<WatchdogCategory, Double> categoryVl() {
        return Map.copyOf(categoryVl);
    }
}
