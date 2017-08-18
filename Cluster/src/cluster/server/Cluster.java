package cluster.server;

import java.rmi.RMISecurityManager;

public class Cluster {
	// The number of clusters which is required to start the algorithm.
	static final int expectedNetworkSize = 1;
	static final int nodeCount = 50;
	
	// NOTE: In the beginning start the registry with the rmiregistry command.		
	public static void main (String args[]) throws Exception{
		// 1. Before starting the clusters, manually create the registry with the rmiregistry command.
		// 2. Connect to the registry. Call getRegistry method of the clusterImplementation class on port registryPort.
		// 3. Register in the registry, notify the others that you've joined the network.
		// 4. If there are enough clusters have joined then start the algorithm.
		
		String myURL = args[0];
		int registryPort = Integer.parseInt(args[1]);
		int clusterPort = Integer.parseInt(args[2]);
		int jobCount = Integer.parseInt(args[3]);
		String gridSchedulerURL = args[4];
		// Create and install a security manager
		if (System.getSecurityManager() == null){
			System.setSecurityManager(new RMISecurityManager());
		}
		ClusterImplementation cluster = new ClusterImplementation(registryPort, clusterPort, gridSchedulerURL, nodeCount, jobCount, expectedNetworkSize, myURL);
		cluster.notifyOthers();
		System.out.println("Waiting for the incoming messages...");
	}
}
