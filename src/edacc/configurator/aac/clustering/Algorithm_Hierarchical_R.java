/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edacc.configurator.aac.clustering;

import edacc.configurator.aac.AAC;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.util.RInterface;
import edacc.configurator.math.ClusterSilhouette;
import java.util.List;
import org.rosuda.JRI.Rengine;


/**
 *
 * @author mugrauer
 */
public class Algorithm_Hierarchical_R implements ClusteringAlgorithm{
    private AAC aac;
    private ClusterHandler handler;
    private ClusteringResources resources;
    private Rengine rengine;
    
    public Algorithm_Hierarchical_R(AAC aac, ClusteringResources resources, ClusterHandler handler){
        this.aac = aac;
        this.handler = handler;
        this.resources = resources;
        this.rengine = RInterface.getRengine();
    }

    public Cluster[] calculateClustering(List<InstanceIdSeed> instances) {        
        try{//get data
            RefinedData refinedData = resources.getRefinedData();
            double[][] data = refinedData.getData();
            //caculate clustering
            ClusterSilhouette cs = new ClusterSilhouette(rengine, data.length, data[0].length, data);
            int clusterNumber = cs.findNumberOfClusters((int)Math.sqrt(data.length));
            int[] clusterEntries = cs.clusterData(clusterNumber);
            //map instances to clusters
            Cluster[] clusters = new Cluster[clusterNumber];
            int clusterIndex;
            for(int i=0; i<clusterEntries.length; i++){
                InstanceIdSeed idSeed = refinedData.getInstanceForIndex(i);
                clusterIndex = clusterEntries[i];
                if(clusters[clusterIndex]==null)
                    clusters[clusterIndex] = new Cluster(idSeed);
                else                    
                    clusters[clusterIndex].addInstance(idSeed);
            }
            return clusters;
        }catch(Exception e){
            handler.log("ERROR: could not calculate clustering:");
            e.printStackTrace();
            System.exit(1);
        }
        return null; //unreachable code
    }

    public String getName() {
        return "Algorithm_Hierarchical_R";
    }
    
}
