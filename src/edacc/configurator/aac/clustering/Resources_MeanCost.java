/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edacc.configurator.aac.clustering;

import edacc.api.API;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.Parameters;
import edacc.model.Course;
import edacc.model.InstanceSeed;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.math.stat.descriptive.moment.Variance;

/**
 *
 * @author mugrauer
 */
public class Resources_MeanCost implements ClusteringResources{
    private ClusterHandler handler;
    protected Variance variance;
    private List<InstanceIdSeed> instanceIdSeedList;
    
    public Resources_MeanCost(API api, Parameters params, ClusterHandler handler) throws Exception{
        this.handler = handler;
        Course course = api.getCourse(params.getIdExperiment());
        List<InstanceSeed> tmpList = course.getInstanceSeedList();
        instanceIdSeedList = new LinkedList<InstanceIdSeed>();
        for(InstanceSeed instSeed: tmpList){
            instanceIdSeedList.add(new InstanceIdSeed(instSeed.instance.getId(), instSeed.seed));
        }
    }
    
    public boolean isInitialDataRequired() {
        return true;
    }
    
    public boolean recalculateOnNewData() {
        return true;
    }

    public List<InstanceIdSeed> prepareInstances() {
        return instanceIdSeedList;
    }

    public Cluster[] establishClustering(Cluster[] temporaryClustering) {
        return temporaryClustering;
    }

    public double calculateInstanceDistance(InstanceIdSeed i1, InstanceIdSeed i2) {
        Map<InstanceIdSeed, InstanceData> instanceIdMap = handler.getInstanceDataMap();
        InstanceData dat1 = instanceIdMap.get(i1);
        InstanceData dat2 = instanceIdMap.get(i2);
        double dist = dat1.getAvg() - dat2.getAvg();
        return (dist < 0) ? -dist : dist;
    }

    public double calculateVariance(List<InstanceIdSeed> instances) {
        Map<InstanceIdSeed, InstanceData> instanceIdMap = handler.getInstanceDataMap();
        InstanceData dat;
        double[] values = new double[instances.size()];
        int count = 0;
        for(InstanceIdSeed idSeed : instances){
            dat = instanceIdMap.get(idSeed);
            values[count] = dat.getAvg();
            count++;
        }
        return variance.evaluate(values);
    }

    public String getName() {
        return "MeanCost";
    }
    
}
