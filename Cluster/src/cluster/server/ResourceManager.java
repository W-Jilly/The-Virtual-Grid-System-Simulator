package cluster.server;


import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.Naming;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import cluster.INodeEventHandler;
import cluster.Job;
import cluster.JobStatus;
import cluster.Node;
import gridscheduler.GSInterface;
import message.ControlMessage;
import message.ControlMessageType;

/**
 * This class represents a resource manager in the VGS. It is a component of a cluster, 
 * and schedulers jobs to nodes on behalf of that cluster. It will offload jobs to the grid
 * scheduler if it has more jobs waiting in the queue than a certain amount.
 * 
 * The <i>jobQueueSize</i> is a variable that indicates the cutoff point. If there are more
 * jobs waiting for completion (including the ones that are running at one of the nodes)
 * than this variable, jobs are sent to the grid scheduler instead. This variable is currently
 * defaulted to [number of nodes] + MAX_QUEUE_SIZE. This means there can be at most MAX_QUEUE_SIZE jobs waiting 
 * locally for completion. 
 * 
 * Of course, this scheme is totally open to revision.
 * 
 * @author Niels Brouwers, Boaz Pat-El
 *
 */
public class ResourceManager implements INodeEventHandler{
	private ClusterImplementation cluster;
	public Queue<Job> jobQueue;
	public int clusterURL;  // should related to cluster port id
	private int jobQueueSize;
	private Registry registry;
	public static final int MAX_QUEUE_SIZE = 55; 

	// Scheduler url
	private String gridSchedulerURL = null;
	private String backupURL = null;
	private boolean GSJoined = false;
	private String localURL;
	private PrintWriter writer;

	/**
	 * Constructs a new ResourceManager object.
	 * <P> 
	 * <DL>
	 * <DT><B>Preconditions:</B>
	 * <DD>the parameter <CODE>cluster</CODE> cannot be null
	 * </DL>
	 * @param cluster the cluster to which this resource manager belongs.
	 */
	public ResourceManager(ClusterImplementation cluster){
		// preconditions
		assert(cluster != null);

		this.jobQueue = new ConcurrentLinkedQueue<Job>();
		this.cluster = cluster;
		this.clusterURL = cluster.getClusterPort();
		this.registry = cluster.registry;
		this.localURL = cluster.localURL;
		try {
			this.writer = new PrintWriter("joblog"+clusterURL+".dat", "UTF-8");
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// Number of jobs in the queue must be larger than the number of nodes, because
		// jobs are kept in queue until finished. The queue is a bit larger than the 
		// number of nodes for efficiency reasons - when there are only a few more jobs than
		// nodes we can assume a node will become available soon to handle that job.
		jobQueueSize = cluster.getNodeCount() + MAX_QUEUE_SIZE;

	}
	
	
	// Find Gridscheduler Interface
	private GSInterface getRemoteGS(String gsURL) throws MalformedURLException, RemoteException, NotBoundException {
		GSInterface remoteGS = (GSInterface) Naming.lookup(gsURL);
		return remoteGS;
	}

	/**
	 * Add a job to the resource manager. If there is a free node in the cluster the job will be
	 * scheduled onto that Node immediately. If all nodes are busy the job will be put into a local
	 * queue. If the local queue is full, the job will be offloaded to the grid scheduler.
	 * <DL>
	 * <DT><B>Preconditions:</B>
	 * <DD>the parameter <CODE>job</CODE> cannot be null
	 * <DD>a grid scheduler url has to be set for this rm before calling this function (the RM has to be
	 * connected to a grid scheduler)
	 * </DL>
	 * @param job the Job to run
	 * @throws Exception 
	 * @throws NotBoundException 
	 * @throws RemoteException 
	 * @throws AccessException 
	 */
	public void addJob(Job job) throws AccessException, RemoteException, NotBoundException, Exception {
		// check preconditions
		assert(job != null) : "the parameter 'job' cannot be null";
		assert(gridSchedulerURL != null) : "No grid scheduler URL has been set for this resource manager";

		// if the jobqueue is full, offload the job to the grid scheduler
		if (jobQueue.size() >= jobQueueSize) {

			ControlMessage controlMessage = new ControlMessage(ControlMessageType.AddJob);
			controlMessage.setJob(job);
			try{
				getRemoteGS(gridSchedulerURL).onMessageReceived(controlMessage);
			}catch(Exception e){
				jobQueue.add(job);
				scheduleJobs();
				System.out.println(String.format("No GS accepts job "));
			}
			
		// otherwise store it in the local queue
		} else {
			jobQueue.add(job);
			scheduleJobs();
		}
		
//		System.out.println(String.format("Too Much Jobs ") + jobQueue.size());

	}

	/**
	 * Tries to find a waiting job in the jobqueue.
	 * @return
	 */
	public Job getWaitingJob() {
		// find a waiting job
		for (Job job : jobQueue) 
			if (job.getStatus() == JobStatus.Waiting) 
				return job;

		// no waiting jobs found, return null
		return null;
	}

	/**
	 * Tries to schedule jobs in the jobqueue to free nodes. 
	 */
	public void scheduleJobs() {
		// while there are jobs to do and we have nodes available, assign the jobs to the 
		// free nodes
		Node freeNode;
		Job waitingJob;

		while ( ((waitingJob = getWaitingJob()) != null) && ((freeNode = cluster.getFreeNode()) != null) ) {
			long responseTime = freeNode.startJob(waitingJob);
			writer.println(waitingJob.getId());
			writer.println(waitingJob.getDuration());
			writer.println(responseTime);
			writer.flush();
			//Create log file for each job
		}

	}

	/**
	 * Called when a job is finished
	 * <p>
	 * pre: parameter 'job' cannot be null
	 */
	
	public void jobDone(Job job) {
		// preconditions
		assert(job != null) : "parameter 'job' cannot be null";

		// job finished, remove it from our pool
		jobQueue.remove(job);
	}
	
	/**
	 * @return the url of the grid scheduler this RM is connected to 
	 */

	public void setBackupURL(String backupURL) {
		this.backupURL = backupURL;
	}

	public void setGSJoined(boolean gSJoined) {
		GSJoined = gSJoined;
	}

	

	public boolean isGSJoined() {
		return GSJoined;
	}


	public String getBackupURL() {
		return backupURL;
	}


	/**
	 * Connect to a grid scheduler
	 * <p>
	 * pre: the parameter 'gridSchedulerURL' must not be null
	 * @param gridSchedulerURL
	 * @throws Exception 
	 * @throws NotBoundException 
	 * @throws RemoteException 
	 * @throws AccessException 
	 */
	public void connectToGridScheduler(String gridSchedulerURL) {

		// preconditions
		assert(gridSchedulerURL != null) : "the parameter 'gridSchedulerURL' cannot be null"; 

		this.gridSchedulerURL = gridSchedulerURL;
		ControlMessage message = new ControlMessage(ControlMessageType.ResourceManagerJoin);
		message.setUrl(localURL);
		try {
			getRemoteGS(gridSchedulerURL).onMessageReceived(message);
		} catch (AccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NotBoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void startReplicateToGrid(){
		
//		while(!GSJoined){
//			try {
//				Thread.sleep(500L);
//			} catch (InterruptedException e) {
//				assert(false) : "GSJoined Thread was interrupted";
//			}	
//		}
		
		System.out.println("Replication starts");
		// Create new thread to replicate Job queue regularly
		new Thread(new Runnable(){
			public void run() {
				ControlMessage controlMessage = new ControlMessage(ControlMessageType.Replication);
				controlMessage.setUrl(localURL);
				while (true) {
					if(!GSJoined){
						try {
							Thread.sleep(500L);
						} catch (InterruptedException e) {
							assert(false) : "GSJoined Thread was interrupted";
						}	
					}
					else{
						if (jobQueue!=null){
						controlMessage.setJobQueue(jobQueue);
						    try {
							     getRemoteGS(gridSchedulerURL).onMessageReceived(controlMessage);
						    } catch (Exception e1) {
							     // TODO Auto-generated catch block
//							     System.out.println("Replication failure");
						    }
					    }
					    try {
						    // Sleep a while before creating a new job
						    Thread.sleep(80L);
					    } catch (InterruptedException e) {
						    assert(false) : "Replication Thread was interrupted";
					    }	
					}
					
				}
			}	
		}).start();
	}
}
