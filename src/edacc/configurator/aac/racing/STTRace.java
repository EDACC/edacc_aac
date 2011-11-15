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
import edacc.configurator.aac.PROAR;
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
	private int a;
	// min number of evaluations and max number of evaluations
	private int minE, maxE;

	/**
	 * @param proar
	 * @param api
	 * @param parameters
	 * @throws SQLException 
	 */
	public STTRace(PROAR proar, API api, Parameters parameters) throws SQLException {
		super(proar, api, parameters);
		this.a = 2;
		this.minE = 5;
		
		incumbentNumber = 0;
		num_instances = ConfigurationScenarioDAO.getConfigurationScenarioByExperimentId(parameters.getIdExperiment()).getCourse().getInitialLength();
		this.maxE = parameters.getMaxParcoursExpansionFactor()*num_instances;
	}

	/*
	 * sc1 is supposed to be the configuration with more jobs and expected
	 * better one
	 */
	@Override
	public int compareTo(SolverConfiguration sc1, SolverConfiguration sc2) {
		// number of jobs that sc1 and sc2 have in common.
		int n = Math.min(sc1.getNumFinishedJobs(), sc2.getNumFinishedJobs());
		float[] y = new float[n]; // times of sc1
		float[] z = new float[n]; // times of sc2
		float[] x = new float[n]; // time difference y-z
		int i = 0;
		double testValue, threshold = 2.D * (double) n;
		float mean = 0f, std2 = 0f; // mean and quadratic standard deviation
		HashMap<InstanceIdSeed, ExperimentResult> sc1JobsMap = new HashMap<InstanceIdSeed, ExperimentResult>();
		for (ExperimentResult job : sc1.getFinishedJobs()) {
			sc1JobsMap.put(new InstanceIdSeed(job.getInstanceId(), job.getSeed()), job);
		}
		// List<ExperimentResult> sc1JobsList = new
		// LinkedList<ExperimentResult>();
		// List<ExperimentResult> sc2JobsList = new
		// LinkedList<ExperimentResult>();
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see edacc.configurator.proar.racing.PROARRacing#getBestSC()
	 */
	@Override
	public SolverConfiguration getBestSC() {
		// TODO Auto-generated method stub
		return bestSC;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * edacc.configurator.proar.racing.PROARRacing#solverConfigurationsFinished
	 * (java.util.List)
	 */
	@Override
	public void solverConfigurationsFinished(List<SolverConfiguration> scs) throws Exception {
		// TODO Auto-generated method stub

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
		// TODO Auto-generated method stub

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
