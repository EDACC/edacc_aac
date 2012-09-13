/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edacc.configurator.aac.clustering;

import edacc.configurator.aac.AAC;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.model.Instance;

/**
 *
 * @author mugrauer
 */
public class Algorithm_CLC extends Algorithm_GreedyBottomUp{
    
    public Algorithm_CLC(AAC aac, ClusteringResources resources, ClusterHandler handler){
        super(aac, resources, handler);
    }
    
    /** calculates the CLC-distance between two given clusters, i.e. the distance between the two
     * furthest-apart instance-seed pairs in the two clusters
     * When implementing a similar method for another clustering algorithm, remember to use
     * Resources.calculateInstanceDistance(inst1, inst2) to obtain the distance between two instances as
     * defined by the resource used to calculate the clustering
     * 
     * @return distance between the two clusters
     */
    @Override
    protected double calculateClusterDistance(Cluster c1, Cluster c2){
        double distance = 0d, tmpDist;
        for(InstanceIdSeed idSeed1 : c1.getInstances()){
            for(InstanceIdSeed idSeed2 : c2.getInstances()){
                tmpDist = resources.calculateInstanceDistance(idSeed1, idSeed2);
                distance = (tmpDist > distance) ? tmpDist : distance;
            }
        }
        return distance;
    }
    
    @Override
    public String getName(){
        return "CLC";
    }    
}
