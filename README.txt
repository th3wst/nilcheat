
Nexora Watchdog v0.6

Replaces the old Watchdog package and plugin.yml.

Features:
- SQLite persistence
- Persistent VL
- Persistent incident history
- Updated /watchdog inspect
- Updated /watchdog history
- /watchdog test command
- Java/Bedrock detection with Floodgate
- Opt-in live alerts
- No auto bans

Bloom Hosting setup:
1. Stop the server.
2. Back up the current files.
3. Copy the updated paper files into the server repo/project.
4. Make sure NetworkCorePaperPlugin.java still has the WatchdogBootstrap import, field, and enable call.
5. Build the plugin with Java 21.
6. Upload/deploy the new NetworkCore-Paper-0.1.0.jar to the Hub and Skyblock plugin folders.
7. Set the Minecraft server runtime to Java 25 in the Bloom panel.
8. Start the server.

Watchdog data is stored at:
plugins/NetworkCore-Paper/watchdog/watchdog.db

Test commands:
/watchdog alerts
/watchdog inspect Danskapolskii
/watchdog history Danskapolskii
/watchdog test Danskapolskii movement
/watchdog test Danskapolskii combat
/watchdog test Danskapolskii inventory
/watchdog test Danskapolskii economy
/watchdog test Danskapolskii exploit

Persistence test:
1. Run a few /watchdog test commands.
2. Run /watchdog inspect Danskapolskii.
3. Restart the server.
4. Run /watchdog inspect Danskapolskii.
5. Run /watchdog history Danskapolskii.

The VL and incident history should still be there after the restart.
