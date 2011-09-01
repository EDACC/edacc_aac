package edacc.configurator.proar;

import java.io.File;
import java.util.Random;
import java.util.Scanner;

import edacc.api.API;
import edacc.api.APIImpl;

public class PROAR {
	
	private API api;
	private int idExperiment;
	private int jobCPUTimeLimit;
	private Random rng;
	//tells if the cost function is to be minimized; if 0 it should be maximized
	private boolean minimize; 
		
	//TODO : statstische funktion noch auswaehlen
	//private statistics  
	
	//if timeObjective true the configurator will optimize the runtime, else the cost function
	private boolean timeObjective; 
	
	//maximum allowed tuning time = sum over all jobs in seconds
	private float maxTuningTime; 
	
	//total cumulated time of all jobs the configurator has started so far in seconds
	private float cumulatedCPUTime;
	
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
	 * Determines if the termination criteria has been met
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
	public void start() {
		// TODO: implement PROAR
		//first initialize the best individual if there is a default or if there are already some solver configurations in the experiment
		initializeBest();
		
		while (!terminate()){
			
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
