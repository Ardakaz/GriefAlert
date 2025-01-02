//TODO
//- Ignore rollbacked actions.
//- Fix container bug.
//- Make spam limiter.

package net.ardakaz.griefalert;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.coreprotect.CoreProtectAPI.ParseResult;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GriefAlert extends JavaPlugin implements Listener {

    private static CoreProtectAPI coreProtectAPI;
    private Integer identicalAlerts = 1;
    private String lastAlert;
    
    private Set<Material> EXCLUDED_BLOCKS;
	private Set<InventoryType> VALID_CONTAINERS;
    private String MAP_LINK;
    private Boolean ALLOW_STEALING;

    // Init GriefAlert
    @Override
    public void onEnable() {
    	
        coreProtectAPI = getCoreProtect();
        if (coreProtectAPI == null) {
            getLogger().severe("CoreProtect not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(this, this);
        
        // Config
        saveDefaultConfig();
        List<String> excludedBlocks = getConfig().getStringList("excluded-blocks");
        EXCLUDED_BLOCKS = excludedBlocks.stream().map(Material::valueOf).collect(Collectors.toSet());
        List<String> validContainers = getConfig().getStringList("valid-containers");
        VALID_CONTAINERS = validContainers.stream().map(InventoryType::valueOf).collect(Collectors.toSet());
        MAP_LINK = getConfig().getString("map-link");
        ALLOW_STEALING = getConfig().getBoolean("allow-stealing");
        
        getLogger().info("GriefAlert has been enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("GriefAlert has been disabled.");
    }
    
	@EventHandler (ignoreCancelled = true)
	// Block break alerts
    public void onBlockBreak(BlockBreakEvent event) {
    	// Exclusion list
		if (EXCLUDED_BLOCKS.contains(event.getBlock().getType())) {
		    return;
		}

        // Event parser
        String playerName = event.getPlayer().getName();
        String blockType = event.getBlock().getType().toString();
        int x = event.getBlock().getX();
        int y = event.getBlock().getY();
        int z = event.getBlock().getZ();
        String worldName = event.getBlock().getWorld().getName();

        // Check if grief
        String target = inspectBlock(event.getBlock(), event.getPlayer());
        if (target != null) {
            // Alert
            String message = ChatColor.RED + playerName + " broke " + blockType + " placed by " + target + " at " + x + " " + y + " " + z + " in " + worldName;
            alert(message, playerName, "[Map Link](" + MAP_LINK + "/?worldname=" + worldName + "&zoom=7&x=" + x + "&y=" + y + "&z=" + z + ")", target);
        }
    }
    
    // Stealing alerts
    @EventHandler (ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
    	if (ALLOW_STEALING) {
    		return;
    	}
    	boolean stealing;
    	
    	// Event parser for inv
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        Inventory clickedInventory = event.getClickedInventory();
        ItemStack item = event.getCurrentItem();

        if (item == null || inventory.getLocation() == null || item.getType() == Material.AIR) {
        	return;
        }
        
        // Exclusion list
     	if (!VALID_CONTAINERS.contains(inventory.getType())) {
     	    return;
     	}
        
        // Inv actions
        InventoryAction action = event.getAction();
        if ((action == InventoryAction.PICKUP_ALL || action == InventoryAction.PICKUP_HALF ||
            action == InventoryAction.PICKUP_ONE || action == InventoryAction.PICKUP_SOME || 
            action == InventoryAction.MOVE_TO_OTHER_INVENTORY) && clickedInventory == inventory) {
            stealing = true;
        } else if (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_SOME ||
            action == InventoryAction.PLACE_ONE || (action == InventoryAction.MOVE_TO_OTHER_INVENTORY && clickedInventory != inventory)) {
        	stealing = false;
        } else {
        	return;
        }
        
        // Event parser for container + check if grief
        String target = inspectBlock(inventory.getLocation().getBlock(), player);
        if (target != null) {
        	String playerName = player.getName();
            String itemName = item.getType().toString();
            int amount = item.getAmount();
            int x = inventory.getLocation().getBlockX();
            int y = inventory.getLocation().getBlockY();
            int z = inventory.getLocation().getBlockZ();
            String worldName = inventory.getLocation().getWorld().getName();
            
            if (stealing) {
            	// Stealing
            	String message = ChatColor.RED + playerName + " took " + amount + " " + itemName + " from " + target + "'s container at " + x + " " + y + " " + z + " in " + worldName;
            	alert(message, playerName, "[Map Link](" + MAP_LINK + "/?worldname=" + worldName + "&zoom=7&x=" + x + "&y=" + y + "&z=" + z + ")", target);
            } else {
            	// Putting back
            	String message = ChatColor.RED + playerName + " put " + amount + " " + itemName + " into " + target + "'s container at " + x + " " + y + " " + z + " in " + worldName;
            	alert(message, playerName, "[Map Link](" + MAP_LINK + "/?worldname=" + worldName + "&zoom=7&x=" + x + "&y=" + y + "&z=" + z + ")", target);
            }
        }
    }

    // Sends the alert (or cancels it)
	private void alert(String message, String playerName, String mapLink, String target) {
    	// Exclude trusted people
    	Player griefer = Bukkit.getPlayer(playerName);
    	if (griefer.hasPermission("griefalert.exclude") || griefer.hasPermission("griefalert.exclude." + target)) {
    		return;
    	}
    	
    	// Spam limiter
    	String realAlertMessage = message;
    	String[] alert1 = null;
    	if (lastAlert != null) {
    		alert1 = lastAlert.split(" ");
    	}
    	String[] alert2 = message.split(" ");
    	
    	if (alert1 != null) {
	    	if (alert1[2].equals(alert2[2]) && alert1[5].equals(alert2[5]) && alert1[1].equals("broke") && alert2[1].equals("broke")) {
	    		identicalAlerts += 1;
	    	}
	    	else if (Arrays.equals(alert1, alert2)) {
	    		identicalAlerts += 1;
	    	}
	    	else {
	    		identicalAlerts = 1;
	    	}
    	}
    	
    	if (identicalAlerts == 4) {
    		message = ChatColor.DARK_RED + "Same behavior continues.";
    		mapLink = null;
    	}
    	
    	if (identicalAlerts > 4) {
    		return;
    	}
    	
    	// Send an event for external hooks
    	GriefAlertEvent griefalert_event;
    	if (mapLink != null && !mapLink.isEmpty()) {
    		griefalert_event = new GriefAlertEvent(message + " (" + mapLink + ")");
    	}
    	else {
    		griefalert_event = new GriefAlertEvent(message);
    	}
    	getServer().getPluginManager().callEvent(griefalert_event);

    	// Notify staff ingame
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("griefalert.notify")) {
            	player.sendMessage(message);
            }
        }
        
        lastAlert = realAlertMessage;
    }
	
	// Block inspector: if the block was placed by another player, returns their name.
	private static String inspectBlock(Block block, Player player) {
		List<String[]> lookup = coreProtectAPI.blockLookup(block, 50000000);
        if (lookup == null || lookup.size() <= 0) {
        	// Natural block
        	return null;
        }
        
        String[] result = lookup.get(0);
        ParseResult parseResult = coreProtectAPI.parseResult(result);
        if (parseResult.isRolledBack()) {
        	result = lookup.get(1);
            parseResult = coreProtectAPI.parseResult(result);
        }

        if (result == null || parseResult == null || parseResult.getPlayer().startsWith("#") || parseResult.getPlayer().equals(player.getName())) {
        	// Placed by breaker or natural event
            return null;
        }
        return parseResult.getPlayer();
        
	}

    private CoreProtectAPI getCoreProtect() {
        Plugin plugin = getServer().getPluginManager().getPlugin("CoreProtect");

        if (plugin == null || !(plugin instanceof CoreProtect)) {
            return null;
        }

        return ((CoreProtect) plugin).getAPI();
    }
}