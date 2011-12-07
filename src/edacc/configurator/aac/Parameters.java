package edacc.configurator.aac;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import edacc.api.costfunctions.CostFunction;
import edacc.util.Pair;

public class Parameters {
	String hostname = "", user = "", password = "", database = "";
	int port = 3306;
	int pollingInterval=2500; //value in ms
	int idExperiment = 0;
	int jobCPUTimeLimit = 10;
	boolean deterministicSolver = false;
	
	int parcoursExpansionPerStep = 1;
	int maxParcoursExpansionFactor = 4;
	int initialDefaultParcoursLength = 5;
	
	String costFunc = "par10";
	
	long searchSeed = System.currentTimeMillis();
	long racingSeed = searchSeed;
	String searchMethod = "ROAR";
	String racingMethod = "Default";
	
	int minE = 1;
	
	boolean minimize = true;
	
	boolean deleteSolverConfigs = false;

	int minCPUCount = 0;
	int maxCPUCount = 0;
	float maxTuningTime = -1;
	
	StatisticFunction statistics;
	HashMap<String, String> searchMethodParams = new HashMap<String, String>();
	HashMap<String, String> racingMethodParams = new HashMap<String, String>();
	
	boolean simulation = false;
	boolean simulationGenerateInstance = false;
	int simulationCorecount = 8;
	long simulationSeed = searchSeed;
	
	

	private boolean pnp = true; //parameters not parsed 
	/*
	 * Lists the help for all parameters with default values or list the settings 
	 */
	public void listParameters(){
		if (pnp){
			System.out.println("\n%Parameters that are supported by EAAC version ... with their default values:");
			System.out.println("%If no default value listed -> specified by user!\n");
		}
		System.out.println("%---Database parameters---");
		System.out.println("host = "+this.hostname+ (pnp?" (name or IP of database host)":""));
		System.out.println("user = " + this.user + (pnp?" (database user)":""));
		System.out.println("password = " + this.password + (pnp?" (database user password)":""));
		System.out.println("port = "+ this.port + (pnp?"(database server port)":""));
		System.out.println("database = " + this.database + (pnp?" (name of database to use)":""));
		System.out.println("pollingIntervall = " + this.pollingInterval + (pnp?" <int>(number of ms between two polls)":""));
		System.out.println("%-----------------------\n");
		System.out.println("%---Experiment parameters---");
		System.out.println("idExperiment = " + this.idExperiment + (pnp?" <int>(id of experiment to run the configurator on)":""));
		System.out.println("jobCPUTimeLimit = " +this.jobCPUTimeLimit + (pnp?" <int>(maximum number of CPU seconds a job is allowed to run)":""));
		System.out.println("deterministicSolver = " + this.deterministicSolver + (pnp?" <boolean>(0 for stochastic / 1 for determinisitc)":""));
		System.out.println("%-----------------------\n");
		System.out.println("%---Parcours parameters---");
		System.out.println("maxParcoursExpansionFactor = " + this.maxParcoursExpansionFactor + (pnp?" <int>(maxParcoursLength = maxParcoursExpansionFactor x numInstances)":""));
		System.out.println("parcoursExpansionPerStep = " + this.parcoursExpansionPerStep + (pnp?" <int>(number of jobs the parcours is expanded in each step)":""));
		System.out.println("initialDefaultParcoursLength = " + this.initialDefaultParcoursLength + (pnp?" <int>(initial length of the parcours)":""));
		System.out.println("%-----------------------\n");
		System.out.println("%---Configurator parameters---");
		System.out.println("searchSeed =  " + this.searchSeed + (pnp?" <long>(seed for search method)":""));
		System.out.println("racingSeed = " + this.racingSeed + (pnp?" <long>(seed for racing method)":""));
		System.out.println("searchMethod = " + this.searchMethod + (pnp?" <string>(method used to generate new SC)":""));
		System.out.println("racingMethod = " + this.racingMethod + (pnp?" <string>(method used to race SC)":""));
		//System.out.println("<searchMethod>_<name> = " + this.ser + (pnp?" (additionel parameters for search method>":""));
		//System.out.println("<racingMethod>_<name> = " +  + (pnp?" (additionel parameters for racing method>":""));
		System.out.println("costFunction = " + this.costFunc + (pnp?" <string>(cost function to be optimized)":""));
		System.out.println("minEvalsNewSC = " + this.minE + (pnp?" <int>(number of evaluations for new SCs)":""));
		System.out.println("minimize = " + this.minimize + (pnp?" <boolean>(1 for mimizing cost function / 0 for maximizing)":""));
		System.out.println("maxTuningTime = " + this.maxTuningTime + (pnp?" <float>(maximum sum of CPU seconds for all generated jobs (-1 no limitation))":""));
		System.out.println("minCPUCount = " + this.minCPUCount + (pnp?" <int>(minimum number of CPU that should be available before starting the configuration proccess (0 no limitation))":""));
		System.out.println("maxCPUCount = " + this.maxCPUCount + (pnp?" <int>(maximum number of CPU that should be available before starting the configuration proccess (0 no limitation))":""));
		System.out.println("deleteSolverConfigs = " + this.deleteSolverConfigs + (pnp?" <boolean>(wheater to delete bad solver configs from DB or not)":""));
		System.out.println("%-----------------------\n");
		System.out.println("%---Simulation parameters---");
		System.out.println("simulation = " + this.simulation + (pnp?" <boolean>(simulate configuration proccess within an full matrix experiment)":""));
		// TODO: maybe implement
		// System.out.println("simulationGenerateInstance = " + this.simulationGenerateInstance + (pnp? " <boolean>(whether to generate an instance to be used in edacc or not)":""));
		// if specified a serialized output of the experiment result cache + solver config cache will be generated and can be used as input instance
		// simulation process could be started without a connection to the db => no traffic needed after downloading the instance
		System.out.println("simulationCorecount = " +this.simulationCorecount + (pnp?" <int>(core count for computation units)":""));
		System.out.println("simulationSeed = " + this.simulationSeed + (pnp?" <long>(seed for simulation)":""));
		System.out.println("%-----------------------\n");
		//TODO : add parameter that generated generic config file!
	}
	
	public void generateGenericConfigFile(){//TODO
		try 
		{
			FileWriter fstream = new FileWriter("generic_config");
			BufferedWriter outputConfig = new BufferedWriter(fstream);
		} catch (IOException e) {
			
			e.printStackTrace();
		}
		
	}
	
	public boolean parseParameters(List<Pair<String, String>> params) {
		for (Pair<String, String> p : params) {
			String key = p.getFirst();
			String value = p.getSecond();
			// database parameters
			if ("host".equalsIgnoreCase(key))
				hostname = value;
			else if ("user".equalsIgnoreCase(key))
				user = value;
			else if ("password".equalsIgnoreCase(key))
				password = value;
			else if ("port".equalsIgnoreCase(key))
				port = Integer.valueOf(value);
			else if ("database".equalsIgnoreCase(key))
				database = value;
			// experiment parameters
			else if ("idExperiment".equalsIgnoreCase(key))
				idExperiment = Integer.valueOf(value);
			else if ("jobCPUTimeLimit".equalsIgnoreCase(key))
				jobCPUTimeLimit = Integer.valueOf(value);
			else if ("deterministicSolver".equalsIgnoreCase(key))
				deterministicSolver = Boolean.parseBoolean(value);
			// parcours parameters
			else if ("maxParcoursExpansionFactor".equalsIgnoreCase(key))
				maxParcoursExpansionFactor = Integer.valueOf(value);
			else if ("parcoursExpansionPerStep".equalsIgnoreCase(key))
				parcoursExpansionPerStep = Integer.valueOf(value);
			else if ("initialDefaultParcoursLength".equalsIgnoreCase(key))
				initialDefaultParcoursLength = Integer.valueOf(value);
			// configurator parameters
			else if ("searchSeed".equalsIgnoreCase(key))
				searchSeed = Long.valueOf(value);
			else if ("racingSeed".equalsIgnoreCase(key))
				racingSeed = Long.valueOf(value);

			else if ("searchMethod".equalsIgnoreCase(key))
				searchMethod = value;
			else if (key.equalsIgnoreCase("racingMethod"))
				racingMethod = value;

			else if (key.startsWith(searchMethod + "_"))
				searchMethodParams.put(key, value);
			else if (key.startsWith(racingMethod + "_"))
				racingMethodParams.put(key, value);

			else if ("minEvalsNewSC".equalsIgnoreCase(key))
				minE = Integer.parseInt(value);// TODO: minE muss kleiner sein
												// als
												// initialDefaultParcoursLength

			else if ("costFunction".equalsIgnoreCase(key))
				costFunc = value;
			else if ("minimize".equalsIgnoreCase(key))
				minimize = Boolean.parseBoolean(value);

			else if (key.equalsIgnoreCase("maxTuningTime"))
				maxTuningTime = Integer.valueOf(value);
			else if (key.equalsIgnoreCase("minCPUCount"))
				minCPUCount = Integer.valueOf(value);
			else if (key.equalsIgnoreCase("maxCPUCount"))
				maxCPUCount = Integer.valueOf(value);
			// simulation parameters
			else if (key.equalsIgnoreCase("simulation"))
				simulation = Boolean.parseBoolean(value);
			else if (key.equalsIgnoreCase("simulationGenerateInstance"))
				simulationGenerateInstance = Boolean.parseBoolean(value);
			else if (key.equalsIgnoreCase("simulationCorecount"))
				simulationCorecount = Integer.parseInt(value);
			else if (key.equalsIgnoreCase("simulationSeed"))
				simulationSeed = Long.parseLong(value);

			else {
				System.err.println("unrecognized parameter:" + " '" + key + "' " + " terminating! \n Valid Parameters for EAAC:");
				listParameters();
				return false;
				
			}
		}
		if (simulation) //within simultations do NOT delete Solver Configurations!!!
			deleteSolverConfigs = false;
		this.pnp = false;
		return true;
	}

	public boolean isDeterministicSolver() {
		return deterministicSolver;
	}
	public void setStatistics(CostFunction costFunc, boolean minimize) {
		this.statistics = new StatisticFunction(costFunc, minimize);
	}
	
	public int getMinRuns() {
		return minE;
	}
	
	/**
	 * Returns the statistic function to be used. 
	 * @return statistic function
	 */
	public StatisticFunction getStatistics() {
		return statistics;
	}
	
	public HashMap<String, String> getSearchMethodParameters() {
		return searchMethodParams;
	}
	
	public HashMap<String, String> getRacingMethodParameters() {
		return racingMethodParams;
	}
	
	/**
	 * The number of jobs that a configuration gets when its parcours is
	 * expanded
	 * @return
	 */
	public int getParcoursExpansion() {
		return parcoursExpansionPerStep;
	}
	
	/**
	 * maximum allowed tuning time = sum over all jobs in seconds 
	 * @return
	 */
	public float getMaxTuningTime() {
		return maxTuningTime;
	}
	
	/**
	 * The maximum length of the parcours that will be generated by the
	 * configurator as a factor of the number of instances; i.e.: if the
	 * maxParcoursExpansionFactor = 10 and we have 250 instances in the
	 * configuration experiment then the maximum length of the parcours will be
	 * 2500.
	 * @return
	 */
	public int getMaxParcoursExpansionFactor() {
		return maxParcoursExpansionFactor;
	}
	
	public int getInitialDefaultParcoursLength() {
		return initialDefaultParcoursLength;
	}
	
	public int getMinCPUCount() {
		return minCPUCount;
	}
	
	public int getMaxCPUCount() {
		return maxCPUCount;
	}
	
	public int getIdExperiment() {
		return idExperiment;
	}
	
	public int getJobCPUTimeLimit() {
		return jobCPUTimeLimit;
	}
	
	public boolean isDeleteSolverConfigs() {
		return deleteSolverConfigs;
	}
	
	/*public String toString() {//TODO : rewrite!!!
		String paramsForAlgo = "";
		for (String key : searchMethodParams.keySet()) {
			paramsForAlgo += "(" + key + "," + searchMethodParams.get(key) + ") ";
		}
		return "c Host: " + user + ":xxxxx" + "@" + hostname + ":" + port + "/" + database + "\n" 
		+ "c Experiment Id: " + idExperiment + "\n" 
		+ "c Algorithm: " + searchMethod + "\n" 
		+ "c Racing Schema: " + racingMethod + "\n" 
		+ "c Optimizing statistic: " + costFunc + "\n" 
		+ "c towards: " + (minimize ? "mimisation" : "maximisation") + "\n" 
		+ "c Parcours expansion pro step: " + parcoursExpansionPerStep + "\n" 
		+ "c Maximum parcours expansion factor: " + maxParcoursExpansionFactor + "\n" 
		+ "c Initial parcours for first config: " + initialDefaultParcoursLength + "\n" 
		+ "c Minimum number of runs for a new solver config: " + minE + "\n"
		+ "c CPU time limit: " + jobCPUTimeLimit + "\n" 
		+ "c Maximum tuning time: " + (maxTuningTime == -1 ? "unlimited" : maxTuningTime) + "\n" 
		+ "c Seed: " + seedSearch + "\n" + "c Parameters for algorithm: " + paramsForAlgo + "\n";
	}*/
}
