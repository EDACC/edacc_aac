package edacc.configurator.aac.clustering;

import edacc.api.costfunctions.CostFunction;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.model.ExperimentResult;
import java.util.LinkedList;

/**
 *
 * @author mugrauer
 */
public class InstanceData {
    private int instanceID, seed;
    private LinkedList<Double> costValues;
    private double avg;
    CostFunction costFunction;
    
    public InstanceData(int instanceID, int seed, CostFunction cf){
        this.instanceID = instanceID;
        this.seed = seed;
        costValues = new LinkedList<Double>();
        avg = 0d;
        costFunction = cf;
    }
    public InstanceData(InstanceIdSeed iis, CostFunction cf){
        this(iis.instanceId, iis.seed, cf);
    }
    
    public void addValue(ExperimentResult r){
        System.out.print("addValue: old value: "+avg+", toAdd: "+costFunction.singleCost(r) +", new value: ");
        avg = avg*((double)costValues.size());
        costValues.add((double)r.getCost());
        avg += costFunction.singleCost(r);
        avg = avg/((double)costValues.size());
        System.out.println(avg);
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
    /*
    public InstanceIdSeed getInstance(){
        return new InstanceIdSeed(instanceID, seed);
    }*/
    
    public Double getAvg(){
        return avg;
    }
}
