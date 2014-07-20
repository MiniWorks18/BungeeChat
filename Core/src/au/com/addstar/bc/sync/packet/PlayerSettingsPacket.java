package au.com.addstar.bc.sync.packet;

import java.util.UUID;

import au.com.addstar.bc.sync.Packet;
import au.com.addstar.bc.sync.PacketSchema;

public class PlayerSettingsPacket extends Packet
{
	public static final PacketSchema schema = PacketSchema.from("id=UUID,nickname=String,lmt=UUID,socialspy=Byte,msgtoggle=Boolean,mute=Long");
	
	public PlayerSettingsPacket(UUID id, String nickname, UUID lastMessageTarget, int socialSpyState, boolean msgToggle, long muteTime)
	{
		super(id, nickname, lastMessageTarget, (byte)socialSpyState, msgToggle, muteTime);
	}
	
	public UUID getID()
	{
		return (UUID)getData(0);
	}
	
	public String getNickname()
	{
		return (String)getData(1);
	}
	
	public UUID getLastMessageTarget()
	{
		return (UUID)getData(2);
	}
	
	public int getSocialSpyState()
	{
		return ((Byte)getData(3)).intValue();
	}
	
	public boolean getMsgToggle()
	{
		return (Boolean)getData(4);
	}
	
	public long getMuteTime()
	{
		return (Long)getData(5);
	}
}
