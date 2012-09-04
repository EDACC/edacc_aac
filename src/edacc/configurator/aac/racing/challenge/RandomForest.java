package edacc.configurator.aac.racing.challenge;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

import edacc.util.Pair;

public class RandomForest implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 23153256236L;
	private List<DecisionTree> forest;
	private Random rng;
	private float performance;
	public RandomForest(Clustering clustering, Random rng, int treeCount, int n) {
		forest = new LinkedList<DecisionTree>();
		this.rng = rng;
		List<Pair<Integer, Float>> scidWeight = new LinkedList<Pair<Integer, Float>>();
		
		for (int scid : clustering.M.keySet()) {
			scidWeight.add(new Pair<Integer, Float>(scid, clustering.getWeight(scid)));
		}
		
		Collections.sort(scidWeight, new Comparator<Pair<Integer, Float>>() {

			@Override
			public int compare(Pair<Integer, Float> arg0, Pair<Integer, Float> arg1) {
				if (arg0.getSecond() - 0.000001f < arg1.getSecond() && arg0.getSecond() + 0.000001f > arg1.getSecond()) {
					return 0;
				} else if (arg0.getSecond() > arg1.getSecond()) {
					return 1;
				} else if (arg1.getSecond() > arg0.getSecond()) {
					return -1;
				}
				return 0;
			}
			
		});
		
		Clustering C_orig = new Clustering(clustering);
	/*	int numConfigs =  Integer.parseInt("12");
		if (numConfigs != -1) {
			while (scidWeight.size() > numConfigs) {
				Pair<Integer, Float> p = scidWeight.get(0);
				System.out.println("Removing " + p.getFirst() + " with weight " + p.getSecond());
				clustering.remove(p.getFirst());
				scidWeight.remove(0);
			}
		}*/
		
		HashMap<Integer, List<Integer>> c = clustering.getClustering(false);
		List<Integer> instances = new LinkedList<Integer>();
		for (List<Integer> list : c.values()) {
			instances.addAll(list);
		}
		
		List<Integer> validationInstances = new LinkedList<Integer>();
		while (validationInstances.size() < 125) {
			int rand = rng.nextInt(instances.size());
			validationInstances.add(instances.get(rand));
			instances.remove(rand);
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
			DecisionTree tree = new DecisionTree(tmp_c, clustering.F, clustering.F.values().iterator().next().length, DecisionTree.ImpurityMeasure.ENTROPYINDEX, C_orig, clustering, 6, rng);
			//if (tree.performance > 0.8f) {
				forest.add(tree);
			//}
		}
		float perf = 0.f;
		float num = 0.f;
		for (int iid: validationInstances) {
			int clazz = getSolverConfig(C_orig.F.get(iid));
			
			perf += C_orig.getMembership(clazz, iid);
			num+= C_orig.getMaximumMembership(iid);
			
			System.out.println(C_orig.getMembership(clazz, iid) + ":" + C_orig.getMaximumMembership(iid));
		}
		
		performance = perf/num;
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
