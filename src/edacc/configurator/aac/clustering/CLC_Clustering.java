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
public class CLC_Clustering implements ClusterMethods{
    private Random rng;
    //private HashMap<Integer, Instance> instanceIDMap;
    private HashMap<InstanceIdSeed, InstanceData> data;
    
    private HashMap<InstanceIdSeed, Integer> instanceClusterMap;
    private Cluster[] clusters;
    
    
    //these values determine the number of clusters that will be generated
    //if useVarianceCriterion is set, clusters will be merged until further mergin would create
    //a cluster with a variance > maximumVariance.
    //Otherwise, clusters will be merged until the number of clusters = staticClusterNumber
    private final double maximumVariance = 0.1;
    private final int staticClusterNumber = 10;
    private boolean useVarianceCriterion = true;
    
    //when a random instance is to be selected from a cluster, should
    //instances with high variance have a higher chance of being picked,
    //as they are more useful in distinguishing between configurations?
    private boolean preferHighVarianceInstances = false;
    
    public CLC_Clustering(Parameters params, API api, Random rng, List<SolverConfiguration> scs) throws Exception{
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
        //initialise clustering
        recalculateClustering();
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
            tmpList.add(new InstanceIdSeed(res.getInstanceId(), res.getSeed()));
        }
        clusterInstances.removeAll(tmpList);//clusterInstances is a copy of the cluster's list -> no side effects
        return getRandomInstance(clusterInstances);
    }
    
    private InstanceIdSeed getRandomInstance(List<InstanceIdSeed> instances){
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
        long time = System.currentTimeMillis();
        recalculateClustering();
        time = System.currentTimeMillis() - time;
        System.out.println("addDataForClustering: Time used to recalculate clustering: "+time+" ms.");
        //visualiseClustering();
    }
    
    public List<InstanceIdSeed> getClusterInstances(int clusterNumber){
        if(clusterNumber<0 || clusterNumber >= clusters.length)
            return null;
        return clusters[clusterNumber].getInstances();
    }
    
    private void addData(SolverConfiguration sc){
        List<ExperimentResult> resList = sc.getJobs();
        for(ExperimentResult r : resList){
            InstanceIdSeed inst = 
                    new InstanceIdSeed(r.getInstanceId(), r.getSeed());
            InstanceData id = data.get(inst);
            id.addValue(r);
        }
    }
    
    private void recalculateClustering(){
        LinkedList<Cluster> clusterList = new LinkedList<Cluster>();
        Collection<InstanceData> datList = data.values();
        for(InstanceData i : datList){
            clusterList.add(new Cluster(new InstanceIdSeed(i.getInstanceID(), i.getSeed())));
        }
        while((!useVarianceCriterion && clusterList.size()>staticClusterNumber) 
                || clusterList.size()>1){//in the unlikely event, that all instances together do not exceed maxVariance
            Cluster mergeA=null, mergeB=null;
            Double distance=Double.MAX_VALUE, tmpDist;
            boolean block;
            for(Cluster cA : clusterList){
                block = true;
                for(Cluster cB : clusterList){
                    if(cA.equals(cB)){
                        block = false;
                        continue;
                    }    
                    if(block) continue; //we already compared cB to cA, no need to do it again
                                    
                    tmpDist = calculateClusterDistance(cA, cB);
                    if(tmpDist < distance){
                        mergeA = cA;
                        mergeB = cB;
                        distance = tmpDist;
                    }
                }
            }
            if(useVarianceCriterion && !checkVarianceCriterion(mergeA, mergeB))
                break;
            clusterList.remove(mergeA);
            clusterList.remove(mergeB);
            mergeA.mergeClusters(mergeB);
            clusterList.add(mergeA);
        }
        clusters = new Cluster[clusterList.size()];
        clusters = clusterList.toArray(clusters);
        instanceClusterMap = new HashMap<InstanceIdSeed, Integer>();
        for(int c=0; c<clusters.length; c++){
            for(InstanceIdSeed i : clusters[c].getInstances()){
                instanceClusterMap.put(i, c);
            }
        }
    }
    
    private Double calculateClusterDistance(Cluster cA, Cluster cB) {
        Double distance, tmpDist;
        distance = 0d;
        InstanceData iA, iB;
        for (InstanceIdSeed a : cA.getInstances()) {
            iA = data.get(a);
            for (InstanceIdSeed b : cB.getInstances()) {
                iB = data.get(b);
                tmpDist = iA.getAvg() - iB.getAvg();
                tmpDist = (tmpDist>0) ? tmpDist : -tmpDist;
                distance = (tmpDist>distance) ? tmpDist : distance;
            }
        }
        return distance;
    }
    
    private boolean checkVarianceCriterion(Cluster a, Cluster b){
        LinkedList<InstanceIdSeed> mergeList = new LinkedList<InstanceIdSeed>();
        mergeList.addAll(a.getInstances());
        mergeList.addAll(b.getInstances());
        return calculateVariance(mergeList) < maximumVariance;        
    }
    
    private Double calculateVariance(List<InstanceIdSeed> instances){        
        double var = 0d, clusterAvg=0d, tmp;
        if(instances.isEmpty()) 
            return 0d;
        LinkedList<InstanceData> datList = new LinkedList<InstanceData>();
        for(InstanceIdSeed instance : instances){
            InstanceData iDat = data.get(instance);
            datList.add(iDat);
            clusterAvg += iDat.getAvg();
        }
        clusterAvg = clusterAvg/((double)datList.size());
        
        for(InstanceData iDat : datList){
            tmp = iDat.getAvg() - clusterAvg;
            var += tmp*tmp;
        }
        var = var/((double)datList.size());        
        return var;
    }
    
    public void visualiseClustering(){
        System.out.println("---------------CLUSTERING-------------------");
        for(int i=0; i<clusters.length; i++){
            List<InstanceIdSeed> iList = clusters[i].getInstances();
            System.out.println("Cluster "+i+" ("+iList.size()+" instances, variance: "+calculateVariance(iList)+"):");            
            for(InstanceIdSeed inst : iList){
                System.out.println("   "+formatDouble(data.get(inst).getAvg())+" ID: "+inst.instanceId+" Seed: "+inst.seed);
            }
        }        
        System.out.println("---------------CLUSTERING-------------------");
    }
    
    public String formatDouble(double v){
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