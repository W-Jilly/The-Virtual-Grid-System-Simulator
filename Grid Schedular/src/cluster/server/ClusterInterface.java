package cluster.server;

import java.rmi.Remote;

import message.ControlMessage;

public interface ClusterInterface extends Remote {
	public void onMessageReceived(ControlMessage message) throws Exception;
	public void newClusterJoined() throws Exception;
	
}
