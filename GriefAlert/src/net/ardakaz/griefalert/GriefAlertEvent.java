// "API" for other plugins

package net.ardakaz.griefalert;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class GriefAlertEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();
    private String alert = "";

    public GriefAlertEvent(String alert) {
        this.alert = alert;
    }

	public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public String getAlert() {
        return this.alert;
    }
}