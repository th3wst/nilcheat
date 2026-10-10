package net.networkcore.paper.watchdog.model;

import java.time.Instant;
import java.util.UUID;

public record WatchdogIncident(
        long id,
        UUID playerId,
        String playerName,
        WatchdogCategory category,
        String checkName,
        double points,
        String details,
        String serverId,
        Instant createdAt
) {
}
