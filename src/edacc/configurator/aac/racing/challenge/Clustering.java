package edacc.configurator.aac.racing.challenge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import edacc.api.API;
import edacc.api.APIImpl;
import edacc.api.costfunctions.CostFunction;
import edacc.api.costfunctions.PARX;
import edacc.model.Experiment.Cost;
import edacc.model.ExperimentResult;
import edacc.model.ExperimentResultDAO;
import edacc.model.Instance;
import edacc.model.InstanceClassMustBeSourceException;
import edacc.model.InstanceDAO;
import edacc.model.InstanceHasProperty;
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
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 2847802957451633450L;
	
	private int n; // number of instances = I.size();
	private HashMap<Integer, Integer> I; // maps instance id to column
	
	private HashMap<Integer, float[]> M; // Membership Matrix M, maps sc id to instance row vector
	private HashMap<Integer, float[]> C; // Cost Matrix C, maps sc id to cost for instance vector
	//HashMap<Integer, Float> W; // relative weight of sc
	private float[] K; // K_i = sum over solver configs for instance row i (ith instance)
	
	// cache for data to be updated before using matrix M
	private HashSet<Integer> update_columns;
	
	protected HashMap<Integer, float[]> F; // feature vectors for instances
	protected HashMap<Integer, String> P; // parameter lines
	
	
	protected DecisionTree tree;
	
	/**
	 * Creates new Clustering object with the given instance ids. Instance ids cannot be changed later.
	 * @param instanceIds the instance ids to be clustered
	 * @throws SQLException 
	 * @throws InstanceClassMustBeSourceException 
	 */
	public Clustering(List<Integer> instanceIds, List<String> feature_names) throws InstanceClassMustBeSourceException, SQLException {
		// generate the column mapping for M,C matrices
		I = new HashMap<Integer, Integer>();
		int row = 0;
		for (Integer id: instanceIds) {
			I.put(id, row++);
		}
		n = I.size();
		
		// generate empty membership, cost and weight matrices
		M = new HashMap<Integer, float[]>();
		C = new HashMap<Integer, float[]>();
		//W = new HashMap<Integer, Float>();
		
		// initialize data to be updated
		update_columns = new HashSet<Integer>();
		
		// initialize K
		K = new float[n];
		for (int i = 0; i < n; i++) {
			K[i] = 0.f;
		}
		
		// load features
		F = new HashMap<Integer, float[]>();
		for (Integer id : instanceIds) {
			float[] f = new float[feature_names.size()];
			HashMap<String, Float> tmp = new HashMap<String, Float>();
			Instance instance = InstanceDAO.getById(id);
			for (InstanceHasProperty ihp : instance.getPropertyValues().values()) {
				try {
					tmp.put(ihp.getProperty().getName(), Float.parseFloat(ihp.getValue()));
				} catch (Exception ex) {
				}
			}
			for (int i = 0; i < feature_names.size(); i++) {
				Float val = tmp.get(feature_names.get(i));
				if (val == null) {
					System.err.println("WARNING: Did not find feature value for name " + feature_names.get(i) + " and instance " + instance.getId());
					f[i] = 0.f;
				} else {
					f[i] = val;
				}
			}
			F.put(id, f);
		}
		P = new HashMap<Integer, String>();
	}
	
	private void updateData() {
		for (int column : update_columns) {
			// Update M entry
			{
				int scs = 0;
				float sum = 0.f;
				float max = 0.f;
				for (float[] tmp : C.values()) {
					if (!Float.isInfinite(tmp[column])) {
						sum += tmp[column];
						scs++;
						if (tmp[column] > max) {
							max = tmp[column];
						}
					}
				}

				for (int tmp_scid : M.keySet()) {
					float[] tmp_c = C.get(tmp_scid);
					float[] tmp_m = M.get(tmp_scid);
					if (Float.isInfinite(tmp_c[column])) {
						tmp_m[column] = 0.f;
					} else {
						// TODO: eps
						if (scs * max - sum > 0.001f) {
							// TODO: multiplicator
							tmp_m[column] = (max * 1.05f - tmp_c[column]) / (scs * max * 1.05f - sum);
						} else {
							tmp_m[column] = 1.f / scs;
						}
						// TODO: maximize: tmp_c[column] / sum
					}
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
	}
	
	/**
	 * Returns a clustering for the current membership matrix. Maps solver configuration id to a list of instance ids (the cluster).
	 * @param removeSmallClusters if true, tries to remove small clusters by moving instances from small clusters to other clusters.
	 * @return
	 */
	public HashMap<Integer, List<Integer>> getClustering(boolean removeSmallClusters) {
		updateData();
		HashMap<Integer, List<Integer>> res = new HashMap<Integer, List<Integer>>();
		for (int instanceid : I.keySet()) {
			float max = 0.f;
			int scid = -1;
			for (int tmp_scid : M.keySet()) {
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
	
	private float getAbsolutWeight(int scid) {
		float[] m = M.get(scid);
		float res = 0.f;
		for (int i = 0; i < n; i++) {
			res += m[i];
		}
		return res;
	}
	
	/**
	 * Returns a value between 0.f and 1.f. This is the weight for the given solver configuration where bigger is better.
	 * @param scid the solver configuration id
	 * @return the weight for the solver configuration
	 */
	public float getWeight(int scid) {
		return getAbsolutWeight(scid) / getCumulatedWeight();
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
	
	
	/**
	 * Test method for Clustering class.
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		API api = new APIImpl();
		
		Properties properties = new Properties();
		InputStream in = Clustering.class.getResourceAsStream("settings.properties");
		properties.load(in);
		in.close();
		in = Clustering.class.getResourceAsStream("private.properties");
		if (in != null) {
			properties.load(in);
			in.close();
		}		
		api.connect(properties.getProperty("DBHost"), Integer.parseInt(properties.getProperty("DBPort")), properties.getProperty("DB"), properties.getProperty("DBUser"), properties.getProperty("DBPassword"), true);
		int expid = Integer.parseInt(properties.getProperty("ExperimentId"));
		
		LinkedList<Instance> instances = InstanceDAO.getAllByExperimentId(expid);
		List<Integer> instanceIds = new LinkedList<Integer>();
		for (Instance i : instances) {
			instanceIds.add(i.getId());
		}
		//instanceIds.add(17164);
		
		List<String> feature_names = new LinkedList<String>();
		feature_names.add("nvarsOrig");
		feature_names.add("nclausesOrig");
		feature_names.add("nvars");
		feature_names.add("nclauses");
		feature_names.add("reducedVars");
		feature_names.add("reducedClauses");
		feature_names.add("Pre-featuretime");
		feature_names.add("vars-clauses-ratio");
		feature_names.add("POSNEG-RATIO-CLAUSE-mean");
		feature_names.add("POSNEG-RATIO-CLAUSE-coeff-variation");
		feature_names.add("POSNEG-RATIO-CLAUSE-min");
		feature_names.add("POSNEG-RATIO-CLAUSE-max");
		feature_names.add("POSNEG-RATIO-CLAUSE-entropy");
		feature_names.add("VCG-CLAUSE-mean");
		feature_names.add("VCG-CLAUSE-coeff-variation");
		feature_names.add("VCG-CLAUSE-min");
		feature_names.add("VCG-CLAUSE-max");
		feature_names.add("VCG-CLAUSE-entropy");
		feature_names.add("UNARY");
		feature_names.add("BINARY+");
		feature_names.add("TRINARY+");
		feature_names.add("Basic-featuretime");
		feature_names.add("VCG-VAR-mean");
		feature_names.add("VCG-VAR-coeff-variation");
		feature_names.add("VCG-VAR-min");
		feature_names.add("VCG-VAR-max");
		feature_names.add("VCG-VAR-entropy");
		feature_names.add("POSNEG-RATIO-VAR-mean");
		feature_names.add("POSNEG-RATIO-VAR-stdev");
		feature_names.add("POSNEG-RATIO-VAR-min");
		feature_names.add("POSNEG-RATIO-VAR-max");
		feature_names.add("POSNEG-RATIO-VAR-entropy");
		feature_names.add("HORNY-VAR-mean");
		feature_names.add("HORNY-VAR-coeff-variation");
		feature_names.add("HORNY-VAR-min");
		feature_names.add("HORNY-VAR-max");
		feature_names.add("HORNY-VAR-entropy");
		feature_names.add("horn-clauses-fraction");
		feature_names.add("VG-mean");
		feature_names.add("VG-coeff-variation");
		feature_names.add("VG-min");
		feature_names.add("VG-max");
		feature_names.add("KLB-featuretime");
		feature_names.add("CG-mean");
		feature_names.add("CG-coeff-variation");
		feature_names.add("CG-min");
		feature_names.add("CG-max");
		feature_names.add("CG-entropy");
		feature_names.add("cluster-coeff-mean");
		feature_names.add("cluster-coeff-coeff-variation");
		feature_names.add("cluster-coeff-min");
		feature_names.add("cluster-coeff-max");
		feature_names.add("cluster-coeff-entropy");
		feature_names.add("CG-featuretime");
		
		Clustering C = new Clustering(instanceIds, feature_names);
		CostFunction f = new PARX(Cost.resultTime, false, 0, 1);
		//int sc_limit = 100;
		//int sc_c = 0;
		
		HashMap<Pair<Integer, Integer>, List<ExperimentResult>> results = new HashMap<Pair<Integer, Integer>, List<ExperimentResult>>();
		
		for (ExperimentResult er : ExperimentResultDAO.getAllByExperimentId(expid)) {
			List<ExperimentResult> tmp = results.get(new Pair<Integer, Integer>(er.getSolverConfigId(), er.getInstanceId()));
			if (tmp == null) {
				tmp = new LinkedList<ExperimentResult>();
				results.put(new Pair<Integer, Integer>(er.getSolverConfigId(), er.getInstanceId()), tmp);
			}
			tmp.add(er);
		}
		
		for (Pair<Integer, Integer> p : results.keySet()) {
			//if (sc_c++ >= sc_limit)
			//	break;
			// System.out.println("" + sc.getId() + ":" + i);
			List<ExperimentResult> r = results.get(p);
			if (!r.isEmpty()) {
				boolean inf = true;
				for (ExperimentResult res : r) {
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
		//C.printM();
		
		
		HashMap<Integer, List<Integer>> c = C.getClustering(true);
		System.out.println("C");
		for (List<Integer> i : c.values()) {
			System.out.println(i);
		}
		//C.printM();
		
		C.tree = new DecisionTree(c, C.F, C.F.values().iterator().next().length);
		
		System.out.println("# instances not used: " + C.getNotUsedInstances().size());
		
		serialize(properties.getProperty("SerializeFilename"), C);
		
		/*
		List<Integer> instanceIds2 = new LinkedList<Integer>();
		instanceIds2.add(1);
		instanceIds2.add(2);
		
		Clustering C2 = new Clustering(instanceIds2);
		
		
		
		C2.update(1, 1, 18.f);
		C2.update(2, 1, 60.f);
		C2.update(3, 1, 200.f);
		C2.update(4, 1, 300.f);
		C2.update(5, 1, 300.f);
		C2.update(6, 1, 300.f);
		C2.update(7, 2, 10.f);
		
		C2.printM();*/
	}
}
