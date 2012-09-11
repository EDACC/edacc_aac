package edacc.configurator.aac.solvercreator;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import edacc.model.InstanceClassMustBeSourceException;
import edacc.model.ParameterInstanceDAO;
import edacc.model.SolverConfiguration;
import edacc.model.SolverConfigurationDAO;
import edacc.model.SolverDAO;
import edacc.util.Pair;

/**
 * 
 * @author simon
 *
 */
public class Clustering implements Serializable {
	
	public enum HierarchicalClusterMethod {
		SINGLE_LINKAGE, COMPLETE_LINKAGE, AVERAGE_LINKAGE
	}
	
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 2847802957451633450L;
	
	private float weighted_alpha = 1.2f;
	
	private int n; // number of instances = I.size();
	protected HashMap<Integer, Integer> I; // maps instance id to column
	
	protected HashMap<Integer, float[]> M; // Membership Matrix M, maps sc id to instance row vector
	protected HashMap<Integer, float[]> C; // Cost Matrix C, maps sc id to cost for instance vector
	//HashMap<Integer, Float> W; // relative weight of sc
	transient private float[] K; // K_i = sum over solver configs for instance row i (ith instance)
	
	// cache for data to be updated before using matrix M
	transient private boolean solverConfigsRemoved;
	transient private HashSet<Integer> update_columns;
	
	protected HashMap<Integer, float[]> F; // feature vectors for instances
	protected HashMap<Integer, String> P; // parameter lines
	
	private Clustering(HashMap<Integer, float[]> featureMapping) {
		I = new HashMap<Integer, Integer>();
		
		// generate empty membership, cost and weight matrices
		M = new HashMap<Integer, float[]>();
		C = new HashMap<Integer, float[]>();
		//W = new HashMap<Integer, Float>();
		
		// initialize data to be updated
		update_columns = new HashSet<Integer>();
		solverConfigsRemoved = false;
		
		F = featureMapping;
		P = new HashMap<Integer, String>();
	}
	
	public Clustering(Clustering other) {
		this(other.F);
		for (Entry<Integer, Integer> entry : other.I.entrySet()) {
			I.put(entry.getKey(), entry.getValue());
		}
		n = other.n;
		for (Entry<Integer, float[]> entry : other.M.entrySet()) {
			float[] values = entry.getValue();
			M.put(entry.getKey(), Arrays.copyOf(values, values.length));
		}
		for (Entry<Integer, float[]> entry : other.C.entrySet()) {
			float[] values = entry.getValue();
			C.put(entry.getKey(), Arrays.copyOf(values, values.length));
		}
		update_columns.addAll(other.update_columns);
		solverConfigsRemoved = other.solverConfigsRemoved;
		for (Entry<Integer, String> entry : other.P.entrySet()) {
			P.put(entry.getKey(), entry.getValue());
		}
		K = Arrays.copyOf(other.K, n);
	}
	
	public int getFeaturesCount() {
		if (F.isEmpty()) {
			return 0;
		} else {
			return F.values().iterator().next().length;
		}
	}
	
	public HashMap<Integer, float[]> getFeatures() {
		return F;
	}
	
	/**
	 * Creates new Clustering object with the given instance ids. Instance ids cannot be changed later.
	 * @param instanceIds the instance ids to be clustered
	 * @throws SQLException 
	 * @throws InstanceClassMustBeSourceException 
	 */
	public Clustering(List<Integer> instanceIds, HashMap<Integer, float[]> featureMapping) {
		this(featureMapping);
		
		// generate the column mapping for M,C matrices
		int row = 0;
		for (Integer id: instanceIds) {
			I.put(id, row++);
		}
		n = I.size();
		
		// initialize K
		K = new float[n];
		for (int i = 0; i < n; i++) {
			K[i] = 0.f;
		}
	}

	public void updateData() {
		if (solverConfigsRemoved) {
                    solverConfigsRemoved = false;
			update_columns.addAll(I.values());
		}
                if (update_columns.isEmpty())
                    return;
		for (int column : update_columns) {
			// Update M entry
			{
				int scs = 0;
				float sum = 0.f;
				float max = 0.f;
				for (float[] tmp : C.values()) {
					float t = tmp[column];
					if (!Float.isInfinite(tmp[column])) {
					//	tmp[column] = (float) Math.log(tmp[column]);
						if (tmp[column] < 0.f) tmp[column] = 0.f;
					}
					
					if (!Float.isInfinite(tmp[column])) {
						sum += tmp[column];
						scs++;
						if (tmp[column] > max) {
							max = tmp[column];
						}
					}
					
					tmp[column] = t;
				}

				for (int tmp_scid : M.keySet()) {
					float[] tmp_c = C.get(tmp_scid);
					float[] tmp_m = M.get(tmp_scid);
					
					float t = tmp_c[column];
					if (!Float.isInfinite(tmp_c[column])) {
					//	tmp_c[column] = (float) Math.log(tmp_c[column]);
						if (tmp_c[column] < 0.f) tmp_c[column] = 0.f;
					}
					
					if (Float.isInfinite(tmp_c[column])) {
						tmp_m[column] = 0.f;
					} else {
						// TODO: eps
						float eps = 0.0001f;
						if (scs * max - sum > eps) {
							tmp_m[column] = (max * weighted_alpha - tmp_c[column]) / (scs * max * weighted_alpha - sum);
						} else {
							tmp_m[column] = 1.f / scs;
						}
						// TODO: maximize: tmp_c[column] / sum
					}
					
					tmp_c[column] = t;
				}
			}

			// Update K entry
			float sum = 0.f;
			for (float[] tmp : M.values()) {
				sum += tmp[column];
			}
			K[column] = sum;
		}
		update_columns.clear();
	}
	

	/**
	 * Updates the cost of the given solver configuration on the given instance.
	 * @param scid the id of the solver configuration
	 * @param instanceid the id of the instance
	 * @param cost the cost, can be FLOAT.POSITIVE_INFINITY, but must be greater or equal zero.
	 */
	public void update(int scid, int instanceid, float cost) {
		if (!M.containsKey(scid)) {
			// new solver configuration, first cost
			// initial M row is empty for all instances
			float[] f = new float[n];
			for (int i = 0; i < n; i++) {
				f[i] = 0.f;
			}
			M.put(scid, f);
			// initial C row is infinity for all instances
			f = new float[n];
			for (int i = 0; i < n; i++) {
				f[i] = Float.POSITIVE_INFINITY;
			}
			C.put(scid, f);
			
			// initial weight is 0.
			//W.put(scid, 0.f);
			
			// get parameter line
			try {
				SolverConfiguration sc = SolverConfigurationDAO.getSolverConfigurationById(scid);
				P.put(scid, edacc.experiment.Util.getSolverParameterString(ParameterInstanceDAO.getBySolverConfig(sc), SolverDAO.getById(sc.getSolverBinary().getIdSolver())));
			} catch (Exception ex) {
				System.err.println("WARNING: Could not add parameter line for solver configuration " + scid);
			}
		}
		int column = I.get(instanceid);
		float[] c = C.get(scid);
		
		// Update C entry
		c[column] = cost;
		
		// Add to data to be updated, invalidates the column of matrix M
		update_columns.add(column);
	}
	
	/**
	 * Removes the specified solver configuration.
	 * @param scid
	 */
	public void remove(int scid) {
		M.remove(scid);
		C.remove(scid);
		solverConfigsRemoved = true;
	}
	
	public float performance(HashMap<Integer, List<Integer>> clustering) {
		float res = 0.f;

		float[] maxValues = new float[n];
		for (int i = 0; i < n; i++) maxValues[i] = 0.f;
		
		for (Entry<Integer, float[]> entry : M.entrySet()) {
			for (int i = 0; i < n; i++) {
				if (entry.getValue()[i] > maxValues[i]) {
					maxValues[i] = entry.getValue()[i];
				}
			}
		}
		
		float max = 0.f;
		for (float f : maxValues) {
			max += f;
		}
		if (max < 0.001f) {
			return 0.f;
		}
		
		for (Entry<Integer, List<Integer>> entry : clustering.entrySet()) {
			for (Integer instanceId : entry.getValue()) {
				int col = I.get(instanceId);
				float val = M.get(entry.getKey())[col];
				res += val;
			}
		}
		return res / max;
	}
	
	
	public HashMap<Integer, List<Integer>> getClusteringGreedy(Random rng) {
		updateData();
		HashMap<Integer, List<Integer>> res = new HashMap<Integer, List<Integer>>();
		List<Integer> instanceIds = new LinkedList<Integer>();
		instanceIds.addAll(I.keySet());
		
		HashMap<Integer, Pair<Integer, Float>> maxSCIdValues = new HashMap<Integer, Pair<Integer, Float>>();
		for (int i = instanceIds.size()-1; i >= 0; i --) {
			int instanceId = instanceIds.get(i);
			int scid = -1;
			float max = 0.f;
			for (int tmp_scid : M.keySet()) {
				float[] m = M.get(tmp_scid);
				if (m[I.get(instanceId)] > max) {
					max = m[I.get(instanceId)];
					scid = tmp_scid;
				}
			}
			if (scid != -1) {
				maxSCIdValues.put(instanceId, new Pair<Integer, Float>(scid, max));
			} else {
				instanceIds.remove(i);
			}
		}
		
		while (!instanceIds.isEmpty()) {
			int rand = rng.nextInt(instanceIds.size());
			int instanceId = instanceIds.get(rand);
			instanceIds.remove(rand);
			int scid = maxSCIdValues.get(instanceId).getFirst();
			maxSCIdValues.remove(instanceId);
			List<Integer> cluster = new LinkedList<Integer>();
			cluster.add(instanceId);
			res.put(scid, cluster);
			
			for (int i = instanceIds.size()-1; i >= 0; i--) {
				int insId = instanceIds.get(i);
				float val = M.get(scid)[I.get(insId)];
				float max = maxSCIdValues.get(insId).getSecond();
				// TODO: multiplicator
				if (val * 1.5f >= max) {
					cluster.add(insId);
					instanceIds.remove(i);
					maxSCIdValues.remove(insId);
				}
			}
		}
		
		return res;
	}
	
	public HashMap<Integer, List<Integer>> getClustering(boolean removeSmallClusters, float threshold) {
		List<Pair<Integer, Float>> scidWeight = new LinkedList<Pair<Integer, Float>>();
		for (int scid : M.keySet()) {
			scidWeight.add(new Pair<Integer, Float>(scid, getWeight(scid)));
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
		HashMap<Integer, List<Integer>> res = new HashMap<Integer, List<Integer>>();
		HashSet<Integer> scids = new HashSet<Integer>();
		scids.addAll(M.keySet());
		for (int i = 0; i < scidWeight.size(); i++) {
			HashMap<Integer, List<Integer>> tmp = getClustering(false, scids);
			if (performance(tmp) >= threshold) {
				res = tmp;
			} else {
				break;
			}
			scids.remove(scidWeight.get(i).getFirst());
		}
		
		return res;
	}
	
	/**
	 * Returns a clustering for the current membership matrix. Maps solver configuration id to a list of instance ids (the cluster).
	 * @param removeSmallClusters if true, tries to remove small clusters by moving instances from small clusters to other clusters.
	 * @return
	 */
	public HashMap<Integer, List<Integer>> getClustering(boolean removeSmallClusters) {
		return getClustering(removeSmallClusters, M.keySet());
	}

	public HashMap<Integer, List<Integer>> getClustering(boolean removeSmallClusters, Set<Integer> solverConfigs) {
		// for mindist method
		if (update_columns != null) {
			updateData();
		}
		HashMap<Integer, List<Integer>> res = new HashMap<Integer, List<Integer>>();
		for (int instanceid : I.keySet()) {
			float max = 0.f;
			int scid = -1;
			for (int tmp_scid : solverConfigs) {
				float[] m = M.get(tmp_scid);
				if (m[I.get(instanceid)] > max) {
					max = m[I.get(instanceid)];
					scid = tmp_scid;
				}
			}
			if (scid != -1) {
				List<Integer> instanceids = res.get(scid);
				if (instanceids == null) {
					instanceids = new LinkedList<Integer>();
					res.put(scid, instanceids);
				}
				instanceids.add(instanceid);
			}
			
		}
		if (removeSmallClusters) {
			boolean found = false;
			do {
				found = false;
				int rmscid = -1;
				for (int scid : res.keySet()) {
					List<Integer> instances = res.get(scid);
					if (instances.size() < 5) {
						float max = 0.f;
						int new_scid = -1;
						for (int instanceid : instances) {
							for (int tmp_scid : res.keySet()) {
								if (tmp_scid == scid) {
									continue;
								}
								float[] m = M.get(tmp_scid);
								if (m[I.get(instanceid)] > max) {
									found = true;
									max = m[I.get(instanceid)];
									new_scid = tmp_scid;
								}
							}
							if (new_scid != -1) {
								res.get(new_scid).add(instanceid);
							} else {
								// TODO: DON'T REMOVE
							}
						}
					}
					if (found) {
						rmscid = scid;
						break;
					}

				}
				if (found) {
					res.remove(rmscid);
				}
			} while (found);
		}
		return res;
	}
	
	private float getCumulatedWeight() {
		// TODO: cache cumulated weight, not really a problem.
		float sum = 0.f;
		for (float[] m : M.values()) {
			for (int i = 0; i < n; i++) {
				sum += m[i];
			}
		}
		return sum;
	}
	
	private float getAbsoluteWeight(int scid) {
		float[] m = M.get(scid);
		float res = 0.f;
		for (int i = 0; i < n; i++) {
			res += m[i];
		}
		return res;
	}
	
	public boolean contains(int scid) {
		updateData();
		return M.containsKey(scid);
	}
	
	public List<Integer> getSolverConfigIds() {
		LinkedList<Integer> res = new LinkedList<Integer>();
		res.addAll(M.keySet());
		return res;
	}
	
	/**
	 * Returns a value between 0.f and 1.f. This is the weight for the given solver configuration where bigger is better.
	 * @param scid the solver configuration id
	 * @return the weight for the solver configuration
	 */
	public float getWeight(int scid) {
		updateData();
		return getAbsoluteWeight(scid) / getCumulatedWeight();
	}
	
	/**
	 * Returns a list of instance ids where costs are infinity for all solver configurations to be considered.
	 * @return
	 */
	public HashSet<Integer> getNotUsedInstances() {
		updateData();
		HashSet<Integer> res = new HashSet<Integer>();
		for (int instanceid: I.keySet()) {
			int column = I.get(instanceid);
			// K[column] is in {0,1}
			if (K[column] < 0.5f) {
				res.add(instanceid);
			}
		}
		return res;
	}
	
	public float getMembership(int scid, int instanceid) {
		updateData();
		Integer col = I.get(instanceid);
		if (col == null) {
			return 0.f;
		}
		float[] values = M.get(scid);
		if (values == null) {
			return 0.f;
		}
		return values[col];
	}
	
	public float getMaximumMembership(int instanceid) {
		updateData();
		Integer col = I.get(instanceid);
		if (col == null) {
			return 0.f;
		}
		float res = 0.f;
		for (float[] values : M.values()) {
			if (values[col] > res) {
				res = values[col];
			}
		}
		return res;
	}
	
	public float getMinimumCost(int instanceid) {
		updateData();
		Integer col = I.get(instanceid);
		if (col == null) {
			return Float.POSITIVE_INFINITY;
		}
		float res = Float.POSITIVE_INFINITY;
		for (float[] values : C.values()) {
			if (values[col] < res) {
				res = values[col];
			}
		}
		return res;
	}
	
	public float getCost(int scid, int instanceid) {
		updateData();
		Integer col = I.get(instanceid);
		if (col == null) {
			return 0.f;
		}
		float[] values = C.get(scid);
		if (values == null) {
			return 0.f;
		}
		return values[col];
	}
	
	/**
	 * Prints the membership matrix in a formatted way.
	 */
	public void printM() {
		updateData();
		System.out.println("Membership Matrix M: " + M.size() + " rows (scs) / " + n + " columns (instances)");
		System.out.println();
		HashMap<Integer, Integer> tmp = new HashMap<Integer, Integer>();
		for (int instanceid : I.keySet()) {
			tmp.put(I.get(instanceid), instanceid);
		}
		System.out.printf("%9s", "sc_id");
		for (int i = 0; i < n; i++) {
			System.out.printf("%9d", (int) tmp.get(i));
		}
		System.out.printf("%9s\n", "weight");
		for (int scid : M.keySet()) {
			float weight = getWeight(scid);
			if (weight <= 0.0f)
				continue;
			float[] m = M.get(scid);
			System.out.printf("%9d", scid);
			for (int i = 0; i < n; i++) {
				System.out.printf("%9f", m[i]);
			}
			System.out.printf("%9f\n", weight);
		}
		System.out.println();
		System.out.printf("%9s", "");
		for (int i = 0; i < n; i++) {
			System.out.printf("%9f", K[i]);
		}
		System.out.println();
	}
	
	public float getDistance(int i1, int i2) {
		updateData();
		
		List<Pair<Integer, Float>> l1 = new LinkedList<Pair<Integer, Float>>();
		List<Pair<Integer, Float>> l2 = new LinkedList<Pair<Integer, Float>>();
		
		for (Entry<Integer, float[]> row : M.entrySet()) {
			l1.add(new Pair<Integer, Float>(row.getKey(), row.getValue()[I.get(i1)]));
			l2.add(new Pair<Integer, Float>(row.getKey(), row.getValue()[I.get(i2)]));
		}
		Comparator<Pair<Integer, Float>> comp = new Comparator<Pair<Integer, Float>>() {

			@Override
			public int compare(Pair<Integer, Float> arg0, Pair<Integer, Float> arg1) {
				if (arg0.getSecond() < arg1.getSecond()) {
					return 1;
				} else if (arg1.getSecond() < arg0.getSecond()) {
					return -1;
				} else {
					return 0;
				}
			}
		};
		
		Collections.sort(l1, comp);
		Collections.sort(l2, comp);
		
		int dist1 = 0, dist2 = 0;
		
		for (int i = 0; i < l1.size(); i++) {
			if (l2.get(i).getFirst().equals(l1.get(0).getFirst())) {
				dist1 = i;
				break;
			}
		}
		for (int i = 0; i < l1.size(); i++) {
			if (l1.get(i).getFirst().equals(l2.get(0))) {
				dist2 = i;
				break;
			}
		}
		
		return ((float) dist1 + (float) dist2) / 2.f;
	}
	
	public HashMap<Pair<Integer, Integer>, Float> getDistanceMatrix() {
		updateData();
		
		HashMap<Pair<Integer, Integer>, Float> res = new HashMap<Pair<Integer, Integer>, Float>();
		for (int i1 : I.keySet()) {
			for (int i2 : I.keySet()) {
				res.put(new Pair<Integer, Integer>(i1, i2), getDistance(i1,i2));
			}
		}
		return res;
	}
	
	private float getDistanceSingleLinkage(List<Integer> c1, List<Integer> c2, HashMap<Pair<Integer, Integer>, Float> distanceMatrix) {
		updateData();
		
		float dist = Float.MAX_VALUE;
		for (int i = 0; i < c1.size(); i++) {
			for (int j = 0; j < c2.size(); j++) {
				float tmp = distanceMatrix.get(new Pair<Integer, Integer>(c1.get(i), c2.get(j)));
				if (tmp < dist) {
					dist = tmp;
				}
			}
		}
		return dist;
	}
	
	private float getDistanceCompleteLinkage(List<Integer> c1, List<Integer> c2, HashMap<Pair<Integer, Integer>, Float> distanceMatrix) {
		updateData();
		
		float dist = 0.f;
		for (int i = 0; i < c1.size(); i++) {
			for (int j = 0; j < c2.size(); j++) {
				float tmp = distanceMatrix.get(new Pair<Integer, Integer>(c1.get(i), c2.get(j)));
				if (tmp > dist) {
					dist = tmp;
				}
			}
		}
		return dist;
	}
	
	private float getDistanceAverageLinkage(List<Integer> c1, List<Integer> c2, HashMap<Pair<Integer, Integer>, Float> distanceMatrix) {
		updateData();
		
		float dist = 0.f;
		for (int i = 0; i < c1.size(); i++) {
			for (int j = 0; j < c2.size(); j++) {
				dist += distanceMatrix.get(new Pair<Integer, Integer>(c1.get(i), c2.get(j)));
			}
		}
		return dist / (float) (c1.size() + c2.size());
	}
	
	public int getBestConfigurationForCluster(List<Integer> cluster) {
		updateData();
		
		float weight = 0.f;
		int res = -1;
		for (Entry<Integer, float[]> entry : M.entrySet()) {
			float tmp = 0.f;
			for (int instanceid : cluster) {
				tmp += entry.getValue()[I.get(instanceid)];
			}
			if (tmp > weight) {
				weight = tmp;
				res = entry.getKey();
			}
		}
		return res;
	}
	
	public HashMap<Integer, List<Integer>> getClusteringHierarchical(HierarchicalClusterMethod method, int k) {
		updateData();
		
		List<List<Integer>> c = new LinkedList<List<Integer>>();
		for (int instanceid : I.keySet()) {
			if (K[I.get(instanceid)] > 0.5f) {
				List<Integer> tmp = new LinkedList<Integer>();
				tmp.add(instanceid);
				c.add(tmp);
			}
		}
		HashMap<Pair<Integer, Integer>, Float> matrix = getDistanceMatrix();
		while (c.size() > k) {
			int m1 = -1, m2 = -1;
			float dist = Float.MAX_VALUE;
			for (int i = 0; i < c.size(); i++) {
				for (int j = i+1; j < c.size(); j++) {
					float tmp;
					if (method.equals(HierarchicalClusterMethod.COMPLETE_LINKAGE)) {
						tmp = getDistanceCompleteLinkage(c.get(i), c.get(j), matrix);
					} else if (method.equals(HierarchicalClusterMethod.SINGLE_LINKAGE)) {
						tmp = getDistanceSingleLinkage(c.get(i), c.get(j), matrix);
					} else if (method.equals(HierarchicalClusterMethod.AVERAGE_LINKAGE)) {
						tmp = getDistanceAverageLinkage(c.get(i), c.get(j), matrix);
					} else {
						throw new IllegalArgumentException("Unknown hierarchical clustering method: " + method);
					}
					
					if (tmp < dist) {
						dist = tmp;
						m1 = i;
						m2 = j;
					}
				}
			}
			c.get(m1).addAll(c.get(m2));
			c.remove(m2);
		}
		
		HashMap<Integer, List<Integer>> res = new HashMap<Integer, List<Integer>>();
		for (List<Integer> cluster : c) {
			int id = getBestConfigurationForCluster(cluster);
			if (res.containsKey(id)) {
				res.get(id).addAll(cluster);
			} else {
				res.put(getBestConfigurationForCluster(cluster), cluster);
			}
		}
		return res;
	}
}
