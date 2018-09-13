package com.gmail.panmpan.MultiverseExtension;

import java.util.HashMap;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import com.dumptruckman.minecraft.util.Logging;
import com.gmail.panmpan.MultiverseExtension.TpRequest;
import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVPlugin;
import com.onarandombox.MultiverseCore.MVWorld;

//import com.sk89q.worldedit.bukkit.WorldEditAPI;

public class MultiverseExtension extends JavaPlugin implements Listener, MVPlugin {
	private static final Logger log = Logger.getLogger("Minecraft");
	private static final String logPrefix = "[My-Multiverse-Exetension] ";

	private MultiverseCore core;
	private MVWorld lobbyWorld;
	private MVWorld survivalWorld;
	
	private BukkitScheduler scheduler = getServer().getScheduler();
	private BukkitTask morningTask;
	private int playersInSleep;
	
	private HashMap<UUID, TpRequest> requests = new HashMap<UUID, TpRequest> ();
	
	public void onEnable() {
		this.core = (MultiverseCore) getServer().getPluginManager().getPlugin("Multiverse-Core");
		
		// find Multiverse-Core, if not disable this plugin
		if (this.core == null) {
			log.info(logPrefix + "Multiverse-Core not found");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		
		// Register this plugin with Multiverse-Core 
		log.info(logPrefix + "- Version " + this.getDescription().getVersion() + " Enabled");
        this.core.incrementPluginCount();
        
        this.lobbyWorld = (MVWorld) this.core.getMVWorldManager().getMVWorld("welcomelobby");
        this.survivalWorld = (MVWorld) this.core.getMVWorldManager().getMVWorld("newSurvival");
        
        getServer().getPluginManager().registerEvents((Listener) this, this);
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (sender instanceof Player) {
			Player player = (Player) sender;
			if (cmd.getName().equalsIgnoreCase("lobby") || cmd.getName().equalsIgnoreCase("hub")) {
				player.teleport(this.lobbyWorld.getSpawnLocation());
			}
			else if (cmd.getName().equalsIgnoreCase("survival")) {
				player.teleport(this.survivalWorld.getSpawnLocation());
			}
			else if (cmd.getName().equalsIgnoreCase("tpto") || cmd.getName().equalsIgnoreCase("tphere")) {
				if (args.length < 1) {
					sender.sendMessage("必須指定一個玩家");
					return false;
				}
				
				@SuppressWarnings("deprecation")
				Player target = (Bukkit.getServer().getPlayer(args[0]));

				if (target == null) {
					sender.sendMessage(args[0] + " 不在線上");
				}
				
				if (player.hasPermission("mimebroper.tp")) {
					if (cmd.getName().equalsIgnoreCase("tpto")) {
						player.teleport(target);
					}
					else {
						target.teleport(player);
					}
					return true;
				}
				else {
					TpRequest request = requests.get(target.getUniqueId());
					if (request != null) {
						player.sendMessage(ChatColor.RED + "對方還沒回應上一個傳送請求");
						return true;
					}

					player.sendMessage(ChatColor.GOLD +"等待對方認同");
					request = new TpRequest();
					request.requester = player.getUniqueId();
					request.create_at = Instant.now();
					
					String message = ChatColor.GOLD + player.getDisplayName();
					if (cmd.getName().equalsIgnoreCase("tpto")) {
						request.type = "tpto";
						message += " 想傳送到你的位置，/tc 接受 /td 不接受";
					}
					else {
						request.type = "tphere";
						message += " 把你傳送到他的位置，/tc 接受 /td 不接受";
					}
					requests.put(target.getUniqueId(), request);
					target.sendMessage(message);

					scheduler.runTaskLater(this, new Runnable () {
						public void run() {
							Set<UUID> keyset = requests.keySet();
							for (UUID key: keyset) {
								TpRequest request = requests.get(key);
								Duration deltaTime = Duration.between(request.create_at, Instant.now());
								long delta = deltaTime.getSeconds();
								
								if (delta > 25) {
									Player target = Bukkit.getPlayer(key);
									Player player = Bukkit.getPlayer(request.requester);
									target.sendMessage(ChatColor.RED + player.getDisplayName() + " 送的 TP 請求過期");
									player.sendMessage(ChatColor.RED + "給 " + target.getDisplayName() + " 的 TP 請求過期");
									requests.remove(key);
								}
							}
							
						}
					}, 600L);
					return true;
				}
			}
			else if (cmd.getName().equalsIgnoreCase("tc")) {
				TpRequest request = requests.get(player.getUniqueId());
				
				if (request == null) {
					player.sendMessage(ChatColor.RED + "目前沒有傳送請求");
					return true;
				}
				if (request.type == "tpto") {
					Player target = Bukkit.getPlayer(request.requester);
					target.teleport(player);
					target.sendMessage(ChatColor.GREEN + player.getDisplayName() + " 接受你的傳送");
					requests.remove(player.getUniqueId());
				}
				else if (request.type == "tphere") {
					Player target = Bukkit.getPlayer(request.requester);
					player.teleport(target);
					target.sendMessage(ChatColor.GREEN + player.getDisplayName() + " 接受你的傳送");
					requests.remove(player.getUniqueId());
				}
				return true;
			}
			else if (cmd.getName().equalsIgnoreCase("td")) {
				TpRequest request = requests.get(player.getUniqueId());
				
				if (request != null) {
					Player target = Bukkit.getPlayer(request.requester);
					
					player.sendMessage(ChatColor.RED + "拒絕 " + target.getDisplayName() + " 的傳送");
					target.sendMessage(ChatColor.RED + player.getDisplayName() + " 拒絕你的傳送");

					requests.remove(player.getUniqueId());
				}
				else {
					player.sendMessage(ChatColor.RED + "目前沒有傳送請求");
				}
				return true;
			}
		}
		return true;
	}
	
//	@EventHandler
//	public void onPlayerJoin(PlayerJoinEvent event) {
//		UUID playerUUID  = event.getPlayer().getUniqueId();
//		this.playerLastLocation.put(playerUUID, new ArrayList<Location>());
//	}
	
//	@EventHandler
//	public void onPlayerLeave(PlayerQuitEvent event) {
//		UUID playerUUID  = event.getPlayer().getUniqueId();
//		this.playerLastLocation.remove(playerUUID);
//	}
	
	@EventHandler
	public void onPlayerSleep(PlayerBedEnterEvent event) {
		Player player = event.getPlayer();
		getLogger().info(player.getWorld().getName());
		if (player.getWorld() == this.survivalWorld.getCBWorld()) {
			Bukkit.broadcastMessage(ChatColor.AQUA + player.getDisplayName() + " 在睡覺中");
			
			this.playersInSleep +=1;
			this.infoInt(this.playersInSleep);
			if (this.morningTask == null) {
				this.morningTask = this.scheduler.runTaskLater(this, new Runnable () {
					public void run () {
						Bukkit.broadcastMessage(ChatColor.AQUA + "早安啊 !");

						survivalWorld.getCBWorld().setTime(23459);
						morningTask = null;
						playersInSleep = 0;
						
						if (survivalWorld.getCBWorld().hasStorm()) {
							survivalWorld.getCBWorld().setStorm(false);
						}
					}
				}, 100);
			}
		}
	}
	
	@EventHandler
	public void onPlayerWake(PlayerBedLeaveEvent event) {
		if (this.morningTask != null && event.getPlayer().getWorld() == this.survivalWorld.getCBWorld()) {
			this.playersInSleep -= 1;
			if (this.playersInSleep == 0) {
				this.morningTask.cancel();
				this.morningTask = null;
			}
		}
	}

	private void infoInt(int msgint) {
		log.info(String.valueOf(msgint));
	}

	public void log(Level level, String msg) {
		Logging.log(level, msg);
	}

	public String dumpVersionInfo(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	public MultiverseCore getCore() {
		// TODO Auto-generated method stub
		return null;
	}

	public int getProtocolVersion() {
		// TODO Auto-generated method stub
		return 0;
	}

	public void setCore(MultiverseCore multiverseCore) {
		this.core = multiverseCore;
	}
}
