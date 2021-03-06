package au.com.addstar.bc.commands;

import au.com.addstar.bc.BungeeChat;
import au.com.addstar.bc.objects.ChatChannel;
import au.com.addstar.bc.utils.Utilities;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Created for the AddstarMC
 * Created by Narimm on 9/08/2017.
 */
public class SubscribeCommand implements CommandExecutor {
    private static BungeeChat instance;
    public SubscribeCommand(BungeeChat plugin) {
        instance = plugin;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String label, String[] args) {
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage("Cannot Execute from Console: " + command.getName());
            return true;
        }
        Player player = (Player) commandSender;
        if (args.length == 0) {
            player.sendMessage(Utilities.colorize("&3Usage: &6/chat <channel>"));
            return false;
        } else {
            String channelName = args[0];
            if (channelName.equalsIgnoreCase("global")) {
                String subscribed = BungeeChat.getPlayerManager().getDefaultChatChannel(player);
                if (subscribed != null  && instance.getChatChannelsManager().hasChannel(subscribed)) {
                    String perm = instance.getChatChannelsManager().getChannelSpeakPerm(subscribed);
                    BungeeChat.getPlayerManager().unsubscribeAll(player);
                    if (!player.hasPermission(perm)) {
                        player.sendMessage(Utilities.colorize("&3Unsubscribed from &a" + subscribed));
                    } else {
                        player.sendMessage(Utilities.colorize("&cCould not unsubscribe from &a" + subscribed));
                    }
                } else {
                    BungeeChat.getPlayerManager().unsubscribeAll(player);
                    player.sendMessage(Utilities.colorize("&3You are not subscribed to any channels."));
                }
                return true;
            }
            if (commandSender.hasPermission("bungeechat.subscribe." + channelName)) {
                String prefix = BungeeChat.getPlayerManager().getPlayerSettings(player).chatName;
                final ChatChannel chatChannel = instance.getChatChannelsManager().getChatChannel(channelName);
                if (chatChannel != null) {
                    String perm = chatChannel.permission;
                    if (perm == null) {
                        instance.getLogger().warning("The speak permission for " + chatChannel.name + " is null");
                        player.sendMessage(Utilities.colorize("&cError with Channel permission please contact admin"));
                        return false;
                    }
                    BungeeChat.getPlayerManager().unsubscribeAll(player);
                    if (chatChannel.subscribe && !player.hasPermission(perm)) {
                        BungeeChat.permissionManager.playerAdd(null,player, perm);
                        player.recalculatePermissions();
                        if (player.hasPermission(perm)) {
                            BungeeChat.getPlayerManager().setDefaultChannel(player, chatChannel.name);
                            player.sendMessage(Utilities.colorize("&3You have subscribed to &a" + chatChannel.name));
                            player.sendMessage(Utilities.colorize("&3Your chat will default to this channel."));
                            player.sendMessage(Utilities.colorize("&3Use &6! &3before your message to send to public chat"));
                            player.sendMessage(Utilities.colorize("&3Use &6/chat global &3to leave &a" + chatChannel.name + " &3chat"));
                            if (instance.getChatChannelsManager().isRolePlay(channelName)) {
                                if (prefix != null) {
                                    player.sendMessage(Utilities.colorize("&3Your roleplay prefix is &b" + prefix));
                                    player.sendMessage(Utilities.colorize("&3Set your roleplay name using &6/chatname <RolePlayName>"));
                                }
                            }
                        }
                    } else {
                        if (!instance.getChatChannelsManager().isSubscribable(channelName)) {
                            player.sendMessage(Utilities.colorize("&cThat channel is either not available or you do not have permission."));
                            return false;
                        }
                        if (commandSender.hasPermission(perm)) {
                            player.sendMessage(Utilities.colorize("&3You are already subscribed to &a" + channelName + " &3 with perm &a" + perm));
                            BungeeChat.getPlayerManager().setDefaultChannel(player, channelName);
                            return true;
                        }
                    }
                    return true;
                } else {
                    player.sendMessage(Utilities.colorize("&cChannel &a" + channelName + " &cdoes not exist."));
                    return false;
                }
            } else {
                player.sendMessage(Utilities.colorize("&cNo Permission for that command"));
                return false;
            }
        }
    }
}
