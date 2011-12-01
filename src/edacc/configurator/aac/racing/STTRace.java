/**
 * 
 */
package edacc.configurator.aac.racing;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import edacc.api.API;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.ConfigurationScenarioDAO;
import edacc.model.ExperimentResult;


/**
 * @author balint Every SC has to be evaluated on the same parcours in
 *         cronological order.
 */
public class STTRace extends RacingMethods {
	private static final boolean deleteSolverConfigs = false;

	SolverConfiguration bestSC;
	int incumbentNumber;
	int num_instances;

	// Threshold for the test
	private double a;
	// min number of evaluations and max number of evaluations
	private int minE, maxE; 
	//minimum number of evaluations to beat best
	private int minEB;
	//add random jobs from best or follow parcours?
	private boolean randJob;
	
	/**
	 * @param pacc
	 * @param api
	 * @param parameters
	 * @throws SQLException
	 */
	public STTRace(AAC pacc, Random rng, API api, Parameters parameters) throws SQLException {
		super(pacc, rng, api, parameters);
		this.a = 0.6;
		this.minE = parameters.getInitialDefaultParcoursLength();
		this.minEB = 10;
		incumbentNumber = 0;
		num_instances = ConfigurationScenarioDAO.getConfigurationScenarioByExperimentId(parameters.getIdExperiment())
				.getCourse().getInitialLength();
		this.maxE = parameters.getMaxParcoursExpansionFactor() * num_instances;
		this.randJob = true;
	
		String val;
		if ((val = parameters.getRacingMethodParameters().get("STTRace_a"))!= null)
			this.a = Double.parseDouble(val);
		if ((val = parameters.getRacingMethodParameters().get("STTRace_minEB"))!= null)
			this.minEB = Integer.parseInt(val);
		if ((val = parameters.getRacingMethodParameters().get("STTRace_randJob"))!= null)
			this.randJob = Boolean.parseBoolean(val);
		if (this.minEB<0){ 
			System.out.println("<STTRace_minEB> <0 not allowed! Setting <STTRace_minEB> to default!)");
			this.minEB = 10;
		}
	}

	public String toString(){
		return "\nThis is a sequential t-test racing method with the following parameters:\n" +
				"<STTRace_a> = " + this.a + " (threshold a value) \n"+
				"<STTRace_minEB> = " + this.minEB +" (minimum number of evaluations before replacing best as %(1..) of num instances) \n"+
				"<STTRace_randJob> = " + this.randJob+ "(new config gets random jobs from best) \n"; 
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
		this.numCompCalls ++;
		// number of jobs that sc1 and sc2 have in common.
		int n1 = sc1.getFinishedJobs().size();
		int n2 = sc2.getFinishedJobs().size();
		int n = Math.min(n1, n2);
		System.out.println("S: " + sc1.getNumber()+"("+sc1.getIdSolverConfiguration()+")" + " vsn " + sc2.getNumber()+"("+sc2.getIdSolverConfiguration()+")");
		System.out.println("S: " + n1 + "(e)" + " vse " + n2 + "(e)");
		System.out.println("S: n = " + n);
		float[] y = new float[n]; // times of sc1
		float[] z = new float[n]; // times of sc2

		float[] x = new float[n]; // time difference y-z
		int i = 0;
		double testValue, threshold = 2.D * a / (double) n;
		double mean = 0., std2 = 0.; // mean and quadratic standard deviation
		double meany = 0, meanz = 0;
		HashMap<InstanceIdSeed, ExperimentResult> sc1JobsMap = new HashMap<InstanceIdSeed, ExperimentResult>();
		//System.out.println("STTRACE: " + sc1.getFinishedJobs().size() + "(e)" + " vs " + sc2.getFinishedJobs().size()	+ "(e)");
		for (ExperimentResult job : sc1.getFinishedJobs()) {
			sc1JobsMap.put(new InstanceIdSeed(job.getInstanceId(), job.getSeed()), job);
		}
		//System.out.println("S: sc1 has " + sc1JobsMap.size());
		for (ExperimentResult job : sc2.getFinishedJobs()) {
			InstanceIdSeed tmp = new InstanceIdSeed(job.getInstanceId(), job.getSeed());
			ExperimentResult sc1Job;
			if ((sc1Job = sc1JobsMap.get(tmp)) != null) {
				//System.out.println(sc1Job.getSeed() + " " + sc1Job.getInstanceId() + " " + sc1Job.getStatus());
				//System.out.println(job.getSeed() + " " + job.getInstanceId() + " " + job.getStatus());

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
		meanz = meanz / (double) n;
		meany = meany / (double) n;
		System.out.println("S: " + meany+"(t=" + sc1.getDbCost()+")" + " vss " + meanz+"(t=" + sc2.getDbCost() +")");
		System.out.println("S: mean = " + mean);
		// compute std2
		for (int j = 0; j < n; j++) {
			std2 += (x[j] - mean) * (x[j] - mean);
		}
		std2 = std2 / (float) n;
		System.out.println("S: std2 = " + std2);
		// the sequential t-test
		testValue = Math.log(1.0 + mean * mean / std2);
		System.out.println("S: testValue = " + testValue);
		System.out.println("S: threshold = " + threshold);
		if ((testValue > threshold) || (n == this.maxE)) {
			//test can stop
			if (n==this.maxE)
				System.out.println("dead end!");
			if (mean > 0) { // sc2 better
				System.out.println("sc2 better");
				return -1;
			} else if (mean < 0) {
				System.out.println("sc1 better");
				return 1;
			} else
				return 0;
		} else {
			System.out.println("More!");
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
	 * goes through the list of finished SC and compares them to the bestSC.
	 * There are several cases that can be taken into consideration:
	 * 1. sc can beat bestSC on less jobs than num of bestSC
	 * 2. sc should have at least num of bestSC / c
	 * 3. sc should have at least as many as bestSC
	 * 4. 
	 */
	@Override
	public void solverConfigurationsFinished(List<SolverConfiguration> scs) throws Exception {
		for (SolverConfiguration sc : scs) {
			if (sc == bestSC)
				continue;
			int comp = compareTo(sc, bestSC);
			if (comp >= 0) {// sc won against bestSC
				//TODO: hier stimmt was nicht!!!
				if (((sc.getJobCount() == bestSC.getJobCount())&&randJob)||((sc.getJobCount()>=this.num_instances/this.minEB)&&(!randJob))) {
					sc.setFinished(true);
					// all jobs from bestSC computed and won against
					// best:
					System.out.println("sc1 won!!!");
					if (comp > 0) {
						bestSC = sc;
						sc.setIncumbentNumber(incumbentNumber++);
						pacc.log("i " + pacc.getWallTime() + "," + sc.getCost() + ",n.A. ,"
								+ sc.getIdSolverConfiguration() + ",n.A. ," + sc.getParameterConfiguration().toString());
					}
					// api.updateSolverConfigurationCost(sc.getIdSolverConfiguration(),
					// sc.getCost(),
					// statistics.getCostFunction());
					// listNewSC.remove(i);
				} else {
					if (this.randJob) {
						int generated = pacc.addRandomJob(1, sc, bestSC, Integer.MAX_VALUE - sc.getNumber());
						pacc.log("c Generated " + generated + " jobs for solver config id " + sc.getNumber());
					}
					else{
						pacc.expandParcoursSC(sc, 1);
					}
					pacc.addSolverConfigurationToListNewSC(sc);
				}
			} else if (comp == -1) {// lost against best on part of the actual
				// parcours:
				sc.setFinished(true);
				if ((deleteSolverConfigs) && (sc.getIncumbentNumber() == -1)) {
					api.removeSolverConfig(sc.getIdSolverConfiguration());
					pacc.log("d Solver config " + sc.getNumber() + " with cost " + sc.getCost()
							+ " lost against best solver config on " + sc.getJobCount() + " runs.");
				}
			} else { // comp == -2 further jobs are needed to asses performance
				if (sc.getJobCount() > bestSC.getJobCount()) {
					pacc.log("c Expanding parcours of best solver config (strange case)" + bestSC.getNumber() + " by 1");
					pacc.expandParcoursSC(bestSC, 1);
					pacc.addSolverConfigurationToListNewSC(bestSC);
					pacc.addSolverConfigurationToListNewSC(sc);
				} else if (sc.getJobCount() < bestSC.getJobCount()) {
					pacc.log("c Expanding parcours of solver config " + sc.getNumber() + " by 1");
					if (this.randJob) {
						int generated = pacc.addRandomJob(1, sc, bestSC, Integer.MAX_VALUE - sc.getNumber());
						pacc.log("c Generated " + generated + " jobs for solver config id " + sc.getIdSolverConfiguration());
					}
					else{
						pacc.expandParcoursSC(sc, 1);
					}
					pacc.addSolverConfigurationToListNewSC(sc);
				} else {
					pacc.log("c Expanding parcours of best and competitor solver config " + bestSC.getNumber()
							+ " by 1");
					pacc.expandParcoursSC(bestSC, 1);
					pacc.addSolverConfigurationToListNewSC(bestSC);
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
		int gen;
		for (SolverConfiguration sc : scs) {
			if (this.randJob){
				gen = pacc.addRandomJob(minE, sc, bestSC, Integer.MAX_VALUE - sc.getNumber());
				System.out.println("added ->"+gen);
			}
			else{
				pacc.expandParcoursSC(sc, minE);
			}
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
		 * geschicktere Moeglichkeit ist es: Anzahl cores = numCores Gr��e der
		 * besseren solver configs in letzter runde = numBests Anzahl der jobs
		 * die in der letzten Iteration berechnet wurden = numJobs Anzahl der
		 * neuen solver configs beim letzten Aufruf zur�ckgeliefert wurden =
		 * lastExpansion CPUTimeLimit = time Dann kann man die Anzahl an neuen
		 * konfigs berechnen durch newNumConfigs = TODO
		 */
	}

}