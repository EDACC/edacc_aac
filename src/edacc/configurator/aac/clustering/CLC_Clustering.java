package edacc.configurator.aac.clustering;

import edacc.api.API;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.ExperimentResult;
import java.util.*;

import edacc.util.Pair;

/**
 *
 * @author mugrauer
 */
public class CLC_Clustering extends ClusteringTemplate implements ClusterMethods {
    
    //these values determine the number of clusters that will be generated
    //if useVarianceCriterion is set, clusters will be merged until further mergin would create
    //a cluster with a variance > maximumVariance.
    //Otherwise, clusters will be merged until the number of clusters = staticClusterNumber
    private final double maximumVariance = 0.1;
    private final int staticClusterNumber = 10;
    private boolean useVarianceCriterion = true;
    
    
    public CLC_Clustering(Parameters params, API api, Random rng, List<SolverConfiguration> scs) throws Exception{
        super(params, api, rng, scs);
        
        //initialise clustering
        calculateClustering();
        visualiseClustering();
    }
    
    @Override
    public void addDataForClustering(SolverConfiguration sc) {
        addData(sc);
        long time = System.currentTimeMillis();
        calculateClustering();
        time = System.currentTimeMillis() - time;
        System.out.println("addDataForClustering: Time used to recalculate clustering: "+time+" ms.");
        //visualiseClustering();        
    }
    
    private void calculateClustering(){
        System.out.print("Initialising clusters ... ");
        LinkedList<Pair<Cluster, Integer>> clusterList = new LinkedList<Pair<Cluster,Integer>>();
        Collection<InstanceData> datList = data.values();
        int count =0;
        Cluster c;
        for(InstanceData i: datList){
            c = new Cluster(new InstanceIdSeed(i.getInstanceID(), i.getSeed()));
            clusterList.add(new Pair(c, count));
            count++;
        }
        System.out.print("done!\nInitialising distance matrix ... ");
        //initialise distance matrix (DistanceMatrix class is at the bottom of this file!)
        DistanceMatrix distMat = new DistanceMatrix(clusterList.size());
        Double v1, v2;
        for(Pair<Cluster,Integer> c1 : clusterList){
            for(Pair<Cluster,Integer> c2 : clusterList){
                if(c1.getSecond() >= c2.getSecond())
                    continue;
                distMat.set(c1.getSecond(), c2.getSecond(), 
                            calculateClusterDistance(c1.getFirst(), c2.getFirst()));
            }
        }
        System.out.print("done!\n Refining clustering ... ");
        //calcualte clustering
        while((!useVarianceCriterion && clusterList.size()>staticClusterNumber)
                || clusterList.size()>1){//in the unlikely event, that all instances together do not exceed maxVariance
            Pair<Cluster,Integer> mergeA=null, mergeB=null;
            Double distance = Double.MAX_VALUE, tmpDist;
            for(Pair<Cluster,Integer> cA : clusterList){
                for(Pair<Cluster,Integer> cB : clusterList){
                    if(cA.getSecond() >= cB.getSecond())
                        continue;
                    
                    tmpDist = distMat.get(cA.getSecond(), cB.getSecond());
                    if(tmpDist<distance){
                        mergeA = cA;
                        mergeB = cB;
                        distance = tmpDist;
                    }
                }
            }
            if(useVarianceCriterion && !checkVarianceCriterion(mergeA.getFirst(), mergeB.getFirst()))
                break;
            clusterList.remove(mergeB);
            mergeA.getFirst().mergeClusters(mergeB.getFirst());
            //update distance matrix:
            for(Pair<Cluster,Integer> cl: clusterList){
                if(cl.getSecond()==mergeA.getSecond())
                    continue;
                distMat.set(mergeA.getSecond(), cl.getSecond(), 
                            calculateClusterDistance(mergeA.getFirst(), cl.getFirst()));
            }
        }
        System.out.print("done!\nEstablishing clustering ... ");
        clusters = new Cluster[clusterList.size()];
        instanceClusterMap = new HashMap<InstanceIdSeed, Integer>();
        int clusterPos = 0;
        for(Pair<Cluster,Integer> cl : clusterList){
            clusters[clusterPos] = cl.getFirst();            
            for(InstanceIdSeed i : clusters[clusterPos].getInstances())
                instanceClusterMap.put(i, clusterPos);
            
            clusterPos++;
        }
        System.out.println("done!");
    }
    /*
    private void recalculateClustering(){
        long timeMerge=0, timeD=0;
        LinkedList<Long> clMerges = new LinkedList<Long>(), mergeD = new LinkedList<Long>();
        
        LinkedList<Cluster> clusterList = new LinkedList<Cluster>();
        Collection<InstanceData> datList = data.values();
        for(InstanceData i : datList){
            clusterList.add(new Cluster(new InstanceIdSeed(i.getInstanceID(), i.getSeed())));
        }
        
        while((!useVarianceCriterion && clusterList.size()>staticClusterNumber) 
                || clusterList.size()>1){//in the unlikely event, that all instances together do not exceed maxVariance
            timeMerge = System.currentTimeMillis();
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
            timeD = System.currentTimeMillis();
            mergeA.mergeClusters(mergeB);
            mergeD.add(System.currentTimeMillis()-timeD);
            clusterList.add(mergeA);
            clMerges.add(System.currentTimeMillis()-timeMerge);
        }
        clusters = new Cluster[clusterList.size()];
        clusters = clusterList.toArray(clusters);
        instanceClusterMap = new HashMap<InstanceIdSeed, Integer>();
        for(int c=0; c<clusters.length; c++){
            for(InstanceIdSeed i : clusters[c].getInstances()){
                instanceClusterMap.put(i, c);
            }
        }
        double tM = 0, tD = 0;
        for(Long i: mergeD)
            tD += i;
        tD = tD / ((double)mergeD.size());
        for(Long i: clMerges)
            tM += i;
        tM = tM / clMerges.size();
        System.out.println("Number of Calculations: "+clMerges.size()+", avg time: "+tM+"ms.");
        System.out.println("Number of Merges: "+mergeD.size()+", avg time: "+tD+"ms.");
    }*/
    
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
    
    @Override
    public final void visualiseClustering(){
        System.out.println("---------------CLUSTERING-------------------");
        System.out.println("Number of Instance-Seed pairs: "+data.size());
        for(int i=0; i<clusters.length; i++){
            List<InstanceIdSeed> iList = clusters[i].getInstances();
            System.out.println("Cluster "+i+" ("+iList.size()+" instances, variance: "+calculateVariance(iList)+"):");            
            /*
            for(InstanceIdSeed inst : iList){
                System.out.println("   "+formatDouble(data.get(inst).getAvg())+" ID: "+inst.instanceId+" Seed: "+inst.seed);
            }*/
        }        
        System.out.println("---------------CLUSTERING-------------------");
    }
}


class DistanceMatrix{
    private double[][] distMat;
    
    public DistanceMatrix(int size){
        distMat = new double[size][size];
        for(int i=0; i<size; i++)
            for(int j=0; j<size; j++)
                distMat[i][j] = Double.MAX_VALUE;
    }
    
    public void set(int i, int j, double value){
        if(i==j)
            return;
        distMat[i][j] = value;
        distMat[j][i] = value;        
    }
    
    public double get(int i, int j){
        return distMat[i][j];
    }
}