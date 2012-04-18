package edacc.configurator.aac.racing;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.ConfigurationScenarioDAO;

public class FullEvaluation extends RacingMethods {
	private int num_instances;
	private int incNumber;

	public FullEvaluation(AAC pacc, Random rng, API api, Parameters parameters, List<SolverConfiguration> firstSCs) throws Exception {
		super(pacc, rng, api, parameters, firstSCs);
		num_instances = ConfigurationScenarioDAO.getConfigurationScenarioByExperimentId(parameters.getIdExperiment()).getCourse().getInitialLength();
		incNumber = 0;
	}

	@Override
	public int compareTo(SolverConfiguration sc1, SolverConfiguration sc2) {
		return sc1.compareTo(sc2);
	}

	@Override
	public List<SolverConfiguration> getBestSolverConfigurations(Integer numSC) {
		List<SolverConfiguration> res = new LinkedList<SolverConfiguration>();
		return res;
	}

	@Override
	public void solverConfigurationsFinished(List<SolverConfiguration> scs) throws Exception {
		for (SolverConfiguration sc : scs) {
			sc.setFinished(true);
		}
	}

	@Override
	public void solverConfigurationsCreated(List<SolverConfiguration> scs) throws Exception {
		for (SolverConfiguration sc : scs) {
			int expansion = parameters.getMaxParcoursExpansionFactor() * num_instances;
			pacc.expandParcoursSC(sc, expansion, Integer.MAX_VALUE - sc.getIdSolverConfiguration());
			pacc.addSolverConfigurationToListNewSC(sc);
		}

	}

	@Override
	public int computeOptimalExpansion(int computationCoreCount, int computationJobCount, int listNewSCSize) {
		return 1;
	}

	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void listParameters() {
		// TODO Auto-generated method stub

	}

}
