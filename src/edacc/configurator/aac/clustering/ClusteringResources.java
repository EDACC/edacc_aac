/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edacc.configurator.aac.clustering;

import edacc.configurator.aac.InstanceIdSeed;
import java.util.List;

/**
 *
 * @author mugrauer
 */
public interface ClusteringResources {
    
    /** 
     * states whether or not this type of resources requires initial data before the clustering can be
     * calculated.
     * If this method returns true, then a batch of initial SolverConfigurations need to complete the entire
     * instance-seed-course to provide initial data. These Solverconfigurations then need to be provided to the
     * constructor of ClusterHandler
     */
    public boolean isInitialDataRequired();
    
    /** 
     * states whether or not the resources used to calculate a clustering gets refined during the algorithm 
     * configuration process.
     * If this method returns true, then clustering should be recalculated periodically, in order to reflect those
     * changes in the data.
     */
    public boolean recalculateOnNewData();
    
    /** 
     * prepares a List of instance-seed-pairs for the clustering algorithm to divide into clusters.
     * This list does NOT need to reflect the instances or seed in the actual course in any way.
     * The list could e.g. contain only the instances (not the seeds) of the course (with seeds set to 0),
     * as in Resources_Properties (which implements this interface).
     * The establishClustering() method is meant to transform the clustering on this list back to a clustering
     * on the actual course. Essentially: preprateInstances() -> calculate clustering -> establishClustering()
     * See also: establishClustering()
     */
    public List<InstanceIdSeed> prepareInstances();
    
    /** 
     * transforms a clustering that was established on the value of prepareInstances() back to a clustering
     * on the actual course. If e.g. the clustering was only calculated for instances, not instance-seed-pairs,
     * as in the Resources_Properties class, this method will replace the dummy instance-seed-pairs with all actual
     * instance-seed-pairs that correspond to the instances.
     * Essentially: preprateInstances() -> calculate clustering -> establishClustering()
     * See also: prepareInstances()
     */
    public Cluster[] establishClustering(Cluster[] temporaryClustering);
    
    /** 
     * calculates the distance between two instance-seed-pairs as determined by this set of resources
     */
    public double calculateInstanceDistance(InstanceIdSeed i1, InstanceIdSeed i2);
    
    /**
     * calculates the variance between instances in a list as determined by this set of resources
     */
    public double calculateVariance(List<InstanceIdSeed> instances);
    
    /** 
     * returns the name of this resource-type, e.g. "Properties" or "MeanCost"
     */
    public String getName();
}
