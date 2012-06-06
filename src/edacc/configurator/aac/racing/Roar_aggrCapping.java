package edacc.configurator.aac.racing;

import java.awt.Point;
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
import edacc.model.ConfigurationScenarioDAO;
import edacc.model.ExperimentResult;

public class Roar_aggrCapping extends RacingMethods {
	SolverConfiguration bestSC;
	int incumbentNumber;
	int num_instances;
	HashSet<Integer> stopEvalSolverConfigIds = new HashSet<Integer>();
	
	float maxCappingFactor = 2f;
	
	public Roar_aggrCapping(AAC proar, Random rng, API api, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
		super(proar, rng, api, parameters, firstSCs, referenceSCs);
		incumbentNumber = 0;
		num_instances = ConfigurationScenarioDAO.getConfigurationScenarioByExperimentId(parameters.getIdExperiment()).getCourse().getInitialLength();
		this.api = api;
		
		if (!firstSCs.isEmpty()) {
			// TODO: ...
			initBestSC(firstSCs.get(0));
		}
	}
	
	private void initBestSC(SolverConfiguration sc) throws Exception {
		this.bestSC = firstSCs.get(0);
		bestSC.setIncumbentNumber(incumbentNumber++);
		pacc.log("i " + pacc.getWallTime() + " ," + bestSC.getCost() + ", n.A.," + bestSC.getIdSolverConfiguration() + ", n.A.," + bestSC.getParameterConfiguration().toString());
		
		int expansion = 0;
		if (bestSC.getJobCount() < parameters.getMaxParcoursExpansionFactor() * num_instances) {
			expansion = Math.min(parameters.getMaxParcoursExpansionFactor() * num_instances - bestSC.getJobCount(), parameters.getInitialDefaultParcoursLength());
			pacc.expandParcoursSC(bestSC, expansion);
		}
		if (expansion > 0) {
			pacc.log("c Expanding parcours of best solver config " + bestSC.getIdSolverConfiguration() + " by " + expansion);
		}
		// update the status of the jobs of bestSC and if first level wait
		// also for jobs to finish
		if (expansion > 0) {
			pacc.log("c Waiting for currently best solver config " + bestSC.getIdSolverConfiguration() + " to finish " + expansion + "job(s)");
			while (true) {
				pacc.updateJobsStatus(bestSC);
				if (bestSC.getNotStartedJobs().isEmpty() && bestSC.getRunningJobs().isEmpty()) {
					break;
				}
				pacc.sleep(1000);
			}
		} else {
			pacc.updateJobsStatus(bestSC);
		}
	}
	

	public String toString(){
		return "Roar with aggressive capping.";
	}
	
	@Override
	public int compareTo(SolverConfiguration sc1, SolverConfiguration sc2) {
		return sc1.compareTo(sc2);
	}

	@Override
	public void solverConfigurationsFinished(List<SolverConfiguration> scs) throws Exception {
		aggressiveCapping(pacc.returnListNewSC());
		
		for (SolverConfiguration sc : scs) {
			if (sc == bestSC) 
				continue;
			int comp = compareTo(sc, bestSC);
			if (!stopEvalSolverConfigIds.contains(sc.getIdSolverConfiguration()) && comp >= 0) {
				if (sc.getJobCount() == bestSC.getJobCount()) {
					sc.setFinished(true);
					// all jobs from bestSC computed and won against
					// best:
					if (comp > 0) {
						bestSC = sc;
						sc.setIncumbentNumber(incumbentNumber++);
						pacc.log("i " + pacc.getWallTime() + "," + sc.getCost() + ",n.A. ," + sc.getIdSolverConfiguration() + ",n.A. ," + sc.getParameterConfiguration().toString());
					}
					// api.updateSolverConfigurationCost(sc.getIdSolverConfiguration(),
					// sc.getCost(),
					// statistics.getCostFunction());
					// listNewSC.remove(i);
				} else {
					int generated = pacc.addRandomJob(sc.getJobCount(), sc, bestSC, Integer.MAX_VALUE - sc.getNumber());
					pacc.log("c Generated " + generated + " jobs for solver config id " + sc.getIdSolverConfiguration());
					pacc.addSolverConfigurationToListNewSC(sc);
				}
			} else {// lost against best on part of the actual (or should not be evaluated anymore)
					// parcours:
				stopEvalSolverConfigIds.remove(sc.getIdSolverConfiguration());
				
				sc.setFinished(true);
				if (parameters.isDeleteSolverConfigs())
					api.removeSolverConfig(sc.getIdSolverConfiguration());
				pacc.log("d Solver config " + sc.getIdSolverConfiguration() + " with cost " + sc.getCost() + " lost against best solver config on " + sc.getJobCount() + " runs.");
				if (bestSC.getJobCount() < parameters.getMaxParcoursExpansionFactor() * num_instances) {
					pacc.log("c Expanding parcours of best solver config " + bestSC.getIdSolverConfiguration() + " by 1");
					pacc.expandParcoursSC(bestSC, 1);
					pacc.addSolverConfigurationToListNewSC(bestSC);
				}
			}
		}
	}

	@Override
	public void solverConfigurationsCreated(List<SolverConfiguration> scs) throws Exception {
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
			pacc.addRandomJob(parameters.getMinRuns(), sc, bestSC, Integer.MAX_VALUE - sc.getNumber());
			pacc.addSolverConfigurationToListNewSC(sc);
		}
	}

	@Override
	public int computeOptimalExpansion(int coreCount, int jobs, int listNewSCSize) {
		int res = 0;
		if (coreCount < parameters.getMinCPUCount() || coreCount > parameters.getMaxCPUCount()) {
			pacc.log("w Warning: Current core count is " + coreCount);
		}
		int staticVariance = 30;
		if(parameters.getJobCPUTimeLimit() < 4) staticVariance = 10;
		double preferredWorkloadFactor = Math.pow(2, (staticVariance + parameters.getJobCPUTimeLimit()) / parameters.getJobCPUTimeLimit());
		preferredWorkloadFactor = Math.min(10, preferredWorkloadFactor);
		preferredWorkloadFactor = Math.max(2, preferredWorkloadFactor);
		
		if(jobs < preferredWorkloadFactor*coreCount) {
			res = (int) ((preferredWorkloadFactor*coreCount) - jobs) / parameters.getMinRuns();
		}
		if (listNewSCSize == 0 && res == 0) {
			res = 1;
		}
		return res;
	}
	
	/**
	 * every time the <code>solverConfigurationsFinished(List<SolverConfiguration> scs)</code>-method is called all 
	 * solver configurations with waiting or running jobs are analysed. If a solver configuration is with less runs 
	 * way worse than the incumbent it is capped.
	 * 
	 * @param listNewSC a list of all solver configurations with running or waiting jobs
	 */
	private void aggressiveCapping(HashMap<Integer, SolverConfiguration> listNewSC) {
		if((parameters.getStatistics().getCostFunction() instanceof edacc.api.costfunctions.PARX) ||
				(parameters.getStatistics().getCostFunction() instanceof edacc.api.costfunctions.Average)) {
			Collection<SolverConfiguration> scs = listNewSC.values();
			Iterator<SolverConfiguration> scsIter = scs.iterator();
			while(scsIter.hasNext()) {
				SolverConfiguration sc = scsIter.next();
				if(sc.getFinishedJobs().size() < parameters.getMinRuns()) continue;
				Point costs = getCostsOfTheSolverConfigs(bestSC, sc);
				double cappingFactor = Math.pow(maxCappingFactor, (1-(sc.getJobCount()/bestSC.getJobCount())));
				if(cappingFactor < 1) cappingFactor = 1.d;
				if (costs.getY() > cappingFactor*costs.getX()) {
					pacc.log("c COST Competitor (" + costs.getY() + ") > Incumbent ("+ (float) cappingFactor +"*" + costs.getX()+")"); 
					pacc.log("c RUNS Competitor (" + sc.getJobCount() + ") < Incumbent (" + bestSC.getJobCount()+")");
					List<ExperimentResult> jobsToKill = sc.getJobs(); 
					for (ExperimentResult j : jobsToKill) {
						try {
							api.killJob(j.getId());
						} catch (Exception e) {
							pacc.log("w Warning: Job "+j.getId()+" could not be killed!");
						} 
					}
					try {
						api.removeSolverConfig(sc.getIdSolverConfiguration());
					} catch (Exception e) {
						pacc.log("w Warning: SolverConfiguration "+sc.getIdSolverConfiguration()+" could not be removed!");
					}
					scsIter.remove();	
					pacc.log("c SolverConfiguration "+sc.getIdSolverConfiguration()+" was capped!"); 
				}
			}
		}
	}
	
	/**
	 * calculates the costs of the incumbent and a competitor on the same experiment results
	 * 
	 * @param best the incumbent ( bestSC )
	 * @param other solver configuration which is raced against the incumbent
	 * @return
	 */
	private Point getCostsOfTheSolverConfigs(SolverConfiguration best, SolverConfiguration other) {
		HashMap<InstanceIdSeed, ExperimentResult> bestJobs = new HashMap<InstanceIdSeed, ExperimentResult>();
		for (ExperimentResult job : best.getFinishedJobs()) {
			bestJobs.put(new InstanceIdSeed(job.getInstanceId(), job.getSeed()), job);
		}
		List<ExperimentResult> bestJobsInCommon = new LinkedList<ExperimentResult>();
		List<ExperimentResult> otherJobsInCommon = new LinkedList<ExperimentResult>();
		for (ExperimentResult job : other.getFinishedJobs()) {
			InstanceIdSeed tmp = new InstanceIdSeed(job.getInstanceId(), job.getSeed());
			ExperimentResult bestJob;
			if ((bestJob = bestJobs.get(tmp)) != null) {
				bestJobsInCommon.add(bestJob);
				otherJobsInCommon.add(job);
			}
		}
		Point costs = new Point();
		CostFunction costFunc = parameters.getStatistics().getCostFunction();
		float costBest = costFunc.calculateCost(bestJobsInCommon);
		float costOther = costFunc.calculateCost(otherJobsInCommon);
		costs.setLocation(costBest, costOther);
		return costs;
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
		try {
			pacc.updateSolverConfigName(bestSC, true);
		} catch (Exception e) {
			pacc.log("Error: Incumbent name could not be changed!");
			e.printStackTrace();
		}
	}

}
