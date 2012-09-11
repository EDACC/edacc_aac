package edacc.configurator.aac.solvercreator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import edacc.util.Pair;

public class DecisionTree implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 3264367978634L;

	public enum ImpurityMeasure {
		MISCLASSIFICATIONINDEX, GINIINDEX, ENTROPYINDEX
	}
	
	private Node root;
	private int num_features;
	private transient HashMap<Integer, float[]> features;	
	private HashSet<Integer> usedFeatures;	
	private ImpurityMeasure impurityMeasure;
	private int m;
	private Random rng;
	
	public float performance;
	
	public DecisionTree(HashMap<Integer, List<Integer>> _clustering, ImpurityMeasure impurityMeasure, Clustering clustering_original, Clustering clu2, int m, Random rng, float validationInstancesFactor) {
		// TODO: if there are equal feature vectors for different instances the training process might result in an exception!
		this.rng = rng;
		this.m = m;
		
		this.impurityMeasure = impurityMeasure;
		this.num_features = clu2.getFeaturesCount();
		this.features = clu2.getFeatures();
		
		HashMap<Integer, List<Integer>> clustering = new HashMap<Integer, List<Integer>>();
		for (Entry<Integer, List<Integer>> entry : _clustering.entrySet()) {
			List<Integer> list = new LinkedList<Integer>();
			list.addAll(entry.getValue());
			clustering.put(entry.getKey(), list);
		}
		
		HashMap<Integer, List<Integer>> validationData = new HashMap<Integer, List<Integer>>();
		
		if (validationInstancesFactor > 0.f) {
			for (Entry<Integer, List<Integer>> entry : clustering.entrySet()) {
				int n = Math.round(validationInstancesFactor * entry.getValue().size());
				if (n > 0) {
					List<Integer> v = new LinkedList<Integer>();
					validationData.put(entry.getKey(), v);
					for (int i = 0; i < n; i++) {
						int rand = rng.nextInt(entry.getValue().size());
						v.add(entry.getValue().get(rand));
						entry.getValue().remove(rand);
					}
				}
			}
		}
		usedFeatures = new HashSet<Integer>();		
		root = new Node(clustering);
		
		initializeNode(root);
		train(root);
		
		//prune(root);
		
		Integer[] featureIndexes = usedFeatures.toArray(new Integer[0]);
		Arrays.sort(featureIndexes);
		System.out.println("[DecisionTree] Used " + usedFeatures.size() + " features of " + num_features);
		System.out.println("[DecisionTree] Feature indexes are: " + Arrays.toString(featureIndexes));
		if (validationData.size() > 0) {
			float num = 0;
			float perf = 0;
			int timeouts = 0;
			int num_i = 0;
			for (Entry<Integer, List<Integer>> entry : validationData.entrySet()) {
				for (int instanceid : entry.getValue()) {
					int clazz = this.query(features.get(instanceid)).getFirst();
					System.out.println(instanceid + ".. " + clustering_original.getCost(clazz, instanceid) + " : " + clustering_original.getMinimumCost(instanceid));
					if (Float.isInfinite(clustering_original.getCost(clazz, instanceid))) {
						timeouts++;
					} else {
						perf += clustering_original.getCost(clazz, instanceid);
						num += clustering_original.getMinimumCost(instanceid);
					}
					num_i++;
				}
			}
			performance = num / perf;// perf/num;
			System.out.println("[DecisionTree] Timeouts: " + timeouts + " / " + num_i);
			System.out.println("[DecisionTree] #all = " + num);
			System.out.println("[DecisionTree] perf(T) = " + performance);
		}
		//System.out.println("[DecisionTree] cost best - tree: " + cost_best + " - " + cost_tree);
	}
	
	private void prune(Node node, Clustering clu2) {
		if (node.split_attribute != -1) {
			prune(node.left, clu2);
			prune(node.right, clu2);
		}
		
		List<Integer> scids = new LinkedList<Integer>();
		scids.addAll(node.clustering.keySet());
		List<Pair<Integer, Float>> possible_scs = new LinkedList<Pair<Integer, Float>>();
		
		for (int i = 0; i < scids.size(); i++) {
			int scid1 = scids.get(i);
			boolean best = true;

			/*for (int j = 1; j < scids.size(); j++) {
				if (i == j) {
					continue;
				}
				int scid2 = scids.get(j);*/
				for (List<Integer> list : node.clustering.values()) {
					for (int iid : list) {
						if (clu2.getMembership(scid1, iid) / clu2.getMaximumMembership(iid) < 0.9) {
							best = false;
							break;
						}
					}
					if (!best)
						break;
				}
				//if (!best) 
				//	break;
				
		//	}
			if (best) {
				// TODO: what if more than one sc is possible here? -> choose best one..
				float sc1_ms = 0.f;
				
				for (List<Integer> list : node.clustering.values()) {
					for (int iid : list) {
						sc1_ms += clu2.getMembership(scid1, iid);
					}
				}
				
				
				possible_scs.add(new Pair<Integer, Float>(scid1, sc1_ms));
				break;
			}
		}
		if (!possible_scs.isEmpty()) {
			System.out.println("[DecisionTree] PRUNING (" + possible_scs.size() + ").");
			
			int scid = -1;
			float ms = -1.f;
			for (Pair<Integer, Float> p : possible_scs) {
				if (ms < p.getSecond()) {
					scid = p.getFirst();
					ms = p.getSecond();
				}
			}
			
			List<Integer> cluster = new LinkedList<Integer>();
			for (List<Integer> list : node.clustering.values()) {
				cluster.addAll(list);
			}
			node.clustering = new HashMap<Integer, List<Integer>>();
			node.clustering.put(scid, cluster);
			node.left = null;
			node.right = null;
			node.split_attribute = -1;
			node.split = Float.NaN;
		} 
	}

	private void train(Node node) {
		if (node.left == null || node.right == null) {
			return;
		}
		if (node.left.clustering.size() > 1) {
			initializeNode(node.left);
			train(node.left);
		}
		if (node.right.clustering.size() > 1) {
			initializeNode(node.right);
			train(node.right);
		}
	}
	
	public Pair<HashMap<Integer, List<Integer>>, HashMap<Integer, List<Integer>>> split(HashMap<Integer, List<Integer>> clustering, int split_attribute, float split_point) {
		Pair<HashMap<Integer, List<Integer>>, HashMap<Integer, List<Integer>>> res = new Pair<HashMap<Integer, List<Integer>>, HashMap<Integer, List<Integer>>>(new HashMap<Integer, List<Integer>>(), new HashMap<Integer, List<Integer>>());
		for (Integer clazz : clustering.keySet()) {
			for (int elem : clustering.get(clazz)) {
				float[] attr = features.get(elem);
				List<Integer> c;
				if (attr[split_attribute] < split_point) {
					c = res.getFirst().get(clazz);
					if (c == null) {
						c = new LinkedList<Integer>();
						res.getFirst().put(clazz, c);
					}
				} else {
					c = res.getSecond().get(clazz);
					if (c == null) {
						c = new LinkedList<Integer>();
						res.getSecond().put(clazz, c);
					}
				}
				c.add(elem);
			}
		}
		return res;
	}
	
	/**
	 * Cleans up all unnecessary data from the tree.
	 */
	public void cleanup() {
		cleanup(root);
	}
	
	private void cleanup(Node node) {
		if (node.split_attribute == -1) {
			return;
		} else {
			node.clustering = null;
			cleanup(node.left);
			cleanup(node.right);
		}
	}
	
	/**
	 * Calculates the purity gain for the given split.
	 * @param clustering
	 * @param left
	 * @param right
	 * @return
	 */
	public float calculatePurityGain(HashMap<Integer, List<Integer>> clustering, HashMap<Integer, List<Integer>> left, HashMap<Integer, List<Integer>> right) {
		int num_elems = 0;
		int num_elems_left = 0;
		int num_elems_right = 0;
		
		for (List<Integer> list : clustering.values()) {
			num_elems += list.size();
		}
		
		for (List<Integer> list : left.values()) {
			num_elems_left += list.size();
		}
		
		for (List<Integer> list: right.values()) {
			num_elems_right += list.size();
		}
		
		float l = (float) num_elems_left / (float) num_elems;
		float r = (float) num_elems_right / (float) num_elems;
                
		switch (impurityMeasure) {
		case MISCLASSIFICATIONINDEX:
			return getMisclassificationIndex(clustering) - l * getMisclassificationIndex(left) - r * getMisclassificationIndex(right);
		case GINIINDEX:
			return getGiniIndex(clustering) - l * getGiniIndex(left) - r * getGiniIndex(right);
		case ENTROPYINDEX:
			return getEntropyIndex(clustering) - l * getEntropyIndex(left) - r * getEntropyIndex(right);
		default:
			throw new IllegalArgumentException("Unknown impurity measure: " + impurityMeasure.toString());
		}
		
		
	}
	
	// ********* impurity measures **********
	
	/**
	 * Calculates the misclassification index for the values.
	 * @param values
	 * @return
	 */
	public float getMisclassificationIndex(HashMap<Integer, List<Integer>> values) {
		// calculate 1 - max p_j
		
		int num_elems = 0;
		for (List<Integer> list : values.values()) {
			num_elems += list.size();
		}
		float tmp = 0.f;
		
		for (List<Integer> list : values.values()) {
			if ((float) list.size() / (float) num_elems > tmp) {
				tmp = (float) list.size() / (float) num_elems;
			}
		}
		return 1.f - tmp;
	}
	
	/**
	 * Calculates the gini index for the values.
	 * @param values
	 * @return
	 */
	public float getGiniIndex(HashMap<Integer, List<Integer>> values) {
		// calculate 1 - sum p_j^2
		
		int num_elems = 0;
		for (List<Integer> list : values.values()) {
			num_elems += list.size();
		}
		float res = 1.f;
		
		for (List<Integer> list : values.values()) {
			float p = (float) list.size() / (float) num_elems;
			res -= p*p;
		}
		return res;
	}

	/**
	 * Calculates the entropy index for the values.
	 * @param values
	 * @return
	 */
	public float getEntropyIndex(HashMap<Integer, List<Integer>> values) {
		// calculate - sum p_j log p_j
		
		int num_elems = 0;
		for (List<Integer> list : values.values()) {
			num_elems += list.size();
		}
		float res = 0.f;
		
		for (List<Integer> list : values.values()) {
			float p = (float) list.size() / (float) num_elems;
			res -= p * Math.log(p) / Math.log(2);
		}
		return res;
	}
	
	// ******** end of impurity measures *********
	
	private SplitAttribute findOptimalSplitAttribute(HashMap<Integer, List<Integer>> clustering) {
		if (clustering.size() <= 1) {
			throw new IllegalArgumentException("Expected at least two clusters.");
		}
		
		float purityGain = -1.f;
		Pair<HashMap<Integer, List<Integer>>, HashMap<Integer, List<Integer>>> res = null;
		float res_split_point = 0.f;
		int split_attribute = -1;
		
		// this list will contain all instance ids
		List<Integer> elements = new LinkedList<Integer>();
		for (List<Integer> list : clustering.values()) {
			elements.addAll(list);
		}
		
		Set<Integer> possible_attributes = null;
		if (m != -1) {
			possible_attributes = new HashSet<Integer>();
			while (possible_attributes.size() < m) {
				possible_attributes.add(rng.nextInt(num_features));
			}
		}
		
		
		for (int attr = 0; attr < num_features; attr++) {
			int tmp_attr = attr;
			if (m != -1) {
				if (possible_attributes.isEmpty()) {
					break;
				}
				tmp_attr = possible_attributes.iterator().next();
				possible_attributes.remove(tmp_attr);
			}
			final int f_attr = tmp_attr;
			//System.err.println("CURRENT ATTRIBUTE: " + attr);
			
			// sort the list of instance ids by current feature value
			Collections.sort(elements, new Comparator<Integer>() {

				@Override
				public int compare(Integer o1, Integer o2) {
					float val1 = features.get(o1)[f_attr];
					float val2 = features.get(o2)[f_attr];
					
					if (val1 < val2) {
						return -1;
					} else if (val2 < val1) {
						return 1;
					} else {
						return 0;
					}
				}


			});
			
			// try every possible split
			float val1 = 0.f;
			float val2 = features.get(elements.get(0))[f_attr];
			
			for (int i = 1; i < elements.size(); i++) {
				val1 = val2;
				val2 = features.get(elements.get(i))[f_attr];
				float split_point = (val1 + val2) / 2.f;
				Pair<HashMap<Integer, List<Integer>>, HashMap<Integer, List<Integer>>> s = split(clustering, f_attr, split_point);
				if (s.getFirst().isEmpty())
					continue;
				if (s.getSecond().isEmpty())
					continue;
				
				float tmp = calculatePurityGain(clustering, s.getFirst(), s.getSecond());
				if (tmp > purityGain) {
					purityGain = tmp;
					res = s;
					res_split_point = split_point;
					split_attribute = f_attr;
				}
			}
		}
		//System.err.println("LEFT SIZE: " + res.getFirst().size() + "  RIGHT SIZE: " + res.getSecond().size());
		//System.err.println("purity gain = " + purityGain);
		if (res == null) {
			return null;
		}
		return new SplitAttribute(res.getFirst(), res.getSecond(), split_attribute, res_split_point, purityGain);
	}
	
	private void initializeNode(Node node) {
		if (node.clustering.isEmpty()) {
			throw new IllegalArgumentException("clustering.isEmpty() is true");
		}
		if (node.clustering.size() == 1) {
			// nothing to split
			return;
		}
		
		// determine the split attribute
		SplitAttribute sa = findOptimalSplitAttribute(node.clustering);
		
		if (sa == null) {
			// no possible split
			return;
		}
		
		// set split values for this node
		node.split = sa.split;
		node.split_attribute = sa.split_attribute;
		
		node.purityGain = sa.purityGain;
		
		usedFeatures.add(sa.split_attribute);
		
		// create children
		node.left = new Node(sa.leftClustering);
		node.right = new Node(sa.rightClustering);
	}
	
	private Pair<Integer, List<Integer>> query(Node node, float[] features) {
		if (node.split_attribute == -1) {  //node.clustering.size() == 1) {
			// return the cluster, there is only one; TODO: <---- this is not true!!!
			return new Pair<Integer, List<Integer>>(node.clustering.keySet().iterator().next(), node.clustering.entrySet().iterator().next().getValue());
		} else {
			// determine the feature value of the feature vector and query the left/right node accordingly
			float val = features[node.split_attribute];
			if (val < node.split) {
				return query(node.left, features);
			} else {
				return query(node.right, features);
			}
		}
	}
	
	/**
	 * Returns the cluster for the requested feature vector as a pair.<br/>
	 * The first value is the solver configuration id and the second value is a list of instance ids.
	 * @param features feature vector
	 * @return class
	 */
	public Pair<Integer, List<Integer>> query(float[] features) {
		if (features.length != num_features) {
			throw new IllegalArgumentException("Invalid feature vector!");
		}
		return query(root, features);
	}	
	
	private class Node implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = -9078130993009897397L;		
		HashMap<Integer, List<Integer>> clustering;
		Node left, right;
		int split_attribute;
		float split;
		float purityGain;

		public Node(HashMap<Integer, List<Integer>> clustering) {
			this.left = null;
			this.right = null;
			this.clustering = clustering;
			this.split_attribute = -1;
			this.split = Float.NaN;
			this.purityGain = Float.NaN;
		}
	}
	
	private class SplitAttribute {
		//double stdDevReduction = 0.;
		HashMap<Integer, List<Integer>> leftClustering;
		HashMap<Integer, List<Integer>> rightClustering;
		int split_attribute;
		float split;
		float purityGain;
		
		public SplitAttribute(HashMap<Integer, List<Integer>> leftClustering, HashMap<Integer, List<Integer>> rightClustering, int split_attribute, float split, float purityGain) {
			this.leftClustering = leftClustering;
			this.rightClustering = rightClustering;
			this.split_attribute = split_attribute;
			this.split = split;
			this.purityGain = purityGain;
		}
	}
	
	
	public int printDotNodeDescription(Node node, int nodenum, BufferedWriter br) throws Exception {
		String name = ""; 
		//name = "";//node.toString();
		
		if (node.left == null) {
			name += " classes: " + node.clustering.keySet() + " i: [";
			for (Entry<Integer, List<Integer>> e : node.clustering.entrySet()) {
				//name += "(" + e.getKey() + "," + e.getValue().size() +")";
				name += "(" + e.getKey()+ ":";
				for (Integer ii : e.getValue()) {
					//name += "(" + ii + "," + clu2.getCost(e.getKey(), ii) + ") ";
					name += ii + ",";
				}
				name += ")";
			}
			name += "]";
		} else {
			name += "Split: " + node.split + " Attr: " + node.split_attribute;
		}
		//name += ", " + node.stddev;
		br.write("N" + nodenum + " [label=\"" + name + "\"];\n");
		int nextNode = nodenum+1;
		if (node.left != null) {
			int nn = nextNode;
			nextNode = printDotNodeDescription(node.left, nextNode, br);
			br.write("N" + nodenum + " -- N" + nn + ";\n");
			nn = nextNode;
			nextNode = printDotNodeDescription(node.right, nextNode, br);
			br.write("N" + nodenum + " -- N" + nn + ";\n");
			/*if (!node.nullNode.results.isEmpty()) {
				nn = nextNode;
				nextNode = printDotNodeDescription(node.nullNode, nextNode, br);
				br.write("N" + nodenum + " -- N" + nn + ";\n");
			}*/
		}
		return nextNode;
	}
	
	
	public void printDot(File file) throws Exception {
		BufferedWriter br = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
		br.write("graph g {\n");
		printDotNodeDescription(root, 1, br);
		br.write("}\n");
		br.close();
	}
	
	public static void main(String[] args) throws Exception {
		HashMap<Integer, List<Integer>> clustering = new HashMap<Integer, List<Integer>>();
		HashMap<Integer, float[]> features = new HashMap<Integer, float[]>();
		int n = 2;
		ImpurityMeasure measure = ImpurityMeasure.ENTROPYINDEX;
		
		List<Integer> l1 = new LinkedList<Integer>();
		l1.add(100);
		features.put(100, new float[] {0.2f, 0.8f});
		l1.add(101);
		features.put(101, new float[] {0.2f, 0.9f});
		l1.add(102);
		features.put(102, new float[] {0.2f, 0.1f});
		List<Integer> l2 = new LinkedList<Integer>();
		l2.add(103);
		features.put(103, new float[] {0.2f, 0.2f});
		l2.add(104);
		features.put(104, new float[] {0.2f, 0.3f});
		clustering.put(0, l1);
		clustering.put(1, l2);
		//DecisionTree tree = new DecisionTree(clustering, features, n, measure);
		//tree.printDot(new File("D:\\dot\\bla.dot"));
	}
}
