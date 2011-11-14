package edacc.configurator.proar.algorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.proar.Parameters;
import edacc.configurator.proar.SolverConfiguration;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;

public class ROAR extends PROARMethods {

	private ParameterGraph graph;

	public ROAR(API api, Random rng, Parameters parameters) throws Exception {
		super(api, rng, parameters);
		graph = api.loadParameterGraphFromDB(parameters.getIdExperiment());
	}

	@Override
	public List<SolverConfiguration> generateNewSC(int num, SolverConfiguration currentBestSC) throws Exception {
		List<SolverConfiguration> res = new ArrayList<SolverConfiguration>();
		for (int i = 0; i < num; i++) {
			ParameterConfiguration paramconfig = graph.getRandomConfiguration(rng);
			int idSolverConfig = api.createSolverConfig(parameters.getIdExperiment(), paramconfig, api.getCanonicalName(parameters.getIdExperiment(), paramconfig));
			res.add(new SolverConfiguration(idSolverConfig, paramconfig, parameters.getStatistics()));
		}
		return res;
	}

}
