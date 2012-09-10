/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edacc.configurator.aac.clustering;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.Instance;
import edacc.model.InstanceHasProperty;
import edacc.util.Pair;
import java.util.*;
import org.apache.commons.math.stat.descriptive.moment.Variance;

/**
 *
 * @author fr
 */
public class PropertyClustering  extends ClusteringTemplate implements ClusterMethods{
    protected HashMap<Integer, Instance> instanceIdMap;
    protected Variance variance;
    
    //these values determine the number of clusters that will be generated
    //if useVarianceCriterion is set, clusters will be merged until further mergin would create
    //a cluster with a variance > maximumVariance.
    //Otherwise, clusters will be merged until the number of clusters = staticClusterNumber
    private final double maximumVariance = 0.1d;
    private final int staticClusterNumber = 10;
    private boolean useVarianceCriterion = true;
        
    public PropertyClustering(AAC aac, Parameters params, API api, Random rng) throws Exception{
        super(aac, params, api, rng, null);
        
        List<Instance> instanceList = api.getExperimentInstances(params.getIdExperiment());
        instanceIdMap = new HashMap<Integer, Instance>();
        for(Instance i : instanceList){
            instanceIdMap.put(i.getId(), i);
        }
        
        calculateGreedyClustering(instanceList);
    }
    
    private void calculateGreedyClustering(List<Instance> instanceList){
        long time = System.currentTimeMillis();
        aac.log("Initialising clusters ... ");
        LinkedList<Pair<Cluster, Integer>> clusterList = new LinkedList<Pair<Cluster,Integer>>();
        Collection<InstanceData> datList = data.values();
        int count =0;
        Cluster c;
        for(Instance i: instanceList){
            c = new Cluster(new InstanceIdSeed(i.getId(), 0));
            clusterList.add(new Pair(c, count));
            count++;
        }
        aac.log("Initialising distance matrix ... ");
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
        aac.log("done!\n Refining clustering ... ");
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
        System.out.print("done!\nEstablishing clustering ... ");
        clusters = new Cluster[clusterList.size()];
        int clusterPos = 0;
        for(Pair<Cluster,Integer> cl : clusterList){
            clusters[clusterPos] = cl.getFirst(); 
            clusterPos++;
        }
        extendClustersWithSeeds(instanceList);
        System.out.println("done! Time elapsed: "+(System.currentTimeMillis()-time)+"ms.");
    }
        
    /** calculates the CLC-distance between two given clusters
     * 
     * @return distance between the two clusters
     */
    protected double calculateClusterDistance(Cluster c1, Cluster c2){
        double distance = 0d, tmpDist;
        Instance i1, i2;
        for(InstanceIdSeed idSeed1 : c1.getInstances()){
            i1 = instanceIdMap.get(idSeed1.instanceId);
            for(InstanceIdSeed idSeed2 : c2.getInstances()){
                i2 = instanceIdMap.get(idSeed2.instanceId);
                
                tmpDist = calculateInstanceDistance(i1, i2);
                distance = (tmpDist > distance) ? tmpDist : distance;
            }
        }
        return distance;
    }
    
    /** calculates the distance between to instances
     * 
     * @param i1
     * @param i2
     * @return distance between the two instances
     */
    protected double calculateInstanceDistance(Instance i1, Instance i2){        
        HashMap<Integer, InstanceHasProperty> m1 = i1.getPropertyValues();
        HashMap<Integer, InstanceHasProperty> m2 = i2.getPropertyValues();
        //only properties 80-133 are properly set up
        double dist = 0, tmp, val1, val2;
        for(int i=80; i<134; i++){
             val1 = Double.parseDouble(m1.get(i).getValue());
             val2 = Double.parseDouble(m2.get(i).getValue());             
             tmp = val1-val2;//TODO: rework to avoid rounding errors
             tmp = tmp * tmp;
             dist += tmp;
        }
        dist = Math.sqrt(dist);
        return dist;
    }
    
    /*
    protected double calculateMaxDistance(List<InstanceIdSeed> instances){
        double maxDist = 0d, tmpDist;
        int countOuter=0, countInner=0;
        Instance i1, i2;
        for(InstanceIdSeed ids1 : instances){
            for(InstanceIdSeed ids2 : instances){
                if(countInner > countOuter){
                    i1 = instanceIdMap.get(ids1.instanceId);
                    i2 = instanceIdMap.get(ids2.instanceId);
                    tmpDist = calculateInstanceDistance(i1, i2);
                    if(tmpDist > maxDist)
                        maxDist = tmpDist;
                }
                countInner++;
            }
            countOuter++;
        }
        return maxDist;
    }*/
    
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
        instances.addAll(c2.getInstances());
        double[] characteristicValues = new double[instances.size()];
        double average = 0, tmp;
        int count = 0;
        for(InstanceIdSeed idSeed : instances){
            Instance instance = instanceIdMap.get(idSeed.instanceId);
            tmp = calculateCharacteristicValue(instance);
            average += tmp;
            characteristicValues[count] = tmp;
            count++;
        }        
        double var = variance.evaluate(characteristicValues, average);
        
        return var < maximumVariance;
    }
    
    
    protected double calculateCharacteristicValue(Instance inst){
        HashMap<Integer, InstanceHasProperty> m = inst.getPropertyValues();
        double val = 0, tmp;
        for(int i=80; i<134; i++){
            tmp = Double.parseDouble(m.get(i).getValue());
            tmp = tmp*tmp;
            val += tmp;
        }
        return Math.sqrt(val);
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
    
    /** extends the clustering (which currently only contains of "dummy" instance-seed-pairs (the instances are correct, but 
     * seeds are 0), to a proper clustering. That is, for each dummy instance in a cluster, all instance-seed-pairs in the course
     * which have the same instance as the dummy will be added to the cluster, and the dummy will be removed
     * 
     * @param instanceList list of Instances (not instance-seed-pairs!) in this clustering
     */
    private void extendClustersWithSeeds(List<Instance> instanceList){
        Collection<InstanceData> datList = data.values();        
        for(Instance inst : instanceList){
            InstanceIdSeed dummy = new InstanceIdSeed(inst.getId(), 0);
            for(int i=0; i<clusters.length; i++){
                if(clusters[i].contains(dummy)){
                    for(InstanceData idSeed : datList){
                        if(idSeed.getInstanceID() == inst.getId())
                            clusters[i].addInstance(new InstanceIdSeed(idSeed.getInstanceID(), idSeed.getSeed()));
                            instanceClusterMap.put(idSeed.getInstanceIdSeed(), i);
                    }
                    clusters[i].removeInstance(inst.getId(), 0); //remove dummy
                    break;
                }
            }
        }
    }
    
    @Override
    protected void log(String message){
        aac.log("PropertyClustering: "+message);
    }
}
