package edacc.configurator.aac.racing.challenge;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Comparator;
import edacc.util.Pair;

public class DecisionTree implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 3264367978634L;

	public enum ImpurityMeasure {
		MISSCLASSIFICATIONINDEX, GINIINDEX, ENTROPYINDEX
	}
	
	private Node root;
	private int num_features;
	private HashMap<Integer, float[]> features;	
	private HashSet<Integer> usedFeatures;	
	private ImpurityMeasure impurityMeasure;
	
	public DecisionTree(HashMap<Integer, List<Integer>> clustering, HashMap<Integer, float[]> features, int num_features, ImpurityMeasure impurityMeasure) {
		
		// TODO: if there are equal feature vectors for different instances the training process might result in an exception!
		
		this.impurityMeasure = impurityMeasure;
		this.num_features = num_features;
		this.features = features;
		
		usedFeatures = new HashSet<Integer>();		
		root = new Node(clustering);
		
		initializeNode(root);
		train(root);
		Integer[] featureIndexes = usedFeatures.toArray(new Integer[0]);
		Arrays.sort(featureIndexes);
		System.out.println("[DecisionTree] Used " + usedFeatures.size() + " features of " + num_features);
		System.out.println("[DecisionTree] Feature indexes are: " + Arrays.toString(featureIndexes));
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
		case MISSCLASSIFICATIONINDEX:
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
	
	public float getEntropyIndex(HashMap<Integer, List<Integer>> values) {
		// calculate - sum p_j log p_j
		
		int num_elems = 0;
		for (List<Integer> list : values.values()) {
			num_elems += list.size();
		}
		float res = 0.f;
		
		for (List<Integer> list : values.values()) {
			float p = (float) list.size() / (float) num_elems;
			res -= p * Math.log(p);
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
		
		List<Integer> elements = new LinkedList<Integer>();
		for (List<Integer> list : clustering.values()) {
			elements.addAll(list);
		}
		
		for (int attr = 0; attr < num_features; attr++) {
			final int f_attr = attr;
			//System.err.println("CURRENT ATTRIBUTE: " + attr);
			
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
		return new SplitAttribute(res.getFirst(), res.getSecond(), split_attribute, res_split_point);
	}
	
	private void initializeNode(Node node) {
		if (node.clustering.isEmpty()) {
			throw new IllegalArgumentException("results.isEmpty() is true");
		}
		if (node.clustering.size() == 1) {
			return;
		}
		
		SplitAttribute sa = findOptimalSplitAttribute(node.clustering);
		
		node.split = sa.split;
		node.split_attribute = sa.split_attribute;
		
		usedFeatures.add(sa.split_attribute);
		
		node.left = new Node(sa.leftClustering);
		node.right = new Node(sa.rightClustering);
	}
	
	private Pair<Integer, List<Integer>> query(Node node, float[] features) {
		if (node.clustering.size() == 1) {
			return new Pair<Integer, List<Integer>>(node.clustering.keySet().iterator().next(), node.clustering.entrySet().iterator().next().getValue());
		} else {
			float val = features[node.split_attribute];
			if (val < node.split) {
				return query(node.left, features);
			} else {
				return query(node.right, features);
			}
		}
	}
	
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

		public Node(HashMap<Integer, List<Integer>> clustering) {
			this.left = null;
			this.right = null;
			this.clustering = clustering;
			this.split_attribute = -1;
			this.split = Float.NaN;
		}
	}
	
	private class SplitAttribute {
		//double stdDevReduction = 0.;
		HashMap<Integer, List<Integer>> leftClustering;
		HashMap<Integer, List<Integer>> rightClustering;
		int split_attribute;
		float split;
		
		public SplitAttribute(HashMap<Integer, List<Integer>> leftClustering, HashMap<Integer, List<Integer>> rightClustering, int split_attribute, float split) {
			this.leftClustering = leftClustering;
			this.rightClustering = rightClustering;
			this.split_attribute = split_attribute;
			this.split = split;
		}
	}
}
