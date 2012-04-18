package edacc.configurator.aac.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.parameterspace.ParameterConfiguration;

public class MB extends SearchMethods {

	public MB(AAC pacc, API api, Random rng, Parameters parameters, List<SolverConfiguration> firstSCs) {
		super(pacc, api, rng, parameters, firstSCs);
	}

	@Override
	public List<SolverConfiguration> generateNewSC(int num) throws Exception {
		List<SolverConfiguration> bestSCs = pacc.racing.getBestSolverConfigurations(1);
		SolverConfiguration currentBestSC = (bestSCs.size() > 0 ? bestSCs.get(0) : firstSCs.get(0));
		
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

	@Override
	public void listParameters() {
		// TODO Auto-generated method stub
		
	}

}
