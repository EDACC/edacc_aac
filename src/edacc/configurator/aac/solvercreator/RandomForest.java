package edacc.configurator.aac.solvercreator;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

public class RandomForest implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 23153256236L;
	private List<DecisionTree> forest;
	private Random rng;
	private double performance;
	public RandomForest(Clustering clustering_original, Clustering clustering, Random rng, int treeCount, int n) {
		forest = new LinkedList<DecisionTree>();
		this.rng = rng;
		
		HashMap<Integer, List<Integer>> c = clustering.getClustering(false);
		List<Integer> instances = new LinkedList<Integer>();
		for (List<Integer> list : c.values()) {
			instances.addAll(list);
		}
		
		int num_valid_instances = Math.round(instances.size() * 0.2f);
		
		List<Integer> validationInstances = new LinkedList<Integer>();
		while (validationInstances.size() < num_valid_instances) {
			int rand = rng.nextInt(instances.size());
			validationInstances.add(instances.get(rand));
			//instances.remove(rand);
		}
		
		while (forest.size() < treeCount) {
			System.out.println("[RandomForest] Building tree " + (forest.size()+1) + " / " + treeCount);	
			
			HashSet<Integer> instance_set = new HashSet<Integer>();
			for (int k = 0; k < n; k++) {
				instance_set.add(instances.get(rng.nextInt(instances.size())));
			}
			
			HashMap<Integer, List<Integer>> tmp_c = new HashMap<Integer, List<Integer>>();
			for (Entry<Integer, List<Integer>> e : c.entrySet()) {
				List<Integer> tmp = new LinkedList<Integer>();
				for (int iid : e.getValue()) {
					if (instance_set.contains(iid)) {
						tmp.add(iid);
					}
				}
				if (!tmp.isEmpty()) {
					tmp_c.put(e.getKey(), tmp);
				}
			}
			DecisionTree tree = new DecisionTree(tmp_c, DecisionTree.ImpurityMeasure.GINIINDEX, clustering_original, clustering, 12, rng, 0.f);
			tree.cleanup();
			//if (tree.performance > 0.8f) {
				forest.add(tree);
			//}
		}
		double perf = 0.f;
		double num = 0.f;
		int timeouts = 0;
		for (int iid: validationInstances) {
			int clazz = getSolverConfig(clustering_original.F.get(iid));
			if (Double.isInfinite(clustering_original.getCost(clazz, iid))) {
				timeouts++;
			} else {
				perf += clustering_original.getCost(clazz, iid);
				num += clustering_original.getMinimumCost(iid);
			}
			
			System.out.println(clustering_original.getCost(clazz, iid) + ":" + clustering_original.getMinimumCost(iid));
		}
		
		performance = num/perf;
		System.out.println("[RandomForest] Timeouts: " + timeouts + " / " + validationInstances.size());
		System.out.println("[RandomForest] #all = " + num);
		System.out.println("[RandomForest] perf(RF) = " + performance);
		
	}
	
	public int getSolverConfig(float[] features) {
		HashMap<Integer, Integer> count = new HashMap<Integer, Integer>();
		for (DecisionTree tree : forest) {
			int scid = tree.query(features).getFirst();
			Integer c = count.get(scid);
			if (c == null) {
				c = 1;
			} else {
				c++;
			}
			count.put(scid, c);
		}
		
		List<Integer> scIds = new LinkedList<Integer>();
		int max = 1;
		for (Entry<Integer, Integer> entry : count.entrySet()) {
			if (entry.getValue() > max) {
				scIds.clear();
				scIds.add(entry.getKey());
				max = entry.getValue();
			} else if (entry.getValue().equals(max)) {
				scIds.add(entry.getKey());
			}
		}
		int scid = scIds.get(rng.nextInt(scIds.size()));
		System.out.println("c " + scIds.size() + " possible solver configs to choose from with " + max + " votes, choosing " + scid + ".");
		System.out.println("c " + scIds);
		return scid;
	}
}
