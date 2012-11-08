package edacc.configurator.aac.racing;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.ConfigurationScenarioDAO;

public class DefaultSMBO extends RacingMethods {
	SolverConfiguration bestSC;
	int incumbentNumber;
	int num_instances;
	private int numSCs = 0;
	private int curThreshold = 0;
	private int increaseIncumbentRunsEvery = 32;
	private boolean aggressiveJobSelection = false;
	
	HashSet<Integer> stopEvalSolverConfigIds = new HashSet<Integer>();
	Set<SolverConfiguration> challengers = new HashSet<SolverConfiguration>();

	public DefaultSMBO(AAC proar, Random rng, API api, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
		super(proar, rng, api, parameters, firstSCs, referenceSCs);
		incumbentNumber = 0;
		num_instances = ConfigurationScenarioDAO.getConfigurationScenarioByExperimentId(parameters.getIdExperiment()).getCourse().getInitialLength();
	
		String val;
        if ((val = parameters.getRacingMethodParameters().get("DefaultSMBO_increaseIncumbentRunsEvery")) != null)
            increaseIncumbentRunsEvery = Integer.valueOf(val);
        if ((val = parameters.getRacingMethodParameters().get("DefaultSMBO_aggressiveJobSelection")) != null)
            aggressiveJobSelection = Integer.valueOf(val) == 1;
        
        if (aggressiveJobSelection) {
            pacc.log("c Using aggressive job selection.");
        }
        
        curThreshold = increaseIncumbentRunsEvery;
		
		if (!firstSCs.isEmpty()) {
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
			pacc.validateIncumbent(bestSC);
		} else {
			pacc.updateJobsStatus(bestSC);
		}
	}
	

	public String toString(){
		return "This is the racing method or ROAR";
	}
	@Override
	public int compareTo(SolverConfiguration sc1, SolverConfiguration sc2) {
		return sc1.compareTo(sc2);
	}

	@Override
	public void solverConfigurationsFinished(List<SolverConfiguration> scs) throws Exception {
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
						pacc.log("new incumbent: " + sc.getIdSolverConfiguration() + ":" + pacc.getWallTime() + ":" + pacc.getCumulatedCPUTime() + ":" + sc.getCost());
						pacc.log("i " + pacc.getWallTime() + "," + sc.getCost() + ",n.A. ," + sc.getIdSolverConfiguration() + ",n.A. ," + sc.getParameterConfiguration().toString());
						pacc.validateIncumbent(bestSC);
					}
					challengers.remove(sc);
					// api.updateSolverConfigurationCost(sc.getIdSolverConfiguration(),
					// sc.getCost(),
					// statistics.getCostFunction());
					// listNewSC.remove(i);
				} else {
				    int generated = 0;
				    if (aggressiveJobSelection) {
				        generated = pacc.addRandomJobAggressive(sc.getJobCount(), sc, bestSC, Integer.MAX_VALUE - sc.getNumber());
				    } else {
				        generated = pacc.addRandomJob(sc.getJobCount(), sc, bestSC, Integer.MAX_VALUE - sc.getNumber());
				    }
					pacc.log("c Generated " + generated + " jobs for solver config id " + sc.getIdSolverConfiguration());
					pacc.addSolverConfigurationToListNewSC(sc);
				}
			} else {// lost against best on part of the actual (or should not be evaluated anymore)
					// parcours:
				stopEvalSolverConfigIds.remove(sc.getIdSolverConfiguration());
				
				challengers.remove(sc);
				sc.setFinished(true);
				if (parameters.isDeleteSolverConfigs())
					api.removeSolverConfig(sc.getIdSolverConfiguration());
				pacc.log("d Solver config " + sc.getIdSolverConfiguration() + " with cost " + sc.getCost() + " lost against best solver config on " + sc.getJobCount() + " runs.");
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
		    if (aggressiveJobSelection) {
		        pacc.log("c Using aggressive job selection");
		        pacc.addRandomJobAggressive(parameters.getMinRuns(), sc, bestSC, Integer.MAX_VALUE - sc.getNumber());
		    } else {
		        pacc.addRandomJob(parameters.getMinRuns(), sc, bestSC, Integer.MAX_VALUE - sc.getNumber());
		    }
			pacc.addSolverConfigurationToListNewSC(sc);
		}
		
		
		for (int i = 0; i < scs.size(); i++) {
		    numSCs += 1;
	        if (numSCs > curThreshold && bestSC.getJobCount() < parameters.getMaxParcoursExpansionFactor() * num_instances) {
	            pacc.log("c Expanding parcours of best solver config " + bestSC.getIdSolverConfiguration() + " by 1");
	            pacc.expandParcoursSC(bestSC, 1);
	            pacc.addSolverConfigurationToListNewSC(bestSC);
	            curThreshold += increaseIncumbentRunsEvery;
	        }
		}

		challengers.addAll(scs);
	}

	@Override
	public int computeOptimalExpansion(int coreCount, int jobs, int listNewSCSize) {
		int res = 0;
		/*if (coreCount < parameters.getMinCPUCount() || coreCount > parameters.getMaxCPUCount()) {
			pacc.log("w Warning: Current core count is " + coreCount);
		}
		if (Math.max(0, coreCount - challengers.size()) < 10) return 0;
		return Math.max(0, coreCount - challengers.size());*/
		
		/*if (challengers.size() > 0) {
		    return 0;
		}
		else {
		    pacc.log("c Requesting " + coreCount + " configurations from search");
		    return coreCount;
		}*/
		
		int min_sc = (Math.max(Math.round(4.f * coreCount), 8) - jobs) / parameters.getMinRuns();
		if (min_sc > 0) {
			res = (Math.max(Math.round(6.f * coreCount), 8) - jobs) / parameters.getMinRuns();
		}
		if (listNewSCSize == 0 && res == 0) {
			res = 1;
		}
		return res;
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
		// TODO Auto-generated method stub
		
	}

}