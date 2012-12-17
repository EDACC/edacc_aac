/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edacc.configurator.aac.clustering;

import edacc.configurator.aac.AAC;
import edacc.configurator.aac.InstanceIdSeed;

/**
 *
 * @author fr
 */
public class Algorithm_Average extends Algorithm_GreedyBottomUp{
    
    public Algorithm_Average(AAC aac, ClusteringResources resources, ClusterHandler handler){
        super(aac, resources, handler);
    }
    
    /* Calculates the distance between two clusters based on the average distance between all the instances
     * included in the clusters
     * When implementing a similar method for another clustering algorithm, remember to use
     * Resources.calculateInstanceDistance(inst1, inst2) to obtain the distance between two instances as
     * defined by the resource used to calculate the clustering
     * 
     * @return distance between the two clusters
     */
    @Override
    protected double calculateClusterDistance(Cluster c1, Cluster c2) {
        double accumulatedDistance = 0;
        int numberOfComparisons = 0;
        for(InstanceIdSeed idSeed1 : c1.getInstances()){
            for(InstanceIdSeed idSeed2 : c2.getInstances()){
                accumulatedDistance += resources.calculateInstanceDistance(idSeed1, idSeed2);
                numberOfComparisons++;
            }           
        }
        return (accumulatedDistance / ((double)numberOfComparisons));
    }

    @Override
    public String getName() {
        return "Algorithm_Average";
    }
}
