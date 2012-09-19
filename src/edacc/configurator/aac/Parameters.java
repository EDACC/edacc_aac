package edacc.configurator.aac;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import edacc.api.costfunctions.CostFunction;
import edacc.util.Pair;

public class Parameters {
	String hostname = "", user = "", password = "", database = "";
	int port = 3306;
	int pollingInterval=2500; //value in ms
	int idExperiment = 0;
	int idExperimentEvaluation = -1;
	String evaluationSolverConfigName = "";
	boolean deleteSolverConfigsAtStart = false;
	int jobCPUTimeLimit = 10;
	int jobWallClockTimeLimit = -1;
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
	
	boolean deleteSolverConfigs = false;

	int minCPUCount = 0;
	int maxCPUCount = 0;
	float maxTuningTime = -1;
	int maxNumSC=-1;
	
	public int getMaxNumSC() {
		return maxNumSC;
	}

	StatisticFunction statistics;
	HashMap<String, String> searchMethodParams = new HashMap<String, String>();
	HashMap<String, String> racingMethodParams = new HashMap<String, String>();
	
	boolean simulation = false;
	boolean simulationGenerateInstance = false;
	int simulationCorecount = 8;
	long simulationSeed = searchSeed;
	
	List<String> instanceProperties = new LinkedList<String>();
	

	private boolean pnp = true; //parameters not parsed 
	/*
	 * Lists the help for all parameters with default values or list the settings 
	 */
	public List<String> getParameters(){
		List<String> p = new LinkedList<String>();
		if (pnp){
			p.add("\n%Parameters that are supported by EAAC version ... with their default values:");
			p.add("%If no default value listed -> specified by user!\n");
		}
		p.add("%---Database parameters---");
		p.add("host = "+this.hostname+ (pnp?" (name or IP of database host)":""));
		p.add("user = " + this.user + (pnp?" (database user)":""));
		p.add("password = " + this.password + (pnp?" (database user password)":""));
		p.add("port = "+ this.port + (pnp?"(database server port)":""));
		p.add("database = " + this.database + (pnp?" (name of database to use)":""));
		p.add("pollingInterval = " + this.pollingInterval + (pnp?" <int>(number of ms between two polls)":""));
		p.add("deleteSolverConfigsAtStart = " + this.deleteSolverConfigsAtStart + (pnp?" <boolean> (whether to delete solver configs at the beginning or not; can be useful for multiple runs on the same experiment)":""));
		p.add("%-----------------------");
		p.add("%");
		p.add("%---Experiment parameters---");
		p.add("idExperiment = " + this.idExperiment + (pnp?" <int>(id of experiment to run the configurator on)":""));
		p.add("idExperimentEvaluation = " + this.idExperiment + (pnp?" <int> (id of evaluation experiment; best solver config will be added to it at the end)" : ""));
		p.add("evaluationSolverConfigName = " + this.evaluationSolverConfigName + (pnp?" <String> (This string will be used as suffix for the solver config added to the evaluation experiment)" : ""));
		p.add("jobCPUTimeLimit = " +this.jobCPUTimeLimit + (pnp?" <int>(maximum number of CPU seconds a job is allowed to run)":""));
		p.add("jobWallClockTimeLimit = " +this.jobWallClockTimeLimit + (pnp?" <int>(maximum number of wall clock seconds a job is allowed to run)":""));
		p.add("deterministicSolver = " + this.deterministicSolver + (pnp?" <boolean>(0 for stochastic / 1 for determinisitc)":""));
		p.add("%-----------------------");
		p.add("%");
		p.add("%---Parcours parameters---");
		p.add("maxParcoursExpansionFactor = " + this.maxParcoursExpansionFactor + (pnp?" <int>(maxParcoursLength = maxParcoursExpansionFactor x numInstances)":""));
		p.add("parcoursExpansionPerStep = " + this.parcoursExpansionPerStep + (pnp?" <int>(number of jobs the parcours is expanded in each step)":""));
		p.add("initialDefaultParcoursLength = " + this.initialDefaultParcoursLength + (pnp?" <int>(initial length of the parcours)":""));
		p.add("%-----------------------");
		p.add("%");
		p.add("%---Configurator parameters---");
		p.add("searchSeed =  " + this.searchSeed + (pnp?" <long>(seed for search method)":""));
		p.add("racingSeed = " + this.racingSeed + (pnp?" <long>(seed for racing method)":""));
		p.add("searchMethod = " + this.searchMethod + (pnp?" <string>(method used to generate new SC)":""));
		p.add("racingMethod = " + this.racingMethod + (pnp?" <string>(method used to race SC)":""));
		p.add("costFunction = " + this.costFunc + (pnp?" <string>(cost function to be optimized)":""));
		p.add("minEvalsNewSC = " + this.minE + (pnp?" <int>(number of evaluations for new SCs)":""));
		p.add("maxTuningTime = " + this.maxTuningTime + (pnp?" <float>(maximum sum of CPU seconds for all generated jobs (-1 no limitation))":""));
		p.add("minCPUCount = " + this.minCPUCount + (pnp?" <int>(minimum number of CPU that should be available before starting the configuration proccess (0 no limitation))":""));
		p.add("maxCPUCount = " + this.maxCPUCount + (pnp?" <int>(maximum number of CPU that should be available before starting the configuration proccess (0 no limitation))":""));
		p.add("deleteSolverConfigs = " + this.deleteSolverConfigs + (pnp?" <boolean>(wheater to delete bad solver configs from DB or not)":""));
		p.add("maxNumSC = " + this.maxNumSC + (pnp?" <int>(maximum number of solver configurations that the configurator should generate (-1 no limitation))":""));
		String instanceProps = "";
		for (String ip: instanceProperties) instanceProps += ip + ", ";
		p.add("instanceProperties = " + instanceProps + (pnp?" <string>(list of comma separated instance properties that can be used by the configurator))":""));
		p.add("%-----------------------");
		p.add("%");
		p.add("%---Simulation parameters---");
		p.add("simulation = " + this.simulation + (pnp?" <boolean>(simulate configuration proccess within an full matrix experiment)":""));
		// TODO: maybe implement
		// System.out.println("simulationGenerateInstance = " + this.simulationGenerateInstance + (pnp? " <boolean>(whether to generate an instance to be used in edacc or not)":""));
		// if specified a serialized output of the experiment result cache + solver config cache will be generated and can be used as input instance
		// simulation process could be started without a connection to the db => no traffic needed after downloading the instance
		p.add("simulationCorecount = " +this.simulationCorecount + (pnp?" <int>(core count for computation units)":""));
		p.add("simulationSeed = " + this.simulationSeed + (pnp?" <long>(seed for simulation)":""));
		p.add("%-----------------------");
		p.add("%");
		return p;
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
			else if ("pollingInterval".equalsIgnoreCase(key))
				pollingInterval = Integer.valueOf(value);
			else if ("deleteSolverConfigsAtStart".equalsIgnoreCase(key))
				deleteSolverConfigsAtStart = Boolean.parseBoolean(value);
			// experiment parameters
			else if ("idExperiment".equalsIgnoreCase(key))
				idExperiment = Integer.valueOf(value);
			else if ("idExperimentEvaluation".equalsIgnoreCase(key))
				idExperimentEvaluation = Integer.valueOf(value);
			else if ("evaluationSolverConfigName".equalsIgnoreCase(key))
				evaluationSolverConfigName = value;
			else if ("jobCPUTimeLimit".equalsIgnoreCase(key))
				jobCPUTimeLimit = Integer.valueOf(value);
            else if ("jobWallClockTimeLimit".equalsIgnoreCase(key))
                jobWallClockTimeLimit = Integer.valueOf(value);
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
			
			else if (key.equalsIgnoreCase("maxTuningTime"))
				maxTuningTime = Integer.valueOf(value);
			else if (key.equalsIgnoreCase("minCPUCount"))
				minCPUCount = Integer.valueOf(value);
			else if (key.equalsIgnoreCase("maxCPUCount"))
				maxCPUCount = Integer.valueOf(value);
			else if (key.equalsIgnoreCase("deleteSolverConfigs"))
				deleteSolverConfigs = Boolean.parseBoolean(value);
			else if (key.equalsIgnoreCase("maxNumSC"))
				maxNumSC = Integer.valueOf(value);
			// simulation parameters
			else if (key.equalsIgnoreCase("simulation"))
				simulation = Boolean.parseBoolean(value);
			else if (key.equalsIgnoreCase("simulationGenerateInstance"))
				simulationGenerateInstance = Boolean.parseBoolean(value);
			else if (key.equalsIgnoreCase("simulationCorecount"))
				simulationCorecount = Integer.parseInt(value);
			else if (key.equalsIgnoreCase("simulationSeed"))
				simulationSeed = Long.parseLong(value);
			else if (key.equalsIgnoreCase("instanceProperties")) {
			    String[] props = value.split(",\\s+");
			    instanceProperties = new LinkedList<String>();
			    for (String s: props) instanceProperties.add(s);
			}
			else {
				System.err.println("unrecognized parameter:" + " '" + key + "' " + " terminating! \n Valid Parameters for EAAC:");
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
	
    public int getJobWallClockTimeLimit() {
        return jobWallClockTimeLimit;
    }
	
	public boolean isDeleteSolverConfigs() {
		return deleteSolverConfigs;
	}
	
	public int getIdExperimentEvaluation() {
	    return idExperimentEvaluation;
	}
	
	public String getEvaluationSolverConfigName() {
	    return evaluationSolverConfigName;
	}
	
	public long getRacingSeed() {
	    return racingSeed;
	}
	
	public List<String> getInstanceProperties() {
	    return instanceProperties;
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
