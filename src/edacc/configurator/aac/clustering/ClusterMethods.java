package edacc.configurator.aac.clustering;

import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.ExperimentResult;

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
	 * Analyses new provided data for the clusters (Checks if instances should switch 
	 * clusters and calculates the new variance)
	 * 
	 * @param sc
	 */
	public void addDataForClustering(SolverConfiguration sc);
}
