package edacc.configurator.aac;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import edacc.api.API;
import edacc.api.APIImpl;
import edacc.configurator.aac.racing.RacingMethods;
import edacc.configurator.aac.search.SearchMethods;
import edacc.model.ConfigurationScenarioDAO;
import edacc.model.Course;
import edacc.model.DatabaseConnector;
import edacc.model.ExperimentDAO;
import edacc.model.ExperimentResult;
import edacc.model.InstanceSeed;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;

public class AAC {
	private static final boolean useCapping = false;

	private String experimentName;

	/** All parameters for proar */
	private Parameters parameters;
	
	private API api;
	private Random rng;
	private SearchMethods methods;
	private RacingMethods racing;

	/** inidcates if the results of the solver of deterministic nature or not */
	private boolean deterministic;

	private long startTime;

	/**
	 * total cumulated time of all jobs the configurator has started so far in
	 * seconds
	 */
	private float cumulatedCPUTime;

	/**
	 * List of all NEW solver configuration that are going to be raced against
	 * the best
	 */
	private HashMap<Integer, SolverConfiguration> listNewSC;

	/**
	 * If within the Experiment there is an other Solver that the configurator
	 * has to beat then raceCondition should be true. The configurator will then
	 * try to use the information about the results of this solver to try to
	 * beat him according to the statistic and metric function.
	 * */
	private boolean raceCondition;

	/**
	 * If raceCondigition == true then there has to be a competitior which the
	 * configurator will try to beat!
	 */
	private SolverConfiguration competitor;


	private int statNumSolverConfigs;
	private int statNumJobs;

	private ParameterGraph graph;

	public AAC(Parameters params) throws Exception {
		api = new APIImpl();
		api.connect(params.hostname, params.port, params.database, params.user, params.password);
		this.graph = api.loadParameterGraphFromDB(params.idExperiment);
		params.setStatistics(api.costFunctionByName(params.costFunc), params.minimize);
		rng = new edacc.util.MersenneTwister(params.seed);
		listNewSC = new HashMap<Integer, SolverConfiguration>();
		this.statNumSolverConfigs = 0;
		this.statNumJobs = 0;
		this.parameters = params;
		methods = (SearchMethods) ClassLoader.getSystemClassLoader().loadClass("edacc.configurator.aac.search." + params.algorithm).getDeclaredConstructors()[0].newInstance(api, rng, parameters);
		racing = (RacingMethods) ClassLoader.getSystemClassLoader().loadClass("edacc.configurator.aac.racing." + params.racing).getDeclaredConstructors()[0].newInstance(this, api, parameters);
	}

	/**
	 * Checks if there are solver configurations in the experiment that would
	 * match the configuration scenario if there are more than one such
	 * configuration it will pick the best one as the best configuration found
	 * so far
	 * 
	 * @throws Exception
	 */
	private SolverConfiguration initializeBest() throws Exception {
		// TODO: the best one might not match the configuration scenario
		// graph.validateParameterConfiguration(config) should test this,
		// but is currently not implemented and will return false.
		if (methods instanceof edacc.configurator.aac.search.Matrix) {
			edacc.configurator.aac.search.Matrix m = (edacc.configurator.aac.search.Matrix) methods;
			return m.getFirstSC();
		}
		
		
		List<Integer> solverConfigIds = api.getSolverConfigurations(parameters.getIdExperiment(), "default");
		if (solverConfigIds.isEmpty()) {
			solverConfigIds = api.getSolverConfigurations(parameters.getIdExperiment());
			log("c Found " + solverConfigIds.size() + " solver configuration(s)");
		} else {
			log("c Found " + solverConfigIds.size() + " default configuration(s)");
		}
		List<SolverConfiguration> solverConfigs = new LinkedList<SolverConfiguration>();
		int maxRun = -1;
		for (int id : solverConfigIds) {
			int runCount = api.getNumJobs(id);
			if (runCount > maxRun) {
				solverConfigs.clear();
				maxRun = runCount;
			}
			if (runCount == maxRun) {
				ParameterConfiguration pConfig = api.getParameterConfiguration(parameters.getIdExperiment(), id);
				solverConfigs.add(new SolverConfiguration(id, pConfig, parameters.getStatistics()));
			}
		}
		log("c " + solverConfigs.size() + " solver configs with max run");

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

		log("c Determining best solver configuration from " + solverConfigs.size() + " solver configurations");

		if (solverConfigs.isEmpty()) {
			// no good solver configs in db
			log("c Generating a random solver configuration");

			ParameterConfiguration config = graph.getRandomConfiguration(rng);
			int idSolverConfiguration = api.createSolverConfig(parameters.getIdExperiment(), config, "First Random Configuration " + api.getCanonicalName(parameters.getIdExperiment(), config));
			return new SolverConfiguration(idSolverConfiguration, api.getParameterConfiguration(parameters.getIdExperiment(), idSolverConfiguration), parameters.getStatistics());
		} else {
			Collections.sort(solverConfigs);
			SolverConfiguration bestSC = solverConfigs.get(solverConfigs.size() - 1);
			log("c Best solver configuration: " + api.getSolverConfigName(bestSC.getIdSolverConfiguration()));
			return bestSC;
		}
	}

	/**
	 * Recheck if there is a configuration in the Experiment that was created by
	 * someone else(other configurator or human) and is better then the current
	 * bestSC.
	 * 
	 * @return solver configuration ID that is better than the current bestSC or
	 *         -1 else
	 */
	private int recheck() {
		// TODO : implement
		return -1;
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

	/**
	 * Add num additional runs/jobs from the parcours to the configuration sc.
	 * 
	 * @throws Exception
	 */
	public void expandParcoursSC(SolverConfiguration sc, int num) throws Exception {
		// TODO implement
		// fuer deterministische solver sollte man allerdings beachten,
		// dass wenn alle instanzen schon verwendet wurden das der parcours
		// nicht weiter erweitert werden kann.
		// fuer probabilistische solver kann der parcours jederzeit erweitert
		// werden, jedoch
		// waere ein Obergrenze sinvoll die als funktion der anzahl der
		// instanzen definiert werden sollte
		// z.B: 10*#instanzen
		DatabaseConnector.getInstance().getConn().setAutoCommit(false);
		try {
			for (int i = 0; i < num; i++) {
				statNumJobs++;
				if (methods instanceof edacc.configurator.aac.search.Matrix) {
					edacc.configurator.aac.search.Matrix m = (edacc.configurator.aac.search.Matrix) methods;
					InstanceSeed is = m.course.get(sc.getJobCount());
					ExperimentResult res = m.getJob(sc.getIdSolverConfiguration(), is.instance.getId(), is.seed);
					if (res == null) {
						throw new IllegalArgumentException("No job.");
					}
					sc.putJob(res);
				} else {
					int idJob = api.launchJob(parameters.getIdExperiment(), sc.getIdSolverConfiguration(), parameters.getJobCPUTimeLimit(), rng);
					api.setJobPriority(idJob, Integer.MAX_VALUE);
					sc.putJob(api.getJob(idJob)); // add the job to the solver
					// configuration own job store
				}
			}
		} finally {
			DatabaseConnector.getInstance().getConn().setAutoCommit(true);
		}
	}

	/**
	 * adds random num new runs/jobs from the solver configuration "from" to the
	 * solver configuration "toAdd"
	 * 
	 * @throws Exception
	 */
	public int addRandomJob(int num, SolverConfiguration toAdd, SolverConfiguration from, int priority) throws Exception {
		toAdd.updateJobsStatus();
		from.updateJobsStatus();
		// compute a list with num jobs that "from" has computed and "toadd" has
		// not in its job list
		List<InstanceIdSeed> instanceIdSeedList = toAdd.getInstanceIdSeed(from, num, rng);
		int generated = 0;
		DatabaseConnector.getInstance().getConn().setAutoCommit(false);
		try {
			for (InstanceIdSeed is : instanceIdSeedList) {
				statNumJobs++;
				if (methods instanceof edacc.configurator.aac.search.Matrix) {
					edacc.configurator.aac.search.Matrix m = (edacc.configurator.aac.search.Matrix) methods;
					ExperimentResult res = m.getJob(toAdd.getIdSolverConfiguration(), is.instanceId, is.seed);
					if (res == null) {
						throw new IllegalArgumentException("No job.");
					}
					toAdd.putJob(res);
				} else {
					int idJob = api.launchJob(parameters.getIdExperiment(), toAdd.getIdSolverConfiguration(), is.instanceId, BigInteger.valueOf(is.seed), parameters.getJobCPUTimeLimit(), priority);
					toAdd.putJob(api.getJob(idJob));
				}
				generated++;
			}
		} finally {
			DatabaseConnector.getInstance().getConn().setAutoCommit(true);
		}
		return generated;
	}

	public void start() throws Exception {
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
		log("c Starting PROAR.");
		startTime = System.currentTimeMillis();
		experimentName = ExperimentDAO.getById(parameters.getIdExperiment()).getName();
		// first initialize the best individual if there is a default or if
		// there are already some solver configurations in the experiment
		cumulatedCPUTime = 0.f;
		SolverConfiguration firstSC = initializeBest();// TODO: mittels dem
														// Classloader
														// überschreiben
		if (firstSC == null) {
			throw new RuntimeException("best not initialized");
		}
		firstSC.updateJobsStatus(); // don't add best scs time to cumulatedCPUTime
		
		racing.initFirstSC(firstSC);

		/** error checking for parcours. Needed? What if we don't use the parcours? */
		int num_instances = ConfigurationScenarioDAO.getConfigurationScenarioByExperimentId(parameters.getIdExperiment()).getCourse().getInitialLength();
		if (!(methods instanceof edacc.configurator.aac.search.Matrix) && num_instances == 0) {
			log("e Error: no instances in course.");
			return;
		}
		SolverConfiguration lastBest = null;
		while (!terminate()) {
			// bestSC.updateJobsStatus(); das ist glaube ich doppelt gemoppelt
			// denn im übernächsten if wird auf jeden Fall
			// bestSC.updateJobsSatus() ausgeführt!
			// expand the parcours of the bestSC
			if (racing.getBestSC() != lastBest) {
				updateSolverConfigName(racing.getBestSC(), true);
				if (lastBest != null)
					updateSolverConfigName(lastBest, false);
				lastBest = racing.getBestSC();
			}
			// update the cost of the configuration in the EDACC solver
			// configuration tables
			api.updateSolverConfigurationCost(racing.getBestSC().getIdSolverConfiguration(), racing.getBestSC().getCost(), parameters.getStatistics().getCostFunction());

			int generateNumSC = 0;
			// ----INCREASE PARALLELISM----
			// compute the number of new solver configs that should be generated
			if (!terminate()) {
				if (methods instanceof edacc.configurator.aac.search.Matrix) {
					// 8 cores, 0 jobs currently computing
					generateNumSC = racing.computeOptimalExpansion(8, 0, listNewSC.size());
				} else {
					generateNumSC = racing.computeOptimalExpansion(api.getComputationCoreCount(parameters.getIdExperiment()), api.getComputationJobCount(parameters.getIdExperiment()), listNewSC.size());
				}
			}
			
			// determine and add race solver configurations
			for (SolverConfiguration sc : getRaceSolverConfigurations()) {
				log("c Found RACE solver configuration: " + sc.getIdSolverConfiguration() + " - " + sc.getName());
				sc.setNumber(++statNumSolverConfigs);
				addRandomJob(parameters.getMinRuns(), sc, racing.getBestSC(), Integer.MAX_VALUE - sc.getNumber());
				updateSolverConfigName(sc, false);
				listNewSC.put(sc.getIdSolverConfiguration(), sc);
			}

			if (generateNumSC > 0) {
				int numNewSC = 0;
				if (generateNumSC >= 210) {
					generateNumSC -= 210;
					numNewSC = 210;
				} else {
					numNewSC = generateNumSC;
					generateNumSC = 0;
				}

				List<SolverConfiguration> tmpList;
				DatabaseConnector.getInstance().getConn().setAutoCommit(false);
				try {
					tmpList = methods.generateNewSC(numNewSC, racing.getBestSC());
				} finally {
					DatabaseConnector.getInstance().getConn().setAutoCommit(true);
				}
				if (tmpList.size() == 0 && numNewSC == 0) {
					log("e Error: no solver configs generated in first iteration.");
					return;
				}
				if (!tmpList.isEmpty()) {
					for (SolverConfiguration sc : tmpList) {
						statNumSolverConfigs++;
						sc.setNumber(statNumSolverConfigs);
					}
					racing.solverConfigurationsCreated(tmpList);
				}
				log("c " + statNumSolverConfigs + "SC -> Generated " + numNewSC + " new solver configurations");
			} else {
				Thread.sleep(2500);
			}
			// TODO : implement a method that determines an optimal wait
			// according to the runtimes of the jobs!

			if (listNewSC.isEmpty()) {
				log("c no solver configs in list: exiting");
				break;
			}

			List<SolverConfiguration> finishedSCs = new LinkedList<SolverConfiguration>();
			for (SolverConfiguration sc : listNewSC.values()) {
				// take only solver configs of the current level into
				// consideration
				// there might be some configs for the next level already
				// generated and evaluated
				cumulatedCPUTime += sc.updateJobsStatus();
				if (sc.getNumNotStartedJobs() + sc.getNumRunningJobs() == 0) {
					api.updateSolverConfigurationCost(sc.getIdSolverConfiguration(), sc.getCost(), parameters.getStatistics().getCostFunction());
					if (sc == racing.getBestSC()) {
						updateSolverConfigName(sc, true);
					} else {
						updateSolverConfigName(sc, false);
					}
					finishedSCs.add(sc);
				} else {
					/*if (useCapping) {
						// ---CAPPING RUNS OF BAD CONFIGS---
						// wenn sc schon eine kummulierte Laufzeit der
						// beendeten
						// jobs > der aller beendeten jobs von best
						// kann man sc vorzeitig beedenden! geht nur wenn
						// man parX hat!
						if ((parameters.getStatistics().getCostFunction() instanceof edacc.api.costfunctions.PARX) || (parameters.getStatistics().getCostFunction() instanceof edacc.api.costfunctions.Average))
							// TODO: minimieren / maximieren /negative
							// kosten
							if (sc.getCumulatedCost() > racing.getBestSC().getCumulatedCost()) {
								log("c " + sc.getCumulatedCost() + " >" + racing.getBestSC().getCumulatedCost());
								log("c " + sc.getJobCount() + " > " + racing.getBestSC().getJobCount());
								// kill all running jobs of the sc config!
								List<ExperimentResult> jobsToKill = sc.getJobs();
								for (ExperimentResult j : jobsToKill) {
									this.api.killJob(j.getId());
								}
								api.removeSolverConfig(sc.getIdSolverConfiguration());
								listNewSC.remove(i);
								log("c -----Config capped!!!");
							}
						// sc.killRunningJobs
						// api.removeSolverConfig(sc.)
					}*/
				}

			}
			for (SolverConfiguration sc : finishedSCs) {
				listNewSC.remove(sc.getIdSolverConfiguration());
			}
			racing.solverConfigurationsFinished(finishedSCs);
			/*
			 * updateSolverConfigName(bestSC, false);
			 * System.out.println("c Determining the new best solver config from "
			 * + listBestSC.size() + " solver configurations."); if
			 * (listBestSC.size() > 0) { for (SolverConfiguration sc :
			 * listBestSC) {
			 * 
			 * if (sc.compareTo(bestSC) > 0) { // if bestsc is from the same
			 * level as sc then remove // bestSC from DB as we want to keep only
			 * // 1 best from each level!
			 * 
			 * //if (deleteSolverConfigs && (bestSC.getLevel() == sc.getLevel())
			 * // && (this.algorithm.equals("ROAR") ||
			 * (this.algorithm.equals("MB")))) { //
			 * api.removeSolverConfig(bestSC.getIdSolverConfiguration()); //}
			 * bestSC = sc;
			 * 
			 * } else { //if (deleteSolverConfigs &&
			 * (this.algorithm.equals("ROAR") || (this.algorithm.equals("MB"))))
			 * // api.removeSolverConfig(sc.getIdSolverConfiguration()); } } }
			 */
			/*
			 * if (!listBestSC.isEmpty()) { if (bestSC.getJobCount() <
			 * maxParcoursExpansionFactor * num_instances) { int exp =
			 * Math.min(maxParcoursExpansionFactor * num_instances -
			 * bestSC.getJobCount(), listBestSC.size());
			 * expandParcoursSC(bestSC, exp); } }
			 */
			// updateSolverConfigName(bestSC, true); not neccessary because we
			// update this in the beginning of the loop!
		}

	}
	
	public void addSolverConfigurationToListNewSC(SolverConfiguration sc) {
		this.listNewSC.put(sc.getIdSolverConfiguration(), sc);
	}
	
	public void updateJobsStatus(SolverConfiguration sc) throws Exception {
		cumulatedCPUTime += sc.updateJobsStatus();
	}

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
					sc.setName(api.getSolverConfigName(i));
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
		log("c Total runtime of the execution system (CPU time): " + cumulatedCPUTime);
		log("c Best Configuration found: ");
		log("c ID :" + racing.getBestSC().getIdSolverConfiguration());
		try {
			log("c Canonical name: " + api.getCanonicalName(parameters.getIdExperiment(), racing.getBestSC().getParameterConfiguration()));
		} catch (Exception e) {
			e.printStackTrace();
		}
		log("c halt.");
		api.disconnect();
	}

	public void updateSolverConfigName(SolverConfiguration sc, boolean best) throws Exception {
		api.updateSolverConfigurationName(sc.getIdSolverConfiguration(), (best ? " BEST " : "") + (sc.getName() != null ? " " + sc.getName() + " " : "") + sc.getIdSolverConfiguration() + " Runs: " + sc.getNumFinishedJobs() + "/" + sc.getJobCount() + " SCNum: " + sc.getNumber());// +
																																																																						// " "
																																																																						// +
																																																																						// api.getCanonicalName(idExperiment,
																																																																						// sc.getParameterConfiguration()));
	}

	// public void updateSolverConfigName(SolverConfiguration sc, boolean best)
	// throws Exception {
	// api.updateSolverConfigurationName(sc.getIdSolverConfiguration(),
	// experimentName + " " + sc.getIncumbentNumber() + " " + sc.getCost());
	// }

	/**
	 * Parses the configuration file and starts the configurator.
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.out.println("Missing configuration file. Use java -jar PROAR.jar <config file path>");
			return;
		}
		Parameters params = new Parameters();
		Scanner scanner = new Scanner(new File(args[0]));

		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			if (line.trim().startsWith("%"))
				continue;
			String[] keyval = line.split("=");
			String key = keyval[0].trim();
			String value = keyval[1].trim();
			if ("host".equals(key))
				params.hostname = value;
			else if ("user".equals(key))
				params.user = value;
			else if ("password".equals(key))
				params.password = value;
			else if ("port".equals(key))
				params.port = Integer.valueOf(value);
			else if ("database".equals(key))
				params.database = value;
			else if ("idExperiment".equals(key))
				params.idExperiment = Integer.valueOf(value);
			else if ("seed".equals(key))
				params.seed = Long.valueOf(value);
			else if ("jobCPUTimeLimit".equals(key))
				params.jobCPUTimeLimit = Integer.valueOf(value);
			else if ("algorithm".equals(key))
				params.algorithm = value;
			else if ("costFunction".equals(key))
				params.costFunc = value;
			else if ("minimize".equals(key))
				params.minimize = Boolean.parseBoolean(value);
			else if ("parcoursExpansion".equals(key))
				params.parcoursExpansion = Integer.valueOf(value);
			else if ("maxParcoursExpansionFactor".equals(key))
				params.maxParcoursExpansionFactor = Integer.valueOf(value);
			else if ("initialParcoursDefault".equals(key))
				params.initialDefaultParcoursLength = Integer.valueOf(value);
			else if (key.startsWith(params.algorithm + "_"))
				params.configuratorMethodParams.put(key, value);
			else if (key.startsWith(params.racing + "_"))
				params.racingMethodParams.put(key, value);
			else if (key.equals("maxTuningTime"))
				params.maxTuningTime = Integer.valueOf(value);
			else if (key.equals("minCPUCount"))
				params.minCPUCount = Integer.valueOf(value);
			else if (key.equals("maxCPUCount"))
				params.maxCPUCount = Integer.valueOf(value);
			else if (key.equals("minRuns"))
				params.minRuns = Integer.valueOf(value);
			else if (key.equals("racing"))
				params.racing = value;
		}
		scanner.close();
		System.out.println("c Starting the PROAR configurator with following settings: \n" + params);
		System.out.println("c ---------------------------------");
		AAC configurator = new AAC(params);
		configurator.start();
		configurator.shutdown();
	}

	public void log(String message) {
		System.out.println("[Date: " + new Date() + ",Walltime: " + getWallTime() + ",CPUTime: " + cumulatedCPUTime + ",NumSC: " + statNumSolverConfigs + ",NumJobs: " + statNumJobs + "] " + message);
	}

}
