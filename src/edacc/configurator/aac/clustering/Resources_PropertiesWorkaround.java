/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edacc.configurator.aac.clustering;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.Parameters;
import edacc.model.Instance;
import java.io.File;

/** This class represents the Resources_Properties class, but implements a workaround for when the
 *  InstanceProperties are not pre-calculated and stored in the DB, but need to be calculated at runtime
 *
 * @author mugrauer
 */
public class Resources_PropertiesWorkaround extends Resources_Properties{
    private static String   featureCache = "/home/share/features/featureCache",
                            features = "/home/share/features/features";
    private File featureCacheFolder, featureFolder;
    
    public Resources_PropertiesWorkaround(API api, Parameters params, ClusterHandler handler) throws Exception{
        super(api,params,handler);
        featureCacheFolder = new File(featureCache);
        featureFolder = new File(features);
    }
    
    @Override
    public double calculateInstanceDistance(InstanceIdSeed idSeed1, InstanceIdSeed idSeed2){
        Instance i1 = instanceIdMap.get(idSeed1.instanceId);
        Instance i2 = instanceIdMap.get(idSeed2.instanceId);
        float[] properties1 = getProperties(i1.getId());
        float[] properties2 = getProperties(i2.getId());
        //only properties 80-133 are properly set up
        double dist = 0, tmp, val1, val2;
        for(int i=80; i<134; i++){
             val1 = properties1[i];
             val2 = properties2[i];     
             tmp = val1-val2;
             tmp = tmp * tmp;
             dist += tmp;
        }
        dist = Math.sqrt(dist);
        return dist;
    }
        
    @Override    
    protected double calculateCharacteristicValue(Instance inst){
        float[] properties = getProperties(inst.getId());
        double val = 0, tmp;
        for(int i=80; i<134; i++){
            tmp = properties[i];
            tmp = tmp*tmp;
            val += tmp;
        }
        return Math.sqrt(val);
    }   
    
    private float[] getProperties(int instanceID){
        try{
            float[] res = AAC.calculateFeatures(instanceID, featureFolder, featureCacheFolder);
            return res;
        }catch(Exception e){
            System.out.println("PropertyCalc: Unable to calculate properties:");
            e.printStackTrace();
            System.exit(1);
        }
        return null;
    }
    @Override
    public String getName() {
        return "Resources_PropertiesWorkaround";
    }      
}
