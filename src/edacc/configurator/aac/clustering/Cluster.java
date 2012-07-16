package edacc.configurator.aac.clustering;

import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.ExperimentResult;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author schulte, mugrauer
 */
public class Cluster{
        private HashSet<InstanceIdSeed> idSeed;
	//private HashMap<Integer, InstanceIdSeed> idToInstances;
	
	public Cluster(List<InstanceIdSeed> instances) {
                idSeed = new HashSet<InstanceIdSeed>();
                idSeed.addAll(instances);            
	}
        public Cluster(InstanceIdSeed initialInstance){
                idSeed = new HashSet<InstanceIdSeed>();
                idSeed.add(initialInstance);
        }
	
	/**
	 * Number of runs for this sc in this cluster
	 * 
	 * @param sc
	 * @return number of runs
	 */
	public int countRuns(SolverConfiguration sc) {
		List<ExperimentResult> results = sc.getFinishedJobs();
		int count = 0;
		for (ExperimentResult result : results) {
			if(contains(result.getInstanceId(), result.getSeed())) count++;
		}
		return count;
	}
        
        public boolean contains(int instanceId, int seed){
            return idSeed.contains(new InstanceIdSeed(instanceId, seed));
        }

	
	/**
	 * Returns all instances this cluster holds
	 * 
	 * @return all instances
	 */
	public List<InstanceIdSeed> getInstances() {
		InstanceIdSeed[] instances = new InstanceIdSeed[idSeed.size()];
                instances = idSeed.toArray(instances);
                LinkedList<InstanceIdSeed> iList = new LinkedList<InstanceIdSeed>();
                iList.addAll(Arrays.asList(instances));
                return iList;
        }
	
	/**
	 * Removes a instance from this cluster
	 * 
	 * @param instanceId
	 * @return true if this cluster holds the specific instance
	 */
	public boolean removeInstance(int instanceId, int seed) {
		if(idSeed.contains(new InstanceIdSeed(instanceId, seed))) {
			idSeed.remove(new InstanceIdSeed(instanceId, seed));
			return true;
		}
		return false;
	}
	
	/**
	 * Add an instance to this cluster
	 * 
	 * @param instance
	 * @return true if the instance was successfully added
	 */
	public boolean addInstance(InstanceIdSeed instance) {
		if(idSeed.contains(instance)) return false;
		idSeed.add(instance);
		return true;
	}
        
        /* merges the contents of the specified cluster into this one
         * 
         * @param c the cluster to be merged into this cluster
         */
        public void mergeClusters(Cluster c){
                idSeed.addAll(c.idSeed);
                c.idSeed.clear(); //safety precaution, instances should never be in multiple clusters
        }

        @Override
        public boolean equals(Object obj) {
                if (obj == null) {
                    return false;
                }
                if (getClass() != obj.getClass()) {
                    return false;
                }
                final Cluster other = (Cluster) obj;
                if (this.idSeed != other.idSeed && (this.idSeed == null || !this.idSeed.equals(other.idSeed))) {
                    return false;
                }
                return true;
        }

        @Override
        public int hashCode() {
                int hash = 5;
                hash = 47 * hash + (this.idSeed != null ? this.idSeed.hashCode() : 0);
                return hash;
        }        
}
