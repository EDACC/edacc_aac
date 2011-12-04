package edacc.configurator.aac;

import java.util.HashMap;
import java.util.List;

import edacc.api.costfunctions.CostFunction;
import edacc.util.Pair;

public class Parameters {
	String hostname = "", user = "", password = "", database = "";
	int port = 3306;
	
	int idExperiment = 0;
	int jobCPUTimeLimit = 10;
	boolean deterministicSolver = false;
	
	int parcoursExpansionPerStep = 1;
	int maxParcoursExpansionFactor = 4;
	int initialDefaultParcoursLength = 5;
	
	String costFunc = "par10";
	
	long seedSearch = System.currentTimeMillis();
	long seedRacing = seedSearch;
	String searchMethod = "ROAR";
	String racingMethod = "Default";
	
	int minE = 1;
	
	boolean minimize = true;
	
	
	int minCPUCount = 0;
	int maxCPUCount = 0;
	float maxTuningTime = -1;
	
	StatisticFunction statistics;
	HashMap<String, String> searchMethodParams = new HashMap<String, String>();
	HashMap<String, String> racingMethodParams = new HashMap<String, String>();
	
	boolean simulation = false;
	boolean simulation_generate_instance = false;
	float simulation_multiplicator = 100.f;
	int simulation_corecount = 8;
	long simulation_seed = System.currentTimeMillis();
	
	public void showHelp(){
		System.out.println("Parameters that should/can be specified in the configuration file!");
		System.out.println("---Database parameters---");
		System.out.println("host = <name or IP of database host>");
		System.out.println("user = <database user>");
		System.out.println("password = <database user password>");
		System.out.println("port = <database server port> (default = 3306)");
		System.out.println("database = <name of database to use>");
		System.out.println("-----------------------\n");
		System.out.println("---Experiment parameters---");
		System.out.println("idExperiment = <id of experiment to run the configurator on>");
		System.out.println("jobCPUTimeLimit = <maximum number of CPU seconds a job is allowed to run> (default = 10");
		System.out.println("deterministicSolver = <0 for stochastic / 1 for determinisitc > (default = 0)");
		System.out.println("-----------------------\n");
		System.out.println("---Parcours parameters---");
		System.out.println("maxParcoursExpansionFactor = <maxParcoursLength = maxParcoursExpansionFactor x numInstances > (default = 4)");
		System.out.println("parcoursExpansionPerStep = <number of jobs the parcours is expanded in each step> (default = 1");
		System.out.println("initialDefaultParcoursLength = <initial length of the parcours> (default = 5)");
		System.out.println("-----------------------\n");
		System.out.println("---Configurator parameters---");
		System.out.println("seedSearch = <seed for search method> (default = currentTime())");
		System.out.println("seedRacing = <seed for racing method> (default = currentTime())");
		System.out.println("searchMethod = <method used to generate new SC> (default = ROAR)");
		System.out.println("racingMethod = <method used to race SC> (default = ROAR)");
		System.out.println("<searchMethod>_<name> = <additionel parameters for search method>");
		System.out.println("<racingMethod>_<name> = <additionel parameters for racing method>");
		System.out.println("costFunction = <cost function to be optimized> (default = par10)");
		System.out.println("minEvalsNewSC = <number of evaluations for new SCs> (default = 1)");
		System.out.println("minimize = <1 for mimizing cost function / 0 for maximizing> (default = 1)");
		System.out.println("maxTuningTime = <maximum sum of CPU seconds for all generated jobs (-1 no limitation)> (default = -1)");
		System.out.println("minCPUCount = <minimum number of CPU that should be available before starting the configuration proccess (0 no limitation)> (default = 0)");
		System.out.println("maxCPUCount = <maximum number of CPU that should be available before starting the configuration proccess (0 no limitation)> (default = 0)");
		System.out.println("-----------------------\n");
		System.out.println("---Simulation parameters---");
		System.out.println("simulation = <1/0> (default = 0)");
		System.out.println("simulation_generate_instance = <1/0> (default = 0): whether to generate an instance to be used in edacc or not");
		System.out.println("simulation_multiplicator = <float> (default: 100): 1000 would be real time, 100 real time divided by 10, etc.");
		System.out.println("simulation_corecount = <int> (default: 8): core count for computation units");
		System.out.println("simulation_seed = <seed for simulation> (default = currentTime())");
		System.out.println("-----------------------\n");
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
			else if ("seedSearch".equalsIgnoreCase(key))
				seedSearch = Long.valueOf(value);
			else if ("seedRacing".equalsIgnoreCase(key))
				seedRacing = Long.valueOf(value);

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
			else if (key.equalsIgnoreCase("simulation_generate_instance"))
				simulation_generate_instance = Boolean.parseBoolean(value);
			else if (key.equalsIgnoreCase("simulation_corecount"))
				simulation_corecount = Integer.parseInt(value);
			else if (key.equalsIgnoreCase("simulation_multiplicator"))
				simulation_multiplicator = Float.parseFloat(value);
			else if (key.equalsIgnoreCase("simulation_seed"))
				simulation_seed = Long.parseLong(value);

			else {
				System.err.println("unrecognized parameter:" + " '" + key + "' " + " terminating! \n Valid Parameters for AACE:");
				showHelp();
				return false;
			}
		}
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
	
	public String toString() {//TODO : rewrite!!!
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
	}
}
