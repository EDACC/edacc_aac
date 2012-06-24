package edacc.configurator.aac.clustering;

import edacc.api.API;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.Course;
import edacc.model.ExperimentResult;
import edacc.model.Instance;
import edacc.model.InstanceSeed;
import java.util.*;

/**
 *
 * @author mugrauer
 */
public class CLC_Clustering implements ClusterMethods{
    private Random rng;
    private HashMap<Integer, Instance> instanceIDMap;
    private HashMap<InstanceIdSeed, InstanceData> data;
    
    private HashMap<InstanceIdSeed, Integer> instanceClusterMap;
    private Cluster[] clusters;
    
    public CLC_Clustering(Parameters params, API api, Random rng, List<SolverConfiguration> scs) throws Exception{
        this.rng = rng;
        //initialise instanceID -> instance mapping
        List<Instance> iL = api.getExperimentInstances(params.getIdExperiment());
        instanceIDMap = new HashMap<Integer, Instance>();
        for(Instance i : iL){
            instanceIDMap.put(i.getId(), i);
        }
        
        //initialise data
        data = new HashMap<InstanceIdSeed, InstanceData>();
        Course course = api.getCourse(params.getIdExperiment());
        for(InstanceSeed is : course.getInstanceSeedList()){
            data.put(new InstanceIdSeed(is.instance.getId(), is.seed), 
                        new InstanceData(is.instance.getId(), is.seed));
        }
        for(SolverConfiguration s : scs){
            addData(s);
        }
        
        //initialise clustering
        recalculateClustering();
    }

    public int[] countRunPerCluster(SolverConfiguration sc) {
        int[] numInstances = new int[clusters.length];
        for(ExperimentResult r : sc.getJobs()){
            numInstances[clusterOfInstance(r)]++;
        }
        return numInstances;
    }

    public int clusterOfInstance(ExperimentResult res) {
        InstanceIdSeed inst = 
                new InstanceIdSeed(res.getInstanceId(), res.getSeed());
        return instanceClusterMap.get(inst);
    }

    public InstanceIdSeed getInstanceInCluster(int clusterNumber) {
        List<InstanceIdSeed> instances = clusters[clusterNumber].getInstances();
        int index = rng.nextInt(instances.size());
        return instances.get(index);
    }

    public void addDataForClustering(SolverConfiguration sc) {
        addData(sc);        
        recalculateClustering();
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
        while(clusterList.size()>10/*TODO: Think of a better termination criterion*/){
            Cluster mergeA=null, mergeB=null;
            Double distance=Double.MAX_VALUE, tmpDist;
            boolean block;
            for(Cluster cA : clusterList){
                block = true;
                for(Cluster cB : clusterList){
                    if(block) continue; //we already compared cB to cA, no need to do it again
                    if(cA.equals(cB)){
                        block = false;
                        continue;
                    }                    
                    tmpDist = calculateClusterDistance(cA, cB);
                    if(tmpDist < distance){
                        mergeA = cA;
                        mergeB = cB;
                        distance = tmpDist;
                    }
                }
            }
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
                instanceClusterMap.put(new InstanceIdSeed(i.instanceId, i.seed), c);
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
}