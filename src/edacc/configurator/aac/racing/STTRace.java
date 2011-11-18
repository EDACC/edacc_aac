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
	 * @param pacc
	 * @param api
	 * @param parameters
	 * @throws SQLException
	 */
	public STTRace(AAC pacc, API api, Parameters parameters) throws SQLException {
		super(pacc, api, parameters);
		this.a = 0.5;
		this.minE = parameters.getInitialDefaultParcoursLength();
		incumbentNumber = 0;
		num_instances = ConfigurationScenarioDAO.getConfigurationScenarioByExperimentId(parameters.getIdExperiment())
				.getCourse().getInitialLength();
		this.maxE = parameters.getMaxParcoursExpansionFactor() * num_instances;
	}

	/*
	 * compares sc1 to sc2 with a sequential t-test. returns 
	 * -1 if sc1 is not better than sc2 and the test can be stopped (or has reached maxE) 
	 * 1 if sc1 is better than sc2 and the test can be stopped (or has reached maxE)
	 * 0 if they are equal and the maximum number of evaluations has been reached
	 * -2 if further evaluations are necessary and possible
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
		double mean = 0., std2 = 0.; // mean and quadratic standard deviation
		double meany = 0,meanz = 0;
		HashMap<InstanceIdSeed, ExperimentResult> sc1JobsMap = new HashMap<InstanceIdSeed, ExperimentResult>();
		for (ExperimentResult job : sc1.getFinishedJobs()) {
			sc1JobsMap.put(new InstanceIdSeed(job.getInstanceId(), job.getSeed()), job);
		}
		for (ExperimentResult job : sc2.getFinishedJobs()) {
			InstanceIdSeed tmp = new InstanceIdSeed(job.getInstanceId(), job.getSeed());
			ExperimentResult sc1Job;
			if ((sc1Job = sc1JobsMap.get(tmp)) != null) {
				y[i] = parameters.getStatistics().getCostFunction().singleCost(sc1Job);
				z[i] = parameters.getStatistics().getCostFunction().singleCost(job);
				x[i] = y[i] - z[i];
				i++;
			}
		}
		
		// compute the mean;
		for (int j = 0; j < n; j++) {
			mean += x[j];
			meany += y[j];
			meanz += z[j];
		}
		mean = mean / (double) n;
		meanz = meanz /(double) n;
		meany = meany / (double) n;
		System.out.println("STTRACE: " + sc1.getNumber() + " vs " + sc2.getNumber());
		System.out.println("STTRACE: " + sc1.getJobCount()+"(e)" + " vs " + sc2.getJobCount()+"(e)");
		System.out.println("STTRACE: " + meany + " vs " + meanz);
		System.out.println("STTRACE: n = "+n);
		System.out.println("STTRACE: mean = "+mean);
		// compute std2
		for (int j = 0; j < n; j++) {
			std2 += (x[j] - mean) * (x[j] - mean);
		}
		std2 = std2 / (float) n;
		System.out.println("STTRACE: std2 = "+std2);
		// the sequential t-test
		testValue = Math.log(1.0 + mean * mean / std2);
		System.out.println("STTRACE: testValue = "+testValue);
		System.out.println("STTRACE: threshold = "+threshold);
		if ((testValue > threshold) || (n == this.maxE)) {
			// test can stop
			if (mean > 0){ // sc2 better
				System.out.println("sc2 better");
				return -1;
			}
			else if (mean < 0){
				System.out.println("sc1 better");
				return 1;
			}
			else
				return 0;
		} else{
			System.out.println("More Evaluations needed!");
			return -2;
		}
	}

	public void initFirstSC(SolverConfiguration firstSC) throws Exception {
		this.bestSC = firstSC;
		bestSC.setIncumbentNumber(incumbentNumber++);
		pacc.log("i " + pacc.getWallTime() + " ," + firstSC.getCost() + ", n.A.," + bestSC.getIdSolverConfiguration()
				+ ", n.A.," + bestSC.getParameterConfiguration().toString());

		int expansion = 0;
		if (bestSC.getJobCount() < parameters.getMaxParcoursExpansionFactor() * num_instances) {
			expansion = Math.min(parameters.getMaxParcoursExpansionFactor() * num_instances - bestSC.getJobCount(),
					parameters.getInitialDefaultParcoursLength());
			pacc.expandParcoursSC(bestSC, expansion);
		}
		if (expansion > 0) {
			pacc.log("c Expanding parcours of best solver config " + bestSC.getIdSolverConfiguration() + " by "
					+ expansion);
		}
		// update the status of the jobs of bestSC and if first level wait
		// also for jobs to finish
		if (expansion > 0) {
			pacc.log("c Waiting for currently best solver config " + bestSC.getIdSolverConfiguration() + " to finish "
					+ expansion + "job(s)");
			while (true) {
				pacc.updateJobsStatus(bestSC);
				if (bestSC.getNotStartedJobs().isEmpty() && bestSC.getRunningJobs().isEmpty()) {
					break;
				}
				Thread.sleep(1000);
			}
		} else {
			pacc.updateJobsStatus(bestSC);
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
				System.out.println("STTRACE : best would have lost on " +sc.getJobCount());
				if (sc.getJobCount() > bestSC.getJobCount()/2) {
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
					int generated = pacc.addRandomJob(1, sc, bestSC, Integer.MAX_VALUE - sc.getNumber());
					pacc.log("c Generated " + generated + " jobs for solver config id " + sc.getNumber());
					pacc.addSolverConfigurationToListNewSC(sc);
				}
			} else if (comp == -1) {// lost against best on part of the actual
				// parcours:
				sc.setFinished(true);
				if ((deleteSolverConfigs) && (sc.getIncumbentNumber()==-1))
					api.removeSolverConfig(sc.getIdSolverConfiguration());
				pacc.log("d Solver config " + sc.getNumber() + " with cost " + sc.getCost()
						+ " lost against best solver config on " + sc.getJobCount() + " runs.");
				/*if (bestSC.getJobCount() < parameters.getMaxParcoursExpansionFactor() * num_instances) {
					pacc.log("c Expanding parcours of best solver config " + bestSC.getIdSolverConfiguration()
							+ " by 1");
					pacc.expandParcoursSC(bestSC, 1);
					pacc.addSolverConfigurationToListNewSC(bestSC);
				}*/
			} else { // comp == -2 further jobs are needed to asses performance
				if (sc.getJobCount() > bestSC.getJobCount()) {
					pacc.log("c Expanding parcours of best solver config (strange case)" + bestSC.getNumber()+ " by 1");
					pacc.expandParcoursSC(bestSC, 1);
					//int generated = pacc.addRandomJob(1, bestSC, sc, Integer.MAX_VALUE - sc.getNumber());
					//pacc.log("c Generated " + generated + " jobs for solver config id " + sc.getIdSolverConfiguration());
					pacc.addSolverConfigurationToListNewSC(bestSC);
				}else if (sc.getJobCount() < bestSC.getJobCount()){
					pacc.log("c Expanding parcours of solver config " + sc.getNumber()+ " by 1");
					pacc.expandParcoursSC(sc, 1);
					//int generated = pacc.addRandomJob(1, sc, bestSC, Integer.MAX_VALUE - sc.getNumber());
					//pacc.log("c Generated " + generated + " jobs for solver config id " + sc.getIdSolverConfiguration());
					pacc.addSolverConfigurationToListNewSC(sc);
				}else{
					pacc.log("c Expanding parcours of best and competitor solver config " + bestSC.getNumber()+ " by 1");
					pacc.expandParcoursSC(bestSC, 1);
					pacc.addSolverConfigurationToListNewSC(bestSC);
					//pacc.log("c Expanding parcours of solver config " + sc.getIdSolverConfiguration()+ " by 1");
					pacc.expandParcoursSC(sc, 1);
					pacc.addSolverConfigurationToListNewSC(sc);
				}
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
			pacc.addRandomJob(minE, sc, bestSC, Integer.MAX_VALUE - sc.getNumber());
			pacc.addSolverConfigurationToListNewSC(sc);
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
		 * geschicktere Moeglichkeit ist es: Anzahl cores = numCores Größe der
		 * besseren solver configs in letzter runde = numBests Anzahl der jobs
		 * die in der letzten Iteration berechnet wurden = numJobs Anzahl der
		 * neuen solver configs beim letzten Aufruf zurückgeliefert wurden =
		 * lastExpansion CPUTimeLimit = time Dann kann man die Anzahl an neuen
		 * konfigs berechnen durch newNumConfigs = TODO
		 */
	}

}
