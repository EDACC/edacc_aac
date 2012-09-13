/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edacc.configurator.aac.clustering;

import edacc.configurator.aac.AAC;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.model.Instance;
import edacc.util.Pair;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/** This class implements a generic greedy bottom-up approach to clustering
 * The only method missing to form a functioning clustering algorithm is calculateClusterDistance(c1, c2),
 * which calculates the distance between two clusters.
 * For an example on how to implement this method, see Algorithm_CLC, which implements the method for
 * Complete Linkage Clustering.
 *
 * @author mugrauer
 */
public abstract class Algorithm_GreedyBottomUp implements ClusteringAlgorithm {
    private AAC aac;
    private ClusterHandler handler;
    protected ClusteringResources resources;
    
    //these values determine the number of clusters that will be generated
    //if useVarianceCriterion is set, clusters will be merged until further mergin would create
    //a cluster with a variance > maximumVariance.
    //Otherwise, clusters will be merged until the number of clusters = staticClusterNumber
    private final double maximumVariance = 0.1d;
    private final int staticClusterNumber = 10;
    private boolean useVarianceCriterion = true;
    
    public Algorithm_GreedyBottomUp(AAC aac, ClusteringResources resources, ClusterHandler handler){
        this.aac = aac;
        this.resources = resources;
        this.handler = handler;
    }
    
    /** calculates the distance between two given clusters
     * 
     */
    protected abstract double calculateClusterDistance(Cluster c1, Cluster c2);
    
    /** returns the name of this clustering algorithm
     */
    public abstract String getName();
    
    public Cluster[] calculateClustering(List<InstanceIdSeed> instances){
        Map<InstanceIdSeed, InstanceData> data = handler.getInstanceDataMap();
        long time = System.currentTimeMillis();
        log("Initialising clusters ... ");
        LinkedList<Pair<Cluster, Integer>> clusterList = new LinkedList<Pair<Cluster,Integer>>();
        Cluster c;
        int count = 0;
        for(InstanceIdSeed i : instances){
            c = new Cluster(i);
            clusterList.add(new Pair<Cluster, Integer>(c, count));
            count++;
        }        
        log("Initialising distance matrix ... ");
        //initialise distance matrix (DistanceMatrix class is at the bottom of CLC_Clustering.java!)
        DistanceMatrix distMat = new DistanceMatrix(clusterList.size());
        Double v1, v2;
        for(Pair<Cluster,Integer> c1 : clusterList){
            for(Pair<Cluster,Integer> c2 : clusterList){
                if(c1.getSecond() >= c2.getSecond())
                    continue;
                distMat.set(c1.getSecond(), c2.getSecond(), 
                            calculateClusterDistance(c1.getFirst(), c2.getFirst()));
            }
        }
        log("Refining clustering ... ");
        //calcualte clustering
        while(terminationCriterion(clusterList)){
            Pair<Cluster,Integer> mergeA=null, mergeB=null;
            Double distance = Double.MAX_VALUE, tmpDist;
            for(Pair<Cluster,Integer> cA : clusterList){
                for(Pair<Cluster,Integer> cB : clusterList){
                    if(cA.getSecond() >= cB.getSecond())
                        continue;
                    
                    tmpDist = distMat.get(cA.getSecond(), cB.getSecond());
                    if(tmpDist<distance){
                        mergeA = cA;
                        mergeB = cB;
                        distance = tmpDist;
                    }
                }
            }
            if(!isMergeViable(mergeA.getFirst(), mergeB.getFirst()))
                break;
            clusterList.remove(mergeB);
            mergeA.getFirst().mergeClusters(mergeB.getFirst());
            //update distance matrix:
            for(Pair<Cluster,Integer> cl: clusterList){
                if(cl.getSecond()==mergeA.getSecond())
                    continue;
                distMat.set(mergeA.getSecond(), cl.getSecond(), 
                            calculateClusterDistance(mergeA.getFirst(), cl.getFirst()));
            }
        }
        log("Establishing clustering ... ");
        Cluster[] clusters = new Cluster[clusterList.size()];
        int clusterPos = 0;
        for(Pair<Cluster,Integer> cl : clusterList){
            clusters[clusterPos] = cl.getFirst(); 
            clusterPos++;
        }        
        log("Done! Time elapsed: "+(System.currentTimeMillis()-time)+"ms.");
        return clusters;
    }
    
    /** determines, whether or not to continue the clustering process
     * 
     * @param clusterList the current clustering
     * @return true, if the clustering process should continue; false, if the clustering is finished 
     */
    protected boolean terminationCriterion(List<Pair<Cluster,Integer>> clusterList){
        // false = terminate; true = continue
        if(!useVarianceCriterion){
            if(clusterList.size()<=staticClusterNumber)
                return false;
        }
        
        if(clusterList.size() < 2)
            return false;
        return true;
    }
    
    /** determines, whether or not a merge between two given clusters is considered viable based on the
     * variance of characteristic values in the resulting cluster
     * will always return true if useVarianceCriterion is false
     * 
     * @param c1
     * @param c2
     * @return true, if useVarianceCriterion is false and/or if the merge is viable; false, if it isn't
     */
    protected boolean isMergeViable(Cluster c1, Cluster c2){
        if(!useVarianceCriterion)
            return true;
        
        List<InstanceIdSeed> instances = c1.getInstances();
        instances.addAll(c2.getInstances());//list now contains all instances that would be in the merged cluster      
        double var = resources.calculateVariance(instances);        
        return var < maximumVariance;
    }
    
    protected void log(String message){
        handler.log(message);
    }
}

class DistanceMatrix{
    private double[][] distMat;
    
    public DistanceMatrix(int size){
        distMat = new double[size][size];
        for(int i=0; i<size; i++)
            for(int j=0; j<size; j++)
                distMat[i][j] = Double.MAX_VALUE;
    }
    
    public void set(int i, int j, double value){
        if(i==j)
            return;
        distMat[i][j] = value;
        distMat[j][i] = value;        
    }
    
    public double get(int i, int j){
        return distMat[i][j];
    }
}