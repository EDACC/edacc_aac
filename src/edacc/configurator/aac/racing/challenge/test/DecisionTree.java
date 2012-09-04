package edacc.configurator.aac.racing.challenge.test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Comparator;
import java.util.Map.Entry;

import edacc.configurator.aac.racing.challenge.Clustering;
import edacc.parameterspace.Parameter;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.domain.*;
import edacc.util.Pair;

public class DecisionTree implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2157239629L;

	private Node root;
	
	private List<Parameter> params;
	private ArrayList<Attribute> attributes;
	private ArrayList<Domain> instancePropertyDomains;
	private double alpha;
	private int max_results;
	private IntegerDomain instanceIdDomain;
	private List<Node> leafNodes;
	public List<Pair<ParameterConfiguration, Integer>> pconfigs;
	
	private Double eps = 0.000001;
	
	
	public DecisionTree(double alpha, int max_results, List<Pair<ParameterConfiguration, Integer>> pconfigs, List<Pair<ParameterConfiguration, List<Pair<Integer, Float>>>> trainData, List<Parameter> params, HashMap<Integer, float[]> featureMap, int numFeatures, boolean useInstanceId, Clustering clustering, Clustering c_orig) {
		this.pconfigs = pconfigs;
		this.alpha = alpha;
		this.max_results = max_results;
		root = new NullNode(null);
		/*List<Pair<ParameterConfiguration, List<ExperimentResult>>> sample = new ArrayList<Pair<ParameterConfiguration, List<ExperimentResult>>>();
		for (int i = 0; i < trainData.size(); i++) {
			sample.add(trainData.get(rng.nextInt(trainData.size())));
		}*/
		this.params = params;
		
		//params = new ArrayList<Parameter>();
		//params.addAll(graph.getParameterSet());
		leafNodes = new ArrayList<Node>();
		instancePropertyDomains = new ArrayList<Domain>();
		for (int i = 0; i < numFeatures; i++) {
			instancePropertyDomains.add(null);
		}
		
		int minInstanceId = Integer.MAX_VALUE;
		int maxInstanceId = 0;
		
		List<Sample> sample = new ArrayList<Sample>();
		for (Pair<ParameterConfiguration, List<Pair<Integer, Float>>> data : trainData) {
			Comparable[] parameterValues = new Comparable[params.size()];
			
			for (int i = 0; i < params.size(); i++) {
				parameterValues[i] = (Comparable) data.getFirst().getParameterValue(params.get(i));
			}
			LinkedList<Pair<Integer, Float>> results = new LinkedList<Pair<Integer, Float>>();
			results.addAll(data.getSecond());
			while (!results.isEmpty()) {
				Pair<Integer, Float> samplePair = results.poll();
				int instanceId = samplePair.getFirst();
				Comparable[] instancePropertyValues = new Comparable[numFeatures];
				
				float[] featureValues = featureMap.get(instanceId);
				
				for (int i = 0; i < numFeatures; i++) {					
					instancePropertyValues[i] = featureValues[i];
					
					// update domain for corresponding property
					if (instancePropertyValues[i] != null) {
						Double value = ((Float) instancePropertyValues[i]).doubleValue();
						RealDomain d = (RealDomain) instancePropertyDomains.get(i);
						if (d == null) {
							d = new RealDomain(value, value);
							instancePropertyDomains.set(i, d);
						}
						if (d.getLow() > value) {
							d.setLow(value);
						}
						if (d.getHigh() < value) {
							d.setHigh(value);
						}
					}
				}
				
				if (instanceId < minInstanceId)
					minInstanceId = instanceId;
				if (instanceId > maxInstanceId)
					maxInstanceId = instanceId;
				
				
			//	System.out.println("SAMPLEPAIR: " + samplePair.getSecond());
				if (data.getFirst() == null) {
					System.out.println("NULL!!");
				}
				Sample s = new Sample(data.getFirst(), parameterValues, instancePropertyValues, samplePair.getSecond(), instanceId);
				sample.add(s);
			}
		}
		
		for (Domain d : instancePropertyDomains) {
			((RealDomain) d).setLow(Double.MIN_VALUE);
			((RealDomain) d).setHigh(Double.MAX_VALUE);
		}
		
		List<Sample> validationSamples = new LinkedList<Sample>();
		Random r= new Random();
		while (validationSamples.size() < 0.2f * sample.size()) {
			int rand = r.nextInt(sample.size());
			validationSamples.add(sample.get(rand));
			sample.remove(rand);
		}
		
		instanceIdDomain = new IntegerDomain(minInstanceId, maxInstanceId);
		
		root.results = sample;
		root.stddev = stdDev(sample);
		
		int domainsSize = params.size() + numFeatures + (useInstanceId ? 1 : 0);
		Domain[] domains = new Domain[domainsSize];
		
		int d_index = 0;
		attributes = new ArrayList<Attribute>();
		for (Parameter p : params) {
			attributes.add(new Attribute(p, p.getDomain(), d_index));
			domains[d_index++] = p.getDomain();
		}
		for (int i = 0; i < numFeatures; i++) {
			attributes.add(new Attribute("IP: " + i, instancePropertyDomains.get(i), d_index));
			domains[d_index++] = instancePropertyDomains.get(i);
		}
		if (useInstanceId) {
			attributes.add(new Attribute(new SampleValueType("instanceId"), instanceIdDomain, d_index));
			domains[d_index++] = instanceIdDomain;
		}
		
		initializeNode(root, domains,0);
		train(root, domains,0);
		
		float error_sum = 0.f;
		int error_num = 0;
		for (Sample s : validationSamples) {
			if (!featureMap.containsKey(s.instanceId)) {
				System.out.println("WTF?");
			}
			Float q_ms = this.getCost(s.config, featureMap.get(s.instanceId));
			if (q_ms == null) {
				q_ms = 0.f;
			}
			//float r_ms = clustering.getMembership(s.scid, s.instanceId);
			float error = 0.f;
			if (s.cost != 0.f && q_ms != 0.f) {
				error = Math.abs((q_ms / s.cost) - 1.f);
				error_sum += error;
				error_num++;
			}
			
			System.out.println("query : real = " + q_ms + " : " + s.cost + "   error: " + error);
		}
		System.out.println("Error: " + error_sum / (float) error_num);
		
		
		
		float num = 0;
		float perf = 0;
		for (Sample s : validationSamples) {
				int clazz = this.getSolverConfigId(featureMap.get(s.instanceId));
				//if (!entry.getKey().equals(clazz)) {
				//	wrong+=1;
				//}
				//perf += c_orig.getMembership(clazz, s.instanceId);
				//num+= c_orig.getMaximumMembership(s.instanceId);
				perf += c_orig.getCost(clazz, s.instanceId);
				num += c_orig.getMinimumCost(s.instanceId);
				/*float cost = clu.getCost(clazz, instanceid);;
				if (Float.isInfinite(cost))
					cost_tree += 150.f * 10;
				else 
					cost_tree += cost;
				cost_best += clu.getCost(entry.getKey(), instanceid);*/
		}
		float performance = num/perf; //perf/num;
		System.out.println("[RegressionTree] #all = " + num);
		System.out.println("[RegressionTree] perf(T) = " + performance);
		System.out.println("[RegressionTree] perf(C) = " + c_orig.performance(clustering.getClustering(false)));
		
	}

	private void train(Node node, Domain[] domains, int depth) {
		if (node.left == null) {
			//System.err.println("Node has no children");
			return;
		}

		if (node.left.stddev < alpha) {
			// no information can be gained in this node
			node.left.domains = new Domain[domains.length];
			System.arraycopy(domains, 0, node.left.domains, 0, domains.length);
			node.left.domains[node.left.attr.index] = node.left.domain;
			leafNodes.add(node.left);
		} else {
			// save old domain for backtracking
			Domain tmpDomain = null;
			int p_index = -1;
			if (node.left.domain != null) {
				p_index = node.left.attr.index;
				tmpDomain = domains[p_index];
				domains[p_index] = node.left.domain;
			} else {
				throw new IllegalArgumentException("NULL!");
			}
			// initialize and train node
			initializeNode(node.left, domains, depth);
			train(node.left, domains, depth+1);
			// backtracking
			if (node.left.domain != null) {
				domains[p_index] = tmpDomain;
			}
		}
		if (node.right.stddev < alpha) {
			// no information can be gained in this node
			node.right.domains = new Domain[domains.length];
			System.arraycopy(domains, 0, node.right.domains, 0, domains.length);
			node.right.domains[node.right.attr.index] = node.right.domain;		
			leafNodes.add(node.right);
		} else {
			// save old domain for backtracking
			Domain tmpDomain = null;
			int p_index = -1;
			if (node.right.domain != null) {
				p_index = node.right.attr.index;
				tmpDomain = domains[p_index];
				domains[p_index] = node.right.domain;
			} else {
				throw new IllegalArgumentException("NULL!");
			}
			// initialize and train node
			initializeNode(node.right, domains, depth);
			train(node.right, domains, depth+1);
			// backtracking
			if (node.right.domain != null) {
				domains[p_index] = tmpDomain;
			}
		}
		if (node.nullNode.stddev < alpha) {
			// no information can be gained in this node
			node.nullNode.domains = new Domain[domains.length];
			System.arraycopy(domains, 0, node.nullNode.domains, 0, domains.length);
		//	node.nullNode.domains[node.nullNode.attr.index] = node.nullNode.domain;		
			leafNodes.add(node.nullNode);
		} else {
			// TODO: null domain??
			initializeNode(node.nullNode, domains, depth);
			train(node.nullNode, domains, depth+1);
		}
	}
	
	private double stdDev(List<Sample> data) {
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
	}
	
	private SplitAttribute findOptimalSplitAttribute(double stddev, List<Sample> data, int depth) {
		double stdDevReduction = 0.;
		double resStdDevFirst = 0.;
		double resStdDevLast = 0.;
		double resStdDevNull = 0.;
		List<Sample> resFirst = null;
		List<Sample> resLast = null;
		List<Sample> resNull = null;
		Attribute resAttr = null;
		Object resValue = null;
				
		for (Attribute attr : attributes) {
			//System.err.println("CURRENT ATTRIBUTE: " + attr);
			List<Sample> nullValues = new ArrayList<Sample>();
			
			List<Pair<Comparable, Sample>> values = new ArrayList<Pair<Comparable, Sample>>();
			for (int i = 0; i < data.size(); i++) {
				Object pvalue = data.get(i).getValue(attr);
				if (pvalue == null) {
					nullValues.add(data.get(i));
				} else {
					values.add(new Pair<Comparable, Sample>((Comparable) pvalue, data.get(i)));
				}
			}
			Collections.sort(values, new Comparator<Pair<Comparable, Sample>>() {

				@Override
				public int compare(Pair<Comparable, Sample> o1, Pair<Comparable, Sample> o2) {
					return o1.getFirst().compareTo(o2.getFirst());
				}


			});
			double stdDevNull = stdDev(nullValues);
			
			for (int i = 0; i < values.size(); i++) {
				
				Comparable value = values.get(i).getFirst();
				if (attr.domain instanceof IntegerDomain || attr.domain instanceof RealDomain) {
					if (i == values.size() -1)
						continue;
					while (i + 1 < values.size() && values.get(i+1).getFirst().equals(value))
						i++;
					List<Sample> first = new ArrayList<Sample>();
					List<Sample> last = new ArrayList<Sample>();
					
					for (int j = 0; j <= i; j++) {
						first.add(values.get(j).getSecond());
					}
					for (int j = i+1; j < values.size(); j++) {
						last.add(values.get(j).getSecond());
					}
					double stdDevFirst = stdDev(first);
					double stdDevLast = stdDev(last);
					int n = first.size() + last.size() + nullValues.size();
					double tmpStdDev = 0.;
					if (first.size() > 0) {
						tmpStdDev += (first.size() / (double) n)*stdDevFirst;
					}
					if (last.size() > 0) {
						tmpStdDev += (last.size() / (double) n)*stdDevLast;
					}
					if (nullValues.size() > 0) {
						tmpStdDev += (nullValues.size() / (double) n)*stdDevNull;
					}
					double tmpStdDevReduction = stddev - tmpStdDev;
					if (tmpStdDevReduction > eps && tmpStdDevReduction > stdDevReduction) {
						resAttr = attr;
						resFirst = first;
						resLast = last;
						resNull = nullValues;
						resValue = attr.domain.getMidValueOrNull(values.get(i).getFirst(), values.get(i).getSecond());
						if (resValue == null) {
							resValue = values.get(i).getFirst();
						}
						stdDevReduction = tmpStdDevReduction;
						resStdDevFirst = stdDevFirst;
						resStdDevLast = stdDevLast;
						resStdDevNull = stdDevNull;
					}
					
				} else {
					int index_start = i;
					int index_end = i;
					while (i + 1 < values.size() && values.get(i+1).getFirst().equals(value)) {
						i++;
						index_end++;
					}
					
					List<Sample> first = new ArrayList<Sample>();
					List<Sample> last = new ArrayList<Sample>();
					
					for (int j = 0; j < index_start; j++) {
						last.add(values.get(j).getSecond());
					}
					
					for (int j = index_end+1; j < values.size(); j++) {
						last.add(values.get(j).getSecond());
					}
					
					for (int j = index_start; j <= index_end; j++) {
						first.add(values.get(j).getSecond());
					}
					
					double stdDevFirst = stdDev(first);
					double stdDevLast = stdDev(last);
					int n = first.size() + last.size() + nullValues.size();
					double tmpStdDev = 0.;
					if (first.size() > 0) {
						tmpStdDev += (first.size() / (double) n)*stdDevFirst;
					}
					if (last.size() > 0) {
						tmpStdDev += (last.size() / (double) n)*stdDevLast;
					}
					if (nullValues.size() > 0) {
						tmpStdDev += (nullValues.size() / (double) n)*stdDevNull;
					}
					double tmpStdDevReduction = stddev - tmpStdDev;
					if (tmpStdDevReduction > eps && tmpStdDevReduction > stdDevReduction) {
						resAttr = attr;
						resFirst = first;
						resLast = last;
						resNull = nullValues;
						resValue = attr.domain.getMidValueOrNull(values.get(i).getFirst(), values.get(i).getSecond());
						if (resValue == null) {
							resValue = values.get(i).getFirst();
						}
						stdDevReduction = tmpStdDevReduction;
						resStdDevFirst = stdDevFirst;
						resStdDevLast = stdDevLast;
						resStdDevNull = stdDevNull;
					}
				}
			}
		}
		//System.err.println("STDDEVREDUCTION = " + stdDevReduction);
		return new SplitAttribute(stdDevReduction, resFirst, resLast, resNull, resAttr, resValue, resStdDevFirst, resStdDevLast, resStdDevNull);
	}
	
	private void initializeNode(Node node, Domain[] domains, int depth) {
		List<Sample> results = node.results;
		if (results.isEmpty()) {
			throw new IllegalArgumentException("results.isEmpty() is true");
		}
		SplitAttribute sa = findOptimalSplitAttribute(node.stddev, results, depth);
		if (sa.attr == null) {
			return;
		}
		if (sa.firstValues.isEmpty() || sa.lastValues.isEmpty()) {
			System.out.println("ERROR EMPTY RED " + sa.stdDevReduction);
		}
	//	node.param = sp.param;
		Domain attributeDomain = domains[sa.attr.index];
		if (attributeDomain instanceof IntegerDomain) {
			Integer split = (Integer) sa.value;
			IntegerDomain iParamDomain = (IntegerDomain) attributeDomain;
			IntegerDomain leftNodeDomain = new IntegerDomain(iParamDomain.getLow(), split);
			IntegerDomain rightNodeDomain = new IntegerDomain(split, iParamDomain.getHigh());
			node.left = new IntegerDomainNode(sa.attr, leftNodeDomain);
			node.right = new IntegerDomainNode(sa.attr, rightNodeDomain);
		} else if (attributeDomain instanceof RealDomain) {
			Double split;
			if (sa.value instanceof Float) {
				split = ((Float) sa.value).doubleValue();
			} else if (sa.value instanceof Double) {
				split = (Double) sa.value;
			} else {
				throw new IllegalArgumentException("Wrong class for real domain: " + sa.value.getClass());
			}
			RealDomain dParamDomain = (RealDomain) attributeDomain;
			RealDomain leftNodeDomain = new RealDomain(dParamDomain.getLow(), split);
			RealDomain rightNodeDomain = new RealDomain(split, dParamDomain.getHigh());
			node.left = new RealDomainNode(sa.attr, leftNodeDomain);
			node.right = new RealDomainNode(sa.attr, rightNodeDomain);
		} else if (attributeDomain instanceof OrdinalDomain) {
			// TODO: "real" split instead of equality of a single value? It's ordinal!
			String value = (String) sa.value;
			OrdinalDomain oParamDomain = (OrdinalDomain) attributeDomain;
			List<String> leftValues = new ArrayList<String>();
			leftValues.add(value);
			List<String> rightValues = new ArrayList<String>();
			for (String val : oParamDomain.getOrdered_list()) {
				if (!val.equals(value)) {
					rightValues.add(val);
				}
			}
			OrdinalDomain leftNodeDomain = new OrdinalDomain(leftValues);
			OrdinalDomain rightNodeDomain = new OrdinalDomain(rightValues);
			node.left = new ValuesNode(sa.attr, leftNodeDomain);
			node.right = new ValuesNode(sa.attr, rightNodeDomain);
		} else if (attributeDomain instanceof CategoricalDomain) {
			String value = (String) sa.value;
			CategoricalDomain cParamDomain = (CategoricalDomain) attributeDomain;
			Set<String> leftValues = new HashSet<String>();
			leftValues.add(value);
			Set<String> rightValues = new HashSet<String>();
			for (String val : cParamDomain.getCategories()) {
				if (!val.equals(value)) {
					rightValues.add(val);
				}
			}
			CategoricalDomain leftNodeDomain = new CategoricalDomain(leftValues);
			CategoricalDomain rightNodeDomain = new CategoricalDomain(rightValues);
			node.left = new ValuesNode(sa.attr, leftNodeDomain);
			node.right = new ValuesNode(sa.attr, rightNodeDomain);
		} else {
			// TODO: add flag, mixed, optional domain!
			throw new IllegalArgumentException("Domain: " + attributeDomain + " currently not supported.");
		}
		node.left.results = sa.firstValues;
		node.left.stddev = sa.stdDevFirst;
		node.right.results = sa.lastValues;
		node.right.stddev = sa.stdDevLast;
		
		node.nullNode = new NullNode(sa.attr);
		node.nullNode.results = sa.nullValues;
		node.nullNode.stddev = sa.stdDevNull;
		
		node.results.clear();
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
	
	private SearchResult getDomainOrNull(Node node, double stddev, Domain[] domains) {
		if (node.left == null && node.right == null && node.nullNode == null) {
			if (node.stddev > stddev) {
				List<Pair<Parameter, Domain>> parameters = new ArrayList<Pair<Parameter, Domain>>();
				for (int i = 0; i < params.size(); i++) {
					parameters.add(new Pair<Parameter, Domain>(params.get(i), domains[i]));
				}
				Set<Integer> instanceIds = new HashSet<Integer>();
				for (Object o : domains[domains.length-1].getDiscreteValues()) {
					Integer i = (Integer) o;
					instanceIds.add(i);
				}
				Set<ParameterConfiguration> configs = new HashSet<ParameterConfiguration>();
				for (Sample s : node.results) {
					configs.add(s.config);
				}
				
				return new SearchResult(configs, parameters, instanceIds);
			}
			return null;
		}
		
		SearchResult result = null;
		if (node.left != null) {
			// save old domain for backtracking
			Domain tmpDomain = null;
			int p_index = -1;
			if (node.left.domain != null) {
				p_index = node.left.attr.index;
				tmpDomain = domains[p_index];
				domains[p_index] = node.left.domain;
			}
			
			result = getDomainOrNull(node.left, stddev, domains);
			
			// backtracking
			if (node.left.domain != null) {
				domains[p_index] = tmpDomain;
			}
		}
		if (node.right != null) {
			// save old domain for backtracking
			Domain tmpDomain = null;
			int p_index = -1;
			if (node.right.domain != null) {
				p_index = node.right.attr.index;
				tmpDomain = domains[p_index];
				domains[p_index] = node.right.domain;
			}
			
			result = mergeSearchResults(result, getDomainOrNull(node.right, stddev, domains));
			// backtracking
			if (node.right.domain != null) {
				domains[p_index] = tmpDomain;
			}			
		}
		if (node.nullNode != null) {
			result = mergeSearchResults(result, getDomainOrNull(node.nullNode, stddev, domains));
		}
		return result;
	}
	
	/*public QueryResult query(double beta) {
		Domain[] domains = new Domain[params.size() + instanceProperties.size() + 1];
		int d_index = 0;
		attributes = new ArrayList<Attribute>();
		for (Parameter p : params) {
			attributes.add(new Attribute(p, p.getDomain(), d_index));
			domains[d_index++] = p.getDomain();
		}
		for (int i = 0; i < instanceProperties.size(); i++) {
			attributes.add(new Attribute(instanceProperties.get(i), instancePropertyDomains.get(i), d_index));
			domains[d_index++] = instancePropertyDomains.get(i);
		}
		attributes.add(new Attribute(new SampleValueType("instanceId"), instanceIdDomain, d_index));
		domains[d_index++] = instanceIdDomain;
		
		SearchResult sr = getDomainOrNull(root, beta, domains);
		if (sr == null) {
			return null;
		}
		return new QueryResult (sr.configs, sr.parameters, sr.instanceIds);
	}*/
	
	public class QueryResult {
		public Set<ParameterConfiguration> configs;
		public List<Pair<Parameter, Domain>> parameterDomains;
		public Set<Integer> instanceIds;
		public QueryResult(Set<ParameterConfiguration> configs, List<Pair<Parameter, Domain>> parameterDomains, Set<Integer> instanceIds) {
			this.configs = configs;
			this.parameterDomains = parameterDomains;
			this.instanceIds = instanceIds;
		}
	}
	
	public Integer getSolverConfigId(float[] features) {
		float membership = -1.f;
		int scid = -1;
		for (Pair<ParameterConfiguration, Integer> pc : pconfigs) {
			Float tmp = getCost(pc.getFirst(), features);
			if (tmp != null && tmp > membership) {
				membership = tmp;
				scid = pc.getSecond();
			}
		}
		return scid == -1 ? null : scid;
	}

	public Float getCost(ParameterConfiguration config, float[] features) {
		List<Float> costs = getCosts(config, features);
		if (costs.size() == 0)
			return null;
		float res = 0.f;
		for (Float c : costs)
			res += c;
		res /= costs.size();
		return res;
	}
	
	public List<Float> getCosts(ParameterConfiguration config, float[] features) {
		Node curNode = root;
		Map<Parameter, Object> paramValues = config.getParameter_instances();
		
		Comparable[] parameterValues = new Comparable[params.size()];
		
		for (int i = 0; i < params.size(); i++) {
			parameterValues[i] = (Comparable) config.getParameterValue(params.get(i));
		}
		
		Comparable[] instancePropertyValues = new Comparable[features.length];
		for (int i = 0; i < features.length; i++) {
			instancePropertyValues[i] = features[i];
		}
		
		Sample sample = new Sample(config, parameterValues, instancePropertyValues, null, -1);
		
		List<Float> res = new ArrayList<Float>();
		while (curNode.left != null || curNode.right != null || curNode.nullNode != null) {
			boolean found = false;
			if (curNode.left != null) {
				Attribute attr = curNode.left.attr;
				if (curNode.left.domain.contains(sample.getValue(attr))) {
					curNode = curNode.left;
					found = true;
				}
			}
			if (!found && curNode.right != null) {
				Attribute attr = curNode.right.attr;
				if (curNode.right.domain.contains(sample.getValue(attr))) {
					curNode = curNode.right;
					found = true;
				}
			}
			if (!found && curNode.nullNode != null) {
				if (sample.getValue(curNode.nullNode.attr) == null) {
					curNode = curNode.nullNode;
					found = true;
				}
			}
			if (!found) {
				return res;
			}
		}
		
		
		if (curNode.results.size() == 0) {
			return res;
		}
		for (Sample s : curNode.results) {
			res.add(s.cost);
		}
		return res;
	}

	private abstract class Node implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = -1532234758962L;
		Attribute attr;
		double stddev;
		Node left, right;
		Node nullNode;
		List<Sample> results;
		Domain domain;
		Domain[] domains;

		public Node(Attribute attr, Domain domain) {
			this.left = null;
			this.right = null;
			this.nullNode = null;
			this.stddev = Double.NaN;
			this.attr = attr;
			this.domain = domain;
			results = new ArrayList<Sample>();
			domains = null;
		}

		public abstract boolean contains(Object value);
	}

	private class IntegerDomainNode extends Node {
		IntegerDomain domain;
		
		public IntegerDomainNode(Attribute attr, IntegerDomain domain) {
			super(attr, domain);
			this.domain = domain;
		}
		
		@Override
		public String toString() {
			return "[Integer (" + (attr == null ? "leaf" : attr.toString()) + "): " + domain.toString() + "]";
		}

		@Override
		public boolean contains(Object value) {
			if (value == null) {
				throw new IllegalArgumentException();
			}
			if (value instanceof Integer) {
				return domain.contains(value);
			} else {
				throw new IllegalArgumentException("Method contains() not applicable to class " + value.getClass());
			}
		}
	}
	
	private class RealDomainNode extends Node {
		RealDomain domain;
		
		public RealDomainNode(Attribute attr, RealDomain domain) {
			super(attr, domain);
			this.domain = domain;
		}
		
		@Override
		public String toString() {
			return "[Real (" + (attr == null ? "leaf" : attr.toString()) + "): " + domain.toString() + "]";
		}

		@Override
		public boolean contains(Object value) {
			if (value == null) {
				throw new IllegalArgumentException();
			}
			if (value instanceof Double) {
				return domain.contains(value);
			} else {
				throw new IllegalArgumentException("Method contains() not applicable to class " + value.getClass());
			}
		}
	}

	private class ValuesNode extends Node {
		Domain domain;
		
		public ValuesNode(Attribute attr, Domain domain) {
			super(attr, domain);
			this.domain = domain;
		}
		
		@Override
		public String toString() {
			return "[Values (" + (attr == null ? "leaf" : attr.toString()) + ")]";
		}
		
		@Override
		public boolean contains(Object o) {
			return domain.contains(o);
		}
	}
	
	private class NullNode extends Node {

		public NullNode(Attribute attr) {
			super(attr, null);
		}
		
		@Override
		public String toString() {
			if (this == root) {
				return "[root]";
			}
			return "[NullNode (" + (attr == null ? "leaf" : attr.toString()) + ")]";
		}

		@Override
		public boolean contains(Object value) {
			return value == null;
		}
		
	}
	
	private class SplitAttribute implements Serializable {
		double stdDevReduction = 0.;
		List<Sample> firstValues = null;
		List<Sample> lastValues = null;
		List<Sample> nullValues = null;
		Attribute attr = null;
		Object value = null;
		double stdDevFirst, stdDevLast, stdDevNull;
		public SplitAttribute(double stdDevReduction, List<Sample> firstValues,
				List<Sample> lastValues,
				List<Sample> nullValues,
				Attribute attr,
				Object value,
				double stdDevFirst, double stdDevLast, double stdDevNull) {
			this.stdDevReduction = stdDevReduction;
			this.firstValues = firstValues;
			this.lastValues = lastValues;
			this.nullValues = nullValues;
			this.attr = attr;
			this.value = value;
			this.stdDevFirst = stdDevFirst;
			this.stdDevLast = stdDevLast;
			this.stdDevNull = stdDevNull;
		}
	}
	
	private class Sample implements Serializable {
		ParameterConfiguration config;
		Comparable[] parameterValues;
		Comparable[] instancePropertyValues;
		float cost;
		int instanceId;
		
		public Sample(ParameterConfiguration config, Comparable[] parameterValues, Comparable[] instancePropertyValues, Float cost, int instanceId) {
			this.config = config;
			this.parameterValues = parameterValues;
			this.instancePropertyValues = instancePropertyValues;
			this.cost = cost == null ? 0.f : cost;
			this.instanceId = instanceId;
		}
		
		public Object getValue(Attribute p) {
			if (p.attribute instanceof Parameter) {
				return parameterValues[p.index];
			} else if (p.attribute instanceof String) {
				return instancePropertyValues[p.index - params.size()];
			} else if (p.attribute instanceof SampleValueType) {
				String type = ((SampleValueType) p.attribute).name;
				if ("instanceId".equals(type)) {
					return instanceId;
				} else {
					throw new IllegalArgumentException("Unknown SampleValueType: " + type);
				}
			} else {
				throw new IllegalArgumentException("Can't return values for class " + p.getClass());
			}
		}
	}
	
	private class SampleValueType implements Serializable {
		String name;
		
		public SampleValueType(String name) {
			this.name = name;
		}
		
		@Override
		public String toString() {
			return "[SVT: " + name + "]";
		}
	}
	
	private class Attribute implements Serializable {
		Domain domain;
		Object attribute;
		int index;
		public Attribute(Object attribute, Domain domain, int index) {
			if (attribute instanceof String || attribute instanceof Parameter
					|| attribute instanceof SampleValueType) {
				this.attribute = attribute;
				this.domain = domain;
				this.index = index;
			} else {
				throw new IllegalArgumentException("Wrong attribute type: "
					+ attribute.getClass());
			}
		}
		
		@Override
		public String toString() {
			return attribute.toString();
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
	
	/*public static void main(String[] args) throws Exception {
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

		if (action.equals("query")) {
			queryTree(api, graph, expids.get(0), trainData, params, instancePropertyNames);
		} else if (action.equals("source")) {
			writeSolverCode(trainData, params, instancePropertyNames);
		}
		
	}*/
	
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
	}	*/
	
}
