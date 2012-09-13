/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edacc.configurator.aac.clustering;

import edacc.api.API;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.Parameters;
import edacc.model.Course;
import edacc.model.Instance;
import edacc.model.InstanceHasProperty;
import edacc.model.InstanceSeed;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.math.stat.descriptive.moment.Variance;

/**
 *
 * @author mugrauer
 */
public class Resources_Properties extends ClusteringResources{
    protected HashMap<Integer, Instance> instanceIdMap;
    protected List<InstanceIdSeed> instanceSeedList;
    protected Variance variance;
    
    public Resources_Properties(API api, Parameters params, ClusterHandler handler) throws Exception{
        variance = new Variance();
        List<Instance> instanceList = api.getExperimentInstances(params.getIdExperiment());
        instanceIdMap = new HashMap<Integer, Instance>();
        for(Instance i : instanceList){
            instanceIdMap.put(i.getId(), i);
        }
        Course course = api.getCourse(params.getIdExperiment());
        List<InstanceSeed> tmpList = course.getInstanceSeedList();
        instanceSeedList = new LinkedList<InstanceIdSeed>();
        for(InstanceSeed instSeed: tmpList){
            instanceSeedList.add(new InstanceIdSeed(instSeed.instance.getId(), instSeed.seed));
        }
    }
    
    
    @Override
    public boolean recalculateOnNewData() {
        return false;
    }
    
    @Override
    public List<InstanceIdSeed> prepareInstances() {
        Collection<Instance> instanceList = instanceIdMap.values();
        List<InstanceIdSeed> instanceIdSeedList = new LinkedList<InstanceIdSeed>();
        for(Instance instance : instanceList){
            instanceIdSeedList.add(new InstanceIdSeed(instance.getId(), 0));
        }
        return instanceIdSeedList;
    }
    
    @Override
    public Cluster[] establishClustering(Cluster[] temporaryClustering) {
        for(Instance inst : instanceIdMap.values()){
            InstanceIdSeed dummy = new InstanceIdSeed(inst.getId(), 0);
            for(int i=0; i<temporaryClustering.length; i++){
                if(temporaryClustering[i].contains(dummy)){
                    for(InstanceIdSeed idSeed : instanceSeedList){
                        if(idSeed.instanceId == inst.getId())
                            temporaryClustering[i].addInstance(new InstanceIdSeed(idSeed.instanceId, idSeed.seed));
                    }
                    temporaryClustering[i].removeInstance(inst.getId(), 0); //remove dummy
                    break;
                }
            }
        }
        return temporaryClustering;
    }

    @Override
    public double calculateInstanceDistance(InstanceIdSeed idSeed1, InstanceIdSeed idSeed2){
        Instance i1 = instanceIdMap.get(idSeed1.instanceId);
        Instance i2 = instanceIdMap.get(idSeed2.instanceId);
        HashMap<Integer, InstanceHasProperty> m1 = i1.getPropertyValues();
        HashMap<Integer, InstanceHasProperty> m2 = i2.getPropertyValues();
        //only properties 80-133 are properly set up
        double dist = 0, tmp, val1, val2;
        for(int i=80; i<134; i++){
             val1 = Double.parseDouble(m1.get(i).getValue());
             val2 = Double.parseDouble(m2.get(i).getValue());             
             tmp = val1-val2;//TODO: rework to avoid rounding errors
             tmp = tmp * tmp;
             dist += tmp;
        }
        dist = Math.sqrt(dist);
        return dist;
    }

    @Override
    public double calculateVariance(List<InstanceIdSeed> instances) {        
        double[] characteristicValues = new double[instances.size()];
        Instance instance;
        double val;
        int count = 0;
        for(InstanceIdSeed idSeed : instances){
            instance = instanceIdMap.get(idSeed.instanceId);
            val = calculateCharacteristicValue(instance);
            characteristicValues[count] = val;
            count++;
        }
        return variance.evaluate(characteristicValues);
    }
    
    protected double calculateCharacteristicValue(Instance inst){
        HashMap<Integer, InstanceHasProperty> m = inst.getPropertyValues();
        double val = 0, tmp;
        for(int i=80; i<134; i++){
            tmp = Double.parseDouble(m.get(i).getValue());
            tmp = tmp*tmp;
            val += tmp;
        }
        return Math.sqrt(val);
    }

    @Override
    public String getName() {
        return "Resources_Properties";
    }   
}
