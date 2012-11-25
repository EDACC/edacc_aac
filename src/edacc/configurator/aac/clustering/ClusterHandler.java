package edacc.configurator.aac.clustering;

import edacc.api.API;
import edacc.api.costfunctions.CostFunction;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.ConfigurationScenarioDAO;
import edacc.model.Course;
import edacc.model.ExperimentResult;
import edacc.model.InstanceSeed;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;

import java.util.*;

/**
 * @author mugrauer, schulte
 */
public class ClusterHandler implements ClusterMethods{
    protected Random rng;
    protected AAC aac;
    protected API api;
    protected int expID;
    protected HashMap<InstanceIdSeed, InstanceData> data;    
    protected HashMap<InstanceIdSeed, Integer> instanceClusterMap;
    protected Cluster[] clusters;
	
    private Parameters params;
    private List<SolverConfiguration> firstSCs;
    private List<SolverConfiguration> referenceSCs;
    private int num_instances;
	// Graph representation of the parameter pool. Used to create new
	// parameter configurations
    private ParameterGraph paramGraph;
    private SolverConfiguration bestSC;
    private String algorithmName = "Algorithm_CLC";
    private String resourcesName = "Resources_Properties";
	// A set of fully evaluated SCs is required to create an initial
	// clustering. The number of those SCs is defined in this variable
    private int numberOfMinStartupSCs = 1;
	// Interface for a clustering-algorithm
    private Class<?> algorithmClass;
    private Class<?> resourcesClass;
    private ClusteringAlgorithm algorithm;
    private ClusteringResources resources;
    private ClusterHandler clusterHandler;
    
    //states how much information (= number of solverconfigs) has been added to data since 
    //the clustering has been calculated
    private int newDataAvailable = 0;
    //states how much CPU-Time has been used up by the configurator since the clustering
    //has ben calculated
    private double cpuTimeElapsed = 0d;
    
    //whether to use time- or data-volume-based recalculatioon
    private boolean useTimeBasedRecalc = true;
    //Threshold for when to recalculate the clustering
    //if useTimeBasedRecalc is set, this means the CPUTime spent since the last recalculation
    //otherwise, this means the number of solverconfigs whose data has been added since the last recalculation
    private int recalculationThreshold = 38400;
    
    
    //when a random instance is to be selected from a cluster, should
    //instances with high variance of cost have a higher chance of being picked?
    //(higher variance in cost means the instance might be more useful in distinguishing
    // between configs with good or poor perfomance)
    protected boolean preferHighVarianceInstances = true;
    
    public ClusterHandler(AAC aac, Parameters params, API api, Random rng, 
    		List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception{
        this.rng = rng;
        this.aac = aac;
        this.api = api;
        this.expID = params.getIdExperiment();
        this.params = params;
        this.firstSCs = firstSCs;
		this.referenceSCs = referenceSCs;
		paramGraph = api.loadParameterGraphFromDB(params.getIdExperiment());
		num_instances = ConfigurationScenarioDAO
				.getConfigurationScenarioByExperimentId(
						params.getIdExperiment()).getCourse()
				.getInitialLength();
		HashMap<String, String> parameters = params.getRacingMethodParameters();
		if(parameters.containsKey("Clustering_algorithm")) {
			algorithmName = parameters.get("Clustering_algorithm");
		}
		if(parameters.containsKey("Clustering_resources")) {
			resourcesName = parameters.get("Clustering_resources");
		}
		if (parameters.containsKey("Clustering_minStartupSCs")) {
			numberOfMinStartupSCs = Integer.parseInt(parameters
					.get("Clustering_minStartupSCs"));
		}
        // Initialise Algorithms and Resources here
		algorithmClass = ClassLoader.getSystemClassLoader().loadClass(
				"edacc.configurator.aac.clustering." + algorithmName);
		resourcesClass = ClassLoader.getSystemClassLoader().loadClass(
				"edacc.configurator.aac.clustering." + resourcesName);
		resources = (ClusteringResources) resourcesClass
				.getDeclaredConstructors()[0].newInstance(api, params, this);
		algorithm = (ClusteringAlgorithm) algorithmClass
				.getDeclaredConstructors()[0].newInstance(aac, resources, this);
        // End algorithm+resources initialisation
        
        //initialise data
        instanceClusterMap = new HashMap<InstanceIdSeed, Integer>();
        data = new HashMap<InstanceIdSeed, InstanceData>();
        Course course = api.getCourse(params.getIdExperiment());       
        for(InstanceSeed is : course.getInstanceSeedList()){
            data.put(new InstanceIdSeed(is.instance.getId(), is.seed), 
                        new InstanceData(is.instance.getId(), is.seed, params.getStatistics().getCostFunction()));
        }
        if(resources.isInitialDataRequired(resourcesName)) {
        	List<SolverConfiguration> startupSCs = initClusteringData();
        	for (SolverConfiguration sc : startupSCs) {
				addData(sc);
			}
        } else {
        	numberOfMinStartupSCs = 1;
        	initClusteringData();
        }
        
        calculateClustering();
        cpuTimeElapsed = getCPUTime();
        visualiseClustering();
    }
    
    private void calculateClustering(){
	clusters = algorithm.calculateClustering(resources.prepareInstances());
	clusters = resources.establishClustering(clusters);
        instanceClusterMap.clear();
        for(int i=0; i<clusters.length; i++){
            for(InstanceIdSeed idSeed : clusters[i].getInstances()){
                instanceClusterMap.put(idSeed, i);
            }
        }
    }
    
    /**
         * Maps the ExperimentResults of a given SolverConfiguration to the clusters their instance-seed-pairs
         * belong to
         * 
         * @param sc the config for which the results should be mapped 
         * @return An array containing Lists of ExperimentResults. Each position in the array corresponds to one
         *          cluster, e.g. array[5] would give you the list of Results for cluster 5.
         */
    public List<ExperimentResult>[] mapResultsToClusters(SolverConfiguration sc){
        LinkedList<ExperimentResult>[] resultClusterMap = new LinkedList[clusters.length];
        for(int i=0; i<clusters.length; i++){
            resultClusterMap[i] = new LinkedList<ExperimentResult>();
        }
        
        int clusterNr;
        InstanceIdSeed idSeed;
        for(ExperimentResult r : sc.getFinishedJobs()){
            idSeed = new InstanceIdSeed(r.getInstanceId(), r.getSeed());
            clusterNr = instanceClusterMap.get(idSeed);
            resultClusterMap[clusterNr].add(r);
        }
        return resultClusterMap;
    }
    
    public int[] countRunPerCluster(SolverConfiguration sc){
        int[] numRuns = new int[clusters.length];
        for(int i=0; i<clusters.length; i++)
            numRuns[i] = clusters[i].countRuns(sc);
        return numRuns;
    }
   
    public int clusterOfInstance(ExperimentResult res) {
        if(res == null)
            return -1;
        InstanceIdSeed inst = new InstanceIdSeed(res.getInstanceId(), res.getSeed());
        for(int i=0; i<clusters.length; i++)
            if(clusters[i].contains(inst))
                return i;
        return -1;
    }

    public InstanceIdSeed getInstanceInCluster(int clusterNumber) {
        return getRandomInstance(clusters[clusterNumber].getInstances());
    }
    public InstanceIdSeed getInstanceInCluster(int clusterNr, SolverConfiguration solverConfig){
        List<InstanceIdSeed> clusterInstances = clusters[clusterNr].getInstances();
        List<InstanceIdSeed> tmpList = getInstances(solverConfig);
        clusterInstances.removeAll(tmpList);//clusterInstances is a copy of the cluster's list -> no side effects
        InstanceIdSeed newInstance = getRandomInstance(clusterInstances);
        if(tmpList.contains(newInstance))
            System.out.println("ERROR: getInstanceInCluster returns duplicate Instance!!");
        if(!clusters[clusterNr].contains(newInstance))
            System.out.println("ERROR: getInstanceInCluster returns an instance not present in this cluster!");
        return newInstance;
    }
    
    public List<InstanceIdSeed> getInstancesInCluster(int clusterNr, SolverConfiguration sc, int numOfConfigs){
        List<InstanceIdSeed> clusterInstances = clusters[clusterNr].getInstances();
        List<InstanceIdSeed> tmpList = getInstances(sc);
        clusterInstances.removeAll(tmpList);
        if(numOfConfigs > clusterInstances.size())
            return null;    //expected behaviour, see documentation in ClusterMethods interface
        
        LinkedList<InstanceIdSeed> resultList = new LinkedList<InstanceIdSeed>();
        InstanceIdSeed tmp;
        for(int i=0; i<numOfConfigs; i++){
            tmp = getRandomInstance(clusterInstances);
            resultList.add(tmp);
            clusterInstances.remove(tmp);
        }
        return resultList;
    }
    
    protected InstanceIdSeed getRandomInstance(List<InstanceIdSeed> instances){
        if(instances.isEmpty())
            return null;
        LinkedList<InstanceData> datList = new LinkedList<InstanceData>();
        for(InstanceIdSeed inst : instances)
            datList.add(data.get(inst));       
        
        if(!preferHighVarianceInstances){
            int index = rng.nextInt(datList.size());
            return datList.get(index).getInstanceIdSeed();
        }
        
        //randomly select instance; preferring instances with high variance
        double varianceSum = 0;
        for(InstanceData id : datList)
            varianceSum += id.getNormalisedVariance();
        
        //this orders the list in _descending_ order!
        Collections.sort(datList, new InstanceDataComparator());
        
        double randomVal = rng.nextDouble()*varianceSum;
        for(InstanceData id : datList){
            if(randomVal < id.getNormalisedVariance())
                return id.getInstanceIdSeed();
            else
                randomVal -= id.getNormalisedVariance();
        }
        return datList.get(datList.size()-1).getInstanceIdSeed();
    }

    public void addDataForClustering(SolverConfiguration sc) {
        addData(sc);
        newDataAvailable++;
        if(resources.recalculateOnNewData() && recalculationCriterion()){
            log("Recalculating clustering: "+newDataAvailable
                    +" new SolverConfigurations since last clustering was established");
            calculateClustering();            
            newDataAvailable = 0;
            cpuTimeElapsed = getCPUTime();
        }        
        //visualiseClustering();        
    }
    
    /** determines whether or not the criterion for recalculation of the clustering has been met
     * (currently, this can mean that either a sufficient amount of new data is available, or that some new data
     * is available and a sufficient amount of cpu-time has passed.
     * 
     * @return true, if the clustering needs to be recalculated, false otherwise
     */
    private boolean recalculationCriterion(){
        if(useTimeBasedRecalc){      //current cputime - cputime at last recalc
            if(recalculationThreshold < (getCPUTime() - cpuTimeElapsed))
                return true;
        }else{
            if(recalculationThreshold < newDataAvailable)
                return true;
        }
        return false;
    }
    
    public List<InstanceIdSeed> getClusterInstances(int clusterNumber){
        if(clusterNumber<0 || clusterNumber >= clusters.length)
            return null;
        return clusters[clusterNumber].getInstances();
    }
    
    protected final void addData(SolverConfiguration sc){
        List<ExperimentResult> resList = sc.getJobs();
        for(ExperimentResult r : resList){
            InstanceIdSeed inst = 
                    new InstanceIdSeed(r.getInstanceId(), r.getSeed());
            InstanceData id = data.get(inst);
            id.addValue(r);
        }
    }
    
    /**
     * Calculates the costs of two SCs based on the clusters and not on specific instances
     * 
     * @param sc
     * @param competitor
     * @param costFunc
     * @return the costs as double values 
     */
    public Costs costs(SolverConfiguration sc, SolverConfiguration competitor, CostFunction costFunc) {
		List<ExperimentResult> scJobs = new LinkedList<ExperimentResult>();
		List<ExperimentResult> competitorJobs = new LinkedList<ExperimentResult>();
		ArrayList<LinkedList<ExperimentResult>> scClusterJobs = new ArrayList<LinkedList<ExperimentResult>>(clusters.length);
		ArrayList<LinkedList<ExperimentResult>> competitorClusterJobs = new ArrayList<LinkedList<ExperimentResult>>(clusters.length);
		for (int i = 0; i < clusters.length; i++) {
			scClusterJobs.add(new LinkedList<ExperimentResult>());
			competitorClusterJobs.add(new LinkedList<ExperimentResult>());
		}
		for (ExperimentResult res : sc.getFinishedJobs()) {
			InstanceIdSeed tmpInstance = new InstanceIdSeed(res.getInstanceId(), res.getSeed());
			if(!instanceClusterMap.containsKey(tmpInstance)) {
				log(" ERROR: An instance ("+ res.getInstanceId()+";"+ res.getSeed() +") was found with no entry in any cluster!");
			} else {
				int clusterID = instanceClusterMap.get(new InstanceIdSeed(res.getInstanceId(), res.getSeed()));
				scClusterJobs.get(clusterID).add(res);
			}
		}
		for (ExperimentResult res : competitor.getFinishedJobs()) {
			InstanceIdSeed tmpInstance = new InstanceIdSeed(res.getInstanceId(), res.getSeed());
			if(!instanceClusterMap.containsKey(tmpInstance)) {
				log(" ERROR: An instance ("+ res.getInstanceId()+";"+ res.getSeed() +") was found with no entry in any cluster!");
			} else {
				int clusterID = instanceClusterMap.get(new InstanceIdSeed(res.getInstanceId(), res.getSeed()));
				competitorClusterJobs.get(clusterID).add(res);
			}
		}
		for (int i = 0; i < clusters.length; i++) {
			int min = Math.min(scClusterJobs.get(i).size(), competitorClusterJobs.get(i).size());
			for (int j = 0; j < min; j++) {
				scJobs.add(scClusterJobs.get(i).get(j));
				competitorJobs.add(competitorClusterJobs.get(i).get(j));
			}
		}
		double costBest = costFunc.calculateCost(scJobs);
		double costOther = costFunc.calculateCost(competitorJobs);
		return new Costs(costBest, costOther, scJobs.size());
    }
    
    
    public void visualiseClustering(){
        log("New clustering established!");
        log("Algorithm: "+algorithm.getName());
        log("Resources: "+resources.getName());
        log("Number of Instance-Seed pairs: "+data.size());        
        for(int i=0; i<clusters.length; i++){
            List<InstanceIdSeed> iList = clusters[i].getInstances();
            log("Cluster "+i+" ("+iList.size()+" instances)");
        }
    }
    
    protected String formatDouble(double v){
        v = v*100d;
        v = Math.round(v);
        v = v/(100d);
        String s = ""+v;
        while(s.length() < 7)
            s = " "+s;
        return s;
    }
    
    protected List<InstanceIdSeed> getInstances(SolverConfiguration sc){
        LinkedList<InstanceIdSeed> list = new LinkedList<InstanceIdSeed>();
        for(ExperimentResult r : sc.getJobs()){
            list.add(new InstanceIdSeed(r.getInstanceId(), r.getSeed()));
        }
        return list;
    }
    
    protected void log(String message){
        aac.log("Clustering: ("+algorithm.getName()+", "+resources.getName()+")"+message);
    }
    
    protected final double getCPUTime(){
        try{
            return api.getTotalCPUTime(expID);
        }catch(Exception e){
            log("Error: Connection to database failed!");
            e.printStackTrace();
            System.exit(1);
            return 1;
        }
    }
    
    /* returns a mapping of InstanceIdSeed to InstanceData objects, that allows you to obtain information
     * on average cost as well as variance in cost of configurations on this instance-seed-pair
     */
    public Map<InstanceIdSeed, InstanceData> getInstanceDataMap(){
        return data;
    }
    
    private List<SolverConfiguration> initClusteringData() throws Exception {
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
			aac.log("c " + startupSCs.size() + ": "
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
				int scID = api.createSolverConfig(params.getIdExperiment(),
						randomConf, api.getCanonicalName(
								params.getIdExperiment(), randomConf));

				SolverConfiguration randomSC = new SolverConfiguration(scID,
						randomConf, params.getStatistics());
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
			if (sc.getJobCount() < params.getMaxParcoursExpansionFactor()
					* num_instances) {
				expansion = params.getMaxParcoursExpansionFactor()
						* num_instances - sc.getJobCount();
				aac.expandParcoursSC(sc, expansion);
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
				aac.updateJobsStatus(sc);
				if (!(sc.getNotStartedJobs().isEmpty() && sc.getRunningJobs()
						.isEmpty())) {
					finished = false;
				}
				aac.sleep(1000);
			}
		}
		// Set bestSc
		double bestCost = Double.MAX_VALUE;
		for (SolverConfiguration sc : startupSCs) {
			if (sc.getCost() < bestCost) {
				bestCost = sc.getCost();
				bestSC = sc;
			}
		}
		aac.validateIncumbent(bestSC);
		return startupSCs;
	}
    
    /**
     * Retrieves the first incumbent based on the startup sc(s)
     * 
     * @return the best sc from all scs used to initial the clustering
     */
	public SolverConfiguration initBestSC() {
		return bestSC;
	}
}

class InstanceDataComparator implements Comparator<InstanceData>{
    //this comparator is supposed to be used to order elements in _descending_
    //order
    public int compare(InstanceData t, InstanceData t1) {
        if(t.getNormalisedVariance() == t1.getNormalisedVariance())
            return 0;
        if(t.getNormalisedVariance() < t1.getNormalisedVariance())
            return 1;
        else
            return -1;
    }    
}