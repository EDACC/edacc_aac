package edacc.configurator.aac.clustering;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.ExperimentResult;

public class Cluster{
	private HashMap<Integer, InstanceIdSeed> idToInstances;
	
	public Cluster(List<InstanceIdSeed> instances) {
		for (InstanceIdSeed instance : instances) {
			idToInstances = new HashMap<Integer, InstanceIdSeed>();
			idToInstances.put(instance.instanceId, instance);
		}
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
		ArrayList<InstanceIdSeed> instancesList = new ArrayList<InstanceIdSeed>();
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
}
