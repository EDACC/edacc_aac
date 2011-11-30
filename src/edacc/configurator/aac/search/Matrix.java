package edacc.configurator.aac.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.SolverConfigurationDAO;

public class Matrix extends SearchMethods {
	private List<SolverConfiguration> solverConfigs;
	
	public Matrix(API api, Random rng, Parameters parameters) throws Exception {
		super(api, rng, parameters);
		solverConfigs = new ArrayList<SolverConfiguration>();
		
		for (edacc.model.SolverConfiguration db_sc : SolverConfigurationDAO.getSolverConfigurationByExperimentId(parameters.getIdExperiment())) {
			SolverConfiguration sc = new SolverConfiguration(db_sc.getId(), api.getParameterConfiguration(parameters.getIdExperiment(), db_sc.getId()), parameters.getStatistics(), 0.f);
			solverConfigs.add(sc);
		}
	}
	
	public SolverConfiguration getFirstSC() throws Exception {
		List<SolverConfiguration> scs = generateNewSC(1, null);
		if (!scs.isEmpty()) {
			return scs.get(0);
		}
		return null;
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
