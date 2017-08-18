package message;

import java.net.InetSocketAddress;

public interface IMessageReceivedHandler {
	public void onMessageReceived(Message message);
	//public void onConnectExceptionThrown(Message message, InetSocketAddress destinationAddress, boolean requiresRepsonse);
	//public void onWriteExceptionThrown(Message message, InetSocketAddress destinationAddress, boolean requiresRepsonse);
	//public void onReadExceptionThrown(Message message, InetSocketAddress destinationAddress);
	
}
