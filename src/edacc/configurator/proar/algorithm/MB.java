package edacc.configurator.proar.algorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.proar.Parameters;
import edacc.configurator.proar.SolverConfiguration;
import edacc.parameterspace.ParameterConfiguration;

public class MB extends PROARMethods {

	public MB(API api, Random rng, Parameters parameters) {
		super(api, rng, parameters);
	}

	@Override
	public List<SolverConfiguration> generateNewSC(int num, SolverConfiguration currentBestSC) throws Exception {
		List<SolverConfiguration> res = new ArrayList<SolverConfiguration>();
		ParameterConfiguration bestSCP = currentBestSC.getParameterConfiguration();
		for (int i = 0; i < num; i++) {
			//ParameterConfiguration paramconfig = api.loadParameterGraphFromDB(idExperiment).getRandomConfiguration(rng);
			ParameterConfiguration paramconfig = new ParameterConfiguration(bestSCP);
			api.loadParameterGraphFromDB(parameters.getIdExperiment()).mutateParameterConfiguration(rng, paramconfig, rng.nextFloat(), 0.8f);
			int idSolverConfig = api.createSolverConfig(parameters.getIdExperiment(), paramconfig, api.getCanonicalName(parameters.getIdExperiment(), paramconfig));
			res.add(new SolverConfiguration(idSolverConfig, api.getParameterConfiguration(parameters.getIdExperiment(), idSolverConfig), parameters.getStatistics()));
		}
		return res;
	}

}
