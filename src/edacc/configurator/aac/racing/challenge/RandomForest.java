package edacc.configurator.aac.racing.challenge;

import java.io.Serializable;
import java.util.HashMap;
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
	public RandomForest(Clustering clustering, Random rng, int treeCount) {
		forest = new LinkedList<DecisionTree>();
		this.rng = rng;
		for (int i = 0; i < treeCount; i++) {
			System.out.println("[RandomForest] Building tree " + (i+1) + " / " + treeCount);
			HashMap<Integer, List<Integer>> c = clustering.getClusteringGreedy(rng);
			forest.add(new DecisionTree(c, clustering.F, clustering.F.values().iterator().next().length, DecisionTree.ImpurityMeasure.ENTROPYINDEX));
		}
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
