package edacc.configurator.aac.search.ibsutils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.sql.SQLException;
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

import org.w3c.dom.Attr;

import edacc.api.API;
import edacc.api.APIImpl;
import edacc.api.costfunctions.Average;
import edacc.api.costfunctions.CostFunction;
import edacc.api.costfunctions.PARX;
import edacc.model.ComputationMethodDoesNotExistException;
import edacc.model.Experiment;
import edacc.model.ExperimentResult;
import edacc.model.ExperimentResultDAO;
import edacc.model.Instance;
import edacc.model.InstanceClassMustBeSourceException;
import edacc.model.InstanceDAO;
import edacc.model.InstanceHasProperty;
import edacc.model.InstanceHasPropertyDAO;
import edacc.model.InstanceHasPropertyNotInDBException;
import edacc.model.InstanceProperty;
import edacc.model.NoConnectionToDBException;
import edacc.model.Property;
import edacc.model.PropertyDAO;
import edacc.model.PropertyNotInDBException;
import edacc.model.SolverConfiguration;
import edacc.model.SolverConfigurationDAO;
import edacc.parameterspace.Parameter;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.domain.*;
import edacc.parameterspace.graph.ParameterGraph;
import edacc.properties.PropertyTypeNotExistException;
import edacc.util.Pair;

public class DecisionTree {

	private Node root;
	private Random rng;
	
	private List<Parameter> params;
	private ArrayList<Attribute> attributes;
	private ArrayList<Property> instanceProperties;
	private ArrayList<Domain> instancePropertyDomains;
	private double alpha;
	private int max_results;
	private CostFunction func;
	private IntegerDomain instanceIdDomain;
	private List<Node> leafNodes;
	
	private Double eps = 0.000001;
	
	
	public DecisionTree(Random rng, CostFunction func, double alpha, int max_results, List<Pair<ParameterConfiguration, List<ExperimentResult>>> trainData, List<Parameter> params, List<String> instancePropertyNames, boolean useInstanceId) throws NoConnectionToDBException, PropertyNotInDBException, PropertyTypeNotExistException, ComputationMethodDoesNotExistException, SQLException, IOException, InstanceHasPropertyNotInDBException {
		this.rng = rng;
		this.func = func;
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
		instanceProperties = new ArrayList<Property>();
		instancePropertyDomains = new ArrayList<Domain>();
		List<Property> dbInstanceProperties = PropertyDAO.getAllInstanceProperties();
		for (String name : instancePropertyNames) {
			boolean found = false;
			for (Property p : dbInstanceProperties) {
				if (p.getName().equals(name)) {
					found = true;
					instanceProperties.add(p);
					instancePropertyDomains.add(null);
					break;
				}
			}
			if (!found) {
				throw new IllegalArgumentException("Did not find instance property: " + name);
			}
		}
		
		int minInstanceId = Integer.MAX_VALUE;
		int maxInstanceId = 0;
		
		List<Sample> sample = new ArrayList<Sample>();
		for (Pair<ParameterConfiguration, List<ExperimentResult>> data : trainData) {
			Comparable[] parameterValues = new Comparable[params.size()];
			
			for (int i = 0; i < params.size(); i++) {
				parameterValues[i] = (Comparable) data.getFirst().getParameterValue(params.get(i));
			}
			LinkedList<ExperimentResult> results = new LinkedList<ExperimentResult>();
			results.addAll(data.getSecond());
			Collections.sort(results, new InstanceIdSort());
			while (!results.isEmpty()) {
				List<ExperimentResult> sampleResults = new ArrayList<ExperimentResult>();
				int instanceId = results.getFirst().getInstanceId();
				while (!results.isEmpty() && results.getFirst().getInstanceId() == instanceId) {
					sampleResults.add(results.poll());
				}
				Comparable[] instancePropertyValues = new Comparable[instanceProperties.size()];
				for (int i = 0; i < instanceProperties.size(); i++) {
					
					InstanceHasProperty ihp = InstanceDAO.getById(instanceId).getPropertyValues().get(instanceProperties.get(i).getId());//InstanceHasPropertyDAO.getByInstanceAndProperty(InstanceDAO.getById(instanceId), instanceProperties.get(i));
					try {
						instancePropertyValues[i] = (Comparable) instanceProperties.get(i).getPropertyValueType().getJavaTypeRepresentation(ihp.getValue());
						//System.err.println("instance id = " + instanceId + " IP = " + instancePropertyValues[i]);
					
					} catch (Exception ex) {
						//System.err.println("IP =  null");
						instancePropertyValues[i] = null;
					}
					
					// update domain for corresponding property
					if (instancePropertyValues[i] != null) {
						if (instanceProperties.get(i).getPropertyValueType().getJavaType() == Integer.class) {
							Integer value = (Integer) instancePropertyValues[i];
							IntegerDomain d = (IntegerDomain) instancePropertyDomains.get(i);
							if (d == null) {
								d = new IntegerDomain(value, value);
								instancePropertyDomains.set(i, d);
							}
							if (d.getLow() > value) {
								d.setLow(value);
							}
							if (d.getHigh() < value) {
								d.setHigh(value);
							}
						} else if (instanceProperties.get(i).getPropertyValueType().getJavaType() == Double.class || instanceProperties.get(i).getPropertyValueType().getJavaType() == Float.class) {
							Double value = null;
							if (instanceProperties.get(i).getPropertyValueType().getJavaType() == Double.class) {
								value = (Double) instancePropertyValues[i];
							} else {
								value = ((Float) instancePropertyValues[i]).doubleValue();
							}
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
						} else if (instanceProperties.get(i).getPropertyValueType().getJavaType() == String.class) {
							String value = (String) instancePropertyValues[i];
							CategoricalDomain d = (CategoricalDomain) instancePropertyDomains.get(i);
							if (d == null) {
								d = new CategoricalDomain(new HashSet<String>());
							}
							Set<String> set = d.getCategories();
							set.add(value);
							d.setCategories(set);
						} else {
							throw new IllegalArgumentException("Invalid property value type: " + instanceProperties.get(i).getDescription());
						}
					}
						
				}
				
				if (instanceId < minInstanceId)
					minInstanceId = instanceId;
				if (instanceId > maxInstanceId)
					maxInstanceId = instanceId;
				
				// TODO: remove:
				boolean use = true;
				for (ExperimentResult res : sampleResults)
					if (!String.valueOf(res.getResultCode().getResultCode()).startsWith("1")) {
						use = false;
						break;
					}
					
				if (!use)
					continue;
				
				Sample s = new Sample(data.getFirst(), parameterValues, instancePropertyValues, sampleResults, instanceId);
				sample.add(s);
			}
		}
		
		instanceIdDomain = new IntegerDomain(minInstanceId, maxInstanceId);
		
		root.results = sample;
		root.stddev = stdDev(sample);
		
		int domainsSize = params.size() + instanceProperties.size() + (useInstanceId ? 1 : 0);
		Domain[] domains = new Domain[domainsSize];
		
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
		if (useInstanceId) {
			attributes.add(new Attribute(new SampleValueType("instanceId"), instanceIdDomain, d_index));
			domains[d_index++] = instanceIdDomain;
		}
		
		initializeNode(root, domains);
		train(root, domains);
	}

	private void train(Node node, Domain[] domains) {
		if (node.left == null) {
			//System.err.println("Node has no children");
			return;
		}

		if (node.left.stddev < alpha || node.left.results.size() <= max_results) {
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
			initializeNode(node.left, domains);
			train(node.left, domains);
			// backtracking
			if (node.left.domain != null) {
				domains[p_index] = tmpDomain;
			}
		}
		if (node.right.stddev < alpha || node.right.results.size() <= max_results) {
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
			initializeNode(node.right, domains);
			train(node.right, domains);
			// backtracking
			if (node.right.domain != null) {
				domains[p_index] = tmpDomain;
			}
		}
		if (node.nullNode.stddev < alpha || node.nullNode.results.size() <= max_results) {
			// no information can be gained in this node
			node.nullNode.domains = new Domain[domains.length];
			System.arraycopy(domains, 0, node.nullNode.domains, 0, domains.length);
		//	node.nullNode.domains[node.nullNode.attr.index] = node.nullNode.domain;		
			leafNodes.add(node.nullNode);
		} else {
			// TODO: null domain??
			initializeNode(node.nullNode, domains);
			train(node.nullNode, domains);
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
	
	private SplitAttribute findOptimalSplitAttribute(double stddev, List<Sample> data) {
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
	
	private void initializeNode(Node node, Domain[] domains) {
		List<Sample> results = node.results;
		if (results.isEmpty()) {
			throw new IllegalArgumentException("results.isEmpty() is true");
		}
		SplitAttribute sa = findOptimalSplitAttribute(node.stddev, results);
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
			if (node.results.size() <= max_results || node.stddev > stddev) {
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
	
	public QueryResult query(double beta) {
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
	}
	
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

	public Float getCost(ParameterConfiguration config, int instanceId) throws NoConnectionToDBException, PropertyNotInDBException, PropertyTypeNotExistException, ComputationMethodDoesNotExistException, InstanceClassMustBeSourceException, SQLException, IOException, InstanceHasPropertyNotInDBException {
		List<ExperimentResult> results = getResults(config, instanceId);
		if (results.size() == 0)
			return null;
		return func.calculateCost(results);
	}
	
	public List<ExperimentResult> getResults(ParameterConfiguration config, int instanceId) throws NoConnectionToDBException, PropertyNotInDBException, PropertyTypeNotExistException, ComputationMethodDoesNotExistException, InstanceClassMustBeSourceException, SQLException, IOException, InstanceHasPropertyNotInDBException {
		Node curNode = root;
		Map<Parameter, Object> paramValues = config.getParameter_instances();
		
		Comparable[] parameterValues = new Comparable[params.size()];
		
		for (int i = 0; i < params.size(); i++) {
			parameterValues[i] = (Comparable) config.getParameterValue(params.get(i));
		}
		
		Comparable[] instancePropertyValues = new Comparable[instanceProperties.size()];
		for (int i = 0; i < instanceProperties.size(); i++) {
			InstanceHasProperty ihp = InstanceDAO.getById(instanceId).getPropertyValues().get(instanceProperties.get(i).getId());//InstanceHasPropertyDAO.getByInstanceAndProperty(InstanceDAO.getById(instanceId), instanceProperties.get(i));
			try {
				instancePropertyValues[i] = (Comparable) instanceProperties.get(i).getPropertyValueType().getJavaTypeRepresentation(ihp.getValue());
			} catch (Exception ex) {
				instancePropertyValues[i] = null;
			}
		}
		
		Sample sample = new Sample(config, parameterValues, instancePropertyValues, null, instanceId);
		
		List<ExperimentResult> res = new ArrayList<ExperimentResult>();
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
			if (!found)
				return res;
		}
		
		
		if (curNode.results.size() == 0)
			return res;
		for (Sample s : curNode.results) {
			res.addAll(s.results);
		}
		return res;
	}

	private abstract class Node {
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
	
	private class SplitAttribute {
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
	
	private class Sample {
		ParameterConfiguration config;
		Comparable[] parameterValues;
		Comparable[] instancePropertyValues;
		Instance instance;
		List<ExperimentResult> results;
		float cost;
		int instanceId;
		
		public Sample(ParameterConfiguration config, Comparable[] parameterValues, Comparable[] instancePropertyValues, List<ExperimentResult> results, int instanceId) {
			this.config = config;
			this.parameterValues = parameterValues;
			this.instancePropertyValues = instancePropertyValues;
			this.results = results;
			if (results != null) {
				this.cost = func.calculateCost(results);
			} else {
				this.cost = 0.f;
			}
			this.instanceId = instanceId;
		}
		
		public Object getValue(Attribute p) {
			if (p.attribute instanceof Parameter) {
				return parameterValues[p.index];
			} else if (p.attribute instanceof Property) {
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
	
	private class SampleValueType {
		String name;
		
		public SampleValueType(String name) {
			this.name = name;
		}
		
		@Override
		public String toString() {
			return "[SVT: " + name + "]";
		}
	}
	
	private class Attribute {
		Domain domain;
		Object attribute;
		int index;
		public Attribute(Object attribute, Domain domain, int index) {
			if (attribute instanceof Property || attribute instanceof Parameter
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
	
	private class InstanceIdSort implements Comparator<ExperimentResult> {

		@Override
		public int compare(ExperimentResult arg0, ExperimentResult arg1) {
			return arg0.getInstanceId() - arg1.getInstanceId();
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
	
	public int printDotNodeDescription(Node node, int nodenum, BufferedWriter br) throws Exception {
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
	}
	
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

		if (action.equals("query")) {
			queryTree(api, graph, expids.get(0), trainData, params, instancePropertyNames);
		} else if (action.equals("source")) {
			writeSolverCode(trainData, params, instancePropertyNames);
		}
		
	}
	
	public static void queryTree(API api, ParameterGraph graph, int expid, List<Pair<ParameterConfiguration, List<ExperimentResult>>> trainData, List<Parameter> params, ArrayList<String> instancePropertyNames) throws Exception {

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
	}
	
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
			for (Sample s: node.results) {
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
			}
			
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
				
				for (ParameterConfiguration c : paramConfigs) {
					
				}
				
				
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
			Attribute attr = node.left.attr;
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
			}
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
		
		DecisionTree instancePropertyTree = new DecisionTree(new Random(), new Average(Experiment.Cost.resultTime, true), 0.5, 20, trainData, new ArrayList<Parameter>(), instanceProperties, false);
		
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
		}
		bw.write("\t*/\n");
		bw.write("\tint findParameterValues(");
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
		bw.close();
	
	}
	
	
}
