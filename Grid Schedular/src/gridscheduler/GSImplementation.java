package gridscheduler;

import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.Naming;	
import java.net.MalformedURLException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.atomic.*;

import cluster.Job;
import cluster.Node;
import cluster.server.ClusterInterface;
import gui.GridSchedulerPanel;
import message.ControlMessage;
import message.ControlMessageType;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GSImplementation  extends UnicastRemoteObject implements GSInterface, Runnable{

	private static final long serialVersionUID = 1L;
	private int nodePort;
	private int expectedNetworkSize;
	private String upstreamURL;
	private String downstreamURL;
	private String offloadURL;	
	private Registry registry;
	private AtomicInteger GSJoined = new AtomicInteger(0);
	// job queue
	private ConcurrentLinkedQueue<Job> jobQueue;
	private int currentLoad;
	GSInterface remoteGS;
	private ConcurrentLinkedQueue <String> connectedRM;
	
	// a hashmap linking each resource manager to an estimated load
	private ConcurrentHashMap<String, Integer> resourceManagerLoad;
	private ConcurrentHashMap<String, Queue<Job>> clusterQueueReplication;

	// polling frequency, 1hz
	private long pollSleep = 1000;
	public static final int MAX_JOBQUEUE_SIZE = 4; 
	private long failTime;
	
	// polling thread
	private Thread pollingThread;
	private boolean running;
	private String IP;
	private String localURL;
	private GridSchedulerPanel gsPanel;
	
	protected GSImplementation(int registryPort, int nodePort, String upstreamURL, String downstreamURL, int expectedNetworkSize, String myURL) throws RemoteException {
		super();
		this.nodePort = nodePort;
		this.IP = myURL;
		this.localURL = this.IP + ":" + Integer.toString(registryPort) 
		+  "/" +Integer.toString(this.nodePort);
		this.expectedNetworkSize = expectedNetworkSize;
		this.registry = LocateRegistry.getRegistry(registryPort);
		this.downstreamURL = downstreamURL;
		this.upstreamURL = upstreamURL;
		this.connectedRM = new ConcurrentLinkedQueue<String>();  
		// Registration in the registry.
		try {
			this.registry.bind(Integer.toString(this.nodePort), this);
		} catch (AlreadyBoundException e) {
			System.out.println("Daboom: " + e);
		}
		this.offloadURL = localURL;
		this.resourceManagerLoad = new ConcurrentHashMap<String, Integer>();
		this.clusterQueueReplication = new ConcurrentHashMap<String, Queue<Job>>();
		this.jobQueue = new ConcurrentLinkedQueue<Job>();
		running = true;
		this.gsPanel = new GridSchedulerPanel(this);
		this.gsPanel.start();
//		pollingThread = new Thread(this);
//	    pollingThread.start();
	}
	
	// Notify other nodes that you have joined the network.
	public void notifyOthers() {		
		try
		{
			// Inform the other nodes that you have joined the network by calling their newNodeJoined remote method.
			try{
				remoteGS = getRemoteGS(upstreamURL);
				
			}catch(NotBoundException e){
				System.out.println("Upstream server is off-line: ");
				Thread.sleep(2000);
				notifyOthers();
			}			
			remoteGS.newGSJoined();
			
		} catch (Exception e) {
			System.out.println("Kaboom: " + e);
		}
	}
	
	private GSInterface getRemoteGS(String gsURL) throws MalformedURLException, RemoteException, NotBoundException {
		GSInterface remoteGS = (GSInterface) Naming.lookup(gsURL);
		return remoteGS;
	}
	
	private ClusterInterface getRemoteCluster(String nodeURL) throws MalformedURLException, RemoteException, NotBoundException {
		ClusterInterface remoteRM = (ClusterInterface) Naming.lookup(nodeURL);
		return remoteRM;
	}
	
	
	public void newGSJoined() {
		// Increase the counter of the nodes already in the network.
		//GSJoined.incrementAndGet();
		// Start the algorithm if enough nodes have joined the network.
		if (GSJoined.incrementAndGet() == expectedNetworkSize){
			System.out.println("My downstream and I are ready ");
			pollingThread = new Thread(this);
		    pollingThread.start();
		    sendHeartbeat();
		}
			
	}
	
	public int getUrl() {
		return nodePort;
	}
	
	public void sendHeartbeat(){
		new Thread(new Runnable(){
			public void run() {
				
				while (true) {
					ControlMessage isAlive = new ControlMessage(ControlMessageType.HeartBeat);
					isAlive.setSender(localURL);
					for (String rmName : connectedRM){
						try{
							getRemoteCluster(rmName).onMessageReceived(isAlive);
						}catch(Exception e){
							System.out.println("Fail to connect to cluster");	
						}
					}
					try {
						// Sleep a while before creating a new job
						Thread.sleep(300L);
					} catch (InterruptedException e) {
						assert(false) : "Heatbeat Thread was interrupted";
					}	
				}
			}	
		}).start();

	}
	
	public int getWaitingJobs() {
		int ret = 0;
		ret = jobQueue.size();
		return ret;
	}
	
	public void onMessageReceived(ControlMessage message) {
		// preconditions
		assert(message != null) : "parameter 'message' cannot be null";
		
//		ControlMessage controlMessage = (ControlMessage)message;
		
		// resource manager wants to join this grid scheduler 
		// when a new RM is added, its load is set to Integer.MAX_VALUE to make sure
		// no jobs are scheduled to it until we know the actual load
		if (message.getType() == ControlMessageType.ResourceManagerJoin){
			resourceManagerLoad.put(message.getUrl(), Integer.MAX_VALUE);
			connectedRM.add(message.getUrl());
			ControlMessage ack = new ControlMessage(ControlMessageType.JoinAck);
			ack.setSender(localURL);
			ack.setUrl(upstreamURL);
			try{
				getRemoteCluster(message.getUrl()).onMessageReceived(ack);	
			}catch(Exception e){
				System.out.println("Ack to cluster failure");	
			}
			System.out.println("Cluster Joined: "+ message.getUrl());
            
		}
		
		// cluster informs of the single failure of GS
		if (message.getType() == ControlMessageType.FailReport){
			failTime = System.currentTimeMillis();
			
		    ControlMessage repair = new ControlMessage(ControlMessageType.RingConnect);
			repair.setSender(localURL);
			repair.setUrl(this.downstreamURL);
			try{
				getRemoteGS(upstreamURL).onMessageReceived(repair);	
			}catch(Exception e){
				System.out.println("Repair Transmission failure");	
			}
			System.out.println("Single failure of GS starts repairing");
		}
		
		if (message.getType() == ControlMessageType.RingConnect){
			if(message.getSender().equals(localURL)){
				downstreamURL = message.getUrl();
				System.out.println("Unidirectional ring has reconnected");
			}
			else{
				if(message.getUrl().equals(upstreamURL)){
					upstreamURL = message.getSender();	
					message.setUrl(localURL);
				}
				try{
					getRemoteGS(upstreamURL).onMessageReceived(message);	
				}catch(Exception e){
					System.out.println("RingConnect failure");		
				}
			}
			System.out.println(System.currentTimeMillis()-failTime);
		}
		
		// resource manager wants to offload a job to us 
		if (message.getType() == ControlMessageType.AddJob){
			if((getWaitingJobs() > MAX_JOBQUEUE_SIZE) && (!offloadURL.equals(localURL))){
				try{
					//System.out.println( "dispatch job to node " + offloadURL);
					getRemoteGS(offloadURL).onMessageReceived(message);	
				}catch(Exception e){
					System.out.println("AddJob failure");	
					offloadURL = localURL;
					jobQueue.add(message.getJob());
				}
			}
			else{
				jobQueue.add(message.getJob());
			}
			
			//System.out.println("Job Received "+ message.getUrl());
		}
			
		// resource manager wants to offload a job to us 
		if (message.getType() == ControlMessageType.ReplyLoad){
			
			resourceManagerLoad.put(message.getUrl(),message.getLoad());
			System.out.println("Load of " + message.getUrl() + " is " + message.getLoad());
		    ControlMessage replyMessage = new ControlMessage(ControlMessageType.UpdateLoad);
			replyMessage.setSender(localURL);
			replyMessage.setLoad(message.getLoad());
			replyMessage.setUrl(message.getUrl());
			try{
				getRemoteGS(upstreamURL).onMessageReceived(replyMessage);	
			}catch(Exception e){
				System.out.println("ReplyLoad failure");	
			}
								
		}
		
		if (message.getType() == ControlMessageType.UpdateLoad){
			if(!message.getSender().equals(localURL)){
				resourceManagerLoad.put(message.getUrl(),message.getLoad());
				if(!message.getSender().equals(upstreamURL)){
					try{
						getRemoteGS(upstreamURL).onMessageReceived(message);	
					}catch(Exception e){
						System.out.println("UpdateLoad failure");		
					}
				}
			}
		}
			
		if (message.getType() == ControlMessageType.ProbeGSLoad){
			if(message.getUrl().equals(localURL)){
				offloadURL = localURL;
//				System.out.println("no offload node found");
			}
			else{
				if(currentLoad < message.getLoad()/2){
				   ControlMessage rMessage = new ControlMessage(ControlMessageType.ReplyGSLoad);
				   rMessage.setUrl(localURL);
				   try{
					   getRemoteGS(message.getUrl()).onMessageReceived(rMessage);	
				   }catch(Exception e){
					   System.out.println("ReplyGSLoad failure");	
				   }
			    }
			   else{
				   try{
					   getRemoteGS(upstreamURL).onMessageReceived(message);	
				   }catch(Exception e){
					   System.out.println("Relay failure");	
			 	   }
			   }
			}
					
		}
		
		if (message.getType() == ControlMessageType.ReplyGSLoad){
			offloadURL = message.getUrl();
//			System.out.println("Node " + message.getUrl() + "can be offloaded");
		}
		
		if (message.getType() == ControlMessageType.Replication){
			if (message.getJobQueue() != null){
				this.clusterQueueReplication.put(message.getUrl(),message.getJobQueue());
//				System.out.println("Node " + message.getUrl() + " Replicated");
			}
		}
		
		
	}
		
	// finds the least loaded resource manager and returns its url
	private String getLeastLoadedRM() {
		String ret = null; 
		int minLoad = Integer.MAX_VALUE;
		
		// loop over all resource managers, and pick the one with the lowest load
		for (String key : resourceManagerLoad.keySet())
		{
			if (resourceManagerLoad.get(key) <= minLoad)
			{
				ret = key;
				minLoad = resourceManagerLoad.get(key);
			}
		}
		
		return ret;		
	}
	
	public void run() {
		while (running) {
			System.out.println("GS " + this.nodePort + "has load " + getWaitingJobs());
			// send a message to each resource manager, requesting its load
			for (String rmName : connectedRM)
			{
				System.out.println(resourceManagerLoad.get(rmName));
			}

			// Check if alive
			for (String rmName : connectedRM)
			{
				ControlMessage cMessage = new ControlMessage(ControlMessageType.HeartPulse);
                cMessage.setUrl(localURL);
				try {
					getRemoteCluster(rmName).onMessageReceived(cMessage);
				} catch (Exception e) {
					long startTime = System.currentTimeMillis();
					resourceManagerLoad.remove(rmName);
					connectedRM.remove(rmName);
					jobQueue.addAll(clusterQueueReplication.get(rmName));
					clusterQueueReplication.remove(rmName);					
					long endTime   = System.currentTimeMillis();
					long totalTime = endTime - startTime;
					System.out.println("Cluster Failure Detected and recovered, recovery time is " + totalTime);
				}
			}

			for (String rmName : connectedRM)
			{
				ControlMessage cMessage = new ControlMessage(ControlMessageType.RequestLoad);
                cMessage.setUrl(localURL);
                try{
                	getRemoteCluster(rmName).onMessageReceived(cMessage);
                }catch(Exception e){
    				System.out.println("request load fails; RM port is " + rmName);
    			}
			}
			
			currentLoad = getWaitingJobs();
			ControlMessage pMessage = new ControlMessage(ControlMessageType.ProbeGSLoad);
			pMessage.setUrl(localURL);
			pMessage.setLoad(currentLoad);
			try{
				getRemoteGS(upstreamURL).onMessageReceived(pMessage);
			}catch(Exception e){
				System.out.println("upstream node fails");
			}
			
			
			// schedule waiting messages to the different clusters
			for (Job job : jobQueue)
			{
				String leastLoadedRM =  getLeastLoadedRM();
				
				if (leastLoadedRM!=null) {
				
					ControlMessage cMessage = new ControlMessage(ControlMessageType.AddJob);
					cMessage.setJob(job);
					cMessage.setSender(localURL);
					try{
						getRemoteCluster(leastLoadedRM).onMessageReceived(cMessage);
					}catch(Exception e){
						System.out.println("add job fails");
					}
					
					
					jobQueue.remove(job);
					
					// increase the estimated load of that RM by 1 (because we just added a job)
					int load = resourceManagerLoad.get(leastLoadedRM);
					resourceManagerLoad.put(leastLoadedRM, load+1);
					
				}
				
			}
		  
			
			// sleep
			try
			{
				Thread.sleep(pollSleep);
			} catch (InterruptedException ex) {
				assert(false) : "Grid scheduler runtread was interrupted";
			}
			
		}
		
	}
	
	public void stopPollThread() {
		running = false;
		try {
			pollingThread.join();
		} catch (InterruptedException ex) {
			assert(false) : "Grid scheduler stopPollThread was interrupted";
		}
		
	}
	
}
