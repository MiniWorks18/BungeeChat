package au.com.addstar.bc;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.milkbowl.vault.permission.Permission;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import au.com.addstar.bc.utils.Utilities;

public class BungeeChat extends JavaPlugin implements PluginMessageListener, Listener
{
	static Permission permissionManager;
	
	public static String serverName = "ERROR";
	private static BungeeChat mInstance;
	
	private boolean mHasRequestedUpdate = false;
	private boolean mHasUpdated = false;
	
	private HashMap<String, ChatChannel> mChannels = new HashMap<String, ChatChannel>();
	private ArrayList<String> mAllPlayers = new ArrayList<String>();
	private HashMap<CommandSender, String> mLastMsgTarget = new HashMap<CommandSender, String>();
	private HashMap<CommandSender, Boolean> mSocialSpyOn = new HashMap<CommandSender, Boolean>();
	private HashSet<String> mSocialSpyKeywords = new HashSet<String>();
	
	@Override
	public void onEnable()
	{
		mInstance = this;
		
		RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(Permission.class);
		if (permissionProvider != null)
			permissionManager = permissionProvider.getProvider();
		else
			permissionManager = null;
		
		Bukkit.getPluginManager().registerEvents(new ChatHandler(), this);
		Bukkit.getMessenger().registerIncomingPluginChannel(this, "BungeeChat", this);
		Bukkit.getMessenger().registerOutgoingPluginChannel(this, "BungeeChat");
		
		Bukkit.getPluginManager().registerEvents(this, this);
		
		requestUpdate();
		
		MessageCommand cmd = new MessageCommand();
		
		getCommand("msg").setExecutor(cmd);
		getCommand("msg").setTabCompleter(cmd);
		getCommand("reply").setExecutor(cmd);
		getCommand("reply").setTabCompleter(cmd);
	}
	
	@Override
	public void onDisable()
	{
		for(ChatChannel channel : mChannels.values())
			channel.unregisterChannel();
	}
	
	@Override
	public boolean onCommand( CommandSender sender, Command command, String label, String[] args )
	{
		if(command.getName().equals("socialspy"))
		{
			if(!(sender instanceof Player))
				return false;
			
			Boolean on = mSocialSpyOn.get(sender);
			if(on == null)
				on = true;
			else
				on = !on;
			
			if(on)
				sender.sendMessage(ChatColor.GREEN + "SocialSpy now on");
			else
				sender.sendMessage(ChatColor.GREEN + "SocialSpy now off");
			
			mSocialSpyOn.put(sender, on);
			new MessageOutput("BungeeChat", "SocialSpy")
				.writeUTF(sender.getName())
				.writeBoolean(on)
				.send(this);
			
			return true;
		}
		
		return false;
	}
	
	private void requestUpdate()
	{
		Player player = null;
		
		if(Bukkit.getOnlinePlayers().length > 0)
			player = Bukkit.getOnlinePlayers()[0];
		else
			return;
		
		new MessageOutput("BungeeChat", "Update").send(player, mInstance);
		
		mHasRequestedUpdate = true;
	}
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	private void onPlayerJoin(PlayerJoinEvent event)
	{
		String target = mLastMsgTarget.remove(new RemotePlayer(event.getPlayer().getName()));
		if(target != null)
			mLastMsgTarget.put(event.getPlayer(), target);
		
		if(!mHasRequestedUpdate || !mHasUpdated)
		{
			Bukkit.getScheduler().runTaskLater(this, new Runnable()
			{
				@Override
				public void run()
				{
					requestUpdate();
				}
			}, 2);
		}
	}
	
	private boolean processCommands(CommandSender sender, String message)
	{
		String command;
		int pos = message.indexOf(' ');
		if(pos == -1)
		{
			command = message;
			message = null;
		}
		else
		{
			command = message.substring(0, pos);
			message = message.substring(pos+1);
		}
		
		for(ChatChannel channel : mChannels.values())
		{
			if(channel.command.equals(command))
			{
				if(channel.permission != null && !sender.hasPermission(channel.permission))
					break;
				
				if(message != null)
					channel.say(sender, message);
				
				return true;
			}
		}
		
		return false;
	}
	
	@EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
	private void onPlayerCommand(PlayerCommandPreprocessEvent event)
	{
		String command = event.getMessage().split(" ")[0];
		if(mSocialSpyKeywords.contains(command.toLowerCase()))
		{
			Utilities.broadcast(event.getMessage(), "bungeechat.socialspy", event.getPlayer(), Utilities.SOCIAL_SPY_ENABLED);
			mirrorChat(event.getMessage(), "~SS");
		}
		
		if(processCommands(event.getPlayer(), event.getMessage().substring(1)))
		{
			event.setMessage("/nullcmd");
			event.setCancelled(true);
		}
	}
	
	@EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
	private void onServerCommand(ServerCommandEvent event)
	{
		if(processCommands(event.getSender(), event.getCommand()))
		{
			event.setCommand("/nullcmd");
		}
	}
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	private void onPlayerDC(PlayerQuitEvent event)
	{
		String target = mLastMsgTarget.remove(event.getPlayer());
		if(target != null)
			mLastMsgTarget.put(new RemotePlayer(event.getPlayer().getName()), target);
	}
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	private void onPlayerDC(PlayerKickEvent event)
	{
		String target = mLastMsgTarget.remove(event.getPlayer());
		if(target != null)
			mLastMsgTarget.put(new RemotePlayer(event.getPlayer().getName()), target);
	}
	
	public static void sendMessage(RemotePlayer player, String message)
	{
		new MessageOutput("BungeeChat", "Send")
			.writeUTF(player.getName())
			.writeUTF(message)
			.send(mInstance);
	}
	
	public static void mirrorChat(String fullChat, String channel)
	{
		new MessageOutput("BungeeChat", "Mirror")
			.writeUTF(channel)
			.writeUTF(fullChat)
			.send(mInstance);
	}
	
	public static String getPrimaryGroup(Player player)
	{
		if(permissionManager == null)
			return null;
		
		try
		{
			return permissionManager.getPrimaryGroup(player);
		}
		catch(UnsupportedOperationException e)
		{
			return null;
		}
	}
	
	public static boolean isInGroup(Player player, String group)
	{
		if(permissionManager == null)
			return false;
		
		try
		{
			return permissionManager.playerInGroup(player, group);
		}
		catch(UnsupportedOperationException e)
		{
			return false;
		}
	}

	private void update(DataInputStream input) throws IOException
	{
		mHasRequestedUpdate = true;
		mHasUpdated = true;
		
		serverName = input.readUTF();
		String consoleName = input.readUTF();
		if(consoleName.isEmpty())
			Formatter.consoleOverride = null;
		else
			Formatter.consoleOverride = ChatColor.translateAlternateColorCodes('&', consoleName);
		
		Formatter.mPMFormatInbound = ChatColor.translateAlternateColorCodes('&', input.readUTF());
		Formatter.mPMFormatOutbound = ChatColor.translateAlternateColorCodes('&', input.readUTF());
		
		Formatter.permissionLevels.clear();
		
		int count = input.readShort();
		for(int i = 0; i < count; ++i)
			Formatter.permissionLevels.add(new PermissionSetting(input.readUTF(), input.readShort(), input.readUTF(), input.readUTF()));
		
		Collections.sort(Formatter.permissionLevels);
		
		for(ChatChannel channel : mChannels.values())
			channel.unregisterChannel();
		
		mChannels.clear();
		
		count = input.readShort();
		for(int i = 0; i < count; ++i)
		{
			ChatChannel channel = new ChatChannel(input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF());
			channel.registerChannel();
			mChannels.put(channel.name, channel);
		}
		
		Formatter.keywordsEnabled = input.readBoolean();
		if(Formatter.keywordsEnabled)
		{
			Formatter.keywordPerm = input.readUTF();
			Formatter.keywordEnabledChannels.clear();
			Formatter.keywordPatterns.clear();
			
			count = input.readShort();
			for(int i = 0; i < count; ++i)
				Formatter.keywordEnabledChannels.add(input.readUTF());
			
			count = input.readShort();
			for(int i = 0; i < count; ++i)
			{
				try
				{
					Pattern pattern = Pattern.compile(input.readUTF(), Pattern.CASE_INSENSITIVE);
					Formatter.keywordPatterns.put(pattern, input.readUTF());
				}
				catch (PatternSyntaxException e)
				{
					// Cant happen
				}
			}
			
			try
			{
				Bukkit.getPluginManager().addPermission(new org.bukkit.permissions.Permission(Formatter.keywordPerm, PermissionDefault.OP));
			}
			catch(IllegalArgumentException e)
			{
			}
		}
		
		mSocialSpyKeywords.clear();
		count = input.readShort();
		for(int i = 0; i < count; ++i)
			mSocialSpyKeywords.add(input.readUTF());
	}

	@Override
	public void onPluginMessageReceived( String channel, Player sender, byte[] data )
	{
		ByteArrayInputStream stream = new ByteArrayInputStream(data);
		DataInputStream input = new DataInputStream(stream);
		try
		{
			String subChannel = input.readUTF();
			if(channel.equals("BungeeChat"))
			{
				if(subChannel.equals("Mirror"))
				{
					String chatChannel = input.readUTF();
					String message = input.readUTF();
					
					if(chatChannel.isEmpty())
						Formatter.broadcastChat(message);
					else if(chatChannel.startsWith("~"))
					{
						if(chatChannel.equals("~"))
						{
							if(Formatter.keywordsEnabled)
								Bukkit.broadcast(message, Formatter.keywordPerm);
						}
						else if(chatChannel.equals("~SS"))
						{
							Utilities.broadcast(message, "bungeechat.socialspy", Utilities.SOCIAL_SPY_ENABLED);
						}
					}
					else
					{
						ChatChannel channelObj = mChannels.get(chatChannel);
						if(channelObj != null)
						{
							if(channelObj.listenPermission != null)
								Bukkit.broadcast(message, channelObj.listenPermission);
							else
								Bukkit.broadcastMessage(message);
						}
					}
				}
				else if(subChannel.equals("Update"))
				{
					update(input);
				}
				else if(subChannel.equals("UpdatePlayers"))
				{
					int count = input.readShort();
					mAllPlayers.clear();
					for(int i = 0; i < count; ++i)
						mAllPlayers.add(input.readUTF());
				}
				else if(subChannel.equals("Message"))
				{
					Player ply = Bukkit.getPlayerExact(input.readUTF());
					String message = input.readUTF();
					if(ply != null)
						ply.sendMessage(message);
				}
				else if(subChannel.equals("MsgTarget"))
				{
					String playerName = input.readUTF();
					String target = input.readUTF();
					
					mLastMsgTarget.put(getPlayerExact(playerName), target);
				}
				else if(subChannel.equals("SocialSpy"))
				{
					String playerName = input.readUTF();
					int type = input.readByte();
					CommandSender player = Bukkit.getPlayerExact(playerName);
					
					if(player != null)
					{
						if(type == 2)
							mSocialSpyOn.remove(player);
						else if(type == 0)
							mSocialSpyOn.put(player, false);
						else
							mSocialSpyOn.put(player, true);
					}
				}
			}
		}
		catch(IOException e) 
		{
		}
	}
	
	public static String colorize(String message, CommandSender sender)
	{
		int pos = -1;
		char colorChar = '&';
		
		StringBuffer buffer = new StringBuffer(message);
		
		boolean hasColor = sender.hasPermission("bungeechat.color");
		boolean hasReset = sender.hasPermission("bungeechat.format.reset");
		boolean hasBold = sender.hasPermission("bungeechat.format.bold");
		boolean hasItalic = sender.hasPermission("bungeechat.format.italic");
		boolean hasUnderline = sender.hasPermission("bungeechat.format.underline");
		boolean hasStrikethrough = sender.hasPermission("bungeechat.format.strikethrough");
		boolean hasMagic = sender.hasPermission("bungeechat.format.magic");
		
		while((pos = message.indexOf(colorChar, pos+1)) != -1)
		{
			if(message.length() > pos + 1)
			{
				char atPos = Character.toLowerCase(message.charAt(pos+1));
				
				boolean allow = false;
				if(((atPos >= '0' && atPos <= '9') || (atPos >= 'a' && atPos <= 'f')) && hasColor)
					allow = true;
				else if(atPos == 'r' && hasReset)
					allow = true;
				else if(atPos == 'l' && hasBold)
					allow = true;
				else if(atPos == 'm' && hasStrikethrough)
					allow = true;
				else if(atPos == 'n' && hasUnderline)
					allow = true;
				else if(atPos == 'o' && hasItalic)
					allow = true;
				else if(atPos == 'k' && hasMagic)
					allow = true;
				
				if(allow)
					buffer.setCharAt(pos, ChatColor.COLOR_CHAR);
			}
		}
		
		return buffer.toString();
	}
	
	public static List<String> matchPlayers(String player)
	{
		player = player.toLowerCase();
		
		HashSet<String> used = new HashSet<String>();
		for(Player p : Bukkit.matchPlayer(player))
			used.add(p.getName());

		for(String name : mInstance.mAllPlayers)
		{
			if(name.toLowerCase().startsWith(player))
				used.add(name);
		}
		
		return new ArrayList<String>(used);
	}
	
	public static CommandSender getPlayer(String name)
	{
		if(name.equalsIgnoreCase("console"))
			return Bukkit.getConsoleSender();
		
        String found = null;
        String lowerName = name.toLowerCase();
        int delta = Integer.MAX_VALUE;
        for (String player : mInstance.mAllPlayers) 
        {
            if (player.toLowerCase().startsWith(lowerName)) 
            {
                int curDelta = player.length() - lowerName.length();
                if (curDelta < delta) 
                {
                    found = player;
                    delta = curDelta;
                }
                if (curDelta == 0) 
                	break;
            }
        }
        
        for (Player player : Bukkit.getOnlinePlayers()) 
        {
            if (player.getName().toLowerCase().startsWith(lowerName)) 
            {
                int curDelta = player.getName().length() - lowerName.length();
                if (curDelta < delta) 
                {
                    found = player.getName();
                    delta = curDelta;
                }
                if (curDelta == 0) 
                	break;
            }
        }
        
        if(found == null)
        	return null;
        
        Player player = Bukkit.getPlayerExact(found);
        
        if(player != null)
        	return player;
        
        return new RemotePlayer(found);
	}
	
	public static CommandSender getPlayerExact(String name)
	{
		if(name.equalsIgnoreCase("console"))
			return Bukkit.getConsoleSender();
		
		CommandSender player = Bukkit.getPlayerExact(name);
		if(player != null)
			return player;
		
		for (String ply : mInstance.mAllPlayers) 
        {
            if (ply.equalsIgnoreCase(name)) 
            	return new RemotePlayer(ply);
        }
		
		return null;
	}
	
	
	public static void setLastMsgTarget(CommandSender sender, CommandSender target)
	{
		mInstance.mLastMsgTarget.put(sender, target.getName());
		
		if(!(sender instanceof Player) && !(sender instanceof RemotePlayer))
			return;
		
		new MessageOutput("BungeeChat", "MsgTarget")
			.writeUTF(sender.getName())
			.writeUTF(target.getName())
			.send(mInstance);
	}
	
	public static CommandSender getLastMsgTarget(CommandSender sender)
	{
		String target = mInstance.mLastMsgTarget.get(sender);
		if(target == null)
			return null;
		
		return getPlayerExact(target);
	}

	public static boolean isSocialSpyEnabled( CommandSender object )
	{
		if(mInstance.mSocialSpyOn.containsKey(object))
			return mInstance.mSocialSpyOn.get(object);
		
		return false;
	}
}
