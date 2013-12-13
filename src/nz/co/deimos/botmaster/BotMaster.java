package nz.co.deimos.botmaster;

import java.net.InetAddress;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.anjocaido.groupmanager.GroupManager;
import org.anjocaido.groupmanager.permissions.AnjoPermissionsHandler;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import org.kitteh.vanish.staticaccess.VanishNoPacket;
import org.kitteh.vanish.event.VanishStatusChangeEvent;

public class BotMaster extends JavaPlugin {
	
	private FileConfiguration config;
	protected Logger logger;
	
	private Map<World, Bot> botmap = new HashMap<World, Bot>();
	private Set<String> botnames = new HashSet<String>();
	
	private Map<Character, String> colourmap = new HashMap<Character, String>();
	private String relaychannel;
	private String adminchannel;
	private String eventchannel;
	
	private IRCSelector irc_instance;
	private Set<String> ircstaff = new HashSet<String>();
	private Player lasthelpme = null;
	
	private GroupManager groupmanager = null;

	public void onEnable() {
		
		logger = getLogger();
		PluginManager pm = getServer().getPluginManager();
		
		try {
			loadConfig();
		} catch (Exception e) {
			e.printStackTrace();
			pm.disablePlugin(this);
			return;
		}
		
		try {
			irc_instance = new IRCSelector(this);
			logger.info("Launching IRCSelector thread");
			Thread irc = new Thread(irc_instance);
			irc.start();
			launchBots();
		} catch (Exception e) {
			e.printStackTrace();
			getPluginLoader().disablePlugin(this);
		}
		
		// register event listeners
		pm.registerEvents(new BukkitEvent(), this);
		
		// hook into GroupManager
		Plugin gm = pm.getPlugin("GroupManager");
		if (gm == null) {
			logger.warning("Failed to hook into GroupManager");
			pm.disablePlugin(this);
		} else {
			groupmanager = (GroupManager) gm;
			logger.info("Hooked into GroupManager successfully");
		}
		
	}
	
	public void onDisable() {
		for (Bot bot : botmap.values()) {
			bot.kill();
		}
		irc_instance.kill();
		// clear old data structures
		botmap.clear();
		botnames.clear();
		colourmap.clear();
		ircstaff.clear();
	}
	
	private void loadConfig() throws Exception {
		saveDefaultConfig();
		config = getConfig();
		
		Bot bot;
		
		if (config.getBoolean("config-set")) {
			
			relaychannel = config.getString("irc.chan");
			adminchannel = config.getString("irc.adminchan");
			eventchannel = config.getString("botmaster.eventchan");
			
			// add the admin bot
			bot = createBot(null, config.getString("botmaster.nick"));
			bot.setMaster();
			bot.addOnPingCalls(config.getStringList("botmaster.onping"));
			bot.addOnMotdCalls(config.getStringList("botmaster.onmotd"));
			bot.addChannel(eventchannel);
			bot.addChannel(relaychannel);
			bot.addChannel(adminchannel);
			
			for (String key : config.getKeys(true)) {
				
				// add the bots
				if (key.startsWith("bots.")) {
					bot = createBot(key.substring(5), config.getString(key));
					if (bot != null) {
						bot.addChannel(relaychannel);
						bot.addChannel(adminchannel);
					}
				}
				
				// add the colourmap
				else if (key.startsWith("colourmap.")) {
					colourmap.put(key.charAt(10), config.getString(key));
				}
			}
			
			logger.info("colourmap: " + colourmap);
			
		} else {
			throw new Exception("Check your configuration");
		}
		
	}
	
	public Bot getBotByName(String botname) {
		for (Bot bot : botmap.values()) {
			if (bot.getBotName().equalsIgnoreCase(botname)) {
				return bot;
			}
		}
		return null;
	}
	
	private Bot createBot(String worldname, String botname) {
		World w;
		Bot b;
		if (worldname == null) {
			w = null;
		} else {
			w = getServer().getWorld(worldname);
		}
		if (botmap.containsKey(w)) {
			return null;
		} else {
			b = getBotByName(botname);
			if (b == null) {
				if (worldname == null) {
					logger.info("Creating bot " + botname + " (botmaster)");
				} else {
					logger.info("Creating bot " + botname + " for world " + worldname);
				}
				Bot bot = new Bot(this, botname, config.getString("irc.user"), config.getString("irc.auth"), config.getStringList("irc.onmotd"), config.getStringList("irc.onping"));
				botmap.put(w, bot);
				botnames.add(botname);
				return bot;
			} else {
				logger.info("Adding world " + worldname + " to bot " + botname);
				botmap.put(w, b);
				botnames.add(botname);
				return b;
			}
		}
	}
	
	private void launchBots() throws Exception {
		SocketChannel socket;
		Set<Bot> launched = new HashSet<Bot>();
		
		for (Bot bot : botmap.values()) {
			if (!launched.contains(bot)) {
				logger.info("Launching bot " + bot.getBotName());
				Thread.sleep(2500);
				socket = bot.createSocket(config.getString("irc.host"), config.getInt("irc.port"));
				irc_instance.registerSocket(socket, bot);
				launched.add(bot);
			}
		}
		
	}
	
	public boolean botnameExists(String botname) {
		return botnames.contains(botname);
	}

	public void clearIRCStaff() {
		ircstaff.clear();
	}
	
	public void addIRCStaff(String nick) {
		ircstaff.add(nick);
	}
	
	public boolean isIRCStaff(String nick) {
		return ircstaff.contains(nick);
	}
	
	public boolean isVanished(Player player) {
		try {
			return VanishNoPacket.isVanished(player.getName());
		} catch (final Exception e) {
			return false;
		}
	}
	
	/**
	 * Greedy method to get an IRC colour code from a mc group prefix
	 * @param mcprefix The string prefix to search for colour codes
	 * @return The raw IRC colour code value
	 */
	private String getIRCColourCode(String mcprefix) {
		for (char c : colourmap.keySet()) {
			if (mcprefix.indexOf(c) >= 0) {
				return colourmap.get(c);
			}
		}
		return "";
	}
	
	public void toIngameAdmins(String sender, String message) {
		String formattedchat = 	ChatColor.GRAY + "[" + ChatColor.DARK_AQUA + ChatColor.ITALIC + "#admin" + ChatColor.GRAY + "] " +
								ChatColor.DARK_PURPLE + sender + ChatColor.GRAY + ": " + ChatColor.WHITE + message;
		for (Player player : getServer().getOnlinePlayers()) {
			if (player.hasPermission("botmaster.admin")) {
				player.sendMessage(formattedchat);
			}
		}
	}
	
	public String getOnlinePlayerString() {
		String s = "";
		short c = 0;
		for (Player player : getServer().getOnlinePlayers()) {
			c++;
			AnjoPermissionsHandler aph = groupmanager.getWorldsHolder().getWorldPermissions(player);
			String playername = player.getName();
			String groupname = aph.getGroup(playername);
			String irccolour = getIRCColourCode(aph.getGroupPrefix(groupname));
			s += "\u0003 \u0003" + irccolour + playername;
		}
		return "Online: (" + c + "/" + getServer().getMaxPlayers() + ")" + s;
	}
	
	public String getVanishedString() {
		String s = "";
		short c = 0;
		for (Player player : getServer().getOnlinePlayers()) {
			if (isVanished(player)) {
				c++;
				AnjoPermissionsHandler aph = groupmanager.getWorldsHolder().getWorldPermissions(player);
				String playername = player.getName();
				String groupname = aph.getGroup(playername);
				String irccolour = getIRCColourCode(aph.getGroupPrefix(groupname));
				s += "\u0003 \u0003" + irccolour + playername;
			}
		}
		return "Vanished: (" + c + "/" + getServer().getOnlinePlayers().length + ")" + s;
	}
	
	private void onIRCRolePlayingAction(String sender, String message) {
		ChatColor groupcolour;
		if (isIRCStaff(sender)) {
			groupcolour = ChatColor.RED;
		} else {
			groupcolour = ChatColor.GRAY;
		}
		String formattedchat = 	ChatColor.GRAY + "[" + ChatColor.DARK_AQUA + "IRC" + ChatColor.GRAY + "] *" +
				ChatColor.WHITE + " " + groupcolour + ChatColor.ITALIC + sender + ChatColor.WHITE + " " + ChatColor.ITALIC + message;
		getServer().broadcastMessage(formattedchat);
	}
	
	public void onPrivmsg(String channel, String sender, String message) {
		
		if (message.equalsIgnoreCase(".online")) {
			String online = getOnlinePlayerString();
			botmap.get(null).sendPrivmsg(channel, online);
		
		} else if (channel.equals(relaychannel)) {
			
			if (message.startsWith("\001ACTION")) {
				onIRCRolePlayingAction(sender, message.substring(8));
			} else {
				String groupname;
				ChatColor groupcolour;
				if (isIRCStaff(sender)) {
					groupname = "Staff";
					groupcolour = ChatColor.RED;
				} else {
					groupname = "User";
					groupcolour = ChatColor.GRAY;
				}
				String formattedchat = 	ChatColor.GRAY + "[" + ChatColor.DARK_AQUA + "IRC" + ChatColor.GRAY + "][" +
										ChatColor.ITALIC + groupname + ChatColor.RESET + ChatColor.GRAY + "] " +
										groupcolour + sender + ChatColor.GRAY + ": " + ChatColor.WHITE + message;
				getServer().broadcastMessage(formattedchat);
			}
		
		} else if (channel.equals(adminchannel)) {
			
			int firstwhitespace = message.indexOf(' ');
			
			if (message.equalsIgnoreCase(".vanished")) {
				String v = getVanishedString();
				botmap.get(null).sendPrivmsg(adminchannel, v);
				
			} else if (message.startsWith(">>") && message.length() > 2 && lasthelpme != null) {
				AnjoPermissionsHandler aph = groupmanager.getWorldsHolder().getWorldPermissions(lasthelpme);
				String playername = lasthelpme.getName();
				String groupname = aph.getGroup(playername);
				String groupcolour = ChatColor.translateAlternateColorCodes('&', aph.getGroupPrefix(groupname));
				String formattedchat = 	ChatColor.GRAY + "[" + ChatColor.DARK_AQUA + "IRC" + ChatColor.GRAY + "][" +
						ChatColor.RED + ChatColor.ITALIC + "Staff" + ChatColor.RESET + ChatColor.GRAY + " -> " +
						groupcolour + ChatColor.ITALIC + playername + ChatColor.RESET + ChatColor.GRAY + "] " + 
						ChatColor.WHITE + message.substring(2);
				lasthelpme.sendMessage(formattedchat);
			
			} else if (message.startsWith(">") && firstwhitespace > 0) {
				String playername = message.substring(1, firstwhitespace);
				Player player = getServer().getPlayer(playername);
				if (player != null) {
					AnjoPermissionsHandler aph = groupmanager.getWorldsHolder().getWorldPermissions(player);
					String groupname = aph.getGroup(playername);
					String groupcolour = ChatColor.translateAlternateColorCodes('&', aph.getGroupPrefix(groupname));
					String formattedchat = 	ChatColor.GRAY + "[" + ChatColor.DARK_AQUA + "IRC" + ChatColor.GRAY + "][" +
							ChatColor.RED + ChatColor.ITALIC + "Staff" + ChatColor.RESET + ChatColor.GRAY + " -> " +
							groupcolour + ChatColor.ITALIC + playername + ChatColor.RESET + ChatColor.GRAY + "] " + 
							ChatColor.WHITE + message.substring(firstwhitespace + 1);
					player.sendMessage(formattedchat);
				}
				
			} else {
				toIngameAdmins(sender, message);
			}
		}
		
	}

	private void onPlayerChat(Player player, String message) {
		// prepare string to send to IRC
		AnjoPermissionsHandler aph = groupmanager.getWorldsHolder().getWorldPermissions(player);
		String playername = player.getName();
		String groupname = aph.getGroup(playername);
		String irccolour = getIRCColourCode(aph.getGroupPrefix(groupname));
		String formattedchat = "\u000315,0" + groupname + "\u0003 \u0003" + irccolour + playername + "\u0003\u000315:\u0003 " + message;
		
		botmap.get(player.getWorld()).sendPrivmsg(relaychannel, formattedchat);
	}
	
	private void onPlayerJoin(Player player) {
		AnjoPermissionsHandler aph = groupmanager.getWorldsHolder().getWorldPermissions(player);
		String playername = player.getName();
		String groupname = aph.getGroup(playername);
		String irccolour = getIRCColourCode(aph.getGroupPrefix(groupname));
		String formattedchat = "\u000315,3" + groupname + "\u0003 \u0003" + irccolour + playername + "\u0003 joined the game";
		
		botmap.get(player.getWorld()).sendPrivmsg(relaychannel, formattedchat);
	}
	
	private void onPlayerQuit(Player player) {
		AnjoPermissionsHandler aph = groupmanager.getWorldsHolder().getWorldPermissions(player);
		String playername = player.getName();
		String groupname = aph.getGroup(playername);
		String irccolour = getIRCColourCode(aph.getGroupPrefix(groupname));
		String formattedchat = "\u000315,5" + groupname + "\u0003 \u0003" + irccolour + playername + "\u0003 left the game";
		
		botmap.get(player.getWorld()).sendPrivmsg(relaychannel, formattedchat);
	}
	
	/**
	 * Sends a message to the admin channel
	 * @param s The string to send
	 */
	public void toAdminChan(String s) {
		botmap.get(null).sendPrivmsg(adminchannel, s);
	}
	
	/**
	 * Sends a message to the event channel
	 * @param s The string to send
	 */
	public void toEventChan(String s) {
		botmap.get(null).sendPrivmsg(eventchannel, s);
	}

	private void onHelpMe(Player player, String message) {
		// some preprocessing
		lasthelpme = player;
		AnjoPermissionsHandler aph = groupmanager.getWorldsHolder().getWorldPermissions(player);
		String playername = player.getName();
		String groupname = aph.getGroup(playername);
		String groupcolour = ChatColor.translateAlternateColorCodes('&', aph.getGroupPrefix(groupname));
		String irccolour = getIRCColourCode(groupcolour);
		
		// prepare ingame message
		String formattedchat = 	ChatColor.GRAY + "[" + ChatColor.DARK_AQUA + "IRC" + ChatColor.GRAY + "][" +
				groupcolour + ChatColor.ITALIC + playername + ChatColor.RESET + ChatColor.GRAY + " -> " + 
				ChatColor.RED + ChatColor.ITALIC + "Staff" + ChatColor.RESET + ChatColor.GRAY + "] " +
				ChatColor.WHITE + message;
		
		// send to that sender
		player.sendMessage(formattedchat);
		
		// send to ingame admins
		for (Player p : getServer().getOnlinePlayers()) {
			if (p.hasPermission("botmaster.admin") && !p.equals(player)) {
				p.sendMessage(formattedchat);
			}
		}
		
		// prepare IRC message
		formattedchat = "\u000315,14[\u0003" + irccolour + ",14" + playername + "\u000315,14 -> #admin]\u0003 " + message;
		
		// send to irc admins
		botmap.get(player.getWorld()).sendPrivmsg(adminchannel, formattedchat);
	}
	
	private void onRolePlayingAction(Player player, String message) {
		// some preprocessing
		AnjoPermissionsHandler aph = groupmanager.getWorldsHolder().getWorldPermissions(player);
		String playername = player.getName();
		String groupname = aph.getGroup(playername);
		String groupcolour = ChatColor.translateAlternateColorCodes('&', aph.getGroupPrefix(groupname));
		String irccolour = getIRCColourCode(groupcolour);
		
		// prepare the ingame message
		String formattedchat = 	ChatColor.GRAY + "*" + ChatColor.WHITE + " " + groupcolour +
				ChatColor.ITALIC + playername + ChatColor.WHITE + " " + ChatColor.ITALIC + message;
		
		// send to ingame players
		getServer().broadcastMessage(formattedchat);
		
		// prepare IRC message
		formattedchat = "\u0003" + irccolour + playername + "\u0003 " + message;
		botmap.get(player.getWorld()).sendPrivmsg(relaychannel, formattedchat);
		
	}
	
	private String argsToString(String[] args) {
		String r = args[0];
		for (short i = 1; i < args.length; i ++) {
			r += " " + args[i];
		}
		return r;
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String commandlabel, String[] args) {
		
		if ((sender instanceof Player)) {
			Player player = (Player) sender;
			
			if (cmd.getName().equalsIgnoreCase("ac")) {
				if (player.hasPermission("botmaster.admin")) {
					if (args.length == 0) {
						// TODO /ac toggle
					} else {
						String message = argsToString(args);
						
						AnjoPermissionsHandler aph = groupmanager.getWorldsHolder().getWorldPermissions(player);
						String playername = player.getName();
						String groupname = aph.getGroup(playername);
						String irccolour = getIRCColourCode(aph.getGroupPrefix(groupname));
						String formattedchat = "\u000315,0" + groupname +"\u0003 \u0003" + irccolour + playername + "\u0003\u000315:\u0003 " + message;
						botmap.get(player.getWorld()).sendPrivmsg(adminchannel, formattedchat);
						
						toIngameAdmins(playername, message);
						return true;
					}
				} else {
					player.sendMessage("Permission denied: /ac is not available to you");
					return true;
				}
			
			} else if (cmd.getName().equalsIgnoreCase("helpme")) {
				if (args.length > 0) {
					onHelpMe(player, argsToString(args));
					return true;
				}
			
			} else if (cmd.getName().equalsIgnoreCase("me")) {
				if (args.length > 0) {
					onRolePlayingAction(player, argsToString(args));
					return true;
				}
			}
			
		}
		
		return false;
	}

	@SuppressWarnings("unused")
	private class BukkitEvent implements Listener {
		
		@EventHandler(priority = EventPriority.NORMAL)
		public void onEvent(PlayerJoinEvent e) {
			Player p = e.getPlayer();
			World w = p.getWorld();
			Location l = p.getLocation();
			toEventChan("PlayerJoin " + p.getName() + " " + p.getEntityId() + " " + w.getName() + " (" + l.getX() + "," + l.getY() + "," + l.getZ() + ")");
			if (!isVanished(p)) onPlayerJoin(p);
		}
		
		@EventHandler(priority = EventPriority.NORMAL)
		public void onEvent(PlayerQuitEvent e) {
			Player p = e.getPlayer();
			World w = p.getWorld();
			Location l = p.getLocation();
			toEventChan("PlayerQuit " + p.getName() + " " + p.getEntityId() + " " + w.getName() + " (" + l.getX() + "," + l.getY() + "," + l.getZ() + ")");
			if (!isVanished(p)) onPlayerQuit(p);
		}
		
		@EventHandler(priority = EventPriority.NORMAL)
		public void onEvent(AsyncPlayerPreLoginEvent e) {
			InetAddress a = e.getAddress();
			toEventChan("PlayerPreLogin " + e.getName() + " [" + a.getCanonicalHostName() + "/" + a.getHostAddress() + "]");
		}
		
		@EventHandler(priority = EventPriority.HIGHEST)
		public void onEvent(AsyncPlayerChatEvent e) {
			onPlayerChat(e.getPlayer(), e.getMessage());
		}
		
		@EventHandler(priority = EventPriority.NORMAL)
		public void onEvent(VanishStatusChangeEvent e) {
			toEventChan("VanishStatusChange " + e.getName() + " " + e.isVanishing());
		}
		
	}

}
