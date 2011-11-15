/**
 * 
 */
package edacc.configurator.aac.racing;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import edacc.api.API;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.ConfigurationScenarioDAO;
import edacc.model.ExperimentResult;
import edacc.model.ExperimentResultDAO;

/**
 * @author balint Every SC has to be evaluated on the same parcours in
 *         cronological order.
 */
public class STTRace extends RacingMethods {
	private static final boolean deleteSolverConfigs = true;

	SolverConfiguration bestSC;
	int incumbentNumber;
	int num_instances;

	// Threshold for the test
	private double a;
	// min number of evaluations and max number of evaluations
	private int minE, maxE;

	/**
	 * @param proar
	 * @param api
	 * @param parameters
	 * @throws SQLException
	 */
	public STTRace(AAC proar, API api, Parameters parameters) throws SQLException {
		super(proar, api, parameters);
		this.a = 2;
		this.minE = 5;
		incumbentNumber = 0;
		num_instances = ConfigurationScenarioDAO.getConfigurationScenarioByExperimentId(parameters.getIdExperiment())
				.getCourse().getInitialLength();
		this.maxE = parameters.getMaxParcoursExpansionFactor() * num_instances;
	}

	/*
	 * compares sc1 to sc2 with a sequential t-test. returns -1 if sc1 is not
	 * better than sc2 and the test can be stopped (or has reached maxE) 1 if
	 * sc1 is better than sc2 and the test can be stopped (or has reached maxE)
	 * 0 if they are equal and the maximum number of evaluations has been
	 * reached -2 if further evaluations are necessary and possible
	 */
	@Override
	public int compareTo(SolverConfiguration sc1, SolverConfiguration sc2) {
		// number of jobs that sc1 and sc2 have in common.
		int n = Math.min(sc1.getNumFinishedJobs(), sc2.getNumFinishedJobs());
		float[] y = new float[n]; // times of sc1
		float[] z = new float[n]; // times of sc2
		float[] x = new float[n]; // time difference y-z
		int i = 0;
		double testValue, threshold = 2.D * a /(double) n;
		float mean = 0f, std2 = 0f; // mean and quadratic standard deviation
		HashMap<InstanceIdSeed, ExperimentResult> sc1JobsMap = new HashMap<InstanceIdSeed, ExperimentResult>();
		for (ExperimentResult job : sc1.getFinishedJobs()) {
			sc1JobsMap.put(new InstanceIdSeed(job.getInstanceId(), job.getSeed()), job);
		}
		for (ExperimentResult job : sc2.getFinishedJobs()) {
			InstanceIdSeed tmp = new InstanceIdSeed(job.getInstanceId(), job.getSeed());
			ExperimentResult sc1Job;
			if ((sc1Job = sc1JobsMap.get(tmp)) != null) {
				// sc1JobsList.add(sc1Job);

				y[i] = parameters.getStatistics().getCostFunction().singleCost(sc1Job);
				z[i] = parameters.getStatistics().getCostFunction().singleCost(job);
				x[i] = y[i] - z[i];
				// sc2JobsList.add(job);
				i++;
			}
		}
		// compute the mean;
		for (int j = 0; j < n; j++) {
			mean += x[j];
		}
		mean = mean / (float) n;
		// compute std2
		for (int j = 0; j < n; j++) {
			std2 += (x[j] - mean) * (x[j] - mean);
		}
		std2 = std2 / (float) n;
		// the sequential t-test
		testValue = Math.log(1.0 + mean * mean / std2);
		if ((testValue > threshold) || (n == this.maxE)) {
			// test can stop
			if (mean > 0) // sc2 better
				return -1;
			else if (mean < 0)
				return 1;
			else
				return 0;
		} else
			return -2;
	}

	public void initFirstSC(SolverConfiguration firstSC) throws Exception {
		this.bestSC = firstSC;
		bestSC.setIncumbentNumber(incumbentNumber++);
		proar.log("i " + proar.getWallTime() + " ," + firstSC.getCost() + ", n.A.," + bestSC.getIdSolverConfiguration()
				+ ", n.A.," + bestSC.getParameterConfiguration().toString());

		int expansion = 0;
		if (bestSC.getJobCount() < parameters.getMaxParcoursExpansionFactor() * num_instances) {
			expansion = Math.min(parameters.getMaxParcoursExpansionFactor() * num_instances - bestSC.getJobCount(),
					parameters.getInitialDefaultParcoursLength());
			proar.expandParcoursSC(bestSC, expansion);
		}
		if (expansion > 0) {
			proar.log("c Expanding parcours of best solver config " + bestSC.getIdSolverConfiguration() + " by "
					+ expansion);
		}
		// update the status of the jobs of bestSC and if first level wait
		// also for jobs to finish
		if (expansion > 0) {
			proar.log("c Waiting for currently best solver config " + bestSC.getIdSolverConfiguration() + " to finish "
					+ expansion + "job(s)");
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
	public SolverConfiguration getBestSC() {
		return bestSC;
	}

	/*
	 * goes through the list of finished SC and compares them to the bestSC
	 */
	@Override
	public void solverConfigurationsFinished(List<SolverConfiguration> scs) throws Exception {
		for (SolverConfiguration sc : scs) {
			if (sc == bestSC)
				continue;
			int comp = compareTo(sc, bestSC);
			if (comp >= 0) {// sc won against bestSC
				sc.setFinished(true);
				bestSC = sc;
				sc.setIncumbentNumber(incumbentNumber++);
				proar.log("i " + proar.getWallTime() + "," + sc.getCost() + ",n.A. ," + sc.getIdSolverConfiguration()
						+ ",n.A. ," + sc.getParameterConfiguration().toString());
			} else if (comp == -1) {// lost against best on part of the actual
				// parcours:
				sc.setFinished(true);
				if (deleteSolverConfigs)
					api.removeSolverConfig(sc.getIdSolverConfiguration());
				proar.log("d Solver config " + sc.getIdSolverConfiguration() + " with cost " + sc.getCost()
						+ " lost against best solver config on " + sc.getJobCount() + " runs.");
				if (bestSC.getJobCount() < parameters.getMaxParcoursExpansionFactor() * num_instances) {
					proar.log("c Expanding parcours of best solver config " + bestSC.getIdSolverConfiguration()
							+ " by 1");
					proar.expandParcoursSC(bestSC, 1);
					proar.addSolverConfigurationToListNewSC(bestSC);
				}
			} else { // comp == -2 further jobs are needed to asses performance
				if (sc.getJobCount() == bestSC.getJobCount()) {//add also to best 1 job
					proar.expandParcoursSC(bestSC, 1);
					proar.addSolverConfigurationToListNewSC(bestSC);
				}
				int generated = proar.addRandomJob(1, sc, bestSC, Integer.MAX_VALUE - sc.getNumber());
				proar.log("c Generated " + generated + " jobs for solver config id " + sc.getIdSolverConfiguration());
				proar.addSolverConfigurationToListNewSC(sc);

			}

		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * edacc.configurator.proar.racing.PROARRacing#solverConfigurationsCreated
	 * (java.util.List)
	 */
	@Override
	public void solverConfigurationsCreated(List<SolverConfiguration> scs) throws Exception {
		for (SolverConfiguration sc : scs) {
			// add 1 random job from the best configuration with the
			// priority corresponding to the level
			// lower levels -> higher priorities
			proar.addRandomJob(minE, sc, bestSC, Integer.MAX_VALUE - sc.getNumber());
			proar.addSolverConfigurationToListNewSC(sc);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * edacc.configurator.proar.racing.PROARRacing#computeOptimalExpansion(int,
	 * int, int)
	 */
	@Override
	public int computeOptimalExpansion(int computationCoreCount, int computationJobCount, int listNewSCSize) {
		// TODO Auto-generated method stub
		return 0;
	}

}
