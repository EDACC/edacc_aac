/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edacc.configurator.aac.clustering;

import edacc.configurator.aac.InstanceIdSeed;
import java.util.List;

/**
 * This abstract class provides a standardised way to handle the resources (i.e. data) used to calculate a 
 * clustering of instance-seed-pairs
 * When implementing your own version of resources, remember to specify whether or not your implementation
 * requires a list of SolverConfigurations that has already completed the instance-seed-course as initial data.
 * You can do so in the isInitialDataRequired method in this file.
 *
 * @author mugrauer
 */
public abstract class ClusteringResources {
    
    /** 
     * states whether or not this type of resources requires initial data before the clustering can be
     * calculated.
     * If this method returns true, then a batch of initial SolverConfigurations need to complete the entire
     * instance-seed-course to provide initial data. These SolverConfigurations then need to be provided to the
     * constructor of ClusterHandler
     */
    public static boolean isInitialDataRequired(String resourceType){
        if(resourceType.equals("Resources_MeanCost"))
            return true;
        if(resourceType.equals("Resources_Properties"))
            return false;
        if(resourceType.equals("Resources_PropertiesWorkaround"))
            return false;
        //default: if in doubt, provide data to prevent crashes
        System.out.println("Warning: Clustering resource type does not specify whether initial data is required");
        return true;
    }
    
    /** 
     * states whether or not the resources used to calculate a clustering get refined during the algorithm 
     * configuration process.
     * If this method returns true, then clustering will be recalculated periodically, in order to reflect the
     * updated data
     */
    public abstract boolean recalculateOnNewData();
    
    /** 
     * prepares a List of instance-seed-pairs for the clustering algorithm to divide into clusters.
     * This list does NOT need to reflect the instances or seed in the actual course in any way.
     * The list could e.g. contain only the instances (not the seeds) of the course (with seeds set to 0),
     * as in Resources_Properties (which implements this interface).
     * The establishClustering() method is meant to transform the clustering on this list back to a clustering
     * on the actual course. Essentially: preprateInstances() -> calculate clustering -> establishClustering()
     * See also: establishClustering()
     */
    public abstract List<InstanceIdSeed> prepareInstances();
    
    /** 
     * transforms a clustering that was established on the value of prepareInstances() back to a clustering
     * on the actual course. If e.g. the clustering was only calculated for instances, not instance-seed-pairs,
     * as in the Resources_Properties class, this method will replace the dummy instance-seed-pairs with all actual
     * instance-seed-pairs that correspond to the instances.
     * Essentially: preprateInstances() -> calculate clustering -> establishClustering()
     * See also: prepareInstances()
     */
    public abstract Cluster[] establishClustering(Cluster[] temporaryClustering);
    
    /** 
     * calculates the distance between two instance-seed-pairs as determined by this set of resources
     */
    public abstract  double calculateInstanceDistance(InstanceIdSeed i1, InstanceIdSeed i2);
    
    /**
     * calculates the variance between instance-seed-pairs in a list as determined by this set of resources
     */
    public abstract double calculateVariance(List<InstanceIdSeed> instances);
    
    /**
     * returns a representation of this resource in array format (first index represents instance-seed-pairs, 
     * second index represents data values)
     * The data does not contain null values, and is in some way normalised (to [-1,1]), so that no knowledge of
     * the underlying resource is required to meaningfully interpret it
     */
    public abstract RefinedData getRefinedData();
    
    /** 
     * returns the name of this resource-type, e.g. "Resources_Properties" or "Resources_MeanCost"
     */
    public abstract String getName();
}
