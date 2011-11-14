package edacc.configurator.proar.racing;

import java.sql.SQLException;
import java.util.List;

import edacc.api.API;
import edacc.configurator.proar.PROAR;
import edacc.configurator.proar.Parameters;
import edacc.configurator.proar.SolverConfiguration;
import edacc.model.ConfigurationScenarioDAO;

public class Default extends PROARRacing {
	private static final boolean deleteSolverConfigs = true;

	SolverConfiguration bestSC;
	int incumbentNumber;
	int num_instances;

	public Default(PROAR proar, API api, Parameters parameters) throws SQLException {
		super(proar, api, parameters);
		incumbentNumber = 0;
		num_instances = ConfigurationScenarioDAO.getConfigurationScenarioByExperimentId(parameters.getIdExperiment()).getCourse().getInitialLength();
	}

	@Override
	public int compareTo(SolverConfiguration sc1, SolverConfiguration sc2) {
		return sc1.compareTo(sc2);
	}

	@Override
	public SolverConfiguration getBestSC() {
		return bestSC;
	}

	@Override
	public void solverConfigurationsFinished(List<SolverConfiguration> scs) throws Exception {
		for (SolverConfiguration sc : scs) {
			if (sc == bestSC) 
				continue;
			int comp = compareTo(bestSC, sc);
			if (comp >= 0) {
				if (sc.getJobCount() == bestSC.getJobCount()) {
					sc.setFinished(true);
					// all jobs from bestSC computed and won against
					// best:
					if (comp > 0) {
						proar.updateSolverConfigName(bestSC, false);
						bestSC = sc;
						sc.setIncumbentNumber(incumbentNumber++);
						proar.updateSolverConfigName(bestSC, true);
						proar.log("i " + proar.getWallTime() + "," + sc.getCost() + ",n.A. ," + sc.getIdSolverConfiguration() + ",n.A. ," + sc.getParameterConfiguration().toString());

					}
					// api.updateSolverConfigurationCost(sc.getIdSolverConfiguration(),
					// sc.getCost(),
					// statistics.getCostFunction());
					// listNewSC.remove(i);
				} else {
					int generated = proar.addRandomJob(sc.getJobCount(), sc, bestSC, Integer.MAX_VALUE - sc.getNumber());
					proar.log("c Generated " + generated + " jobs for solver config id " + sc.getIdSolverConfiguration());
					proar.addSolverConfigurationToListNewSC(sc);
				}
			} else {// lost against best on part of the actual
					// parcours:
				sc.setFinished(true);
				if (deleteSolverConfigs)
					api.removeSolverConfig(sc.getIdSolverConfiguration());
				proar.log("d Solver config " + sc.getIdSolverConfiguration() + " with cost " + sc.getCost() + " lost against best solver config on " + sc.getJobCount() + " runs.");
				if (bestSC.getJobCount() < parameters.getMaxParcoursExpansionFactor() * num_instances) {
					proar.log("c Expanding parcours of best solver config " + bestSC.getIdSolverConfiguration() + " by 1");
					proar.expandParcoursSC(bestSC, 1);
				}
			}
		}
	}

	@Override
	public void initFirstSC(SolverConfiguration firstSC) throws Exception {
		this.bestSC = firstSC;
		bestSC.setIncumbentNumber(incumbentNumber++);
		proar.log("i " + proar.getWallTime() + " ," + firstSC.getCost() + ", n.A.," + bestSC.getIdSolverConfiguration() + ", n.A.," + bestSC.getParameterConfiguration().toString());
		
		int expansion = 0;
		if (bestSC.getJobCount() < parameters.getMaxParcoursExpansionFactor() * num_instances) {
			expansion = Math.min(parameters.getMaxParcoursExpansionFactor() * num_instances - bestSC.getJobCount(), parameters.getInitialDefaultParcoursLength());
			proar.expandParcoursSC(bestSC, expansion);
		}
		if (expansion > 0) {
			proar.log("c Expanding parcours of best solver config " + bestSC.getIdSolverConfiguration() + " by " + expansion);
		}
		// update the status of the jobs of bestSC and if first level wait
		// also for jobs to finish
		if (expansion > 0) {
			proar.log("c Waiting for currently best solver config " + bestSC.getIdSolverConfiguration() + " to finish " + expansion + "job(s)");
			while (true) {
				proar.updateJobsStatus(bestSC);
				if (bestSC.getNotStartedJobs().isEmpty() && bestSC.getRunningJobs().isEmpty()) {
					break;
				}
				Thread.sleep(1000);
			}
		} else {
			proar.updateJobsStatus(bestSC);
		}
	}

	@Override
	public void solverConfigurationsCreated(List<SolverConfiguration> scs) throws Exception {
		
		for (SolverConfiguration sc : scs) {
			// add 1 random job from the best configuration with the
			// priority corresponding to the level
			// lower levels -> higher priorities
			
			proar.addRandomJob(parameters.getMinRuns(), sc, bestSC, Integer.MAX_VALUE - sc.getNumber());

		}
	}

}
