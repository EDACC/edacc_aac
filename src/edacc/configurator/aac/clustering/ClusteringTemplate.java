package edacc.configurator.aac.clustering;

import edacc.api.API;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.Course;
import edacc.model.ExperimentResult;
import edacc.model.InstanceSeed;
import java.util.*;



/**
 *
 * @author mugrauer
 */
public abstract class ClusteringTemplate implements ClusterMethods{
    protected Random rng;
    protected HashMap<InstanceIdSeed, InstanceData> data;    
    protected HashMap<InstanceIdSeed, Integer> instanceClusterMap;
    protected Cluster[] clusters;   
    
    
    //when a random instance is to be selected from a cluster, should
    //instances with high variance of cost have a higher chance of being picked?
    //(higher variance in cost means the instance might be more useful in distinguishing
    // between configs with good or poor perfomance)
    protected boolean preferHighVarianceInstances = false;
    
    public ClusteringTemplate(Parameters params, API api, Random rng, List<SolverConfiguration> scs) throws Exception{
        this.rng = rng;
        
        //initialise data
        data = new HashMap<InstanceIdSeed, InstanceData>();
        Course course = api.getCourse(params.getIdExperiment());       
        for(InstanceSeed is : course.getInstanceSeedList()){
            data.put(new InstanceIdSeed(is.instance.getId(), is.seed), 
                        new InstanceData(is.instance.getId(), is.seed, params.getStatistics().getCostFunction()));
        }
        for(SolverConfiguration s : scs){
            addData(s);
        }        
    }

    public int[] countRunPerCluster(SolverConfiguration sc) {
        int[] numInstances = new int[clusters.length];
        for(int i=0; i<numInstances.length; i++)
            numInstances[i] = 0;
        for(ExperimentResult r : sc.getJobs()){
            numInstances[clusterOfInstance(r)]++;
        }
        return numInstances;
    }

    public int clusterOfInstance(ExperimentResult res) {
        if(res == null)
            return -1;
        InstanceIdSeed inst = new InstanceIdSeed(res.getInstanceId(), res.getSeed());
        return instanceClusterMap.get(inst);
    }

    public InstanceIdSeed getInstanceInCluster(int clusterNumber) {
        return getRandomInstance(clusters[clusterNumber].getInstances());
    }
    public InstanceIdSeed getInstanceInCluster(int clusterNr, SolverConfiguration solverConfig){
        List<InstanceIdSeed> clusterInstances = clusters[clusterNr].getInstances();
        List<ExperimentResult> resList = solverConfig.getJobs();
        LinkedList<InstanceIdSeed> tmpList = new LinkedList<InstanceIdSeed>();
        for(ExperimentResult res : resList){
            InstanceIdSeed ids = new InstanceIdSeed(res.getInstanceId(), res.getSeed());
            tmpList.add(ids);
            if(!data.containsKey(ids))
                System.out.println("ERROR: Inconsistent InstanceIdSeed Pairs!");
        }
        clusterInstances.removeAll(tmpList);//clusterInstances is a copy of the cluster's list -> no side effects
        InstanceIdSeed newInstance = getRandomInstance(clusterInstances);
        if(tmpList.contains(newInstance))
            System.out.println("ERROR: getInstanceInCluster returns duplicate Instance!!");
        if(resList.size() >= data.size() && newInstance != null)
            System.out.println("ERROR: getInstanceInCluster returns non-null result for finished config!");
        return newInstance;
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