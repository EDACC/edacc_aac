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
public interface ClusteringAlgorithm {
    
    /** 
     * calculates a clustering
     */
    public Cluster[] calculateClustering(List<InstanceIdSeed> instances);
    
    /** 
     * returns the name of this clustering algorithm, e.g. "Algorithm_CLC"
     */
    public String getName();
    
}
