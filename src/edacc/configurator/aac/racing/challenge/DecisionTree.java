package edacc.configurator.aac.racing.challenge;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Comparator;

import edacc.api.API;
import edacc.api.APIImpl;
import edacc.api.costfunctions.Average;
import edacc.api.costfunctions.CostFunction;
import edacc.model.Experiment;
import edacc.model.ExperimentResult;
import edacc.model.ExperimentResultDAO;
import edacc.model.InstanceDAO;
import edacc.model.SolverConfiguration;
import edacc.model.SolverConfigurationDAO;
import edacc.parameterspace.Parameter;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.domain.*;
import edacc.parameterspace.graph.ParameterGraph;
import edacc.util.Pair;

public class DecisionTree implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 3264367978634L;

	private Node root;

	private int num_features;
	private HashMap<Integer, float[]> features;
	
	private HashSet<Integer> usedFeatures;
	
	private Double eps = 0.000001;
	
	
	public DecisionTree(HashMap<Integer, List<Integer>> clustering, HashMap<Integer, float[]> features, int num_features) {
		
		// TODO: if there are equal feature vectors for different instances the training process might result in an exception!
		
		this.num_features = num_features;
		this.features = features;
		
		usedFeatures = new HashSet<Integer>();
		/*List<Pair<ParameterConfiguration, List<ExperimentResult>>> sample = new ArrayList<Pair<ParameterConfiguration, List<ExperimentResult>>>();
		for (int i = 0; i < trainData.size(); i++) {
			sample.add(trainData.get(rng.nextInt(trainData.size())));
		}*/
		
		//params = new ArrayList<Parameter>();
		//params.addAll(graph.getParameterSet());
		
		root = new Node(clustering);
		
		initializeNode(root);
		train(root);
		
		System.out.println("[DecisionTree] Used " + usedFeatures.size() + " features of " + num_features);
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
	
	/*private double stdDev(List<Sample> data) {
		if (data.size() <= 1)
			return 0.;
		double res = 0.;
		double m = 0.;
		double costs[] = new double[data.size()];
		for (int i = 0; i < data.size(); i++) {
			costs[i] = data.get(i).cost;
			m+=costs[i];
		}
		m /= data.size();
		for (int i = 0; i < data.size(); i++) {
			res += (costs[i] - m) * (costs[i] - m);
		}
		res /= data.size()-1;
		
		return Math.sqrt(res);
	}*/
	
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
		
		// TODO: not only misclassification index..
		
		return getEntropyIndex(clustering) - l * getEntropyIndex(left) - r * getEntropyIndex(right);
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
	
	private int query(Node node, float[] features) {
		if (node.clustering.size() == 1) {
			return node.clustering.keySet().iterator().next();
		} else {
			float val = features[node.split_attribute];
			if (val < node.split) {
				return query(node.left, features);
			} else {
				return query(node.right, features);
			}
		}
	}
	
	public int query(float[] features) {
		if (features.length != num_features) {
			throw new IllegalArgumentException("Invalid feature vector!");
		}
		return query(root, features);
	}
	
	private class SearchResult {
		Set<ParameterConfiguration> configs;
		List<Pair<Parameter, Domain>> parameters;
		Set<Integer> instanceIds;
		
		public SearchResult(Set<ParameterConfiguration> configs, List<Pair<Parameter, Domain>> parameters, Set<Integer> instanceIds) {
			this.configs = configs;
			this.parameters = parameters;
			this.instanceIds = instanceIds;
		}
	}
	
	private SearchResult mergeSearchResults(SearchResult sr1, SearchResult sr2) {
		if (sr1 == null)
			return sr2;
		if (sr2 == null)
			return sr1;
		Set<ParameterConfiguration> configs = new HashSet<ParameterConfiguration>();
		List<Pair<Parameter, Domain>> parameters = new ArrayList<Pair<Parameter, Domain>>();
		Set<Integer> instanceIds = new HashSet<Integer>();
		configs.addAll(sr1.configs);
		configs.addAll(sr2.configs);
		parameters.addAll(sr1.parameters);
		parameters.addAll(sr2.parameters);
		instanceIds.addAll(sr1.instanceIds);
		instanceIds.addAll(sr2.instanceIds);
		return new SearchResult(configs, parameters, instanceIds);
	}

	private class Node implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = -9078130993009897397L;		
		HashMap<Integer, List<Integer>> clustering;
		double stddev;
		Node left, right;
		int split_attribute;
		float split;

		public Node(HashMap<Integer, List<Integer>> clustering) {
			this.left = null;
			this.right = null;
			this.clustering = clustering;
			this.stddev = Double.NaN;
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
	
	/*Collections.sort(results, new Comparator<Pair<ParameterConfiguration, List<ExperimentResult>>>() {

		@Override
		public int compare(Pair<ParameterConfiguration, List<ExperimentResult>> o1, Pair<ParameterConfiguration, List<ExperimentResult>> o2) {
			Object first = o1.getFirst().getParameterValue(p);
			Object second = o2.getFirst().getParameterValue(p);
			if (first instanceof Float) {
				Float f1 = (Float) first;
				Float f2 = (Float) second;
				if (f1 < f2)
					return -1;
				else if (f1 > f2)
					return 1;
				else
					return 0;
			} else if (first instanceof Integer) {
				Integer f1 = (Integer) first;
				Integer f2 = (Integer) second;
				if (f1 < f2)
					return -1;
				else if (f1 > f2)
					return 1;
				else
					return 0;					
			} else if (first instanceof Double) {
				Double f1 = (Double) first;
				Double f2 = (Double) second;
				if (f1 < f2)
					return -1;
				else if (f1 > f2)
					return 1;
				else
					return 0;					
			} else {
				throw new IllegalArgumentException();
			}
		}

		
	});*/
	
	/*public int printDotNodeDescription(Node node, int nodenum, BufferedWriter br) throws Exception {
		String name = "null"; 
		name = node.toString();
		
		if (node.left == null) {
			List<ExperimentResult> results = new ArrayList<ExperimentResult>();
			for (Sample sample : node.results) 
				results.addAll(sample.results);
			name += " cost: " + func.calculateCost(results) + " numResults: " + results.size();
		}
		name += ", " + node.stddev;
		br.write("N" + nodenum + " [label=\"" + name + "\"];\n");
		int nextNode = nodenum+1;
		if (node.left != null) {
			int nn = nextNode;
			nextNode = printDotNodeDescription(node.left, nextNode, br);
			br.write("N" + nodenum + " -- N" + nn + ";\n");
			nn = nextNode;
			nextNode = printDotNodeDescription(node.right, nextNode, br);
			br.write("N" + nodenum + " -- N" + nn + ";\n");
			if (!node.nullNode.results.isEmpty()) {
				nn = nextNode;
				nextNode = printDotNodeDescription(node.nullNode, nextNode, br);
				br.write("N" + nodenum + " -- N" + nn + ";\n");
			}
		}
		return nextNode;
	}

	
	public void printDot(File file) throws Exception {
		BufferedWriter br = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
		br.write("graph g {\n");
		printDotNodeDescription(root, 1, br);
		br.write("}\n");
		br.close();
	}*/
	
	public static void main(String[] args) throws Exception {
		if (args.length < 9) {
			System.out.println("Parameters: <host> <port> <username> <password> <db> <expid1,expid2,.. (first expid (must be config exp) will be used for parameters etc.)> <numscs -1 == unlimited> <use timeout results> <action = query/code> [<instance property 1> <instance property 2> ...]");
			return;
		}
		int numscs = Integer.MAX_VALUE;
		String host = args[0];
		int port = Integer.valueOf(args[1]);
		String username = args[2];
		String password = args[3];
		String db = args[4];
		List<Integer> expids = new ArrayList<Integer>();
		for (String s : args[5].split(",")) {
			expids.add(Integer.valueOf(s));
		}
		if (expids.isEmpty()) {
			expids.add(Integer.valueOf(args[5]));
		}
		numscs = Integer.valueOf(args[6]);
		boolean use_timeout_results = Boolean.parseBoolean(args[7]);
		String action = args[8];
		if (numscs < 0) {
			numscs = Integer.MAX_VALUE;
		}
		ArrayList<String> instancePropertyNames = new ArrayList<String>();
		for (int i = 9; i < args.length; i++) {
			instancePropertyNames.add(args[i]);
		}
		
		API api = new APIImpl();
		
		api.connect(host, port, db, username, password, true);
		
		InstanceDAO.getAll();
		
		List<SolverConfiguration> scs = new ArrayList<SolverConfiguration>(); 
		for (int expid : expids) {
			scs.addAll(SolverConfigurationDAO.getSolverConfigurationByExperimentId(expid));
		}
		
		ArrayList<Pair<ParameterConfiguration, List<ExperimentResult>>> trainData = new ArrayList<Pair<ParameterConfiguration, List<ExperimentResult>>>();
		
		int numRuns = 0;
		int numScs = 0;
		
		int cur = 0;
		for (SolverConfiguration sc : scs) {
			System.err.println((++cur) + "/" + scs.size());
			ParameterConfiguration pc = api.getParameterConfiguration(sc.getExperiment_id(), sc.getId());
			List<ExperimentResult> results = ExperimentResultDAO.getAllBySolverConfiguration(sc);
			if (!use_timeout_results) {
				for (int i = results.size() - 1; i >= 0; i--) {
					if (!String.valueOf(results.get(i).getResultCode().getResultCode()).startsWith("1")) {
						results.remove(i);
					}
				}
			}
			
			if (results.isEmpty())
				continue;
			trainData.add(new Pair<ParameterConfiguration, List<ExperimentResult>>(pc, results));
			numScs++;
			numRuns += results.size();
			if (numScs > numscs)
				break;
		}
		//System.out.println("" +stdDev(trainData,new Average()));
		System.err.println("Num SCs: " + numScs);
		System.err.println("Num Runs: " + numRuns);
		
		ParameterGraph graph = api.loadParameterGraphFromDB(expids.get(0));
		List<Parameter> params = new ArrayList<Parameter>();
		params.addAll(graph.getParameterSet());

	/*	if (action.equals("query")) {
			queryTree(api, graph, expids.get(0), trainData, params, instancePropertyNames);
		} else if (action.equals("source")) {
			writeSolverCode(trainData, params, instancePropertyNames);
		}*/
		
	}
	
	/*public static void queryTree(API api, ParameterGraph graph, int expid, List<Pair<ParameterConfiguration, List<ExperimentResult>>> trainData, List<Parameter> params, ArrayList<String> instancePropertyNames) throws Exception {

		System.err.println("Generating random tree..");

		DecisionTree rt = new DecisionTree(new Random(), new Average(Experiment.Cost.resultTime, true), 2.5, 8, trainData, params, instancePropertyNames, true);
		System.err.println("Done.");
		//rt.printDot();
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String line;
		
		while (true) {
			ParameterConfiguration config = graph.getRandomConfiguration(new Random());
			for (Parameter p : api.getConfigurableParameters(expid)) {
				System.out.println("Value for " + p.getName());
				line = br.readLine();
				if (line == null)
					break;
				if (p.getDomain() instanceof IntegerDomain) {
					config.setParameterValue(p, Integer.valueOf(line));
				} else if (p.getDomain() instanceof RealDomain) {
					config.setParameterValue(p, Double.valueOf(line));
				} else {
					config.setParameterValue(p, line);
				}
			}
			System.out.println("Instance id");
			line = br.readLine();
			if (line == null)
				break;
			int instanceId = Integer.valueOf(line);
			List<ExperimentResult> results = rt.getResults(config, instanceId);
			//for (ExperimentResult result : results) {
			//	System.out.println("" + result.getResultTime());
			//}
			System.out.println("" + results.size() + " results with cost (avg): " + new Average(Experiment.Cost.resultTime, true).calculateCost(results));
		}
	}*/
	
	public static void writeSolverCode(BufferedWriter bw, Node node, HashMap<String, String> parameterVariables, HashMap<String, String> instancePropertyVariables, List<Parameter> params, int level) throws Exception {
		if (node.left == null) {
			// leaf
		//	
			/*List<Pair<ParameterConfiguration, List<ExperimentResult>>> trainData = new ArrayList<Pair<ParameterConfiguration, List<ExperimentResult>>>();
			for (Sample s : node.results) {
				trainData.add(new Pair<ParameterConfiguration, List<ExperimentResult>>(s.config, s.results));
			}
			System.out.println("RESULTS SIZE: " + node.results.size());
			
			if (node.results.size() == 0) {
				for (int k = 0; k < level; k++) bw.write('\t');
				bw.write("// result bucket was empty..\n");
				return;
			}
			CostFunction func = new PARX(10);
			RandomTree paramTree = new RandomTree(new Random(), func, 0.5, 8, trainData, params, new ArrayList<String>(), false);
			Node paramNode = null;
			float cost = Float.MAX_VALUE;
			for (Node n : paramTree.leafNodes) {
				List<ExperimentResult> results = new ArrayList<ExperimentResult>();
				for (Sample s : n.results) {
					results.addAll(s.results);
				}
				if (results.isEmpty())
					continue;
				float tmpCost = func.calculateCost(results);
				if (tmpCost < cost) {
					paramNode = n;
					cost = tmpCost;
				}
			}*/
			boolean error = false;
			CostFunction func = new Average(Experiment.Cost.resultTime, true);
			float minCost = Float.MAX_VALUE;
			ParameterConfiguration config = null;
	/*		for (Sample s: node.results) {
				//float tmpCost = func.calculateCost(s.results);
				float tmpCost = s.results.size();
				float numSolved = 0.f;
				for (ExperimentResult er : s.results) {
					if (String.valueOf(er.getResultCode().getResultCode()).startsWith("1")) {
						numSolved = numSolved + 1.f;
					}
				}
				tmpCost = - (numSolved/tmpCost);
				
				if (tmpCost < minCost) {
					minCost = tmpCost;
					config = s.config;
				}
			}
			List<ParameterConfiguration> paramConfigs = new ArrayList<ParameterConfiguration>();
			paramConfigs.add(config);
			for (Sample s: node.results) {
				if (func.calculateCost(s.results) < 2*minCost) {
					paramConfigs.add(s.config);
				}
			}*/
			
			for (int i = 0; i < params.size(); i++) {
				Parameter p = params.get(i);
				/*Domain d = paramNode.domains[i];
				
				
				float minCost = Float.MAX_VALUE;
				Object value = null;
				for (int k = 0; k < level; k++) bw.write('\t');
				bw.write("cout << \"Possible costs are: ");
				for (Sample s : paramNode.results) {
					float tmpCost = func.calculateCost(s.results);
					bw.write("" + tmpCost + ", ");
					if (tmpCost < minCost) {
						minCost = tmpCost;
						value = s.config.getParameterValue(p);
					}
				}
				bw.write("\" << endl;\n");*/
				for (int k = 0; k < level; k++) bw.write('\t');
				
			/*	for (ParameterConfiguration c : paramConfigs) {
					
				}*/
				
				
				Object value = config.getParameterValue(p);
				if (value != null) {
					for (int k = 0; k < level; k++) bw.write('\t');
					bw.write("*" + parameterVariables.get(p.getName()) + " = " + value + ";\n");
				} else {
					for (int k = 0; k < level; k++) bw.write('\t');
					bw.write("// TODO: did not find a value?\n");
					error = true;
				}
				/*if (d instanceof IntegerDomain) {
					IntegerDomain id = (IntegerDomain) d;
					int hi = id.getLow();
					int lo = id.getHigh();
					for (Sample s : paramNode.results) {
						if ((Integer) s.config.getParameterValue(p) < lo)
							lo = (Integer) s.config.getParameterValue(p);
						if ((Integer) s.config.getParameterValue(p) > hi)
							hi = (Integer) s.config.getParameterValue(p);
					}
					int range = hi - lo +1;
					if (range == 1) {
						for (int k = 0; k < level; k++) bw.write('\t');
						bw.write("*"+parameterVariables.get(p.getName()) + " = " + lo + ";\n");
					} else {
						for (int k = 0; k < level; k++) bw.write('\t');
						bw.write("*"+parameterVariables.get(p.getName()) + " = (rand() % " + range + ") + " + lo + ";\n");
					}
					for (int k = 0; k < level; k++) bw.write('\t');
					bw.write("cout << \"c range for " + p.getName() + " is \" << " + range + " << endl;\n"); 
					for (int k = 0; k < level; k++) bw.write('\t');
					bw.write("cout << \"Domain is " + d + "\" << endl;\n");
					for (int k = 0; k < level; k++) bw.write('\t');
					bw.write("cout << \"Restricted domain is [" + lo + "," + hi + "]\" << endl;\n");
				} else if (d instanceof RealDomain) {
					RealDomain rd = (RealDomain) d;
					double hi = rd.getLow();
					double lo = rd.getHigh();
					for (Sample s : paramNode.results) {
						if ((Double) s.config.getParameterValue(p) < lo)
							lo = (Double) s.config.getParameterValue(p);
						if ((Double) s.config.getParameterValue(p) > hi)
							hi = (Double) s.config.getParameterValue(p);
					}
					double range = hi - lo;
					for (int k = 0; k < level; k++) bw.write('\t');
					bw.write("*" + parameterVariables.get(p.getName()) + " = ((double)rand()/(double)RAND_MAX) * " + range + " + " + lo + ";\n");
					for (int k = 0; k < level; k++) bw.write('\t');
					bw.write("cout << \"c range for " + p.getName() + " is \" << " + range + " << endl;\n"); 
					for (int k = 0; k < level; k++) bw.write('\t');
					bw.write("cout << \"Domain is " + d + "\" << endl;\n");
					for (int k = 0; k < level; k++) bw.write('\t');
					bw.write("cout << \"Restricted domain is [" + lo + "," + hi + "]\" << endl;\n");
				} else {
					for (int k = 0; k < level; k++) bw.write('\t');
					bw.write("// TODO: implement for domain " + d + "\n");
					error = true;
				}*/
			}
			if (!error) {
				for (int k = 0; k < level; k++) bw.write('\t');
				bw.write("found = 1;\n");
			}
		} else {
		/*	Attribute attr = node.left.attr;
			Domain d = node.left.domain;
			Property p = (Property) attr.attribute;
			for (int i = 0; i < level; i++) bw.write('\t');
			if (d instanceof IntegerDomain || d instanceof RealDomain) {
				Object split;
				if (d instanceof IntegerDomain) {
					split = ((IntegerDomain) d).getHigh();
				} else {
					split = ((RealDomain) d).getHigh();
				}
				bw.write("if (" + instancePropertyVariables.get(p.getName()) + " <= " + split + ") {\n");
				writeSolverCode(bw, node.left, parameterVariables, instancePropertyVariables, params, level+1);
				for (int i = 0; i < level; i++) bw.write('\t');
				bw.write("} else {\n");
				writeSolverCode(bw, node.right, parameterVariables, instancePropertyVariables, params, level+1);
				for (int i = 0; i < level; i++) bw.write('\t');
				bw.write("}\n");
			} else {
				// TODO: implement
				throw new IllegalArgumentException("Domain " + d + " currently not supported!");
			}*/
		}
	}
	/**
	 * 
	 * @param file
	 * @param trainData
	 * @param params
	 * @throws Exception
	 */
	public static void writeSolverCode(List<Pair<ParameterConfiguration, List<ExperimentResult>>> trainData, List<Parameter> params, List<String> instanceProperties) throws Exception {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String line;
		System.out.println("Filename?");
		line = br.readLine();
		br.close();
		File file = new File(line);
		
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
		
	/*	DecisionTree instancePropertyTree = new DecisionTree(new Random(), new Average(Experiment.Cost.resultTime, true), 0.5, 20, trainData, new ArrayList<Parameter>(), instanceProperties, false);
		
		File dotFile = new File("./test.dot");
		instancePropertyTree.printDot(dotFile);
		
		HashMap<String, String> instancePropertyVariables = new HashMap<String, String>();
		HashMap<String, String> parameterVariables = new HashMap<String, String>();
		
		
		bw.write("\t/**\n\t*\n");
		
		int vcounter = 0;
		for (Property ip : instancePropertyTree.instanceProperties) {
			bw.write("\t* @param ip_" + vcounter + " input: instance property " + ip.getName() + "\n");
			instancePropertyVariables.put(ip.getName(), "ip_" + (vcounter++));
		}
		vcounter = 0;
		for (Parameter p : params) {
			bw.write("\t* @param param_" + vcounter + " output: parameter " + p.getName() + "\n");
			parameterVariables.put(p.getName(), "param_" + (vcounter++));
		}*/
		bw.write("\t*/\n");
	/*	bw.write("\tint findParameterValues(");
		for (int i = 0; i < instancePropertyTree.instanceProperties.size(); i++) {
			Property p = instancePropertyTree.instanceProperties.get(i);
			Domain d = instancePropertyTree.instancePropertyDomains.get(i);
			String type = "char*";
			if (d instanceof IntegerDomain) {
				type = "int";
			} else if (d instanceof RealDomain) {
				type = "double";
			}
			bw.write(type + " " + instancePropertyVariables.get(p.getName()) + ", ");
		}
		
		int i = 0;
		for (Parameter p : params) {
			String type = "char**";
			if (p.getDomain() instanceof IntegerDomain) {
				type = "int*";
			} else if (p.getDomain() instanceof RealDomain) {
				type = "double*";
			}
			bw.write(type + " " + parameterVariables.get(p.getName()) + (i != params.size()-1 ? ", " : ""));
			i++;
		}
		bw.write(") {\n");
		bw.write("\tint found = 0;\n");
		
		writeSolverCode(bw, instancePropertyTree.root, parameterVariables, instancePropertyVariables, params, 2);
		bw.write("\treturn !found;\n");
		bw.write("\t}\n");
		bw.close();*/
	
	}
	
	
}
