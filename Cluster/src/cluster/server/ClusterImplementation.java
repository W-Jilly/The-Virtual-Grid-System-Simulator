package cluster.server;


import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.Naming;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import cluster.Job;
import cluster.Node;
import cluster.NodeStatus;
import gridscheduler.GSInterface;
import message.ControlMessage;
import message.ControlMessageType;
import message.Message;
import java.util.Date;
import gui.ClusterPanel;


public class ClusterImplementation extends UnicastRemoteObject implements ClusterInterface {
	private static final long serialVersionUID = 1L;
	private int clusterPort;
	private int expectedNetworkSize;
	public Registry registry;
	int clustersJoined;
	private List <Node> nodes;
	private ResourceManager resourceManager;
	private String gridSchedulerURL = null;
	private long TimeStamp;
	
	// polling frequency, 10hz
	private long pollSleep = 100;
	private int TIMEOUT = 1500;
	
	private boolean running;
	private boolean isGSAlive = false;
	private String IP;
	public String localURL;
	private int jobCount;
	private long jobFinished = 0;
	private long failTime;
	private ClusterPanel ClusterPanel;
	private PrintWriter writer;
	
	
	protected ClusterImplementation(int registryPort, int clusterPort, String gridSchedulerURL, int nodeCount, int jobCount, int expectedNetworkSize, String myURL) throws NotBoundException, Exception {
		super();
		// Preconditions
		assert(gridSchedulerURL != null) : "parameter 'gridSchedulerURL' cannot be null";
		this.IP = myURL;
		System.out.println(IP);
		this.jobCount = jobCount;
		this.nodes = new ArrayList<Node>(nodeCount);
		this.clusterPort = clusterPort;
		this.writer = new PrintWriter("log"+clusterPort+".dat", "UTF-8");
		this.expectedNetworkSize = expectedNetworkSize;
		this.registry = LocateRegistry.getRegistry(registryPort);
		this.gridSchedulerURL = gridSchedulerURL;
		// Registration in the registry.
		try {
			this.registry.bind(Integer.toString(this.clusterPort), this);
		} catch (AlreadyBoundException e) {
			System.out.println("Daboom: " + e);
		}
		this.localURL = this.IP + ":" + Integer.toString(registryPort) 
						+  "/" +Integer.toString(this.clusterPort);
		System.out.println(localURL);
		// Initialize the resource manager for this cluster
		resourceManager = new ResourceManager(this);
		resourceManager.connectToGridScheduler(gridSchedulerURL);
		resourceManager.startReplicateToGrid();
		
		// Initialize the nodes 
		for (int i = 0; i < nodeCount; i++) {
			Node n = new Node();		
			// Make nodes report their status to the resource manager
			n.addNodeEventHandler(resourceManager);
			nodes.add(n);
		}
				
		// Start the polling thread
		running = true;
		this.ClusterPanel = new ClusterPanel(this);
		this.ClusterPanel.start();
	}
	
	// Notify other nodes that you have joined the network.
	public void notifyOthers() {		
		try
		{
			ClusterInterface remoteRM = getRemoteNode(Integer.toString(clusterPort));
			remoteRM.newClusterJoined();

		} catch (Exception e) {
			System.out.println("Notify !!! Kaboom: " + e);
		}
	}
	
	private void startAlgorithm() {
		// The test algorithm does the following:
		// 1. Gets the list of all the registered nodes.
		// 2. Iterates through the list and sends a 'Hello' message to each node.
		this.TimeStamp = new Date().getTime();
		
		
		new Thread(new Runnable(){
			public void run() {
					runJobs();
			}	
		}).start();
		
		
		new Thread(new Runnable() {
		    public void run() {
		    	long initialTime = System.currentTimeMillis();;
	    		try {
			    	// Send the messages until death.
			    	while (running) {
			    		long timeout = new Date().getTime() - TimeStamp;
			    		if (timeout > TIMEOUT && isGSAlive){
			    			failTime = System.currentTimeMillis();
			    			failureHandle();
			    			System.out.println("DownTime!!!!");
  			    			System.out.println(System.currentTimeMillis()- failTime);
			    		}
						// poll the nodes
						for (Node node : nodes)
							jobFinished = node.poll(jobFinished);
							writer.println(System.currentTimeMillis()-initialTime);
							writer.println(jobFinished);
							writer.println(resourceManager.jobQueue.size());
							writer.flush();
						// sleep
						try {
							Thread.sleep(pollSleep);
						} catch (InterruptedException ex) {
							assert(false) : "Cluster poll thread was interrupted";
						}	
					}
	    		}catch (Exception e) {
					System.out.println("Waboom: " + e);
				}
		    }
		}).start();
	}
	
	private ClusterInterface getRemoteNode(String nodeStringId) throws AccessException, RemoteException, NotBoundException {
		ClusterInterface remoteNode = (ClusterInterface) this.registry.lookup(nodeStringId);
		return remoteNode;
	}
	
	private GSInterface getRemoteGS(String gsURL) throws MalformedURLException, RemoteException, NotBoundException {
		GSInterface remoteGS = (GSInterface) Naming.lookup(gsURL);
		return remoteGS;
	}
	
	public void newClusterJoined() {
		// Increase the counter of the nodes already in the network.
		clustersJoined++;
		// Start the algorithm if enough nodes have joined the network.
		if (clustersJoined  == expectedNetworkSize)
			startAlgorithm();
	}
	
	public void failureHandle(){
		resourceManager.setGSJoined(false);
		this.isGSAlive = false;
		gridSchedulerURL = resourceManager.getBackupURL();
	    ControlMessage msg = new ControlMessage(ControlMessageType.FailReport);
		msg.setSender(localURL);
		try{
			getRemoteGS(resourceManager.getBackupURL()).onMessageReceived(msg);	
		}catch(Exception e){
			System.out.println("ReConnect failure");	
		}
		resourceManager.connectToGridScheduler(resourceManager.getBackupURL());
		resourceManager.startReplicateToGrid();
//		try {
//			Thread.sleep(400);
//		} catch (InterruptedException ex) {
			
//		}	
	}
	
	public void onMessageReceived(ControlMessage message){
		assert(message instanceof ControlMessage) : "parameter 'message' should be of type ControlMessage";
		assert(message != null) : "parameter 'message' cannot be null";

		ControlMessage controlMessage = (ControlMessage)message;

		// succeed in joining grid scheduler
		if (controlMessage.getType() == ControlMessageType.JoinAck){
			resourceManager.setBackupURL(controlMessage.getUrl());
			resourceManager.setGSJoined(true);
			System.out.println("Grid Scheduler " + controlMessage.getSender() + " Acknowledges me");
		}
		
		// resource manager wants to offload a job to us 
		if (controlMessage.getType() == ControlMessageType.AddJob)
		{
			resourceManager.jobQueue.add(controlMessage.getJob());
			resourceManager.scheduleJobs();
			System.out.println("Grid " + controlMessage.getSender() + " Give Job To Me");
		}

		// resource manager wants to offload a job to us 
		if (controlMessage.getType() == ControlMessageType.RequestLoad)
		{
			ControlMessage replyMessage = new ControlMessage(ControlMessageType.ReplyLoad);
			resourceManager.scheduleJobs();
			replyMessage.setUrl(localURL);
			replyMessage.setLoad(resourceManager.jobQueue.size());
			try{
				getRemoteGS(gridSchedulerURL).onMessageReceived(replyMessage);	
			}catch(Exception e){
				System.out.println("Reply Load fails");
			}
			
			System.out.println("Grid " + controlMessage.getUrl() + " Request Load to Me; Load is " + replyMessage.getLoad());
		}
		
		// status of grid scheduler
		if (controlMessage.getType() == ControlMessageType.HeartBeat){
			isGSAlive = true;
            TimeStamp = new Date().getTime();
//			System.out.println("Grid Scheduler" + controlMessage.getSender() + "is Alive");
		}
	}
	
	/**
	 * Returns the number of nodes in this cluster. 
	 * @return the number of nodes in this cluster
	 */
	public int getNodeCount() {
		return nodes.size();
	}
	
	/**
	 * Returns the resource manager object for this cluster.
	 * @return the resource manager object for this cluster
	 */
	public ResourceManager getResourceManager() {
		return resourceManager;
	}

	/**
	 * Returns the clusterPort of the cluster
	 * @return the clusterPort of the cluster
	 */
	public int getClusterPort() {
		return clusterPort;
	}
	
	/**
	 * Returns the nodes inside the cluster as an array.
	 * @return an array of Node objects
	 */
	public List<Node> getNodes() {
		return nodes;
	}
	
	/**
	 * Finds a free node and returns it. If no free node can be found, the method returns null.
	 * @return a free Node object, or null if no such node can be found. 
	 */
	public Node getFreeNode() {
		// Find a free node among the nodes in our cluster
	    for (Node node : nodes)
			if (node.getStatus() == NodeStatus.Idle) return node;
		
		// if we haven't returned from the function here, we haven't found a suitable node
		// so we just return null
		return null;
	}
	
	/**
	 * Stops the polling thread. This must be called explicitly to make sure the program
	 * terminates cleanly.
	 * @throws Exception 
	 * @throws NotBoundException 
	 * @throws RemoteException 
	 * @throws AccessException 
	 */
/*	public void stopPollThread() {
		running = false;
		try {
			pollingThread.join();
		} catch (InterruptedException ex) {
			assert(false) : "Cluster stopPollThread was interrupted";
		}
		
	}*/
	
	public void runJobs() {
		long jobId = 0;
		// Do not stop the simulation as long as the gridscheduler panel remains open
		try {
			Thread.sleep(60000L);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		while (jobId <= jobCount-1) {
			try{
				// Add a new job to the system that take up random time
			    Job job = new Job(10000 + (int)(Math.random() * 5000), jobId++, System.currentTimeMillis());
			    this.getResourceManager().addJob(job);
			    // System.out.println("Create Job " + Long.toString(jobId));
			    try {
				    // Sleep a while before creating a new job
				    Thread.sleep(80L);
			    } catch (InterruptedException e) {
				    assert(false) : "Simulation runtread was interrupted";
			    }	
			}catch(Exception e){
				System.out.println("GS connection is broken");
			}
			
		}
	}
	
}
