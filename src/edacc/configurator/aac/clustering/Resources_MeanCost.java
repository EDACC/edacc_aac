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
public class Resources_MeanCost extends ClusteringResources{
    private ClusterHandler handler;
    protected Variance variance;
    private List<InstanceIdSeed> instanceIdSeedList;
    
    public Resources_MeanCost(API api, Parameters params, ClusterHandler handler) throws Exception{
        this.handler = handler;
        this.variance = new Variance();
        Course course = api.getCourse(params.getIdExperiment());
        List<InstanceSeed> tmpList = course.getInstanceSeedList();
        instanceIdSeedList = new LinkedList<InstanceIdSeed>();
        for(InstanceSeed instSeed: tmpList){
            instanceIdSeedList.add(new InstanceIdSeed(instSeed.instance.getId(), instSeed.seed));
        }
    }
    
    @Override
    public boolean recalculateOnNewData() {
        return true;
    }
    
    @Override
    public List<InstanceIdSeed> prepareInstances() {
        return instanceIdSeedList;
    }
    
    @Override
    public Cluster[] establishClustering(Cluster[] temporaryClustering) {
        return temporaryClustering;
    }

    @Override
    public double calculateInstanceDistance(InstanceIdSeed i1, InstanceIdSeed i2) {
        Map<InstanceIdSeed, InstanceData> instanceIdMap = handler.getInstanceDataMap();
        InstanceData dat1 = instanceIdMap.get(i1);
        InstanceData dat2 = instanceIdMap.get(i2);
        double dist = dat1.getAvg() - dat2.getAvg();
        return (dist < 0) ? -dist : dist;
    }

    @Override
    public double calculateVariance(List<InstanceIdSeed> instances) {
        Map<InstanceIdSeed, InstanceData> instanceIdMap = handler.getInstanceDataMap();
        if(instanceIdMap == null)
            System.out.println("ERROR: instanceIdMap = null");
        InstanceData dat;
        double[] values = new double[instances.size()];
        int count = 0;
        for(InstanceIdSeed idSeed : instances){
            if(idSeed == null)
                System.out.println("ERROR: idSeed = null");
            dat = instanceIdMap.get(idSeed);
            if(dat==null)
                System.out.println("Error: dat = null");
            values[count] = dat.getAvg();
            count++;
        }
        return variance.evaluate(values);
    }
    
    @Override
    public String getName() {
        return "Resources_MeanCost";
    }
    
}
