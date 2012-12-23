/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edacc.configurator.aac.clustering;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.Parameters;
import edacc.model.Course;
import edacc.model.Instance;
import edacc.model.InstanceHasProperty;
import edacc.model.InstanceSeed;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import org.apache.commons.math.stat.descriptive.moment.Variance;

/**
 *
 * @author mugrauer
 */
public class Resources_Properties extends ClusteringResources{
    private static String   featureCache = "/home/share/features/featureCache",
                            features = "/home/share/features/features";
    private static boolean readPropertiesFromFolder = true;
    
    private File featureCacheFolder, featureFolder;
    
    private double[][] data;
    private List<InstanceIdSeed> instanceSeedList;
    private HashMap<Integer, Integer> idToIndex;
    private Variance variance;
    
    public Resources_Properties(API api, Parameters params, ClusterHandler handler) throws Exception{
        featureCacheFolder = new File(featureCache);
        featureFolder = new File(features);
        variance = new Variance();
        idToIndex = new HashMap<Integer,Integer>();      
        
        //prepare list of Instances+Seeds
        Course course = api.getCourse(params.getIdExperiment());
        List<InstanceSeed> tmpList = course.getInstanceSeedList();
        instanceSeedList = new LinkedList<InstanceIdSeed>();
        for(InstanceSeed instSeed: tmpList){
            instanceSeedList.add(new InstanceIdSeed(instSeed.instance.getId(), instSeed.seed));
        }
        
        //Prepare property data for clustering
        List<Instance> instanceList = api.getExperimentInstances(params.getIdExperiment());
        data = new double[instanceList.size()][];
        int highestPropertyIndex = 0;
        int count = 0;
        for(Instance inst : instanceList){
            float[] props = getProperties(inst);
            data[count] = new double[props.length];
            for(int i=0; i<props.length; i++)
                data[count][i] = props[i];
            idToIndex.put(inst.getId(), count);
            if(props.length > highestPropertyIndex)
                highestPropertyIndex = props.length;
            count++;
        }
        
        //find irrelevant properties, scale values of relevant ones to [-1,1]
        boolean[] ignoreProperty = new boolean[highestPropertyIndex];
        int numOfIgnoredProperties=0;
        for(int propertyIndex=0; propertyIndex<highestPropertyIndex; propertyIndex++){
            double maxValue = Double.MAX_VALUE;
            double minValue = Double.MIN_VALUE;
            for(int instanceIndex=0; instanceIndex<data.length; instanceIndex++){
                if(data[instanceIndex].length <= propertyIndex){
                    ignoreProperty[propertyIndex] = true;
                    numOfIgnoredProperties++;
                    continue;
                }
                maxValue = Math.max(maxValue, data[instanceIndex][propertyIndex]);
                minValue = Math.min(minValue, data[instanceIndex][propertyIndex]);
            }
            if(minValue == maxValue){
                ignoreProperty[propertyIndex] = true;
                numOfIgnoredProperties++;
                continue;
            }
            //scale properties:
            double val;
            for(int instanceIndex=0; instanceIndex<data.length; instanceIndex++){
                val = data[instanceIndex][propertyIndex];
                val = val - minValue;
                val = val/(maxValue-minValue);
                val = val*2d - 1d; 
                data[instanceIndex][propertyIndex] = val;
            }
        }
        //remove irrelevant properties:
        double[][] relevantData = new double[data.length][highestPropertyIndex-numOfIgnoredProperties];
        int relevantIndex = 0;
        for(int propertyIndex=0; propertyIndex<highestPropertyIndex; propertyIndex++){
            if(ignoreProperty[propertyIndex]){
                continue;
            }
            for(int instanceIndex=0; instanceIndex<data.length; instanceIndex++){
                relevantData[instanceIndex][relevantIndex]
                                = data[instanceIndex][propertyIndex];
            }
            relevantIndex++;
        }
        data = relevantData;
    }

    @Override
    public boolean recalculateOnNewData() {
        return false;
    }

    @Override
    public List<InstanceIdSeed> prepareInstances() {
        Collection<Integer> instanceIDs = idToIndex.keySet();
        List<InstanceIdSeed> instanceIdSeedList = new LinkedList<InstanceIdSeed>();
        for(Integer id : instanceIDs){
            instanceIdSeedList.add(new InstanceIdSeed(id, 0));
        }
        return instanceIdSeedList;
    }

    @Override
    public Cluster[] establishClustering(Cluster[] temporaryClustering) {
        for(Integer id : idToIndex.keySet()){
            InstanceIdSeed dummy = new InstanceIdSeed(id, 0);
            for(int i=0; i<temporaryClustering.length; i++){
                if(temporaryClustering[i].contains(dummy)){
                    for(InstanceIdSeed idSeed : instanceSeedList){
                        if(idSeed.instanceId == id)
                            temporaryClustering[i].addInstance(new InstanceIdSeed(idSeed.instanceId, idSeed.seed));
                    }
                    temporaryClustering[i].removeInstance(id, 0); //remove dummy
                    break;
                }
            }
        }
        return temporaryClustering;
    }

    @Override
    public double calculateInstanceDistance(InstanceIdSeed i1, InstanceIdSeed i2) {
        int index1 = idToIndex.get(i1.instanceId);
        int index2 = idToIndex.get(i2.instanceId);
        double dist = 0, tmp;
        for(int i=0; i<data[0].length; i++){
             tmp = data[index1][i]-data[index2][i];
             tmp = tmp * tmp;
             dist += tmp;
        }
        dist = Math.sqrt(dist);
        return dist;
    }

    @Override
    public double calculateVariance(List<InstanceIdSeed> instances) {
        double[] characteristicValues = new double[instances.size()];
        int count = 0;
        for(InstanceIdSeed idSeed : instances){
            characteristicValues[count] = calculateCharacteristicValue(idSeed.instanceId);
            count++;
        }
        return variance.evaluate(characteristicValues);
    }

    @Override
    public String getName() {
        return "Resources_Properties";
    }
    
    private double calculateCharacteristicValue(int instanceID){
        int index = idToIndex.get(instanceID);
        double val = 0, tmp;
        for(int i=0; i<data[index].length; i++){
            tmp = data[index][i];
            tmp = tmp*tmp;
            val += tmp;
        }
        return Math.sqrt(val);
    }
    
    private float[] getProperties(Instance instance) throws Exception{
        if(readPropertiesFromFolder){
            return AAC.calculateFeatures(instance.getId(), featureFolder, featureCacheFolder);
        }
        else{
            HashMap<Integer, InstanceHasProperty> propMap = instance.getPropertyValues();
            int highestProperty = -1;
            for(Integer i : propMap.keySet())
                if(i > highestProperty)
                    highestProperty = i;
            float[] res = new float[highestProperty+1];
            for(Entry<Integer,InstanceHasProperty> e : propMap.entrySet()){
                String s = e.getValue().getValue();
                Float f = (s==null) ? 0f : Float.parseFloat(s);                
                res[e.getKey()] = f;
            }
            return res;
        }
    }

    @Override
    public RefinedData getRefinedData() {
        double[][] dataCopy = new double[data.length][data[0].length];
        for(int i=0; i<data.length; i++)
            for(int j=0; j<data[0].length; j++)
                dataCopy[i][j] = data[i][j];
        HashMap<Integer, InstanceIdSeed> indexToInstance = new HashMap<Integer,InstanceIdSeed>();
        for(Entry<Integer,Integer> entry: idToIndex.entrySet()){
            indexToInstance.put(entry.getValue(), new InstanceIdSeed(entry.getKey(), 0));
        }
        return new RefinedData(dataCopy, indexToInstance);
    }
}
