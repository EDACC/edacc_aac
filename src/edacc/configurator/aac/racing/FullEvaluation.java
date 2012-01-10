package edacc.configurator.aac.racing;

import java.sql.SQLException;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.ConfigurationScenarioDAO;

public class FullEvaluation extends RacingMethods {
	private SolverConfiguration bestSC;
	private int num_instances;
	private int incNumber;
	public FullEvaluation(AAC pacc, Random rng, API api, Parameters parameters) throws SQLException {
		super(pacc, rng, api, parameters);
		num_instances = ConfigurationScenarioDAO.getConfigurationScenarioByExperimentId(parameters.getIdExperiment()).getCourse().getInitialLength();
		incNumber = 0;
	}

	@Override
	public int compareTo(SolverConfiguration sc1, SolverConfiguration sc2) {
		return sc1.compareTo(sc2);
	}

	@Override
	public void initFirstSC(SolverConfiguration firstSC) throws Exception {
		bestSC = firstSC;
		if (bestSC.getJobCount() < parameters.getMaxParcoursExpansionFactor() * num_instances) {
			int expansion = parameters.getMaxParcoursExpansionFactor() * num_instances - bestSC.getJobCount();
			for (int i = 0; i < expansion; i++)
			pacc.expandParcoursSC(bestSC, 1);
		}
		pacc.addSolverConfigurationToListNewSC(bestSC);
		bestSC.setIncumbentNumber(incNumber++);
	}

	@Override
	public SolverConfiguration getBestSC() {
		return bestSC;
	}

	@Override
	public void solverConfigurationsFinished(List<SolverConfiguration> scs) throws Exception {
		if (!bestSC.isFinished()) {
			for (SolverConfiguration sc : scs) {
				if (sc == bestSC) {
					bestSC.setFinished(true);
				} else {
					pacc.addSolverConfigurationToListNewSC(sc);
				}
			}
		} else {
			for (SolverConfiguration sc : scs) {
				sc.setFinished(true);
				if (compareTo(sc, bestSC) > 0) {
					bestSC = sc;
					bestSC.setIncumbentNumber(incNumber++);
				}
			}
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
