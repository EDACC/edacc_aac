package edacc.configurator.aac.search;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.SolverConfigurationDAO;

public class Matrix extends SearchMethods {
	private List<SolverConfiguration> solverConfigs;
	
	public Matrix(AAC pacc, API api, Random rng, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
		super(pacc, api, rng, parameters, firstSCs, referenceSCs);
		solverConfigs = new ArrayList<SolverConfiguration>();
		
		for (edacc.model.SolverConfiguration db_sc : SolverConfigurationDAO.getSolverConfigurationByExperimentId(parameters.getIdExperiment())) {
			SolverConfiguration sc = new SolverConfiguration(db_sc.getId(), api.getParameterConfiguration(parameters.getIdExperiment(), db_sc.getId()), parameters.getStatistics(), 0.f);
			solverConfigs.add(sc);
		}
	}
	
	public SolverConfiguration getFirstSC() throws Exception {
		List<SolverConfiguration> scs = generateNewSC(1);
		if (!scs.isEmpty()) {
			return scs.get(0);
		}
		return null;
	}

	@Override
	public List<SolverConfiguration> generateNewSC(int num) throws Exception {
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

	@Override
	public List<String> getParameters() {
		// TODO Auto-generated method stub
		return new LinkedList<String>();
	}

	@Override
	public void searchFinished() {
		// TODO Auto-generated method stub
		
	}
}
