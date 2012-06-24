package edacc.configurator.aac.clustering;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.ExperimentResult;

/**
 *
 * @author schulte, mugrauer
 */
public class Cluster{
	private HashMap<Integer, InstanceIdSeed> idToInstances;
	
	public Cluster(List<InstanceIdSeed> instances) {
                idToInstances = new HashMap<Integer, InstanceIdSeed>();
		for (InstanceIdSeed instance : instances) {			
			idToInstances.put(instance.instanceId, instance);
		}
	}
        public Cluster(InstanceIdSeed initialInstance){
            idToInstances = new HashMap<Integer, InstanceIdSeed>();
            idToInstances.put(initialInstance.instanceId, initialInstance);
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
			if(null != idToInstances.get(result.getInstanceId())) count++;
		}
		return count;
	}

	
	/**
	 * Returns all instances this cluster holds
	 * 
	 * @return all instances
	 */
	public List<InstanceIdSeed> getInstances() {
		Collection<InstanceIdSeed> instances = idToInstances.values();
		Iterator<InstanceIdSeed> instancesIter = instances.iterator();
		ArrayList<InstanceIdSeed> instancesList = new ArrayList<InstanceIdSeed>(idToInstances.size());
		while(instancesIter.hasNext()) {
			InstanceIdSeed tmp = instancesIter.next();
			instancesList.add(tmp);
		}
		return instancesList;
	}
	
	/**
	 * Removes a instance from this cluster
	 * 
	 * @param instanceId
	 * @return true if this cluster holds the specific instance
	 */
	public boolean removeInstance(int instanceId) {
		if(idToInstances.containsKey(instanceId)) {
			idToInstances.remove(instanceId);
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
		if(idToInstances.containsKey(instance.instanceId)) return false;
		idToInstances.put(instance.instanceId, instance);
		return true;
	}
        
        /* merges the contents of the specified cluster into this one
         * 
         * @param c the cluster to be merged into this cluster
         */
        public void mergeClusters(Cluster c){
            idToInstances.putAll(c.idToInstances);
            c.idToInstances.clear(); //instances shouldn't be in multiple clusters
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
        if (this.idToInstances != other.idToInstances && (this.idToInstances == null || !this.idToInstances.equals(other.idToInstances))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 47 * hash + (this.idToInstances != null ? this.idToInstances.hashCode() : 0);
        return hash;
    }        
}
