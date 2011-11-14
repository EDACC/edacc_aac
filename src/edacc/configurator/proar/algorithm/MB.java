package edacc.configurator.proar.algorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.proar.SolverConfiguration;
import edacc.configurator.proar.StatisticFunction;
import edacc.parameterspace.ParameterConfiguration;

public class MB extends PROARMethods {

	public MB(API api, int idExperiment, StatisticFunction statistics, Random rng, Map<String, String> params) {
		super(api, idExperiment, statistics, rng, params);
	}

	@Override
	public List<SolverConfiguration> generateNewSC(int num, SolverConfiguration currentBestSC) throws Exception {
		List<SolverConfiguration> res = new ArrayList<SolverConfiguration>();
		ParameterConfiguration bestSCP = currentBestSC.getParameterConfiguration();
		for (int i = 0; i < num; i++) {
			//ParameterConfiguration paramconfig = api.loadParameterGraphFromDB(idExperiment).getRandomConfiguration(rng);
			ParameterConfiguration paramconfig = new ParameterConfiguration(bestSCP);
			api.loadParameterGraphFromDB(idExperiment).mutateParameterConfiguration(rng, paramconfig, rng.nextFloat(), 0.8f);
			int idSolverConfig = api.createSolverConfig(idExperiment, paramconfig, api.getCanonicalName(idExperiment, paramconfig));
			res.add(new SolverConfiguration(idSolverConfig, api.getParameterConfiguration(idExperiment, idSolverConfig), statistics));
		}
		return res;
	}

}
