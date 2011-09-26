package edacc.configurator.proar;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import edacc.api.API;
import edacc.api.APIImpl;
import edacc.configurator.proar.algorithm.PROARMethods;
import edacc.model.ConfigurationScenarioDAO;
import edacc.model.Course;
import edacc.model.ExperimentResult;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;

public class PROAR {

	private static final boolean generateSCForNextLevel = true;
	private static final boolean useCapping = true;
	private static final boolean deleteSolverConfigs = true;
	
	private API api;
	private int idExperiment;
	private int jobCPUTimeLimit;
	private String algorithm;
	private Random rng;
	private PROARMethods methods;

	/** The statistics function to be used */
	private StatisticFunction statistics;

	/** inidcates if the results of the solver of deterministic nature or not */
	private boolean deterministic;

	/** maximum allowed tuning time = sum over all jobs in seconds */
	private float maxTuningTime; // TODO: take into consideration

	/**
	 * total cumulated time of all jobs the configurator has started so far in
	 * seconds
	 */
	private float cumulatedCPUTime;

	/** the best solver configuration found so far */
	private SolverConfiguration bestSC;

	/**
	 * List of all solver configuration that turned out to be better than the
	 * best configuration
	 */
	private List<SolverConfiguration> listBestSC;

	/**
	 * List of all NEW solver configuration that are going to be raced against
	 * the best
	 */
	private List<SolverConfiguration> listNewSC;

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

	/**
	 * just for debugging
	 */
	private int level;
	/**
	 * The number of jobs that a configuration gets when its parcours is
	 * expanded
	 */
	private int parcoursExpansion;
	/**
	 * The maximum length of the parcours that will be generated by the
	 * configurator as a factor of the number of instances; i.e.: if the
	 * maxParcoursExpansionFactor = 10 and we have 250 instances in the
	 * configuration experiment then the maximum length of the parcours will be
	 * 2500.
	 */
	private int maxParcoursExpansionFactor;

	private int initialDefaultParcoursLength;

	private int statNumSolverConfigs;
	private int statNumJobs;

	private ParameterGraph graph;
	public PROAR(String hostname, int port, String database, String user, String password, int idExperiment,
			int jobCPUTimeLimit, long seed, String algorithm, String statFunc, boolean minimize, int pe, int mpef,
			int ipd, Map<String, String> configuratorMethodParams) throws Exception {
		// TODO: MaxTuningTime in betracht ziehen!
		api = new APIImpl();
		api.connect(hostname, port, database, user, password);
		this.graph = api.loadParameterGraphFromDB(idExperiment);
		this.idExperiment = idExperiment;
		this.jobCPUTimeLimit = jobCPUTimeLimit;
		this.algorithm = algorithm;
		this.statistics = new StatisticFunction(api.costFunctionByName(statFunc), minimize);
		this.parcoursExpansion = pe;
		this.maxParcoursExpansionFactor = mpef;
		this.initialDefaultParcoursLength = ipd;
		rng = new edacc.util.MersenneTwister(seed);
		listBestSC = new ArrayList<SolverConfiguration>();
		listNewSC = new ArrayList<SolverConfiguration>();
		this.statNumSolverConfigs = 0;
		this.statNumJobs = 0;
		// TODO: Die beste config auch noch mittels einer methode bestimmen!
		methods = (PROARMethods) ClassLoader.getSystemClassLoader()
				.loadClass("edacc.configurator.proar.algorithm." + algorithm).getDeclaredConstructors()[0].newInstance(
				api, idExperiment, statistics, rng, configuratorMethodParams);
	}

	/**
	 * Checks if there are solver configurations in the experiment that would
	 * match the configuration scenario if there are more than one such
	 * configuration it will pick the best one as the best configuration found
	 * so far
	 * 
	 * @throws Exception
	 */
	private void initializeBest() throws Exception {
		// TODO: the best one might not match the configuration scenario
		// graph.validateParameterConfiguration(config) should test this,
		// but is currently not implemented and will return false.
		
		List<Integer> solverConfigIds = api.getSolverConfigurations(idExperiment, "default");
		if (solverConfigIds.isEmpty()) {
			solverConfigIds = api.getSolverConfigurations(idExperiment);
			System.out.println("Found " + solverConfigIds.size() + " solver configuration(s)");
		} else {
			System.out.println("Found " + solverConfigIds.size() + " default configuration(s)");
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
				ParameterConfiguration pConfig = api.getParameterConfiguration(idExperiment, id);
				solverConfigs.add(new SolverConfiguration(id, pConfig, statistics, level));
			}
		}
		System.out.println("" + solverConfigs.size() + " solver configs with max run");
		
		
		Course c = ConfigurationScenarioDAO.getConfigurationScenarioByExperimentId(idExperiment).getCourse();
		for (int sc_index = solverConfigs.size()-1; sc_index >= 0; sc_index--) {
			SolverConfiguration sc = solverConfigs.get(sc_index);
			HashSet<InstanceIdSeed> iis = new HashSet<InstanceIdSeed>();
			for (ExperimentResult job : api.getRuns(idExperiment, sc.getIdSolverConfiguration())) {
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
					System.out.println("Course invalid at instance number " + i + " instance: " + c.get(i).instance.getName());
					break;
				}
			}
			if (!courseValid) {
				System.out.println("Removing solver configuration " + api.getSolverConfigName(sc.getIdSolverConfiguration()) + " caused by invalid course.");
				solverConfigs.remove(sc_index);
			}
		}
		
		System.out.println("Determining best solver configuration from " + solverConfigs.size() + " solver configurations");
		
		if (solverConfigs.isEmpty()) {
			// no good solver configs in db
			System.out.println("Generating a random solver configuration");

			ParameterConfiguration config = graph.getRandomConfiguration(rng);
			int idSolverConfiguration = api.createSolverConfig(idExperiment, config,
					"First Random Configuration " + api.getCanonicalName(idExperiment, config) + " level " + level);
			bestSC = new SolverConfiguration(idSolverConfiguration, api.getParameterConfiguration(idExperiment,
					idSolverConfiguration), statistics, level);
		} else {
			Collections.sort(solverConfigs);
			bestSC = solverConfigs.get(solverConfigs.size()-1);
			System.out.println("Best solver configuration: " + api.getSolverConfigName(bestSC.getIdSolverConfiguration()));
			
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
		if (this.maxTuningTime < 0)
			return false;
		// at the moment only the time budget is taken into consideration
		float exceed = this.cumulatedCPUTime - this.maxTuningTime;
		if (exceed > 0) {
			System.out.println("Maximum allowed CPU time exceeded with: " + exceed + " seconds!!!");
			return true;
		} else
			return false;
	}

	/**
	 * Add num additional runs/jobs from the parcours to the configuration sc.
	 * 
	 * @throws Exception
	 */
	private void expandParcoursSC(SolverConfiguration sc, int num) throws Exception {
		// TODO implement
		// fuer deterministische solver sollte man allerdings beachten,
		// dass wenn alle instanzen schon verwendet wurden das der parcours
		// nicht weiter erweitert werden kann.
		// fuer probabilistische solver kann der parcours jederzeit erweitert
		// werden, jedoch
		// waere ein Obergrenze sinvoll die als funktion der anzahl der
		// instanzen definiert werden sollte
		// z.B: 10*#instanzen
		for (int i = 0; i < num; i++) {
			int idJob = api.launchJob(idExperiment, sc.getIdSolverConfiguration(), jobCPUTimeLimit, rng);
			api.setJobPriority(idJob, Integer.MAX_VALUE);
			sc.putJob(api.getJob(idJob)); // add the job to the solver
											// configuration own job store
		}
	}

	/**
	 * Determines how many new solver configuration can be taken into
	 * consideration.
	 * 
	 * @throws Exception
	 */
	private int computeOptimalExpansion() throws Exception {
		return Math.max(0, api.getComputationCoreCount(idExperiment) - listNewSC.size());
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

	/**
	 * adds random num new runs/jobs from the solver configuration "from" to the
	 * solver configuration "toAdd"
	 * 
	 * @throws Exception
	 */
	private int addRandomJob(int num, SolverConfiguration toAdd, SolverConfiguration from, int priority)
			throws Exception {
		toAdd.updateJobsStatus();
		from.updateJobsStatus();
		// compute a list with num jobs that "from" has computed and "toadd" has
		// not in its job list
		List<InstanceIdSeed> instanceIdSeedList = toAdd.getInstanceIdSeed(from, num, rng);
		int generated = 0;
		for (InstanceIdSeed is : instanceIdSeedList) {
			int idJob = api.launchJob(idExperiment, toAdd.getIdSolverConfiguration(), is.instanceId,
					BigInteger.valueOf(is.seed), jobCPUTimeLimit);
			api.setJobPriority(idJob, priority);
			toAdd.putJob(api.getJob(idJob));
			generated++;
		}
		return generated;
	}

	public void start() throws Exception {
		int numNewSC;
		// first initialize the best individual if there is a default or if
		// there are already some solver configurations in the experiment
		level = 0;
		initializeBest();// TODO: mittels dem Classloader �berschreiben
		int expansion;
		int num_instances = ConfigurationScenarioDAO.getConfigurationScenarioByExperimentId(idExperiment).getCourse()
				.getInitialLength();

		while (!terminate()) {
			level++;
			// bestSC.updateJobsStatus(); das ist glaube ich doppelt gemoppelt
			// denn im �bern�chsten if wird auf jeden Fall
			// bestSC.updateJobsSatus() ausgef�hrt!
			// expand the parcours of the bestSC
			expansion = 0;
			if (bestSC.getJobCount() < maxParcoursExpansionFactor * num_instances) {
				expansion = Math.min(maxParcoursExpansionFactor * num_instances - bestSC.getJobCount(),
						parcoursExpansion);
				expandParcoursSC(bestSC, expansion);
			}
			System.out.println("Expanding parcours of best solver config " + bestSC.getIdSolverConfiguration() + " by "
					+ expansion);

			// update the status of the jobs of bestSC and if first level wait
			// also for jobs to finish
			if (level == 1) {
				System.out.println("Waiting for currently best solver config " + bestSC.getIdSolverConfiguration()
						+ " to finish " + expansion + "job(s)");
				while (true) {
					bestSC.updateJobsStatus();
					if (bestSC.getNotStartedJobs().isEmpty() && bestSC.getRunningJobs().isEmpty()) {
						break;
					}
					Thread.sleep(1000);
				}
			} else {
				bestSC.updateJobsStatus();
			}
			updateSolverConfigName(bestSC, true);
			// update the cost of the configuration in the EDACC solver
			// configuration tables
			api.updateSolverConfigurationCost(bestSC.getIdSolverConfiguration(), bestSC.getCost(),
					statistics.getCostFunction());
			System.out.println("Generating new Solver Configurations.");
			System.out.println("There are currently " + listNewSC.size()
					+ " solver configurations for the current level (" + level + ") generated in the last level.");

			// compute the number of new solver configs that should be generated
			// for this level
			numNewSC = computeOptimalExpansion();
			List<SolverConfiguration> tmpList = methods.generateNewSC(numNewSC, listBestSC, bestSC, level, level);
			this.statNumSolverConfigs += numNewSC;
			this.listNewSC.addAll(tmpList);
			listBestSC.clear();
			System.out.println(statNumSolverConfigs + "SC -> Generated " + numNewSC + " new solver configurations");

			for (SolverConfiguration sc : tmpList) {
				// add 1 random job from the best configuration with the
				// priority corresponding to the level
				// lower levels -> higher priorities
				addRandomJob(1, sc, bestSC, Integer.MAX_VALUE - level);
				updateSolverConfigName(sc, false);
			}

			boolean currentLevelFinished = false;
			// only when all jobs of the current level are finished we can
			// continue
			while (!currentLevelFinished) {
				currentLevelFinished = true;
				Thread.sleep(1000);
				// TODO : implement a method that determines an optimal wait
				// according to the runtimes of the jobs!

				for (int i = listNewSC.size() - 1; i >= 0; i--) {
					SolverConfiguration sc = listNewSC.get(i);
					// take only solver configs of the current level into
					// consideration
					// there might be some configs for the next level already
					// generated and evaluated
					if (sc.getLevel() == level) {
						currentLevelFinished = false;
					}
					sc.updateJobsStatus();
					updateSolverConfigName(sc, false);
					if (sc.getNumNotStartedJobs() + sc.getNumRunningJobs() == 0) {
						int comp = sc.compareTo(bestSC);
						if (comp >= 0) {
							if (sc.getJobCount() == bestSC.getJobCount()) {
								if (sc.getLevel() != level) {
									// don't add solver configurations from the
									// next level to the best sc list
									continue;
								}
								// all jobs from bestSC computed and won against
								// best:
								if (comp > 0) {
									listBestSC.add(sc);
								}
								api.updateSolverConfigurationCost(sc.getIdSolverConfiguration(), sc.getCost(),
										statistics.getCostFunction());
								listNewSC.remove(i);
							} else {
								int generated = addRandomJob(sc.getJobCount(), sc, bestSC,
										Integer.MAX_VALUE - sc.getLevel());
								System.out.println("Generated " + generated + " jobs for level " + sc.getLevel());
							}
						} else {// lost against best on part of the actual
								// parcours:
							if (deleteSolverConfigs)
								api.removeSolverConfig(sc.getIdSolverConfiguration());
							listNewSC.remove(i);// remove from new
												// configurations
							// System.out.println(">>>>>Config lost!!!");
						}
					} else {
						if (useCapping) {
							// ---CAPPING RUNS OF BAD CONFIGS---
							// wenn sc schon eine kummulierte Laufzeit der
							// beendeten
							// jobs > der aller beendeten jobs von best
							// kann man sc vorzeitig beedenden! geht nur wenn
							// man parX hat!
							if ((this.statistics.getCostFunction() instanceof edacc.api.costfunctions.PARX) || (this.statistics.getCostFunction() instanceof edacc.api.costfunctions.Average))
								// TODO: minimieren / maximieren /negative
								// kosten
								if (sc.getCumulatedCost() > bestSC.getCumulatedCost()) {
									System.out.println(sc.getCumulatedCost() + " >" + bestSC.getCumulatedCost());
									System.out.println(sc.getJobCount() + " > " + bestSC.getJobCount());
									// kill all running jobs of the sc config!
									List<ExperimentResult> jobsToKill = sc.getJobs();
									for (ExperimentResult j : jobsToKill) {
										this.api.killJob(j.getId());
									}
									api.removeSolverConfig(sc.getIdSolverConfiguration());
									listNewSC.remove(i);
									System.out.println("-----Config capped!!!");
								}
							// sc.killRunningJobs
							// api.removeSolverConfig(sc.)
						}
					}

				}
				// ----INCREASE PARALLELISM----
				// determine how many idleing cores we have and generate new
				// solver configurations for the next level
				
				if (generateSCForNextLevel) {
					int coreCount = api.getComputationCoreCount(idExperiment);
					int jobs = api.getComputationJobCount(idExperiment);
					int min_sc = Math.max(Math.round(1.2f * coreCount), 8) - jobs;
					if (min_sc > 0) {
						int sc_to_generate = Math.max(Math.round(1.5f * coreCount), 8) - jobs;
						// System.out.println("Generating " + sc_to_generate +
						// " solver configurations for the next level.");
						List<SolverConfiguration> scs = methods.generateNewSC(sc_to_generate, new ArrayList<SolverConfiguration>(), bestSC, level + 1, level);
						this.statNumSolverConfigs += sc_to_generate;
						listNewSC.addAll(scs);

						for (SolverConfiguration sc : scs) {
							addRandomJob(1, sc, bestSC, Integer.MAX_VALUE - level - 1);
							updateSolverConfigName(sc, false);
						}
					}
				}
				if (bestSC.getNumNotStartedJobs() + bestSC.getNumRunningJobs() != 0) {
					bestSC.updateJobsStatus();
					updateSolverConfigName(bestSC, true);
					if (bestSC.getNumNotStartedJobs() + bestSC.getNumRunningJobs() == 0) {
						api.updateSolverConfigurationCost(bestSC.getIdSolverConfiguration(), bestSC.getCost(),
								statistics.getCostFunction());
					}
				}
				// determine and add race solver configurations
				for (SolverConfiguration sc : getRaceSolverConfigurations()) {
					System.out.println("Found RACE solver configuration: " + sc.getIdSolverConfiguration() + " - " + sc.getName());
					listNewSC.add(sc);
				}
				
			}
			updateSolverConfigName(bestSC, false);
			System.out.println("Determining the new best solver config from " + listBestSC.size()
					+ " solver configurations.");
			if (listBestSC.size() > 0) {
				for (SolverConfiguration sc : listBestSC) {
					if (sc.compareTo(bestSC) > 0) {
						// if bestsc is from the same level as sc then remove
						// bestSC from DB as we want to keep only
						// 1 best from each level!
						if (deleteSolverConfigs && (bestSC.getLevel() == sc.getLevel())
								&& (this.algorithm.equals("ROAR") || (this.algorithm.equals("MB")))) {
							api.removeSolverConfig(bestSC.getIdSolverConfiguration());
						}
						bestSC = sc;
					} else {
						if (deleteSolverConfigs && (this.algorithm.equals("ROAR") || (this.algorithm.equals("MB"))))
							api.removeSolverConfig(sc.getIdSolverConfiguration());
					}
				}
			}
			// updateSolverConfigName(bestSC, true); not neccessary because we
			// update this in the beginning of the loop!
		}

	}

	/**
	 * Determines the solver configurations for which the user has set the race hint.<br/>
	 * Does not return solver configurations which have runs.
	 * @return
	 * @throws Exception
	 */
	public List<SolverConfiguration> getRaceSolverConfigurations() throws Exception {
		List<SolverConfiguration> res = new LinkedList<SolverConfiguration>();
		// get solver config ids
		List<Integer> solverConfigIds = api.getSolverConfigurations(idExperiment, "race");
		// reset hint field
		for (Integer i : solverConfigIds) {
			api.setSolverConfigurationHint(idExperiment, i, "");
		}
		// create solver configs and return them
		for (Integer i : solverConfigIds) {
			if (api.getRuns(idExperiment, i).isEmpty()) {
				SolverConfiguration sc = new SolverConfiguration(i, api.getParameterConfiguration(idExperiment, i), statistics, level);
				sc.setName(api.getSolverConfigName(i));
				res.add(sc);
			}
		}
		return res;
	}
	
	public void shutdown() {
		System.out.println("Solver Configurations generated: " + this.statNumSolverConfigs);
		System.out.println("Jobs generated: " + statNumJobs);
		System.out.println("Total runtime of the execution system (CPU time): " + cumulatedCPUTime);
		System.out.println("Best Configuration found: ");
		System.out.println("ID :" + bestSC.getIdSolverConfiguration());
		try {
			System.out.println("Canonical name: "
					+ api.getCanonicalName(this.idExperiment, bestSC.getParameterConfiguration()));
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("halt.");
		api.disconnect();
	}

	public void updateSolverConfigName(SolverConfiguration sc, boolean best) throws Exception {
		api.updateSolverConfigurationName(
				sc.getIdSolverConfiguration(),
				(best ? " BEST " : "") + (sc.getName() != null ? " " + sc.getName() + " " : "") + sc.getIdSolverConfiguration() + " Runs: " + sc.getNumFinishedJobs()
						+ "/" + sc.getJobCount() + " Level: " + sc.getLevel()
						+ " " + api.getCanonicalName(idExperiment, sc.getParameterConfiguration()));
	}

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
		
		Scanner scanner = new Scanner(new File(args[0]));
		String hostname = "", user = "", password = "", database = "";
		int idExperiment = 0;
		int port = 3306;
		int jobCPUTimeLimit = 13;
		long seed = System.currentTimeMillis();
		String algorithm = "ROAR";
		String costFunc = "par10";
		boolean minimize = true;
		int pe = 1;
		int mpef = 5;
		int ipd = 10;
		
		HashMap<String, String> configuratorMethodParams = new HashMap<String, String>();

		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			if (line.trim().startsWith("%"))
				continue;
			String[] keyval = line.split("=");
			String key = keyval[0].trim();
			String value = keyval[1].trim();
			if ("host".equals(key))
				hostname = value;
			else if ("user".equals(key))
				user = value;
			else if ("password".equals(key))
				password = value;
			else if ("port".equals(key))
				port = Integer.valueOf(value);
			else if ("database".equals(key))
				database = value;
			else if ("idExperiment".equals(key))
				idExperiment = Integer.valueOf(value);
			else if ("seed".equals(key))
				seed = Long.valueOf(value);
			else if ("jobCPUTimeLimit".equals(key))
				jobCPUTimeLimit = Integer.valueOf(value);
			else if ("algorithm".equals(key))
				algorithm = value;
			else if ("costFunction".equals(key))
				costFunc = value;
			else if ("minimize".equals(key))
				minimize = Boolean.parseBoolean(value);
			else if ("parcoursExpansion".equals(key))
				pe = Integer.valueOf(value);
			else if ("maxParcoursExpansionFactor".equals(key))
				mpef = Integer.valueOf(value);
			else if ("initialParcoursDefault".equals(key))
				ipd = Integer.valueOf(value);
			else if (key.startsWith(algorithm + "_")) 
				configuratorMethodParams.put(key, value);
			
		}
		scanner.close();
		System.out.println("Starting the PROAR configurator with following settings: ");
		System.out.println("Algorithm: " + algorithm);
		System.out.println("Optimizing statistic: " + costFunc);
		System.out.println("towards: " + (minimize ? "mimisation" : "maximisation"));
		System.out.println("Parcours expansion pro level: " + pe);
		System.out.println("Maximum parcours expansion factor: " + mpef);

		System.out.println("CPU time limit: " + jobCPUTimeLimit);
		System.out.println("---------------------------------");
		PROAR configurator = new PROAR(hostname, port, database, user, password, idExperiment, jobCPUTimeLimit, seed,
				algorithm, costFunc, minimize, pe, mpef, ipd, configuratorMethodParams);
		configurator.start();
		configurator.shutdown();
	}
}
