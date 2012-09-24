package edacc.configurator.aac.search;

import java.util.HashMap;
import java.util.HashSet;
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

	double stddev = 2.5;
	
	
	HashMap<Integer, SolverConfiguration> solverConfigs;
	Set<Integer> solvedInstances;
	List<Parameter> configurableParameters;
	ParameterGraph graph;
	List<Integer> instanceIds = new LinkedList<Integer>(); 
	
	public InstanceBasedSearching(AAC pacc, API api, Random rng, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
		super(pacc, api, rng, parameters, firstSCs, referenceSCs);
		
		String val;
		if ((val = parameters.getSearchMethodParameters().get("InstanceBasedSearching_stddev")) != null) {
			stddev = Double.parseDouble(val);
		}
		
		solvedInstances = new HashSet<Integer>();
		graph = api.loadParameterGraphFromDB(parameters.getIdExperiment());
		pacc.addJobListener(this);
		configurableParameters = api.getConfigurableParameters(parameters.getIdExperiment());
		solverConfigs = new HashMap<Integer, SolverConfiguration>();
		
		for (SolverConfiguration sc : firstSCs) {
			solverConfigs.put(sc.getIdSolverConfiguration(), sc);
		}
		
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
	
	@Override
	public List<SolverConfiguration> generateNewSC(int num) throws Exception {
		pacc.log("[IBS] Generating " + num + " solver configurations");
		List<SolverConfiguration> res = new LinkedList<SolverConfiguration>();
		HashMap<Integer, DecisionTree> treeCache = new HashMap<Integer, DecisionTree>();
		
		if (instanceIds.isEmpty()) {
			instanceIds.addAll(solvedInstances);
		}
		
		List<List<Integer>> clustering = null;
		if (pacc.racing instanceof ClusterRacing) {
			HashMap<Integer, List<Integer>> tmp = ((ClusterRacing) pacc.racing).getClustering();
			if (!tmp.isEmpty()) {
				clustering = new LinkedList<List<Integer>>();
				clustering.addAll(tmp.values());
			}
		}
		
		while (num > 0) {
			num--;
			if (clustering == null && instanceIds.isEmpty()) {
				// create a random config
				
				ParameterConfiguration paramconfig = graph.getRandomConfiguration(rng);
				int idSolverConfig = api.createSolverConfig(parameters.getIdExperiment(), paramconfig, "random config");
				SolverConfiguration sc = new SolverConfiguration(idSolverConfig, paramconfig, parameters.getStatistics());
				sc.setNameSearch(sc.getIdSolverConfiguration() + " random config");
				pacc.log("[IBS] Generated a random configuration");
				res.add(sc);
			} else {
				// create a random config using the model
				String solverConfigName = null;
				DecisionTree tree = null;
				Integer instanceId = null;
				HashSet<Integer> iids = null;
				int rand = rng.nextInt(instanceIds.size());
				instanceId = instanceIds.get(rand);
				instanceIds.remove(rand);
				if (clustering == null) {
					tree = treeCache.get(instanceId);
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
						iids = new HashSet<Integer>();
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
						
						for (int iid : cluster) {
							iids.add(iid);
							instanceIds.remove(new Integer(iid));
						}
						pacc.log("[IBS] Using cluster: " + cluster);
						

						
						for (Entry<Integer, List<ExperimentResult>> entry : getScResultMap(iids).entrySet()) {
							trainData.add(new Pair<ParameterConfiguration, List<ExperimentResult>>(solverConfigs.get(entry.getKey()).getParameterConfiguration(), entry.getValue()));
						}
						
						solverConfigName = "Random from restricted domains (cluster size: " + cluster.size() + ", " + trainData.size() + " configs involved)";
						pacc.log("[IBS] Generating a decision tree for a cluster with size: " + cluster.size() + " using " + trainData.size() + " parameter configurations.");
					}
					try {
						tree = new DecisionTree(rng, parameters.getStatistics().getCostFunction(), stddev, 4, trainData, configurableParameters, new LinkedList<String>(), false);
					} catch (Exception ex) {
						// only time out results?
						continue;
					}
					if (clustering == null) {
						treeCache.put(instanceId, tree);
					}
				}
				DecisionTree.QueryResult q = tree.query(stddev);
				if (q == null || q.configs.isEmpty()) {
					
					ParameterConfiguration paramconfig = graph.getRandomConfiguration(rng);
					int idSolverConfig = api.createSolverConfig(parameters.getIdExperiment(), paramconfig, "random config");
					SolverConfiguration sc = new SolverConfiguration(idSolverConfig, paramconfig, parameters.getStatistics());
					sc.setNameSearch(sc.getIdSolverConfiguration() + " random config (query result was null)");
					pacc.log("[IBS] Generated a random configuration (query result was null)");
					res.add(sc);
					
					continue;
				}
				List<ParameterConfiguration> possibleBaseConfigs = new LinkedList<ParameterConfiguration>();
				possibleBaseConfigs.addAll(q.configs);
				
				ParameterConfiguration paramconfig = new ParameterConfiguration(possibleBaseConfigs.get(rng.nextInt(possibleBaseConfigs.size())));
				for (Pair<Parameter, Domain> pd : q.parameterDomains) {
					paramconfig.setParameterValue(pd.getFirst(), pd.getSecond().randomValue(rng));
				}
				int idSolverConfig = api.createSolverConfig(parameters.getIdExperiment(), paramconfig, "Random from restricted domains");
				SolverConfiguration sc = null;
				if (iids != null) {
					sc = new SolverConfigurationIBS(idSolverConfig, paramconfig, parameters.getStatistics(), iids);
				} else {
					sc = new SolverConfiguration(idSolverConfig, paramconfig, parameters.getStatistics());
				}
				sc.setNameSearch(sc.getIdSolverConfiguration() + " " + solverConfigName);
				pacc.log("[IBS] Generated a configuration using model of iid: " + instanceId);
				res.add(sc);
			}
			
			if (instanceIds.isEmpty()) {
				instanceIds.addAll(solvedInstances);
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
		res.add("InstanceBasedSearching_stddev = " + stddev);
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
				solvedInstances.add(result.getInstanceId());
			}
		}
	}

}
