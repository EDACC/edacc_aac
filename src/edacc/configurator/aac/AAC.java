package edacc.configurator.aac;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import java.util.Random;
import java.util.Scanner;

import edacc.api.API;
import edacc.api.APIImpl;
import edacc.api.APISimulation;
import edacc.api.costfunctions.CostFunction;
import edacc.configurator.aac.racing.RacingMethods;
import edacc.configurator.aac.search.SearchMethods;
import edacc.configurator.aac.util.RInterface;
import edacc.model.ConfigurationScenarioDAO;
import edacc.model.Course;
import edacc.model.DatabaseConnector;
import edacc.model.ExperimentResultDAO;
import edacc.model.Instance;
import edacc.model.InstanceClassMustBeSourceException;
import edacc.model.InstanceDAO;
import edacc.model.InstanceNotInDBException;
import edacc.model.NoConnectionToDBException;
import edacc.model.ResultCode;
import edacc.model.StatusCode;
//import edacc.model.ExperimentDAO;
import edacc.model.ExperimentResult;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;
import edacc.util.Pair;

public class AAC {
	// private static final boolean useCapping = false;

	// private String experimentName;

	/** All parameters for racing and searching methods */
	private Parameters parameters;

	private API api;
	/**
	 * The random number generator for the search method - controls which solver
	 * configuration to analyze next; use only within the search method!!!
	 */
	private Random rngSearch;

	/**
	 * The random number generator for the racing method - controls which jobs
	 * to evaluate next; use only within the racing method!!!
	 */
	private Random rngRacing;

	private Class<?> searchClass;
	private Class<?> racingClass;

	/** Method used for searching within the parameter search space */
	public SearchMethods search;

	/** Method to race SC against each other */
	public RacingMethods racing;

	/**
	 * Indicates the start time of the configurator (used to determine
	 * walltime).
	 */
	private long startTime;
	private long lastStats;

	/**
	 * Total cumulated CPU-Time of all jobs the configurator has finished so far
	 * in seconds.
	 */
	private float cumulatedCPUTime;

	public float getCumulatedCPUTime() {
        return cumulatedCPUTime;
    }

    /**
	 * List of all solver configuration generated by the search method, that are
	 * going to be raced against the best with the race method.
	 */
	private HashMap<Integer, SolverConfiguration> listNewSC;

	/**
	 * The CPUTimeLimits for the instances.
	 */
	private HashMap<Integer, Integer> instanceCPUTimeLimits;
	
	private List<SolverConfiguration> solverConfigs;
	
	
	// listeners
	private List<JobListener> jobListeners;
	
	// statistics
	private int statNumSolverConfigs;
	private int statNumJobs;
	private int statNumRestartedJobs;

	
	private ParameterGraph graph;

	public AAC(Parameters params) throws Exception {
		startTime = System.currentTimeMillis();
		lastStats = 0;
		
		if (params.simulation) {
			Random rng = new edacc.util.MersenneTwister(params.simulationSeed);
			log("Simulation flag set, using simulation api.");
			api = new APISimulation(params.simulationCorecount, rng);
			api.connect(params.hostname, params.port, params.database, params.user, params.password, true);
			((APISimulation) api).generateCourse(params.idExperiment);
			((APISimulation) api).cacheJobs(params.idExperiment);
		} else {
			api = new APIImpl();
			api.connect(params.hostname, params.port, params.database, params.user, params.password);
		}

		jobListeners = new LinkedList<JobListener>();
		
		this.graph = api.loadParameterGraphFromDB(params.idExperiment);
		System.out.println(params.costFunc + " .. " + params.idExperiment);
		CostFunction costFunction = api.costFunctionByExperiment(params.idExperiment, params.costFunc);
		System.out.println(costFunction);
		params.setStatistics(costFunction, costFunction.getMinimize());
		rngSearch = new edacc.util.MersenneTwister(params.searchSeed);
		rngRacing = new edacc.util.MersenneTwister(params.racingSeed);
		listNewSC = new HashMap<Integer, SolverConfiguration>();
		this.statNumSolverConfigs = 0;
		this.statNumJobs = 0;
		this.parameters = params;
		searchClass = ClassLoader.getSystemClassLoader().loadClass("edacc.configurator.aac.search." + params.searchMethod);
		racingClass = ClassLoader.getSystemClassLoader().loadClass("edacc.configurator.aac.racing." + params.racingMethod);
		solverConfigs = new ArrayList<SolverConfiguration>();
		
		if (params.deleteSolverConfigsAtStart) {
			log("c Removing solver configurations..");
			HashSet<Integer> retain = new HashSet<Integer>();
			retain.addAll(api.getSolverConfigurations(parameters.idExperiment, "default"));
			retain.addAll(api.getSolverConfigurations(parameters.idExperiment, "reference"));
			for (Integer id : api.getSolverConfigurations(parameters.idExperiment)) {
				if (!retain.contains(id))
					api.removeSolverConfig(id);
			}
			log("c Done.");
		}
		instanceCPUTimeLimits = new HashMap<Integer, Integer>();
	}

	/**
	 * Returns a list of solver configuration with default solver configurations or the best solver configurations in the db.
	 * @return
	 * @throws Exception
	 */
	private List<SolverConfiguration> initializeFirstSCs() throws Exception {
		// TODO: the best one might not match the configuration scenario
		// graph.validateParameterConfiguration(config) should test this,
		// but is currently not implemented and will return false.
		if (search instanceof edacc.configurator.aac.search.Matrix) {
			edacc.configurator.aac.search.Matrix m = (edacc.configurator.aac.search.Matrix) search;
			List<SolverConfiguration> res = new LinkedList<SolverConfiguration>();
			res.add(m.getFirstSC());
			return res;
		}

		List<Integer> solverConfigIds = api.getSolverConfigurations(parameters.getIdExperiment(), "default");
		if (solverConfigIds.isEmpty()) {
			solverConfigIds = api.getSolverConfigurations(parameters.getIdExperiment());
			log("c Found " + solverConfigIds.size() + " solver configuration(s)");
		} else {
			log("c Found " + solverConfigIds.size() + " default configuration(s)");
		}
		List<SolverConfiguration> solverConfigs = new LinkedList<SolverConfiguration>();
		for (int id : solverConfigIds) {
			ParameterConfiguration pConfig = api.getParameterConfiguration(parameters.getIdExperiment(), id);
			solverConfigs.add(new SolverConfiguration(id, pConfig, parameters.getStatistics()));
		}

		Course c = ConfigurationScenarioDAO.getConfigurationScenarioByExperimentId(parameters.getIdExperiment()).getCourse();
		for (int sc_index = solverConfigs.size() - 1; sc_index >= 0; sc_index--) {
			SolverConfiguration sc = solverConfigs.get(sc_index);
			HashSet<InstanceIdSeed> iis = new HashSet<InstanceIdSeed>();
			for (ExperimentResult job : api.getRuns(parameters.getIdExperiment(), sc.getIdSolverConfiguration())) {
				sc.putJob(job);
				iis.add(new InstanceIdSeed(job.getInstanceId(), job.getSeed()));
			}
			boolean courseValid = true;
			boolean courseEnded = false;
			for (int i = 0; i < c.getLength(); i++) {
				InstanceIdSeed tmp = new InstanceIdSeed(c.get(i).instance.getId(), c.get(i).seed);
				courseValid = !(courseEnded && iis.contains(tmp));
				courseEnded = courseEnded || !iis.contains(tmp);
				if (!courseValid) {
					log("c Course invalid at instance number " + i + " instance: " + c.get(i).instance.getName());
					break;
				}
			}
			if (!courseValid) {
				log("c Removing solver configuration " + api.getSolverConfigName(sc.getIdSolverConfiguration()) + " caused by invalid course.");
				solverConfigs.remove(sc_index);
			}
		}
		if (solverConfigs.isEmpty()) {
			// no good solver configs in db
			log("c no solver configs found");
		}
		
		if (!solverConfigs.isEmpty()) {
			float cputime = 0.f;
			List<ExperimentResult> jobs = ExperimentResultDAO.getAllByExperimentId(parameters.getIdExperiment());
			HashMap<Integer, List<ExperimentResult>> scJobs = new HashMap<Integer, List<ExperimentResult>>();
			for (ExperimentResult job : jobs) {
				List<ExperimentResult> list = scJobs.get(job.getSolverConfigId());
				if (list == null) {
					list = new LinkedList<ExperimentResult>();
					scJobs.put(job.getSolverConfigId(), list);
				}
				list.add(job);
			}
			for (SolverConfiguration sc : solverConfigs) {
				List<ExperimentResult> list = scJobs.get(sc.getIdSolverConfiguration());
				if (list != null) {
					for (ExperimentResult job : list) {
						sc.putJob(job);
					}
				}
				sc.updateJobsStatus(api);
				cputime += sc.getTotalRuntime();
			}
			log("c added jobs for first solver configs with total runtime: " + cputime + "s.");
		}
		return solverConfigs;
	}

	private List<SolverConfiguration> getReferenceSolverConfigs() throws Exception {
		List<SolverConfiguration> res = new ArrayList<SolverConfiguration>();
		List<Integer> scIds = api.getSolverConfigurations(parameters.getIdExperiment(), "reference");
		for (Integer scId : scIds) {
			SolverConfiguration sc = new SolverConfiguration(scId, null, parameters.getStatistics());
			for (ExperimentResult job : api.getRuns(parameters.idExperiment, sc.getIdSolverConfiguration())) {
				sc.putJob(job);
			}
			res.add(sc);
		}
		return res;
	}
	
	public void addJobListener(JobListener listener) {
		jobListeners.add(listener);
	}
	
	public void removeJobListener(JobListener listener) {
		jobListeners.remove(listener);
	}
	
	/**
	 * Determines if the termination criteria holds
	 * 
	 * @return true if the termination criteria is met;
	 */
	private boolean terminate() {
		if (parameters.getMaxTuningTime() < 0)
			return false;
		// at the moment only the time budget is taken into consideration
		float exceed = this.cumulatedCPUTime - parameters.getMaxTuningTime();
		if (exceed > 0) {
			log("c Maximum allowed CPU time exceeded with: " + exceed + " seconds!!!");
			return true;
		} else
			return false;
	}

	public void expandParcoursSC(SolverConfiguration sc, int num) throws Exception {
		expandParcoursSC(sc, num, Integer.MAX_VALUE);
	}

	/**
	 * Add num additional runs/jobs from the parcours to the configuration sc.
	 * 
	 * @throws Exception
	 */
	public void expandParcoursSC(SolverConfiguration sc, int num, int priority) throws Exception {
		// TODO implement
		// fuer deterministische solver sollte man allerdings beachten,
		// dass wenn alle instanzen schon verwendet wurden das der parcours
		// nicht weiter erweitert werden kann.
		// fuer probabilistische solver kann der parcours jederzeit erweitert
		// werden, jedoch
		// waere ein Obergrenze sinvoll die als funktion der anzahl der
		// instanzen definiert werden sollte
		// z.B: 10*#instanzen

		int[] cputimelimits = new int[num];
		int[] wallclocktimelimits = new int[num];
		int[] priorities = new int[num];
		for (int i = 0; i < num; i++) {
			cputimelimits[i] = parameters.getJobCPUTimeLimit();
			wallclocktimelimits[i] = parameters.getJobWallClockTimeLimit();
			priorities[i] = priority;
		}

		List<Integer> ids = api.launchJob(parameters.getIdExperiment(), sc.getIdSolverConfiguration(), cputimelimits, wallclocktimelimits, num, priorities, rngSearch);
		for (ExperimentResult er : api.getJobsByIDs(ids).values())
			sc.putJob(er);// add the job to the solver configuration own job store

		statNumJobs += num;
	}

	/**
	 * adds random num new runs/jobs from the solver configuration "from" to the
	 * solver configuration "toAdd"
	 * 
	 * @throws Exception
	 */
	public int addRandomJob(int num, SolverConfiguration toAdd, SolverConfiguration from, int priority) throws Exception {
		toAdd.updateJobsStatus(api);
		from.updateJobsStatus(api);
		// compute a list with num jobs that "from" has computed and "toadd" has
		// not in its job list
		List<InstanceIdSeed> instanceIdSeedList = toAdd.getInstanceIdSeed(from, num, rngRacing);
		int generated = 0;
		DatabaseConnector.getInstance().getConn().setAutoCommit(false);
		try {
			for (InstanceIdSeed is : instanceIdSeedList) {
				statNumJobs++;
				int idJob = api.launchJob(parameters.getIdExperiment(), toAdd.getIdSolverConfiguration(), is.instanceId, BigInteger.valueOf(is.seed), parameters.getJobCPUTimeLimit(), priority);
				toAdd.putJob(api.getJob(idJob));
				generated++;
			}
		} finally {
			DatabaseConnector.getInstance().getConn().setAutoCommit(true);
		}
		return generated;
	}
	
	/**
	 * Generates and launches a new job for the solver configuration <code>to</code>.
	 * @param to
	 * @param seed
	 * @param instanceId
	 * @param priority
	 * @throws Exception
	 */
	public void addJob(SolverConfiguration to, int seed, int instanceId, int priority) throws Exception {
		DatabaseConnector.getInstance().getConn().setAutoCommit(false);
		try {
			statNumJobs++;
			Integer cputimelimit = instanceCPUTimeLimits.get(instanceId);
			if (cputimelimit == null) {
				cputimelimit = parameters.getJobCPUTimeLimit();
			}
			int wallclocklimit = parameters.getJobWallClockTimeLimit();
			int idJob = api.launchJob(parameters.getIdExperiment(), to.getIdSolverConfiguration(), instanceId, BigInteger.valueOf(seed), cputimelimit, wallclocklimit, priority);
			to.putJob(api.getJob(idJob));
		} finally {
			DatabaseConnector.getInstance().getConn().setAutoCommit(true);
		}
	}
	
	/**
	 * Updates the cpu time limit for the instance specified by <code>instanceId</code>.<br />
	 * <br />
	 * If <code>restart</code> is true:<br />
	 *   running jobs of the solver configurations in <code>scs</code> list will be restarted. <br />
	 *   finished jobs of the solver configurations in <code>scs</code> will be restarted if the limit is higher than the limit it was before. <br />
	 * <br />
	 * If <code>changeStatus</code> is true:<br />
	 *   the status of finished jobs will be changed according to the new time limit.<br />
	 * <br />
	 * Note: solver configurations will not be added to the listNewSC list.
	 * @param instanceId the id of the instance
	 * @param scs the list of the solver configurations
	 * @param restart
	 * @param changeStatus
	 * @return list of solver configurations for which jobs had to be reset.
	 */
	public List<SolverConfiguration> changeCPUTimeLimit(int instanceId, int limit, List<SolverConfiguration> scs, boolean restart, boolean changeStatus) throws Exception {
		if (limit < 1 && limit != -1)
			limit = 1;
		
		List<SolverConfiguration> res = new LinkedList<SolverConfiguration>();
		
		log("Changing CPUTimeLimit of instance " + instanceId + " to " + limit + "s.");
		
		List<Pair<Integer, Integer>> enableJobs = new LinkedList<Pair<Integer, Integer>>();
		List<ExperimentResult> addToCPUTime = new LinkedList<ExperimentResult>();
		
		instanceCPUTimeLimits.put(instanceId, limit);
		if (scs != null && (restart || changeStatus)) {
			for (SolverConfiguration sc : scs) {
				boolean jobsReset = false;
				for (ExperimentResult er : sc.getJobs()) {
					if (er.getInstanceId() == instanceId) {
						boolean rst = false;
						ExperimentResult apiER = api.getJob(er.getId());
						if (restart) {
							// disable job and remember priority
							enableJobs.add(new Pair<Integer, Integer>(er.getId(), er.getPriority()));
							api.setJobPriority(er.getId(), -1);
							
							apiER = api.getJob(er.getId());
							
							rst = (apiER.getCPUTimeLimit() < limit && !String.valueOf(apiER.getResultCode()).startsWith("1"));
							rst |= (apiER.getStatus().equals(StatusCode.RUNNING));
							rst |= (apiER.getStatus().equals(StatusCode.NOT_STARTED));
							
							if (rst) {
								if (apiER.getStatus().equals(StatusCode.RUNNING)) {
									api.killJob(er.getId());
									addToCPUTime.add(apiER);
								} else {
									api.restartJob(er.getId(), limit);
								}
								sc.jobReset(er);
								statNumRestartedJobs++;
								jobsReset = true;
							}
						}
						if (changeStatus && !rst) {
							er.setCPUTimeLimit(limit);
							apiER.setCPUTimeLimit(limit);
							if (er.getResultTime() > limit && er.getResultCode().isCorrect()) {
								log("Setting time limit exceeded to job " + er.getId() + ".");
								er.setStatus(StatusCode.TIMELIMIT);
								apiER.setStatus(StatusCode.TIMELIMIT);
								er.setResultCode(ResultCode.UNKNOWN);
								apiER.setResultCode(ResultCode.UNKNOWN);
							}
							// apiER is more up2date.
							// should be done by api?:
							Statement st = DatabaseConnector.getInstance().getConn().createStatement();
							st.executeUpdate("UPDATE ExperimentResults SET status = " + apiER.getStatus().getStatusCode() + ", resultCode = " + apiER.getResultCode().getResultCode() + ", CPUTimeLimit = " + apiER.getCPUTimeLimit() + " WHERE idJob = " + apiER.getId());
							st.close();
						}
					}
				}
				if (jobsReset) {
					res.add(sc);
				}
			}
		}
		
		if (!addToCPUTime.isEmpty()) {
			log("Waiting for clients to kill jobs..");
			
			float cputime = 0.f;
			for (ExperimentResult er : addToCPUTime) {
				ExperimentResult apiER = api.getJob(er.getId());
				while (apiER.getStatus().equals(StatusCode.RUNNING)) {
					Thread.sleep(1000);
					apiER = api.getJob(er.getId());
				}
				List<ExperimentResult> erTimeList = new LinkedList<ExperimentResult>();
				erTimeList.add(apiER);
				cputime += getResultTime(erTimeList);
				log("Restarting job " + er.getId() + " with new limit.");	
				api.restartJob(er.getId(), limit);
			}
			log("Done.");
			log("Adding " + cputime + "s to cumulated cpu time for restarted jobs.");
			cumulatedCPUTime += cputime;
		}
		
		// reset priority
		for (Pair<Integer, Integer> p : enableJobs) {
			api.setJobPriority(p.getFirst(), p.getSecond());
		}
		
		return res;
	}
	
	/**
	 * Returns the CPU time limit for this instance.<br/>
	 * If there is no CPU time limit set by the configurator then <code>parameters.getJobCPUTimeLimit</code> is returned.
	 * @param instanceId
	 * @return
	 */
	public int getCPUTimeLimit(int instanceId) {
		Integer res = instanceCPUTimeLimits.get(instanceId);
		if (res == null) {
			res = parameters.getJobCPUTimeLimit();
		}
		return res;
	}
	
	private float getResultTime(List<ExperimentResult> results) {
		float sum = 0.f;
		for (ExperimentResult res : results) 
			sum += res.getResultTime();
		return sum;
	}

	public void start() throws Exception {
		api.setOutput(parameters.idExperiment, "");
		log_db("AAC started.");
		if (parameters.getMaxCPUCount() == 0) {
			parameters.maxCPUCount = Integer.MAX_VALUE;
		}
		while (true) {
			int coreCount = api.getComputationCoreCount(parameters.getIdExperiment());
			if (coreCount >= parameters.getMinCPUCount() && coreCount <= parameters.getMaxCPUCount()) {
				break;
			}
			log("c Current core count: " + coreCount);
			log("c Waiting for #cores to satisfy: " + parameters.getMinCPUCount() + " <= #cores <= " + parameters.getMaxCPUCount());
			Thread.sleep(10000);
		}
		startTime = System.currentTimeMillis();
		lastStats = 0;
		
		log("c Starting AAC.");
		cumulatedCPUTime = 0.f;
		
		// determine first solver configurations (defaults)
		List<SolverConfiguration> firstSCs = initializeFirstSCs();
		// determine reference solver configurations for search method
		List<SolverConfiguration> referenceSCs = getReferenceSolverConfigs();
		// create a copy for racing method
		LinkedList<SolverConfiguration> referenceSCsCopy = new LinkedList<SolverConfiguration>();
		referenceSCsCopy.addAll(referenceSCs);
		
		// add solver configurations to global solver configuration list
		solverConfigs.addAll(firstSCs);
		solverConfigs.addAll(referenceSCs);
		
		// update the solver configuration status without adding time to cumulatedCPUTime
		for (SolverConfiguration sc : solverConfigs) {
			sc.updateJobsStatus(api);
		}
		
		// create search and racing instances
		search = (SearchMethods) searchClass.getDeclaredConstructors()[0].newInstance(this, api, rngSearch, parameters, firstSCs, referenceSCs);
		racing = (RacingMethods) racingClass.getDeclaredConstructors()[0].newInstance(this, rngRacing, api, parameters, firstSCs, referenceSCsCopy);

		// print parameters
		log("c Starting the EAAC configurator with following settings:");
		log("c ---------------------------------");
		List<String> params = parameters.getParameters();
		params.add("%");
		params.addAll(search.getParameters());
		params.add("%");
		params.addAll(racing.getParameters());
		params.add("%");
		for (String p : params) {
			log("c " + p);
			log_db(p);
		}

		// user specified racing solver configurations
		List<SolverConfiguration> racingScs = new LinkedList<SolverConfiguration>();
		
		/**
		 * error checking for parcours. Needed? What if we don't use the
		 * parcours?
		 */
		int num_instances = ConfigurationScenarioDAO.getConfigurationScenarioByExperimentId(parameters.getIdExperiment()).getCourse().getInitialLength();
		if (!(search instanceof edacc.configurator.aac.search.Matrix) && num_instances == 0) {
			log("e Error: no instances in course.");
			return;
		}
		HashMap<Integer, SolverConfiguration> lastBestSCs = new HashMap<Integer, SolverConfiguration>();
		while (!terminate()) {
			// retrieve best solver configurations from racing method and update the solver configuration names
			// of old best scs and new best scs
			List<SolverConfiguration> racingBestSCs = racing.getBestSolverConfigurations(null);
			HashMap<Integer, SolverConfiguration> notBestSCs = new HashMap<Integer, SolverConfiguration>();
			notBestSCs.putAll(lastBestSCs);
			lastBestSCs.clear();

			for (SolverConfiguration config : racingBestSCs) {
				if (!notBestSCs.containsKey(config.getIdSolverConfiguration())) {
					updateSolverConfigName(config, true);
				}
				lastBestSCs.put(config.getIdSolverConfiguration(), config);
				notBestSCs.remove(config.getIdSolverConfiguration());
			}

			for (SolverConfiguration sc : notBestSCs.values()) {
				updateSolverConfigName(sc, false);
			}
			notBestSCs.clear();

			
			// ----INCREASE PARALLELISM----
			// compute the number of new solver configs that should be generated
			int generateNumSC = 0;
			if (!terminate()) {
				generateNumSC = racing.computeOptimalExpansion(api.getComputationCoreCount(parameters.getIdExperiment()), api.getComputationJobCount(parameters.getIdExperiment()), listNewSC.size());
			}

			// determine and add race solver configurations
			for (SolverConfiguration sc : getRaceSolverConfigurations()) {
				log("c Found RACE solver configuration: " + sc.getIdSolverConfiguration() + " - " + sc.getName() + ", will be added later..");
				racingScs.add(sc);
			}

			boolean generatedSCs = false;
			// if the number of sc to be generated together with the number of
			// sc so far generated excceds total allowed number
			if (parameters.getMaxNumSC() >= 0 && this.statNumSolverConfigs + generateNumSC > parameters.getMaxNumSC()) {
				// then limit the number of new sc
				generateNumSC = parameters.getMaxNumSC() - this.statNumSolverConfigs; 
			}
			
			if (generateNumSC > 0) {
				List<SolverConfiguration> tmpList = new LinkedList<SolverConfiguration>();
				// add (user defined) racing solver configurations first
				while (!racingScs.isEmpty() && generateNumSC > 0) {
					log("c adding racing solver configuration");
					tmpList.add(racingScs.get(0));
					racingScs.remove(0);
					generateNumSC--;
				}
				
				if (generateNumSC > 0) {
					DatabaseConnector.getInstance().getConn().setAutoCommit(false);
					try {
						tmpList.addAll(search.generateNewSC(generateNumSC));
					} finally {
						DatabaseConnector.getInstance().getConn().setAutoCommit(true);
					}
				}
				if (tmpList.size() == 0 && generateNumSC == 0) {
					log("e Error: no solver configs generated in first iteration.");
					return;
				}
				if (!tmpList.isEmpty()) {
					for (SolverConfiguration sc : tmpList) {
						statNumSolverConfigs++;
						sc.setNumber(statNumSolverConfigs);
					}
					solverConfigs.addAll(tmpList);
					racing.solverConfigurationsCreated(tmpList);
					for (SolverConfiguration sc : tmpList) {
						updateSolverConfigName(sc, false);
					}
				}
				log("c " + statNumSolverConfigs + "SC -> Generated " + tmpList.size() + " new solver configurations");
				generatedSCs = (!tmpList.isEmpty());
			}
			if (!generatedSCs) {
				int sleepTime = parameters.pollingInterval;
				if (api instanceof APISimulation) {
					((APISimulation) api).incrementTime(sleepTime);
				} else {
					Thread.sleep(sleepTime);
				}
			}

			if (listNewSC.isEmpty()) {
				log("c no solver configs in list: exiting");
				break;
			}
			List<ExperimentResult> finishedJobs = new LinkedList<ExperimentResult>();
			List<SolverConfiguration> finishedSCs = new LinkedList<SolverConfiguration>();
			for (SolverConfiguration sc : listNewSC.values()) {
				List<ExperimentResult> scFinishedJobs = sc.updateJobsStatus(api);
				
				if (!scFinishedJobs.isEmpty()) {
					sc.nameUpdated = true;
					finishedJobs.addAll(scFinishedJobs);
				}
				if (sc.getNumNotStartedJobs() + sc.getNumRunningJobs() == 0) {
					finishedSCs.add(sc);
				} else {
					/*
					 * if (useCapping) { // ---CAPPING RUNS OF BAD CONFIGS--- //
					 * wenn sc schon eine kummulierte Laufzeit der // beendeten
					 * // jobs > der aller beendeten jobs von best // kann man
					 * sc vorzeitig beedenden! geht nur wenn // man parX hat! if
					 * ((parameters.getStatistics().getCostFunction() instanceof
					 * edacc.api.costfunctions.PARX) ||
					 * (parameters.getStatistics().getCostFunction() instanceof
					 * edacc.api.costfunctions.Average)) // TODO: minimieren /
					 * maximieren /negative // kosten if (sc.getCumulatedCost()
					 * > racing.getBestSC().getCumulatedCost()) { log("c " +
					 * sc.getCumulatedCost() + " >" +
					 * racing.getBestSC().getCumulatedCost()); log("c " +
					 * sc.getJobCount() + " > " +
					 * racing.getBestSC().getJobCount()); // kill all running
					 * jobs of the sc config! List<ExperimentResult> jobsToKill
					 * = sc.getJobs(); for (ExperimentResult j : jobsToKill) {
					 * this.api.killJob(j.getId()); }
					 * api.removeSolverConfig(sc.getIdSolverConfiguration());
					 * listNewSC.remove(i); log("c -----Config capped!!!"); } //
					 * sc.killRunningJobs // api.removeSolverConfig(sc.) }
					 */
				}

			}
			// update cumulated cpu time
			cumulatedCPUTime += getResultTime(finishedJobs);
			
			// remove finished solver configurations
			for (SolverConfiguration sc : finishedSCs) {
				listNewSC.remove(sc.getIdSolverConfiguration());
			}
			
			// notify racing method
			racing.solverConfigurationsFinished(finishedSCs);
			
			// notify job listeners
			notifyJobListeners(finishedJobs);
			
			// update solver configuration names
			for (SolverConfiguration sc : solverConfigs) {
				boolean currentBest = lastBestSCs.containsKey(sc.getIdSolverConfiguration());
				if (sc.nameUpdated || currentBest != sc.wasBest) {
					if (currentBest) {
						updateSolverConfigName(sc, true);
					} else {
						updateSolverConfigName(sc, false);
					}
					sc.wasBest = currentBest;
				}
			}
		}
		// aac main procedure finished, notify search and racing methods
		search.searchFinished();
		racing.raceFinished();
		RInterface.shutdown();
	}

	public void addSolverConfigurationToListNewSC(SolverConfiguration sc) {
		this.listNewSC.put(sc.getIdSolverConfiguration(), sc);
	}

	public void updateJobsStatus(SolverConfiguration sc) throws Exception {
		List<ExperimentResult> finishedJobs = sc.updateJobsStatus(api);
		cumulatedCPUTime += getResultTime(finishedJobs);
		notifyJobListeners(finishedJobs);
	}
	
	private void notifyJobListeners(List<ExperimentResult> jobs) throws Exception {
		for (JobListener listener : jobListeners) {
			listener.jobsFinished(jobs);
		}
	}
	/**
	 * Returns wall time in seconds.
	 * @return wall time
	 */
	public float getWallTime() {
		return (System.currentTimeMillis() - startTime) / 1000.f;
	}

	/**
	 * Determines the solver configurations for which the user has set the race
	 * hint.<br/>
	 * Does not return solver configurations which have runs.
	 * 
	 * @return
	 * @throws Exception
	 */
	public List<SolverConfiguration> getRaceSolverConfigurations() throws Exception {
		List<SolverConfiguration> res = new LinkedList<SolverConfiguration>();
		// get solver config ids
		List<Integer> solverConfigIds = api.getSolverConfigurations(parameters.getIdExperiment(), "race");
		// reset hint field
		for (Integer i : solverConfigIds) {
			api.setSolverConfigurationHint(parameters.getIdExperiment(), i, "");
		}
		// create solver configs and return them
		for (Integer i : solverConfigIds) {
			if (api.getRuns(parameters.getIdExperiment(), i).isEmpty()) {
				try {
					SolverConfiguration sc = new SolverConfiguration(i, api.getParameterConfiguration(parameters.getIdExperiment(), i), parameters.getStatistics());
					sc.setNameSearch(api.getSolverConfigName(i));
					res.add(sc);
				} catch (Exception e) {
					log("c getRaceSolverConfigurations(): invalid solver config: " + i + " Exception:");
					for (StackTraceElement element : e.getStackTrace()) {
						log("c " + element.toString());
					}
				}
			}
		}
		return res;
	}

	public void shutdown() {
		log("c Solver Configurations generated: " + this.statNumSolverConfigs);
		log("c Jobs generated: " + statNumJobs);
		log("c Number of comparision performed with the racing method: " + racing.getNumCompCalls());
		log("c Total runtime of the execution system (CPU time): " + cumulatedCPUTime);
		log("c Best Configurations found: ");

		for (SolverConfiguration bestSC : racing.getBestSolverConfigurations(null)) {
			try {
				log_db("ID :" + bestSC.getIdSolverConfiguration());
			} catch (Exception ex) {
			}
			
			log("c ID :" + bestSC.getIdSolverConfiguration());
			try {
				try {
					log_db("Canonical name: " + api.getCanonicalName(parameters.getIdExperiment(), bestSC.getParameterConfiguration()));
				} catch (Exception ex) {
				}
				
				log("c Canonical name: " + api.getCanonicalName(parameters.getIdExperiment(), bestSC.getParameterConfiguration()));
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (api instanceof APISimulation) {
				((APISimulation) api).printStats();
			}

			if (parameters.idExperimentEvaluation > 0) {
				String name = ("".equals(parameters.evaluationSolverConfigName) ? "" : parameters.evaluationSolverConfigName + " ") + getSolverConfigName(bestSC, true);
				log("c adding " + bestSC + " to experiment " + parameters.idExperimentEvaluation + " with name " + name);
				try {
					api.createSolverConfig(parameters.idExperimentEvaluation, bestSC.getParameterConfiguration(), name);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}

		log("c halt.");
		api.disconnect();
	}

	public void updateSolverConfigName(SolverConfiguration sc, boolean best) throws Exception {
		api.updateSolverConfigurationName(sc.getIdSolverConfiguration(), getSolverConfigName(sc, best));
		sc.nameUpdated = false;
	}

	/*public String getSolverConfigName(SolverConfiguration sc, boolean best) {
		return (best ? "_ BEST " : "") + (sc.getIncumbentNumber() == -1 ? "" : -sc.getIncumbentNumber()) + " " + sc.getNumber() + " " + (sc.getName() != null ? " " + sc.getName() + " " : "") + " Runs: " + sc.getNumFinishedJobs() + "/" + sc.getJobCount() + " ID: " + sc.getIdSolverConfiguration();
	}*/
	public String getSolverConfigName(SolverConfiguration sc, boolean best) {
		//System.out.println("Altering the name of a solverConfig to: "+sc.getName());
		return (best ? "_ BEST " : "") + sc.getName();
	}

	public void sleep(long millis) throws InterruptedException {
		if (api instanceof APISimulation) {
			((APISimulation) api).incrementTime(millis);
		} else {
			Thread.sleep(millis);
		}
	}

	public boolean listNewSCContains(SolverConfiguration sc) {
		return listNewSC.containsKey(sc.getIdSolverConfiguration());
	}
	
	/**
	 * information about all recent solver configurations with running jobs or jobs waiting for execution
	 * 
	 * @return a map where the solver configuration ID references the related solver configuration
	 */
	public HashMap<Integer, SolverConfiguration> returnListNewSC() {
		return listNewSC;
	}

	/**
	 * Parses the configuration file and starts the configurator.
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		Parameters params = new Parameters();
		if (args.length < 1) {
			System.out.println("% Missing configuration file. Use java -jar PROAR.jar <config file path> [<key=value>]*");
			System.out.println("% If <key=value> pairs are given, config parameters will be overwritten.");
			for (String p : params.getParameters()) {
				System.out.println(p);
			}
			return;
		}
		// TODO: parameter to create a generic config file
		
		Scanner scanner = new Scanner(new File(args[0]));

		List<Pair<String, String>> paramvalues = new LinkedList<Pair<String, String>>();
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			if (line.trim().startsWith("%") || "".equals(line.trim()))
				continue;
			String[] keyval = line.split("=");
			if (keyval.length != 2) {
				System.err.println("Error while parsing: '" + line + "' in config. exiting.");
				return;
			}
			String key = keyval[0].trim();
			String value = keyval[1].trim();
			paramvalues.add(new Pair<String, String>(key, value));
		}
		scanner.close();
		for (int i = 1; i < args.length; i++) {
			String[] keyval = args[i].split("=");
			if (keyval.length != 2) {
				System.err.println("Error while parsing: '" + args[i] + "' in parameters. exiting.");
				return;
			}
			paramvalues.add(new Pair<String, String>(keyval[0].trim(), keyval[1].trim()));
		}

		if (!params.parseParameters(paramvalues)) {
			System.out.println("Error while parsing parameters; exiting.");
			return;
		}
		AAC configurator = new AAC(params);
		configurator.start();
		configurator.shutdown();
	}
	
	/**
	 * Logs <code>message</code> to standard output.
	 * @param message the message
	 */
	public synchronized void log(String message) {
		if (System.currentTimeMillis() - lastStats > 120*1000) {
			lastStats = System.currentTimeMillis();
			log("Walltime: " + getWallTime() + ",CPUTime: " + cumulatedCPUTime + ",NumSC: " + statNumSolverConfigs + ",NumJobs: " + statNumJobs);
		}
		System.out.println("[Date: " + new Date() + "] " + message);
	}

	/**
	 * Logs <code>message</code> to the database.
	 * @param message the message
	 * @throws Exception an exception is thrown on db errors
	 */
	public void log_db(String message) throws Exception {
		api.addOutput(parameters.getIdExperiment(), "[Date: " + new Date() + ",Walltime: " + getWallTime() + ",CPUTime: " + cumulatedCPUTime + ",NumSC: " + statNumSolverConfigs + ",NumJobs: " + statNumJobs + "] " + message + "\n");
	}
	
	
	public static float[] calculateFeatures(int instanceId, File featureFolder, File featuresCacheFolder) throws IOException, NoConnectionToDBException, InstanceClassMustBeSourceException, InstanceNotInDBException, InterruptedException, SQLException {
		Properties properties = new Properties();
		File propFile = new File(featureFolder, "features.properties");
		FileInputStream in = new FileInputStream(propFile);
		properties.load(in);
		in.close();
		String featuresRunCommand = properties.getProperty("FeaturesRunCommand");
		String featuresParameters = properties.getProperty("FeaturesParameters");
		String[] features = properties.getProperty("Features").split(",");
		Instance instance = InstanceDAO.getById(instanceId);
		
		float[] res = new float[features.length];
		
		File cacheFile = null;
		if (featuresCacheFolder != null) {
			cacheFile = new File(featuresCacheFolder, instance.getMd5());
		}
		if (cacheFile != null) {
			featuresCacheFolder.mkdirs();
			
			if (cacheFile.exists()) {
				System.out.println("Found cached features.");
				try {
					BufferedReader br = new BufferedReader(new FileReader(cacheFile));
					String[] featuresNames = br.readLine().split(",");
					String[] f_str = br.readLine().split(",");
					br.close();
					if (f_str.length != features.length || !Arrays.equals(features, featuresNames)) {
						System.err.println("Features changed? Recalculating!");
					} else {
						for (int i = 0; i < res.length; i++) {
							res[i] = Float.parseFloat(f_str[i]);
						}
						return res;
					}
					
				} catch (Exception ex) {
					System.err.println("Could not load cache file: " + cacheFile.getAbsolutePath() + ". Recalculating features.");
				}
			}
		}
		
		new File("tmp").mkdir();
		File f = File.createTempFile("instance"+instanceId, "instance"+instanceId, new File("tmp"));
		InstanceDAO.getBinaryFileOfInstance(instance, f, false, false);
		
		
		//System.out.println("Call: " + featuresRunCommand + " " + featuresParameters + " " + f.getAbsolutePath());
		
		Process p = Runtime.getRuntime().exec(featuresRunCommand + " " + featuresParameters + " " + f.getAbsolutePath(), null, featureFolder);
		BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
		
		String line;
		while (((line = br.readLine()) != null) && line.startsWith("c "));
		
		String[] features_str = br.readLine().split(",");
		for (int i = 0; i < features_str.length; i++) {
			res[i] = Float.valueOf(features_str[i]);
		}
		br.close();
		p.destroy();
		f.delete();
		
		//System.out.println("Result: " + Arrays.toString(res));
		if (cacheFile != null) {
			cacheFile.delete();
			BufferedWriter bw = new BufferedWriter(new FileWriter(cacheFile));
			bw.write(properties.getProperty("Features") + '\n');
			for (int i = 0; i < res.length; i++) {
				bw.write(String.valueOf(res[i]));
				if (i != res.length - 1) {
					bw.write(',');
				}
			}
			bw.write('\n');
			bw.close();
		}
		
		return res;
	}
	
	public static String[] getFeatureNames(File featureFolder) throws Exception {
        Properties properties = new Properties();
        File propFile = new File(featureFolder, "features.properties");
        FileInputStream in = new FileInputStream(propFile);
        properties.load(in);
        in.close();
        return properties.getProperty("Features").split(",");
	}
}
