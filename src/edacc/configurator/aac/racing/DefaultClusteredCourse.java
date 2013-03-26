package edacc.configurator.aac.racing;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.rosuda.JRI.Rengine;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.configurator.aac.course.StratifiedClusterCourse;
import edacc.configurator.aac.util.RInterface;
import edacc.model.ConfigurationScenarioDAO;

public class DefaultClusteredCourse extends RacingMethods {
	SolverConfiguration bestSC;
	int incumbentNumber;
	int num_instances;
	HashSet<Integer> stopEvalSolverConfigIds = new HashSet<Integer>();
	private List<InstanceIdSeed> completeCourse;
	private Rengine rengine;
	
	private String featureFolder = null;
	private String featureCacheFolder = null;

	public DefaultClusteredCourse(AAC proar, Random rng, API api, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
		super(proar, rng, api, parameters, firstSCs, referenceSCs);
		incumbentNumber = 0;
		num_instances = ConfigurationScenarioDAO.getConfigurationScenarioByExperimentId(parameters.getIdExperiment()).getCourse().getInitialLength();
		
        String val;
        if ((val = parameters.getRacingMethodParameters().get("DefaultClusteredCourse_featureFolder")) != null)
            featureFolder = val;
        if ((val = parameters.getRacingMethodParameters().get("DefaultClusteredCourse_featureCacheFolder")) != null)
            featureCacheFolder = val;
		
        rengine = RInterface.getRengine();

        if (rengine.eval("library(asbio)") == null) {
            rengine.end();
            throw new Exception("Did not find R library asbio (try running install.packages(\"asbio\")).");
        }
        if (rengine.eval("library(survival)") == null) {
            rengine.end();
            throw new Exception("Did not find R library survival (should come with R though).");
        }
		
		this.completeCourse = new StratifiedClusterCourse(rengine, api.getExperimentInstances(parameters.getIdExperiment()), null, null, parameters.getMaxParcoursExpansionFactor(), rng, featureFolder, featureCacheFolder, null).getCourse();
		
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
			for (int i = 0; i < expansion; i++) {
                pacc.addJob(bestSC, completeCourse.get(bestSC.getJobCount()).seed,
                        completeCourse.get(bestSC.getJobCount()).instanceId,
                        parameters.getMaxParcoursExpansionFactor() * num_instances - bestSC.getJobCount());
			}
			//pacc.expandParcoursSC(bestSC, expansion);
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
				pacc.validateIncumbent(bestSC);
			}
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
					pacc.addJob(bestSC, completeCourse.get(bestSC.getJobCount()).seed,
	                        completeCourse.get(bestSC.getJobCount()).instanceId,
	                        parameters.getMaxParcoursExpansionFactor() * num_instances - bestSC.getJobCount());
					
					//pacc.expandParcoursSC(bestSC, 1);
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
		int min_sc = (Math.max(Math.round(4.f * coreCount), 8) - jobs) / parameters.getMinRuns();
		if (min_sc > 0) {
			res = (Math.max(Math.round(6.f * coreCount), 8) - jobs) / parameters.getMinRuns();
		}
		if (listNewSCSize == 0 && res == 0) {
			res = 1;
		}
		return res;
		/*
		 * TODO: was geschickteres implementieren, denn von diesem Wert haengt
		 * sehr stark der Grad der parallelisierung statt. denkbar ware noch
		 * api.getNumComputingUnits(); wenn man die Methode haette. eine andere
		 * geschicktere Moeglichkeit ist es: Anzahl cores = numCores Gr��e der
		 * besseren solver configs in letzter runde = numBests Anzahl der jobs
		 * die in der letzten Iteration berechnet wurden = numJobs Anzahl der
		 * neuen solver configs beim letzten Aufruf zur�ckgeliefert wurden =
		 * lastExpansion CPUTimeLimit = time Dann kann man die Anzahl an neuen
		 * konfigs berechnen durch newNumConfigs = TODO
		 */
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
