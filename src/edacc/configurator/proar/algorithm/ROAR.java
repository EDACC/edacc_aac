package edacc.configurator.proar.algorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.proar.SolverConfiguration;
import edacc.configurator.proar.StatisticFunction;
import edacc.parameterspace.ParameterConfiguration;

public class ROAR extends PROARMethods {

	public ROAR(API api, int idExperiment, StatisticFunction statistics, Random rng, Map<String, String> params) {
		super(api, idExperiment, statistics, rng, params);
	}

	@Override
	public List<SolverConfiguration> generateNewSC(int num, List<SolverConfiguration> lastBestSCs, SolverConfiguration currentBestSC, int level, int currentLevel) throws Exception {
		List<SolverConfiguration> res = new ArrayList<SolverConfiguration>();
		for (int i = 0; i < num; i++) {
			ParameterConfiguration paramconfig = api.loadParameterGraphFromDB(idExperiment).getRandomConfiguration(rng);
			int idSolverConfig = api.createSolverConfig(idExperiment, paramconfig, api.getCanonicalName(idExperiment, paramconfig) + " level " + level);
			res.add(new SolverConfiguration(idSolverConfig, api.getParameterConfiguration(idExperiment, idSolverConfig), statistics, level));
		}
		return res;
	}

}
