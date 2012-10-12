package edacc.configurator.aac.clustering;

import edacc.api.costfunctions.CostFunction;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.model.ExperimentResult;
import java.util.LinkedList;
import org.apache.commons.math.stat.descriptive.moment.Variance;

/**
 *
 * @author mugrauer
 */
public class InstanceData {
    private int instanceID, seed;
    private LinkedList<Double> costValues;
    private double avg;
    private double var;
    private CostFunction costFunction;
    private Variance variance;
    
    public InstanceData(int instanceID, int seed, CostFunction cf){
        this.instanceID = instanceID;
        this.seed = seed;
        costValues = new LinkedList<Double>();
        avg = 0d;
        var = 0;
        costFunction = cf;
        variance = new Variance();
    }
    public InstanceData(InstanceIdSeed iis, CostFunction cf){
        this(iis.instanceId, iis.seed, cf);
    }
    
    public void addValue(ExperimentResult r){
        double oldSize = costValues.size();
        double newSize = oldSize +1;
        
        costValues.add((double)r.getCost());        
        
        //calculate new average
        avg = avg*oldSize;        
        avg += costFunction.singleCost(r);
        avg = avg/newSize;
        
        double[] vals = new double[costValues.size()];
        int i=0;
        for(Double d : costValues){
            vals[i] = d;
            i++;
        }
        var = variance.evaluate(vals, avg);
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
    
    public InstanceIdSeed getInstanceIdSeed(){
        return new InstanceIdSeed(instanceID, seed);
    }
       
    public Double getAvg(){
        return avg;
    }
    
    public Double getNormalisedVariance(){
        return var/avg;
    }
}
