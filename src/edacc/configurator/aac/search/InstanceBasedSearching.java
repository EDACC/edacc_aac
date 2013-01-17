package edacc.configurator.aac.search;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.JobListener;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.configurator.aac.racing.ClusterRacing;
import edacc.configurator.aac.search.ibsutils.DecisionTree;
import edacc.configurator.aac.search.ibsutils.SolverConfigurationIBS;
import edacc.configurator.aac.solvercreator.Clustering;
import edacc.model.ExperimentResult;
import edacc.model.ExperimentResultDAO;
import edacc.parameterspace.Parameter;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.domain.Domain;
import edacc.parameterspace.graph.ParameterGraph;
import edacc.util.Pair;

public class InstanceBasedSearching extends SearchMethods implements JobListener {

	Double maxCost = null;
	int numCachedConfigs = 75;
	int minConfigs = 100;
	
	Integer IBSConfigsCPUTime = null;
	
	int regressionTree_maxTrainDataSize = -1;
	
	HashSet<ParameterConfiguration> existingParameterConfigurations;
	HashMap<Integer, SolverConfiguration> solverConfigs;
	Set<Integer> solvedInstances;
	List<Parameter> configurableParameters;
	ParameterGraph graph;
	List<Integer> instanceIds = new LinkedList<Integer>(); 
	List<ParameterConfiguration> defaultMutations;
	
	List<Pair<HashSet<Integer>, Pair<ParameterConfiguration, String>>> cachedParameterConfigurations;
	
	int numConfigsModel, numConfigsRandom;
	int numParams;
	
	public InstanceBasedSearching(AAC pacc, API api, Random rng, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
		super(pacc, api, rng, parameters, firstSCs, referenceSCs);
		
		numConfigsModel = 0;
		numConfigsRandom = 0;
		String val;
		if ((val = parameters.getSearchMethodParameters().get("InstanceBasedSearching_maxCost")) != null)
			maxCost = Double.parseDouble(val);
		if ((val = parameters.getSearchMethodParameters().get("InstanceBasedSearching_IBSConfigsCPUTime")) != null)
			IBSConfigsCPUTime = Integer.parseInt(val);
			
		numParams = api.getConfigurableParameters(parameters.getIdExperiment()).size();
		
		cachedParameterConfigurations = new LinkedList<Pair<HashSet<Integer>, Pair<ParameterConfiguration, String>>>();
		solvedInstances = new HashSet<Integer>();
		graph = api.loadParameterGraphFromDB(parameters.getIdExperiment());
		pacc.addJobListener(this);
		configurableParameters = api.getConfigurableParameters(parameters.getIdExperiment());
		solverConfigs = new HashMap<Integer, SolverConfiguration>();
		
		defaultMutations = new LinkedList<ParameterConfiguration>();
		for (SolverConfiguration sc : firstSCs) {
			solverConfigs.put(sc.getIdSolverConfiguration(), sc);
			defaultMutations.addAll(graph.getGaussianNeighbourhood(sc.getParameterConfiguration(), rng, 0.2f, 1, true));
		}
		existingParameterConfigurations = new HashSet<ParameterConfiguration>();
		
		pacc.log("[IBS] Cached " + defaultMutations.size() + " default mutations.");
		jobsFinished(ExperimentResultDAO.getAllByExperimentId(parameters.getIdExperiment()));
		
	
	}
	
	private HashMap<Integer, List<ExperimentResult>> getScResultMap(int instanceId) {
		HashSet<Integer> tmp = new HashSet<Integer>();
		tmp.add(instanceId);
		return getScResultMap(tmp);
	}

	private HashMap<Integer, List<ExperimentResult>> getScResultMap(Set<Integer> instanceIds) {
		HashMap<Integer, List<ExperimentResult>> res = new HashMap<Integer, List<ExperimentResult>>();
		for (SolverConfiguration sc : solverConfigs.values()) {
			for (ExperimentResult er : sc.getJobs()) {
				if (instanceIds.contains(er.getInstanceId())) {
					List<ExperimentResult> list = res.get(er.getSolverConfigId());
					if (list == null) {
						list = new LinkedList<ExperimentResult>();
						res.put(er.getSolverConfigId(), list);
					}
					list.add(er);
				}
			}
		}
		return res;
	}
	
	private SolverConfiguration createSolverConfiguration(ParameterConfiguration config, String name, Set<Integer> iids) throws Exception {
		SolverConfiguration sc = null;
		if (existingParameterConfigurations.contains(config)) {
			pacc.log("[IBS] Mutating config..");
			name = "mutated config";
			for (int t = 0; t < 50; t++) {
				graph.mutateParameterConfiguration(rng, config);
				if (!existingParameterConfigurations.contains(config)) {
					break;
				}
			}
		}
		if (!existingParameterConfigurations.contains(config)) {
			int idSolverConfig = api.createSolverConfig(parameters.getIdExperiment(), config, name);
			if (iids != null && (IBSConfigsCPUTime == null || pacc.getCumulatedCPUTime() > IBSConfigsCPUTime)) {
				sc = new SolverConfigurationIBS(idSolverConfig, config, parameters.getStatistics(), iids);
			} else {
				sc = new SolverConfiguration(idSolverConfig, config, parameters.getStatistics());
			}
			sc.setNameSearch(name);
			existingParameterConfigurations.add(config);
		}
		if (sc == null) {
			pacc.log("[IBS] Could not create solver configuration.");
		}
		return sc;
	}
	
	@Override
	public List<SolverConfiguration> generateNewSC(int num) throws Exception {
		pacc.log("[IBS] Generating " + num + " solver configurations (cache size: " + cachedParameterConfigurations.size() + ")");		
		List<SolverConfiguration> res = new LinkedList<SolverConfiguration>();
		HashMap<Integer, DecisionTree> treeCache = new HashMap<Integer, DecisionTree>();
		
		List<List<Integer>> clustering = null;
		if (pacc.racing instanceof ClusterRacing) {
			ClusterRacing clusterRacing = ((ClusterRacing) pacc.racing);
			HashMap<Integer, List<Integer>> tmp = clusterRacing.getClustering();
			if (!tmp.isEmpty()) {
				clustering = new LinkedList<List<Integer>>();
				clustering.addAll(tmp.values());
			}
		}
		
		while (num > 0) {
			if (instanceIds.isEmpty()) {
				instanceIds.addAll(solvedInstances);
			}
			num--;
			float factor = (defaultMutations.isEmpty() || existingParameterConfigurations.size() < minConfigs ? 9.f : 3.f);
			
			if (numConfigsRandom == 0 || (numConfigsModel / (float) numConfigsRandom > factor) || instanceIds.isEmpty()) {
				// create a random config
				ParameterConfiguration paramconfig = null;
				String name = null;
				if (!defaultMutations.isEmpty() && existingParameterConfigurations.size() >= minConfigs) {
					int rand = rng.nextInt(defaultMutations.size());
					paramconfig = defaultMutations.get(rand);
					defaultMutations.remove(rand);
					name = "default mutation";
					pacc.log("[IBS] Using a default mutation");
				} else {
					paramconfig = graph.getRandomConfiguration(rng);
					name = "random config";
					pacc.log("[IBS] Generating a random configuration");
				}
				SolverConfiguration sc = createSolverConfiguration(paramconfig, name, null);
				if (sc != null) {
					res.add(sc);
				}
				numConfigsRandom++;
			} else {
				// create a random config using the model
				String solverConfigName = null;
				DecisionTree tree = null;
				Integer instanceId = null;
				HashSet<Integer> iids = null;
				int rand = rng.nextInt(instanceIds.size());
				instanceId = instanceIds.get(rand);
				instanceIds.remove(rand);

				if (!cachedParameterConfigurations.isEmpty()) {
					boolean found = false;
					for (int i = 0; i < cachedParameterConfigurations.size(); i++) {
						Pair<HashSet<Integer>, Pair<ParameterConfiguration, String>> p = cachedParameterConfigurations.get(i);
						if (p.getFirst().contains(instanceId)) {
							
							for (int iid : p.getFirst()) {
								instanceIds.remove(new Integer(iid));
							}
							pacc.log("[IBS] Using a cached config.");
							SolverConfiguration sc = createSolverConfiguration(p.getSecond().getFirst(), p.getSecond().getSecond(), p.getFirst());
							if (sc != null) {
								res.add(sc);
								numConfigsModel++;
							}
							found = true;
							cachedParameterConfigurations.remove(i);
							break;
						}
					}
					if (found) {
						continue;
					}
				}
				
				if (clustering == null) {
					tree = treeCache.get(instanceId);
				} else {
					iids = new LinkedHashSet<Integer>();
					List<Integer> cluster = null;
					for (List<Integer> c : clustering) {
						if (c.contains(instanceId)) {
							cluster = c;
							break;
						}
					}
					if (cluster == null) {
						cluster = clustering.get(rng.nextInt(clustering.size()));
					}
					List<Integer> tmp = new LinkedList<Integer>();
					for (int iid : cluster) {
						tmp.add(iid);
					}
					/*int numInstances = (int) Math.round(Math.sqrt(iids.size()));
					if (numInstances < 1) {
						numInstances = 1;
					}
					while (iids.size() > numInstances) {
						tmp.remove(rng.nextInt(tmp.size()));
					}*/
					iids.addAll(tmp);
					for (int iid : iids) {
						instanceIds.remove(new Integer(iid));
					}
					pacc.log("[IBS] Using cluster: " + cluster + " (size: " + cluster.size() + ")");
				}
				if (tree == null) {
					List<Pair<ParameterConfiguration, List<ExperimentResult>>> trainData = new LinkedList<Pair<ParameterConfiguration, List<ExperimentResult>>>();
					
					if (clustering == null) {
						HashMap<Integer, List<ExperimentResult>> scs = getScResultMap(instanceId);
						
						for (Entry<Integer, List<ExperimentResult>> entry : scs.entrySet()) {
							trainData.add(new Pair<ParameterConfiguration, List<ExperimentResult>>(solverConfigs.get(entry.getKey()).getParameterConfiguration(), entry.getValue()));
						}
						solverConfigName = "Random from restricted domains (iid: " + instanceId + ")";
						pacc.log("[IBS] Generating a decision tree for iid: " + instanceId + " using " + trainData.size() + " parameter configurations.");
					} else {
						for (Entry<Integer, List<ExperimentResult>> entry : getScResultMap(iids).entrySet()) {
							trainData.add(new Pair<ParameterConfiguration, List<ExperimentResult>>(solverConfigs.get(entry.getKey()).getParameterConfiguration(), entry.getValue()));
						}
						
						solverConfigName = "Random from restricted domains (instances: " + iids.size() + ", " + trainData.size() + " configs involved)";
						pacc.log("[IBS] Generating a decision tree for a cluster with " + iids.size() + " instances using " + trainData.size() + " parameter configurations.");
					}
					try {
						if (regressionTree_maxTrainDataSize != -1) {
							while (trainData.size() > regressionTree_maxTrainDataSize) {
								trainData.remove(rng.nextInt(trainData.size()));
							}
						}
						
						tree = new DecisionTree(rng, parameters.getStatistics().getCostFunction(), -1, 4, trainData, configurableParameters, new LinkedList<String>(), false);
					} catch (Exception ex) {
						// only time out results?
						ex.printStackTrace();
						continue;
					}
					if (clustering == null) {
						treeCache.put(instanceId, tree);
					}
				}
				List<DecisionTree.QueryResult> q = tree.query(-1);
				boolean resultsRemoved = false;
				for (int i = q.size()-1; i >= 0; i--) {
					if (q.get(i).parametersSorted.isEmpty() || q.get(i).configs.isEmpty()) {
						q.remove(i);
						resultsRemoved = true;
					}
				}
				if (q.isEmpty()) {
					ParameterConfiguration paramconfig = null;
					String name = null;
					if (!defaultMutations.isEmpty() && rng.nextFloat() < 0.5f) {
						int rnd = rng.nextInt(defaultMutations.size());
						paramconfig = defaultMutations.get(rnd);
						defaultMutations.remove(rnd);
						name = "default mutation";
						pacc.log("[IBS] Using a default mutation");
					} else {
						paramconfig = graph.getRandomConfiguration(rng);
						name = "random config";
						pacc.log("[IBS] Generating a random configuration");
					}
					if (!resultsRemoved) {
						name = name + " (query result was empty)";
						pacc.log("[IBS] Generated a random configuration (query result was null)");
					} else {
						name = name + " (no possible randomizable parameters)";
						pacc.log("[IBS] Generated a random configuration (no possible randomizable parameters)");
					}
					SolverConfiguration sc = createSolverConfiguration(paramconfig, name, null);
					if (sc != null) {
						res.add(sc);
					}
					numConfigsRandom++;
					continue;
				}
				pacc.log("[IBS] Query: " + q.size() + " results.");
				
				boolean cacheConfig = false;
				int configsCached = 0;
				do {
					if (q.isEmpty()) {
						break;
					}
					List<ParameterConfiguration> possibleBaseConfigs = new LinkedList<ParameterConfiguration>();
					List<DecisionTree.QueryResult> possibleResults = new LinkedList<DecisionTree.QueryResult>();
					// TODO: maximize cost?
					double cost = Double.POSITIVE_INFINITY;
					
					//if (rng.nextDouble() < 0.2) {
					//	System.out.println("[IBS] Using all results.");
					//	possibleResults.addAll(q);
					//} else {
						for (DecisionTree.QueryResult r : q) {
							if (Math.abs(r.cost - cost) < 0.000001) {
								possibleResults.add(r);
							} else if (r.cost < cost) {
								possibleResults.clear();
								possibleResults.add(r);
								cost = r.cost;
							}
						}
					//}
					DecisionTree.QueryResult result = possibleResults.get(rng.nextInt(possibleResults.size()));

					
					possibleBaseConfigs.addAll(result.configs);
					ParameterConfiguration paramconfig = new ParameterConfiguration(possibleBaseConfigs.get(rng.nextInt(possibleBaseConfigs.size())));
					result.configs.remove(paramconfig);
					if (result.configs.isEmpty()) {
						for (int i = 0; i < q.size(); i++) {
							if (q.get(i) == result) {
								q.remove(i);
								break;
							}
						}
					}
					
					pacc.log("[IBS] Cost " + cost + "; Number of split parameters: " + result.parametersSorted.size());

					Set<String> randomizeParameterNames = new HashSet<String>();
					int depth = 0;
					for (Parameter p : result.parametersSorted) {
						depth++;
						randomizeParameterNames.add(p.getName());
						//if (randomizeParameterNames.size() >= result.parametersSorted.size() / 2) {
						//	break;
						//}
						if (depth >= result.parametersSorted.size() / 2) {
							break;
						}
					}

					
					for (Pair<Parameter, Domain> pd : result.parameterDomains) {
						if (randomizeParameterNames.contains(pd.getFirst().getName())) {
							pacc.log("[IBS] Randomizing parameter " + pd.getFirst().getName() + " with domain " + pd.getSecond().toString());
							paramconfig.setParameterValue(pd.getFirst(), pd.getSecond().randomValue(rng));
						}
					}
					if (!cacheConfig) {
						// if (iids != null) {
						// sc = new SolverConfigurationIBS(idSolverConfig,
						// paramconfig, parameters.getStatistics(), iids);
						// } else {
						// sc = new SolverConfiguration(idSolverConfig,
						// paramconfig,
						// parameters.getStatistics());
						// }
						pacc.log("[IBS] Generated a configuration using model of iid: " + instanceId);
						SolverConfiguration sc = createSolverConfiguration(paramconfig, solverConfigName, iids);
						if (sc != null) {
							res.add(sc);
						}
						numConfigsModel++;
					} else {
						configsCached++;
						pacc.log("[IBS] Caching a config.");
						cachedParameterConfigurations.add(new Pair<HashSet<Integer>, Pair<ParameterConfiguration, String>>(iids, new Pair<ParameterConfiguration, String>(paramconfig, solverConfigName)));
					}
					
					cacheConfig = (existingParameterConfigurations.size() > minConfigs) && (cachedParameterConfigurations.size() < numCachedConfigs) && (configsCached < numCachedConfigs / clustering.size());
					
				} while (cacheConfig);
			}
		}
		for (SolverConfiguration sc : res) {
			solverConfigs.put(sc.getIdSolverConfiguration(), sc);
		}
		return res;
	}
	

	@Override
	public List<String> getParameters() {
		LinkedList<String> res = new LinkedList<String>();
		res.add("InstanceBasedSearching_maxCost = " + maxCost);
		res.add("InstanceBasedSearching_IBSConfigsCPUTime" + IBSConfigsCPUTime);
		return res;
	}

	@Override
	public void searchFinished() {
		pacc.removeJobListener(this);
	}

	@Override
	public void jobsFinished(List<ExperimentResult> _results) {
		for (ExperimentResult result : _results) {
			if (result.getResultCode().isCorrect()) {
				if (maxCost != null && maxCost > 0) {
					List<ExperimentResult> tmp = new LinkedList<ExperimentResult>();
					tmp.add(result);
					if (parameters.getStatistics().getCostFunction().calculateCost(tmp) <= maxCost) {
						solvedInstances.add(result.getInstanceId());
					}
				} else {
					solvedInstances.add(result.getInstanceId());
				}
			}
		}
	}

}
