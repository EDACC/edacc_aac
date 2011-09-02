package edacc.configurator.proar;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

import edacc.api.API;
import edacc.api.APIImpl;

public class PROAR {
	
	private API api;
	private int idExperiment;
	private int jobCPUTimeLimit;
	private Random rng;
	
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
	private float maxTuningTime; 
	
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
	
	/**List of (instance,seed)-pairs on which the solver configurations are going to be evaluated.
	 * The bestSC is always going to be the configuration that is farthest on this parcours. 
	 */
	//TODO: definiere eine Klasse oder andere Datenstruktur instance seed pair
	//private List<InstanceSeed> parcours;
	
	
	public PROAR(String hostname, int port, String database, String user, String password, int idExperiment, int jobCPUTimeLimit, long seed) throws Exception {
		api = new APIImpl();
		api.connect(hostname, port, database, user, password);
		this.idExperiment = idExperiment;
		this.jobCPUTimeLimit = jobCPUTimeLimit;
		rng = new edacc.util.MersenneTwister(seed);
	}
	
	/**
	 * Checks if there are solver configurations in the experiment that would match the configuration scenario
	 * if there are more than one such configuration it will pick the best one as the best configuration found so far 
	 */
	private void initializeBest(){
		
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
	 */
	private void expandParcoursSC(SolverConfiguration sc,int num){
		//TODO implement
		//fuer deterministische solver sollte man allerdings beachten, 
		//dass wenn alle instanzen schon verwendet wurden das der parcours nicht weiter erweitert werden kann.
		//fuer probabilistische solver kann der parcours jederzeit erweitert werden, jedoch 
		//waere ein Obergrenze sinvoll die als funktion der anzahl der instanzen definiert werden sollte
		//z.B: 10*#instanzen
	}
	
	/**
	 * Determines how many new solver configuration can be taken into consideration. 
	 */
	private int computeOptimalExpansion(){
		return 10;
		//TODO: was geschickteres implementieren, denn von diesem Wert haengt sehr stark der Grad der parallelisierung statt.
		//denkbar ware noch api.getNumComputingUnits(); wenn man die Methode haette. 
		
	}
	/**
	 * Generates num new solver configurations
	 * @return a List of the new solver configurations 
	 */
	private List<SolverConfiguration> generateNewSC(int num){
		//TODO: Implement
		//hier kann man die neuen konfigs aus der Liste der besten, dem besten selber und zulaellige configs erzeugen 
		//
		return null;
	}
	
	/** Generates num new random solver configurations
	 * @return a List of the new solver configurations 
	 */
	
	private List<SolverConfiguration> generateNewRandomSC(int num){
		//TODO: Implement
		return null;
	}
	
	public void start() {
		// TODO: implement PROAR
		//first initialize the best individual if there is a default or if there are already some solver configurations in the experiment
		initializeBest();
		int numNewSC = computeOptimalExpansion();
		while (!terminate()){
			expandParcoursSC(bestSC,1); //Problem, es kann sein, dass man auf diesen Ergebniss warten muss, 
			//da man sonst nicht weiter machen kann. Gerade am Anfang wo best sehr wenige Läufe hat.
			listBestSC.clear();
			this.listNewSC = generateNewSC(numNewSC);
		
			
		}
		
	}
	
	public void shutdown() {
		api.disconnect();
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
        }
        scanner.close();
        
        PROAR configurator = new PROAR(hostname, port, database, user, password, idExperiment, jobCPUTimeLimit, seed);
        configurator.start();
        configurator.shutdown();
	}
	
}
