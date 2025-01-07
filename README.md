# GriefAlert
A Minecraft server plugin that alerts staff when someone modifying other people's builds. **Requires** [Coreprotect](https://www.curseforge.com/minecraft/bukkit-plugins/coreprotect) to work.

## Permissions
- `griefalert.notify` - Makes a player to see alerts in the chat.
- `griefalert.exclude` - Excludes a player from sending alerts completely.
- `griefalert.exclude.<player>` - Excludes a player from sending alerts when using the specified player's stuff.

## Discord Support
With [DiscordSRV](https://docs.discordsrv.com/), you can log alerts to Discord by adding following into alerts.yml:

    Alerts:
      - Trigger: GriefAlertEvent
        Channel: grief-alerts # Must be specified in config.yml!
        Content: "${getAlert()}"
