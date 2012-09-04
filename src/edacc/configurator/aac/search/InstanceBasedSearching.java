package edacc.configurator.aac.search;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.JobListener;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.configurator.aac.search.ibsutils.DecisionTree;
import edacc.model.ExperimentResult;
import edacc.parameterspace.Parameter;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.domain.Domain;
import edacc.parameterspace.graph.ParameterGraph;
import edacc.util.Pair;

public class InstanceBasedSearching extends SearchMethods implements JobListener {

	double alpha = 2.5;
	
	
	HashMap<Integer, SolverConfiguration> solverConfigs;
	HashMap<Integer, HashMap<Integer, List<ExperimentResult>>> instanceSCMap;
	List<Parameter> configurableParameters;
	ParameterGraph graph;
	public InstanceBasedSearching(AAC pacc, API api, Random rng, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
		super(pacc, api, rng, parameters, firstSCs, referenceSCs);
		instanceSCMap = new HashMap<Integer, HashMap<Integer, List<ExperimentResult>>>();
		graph = api.loadParameterGraphFromDB(parameters.getIdExperiment());
		pacc.addJobListener(this);
		configurableParameters = api.getConfigurableParameters(parameters.getIdExperiment());
		solverConfigs = new HashMap<Integer, SolverConfiguration>();
	}

	@Override
	public List<SolverConfiguration> generateNewSC(int num) throws Exception {
		pacc.log("[IBS] Generating " + num + " solver configurations");
		List<SolverConfiguration> res = new LinkedList<SolverConfiguration>();
		HashMap<Integer, DecisionTree> treeCache = new HashMap<Integer, DecisionTree>();
		List<Integer> instanceIds = new LinkedList<Integer>(); 
		instanceIds.addAll(instanceSCMap.keySet());
		while (num > 0) {
			num--;
			if (rng.nextDouble() < 0.2 || instanceIds.isEmpty()) {
				// create a random config
				
				ParameterConfiguration paramconfig = graph.getRandomConfiguration(rng);
				int idSolverConfig = api.createSolverConfig(parameters.getIdExperiment(), paramconfig, "random config");
				SolverConfiguration sc = new SolverConfiguration(idSolverConfig, paramconfig, parameters.getStatistics());
				sc.setNameSearch("random config");
				pacc.log("[IBS] Generated a random configuration");
				res.add(sc);
			} else {
				// create a random config using the model
				
				int rand = rng.nextInt(instanceIds.size());
				int instanceId = instanceIds.get(rand);
				instanceIds.remove(rand);
				DecisionTree tree = treeCache.get(instanceId);
				if (tree == null) {
					List<Pair<ParameterConfiguration, List<ExperimentResult>>> trainData = new LinkedList<Pair<ParameterConfiguration, List<ExperimentResult>>>();
					HashMap<Integer, List<ExperimentResult>> scs = instanceSCMap.get(instanceId);
					for (Entry<Integer, List<ExperimentResult>> entry:  scs.entrySet()) {
						trainData.add(new Pair<ParameterConfiguration, List<ExperimentResult>>(solverConfigs.get(entry.getKey()).getParameterConfiguration(), entry.getValue()));
					}
					pacc.log("[IBS] Generating a decision tree for iid: " + instanceId + " using " + trainData.size() + " parameter configurations.");
					try {
						tree = new DecisionTree(rng, parameters.getStatistics().getCostFunction(), alpha, 4, trainData, configurableParameters, new LinkedList<String>(), false);
					} catch (Exception ex) {
						// only time out results?
						continue;
					}
					treeCache.put(instanceId, tree);
				}
				DecisionTree.QueryResult q = tree.query(alpha);
				if (q == null) {
					continue;
				}
				ParameterConfiguration paramconfig = graph.getRandomConfiguration(rng);
				for (Pair<Parameter, Domain> pd : q.parameterDomains) {
					paramconfig.setParameterValue(pd.getFirst(), pd.getSecond().randomValue(rng));
				}
				int idSolverConfig = api.createSolverConfig(parameters.getIdExperiment(), paramconfig, "Random from resticted domains (iid: " + instanceId + ")");
				SolverConfiguration sc = new SolverConfiguration(idSolverConfig, paramconfig, parameters.getStatistics());
				sc.setNameSearch("Random from resticted domains (iid: " + instanceId + ")");
				pacc.log("[IBS] Generated a configuration using model of iid: " + instanceId);
				res.add(sc);
			}
			
			if (instanceIds.isEmpty()) {
				instanceIds.addAll(instanceSCMap.keySet());
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
		return res;
	}

	@Override
	public void searchFinished() {
		pacc.removeJobListener(this);
	}

	@Override
	public void jobFinished(ExperimentResult result) {
		HashMap<Integer, List<ExperimentResult>> resultMap = instanceSCMap.get(result.getInstanceId());
		if (resultMap == null) {
			resultMap = new HashMap<Integer, List<ExperimentResult>>();
			instanceSCMap.put(result.getInstanceId(), resultMap);
		}
		List<ExperimentResult> results = resultMap.get(result.getSolverConfigId());
		if (results == null) {
			results = new LinkedList<ExperimentResult>();
			resultMap.put(result.getSolverConfigId(), results);
		}
		results.add(result);
	}

}
