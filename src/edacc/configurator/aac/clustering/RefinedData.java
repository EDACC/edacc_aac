/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edacc.configurator.aac.clustering;

import edacc.configurator.aac.InstanceIdSeed;
import java.util.HashMap;

/**
 *
 * @author mugrauer
 */
public class RefinedData {
    private double[][] data;
    private HashMap<Integer, InstanceIdSeed> indexToInstance;

    public RefinedData(double[][] data, HashMap<Integer, InstanceIdSeed> indexToInstance) {
        this.data = data;
        this.indexToInstance = indexToInstance;
    }

    public double[][] getData() {
        return data;
    }
    
    public InstanceIdSeed getInstanceForIndex(int index){
        return indexToInstance.get(index);
    }
}
