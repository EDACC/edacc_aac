package edacc.configurator.aac.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.ExperimentResult;

public class Matrix extends SearchMethods {
	private List<SolverConfiguration> solverConfigs;
	public HashMap<Integer, List<ExperimentResult>> mapResults;
	
	public Matrix(API api, Random rng, Parameters parameters) throws Exception {
		super(api, rng, parameters);
		solverConfigs = new ArrayList<SolverConfiguration>();
		mapResults = new HashMap<Integer, List<ExperimentResult>>();
		for (Integer scId : api.getSolverConfigurations(parameters.getIdExperiment())) {
			SolverConfiguration sc = new SolverConfiguration(scId, api.getParameterConfiguration(parameters.getIdExperiment(), scId), parameters.getStatistics());
			solverConfigs.add(sc);
			mapResults.put(scId, api.getRuns(parameters.getIdExperiment(), scId));
		}
		
	}

	@Override
	public List<SolverConfiguration> generateNewSC(int num,
			SolverConfiguration currentBestSC) throws Exception {
		List<SolverConfiguration> scs = new ArrayList<SolverConfiguration>();
		for (int i = 0; i < num; i++) {
			if (solverConfigs.isEmpty())
				break;
			int rand = rng.nextInt(solverConfigs.size());
			scs.add(solverConfigs.get(rand));
			solverConfigs.remove(rand);
		}
		return scs;
	}
}
