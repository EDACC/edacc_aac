package edacc.configurator.aac.racing;


import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.api.costfunctions.CostFunction;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.configurator.aac.clustering.ClusterHandler;
import edacc.configurator.aac.clustering.Costs;
import edacc.model.ConfigurationScenarioDAO;
import edacc.model.ExperimentResult;

/**
 * @author schulte
 */
public class Roar extends RacingMethods {
	// The incumbent (SC with the best costs)
	SolverConfiguration bestSC;
	// The number of incumbent changes
	int incumbentNumber;
	// Number of different instances the SCs are evaluated on
	int num_instances;
	// Set of SC-IDs which are not evaluated anymore
	HashSet<Integer> stopEvalSolverConfigIds = new HashSet<Integer>();
	// /////////////////////CAPPING///////////////////////////////////
	// Flag to enable/disable the functionality of aggressive capping
	boolean aggressiveCapping = false;
	// Multiplier to tolerate worse SCs for further evaluation. The
	// capping factor decreases with the number of runs a SC gains
	float maxCappingFactor = 2f;
	// ////////////////////CLUSTERING/////////////////////////////////
	// Flag to enable/disable the functionality of instance-clustering
	boolean clustering = true;
	ClusterHandler clusterHandler;

	public Roar(AAC proar, Random rng, API api,
			Parameters parameters, List<SolverConfiguration> firstSCs,
			List<SolverConfiguration> referenceSCs) throws Exception {
		super(proar, rng, api, parameters, firstSCs, referenceSCs);
		incumbentNumber = 0;
		num_instances = ConfigurationScenarioDAO
				.getConfigurationScenarioByExperimentId(
						parameters.getIdExperiment()).getCourse()
				.getInitialLength();
		HashMap<String, String> params = parameters.getRacingMethodParameters();
		if (params.containsKey("Clustering_useClustering")) {
			clustering = Boolean.parseBoolean(params
					.get("Clustering_useClustering"));
		}
		if (params.containsKey("Roar_capping")) {
			aggressiveCapping = Boolean
					.parseBoolean(params.get("Roar_capping"));
		}
		if (params.containsKey("Roar_cappingFactor")) {
			maxCappingFactor = Float.parseFloat(params
					.get("Roar_cappingFactor"));
		}

		if (clustering) {
			clusterHandler = new ClusterHandler(proar, parameters, api, rng,  
					firstSCs, referenceSCs);
			bestSC = clusterHandler.initBestSC();
		} else {
			initBestSC(firstSCs.get(0));
		}
	}

	private void initBestSC(SolverConfiguration sc) throws Exception {
		this.bestSC = firstSCs.get(0);
		bestSC.setIncumbentNumber(incumbentNumber++);
		log(pacc.getWallTime() + " ," + bestSC.getCost() + ", n.A.,"
				+ bestSC.getIdSolverConfiguration() + ", n.A.,"
				+ bestSC.getParameterConfiguration().toString());

		int expansion = 0;
		if (bestSC.getJobCount() < parameters.getMaxParcoursExpansionFactor()
				* num_instances) {
			expansion = Math.min(parameters.getMaxParcoursExpansionFactor()
					* num_instances - bestSC.getJobCount(),
					parameters.getInitialDefaultParcoursLength());
			pacc.expandParcoursSC(bestSC, expansion);
		}
		if (expansion > 0) {
			log("Expanding parcours of best solver config "
					+ bestSC.getIdSolverConfiguration() + " by " + expansion);
		}
		// update the status of the jobs of bestSC and if first level wait
		// also for jobs to finish
		if (expansion > 0) {
			log("Waiting for currently best solver config "
					+ bestSC.getIdSolverConfiguration() + " to finish "
					+ expansion + "job(s)");
			while (true) {
				pacc.updateJobsStatus(bestSC);
				if (bestSC.getNotStartedJobs().isEmpty()
						&& bestSC.getRunningJobs().isEmpty()) {
					break;
				}
				pacc.sleep(1000);
			}
		} else {
			pacc.updateJobsStatus(bestSC);
		}
		pacc.validateIncumbent(bestSC);
	}

	public String toString() {
		return "Roar with aggressive capping.";
	}

	@Override
	public int compareTo(SolverConfiguration sc1, SolverConfiguration sc2) {
		return sc1.compareTo(sc2);
	}

	@Override
	public void solverConfigurationsFinished(List<SolverConfiguration> scs)
			throws Exception {
		if (aggressiveCapping) {
			aggressiveCapping(pacc.returnListNewSC());
		}
		if (clustering) {
			_SCsFinishedUsingClusters(scs);
		} else {
			_SCsFinished(scs);
		}
	}

	@Override
	public void solverConfigurationsCreated(List<SolverConfiguration> scs)
			throws Exception {
		if (scs.isEmpty())
			return;

		if (bestSC == null) {
			initBestSC(scs.get(0));
			scs.remove(0);
		}

		for (SolverConfiguration sc : scs) {
			// add 1 random job from the best configuration with the
			// priority corresponding to the level
			// lower levels -> higher priorities
			pacc.addRandomJob(parameters.getMinRuns(), sc, bestSC,
					Integer.MAX_VALUE - sc.getNumber());
			pacc.addSolverConfigurationToListNewSC(sc);
		}
	}

	@Override
	public int computeOptimalExpansion(int coreCount, int jobs,
			int listNewSCSize) {
		int res = 0;
		if (coreCount < parameters.getMinCPUCount()
				|| coreCount > parameters.getMaxCPUCount()) {
			log("Warning - Current core count is " + coreCount);
		}
		int staticVariance = 30;
		if (parameters.getJobCPUTimeLimit() < 4)
			staticVariance = 10;
		double preferredWorkloadFactor = Math.pow(
				2,
				(staticVariance + parameters.getJobCPUTimeLimit())
						/ parameters.getJobCPUTimeLimit());
		preferredWorkloadFactor = Math.min(10, preferredWorkloadFactor);
		preferredWorkloadFactor = Math.max(2, preferredWorkloadFactor);

		if (jobs < preferredWorkloadFactor * coreCount) {
			res = (int) ((preferredWorkloadFactor * coreCount) - jobs)
					/ parameters.getMinRuns();
		}
		if (listNewSCSize == 0 && res == 0) {
			res = 1;
		}
		return res;
	}

	/**
	 * every time the
	 * <code>solverConfigurationsFinished(List<SolverConfiguration> scs)</code>
	 * -method is called all solver configurations with waiting or running jobs
	 * are analysed. If a solver configuration is with less runs way worse than
	 * the incumbent it is capped.
	 * 
	 * @param listNewSC
	 *            a list of all solver configurations with running or waiting
	 *            jobs
	 */
	private void aggressiveCapping(
			HashMap<Integer, SolverConfiguration> listNewSC) {
		if ((parameters.getStatistics().getCostFunction() instanceof edacc.api.costfunctions.PARX)
				|| (parameters.getStatistics().getCostFunction() instanceof edacc.api.costfunctions.Average)) {
			Collection<SolverConfiguration> scs = listNewSC.values();
			Iterator<SolverConfiguration> scsIter = scs.iterator();
			while (scsIter.hasNext()) {
				SolverConfiguration sc = scsIter.next();
				if (sc.getFinishedJobs().size() < parameters.getMinRuns())
					continue;
				Costs costs;
				if (clustering) {
					costs = clusterHandler.costs(bestSC, sc, parameters
							.getStatistics().getCostFunction());
				} else {
					costs = getCostsOfTheSolverConfigs(bestSC, sc);
				}
				double cappingFactor = Math.pow((double) maxCappingFactor,
						(1.d - ((double) sc.getJobCount() / (double) bestSC
								.getJobCount())));
				if (cappingFactor < 1)
					cappingFactor = 1.d;
				if (costs.getCostSc2() > cappingFactor * costs.getCostSc1()) {
					log("SC " + sc.getIdSolverConfiguration() + " with cost  "
							+ costs.getCostSc2() + " (> " + cappingFactor + "*"
							+ costs.getCostSc1() + ") is capped on "
							+ costs.getRunsInCommon() + " shared runs!");
					if (clustering)
						clusterHandler.addDataForClustering(sc);
					List<ExperimentResult> jobsToKill = sc.getJobs();
					sc.setFinished(true);
					for (ExperimentResult j : jobsToKill) {
						try {
							api.killJob(j.getId());
						} catch (Exception e) {
							log("Warning - Job " + j.getId()
									+ " could not be killed!");
						}
					}
					try {
						api.removeSolverConfig(sc.getIdSolverConfiguration());
					} catch (Exception e) {
						log("Warning - SolverConfiguration "
								+ sc.getIdSolverConfiguration()
								+ " could not be removed!");
					}
					scsIter.remove();
				}
			}
		}
	}

	/**
	 * The racing-method ROAR has the option to use instance-sampling. If this
	 * option is enabled (Parameter: ROAR_clustering) instances are classified
	 * in clusters. Instead of comparing specific instances whole clusters are
	 * now compared.
	 * 
	 * @param scs
	 *            : list of finished solver configurations
	 * @throws Exception
	 */
	private void _SCsFinishedUsingClusters(List<SolverConfiguration> scs)
			throws Exception {
		// defines if the incumbent is added to the active SCs (has runs left)
		boolean runBest = false;
		for (SolverConfiguration sc : scs) {
			if (sc == bestSC
					|| stopEvalSolverConfigIds.contains(sc
							.getIdSolverConfiguration())) {
				continue;
			}
			// defines if the competitor SC and the incumbent have the same
			// amount of jobs in the same clusters
			numCompCalls++;
			boolean equalRuns = true;
			pacc.updateJobsStatus(bestSC);
			int[] competitor = clusterHandler.countRunPerCluster(sc);
			int[] best = clusterHandler.countRunPerCluster(bestSC);
			for (int i = 0; i < best.length; i++) {
				if (competitor[i] < best[i])
					equalRuns = false;
			}
			if (equalRuns) {
				int comp = compareTo(sc, bestSC);
				log("The competitor " + sc.getIdSolverConfiguration()
						+ " has as many runs as the incumbent "
						+ bestSC.getIdSolverConfiguration());
				if (comp > 0) {
					bestSC.setFinished(true);
					clusterHandler.addDataForClustering(bestSC);
					pacc.updateSolverConfigName(sc, true);
					bestSC = sc;
					pacc.validateIncumbent(bestSC);
					sc.setIncumbentNumber(incumbentNumber++);
					log(pacc.getWallTime() + "," + sc.getCost() + ",n.A. ,"
							+ sc.getIdSolverConfiguration() + ",n.A. ,"
							+ sc.getParameterConfiguration().toString());
				} else if (sc.getJobCount() == parameters
						.getMaxParcoursExpansionFactor() * num_instances) {
					sc.setFinished(true);
					clusterHandler.addDataForClustering(sc);
				} else {
					sc.setFinished(true);
				}
			} else {
				int runsToAdd = sc.getJobCount();
				int runsToAddInc = 0;
				if (runsToAdd * 2 > parameters.getMaxParcoursExpansionFactor()
						* num_instances) {
					runsToAdd = parameters.getMaxParcoursExpansionFactor()
							* num_instances - sc.getJobCount();
				}
				if (sc.getJobCount() + runsToAdd > bestSC.getJobCount()) {
					runsToAddInc = sc.getJobCount() + runsToAdd
							- bestSC.getJobCount();
				}
				if (runsToAdd > 0) {
					log("The competitor " + sc.getIdSolverConfiguration()
							+ " gains another " + runsToAdd + " jobs");
					if (runsToAddInc > 0) {
						log("The incumbent "
								+ bestSC.getIdSolverConfiguration() + " gains "
								+ runsToAddInc
								+ " jobs to keep up with the competitor");
					}
				} else if (sc.getJobCount() >= parameters
						.getMaxParcoursExpansionFactor() * num_instances) {
					log("Error - The SC "
							+ sc.getIdSolverConfiguration()
							+ " has already "
							+ sc.getJobCount()
							+ " jobs but still isnt compared to the incumbent with "
							+ bestSC.getIdSolverConfiguration() + " jobs");
				}
				while (runsToAddInc > 0) {
					int rand = rng.nextInt(best.length);
					InstanceIdSeed newRun = clusterHandler
							.getInstanceInCluster(rand, bestSC);
					if (newRun != null) {
						pacc.addJob(bestSC, newRun.seed, newRun.instanceId,
								Integer.MAX_VALUE - bestSC.getNumber());
						runsToAddInc--;
						best[rand]++;
					}
				}
				while (runsToAdd > 0) {
					int rand = rng.nextInt(competitor.length);
					if (competitor[rand] < best[rand]) {
						InstanceIdSeed newRun = clusterHandler
								.getInstanceInCluster(rand, sc);
						if (newRun != null) {
							pacc.addJob(sc, newRun.seed, newRun.instanceId,
									Integer.MAX_VALUE - sc.getNumber());
							runsToAdd--;
							competitor[rand]++;
						}
					}
				}
				pacc.addSolverConfigurationToListNewSC(sc);
			}
		}
		if (runBest) {
			pacc.addSolverConfigurationToListNewSC(bestSC);
		}
	}

	/**
	 * Checks if a competitor-sc should gain more runs and compares promising
	 * competitor-scs to the incumbent.
	 * 
	 * @param scs
	 *            : list of finished solver configurations
	 * @throws Exception
	 */
	private void _SCsFinished(List<SolverConfiguration> scs) throws Exception {
		for (SolverConfiguration sc : scs) {
			if (sc == bestSC)
				continue;
			numCompCalls++;
			int comp = compareTo(sc, bestSC);
			if (!stopEvalSolverConfigIds
					.contains(sc.getIdSolverConfiguration()) && comp >= 0) {
				if (sc.getJobCount() == bestSC.getJobCount()) {
					bestSC.setFinished(true);
					// all jobs from bestSC computed and won against
					// best:
					if (comp > 0) {
						bestSC = sc;
						pacc.validateIncumbent(bestSC);
						sc.setIncumbentNumber(incumbentNumber++);
						log(pacc.getWallTime() + "," + sc.getCost() + ",n.A. ,"
								+ sc.getIdSolverConfiguration() + ",n.A. ,"
								+ sc.getParameterConfiguration().toString());
					}
				} else {
					pacc.addRandomJob(sc.getJobCount(), sc, bestSC,
							Integer.MAX_VALUE - sc.getNumber());
					pacc.addSolverConfigurationToListNewSC(sc);
				}
			} else {// lost against best on part of the actual (or should
					// not be evaluated anymore)
					// parcours:
				stopEvalSolverConfigIds.remove(sc.getIdSolverConfiguration());

				sc.setFinished(true);
				if (parameters.isDeleteSolverConfigs())
					api.removeSolverConfig(sc.getIdSolverConfiguration());
				log("Solver config " + sc.getIdSolverConfiguration()
						+ " with cost " + sc.getCost()
						+ " lost against best solver config on "
						+ sc.getJobCount() + " runs.");
				if (bestSC.getJobCount() < parameters
						.getMaxParcoursExpansionFactor() * num_instances) {
					log("Expanding parcours of best solver config "
							+ bestSC.getIdSolverConfiguration() + " by 1");
					pacc.expandParcoursSC(bestSC, 1);
					pacc.addSolverConfigurationToListNewSC(bestSC);
				}
			}
		}
	}

	/**
	 * calculates the costs of the incumbent and a competitor on the same
	 * experiment results
	 * 
	 * @param best
	 *            the incumbent ( bestSC )
	 * @param other
	 *            solver configuration which is raced against the incumbent
	 * @return
	 */
	private Costs getCostsOfTheSolverConfigs(SolverConfiguration best,
			SolverConfiguration other) {
		HashMap<InstanceIdSeed, ExperimentResult> bestJobs = new HashMap<InstanceIdSeed, ExperimentResult>();
		for (ExperimentResult job : best.getFinishedJobs()) {
			bestJobs.put(
					new InstanceIdSeed(job.getInstanceId(), job.getSeed()), job);
		}
		List<ExperimentResult> bestJobsInCommon = new LinkedList<ExperimentResult>();
		List<ExperimentResult> otherJobsInCommon = new LinkedList<ExperimentResult>();
		for (ExperimentResult job : other.getFinishedJobs()) {
			InstanceIdSeed tmp = new InstanceIdSeed(job.getInstanceId(),
					job.getSeed());
			ExperimentResult bestJob;
			if ((bestJob = bestJobs.get(tmp)) != null) {
				bestJobsInCommon.add(bestJob);
				otherJobsInCommon.add(job);
			}
		}
		CostFunction costFunc = parameters.getStatistics().getCostFunction();
		double costBest = costFunc.calculateCost(bestJobsInCommon);
		double costOther = costFunc.calculateCost(otherJobsInCommon);
		return new Costs(costBest, costOther, otherJobsInCommon.size());
	}

	@Override
	public List<String> getParameters() {
		List<String> p = new LinkedList<String>();
		return p;
	}

	@Override
	public List<SolverConfiguration> getBestSolverConfigurations() {
		List<SolverConfiguration> res = new LinkedList<SolverConfiguration>();
		if (bestSC != null) {
			res.add(bestSC);
		}
		return res;
	}

	@Override
	public void stopEvaluation(List<SolverConfiguration> scs) throws Exception {
		for (SolverConfiguration sc : scs) {
			stopEvalSolverConfigIds.add(sc.getIdSolverConfiguration());
		}
	}

	@Override
	public void raceFinished() {
		// debug
		if (clustering)
			clusterHandler.visualiseClustering();
		try {
			pacc.updateSolverConfigName(bestSC, true);
		} catch (Exception e) {
			log("Error - Incumbent name could not be changed!");
			e.printStackTrace();
		}
	}

	public void log(String msg) {
		pacc.log(" ROAR: " + msg);
	}
}
