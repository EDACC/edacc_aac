package edacc.configurator.aac.clustering;

import edacc.api.API;
import edacc.api.costfunctions.CostFunction;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.Course;
import edacc.model.ExperimentResult;
import edacc.model.InstanceSeed;
import java.util.*;

/**
 * @author mugrauer, schulte
 */
public abstract class ClusteringTemplate implements ClusterMethods{
    protected Random rng;
    protected AAC aac;
    protected API api;
    protected int expID;
    protected HashMap<InstanceIdSeed, InstanceData> data;    
    protected HashMap<InstanceIdSeed, Integer> instanceClusterMap;
    protected Cluster[] clusters;
    
    
    //when a random instance is to be selected from a cluster, should
    //instances with high variance of cost have a higher chance of being picked?
    //(higher variance in cost means the instance might be more useful in distinguishing
    // between configs with good or poor perfomance)
    protected boolean preferHighVarianceInstances = true;
    
    public ClusteringTemplate(AAC aac, Parameters params, API api, Random rng, List<SolverConfiguration> scs) throws Exception{
        this.rng = rng;
        this.aac = aac;
        this.api = api;
        this.expID = params.getIdExperiment();
        //initialise data
        data = new HashMap<InstanceIdSeed, InstanceData>();
        Course course = api.getCourse(params.getIdExperiment());       
        for(InstanceSeed is : course.getInstanceSeedList()){
            data.put(new InstanceIdSeed(is.instance.getId(), is.seed), 
                        new InstanceData(is.instance.getId(), is.seed, params.getStatistics().getCostFunction()));
        }
        if(scs!=null){
            for(SolverConfiguration s : scs){
                addData(s);
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
    
    /*
    public void debugAnalyseDifferences(SolverConfiguration incumbent, SolverConfiguration challenger){
        if(incumbent.getJobCount() != challenger.getJobCount()){
            System.out.println("DEBUG: Job Counts do not match! Incumbent "+incumbent.getJobCount()+", Challenger: "+challenger.getJobCount());            
        }
        int[] iRuns = countRunPerCluster(incumbent);
        int[] cRuns = countRunPerCluster(challenger);
        int iSum =0, cSum=0;
        for(int i=0; i<clusters.length; i++){
            iSum += iRuns[i];
            cSum += cRuns[i];
            System.out.println("DEBUG: Cluster "+i+" ("+clusters[i].size()+" instances): Incumbent: "+iRuns[i]+", Challenger: "+cRuns[i]);
            
        }
        System.out.println("DEBUG: Incumbent has "+incumbent.getJobCount()+" runs, "+iSum+" are in recognised clusters");
        System.out.println("DEBUG: Challenger has "+challenger.getJobCount()+" runs, "+cSum+" are in recognised clusters");
        LinkedList<InstanceIdSeed> iList = new LinkedList<InstanceIdSeed>(), cList = new LinkedList<InstanceIdSeed>();
        InstanceIdSeed tmp;
        for(ExperimentResult r : incumbent.getFinishedJobs()){
            tmp = new InstanceIdSeed(r.getInstanceId(), r.getSeed());
            if(!iList.contains(tmp))
                iList.add(tmp);
        }
        for(ExperimentResult r : challenger.getFinishedJobs()){
            tmp = new InstanceIdSeed(r.getInstanceId(), r.getSeed());
            if(!cList.contains(tmp))
                cList.add(tmp);
        }
        System.out.println("DEBUG: Incumbent has "+incumbent.getJobCount()+" jobs, "
                +incumbent.getFinishedJobs().size()+" of them finished. "+iList.size()+" of them are unique");
        System.out.println("DEBUG: Challenger has "+challenger.getJobCount()+" jobs, "
                +challenger.getFinishedJobs().size()+" of them finished. "+cList.size()+" of them are unique");
        System.exit(1);
    }*/
    
    public int[] countRunPerCluster(SolverConfiguration sc){
        int[] numRuns = new int[clusters.length];
        for(int i=0; i<clusters.length; i++)
            numRuns[i] = clusters[i].countRuns(sc);
        return numRuns;
    }
    /*
    public int[] countRunPerCluster(SolverConfiguration sc) {
        int[] numInstances = new int[clusters.length];
        for(int i=0; i<numInstances.length; i++)
            numInstances[i] = 0;
        for(ExperimentResult r : sc.getJobs()){
            numInstances[clusterOfInstance(r)]++;
        }
        return numInstances;
    }*/

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
        Collections.sort(datList, new InstanceComparator());
        
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
     * @return the costs as float values 
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
		float costBest = costFunc.calculateCost(scJobs);
		float costOther = costFunc.calculateCost(competitorJobs);
		return new Costs(costBest, costOther, scJobs.size());
    }
    
    
    public void visualiseClustering(){
        System.out.println("---------------CLUSTERING-------------------");
        System.out.println("Number of Instance-Seed pairs: "+data.size());
        for(int i=0; i<clusters.length; i++){
            List<InstanceIdSeed> iList = clusters[i].getInstances();
            System.out.println("Cluster "+i+" ("+iList.size()+" instances)");
        }        
        System.out.println("---------------CLUSTERING-------------------");
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
        aac.log("Clustering: "+message);
    }
    
    protected double getCPUTime(){
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
}
class InstanceComparator implements Comparator<InstanceData>{
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