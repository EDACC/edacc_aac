package edacc.configurator.proar.algorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.proar.SolverConfiguration;
import edacc.configurator.proar.StatisticFunction;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;

public class ROAR extends PROARMethods {

	private ParameterGraph graph;

	public ROAR(API api, int idExperiment, StatisticFunction statistics, Random rng, Map<String, String> params) throws Exception {
		super(api, idExperiment, statistics, rng, params);
		graph = api.loadParameterGraphFromDB(idExperiment);
	}

	@Override
	public List<SolverConfiguration> generateNewSC(int num, SolverConfiguration currentBestSC) throws Exception {
		List<SolverConfiguration> res = new ArrayList<SolverConfiguration>();
		for (int i = 0; i < num; i++) {
			ParameterConfiguration paramconfig = graph.getRandomConfiguration(rng);
			int idSolverConfig = api.createSolverConfig(idExperiment, paramconfig, api.getCanonicalName(idExperiment, paramconfig));
			res.add(new SolverConfiguration(idSolverConfig, paramconfig, statistics));
		}
		return res;
	}

}
