package gridscheduler;

import java.rmi.Remote;

import message.ControlMessage;


public interface GSInterface extends Remote {
	public void onMessageReceived(ControlMessage msg) throws Exception;
	public void newGSJoined() throws Exception;
	
}
