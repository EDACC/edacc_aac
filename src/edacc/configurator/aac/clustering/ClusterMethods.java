package edacc.configurator.aac.clustering;

import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.ExperimentResult;
import java.util.List;

/**
 *
 * @author mugrauer, schulte
 */
public interface ClusterMethods {
	
	/**
	 * Number of runs per cluster for a given solver configuration
	 * 
	 * @param sc
	 * @return number of runs per cluster
	 */
	public int[] countRunPerCluster(SolverConfiguration sc);
	
	/**
	 * Returns the cluster in which the specific experiment result is in
	 * 
	 * @param res
	 * @return the id of the cluster
	 */
	public int clusterOfInstance(ExperimentResult res);
	
	/**
	 * Returns one random instance from a given cluster
	 * 
	 * @param clusterNr
	 * @return random instance from the given cluster
	 */
	public InstanceIdSeed getInstanceInCluster(int clusterNr);
        
        /**
         * Returns a random instance from a given cluster that the given solver configuration has
         * not yet completed. Will return null if the configuration has completed all instances in this cluster
         * 
         * @param clusterNr the cluster the instances will be selected from
         * @param solverConfig the Instances this solver configuration has already completed will not be considered
         * 
         * @return random instance from a given cluster that the given solver configuration has
         * not yet completed.
         */
        public InstanceIdSeed getInstanceInCluster(int clusterNr, SolverConfiguration solverConfig);
	
	/**
	 * Analyses new provided data for the clusters (Checks if instances should switch 
	 * clusters and calculates the new variance)
	 * 
	 * @param sc
	 */
	public void addDataForClustering(SolverConfiguration sc);
        
        /**
         * Retrieves a list of all instance-seed-pairs within a cluster
         * 
         * @param clusterNumber the cluster whose instances are to be returned
         * @return List of instances in the specified clustera
         */
        public List<InstanceIdSeed> getClusterInstances(int clusterNumber);
        
        /**
         * Visualises the current clustering in the log file
         */
        public void visualiseClustering();
}
