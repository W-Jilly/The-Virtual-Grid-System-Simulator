package gridscheduler;

import java.rmi.RMISecurityManager;

public class GridScheduler {

	// The number of GS required to start the algorithm.
	static final int expectedNetworkSize = 1;
	
	// NOTE: In the beginning start the registry with the rmiregistry command.		
	public static void main (String args[]) throws Exception{
		// 1. Before starting the nodes, manually create the registry with the rmiregistry command.
		// 2. Connect to the registry. Call getRegistry method of the NodeImplementation class on port registryPort.
		// 3. Register in the registry, notify the others that you've joined the network.
		// 4. If there are enough nodes have joined then start the algorithm.
		
		String myURL = args[0];
		int registryPort = Integer.parseInt(args[1]);
		int nodePort = Integer.parseInt(args[2]);
		String upstreamURL = args[3];
		String downstreamURL = args[4];
//		System.out.println(registryPort + "" + nodePort);
		// Create and install a security manager
		if (System.getSecurityManager() == null){
			System.setSecurityManager(new RMISecurityManager());
		}
		GSImplementation node = new GSImplementation(registryPort, nodePort, upstreamURL, downstreamURL, expectedNetworkSize, myURL);
		node.notifyOthers();
		System.out.println("Waiting for the incoming messages...");
	}
}
