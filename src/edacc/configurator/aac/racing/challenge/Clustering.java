package edacc.configurator.aac.racing.challenge;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
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
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import edacc.api.API;
import edacc.api.APIImpl;
import edacc.api.costfunctions.CostFunction;
import edacc.api.costfunctions.PARX;
import edacc.model.ConfigurationScenarioDAO;
import edacc.model.Experiment.Cost;
import edacc.model.ExperimentResult;
import edacc.model.ExperimentResultDAO;
import edacc.model.Instance;
import edacc.model.InstanceClassMustBeSourceException;
import edacc.model.InstanceDAO;
import edacc.model.InstanceHasProperty;
import edacc.model.InstanceNotInDBException;
import edacc.model.NoConnectionToDBException;
import edacc.model.ParameterInstanceDAO;
import edacc.model.SolverConfiguration;
import edacc.model.SolverConfigurationDAO;
import edacc.model.SolverDAO;
import edacc.parameterspace.ParameterConfiguration;
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
	protected HashMap<Integer, Set<Integer>> seeds;
	
	
	protected DecisionTree tree;
	
	protected RandomForest forest;
	
	protected edacc.configurator.aac.racing.challenge.test.DecisionTree tree2;
	
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
		seeds = new HashMap<Integer, Set<Integer>>();
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
		for (Entry<Integer, Set<Integer>> entry : other.seeds.entrySet()) {
			Set<Integer> copy = new HashSet<Integer>();
			copy.addAll(entry.getValue());
			seeds.put(entry.getKey(), copy);
		}
		K = Arrays.copyOf(other.K, n);
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

	private void updateData() {
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
	
	public static void serialize(String file, Clustering C) throws FileNotFoundException, IOException {
		ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(new File(file)));
		os.writeUnshared(C);
		os.close();
	}
	
	public static Clustering deserialize(String file) throws FileNotFoundException, IOException, ClassNotFoundException {
		ObjectInputStream is = new ObjectInputStream(new FileInputStream(new File(file)));
		Clustering c = (Clustering) is.readUnshared();
		is.close();
		return c;
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
	
	/**
	 * Test method for Clustering class.
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		API api = new APIImpl();
		
		if (args.length < 1) {
			System.out.println("Usage: SolverCreator.jar <settings.properties file>");
			return;
		}
		
		// load user specified properties
		Properties properties = new Properties();
		// general settings
		File settingsFile = new File(args[0]);
		if (settingsFile.exists()) {
			if (!settingsFile.exists()) {
				System.err.println("settings.properties not found.");
				return;
			}
			InputStream in = new FileInputStream(settingsFile);
			properties.load(in);
			in.close();
		} else {

			InputStream in = Clustering.class.getResourceAsStream("settings.properties");
			if (in != null) {
				// starting within eclipse
				properties.load(in);
				in.close();
				// private settings (not in repository)
				in = Clustering.class.getResourceAsStream("private.properties");
				if (in != null) {
					properties.load(in);
					in.close();
				}
			}

		}
		
		// feature specific settings
		final String featureDirectory = properties.getProperty("FeatureDirectory");
		InputStream in = new FileInputStream(new File(new File(featureDirectory), "features.properties"));
		if (in != null) {
			properties.load(in);
			in.close();
		}
		String featuresName = properties.getProperty("FeaturesName");
		String featuresCacheDirectory = properties.getProperty("FeatureCacheDirectory");
		final File featuresCacheFolder = new File(new File(featuresCacheDirectory), featuresName);
		
		boolean loadClustering = Boolean.parseBoolean(properties.getProperty("LoadSerializedClustering"));
		
		// extract feature names
		String[] feature_names = properties.getProperty("Features").split(",");
		
		int expid = Integer.parseInt(properties.getProperty("ExperimentId"));
		
		// connect to database
		api.connect(properties.getProperty("DBHost"), Integer.parseInt(properties.getProperty("DBPort")), properties.getProperty("DB"), properties.getProperty("DBUser"), properties.getProperty("DBPassword"), Boolean.parseBoolean(properties.getProperty("DBCompress")));
		
		LinkedList<Instance> instances = null;
		Clustering C = null;
		if (!loadClustering) {
			// load instances and determine instance ids
			instances = InstanceDAO.getAllByExperimentId(expid);
			
			/*for (int i = instances.size()-1; i>= 0; i--) {
				if (!instances.get(i).getName().contains("k4")) {
					instances.remove(i);
				}
			}*/
			
			List<Integer> instanceIds = new LinkedList<Integer>();
			for (Instance i : instances) {
				instanceIds.add(i.getId());
			}

			final HashMap<Integer, float[]> featureMapping = new HashMap<Integer, float[]>();

			// calculate features
			if (Boolean.parseBoolean(properties.getProperty("UseFeaturesFromDB"))) {
				for (Integer id : instanceIds) {
					float[] f = new float[feature_names.length];
					HashMap<String, Float> tmp = new HashMap<String, Float>();
					Instance instance = InstanceDAO.getById(id);
					for (InstanceHasProperty ihp : instance.getPropertyValues().values()) {
						try {
							tmp.put(ihp.getProperty().getName(), Float.parseFloat(ihp.getValue()));
						} catch (Exception ex) {
						}
					}
					for (int i = 0; i < feature_names.length; i++) {
						Float val = tmp.get(feature_names[i]);
						if (val == null) {
							System.err.println("WARNING: Did not find feature value for name " + feature_names[i] + " and instance " + instance.getId());
							f[i] = 0.f;
						} else {
							f[i] = val;
						}
					}
					featureMapping.put(id, f);
				}
			} else {
				final int size = instanceIds.size();
				final LinkedList<Integer> instanceIdsToGo = new LinkedList<Integer>();
				instanceIdsToGo.addAll(instanceIds);
				int cores = Runtime.getRuntime().availableProcessors();
				Thread[] threads = new Thread[cores];
				System.out.println("Starting " + cores + " threads for property calculation");
				
			/*	for (int i = 0; i < cores; i++) {
					threads[i] = new Thread(new Runnable() {
						
						@Override
						public void run() {
							while (true) {
								int id;
								synchronized (instanceIdsToGo) {
									if (instanceIdsToGo.isEmpty()) {
										break;
									}
									id = instanceIdsToGo.poll();
								}
								try {
									float[] features = calculateFeatures(id, new File(featureDirectory), featuresCacheFolder);
									synchronized (instanceIdsToGo) {
										featureMapping.put(id, features);
										System.out.println("Calculated feature vector " + featureMapping.size() + " / " + size);
									}
								} catch (Exception ex) {
									ex.printStackTrace();
									System.err.println("Error while calculating features for instance " + id);
								}
							}
						}
						
					});
				}
				
				for (Thread thread : threads) {
					thread.start();
				}
				
				for (Thread thread : threads) {
					thread.join();
				}*/
				System.out.println("Done.");
				
				// remove me
				
			/*	float[] normalize = new float[featureMapping.entrySet().iterator().next().getValue().length];
				for (int i = 0; i < normalize.length; i++) {
					normalize[i] = 0.f;
				}
				
				for (int iid : instanceIds) {
					float[] f = featureMapping.get(iid);
					for (int i = 0; i < normalize.length; i++) {
						if (normalize[i] < f[i]) {
							normalize[i] = f[i];
						}
					}
				}
				
				for (int id : instanceIds) {
					List<Integer> ids = new LinkedList<Integer>();
					ids.add(id);
					float[] f1 = featureMapping.get(id);
					f1 = Arrays.copyOf(f1, f1.length);
					for (int i = 0; i < f1.length; i++) {
						if (normalize[i] > 1.f || normalize[i] < -1.f)
							f1[i] /= normalize[i];
					}
					for (int id2: instanceIds) {
						if (id != id2) {
							float[] f2 = featureMapping.get(id2);
							f2 = Arrays.copyOf(f2, f2.length);
							for (int i = 0; i < f2.length; i++) {
								if (normalize[i] > 1.f || normalize[i] < -1.f)
									f2[i] /= normalize[i];
							}
							
							float dist = 0.f;
							for (int i = 0; i < f1.length; i++) {
								dist += Math.abs(f1[i] - f2[i]);
							}
							
							if (dist < 0.05f) {
								ids.add(id2);
							}
						}
					}
					
					if (ids.size() >= 10) {
						System.out.println("FOUND!");
						for (int iid : ids) {
							System.out.print(InstanceDAO.getById(iid).getName() + "|");
						}
						//System.out.println(ids.toString());
					}
					
				}
				return;*/
				// end of remove me
			}

			// create clustering
			C = new Clustering(instanceIds, featureMapping);
			CostFunction f = new PARX(Cost.resultTime, true, 0, 1);
			
			// load experiment results
			HashMap<Pair<Integer, Integer>, List<ExperimentResult>> results = new HashMap<Pair<Integer, Integer>, List<ExperimentResult>>();
			for (ExperimentResult er : ExperimentResultDAO.getAllByExperimentId(expid)) {
				if (!instanceIds.contains(er.getInstanceId()))
					continue;
				
				List<ExperimentResult> tmp = results.get(new Pair<Integer, Integer>(er.getSolverConfigId(), er.getInstanceId()));
				if (tmp == null) {
					tmp = new LinkedList<ExperimentResult>();
					results.put(new Pair<Integer, Integer>(er.getSolverConfigId(), er.getInstanceId()), tmp);
				}
				tmp.add(er);
			}
			Random rng = new Random();
			// update clustering
			for (Pair<Integer, Integer> p : results.keySet()) {
				List<ExperimentResult> r = results.get(p);
				
			//	while (r.size() > 2) {
			//		r.remove(rng.nextInt(r.size()));
			//	}
				if (!r.isEmpty()) {
					boolean inf = true;
					for (ExperimentResult res : r) {
						Set<Integer> s = C.seeds.get(res.getInstanceId());
						if (s == null) {
							s = new HashSet<Integer>();
							C.seeds.put(res.getInstanceId(), s);
						}
						s.add(res.getSeed());

						if (res.getResultCode().isCorrect()) {
							inf = false;
							break;
						}
					}
					float c = inf ? Float.POSITIVE_INFINITY : f.calculateCost(r);
					C.update(p.getFirst(), p.getSecond(), c);
					// System.out.println("" + sc.getId() + ":" + i + ":" + c);
				}
			}
		} else {
			C = Clustering.deserialize(properties.getProperty("SerializeFilename"));
		}
		
		List<Integer> scids2 = new LinkedList<Integer>();
		scids2.addAll(C.M.keySet());
		
		for (int scid : scids2) {
			if (SolverConfigurationDAO.getSolverConfigurationById(scid).getName().contains("removed")) {
				C.remove(scid);
			}
		}
		HashMap<Integer, List<Integer>> clu = C.getClustering(false);//, 0.9f);
		System.out.println(C.performance(clu));
		System.out.println(clu.keySet());
		for (int scid: clu.keySet()) {
			System.out.println(SolverConfigurationDAO.getSolverConfigurationById(scid).getName());
		}
		
		if (Boolean.parseBoolean(properties.getProperty("ShowClustering"))) {
			System.out.println("Generating clustering for single decision tree using default method..");
			HashMap<Integer, List<Integer>> c = C.getClustering(false);
			System.out.println("Clustering size: " + c.size());
			List<Pair<String, List<Integer>>> clustering = new LinkedList<Pair<String, List<Integer>>>();
			for (Entry<Integer, List<Integer>> entry : c.entrySet()) {
				clustering.add(new Pair<String, List<Integer>>(SolverConfigurationDAO.getSolverConfigurationById(entry.getKey()).getName(), entry.getValue()));

			}
			Collections.sort(clustering, new Comparator<Pair<String, List<Integer>>>() {

				@Override
				public int compare(Pair<String, List<Integer>> arg0, Pair<String, List<Integer>> arg1) {
					return arg0.getFirst().compareTo(arg1.getFirst());
				}
				
			});
			
			for (Pair<String, List<Integer>> p : clustering) {
				System.out.print(p.getFirst() + ": ");
				Collections.sort(p.getSecond());
				System.out.println(p.getSecond());
			}
			
			for (Integer i : c.keySet()) {
				System.out.print("(.*ID: " + i + ".*)|");
			}
			System.out.println();
		}
		
		List<Integer> scids = new LinkedList<Integer>();
		scids.addAll(C.C.keySet());
		
		if (Boolean.parseBoolean(properties.getProperty("ShowWeightedRanking"))) {
			// weighted ranking
			List<Pair<Integer, Float>> scweights = new LinkedList<Pair<Integer, Float>>();
			for (int scid : scids) {
				scweights.add(new Pair<Integer, Float>(scid, C.getWeight(scid)));
			}
			Collections.sort(scweights, new Comparator<Pair<Integer, Float>>() {

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

			});
			int count = 1;
			for (Pair<Integer, Float> p : scweights) {
				SolverConfiguration sc = SolverConfigurationDAO.getSolverConfigurationById(p.getFirst());
				System.out.println((count++) + ") " + sc.getName() + "   W: " + p.getSecond());
			}
		}
		
		//System.out.println(C.getClusteringHierarchical(HierarchicalClusterMethod.AVERAGE_LINKAGE, 10));
		
		//C.printM();
		
		
		
		//System.out.println("Performance(C) = " + C.performance(c));
		/*BufferedWriter writer = new BufferedWriter(new FileWriter(new File("D:\\dot\\test.dat")));
		for (Entry<Integer, List<Integer>> e : C.getClustering(false).entrySet()) {
			for (int i : e.getValue()) {
				for (float f : C.F.get(i)) {
					writer.write(f + ", ");
				}
				writer.write(e.getKey() + "\n");
			}
		}
		// data <- read.csv(file="D:\\dot\\test.dat", head =FALSE, sep=",")
		// randomForest(data[1:54], factor(data[,55]), ntree=500)
		writer.close();
		if (true)
			return;*/

		
		if (Boolean.parseBoolean(properties.getProperty("BuildDecisionTree"))) {
			System.out.println("Calculating clustering..");
			

			
			List<Pair<Integer, Float>> scidWeight = new LinkedList<Pair<Integer, Float>>();
			
			for (int scid : C.M.keySet()) {
				scidWeight.add(new Pair<Integer, Float>(scid, C.getWeight(scid)));
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
			
			Clustering C_orig = new Clustering(C);
			int numConfigs =  Integer.parseInt(properties.getProperty("DecisionTree_numConfigs"));
			if (numConfigs != -1) {
				while (scidWeight.size() > numConfigs) {
					Pair<Integer, Float> p = scidWeight.get(0);
					System.out.println("Removing " + p.getFirst() + " with weight " + p.getSecond());
					C.remove(p.getFirst());
					scidWeight.remove(0);
				}
			}
			/*List<Integer> rem_scids = new LinkedList<Integer>();
			for (int scid : C.M.keySet()) {
				if (!(scid == 1048 || scid == 1047)) {
					rem_scids.add(scid);
				}
				
			}
			for (int scid : rem_scids) {
				C.remove(scid);
			}*/
			C.printM();

			/*HashMap<Integer, List<Integer>> c = new HashMap<Integer, List<Integer>>(); //C.getClustering(false);
			
			List<Integer> k5instances = new LinkedList<Integer>();
			List<Integer> k7instances = new LinkedList<Integer>();
			
			for (Instance i : instances) {
				if (i.getName().contains("k5"))
					k5instances.add(i.getId());
				if (i.getName().contains("k7"))
					k7instances.add(i.getId());
			}
			c.put(1048, k5instances);
			c.put(1047, k7instances);	*/		
			HashMap<Integer, List<Integer>> c = C.getClustering(false, 0.9f);
			
			BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
			float max_perf = -1.f;
			float min_perf = Float.POSITIVE_INFINITY;
			for (int tr = 0; tr < 1; tr++) {

				System.out.println("Building decision tree..");
				/*
				 * HashMap<Integer, List<Integer>> c = new HashMap<Integer,
				 * List<Integer>>(); List<Integer> tmp = new
				 * LinkedList<Integer>(); tmp.addAll(C.I.keySet()); Random rng =
				 * new Random(); for (int i = 0; i < 10; i++) { List<Integer>
				 * tmpc = new LinkedList<Integer>(); c.put(i, tmpc); for (int j
				 * = 0; j < 25; j++) { int rand = rng.nextInt(tmp.size());
				 * tmpc.add(tmp.get(rand)); tmp.remove(rand); } }
				 */
				DecisionTree tmp = new DecisionTree(c, C.F, C.F.values().iterator().next().length, DecisionTree.ImpurityMeasure.valueOf(properties.getProperty("DecisionTree_ImpurityMeasure")), C_orig, C, -1, new Random());
				if (tmp.performance < min_perf) {
					min_perf = tmp.performance;
				}
				if (tmp.performance > max_perf) {
					max_perf = tmp.performance;
					C.tree = tmp;
				}
				// C.tree.printDot(new File("D:\\dot\\bla.dot"));
			}
			System.out.println("Performance(C) = " + C_orig.performance(c));
			System.out.println("Performance(T) = [" + min_perf + "," + max_perf + "]");
			//while (input.readLine() != null);
			
			//C.tree.printDot(new File("D:\\proar\\bla.dot"));
			
		}
		
		if (Boolean.parseBoolean(properties.getProperty("BuildRandomForest"))) {
			System.out.println("Generating random forest using greedy clustering method..");
			C.forest = new RandomForest(C, new Random(), Integer.parseInt(properties.getProperty("RandomForestTreeCount")), 250);
			System.out.println("done.");
		}
		
		if (Boolean.parseBoolean(properties.getProperty("BuildRegressionTree"))) {
			
			List<Pair<Integer, Float>> scidWeight = new LinkedList<Pair<Integer, Float>>();
			
			for (int scid : C.M.keySet()) {
				scidWeight.add(new Pair<Integer, Float>(scid, C.getWeight(scid)));
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
			
			Clustering C_orig = new Clustering(C);
			
			int numConfigs =  Integer.parseInt(properties.getProperty("DecisionTree_numConfigs"));
			if (numConfigs != -1) {
				while (scidWeight.size() > numConfigs) {
					Pair<Integer, Float> p = scidWeight.get(0);
					System.out.println("Removing " + p.getFirst() + " with weight " + p.getSecond());
					C.remove(p.getFirst());
					scidWeight.remove(0);
				}
			}
			
			C.updateData();
		
			
			List<Pair<ParameterConfiguration, List<Pair<Integer, Float>>>> trainData = new LinkedList<Pair<ParameterConfiguration, List<Pair<Integer, Float>>>>();
			List<Pair<ParameterConfiguration, Integer>> p_scids = new LinkedList<Pair<ParameterConfiguration, Integer>>();
			for (Entry<Integer, float[]> e : C.M.entrySet()) {
				ParameterConfiguration c = api.getParameterConfiguration(expid, e.getKey());
				p_scids.add(new Pair<ParameterConfiguration, Integer>(c, e.getKey()));
				List<Pair<Integer, Float>> list = new LinkedList<Pair<Integer, Float>>();
				for (Entry<Integer, Integer> ee : C.I.entrySet()) {
					list.add(new Pair<Integer, Float>(ee.getKey(), e.getValue()[ee.getValue()]));
				}
				trainData.add(new Pair<ParameterConfiguration, List<Pair<Integer, Float>>>(c, list));
			}
			
			
			C.tree2 = new edacc.configurator.aac.racing.challenge.test.DecisionTree(0.00001f, 20, p_scids, trainData, api.getConfigurableParameters(expid), C.F, C.F.values().iterator().next().length, false, C, C_orig);
		}
		
		System.out.println("# instances not used: " + C.getNotUsedInstances().size());

		if (Boolean.parseBoolean(properties.getProperty("Serialize"))) {
			serialize(properties.getProperty("SerializeFilename"), C);
		}
		
		if (Boolean.parseBoolean(properties.getProperty("CreateSolver"))) {
			System.out.println("Exporting solver..");
			String folder = properties.getProperty("SolverDirectory");
			File solverFolder = new File(folder);
			if (solverFolder.mkdirs()) {			
				edacc.model.SolverBinaries binary = edacc.model.SolverBinariesDAO.getById(ConfigurationScenarioDAO.getConfigurationScenarioByExperimentId(expid).getIdSolverBinary());
				
	            InputStream binStream = edacc.model.SolverBinariesDAO.getZippedBinaryFile(binary);
	            ZipInputStream zis = new ZipInputStream(binStream);
	            ZipEntry entry;
	            while ((entry = zis.getNextEntry()) != null) {
	            	byte[] b = new byte[2048];
	            	int n;
	            	File file = new File(solverFolder, entry.getName());
	            	OutputStream os = new FileOutputStream(file);
	            	while ((n = zis.read(b)) > 0) {
	            		os.write(b, 0, n);
	            	}
	            	os.close();
	            	zis.closeEntry();
	            }
	            zis.close();
	            binStream.close();
	            
	            System.out.println("Exporting feature binary..");
	            File featureFolder = new File(featureDirectory);
	            copyFiles(featureFolder, solverFolder);
	            System.out.println("Exporting solver launcher..");
	            File solverLauncherFolder = new File(properties.getProperty("SolverLauncherDirectory"));
	            copyFiles(solverLauncherFolder, solverFolder);
	            System.out.println("Saving clustering..");
	            C.updateData();
	            serialize(new File(solverFolder, "clustering").getAbsolutePath(), C);
	            System.out.println("Creating solverlauncher.properties file..");
	            FileWriter fw = new FileWriter(new File(solverFolder, "solverlauncher.properties").getAbsoluteFile());
	            BufferedWriter bw = new BufferedWriter(fw);
	            bw.write("FeaturesBin = " + properties.getProperty("FeaturesRunCommand") + "\n");
	            bw.write("FeaturesParameters = " + properties.getProperty("FeaturesParameters") + "\n");
	            bw.write("SolverBin = ./" + binary.getRunPath() + "\n");
	            bw.write("Clustering = ./clustering\n");
	            bw.close();
	            fw.close();
	            
	            System.out.println("Creating start script..");
	            fw = new FileWriter(new File(solverFolder, "start.sh").getAbsoluteFile());
	            bw = new BufferedWriter(fw);
	            bw.write("#!/bin/bash\njava -Xmx1024M -jar SolverLauncher.jar $1 $2 $3\n");
	            bw.close();
	            fw.close();
			} else {
				System.err.println("Could not create directory: " + folder);
			}
		}
		
		
		if (Boolean.parseBoolean(properties.getProperty("Interactive"))) {
			interactiveMode(C, new File(featureDirectory));
		}
		
	}
	
	
	public static void interactiveMode(Clustering C, File featuresFolder) throws Exception {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String line;
		while ((line = br.readLine()) != null) {
			if ("q".equals(line)) {
				System.out.println("Query Mode");
				System.out.println("Method?");
				String method = br.readLine();
				System.out.println("id?");
				String id = br.readLine();
				if (method == null || id == null)
					break;
				
				float[] features = calculateFeatures(Integer.valueOf(id), featuresFolder, null);
				if ("randomforest".equals(method)) {
					String params = C.P.get(C.forest.getSolverConfig(features));
					System.out.println("Params: " + params);
				} else if ("tree".equals(method)) {
					String params = C.P.get(C.tree.query(features).getFirst());
					System.out.println("Params: " + params);
				}
			}
		}
		br.close();
	}
	
	
	/**
	 * This function will copy files or directories from one location to
	 * another. note that the source and the destination must be mutually
	 * exclusive. This function can not be used to copy a directory to a sub
	 * directory of itself. The function will also have problems if the
	 * destination files already exist.
	 * 
	 * @param src
	 *            -- A File object that represents the source for the copy
	 * @param dest
	 *            -- A File object that represnts the destination for the copy.
	 * @throws IOException
	 *             if unable to copy.
	 */
	public static void copyFiles(File src, File dest) throws IOException {
		// Check to ensure that the source is valid...
		if (!src.exists()) {
			throw new IOException("copyFiles: Can not find source: " + src.getAbsolutePath() + ".");
		} else if (!src.canRead()) { // check to ensure we have rights to the
										// source...
			throw new IOException("copyFiles: No right to source: " + src.getAbsolutePath() + ".");
		}
		// is this a directory copy?
		if (src.isDirectory()) {
			if (!dest.exists()) { // does the destination already exist?
				// if not we need to make it exist if possible (note this is
				// mkdirs not mkdir)
				if (!dest.mkdirs()) {
					throw new IOException("copyFiles: Could not create direcotry: " + dest.getAbsolutePath() + ".");
				}
			}
			// get a listing of files...
			String list[] = src.list();
			// copy all the files in the list.
			for (int i = 0; i < list.length; i++) {
				File dest1 = new File(dest, list[i]);
				File src1 = new File(src, list[i]);
				copyFiles(src1, dest1);
			}
		} else {
			// This was not a directory, so lets just copy the file
			FileInputStream fin = null;
			FileOutputStream fout = null;
			byte[] buffer = new byte[4096]; // Buffer 4K at a time (you can
											// change this).
			int bytesRead;
			try {
				// open the files for input and output
				fin = new FileInputStream(src);
				fout = new FileOutputStream(dest);
				// while bytesRead indicates a successful read, lets write...
				while ((bytesRead = fin.read(buffer)) >= 0) {
					fout.write(buffer, 0, bytesRead);
				}
			} catch (IOException e) { // Error copying file...
				IOException wrapper = new IOException("copyFiles: Unable to copy file: " + src.getAbsolutePath() + "to" + dest.getAbsolutePath() + ".");
				wrapper.initCause(e);
				wrapper.setStackTrace(e.getStackTrace());
				throw wrapper;
			} finally { // Ensure that the files are closed (if they were open).
				if (fin != null) {
					fin.close();
				}
				if (fout != null) {
					fout.close();
				}
			}
		}
	}
	
	static float[] calculateFeatures(int instanceId, File featureFolder, File featuresCacheFolder) throws IOException, NoConnectionToDBException, InstanceClassMustBeSourceException, InstanceNotInDBException, InterruptedException, SQLException {
		Properties properties = new Properties();
		File propFile = new File(featureFolder, "features.properties");
		FileInputStream in = new FileInputStream(propFile);
		properties.load(in);
		in.close();
		String featuresRunCommand = properties.getProperty("FeaturesRunCommand");
		String featuresParameters = properties.getProperty("FeaturesParameters");
		String[] features = properties.getProperty("Features").split(",");
		Instance instance = InstanceDAO.getById(instanceId);
		
		float[] res = new float[features.length];
		
		File cacheFile = null;
		if (featuresCacheFolder != null) {
			cacheFile = new File(featuresCacheFolder, instance.getMd5());
		}
		if (cacheFile != null) {
			featuresCacheFolder.mkdirs();
			
			if (cacheFile.exists()) {
				System.out.println("Found cached features.");
				try {
					BufferedReader br = new BufferedReader(new FileReader(cacheFile));
					String[] featuresNames = br.readLine().split(",");
					String[] f_str = br.readLine().split(",");
					br.close();
					if (f_str.length != features.length || !Arrays.equals(features, featuresNames)) {
						System.err.println("Features changed? Recalculating!");
					} else {
						for (int i = 0; i < res.length; i++) {
							res[i] = Float.parseFloat(f_str[i]);
						}
						return res;
					}
					
				} catch (Exception ex) {
					System.err.println("Could not load cache file: " + cacheFile.getAbsolutePath() + ". Recalculating features.");
				}
			}
		}
		
		new File("tmp").mkdir();
		File f = File.createTempFile("instance"+instanceId, "instance"+instanceId, new File("tmp"));
		InstanceDAO.getBinaryFileOfInstance(instance, f, false, false);
		
		
		//System.out.println("Call: " + featuresRunCommand + " " + featuresParameters + " " + f.getAbsolutePath());
		
		Process p = Runtime.getRuntime().exec(featuresRunCommand + " " + featuresParameters + " " + f.getAbsolutePath(), null, featureFolder);
		BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
		
		String line;
		while (((line = br.readLine()) != null) && line.startsWith("c "));
		
		String[] features_str = br.readLine().split(",");
		for (int i = 0; i < features_str.length; i++) {
			res[i] = Float.valueOf(features_str[i]);
		}
		br.close();
		p.destroy();
		f.delete();
		
		//System.out.println("Result: " + Arrays.toString(res));
		if (cacheFile != null) {
			cacheFile.delete();
			BufferedWriter bw = new BufferedWriter(new FileWriter(cacheFile));
			bw.write(properties.getProperty("Features") + '\n');
			for (int i = 0; i < res.length; i++) {
				bw.write(String.valueOf(res[i]));
				if (i != res.length - 1) {
					bw.write(',');
				}
			}
			bw.write('\n');
			bw.close();
		}
		
		return res;
	}
}
