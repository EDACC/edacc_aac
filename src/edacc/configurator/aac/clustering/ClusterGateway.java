package edacc.configurator.aac.clustering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.api.costfunctions.CostFunction;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.ConfigurationScenarioDAO;
import edacc.model.ExperimentResult;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;

public class ClusterGateway implements ClusterMethods{
	API api;
	AAC pacc;
	Parameters parameters;
	List<SolverConfiguration> firstSCs;
	List<SolverConfiguration> referenceSCs;
	Random rng;
	int num_instances;
	// Graph representation of the parameter pool. Used to create new
	// parameter configurations
	ParameterGraph paramGraph;
	SolverConfiguration bestSC;
	String clusteringMethod = "CLC_Clustering";
	// A set of fully evaluated SCs is required to create an initial
	// clustering. The number of those SCs is defined in this variable
	int numberOfMinStartupSCs = 4;
	// Interface for a clustering-algorithm
	Class<?> clusterClass;
	ClusterMethods clusterHandler;
	boolean useProperties = false;

	public ClusterGateway(AAC pacc, Random rng, API api, Parameters parameters,
			List<SolverConfiguration> firstSCs,
			List<SolverConfiguration> referenceSCs) throws Exception {
		this.api = api;
		this.rng = rng;
		this.pacc = pacc;
		this.parameters = parameters;
		this.firstSCs = firstSCs;
		this.referenceSCs = referenceSCs;
		paramGraph = api.loadParameterGraphFromDB(parameters.getIdExperiment());
		num_instances = ConfigurationScenarioDAO
				.getConfigurationScenarioByExperimentId(
						parameters.getIdExperiment()).getCourse()
				.getInitialLength();
		HashMap<String, String> params = parameters.getRacingMethodParameters();
		if(params.containsKey("Clustering_clusteringMethod")) {
			clusteringMethod = params.get("Clustering_clusteringMethod");
		}
		if (params.containsKey("Clustering_minStartupSCs")) {
			numberOfMinStartupSCs = Integer.parseInt(params
					.get("Clustering_minStartupSCs"));
		}
		if(params.containsKey("Clustering_useProperties")) {
			useProperties = Boolean.parseBoolean(params.get("Clustering_useProperties"));
		}
		// Initialize Clustering
		clusterClass = ClassLoader.getSystemClassLoader().loadClass(
				"edacc.configurator.aac.clustering." + clusteringMethod);
		if (useProperties) {
			clusterHandler = (ClusterMethods) clusterClass
					.getDeclaredConstructors()[0].newInstance(pacc, parameters,
					api, rng, null);
		} else {
			clusterHandler = (ClusterMethods) clusterClass
					.getDeclaredConstructors()[0].newInstance(pacc, parameters,
					api, rng, initClustering());
		}
	}

	private List<SolverConfiguration> initClustering() throws Exception {
		// Gathers a list of SCs to initialize the Clusters and of course the
		// incumbent
		List<SolverConfiguration> startupSCs = new ArrayList<SolverConfiguration>();
		log("Initialisation with the following SCs ...");
		// All reference SCs are added
		log("Reference SCs...");
		if (referenceSCs.size() > 0) {
			for (SolverConfiguration refSc : referenceSCs) {
				log(startupSCs.size() + ": " + refSc.getName());
				startupSCs.add(refSc);
			}
		}
		// Default SCs are added. The maximum is the given number of startup SCs
		int defaultSCs = Math.min(firstSCs.size(), numberOfMinStartupSCs);
		log("Default SCs...");
		for (int i = 0; i < defaultSCs; i++) {
			pacc.log("c " + startupSCs.size() + ": "
					+ firstSCs.get(i).getName());
			startupSCs.add(firstSCs.get(i));
		}
		// At least (number of minimal startupSCs)/2 random SCs are added.
		// Improves the reliability of the predefined data.
		log("Random SCs...");
		int randomSCs = Math.max((int) (numberOfMinStartupSCs / 2), numberOfMinStartupSCs - startupSCs.size());
		for (int i = 0; i < randomSCs; i++) {
			ParameterConfiguration randomConf = paramGraph
					.getRandomConfiguration(rng);
			try {
				int scID = api.createSolverConfig(parameters.getIdExperiment(),
						randomConf, api.getCanonicalName(
								parameters.getIdExperiment(), randomConf));

				SolverConfiguration randomSC = new SolverConfiguration(scID,
						randomConf, parameters.getStatistics());
				startupSCs.add(randomSC);
				log(startupSCs.size() + ": " + scID);
			} catch (Exception e) {
				log("Error - A new random configuration could not be created for the initialising of the clustering!");
				e.printStackTrace();
			}
		}
		// Run the configs on the whole parcour length
		log("Adding jobs for the initial SCs...");
		for (SolverConfiguration sc : startupSCs) {
			int expansion = 0;
			if (sc.getJobCount() < parameters.getMaxParcoursExpansionFactor()
					* num_instances) {
				expansion = parameters.getMaxParcoursExpansionFactor()
						* num_instances - sc.getJobCount();
				pacc.expandParcoursSC(sc, expansion);
			}
			if (expansion > 0) {
				log("Expanding parcours of SC "
						+ sc.getIdSolverConfiguration() + " by " + expansion);
			}
		}
		// Wait for the configs to finish
		boolean finished = false;
		while (!finished) {
			finished = true;
			log("Waiting for initial SCs to finish their jobs");
			for (SolverConfiguration sc : startupSCs) {
				pacc.updateJobsStatus(sc);
				if (!(sc.getNotStartedJobs().isEmpty() && sc.getRunningJobs()
						.isEmpty())) {
					finished = false;
				}
				pacc.sleep(1000);
			}
		}
		// Set bestSc
		float bestCost = Float.MAX_VALUE;
		for (SolverConfiguration sc : startupSCs) {
			if (sc.getCost() < bestCost) {
				bestCost = sc.getCost();
				bestSC = sc;
			}
		}
		return startupSCs;
	}
	
	public void log(String msg) {
		pacc.log(" Clustering: "+msg);
	}
	
	public SolverConfiguration initBestSC() {
		return bestSC;
	}

	@Override
	public List<ExperimentResult>[] mapResultsToClusters(SolverConfiguration sc) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int[] countRunPerCluster(SolverConfiguration sc) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int clusterOfInstance(ExperimentResult res) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public InstanceIdSeed getInstanceInCluster(int clusterNr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public InstanceIdSeed getInstanceInCluster(int clusterNr,
			SolverConfiguration solverConfig) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<InstanceIdSeed> getInstancesInCluster(int clusterNr,
			SolverConfiguration sc, int numberOfInstances) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addDataForClustering(SolverConfiguration sc) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public List<InstanceIdSeed> getClusterInstances(int clusterNumber) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void visualiseClustering() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Costs costs(SolverConfiguration sc, SolverConfiguration competitor,
			CostFunction costFunc) {
		// TODO Auto-generated method stub
		return null;
	}
}
