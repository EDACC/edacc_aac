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
import edacc.model.ExperimentResultDAO;
import edacc.parameterspace.ParameterConfiguration;

public class PROAR {
	
	private API api;
	private int idExperiment;
	private int jobCPUTimeLimit;
	private String algorithm;
	private Random rng;
	private PROARMethods methods;
	
	/**tells if the cost function is to be minimized; if 0 it should be maximized*/
	private boolean minimize; 
		
	//TODO : statstische funktion noch auswaehlen
	//Irgendwie muesste das eigentlich die Api anbieten
	//Die Api sollte alle statistische Funktionen anbieten die man so braucht; Am Anfang kann sich der konfigurator für eine entscheiden
	//und ab da wird nur diese verwendet. In C realisiert man sowas über functions-pointern; in Java muss ich leider passen. 
	//Man übergibt ein Vector mit floats der statistischen Funktion und diese liefert den statistischen wert zurück
	//private statistics  
	
	/**inidcates if the results of the solver of deterministic nature or not*/
	private boolean deterministic;
	
	/**what kind of metric should be optimized? runtime or cost*/
	//TODO:private whateverType metric; 
	
	/**maximum allowed tuning time = sum over all jobs in seconds*/
	private float maxTuningTime; //TODO: take into consideration
	
	/**total cumulated time of all jobs the configurator has started so far in seconds*/
	private float cumulatedCPUTime;
	
	/**the best solver configuration found so far*/
	private SolverConfiguration bestSC;
	
	/**List of all solver configuration that turned out to be better than the best configuration*/
	private List<SolverConfiguration> listBestSC;
	
	/**List of all NEW solver configuration that are going to be raced against the best*/
	private List<SolverConfiguration> listNewSC;
	
	/**If within the Experiment there is an other Solver that the configurator has to beat then raceCondition should be true.
	 * The configurator will then try to use the information about the results of this solver to try to beat him according to 
	 * the statistic and metric function.
	 * */
	private boolean raceCondition;
	
	/** If raceCondigition == true then there has to be a competitior which the configurator will try to beat!
	 */
	private SolverConfiguration competitor;
	
	/**
	 * just for debugging
	 */
	private int level;
	
	
	public PROAR(String hostname, int port, String database, String user, String password, int idExperiment, int jobCPUTimeLimit, long seed, String algorithm) throws Exception {
	//TODO: MaxTuningTime in betracht ziehen!
		api = new APIImpl();
		api.connect(hostname, port, database, user, password);
		this.idExperiment = idExperiment;
		this.jobCPUTimeLimit = jobCPUTimeLimit;
		this.algorithm = algorithm;
		rng = new edacc.util.MersenneTwister(seed);
		listBestSC = new ArrayList<SolverConfiguration>();
		listNewSC = new ArrayList<SolverConfiguration>();
		//TODO: Die beste config auch noch mittels einer methode bestimmen!
		methods = (PROARMethods) ClassLoader.getSystemClassLoader().loadClass("edacc.configurator.proar.algorithm." + algorithm).getDeclaredConstructors()[0].newInstance(api, idExperiment, rng);
	}
	
	/**
	 * Checks if there are solver configurations in the experiment that would match the configuration scenario
	 * if there are more than one such configuration it will pick the best one as the best configuration found so far 
	 * @throws Exception  
	 */
	private void initializeBest() throws Exception {
		// currently a random sc is generated as best one
		ParameterConfiguration config = api.loadParameterGraphFromDB(idExperiment).getRandomConfiguration(rng);
		int idSolverConfiguration = api.createSolverConfig(idExperiment, config, "First Configuration " + api.getCanonicalName(idExperiment, config) + " level " + level);
		bestSC = new SolverConfiguration(idSolverConfiguration, api.getParameterConfiguration(idExperiment, idSolverConfiguration), level);
	}
	
	/**
	 * Recheck if there is a configuration in the Experiment that was created by someone else(other configurator or human) and is better then the current bestSC.
	 * @return solver configuration ID that is better than the current bestSC or -1 else  
	 */
	private int recheck(){
		//TODO : implement
		return -1; 
	}
	
	/**
	 * Determines if the termination criteria holds
	 * @return true if the termination criteria is met;
	 */
	private boolean terminate(){
		if (this.maxTuningTime<0) 
			return false;
		//at the moment only the time budget is taken into consideration
		float exceed=this.cumulatedCPUTime-this.maxTuningTime;
		if (exceed>0){
			System.out.println("Maximum allowed CPU time exceeded with: "+exceed+" seconds!!!");
			return true;
		}
		else 
			return false;
	}
	/**
	 *  Add num additional runs/jobs from the parcours to the configuration sc. 
	 * @throws Exception 
	 */
	private void expandParcoursSC(SolverConfiguration sc,int num) throws Exception{
		//TODO implement
		//fuer deterministische solver sollte man allerdings beachten, 
		//dass wenn alle instanzen schon verwendet wurden das der parcours nicht weiter erweitert werden kann.
		//fuer probabilistische solver kann der parcours jederzeit erweitert werden, jedoch 
		//waere ein Obergrenze sinvoll die als funktion der anzahl der instanzen definiert werden sollte
		//z.B: 10*#instanzen
		for (int i = 0; i < num; i++) {
			int idJob = api.launchJob(idExperiment, sc.getIdSolverConfiguration(), jobCPUTimeLimit, rng);
			sc.putJob(ExperimentResultDAO.getById(idJob));
		}
	}
	
	/**
	 * Determines how many new solver configuration can be taken into consideration. 
	 */
	private int computeOptimalExpansion(){
		return 40;
		/*TODO: was geschickteres implementieren, denn von diesem Wert haengt sehr stark der Grad der parallelisierung statt.
		* denkbar ware noch api.getNumComputingUnits(); wenn man die Methode haette. 
		* eine andere geschicktere Moeglichkeit ist es:
		* Anzahl cores = numCores 
		* Größe der besseren solver configs in letzter runde = numBests
		* Anzahl der jobs die in der letzten Iteration berechnet wurden = numJobs
		* Anzahl der neuen solver configs beim letzten Aufruf zurückgeliefert wurden = lastExpansion
		* CPUTimeLimit = time
		* Dann kann man die Anzahl an neuen konfigs berechnen durch
		* newNumConfigs = TODO
		*/
	}
	
	/**
	 * adds random num new runs/jobs from the solver configuration "from" to  the solver configuration "toAdd"  
	 * @throws Exception 
	 */
	private int addRandomJob(int num, SolverConfiguration toAdd, SolverConfiguration from) throws Exception{
		toAdd.updateJobs();
		from.updateJobs();
		List<InstanceIdSeed> instanceIdSeedList = toAdd.getInstanceIdSeed(from, num);
		int generated = 0;
		for (InstanceIdSeed is : instanceIdSeedList) {
			int idJob = api.launchJob(idExperiment, toAdd.getIdSolverConfiguration(), is.instanceId, BigInteger.valueOf(is.seed), jobCPUTimeLimit);
			toAdd.putJob(ExperimentResultDAO.getById(idJob));
			generated ++;
		}
		return generated;
	}
	
	public void start() throws Exception {
		
		//first initialize the best individual if there is a default or if there are already some solver configurations in the experiment
		level = 0;
		
		initializeBest();//TODO: mittels dem Classloader überschreiben
		
		int numNewSC = computeOptimalExpansion();
		while (!terminate()){
			level++;
			bestSC.updateJobs();
			//TODO: nicht nur 1 sonder f(wieviele es in der letzten Runde geschlagen hat)
			expandParcoursSC(bestSC,1);
			System.out.println("Waiting for currently best solver config to finish a job.");
			while (true) {
				bestSC.updateJobs();
				if (bestSC.getNotStartedJobs().isEmpty() && bestSC.getRunningJobs().isEmpty()) {
					break;
				}
				
				Thread.sleep(1000);
			}
			updateSolverConfigName(bestSC, true);
			System.out.println("Generating new Solver Configurations.");
			
			// at this point: best solver config should have computed all jobs because jobs aren't updated in the main loop.
			// compute the number of new solver configs 
			numNewSC = computeOptimalExpansion();
			this.listNewSC = methods.generateNewSC(numNewSC, listBestSC, bestSC, level);
			listBestSC.clear();
			
			for (SolverConfiguration sc : listNewSC){
				addRandomJob(1,sc,bestSC);
				updateSolverConfigName(sc, false);
			}
			while(!this.listNewSC.isEmpty()){				
				Thread.sleep(1000);
				
				for (int  i = listNewSC.size()-1; i >= 0; i--) {
					SolverConfiguration sc = listNewSC.get(i);
					sc.updateJobs();
					updateSolverConfigName(sc, false);
					// if finishedAll(sc)
					if (sc.getNotStartedJobs().size() + sc.getRunningJobs().size() == 0) {
						int comp = sc.compareTo(bestSC);
						if (comp >= 0) {
							// sc better than bestSC
							if (sc.getJobCount() == bestSC.getJobCount()) {
								// all jobs from bestSC computed.
								if (comp > 0) {
									listBestSC.add(sc);
								}
								listNewSC.remove(i);
							} else {
								int generated = addRandomJob(sc.getJobCount(), sc, bestSC);
								System.out.println("Generated " + generated + " jobs");
							}
						} else {
							listNewSC.remove(i);
						}
					}
				}
			}
			updateSolverConfigName(bestSC, false);
			System.out.println("Determining the new best solver config from " + listBestSC.size() + " solver configurations.");
			if (listBestSC.size() > 0) {
				for (SolverConfiguration sc : listBestSC) {
					if (sc.compareTo(bestSC) > 0) {
						bestSC = sc;
					}
				}
			}
			updateSolverConfigName(bestSC, true);
		}
		
	}
	
	public void shutdown() {
		System.out.println("halt.");
		api.disconnect();
	}
	
	
	public void updateSolverConfigName(SolverConfiguration sc, boolean best) throws Exception {
		api.updateSolverConfigurationName(sc.getIdSolverConfiguration(), "Runs: " + sc.getFinishedJobs().size() + (best ? " BEST" : "") + " level: " + sc.getLevel() + " " + api.getCanonicalName(idExperiment, sc.getParameterConfiguration()));
	}
	
	/**
	 * Parses the configuration file and starts the configurator. 
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
        
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.trim().startsWith("%")) continue;
            String[] keyval = line.split("=");
            String key = keyval[0].trim();
            String value = keyval[1].trim();
            if ("host".equals(key)) hostname = value;
            else if ("user".equals(key)) user = value;
            else if ("password".equals(key)) password = value;
            else if ("port".equals(key)) port = Integer.valueOf(value);
            else if ("database".equals(key)) database = value;
            else if ("idExperiment".equals(key)) idExperiment = Integer.valueOf(value);
            else if ("seed".equals(key)) seed = Long.valueOf(value);
            else if ("jobCPUTimeLimit".equals(key)) jobCPUTimeLimit = Integer.valueOf(value);
            else if ("algorithm".equals(key)) algorithm = value;
        }
        scanner.close();
        
        PROAR configurator = new PROAR(hostname, port, database, user, password, idExperiment, jobCPUTimeLimit, seed, algorithm);
        configurator.start();
        configurator.shutdown();
	}
}
