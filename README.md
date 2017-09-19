# The-Virtual-Grid-System-Simulator
With the increasing demand on multi-cluster distributed systems as infrastructure, the virtual grid system simulator (the VGS) can help the designers to cope with the important features of a distributed system. We proposed a unidirectional ring architecture for the VGS to simulate a small or medium grid system. The key features of the application include its ability to share load even in very imbalanced situation. Moreover, it is proved to be at least 1-fault-tolerant. The experiment is setup on Amazon EC2 instances and the results show that our implementation can meet the system requirements.
## System Overview 
The virtual grid system simulator (the VGS) is a distributed simulator of multi-cluster systems aiming to offer excellent performance-cost trade-off. The system consists of many independent clusters and a set of clusters need a grid scheduler (GS) to enable load balancing among the clusters. Moreover, each cluster contains two types of resources: nodes and a resource manager (RM). Upon arrival of a new job in a cluster, RM either dispatches the job to an idle node or offloads it to the GS if the job queue if full. The GS may balance the load by transferring jobs from heavily loaded cluster to lightly loaded ones. 

The requirements for the simulator are as follows:

a) The VGS contains 20 clusters with at least 1000 nodes, 5 grid scheduler nodes and workload of at least 10,000 jobs. Furthermore, load sharing needs to be enabled to handle imbalanced number of jobs arriving at different clusters.

b) The VGS can tolerate a simple failure model in which a GS or a RM might fail.

![Alt text](figure1.png?raw=true "Title")

As shown in figure 1, Our design is to deal with a small or medium grid system with at least 20 clusters, 5 grid schedulers and workload of at least 10,000 jobs. 
All the GSs are deployed to connect into a unidirectional ring. Then, each newly joined cluster will randomly choose a GS to connect to and maintain a job queue. Whenever a job is sent to a cluster, it will be offloaded to the connected GS if the number of jobs waiting in the queue exceeds a certain threshold. Otherwise, it is dispatched to an idle node. Each GS maintains the load of each cluster in a hashmap and the jobs will be sent to a cluster with the minimum load. Furthermore, the current load of each GS is shared via the ring to enable the load balancing. 

## Experiment Setup
The application is based on JAVA RMI and experimented on Amazon EC2 platform. 4 free AWS instances with each of them having 1 CPU, 1GB memory and low to moderate network performance are utilized. The limited resources of the free instances are sufficient for our system requirements (5 grid schedulers, 20 clusters and 10000 jobs), but the performance degrades with heavier workload.

In every instance, we deployed 5 clusters (50 nodes per cluster) and 1$\sim$2 GSs in an instance, which proves to yield better performance. We also tested other deployment strategies such as putting all 5 GSs in one instance. It turns out that the memory in that instance is insufficient for the workload of 10000 jobs (generation rate of 12.5 jobs/s).

The tested jobs are all independent with a constant generation rate. 

## Experiment Results
The recovery times of the failure of a GS and a cluster are tested. First we simulated the failure of a GS when the network reached its maximum load. The experiment proves that the unidirectional ring is able to repair itself and its connected clusters all successfully re-connect to the backup GS. The total recovery time is around 65ms (including 18ms for ring re-connection and 47ms for clusters re-connection).

Then we examined the failure of a cluster. The experiment shows that after having detected a cluster failure, its connected GS maintains the latest replication of its job queue so that once the cluster restarts, at least 20\% jobs can be recovered. Considering the time needed by the GSs to remove the failed cluster from their list of clusters, the average recovery time is around 16ms. 
The experiment on fault tolerance demonstrates that our system can at least tolerate the failure of one GS and one cluster. After the GS failure, we notice that a peak of the workload appears both in the clusters connected to the failed GS and the backup GS these clusters re-connect to. It is because the clusters cannot offload jobs any more before re-connecting to the backup GS. Moreover, the GS also cannot offload jobs to other GSs before the ring is repaired and thus, its workload also peaks. In the realistic situation, we need to take into account whether the cluster and the GS can stand such peak of workload and for how long. Since a larger ring may take longer time to repair, it must be ensured that all the clusters and the GSs function well before the recovery is done.

It is also interesting to note that with the same total number of jobs (10000), generation rate (12.5/s) and duration (10$\sim$15s), the performance is quite different under various ratios of number of jobs arriving at different clusters. The performance of load balancing is generally good, but in a well balanced situation, the monitored workload gets rather heavy after all the clusters start creating jobs. On the contray, the workload curve of the extremely imbalanced situation (5:0) stays steady approximately at its full load. It implies that in better balanced situations, we need to more carefully examine the bound on the job generation rate and duration to avoid the cluster being overloaded.

The experiment on overheads aims to analyze how long it takes for a node to respond to a job under different ratios. It turns out that the cost of the network communication for job scheduling is negligible in the experiment compared to the waiting time spent in the job queue. Moreover, even though a heavily loaded system tends to finish all the jobs earlier, the overall overheads indeed rise, which should be considered if there is a requirement on the response time of the jobs. 
