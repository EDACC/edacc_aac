package edacc.configurator.aac.clustering;

import edacc.api.costfunctions.CostFunction;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.ExperimentResult;
import java.util.List;

/**
 *
 * @author mugrauer, schulte
 */
public interface ClusterMethods {
    
        //public void debugAnalyseDifferences(SolverConfiguration incumbent, SolverConfiguration challenger);
    
        
        /**
         * Maps the ExperimentResults of a given SolverConfiguration to the clusters their instance-seed-pairs
         * belong to
         * 
         * @param sc the config for which the results should be mapped 
         * @return An array containing Lists of ExperimentResults. Each position in the array corresponds to one
         *          cluster, e.g. array[5] would give you the list of Results for cluster 5.
         */
        public List<ExperimentResult>[] mapResultsToClusters(SolverConfiguration sc);
	
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
         * @param clusterNr the cluster the instance will be selected from
         * @param solverConfig the Instances this solver configuration has already completed will not be considered
         * 
         * @return random instance from a given cluster that the given solver configuration has
         * not yet completed.
         */
        public InstanceIdSeed getInstanceInCluster(int clusterNr, SolverConfiguration solverConfig);
        
        /**
         * Returns a list of random instances from a given cluster that the given solver configuration has not 
         * yet completed. Will return null if there are not enough instances left in the cluster
         * 
         * @param clusterNr the cluster the instances will be selected from
         * @param solverConfig the instances this solver configuration has already completed will not be considered
         * @param numberOfInstances the number of instances the method should return
         * 
         * @return list of random instances from a given cluster that the given solver config has not yet completed 
         */
        public List<InstanceIdSeed> getInstancesInCluster(int clusterNr, SolverConfiguration sc, int numberOfInstances);
	
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
        
        /**
         * Calculates the costs of two SCs based on the clusters and not on specific instances
         * 
         * @param sc
         * @param competitor
         * @param costFunc
         * @return the costs as double values 
         */
        public Costs costs(SolverConfiguration sc, SolverConfiguration competitor, CostFunction costFunc);
}
