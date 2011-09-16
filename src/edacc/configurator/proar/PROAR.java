package edacc.configurator.proar;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

import edacc.api.API;
import edacc.api.APIImpl;
import edacc.configurator.proar.algorithm.PROARMethods;
import edacc.model.ConfigurationScenarioDAO;
import edacc.model.ExperimentResult;
import edacc.parameterspace.ParameterConfiguration;

public class PROAR {

	private API api;
	private int idExperiment;
	private int jobCPUTimeLimit;
	private String algorithm;
	private Random rng;
	private PROARMethods methods;

	/**
	 * tells if the cost function is to be minimized; if 0 it should be
	 * maximized
	 */
	private boolean minimize;

	/** The statistics function to be used */
	private StatisticFunction statistics;

	/** inidcates if the results of the solver of deterministic nature or not */
	private boolean deterministic;

	/** what kind of metric should be optimized? runtime or cost */
	// TODO:private whateverType metric;

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

	private int statNumSolverConfigs;

	public PROAR(String hostname, int port, String database, String user, String password, int idExperiment, int jobCPUTimeLimit, long seed, String algorithm, String statFunc, boolean minimize) throws Exception {
		// TODO: MaxTuningTime in betracht ziehen!
		api = new APIImpl();
		api.connect(hostname, port, database, user, password);
		this.idExperiment = idExperiment;
		this.jobCPUTimeLimit = jobCPUTimeLimit;
		this.algorithm = algorithm;
		this.statistics = new StatisticFunction(api.costFunctionByName(statFunc), minimize);
		rng = new edacc.util.MersenneTwister(seed);
		listBestSC = new ArrayList<SolverConfiguration>();
		listNewSC = new ArrayList<SolverConfiguration>();
		// TODO: Die beste config auch noch mittels einer methode bestimmen!
		methods = (PROARMethods) ClassLoader.getSystemClassLoader().loadClass("edacc.configurator.proar.algorithm." + algorithm).getDeclaredConstructors()[0].newInstance(api, idExperiment, statistics, rng);
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
		int idSolverConfiguration = api.getBestConfiguration(idExperiment, statistics.getCostFunction());
		if (idSolverConfiguration == 0) {
			// no solver configs in db, that have the specified statistic function
			ParameterConfiguration config = api.loadParameterGraphFromDB(idExperiment).getRandomConfiguration(rng);
			idSolverConfiguration = api.createSolverConfig(idExperiment, config, "First Configuration " + api.getCanonicalName(idExperiment, config) + " level " + level);
		}
		bestSC = new SolverConfiguration(idSolverConfiguration, api.getParameterConfiguration(idExperiment, idSolverConfiguration), statistics, level);
		for (ExperimentResult job : api.getRuns(idExperiment, idSolverConfiguration)) {
			bestSC.putJob(job);
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
		 * geschicktere Moeglichkeit ist es: Anzahl cores = numCores Größe der
		 * besseren solver configs in letzter runde = numBests Anzahl der jobs
		 * die in der letzten Iteration berechnet wurden = numJobs Anzahl der
		 * neuen solver configs beim letzten Aufruf zurückgeliefert wurden =
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
	private int addRandomJob(int num, SolverConfiguration toAdd, SolverConfiguration from, int priority) throws Exception {
		toAdd.updateJobs();
		from.updateJobs();
		// compute a list with num jobs that "from" has computed and "toadd" has
		// not in its job list
		List<InstanceIdSeed> instanceIdSeedList = toAdd.getInstanceIdSeed(from, num, rng);
		int generated = 0;
		for (InstanceIdSeed is : instanceIdSeedList) {
			int idJob = api.launchJob(idExperiment, toAdd.getIdSolverConfiguration(), is.instanceId, BigInteger.valueOf(is.seed), jobCPUTimeLimit);
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
		initializeBest();// TODO: mittels dem Classloader überschreiben

		int num_instances = ConfigurationScenarioDAO.getConfigurationScenarioByExperimentId(idExperiment).getCourse().getInitialLength();

		while (!terminate()) {
			level++;
			bestSC.updateJobs();
			// expandParcoursSC(bestSC, 1);
			if (bestSC.getJobCount() < 5 * num_instances) {
				expandParcoursSC(bestSC, Math.min(5 * num_instances - bestSC.getJobCount(), 20));
			}
			
			System.out.println("Waiting for currently best solver config to finish a job.");
			// TODO: nicht mehr darauf warten sondern erst am Ende der Iteration
			// noch einmal testen
			if (level == 1) {
				while (true) {
					bestSC.updateJobs();
					if (bestSC.getNotStartedJobs().isEmpty() && bestSC.getRunningJobs().isEmpty()) {
						break;
					}
					Thread.sleep(1000);
				}
			} else {
				bestSC.updateJobs();
			}
			updateSolverConfigName(bestSC, true);
			// update the cost of the configuration in the EDACC tables
			// This can not be done if not all jobs have finished???
			api.updateSolverConfigurationCost(bestSC.getIdSolverConfiguration(), bestSC.getCost(), statistics.getCostFunction());
			System.out.println("Generating new Solver Configurations.");
			System.out.println("There are currently " + listNewSC.size() + " solver configurations for the current level (" + level + ") generated in the last level.");
			// compute the number of new solver configs
			numNewSC = computeOptimalExpansion();
			
			List<SolverConfiguration> tmpList = methods.generateNewSC(numNewSC, listBestSC, bestSC, level, level);
			this.listNewSC.addAll(tmpList);
			listBestSC.clear();

			for (SolverConfiguration sc : tmpList) {
				//add 1 random job from the best configuration with the priority corresponding to the level
				//lower levels -> higher priorities
				addRandomJob(1, sc, bestSC, Integer.MAX_VALUE - level);
				updateSolverConfigName(sc, false);
			}

			boolean currentLevelFinished = false;
			//only when all jobs of the current level are finished we can continue
			while (!currentLevelFinished) {
				currentLevelFinished = true;
				Thread.sleep(1000);
				/*
				 * TODO: solver configurations cleanup: in der config sollte
				 * noch ein parameter "maxNumConfigsInDB" hinzugefügt werden
				 * dieser gibt an welche die maximale Anzahl an solver configs
				 * ist die in der DB behalten werden sollte, den Rest kann man
				 * löschen. Das sollte dazu dienen noch eine Übersichtlichkeit
				 * über die solver configs zu bewahren und sie mit dem
				 * web-frontend noch gut sehen zu können. Es ist z.B: denkbar
				 * nur die 100 besten immer zu behalten und die restlichen zu
				 * löschen.
				 */

				for (int i = listNewSC.size() - 1; i >= 0; i--) {
					SolverConfiguration sc = listNewSC.get(i);
					//take only solver configs of the current level into consideration
					//there might be some configs for the next level already generated and evaluated
					if (sc.getLevel() == level) {
						currentLevelFinished = false;
					}
					sc.updateJobs();
					
					// this might not be very efficient .. if the list sizes are 0 then the check isn't necessary anymore.
					if (bestSC.getNotStartedJobs().size() + bestSC.getRunningJobs().size() != 0) {
						bestSC.updateJobs();
						if (bestSC.getNotStartedJobs().size() + bestSC.getRunningJobs().size() == 0) {
							api.updateSolverConfigurationCost(bestSC.getIdSolverConfiguration(), bestSC.getCost(), statistics.getCostFunction());
						}
					}
					
					updateSolverConfigName(sc, false);
					// if finishedAll(sc)
					if (sc.getNotStartedJobs().size() + sc.getRunningJobs().size() == 0) {
						int comp = sc.compareTo(bestSC);
						if (comp >= 0) {
							// sc better or equal than bestSC 
							// a timeout is not better than a timeout!!! so maybe ">" would be better
							//if the first instance is not solvable at all, then this might be also a problem!
							if (sc.getJobCount() == bestSC.getJobCount()) {
								if (sc.getLevel() != level) {
									// don't add solver configurations from the next level to the best sc list
									continue;
								}
								// all jobs from bestSC computed.
								if (comp > 0) {
									listBestSC.add(sc);
									// womoeglich hier schon ein job hinzufügen
									// für die besten, damit der Vergleich, danach aussagekraeftiger ist!
								}
								api.updateSolverConfigurationCost(sc.getIdSolverConfiguration(), sc.getCost(), statistics.getCostFunction());
								listNewSC.remove(i);
							} else {
								int generated = addRandomJob(sc.getJobCount(), sc, bestSC, Integer.MAX_VALUE - sc.getLevel());
								System.out.println("Generated " + generated + " jobs for level " + sc.getLevel());
							}
						} else {
							// api.updateSolverConfigurationCost(sc.getIdSolverConfiguration(),
							// sc.getCost(), statistics.getCostFunction());
							api.removeSolverConfig(sc.getIdSolverConfiguration());
							listNewSC.remove(i);
						}
					} else {
						;/*
						 * TODO: adaptive capping:Wir haben eine solver config
						 * new die neu jobs bekommen hat um mit der best
						 * verglichen zu werden.Wir schauen sie momentan nur
						 * dann an wenn all ihre jobs schon fertig sind.Mann
						 * kann sich aber die ergebnise der jobs schon vorher
						 * anschauen um folgende zwei Sachen zu bestimmen.1. ist
						 * new überhaupt noch in der Lage die best zu schlagen
						 * mit den werten die sie schon jetzt hat? dafür
						 * bestimmt man deren kosten und vergleicht mit best
						 * anhand der schon vorhandenen ergebnisse. hat sie
						 * schon verloren kann man sie löschen ohne auf alle
						 * jobs zu warten! das kann gerade gegen Ende des
						 * Vergleichs von Vorteil sein!2. Wenn die new mit den
						 * aktuellen jobs noch nicht verloren hat, kann man noch
						 * immer die timeLimit der bestehenden (noch nicht
						 * gestarteten) jobs in Betracht ziehen: sei x die cost
						 * der best bzgl. der Auswahl an Instanzen auf die mit
						 * new verglichen werden soll dann kann die timeLimit
						 * der restlichen jobs auf min(timeLimit, cost(new)-x))
						 * gesetzt werden oder sowas in der Richtung Das könnte
						 * auch einiges an Rechenarbeit sparen!
						 */
					}
				}

				// determine how many idleing cores we have and generate new solver configurations for the next level
				int coreCount = api.getComputationCoreCount(idExperiment);
				int jobs = api.getComputationJobCount(idExperiment);
				int sc_to_generate = Math.max(coreCount, 8) - jobs;

				if (sc_to_generate > 0) {
					System.out.println("Generating " + sc_to_generate + " solver configurations for the next level.");
					List<SolverConfiguration> scs = methods.generateNewSC(sc_to_generate, new ArrayList<SolverConfiguration>(), bestSC, level + 1, level);
					listNewSC.addAll(scs);

					for (SolverConfiguration sc : scs) {
						addRandomJob(1, sc, bestSC, Integer.MAX_VALUE - level - 1);
						updateSolverConfigName(sc, false);
					}
				}

			}
			updateSolverConfigName(bestSC, false);
			System.out.println("Determining the new best solver config from " + listBestSC.size() + " solver configurations.");
			if (listBestSC.size() > 0) {
				for (SolverConfiguration sc : listBestSC) {
					if (sc.compareTo(bestSC) > 0) {
						bestSC = sc;
					} else {
						// api.removeSolverConfig(sc.getIdSolverConfiguration());
					}
				}
			}
			// updateSolverConfigName(bestSC, true); not neccessary because we
			// update this in the beginning of the loop!
		}

	}

	public void shutdown() {
		System.out.println("halt.");
		api.disconnect();
	}

	public void updateSolverConfigName(SolverConfiguration sc, boolean best) throws Exception {
		api.updateSolverConfigurationName(sc.getIdSolverConfiguration(), (best ? " BEST " : "") + sc.getIdSolverConfiguration() + " Runs: " + sc.getFinishedJobs().size() + " Level: " + sc.getLevel() + " " + api.getCanonicalName(idExperiment, sc.getParameterConfiguration()));
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
		}
		scanner.close();

		PROAR configurator = new PROAR(hostname, port, database, user, password, idExperiment, jobCPUTimeLimit, seed, algorithm, costFunc, minimize);
		configurator.start();
		configurator.shutdown();
	}
}
