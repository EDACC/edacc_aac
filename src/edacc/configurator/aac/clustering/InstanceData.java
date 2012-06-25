package edacc.configurator.aac.clustering;

import edacc.configurator.aac.InstanceIdSeed;
import edacc.model.ExperimentResult;
import edacc.util.Pair;
import java.util.LinkedList;

/**
 *
 * @author mugrauer
 */
public class InstanceData {
    private int instanceID, seed;
    private LinkedList<Double> costValues;
    private double avg;
    
    public InstanceData(int instanceID, int seed){
        this.instanceID = instanceID;
        this.seed = seed;
        costValues = new LinkedList<Double>();
        avg = 0D;
    }
    public InstanceData(InstanceIdSeed iis){
        this(iis.instanceId, iis.seed);
    }
    
    public void addValue(ExperimentResult r){
        avg = avg*((double)costValues.size());
        costValues.add((double)r.getCost());
        avg += (double)r.getCost();
        avg = avg/((double)costValues.size());
    }

    public LinkedList<Double> getCostValues() {
        return costValues;
    }

    public int getInstanceID() {
        return instanceID;
    }

    public int getSeed() {
        return seed;
    }
    
    public Pair<Integer, Integer> getInstance(){
        return new Pair<Integer, Integer>(instanceID, seed);
    }
    
    public Double getAvg(){
        return avg;
    }
}
