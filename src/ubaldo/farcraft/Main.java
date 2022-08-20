/* * * * * * * * * * * * * * * * *
 * [FarCraft Plugin]
 * 
 * Author	: Cesar Carrillo
 * Version	: 1.0
 * Date		: 8/16/2022
 * * * * * * * * * * * * * * * * */
package ubaldo.farcraft;

import java.util.Date;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("deprecation")
public class Main extends JavaPlugin implements Listener
{
	static final double BORDER_BUFFER = 5; // number of blocks in from the border
	
	@Override
	public void onEnable()
	{
		getServer().getPluginManager().registerEvents(this, this);
		saveDefaultConfig();
	}
	
	@Override
	public void onDisable() {}
	
	// get a random number using given range
	public int getRandomNumber(int min, int max) { return (int) ((Math.random() * (max - min)) + min); }

	// get a random position, does not spawn in water/lava/air
	public Location getRandomLocation(World world)
	{
		WorldBorder wborder 	= world.getWorldBorder();

		double 		borderSize 	= (wborder.getSize() / 2) - BORDER_BUFFER;
		
		// random x and z for spawn
		double 		x 			= getRandomNumber(- (int) borderSize, (int) borderSize) + 0.5;
		double 		z 			= getRandomNumber(- (int) borderSize, (int) borderSize) + 0.5;
		
		// start from the top and go down each block until we find the highest non-air block
		// we start from the top because there are pockets of air due to caves
		for(int i = world.getLogicalHeight(); i > 0; i--)
		{
			Location 	location 	= new Location(world, x, i, z);
			Block 		block 		= world.getBlockAt(location);
			
			if(block.getType() != Material.AIR)
			{
				// make sure block is not a liquid
				if(	block.getType() == Material.WATER || 
					block.getType() == Material.LAVA)
				{
					return getRandomLocation(world); // reiterate
				}
				
				return new Location(world, x, i + 1, z);
			}
		}
		
		return null;
	}
	
	// write the initial config for the player
	public void initPlayerConfig(Player player)
	{
		// make sure config does not exist
		if(!getConfig().getBoolean(player.getUniqueId().toString() + "@connected"))
		{
			Location sLocation = getRandomLocation(player.getWorld());
			
			getConfig().set(player.getUniqueId().toString() + "@connected", true);						// has connected to server before
			getConfig().set(player.getUniqueId().toString() + "@dead", 		false);						// is dead
			getConfig().set(player.getUniqueId().toString() + "@name", 		player.getDisplayName());	// name
			getConfig().set(player.getUniqueId().toString() + "@spawnX", 	sLocation.getBlockX());		// spawn location x
			getConfig().set(player.getUniqueId().toString() + "@spawnY", 	sLocation.getBlockY());		// spawn location y
			getConfig().set(player.getUniqueId().toString() + "@spawnZ", 	sLocation.getBlockZ()); 	// spawn location z
			
			saveConfig();
		}
	}
	
	// get a given players spawn point 
	public Location getPlayerSpawn(Player player)
	{
		// make sure config exists
		if(getConfig().getBoolean(player.getUniqueId().toString() + "@connected"))
		{
			int x = getConfig().getInt(player.getUniqueId().toString() + "@spawnX");
			int y = getConfig().getInt(player.getUniqueId().toString() + "@spawnY");
			int z = getConfig().getInt(player.getUniqueId().toString() + "@spawnZ");
			
			return new Location(player.getWorld(), x, y, z);
		}
		
		return null;
	}
	
	// ban a player with date saved in config
	public void deathBanPlayer(Player player)
	{
		Date date = new Date();
		
		getConfig().set(player.getUniqueId().toString() + "@deathTime", date.toString());
		getConfig().set(player.getUniqueId().toString() + "@dead", true);
		
		saveConfig();
		
		player.kickPlayer("You have died, please join back in 24 hours to respawn.");
	}
	
	@EventHandler
	public void onPlayerRespawnEvent(PlayerRespawnEvent event)
	{
		Player player = event.getPlayer();
		
		event.setRespawnLocation(getPlayerSpawn(player));
	}
	
	@EventHandler
	public void onPlayerDeathEvent(PlayerDeathEvent event)
	{
		Player player = event.getEntity();
		
		deathBanPlayer(player);
	}
	
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event)
	{
		Player player = event.getPlayer();
		
		// First time connecting
		if(!getConfig().getBoolean(player.getUniqueId().toString() + "@connected"))
		{
			player.chat(".info");
			
			initPlayerConfig(player);
			
			player.teleport(getPlayerSpawn(player));
			
			return;
		}
		
		if(getConfig().getBoolean(player.getUniqueId().toString() + "@dead"))
		{
			Date 	nowDate 	= new Date();
			Date 	deathDate 	= new Date(getConfig().getString(player.getUniqueId().toString() + "@deathTime"));
			
			long 	seconds 	= (nowDate.getTime() - deathDate.getTime()) / 1000;
			int 	minutes 	= (int) seconds / 60;
			int 	hours 		= minutes / 60;
			
			// if 24 hours have passed then the player is unbanned.
			if(!(hours > 24))
			{
				player.kickPlayer("You have died, please join 24 hours after your death.\n" + 
									"\n[Time passed]\n" +
									"seconds: " + seconds + "\n" +
									"minutes: " + minutes + "\n" +
									"hours: " + hours + "\n");
			}
			else
			{
				// remove ban from the player
				getConfig().set(player.getUniqueId().toString() + "@dead", false);
				saveConfig();
			}
			
			return;
		}
	}
	
	@EventHandler
	public void onPlayerChatEvent(PlayerChatEvent event)
	{
		Player player 	= event.getPlayer();
		String message 	= event.getMessage();
		
		if(message.startsWith("."))
		{
			event.setCancelled(true);
			
			if(message.equals(".info"))
			{
				player.sendMessage("[Welcome to FarCraft]");
				player.sendMessage("Please read this message as it contains important information.");
				player.sendMessage("1. All new player spawns are random");
				player.sendMessage("2. Upon death, you will be banned for 24 hours.");
				player.sendMessage("3. The spawn you get is the one you keep.");
				player.sendMessage("4. To view the rules, type \"/.rules\"'");
				player.sendMessage("5. To view this message again, type \"/.info\"'");
				
				return;
			}
			
			if(message.equals(".rules"))
			{
				player.sendMessage("[Rules]");
				player.sendMessage("1. No cheating/hacking of any kind");
				player.sendMessage("2. Be nice.");
				player.sendMessage("3. Send issues/questions to cucarrillo@protonmail.com");
				
				return;
			}
		}
	}
	
	// DEBUG EVENT!!!
	// used to test random spawn location
	@EventHandler
	public void onPlayerInteractEvent(PlayerInteractEvent event)
	{
		Player player = event.getPlayer();
		
		if(event.getItem() == null) 					{ return; }
		if(event.getItem().getType() != Material.STICK) { return; }
		if(player.isOp() == false) 						{ return; }

		if(event.getItem().getItemMeta().getDisplayName().equalsIgnoreCase("debug-stick"))
		{
			player.sendMessage("You are being teleported to a random location.");
			player.teleport(getRandomLocation(player.getWorld()));
		}
	}
}