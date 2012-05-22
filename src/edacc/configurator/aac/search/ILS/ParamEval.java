/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edacc.configurator.aac.search.ILS;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;
import java.util.*;

import edacc.parameterspace.*;
import edacc.parameterspace.domain.*;
import edacc.parameterspace.graph.*;
import edacc.util.Pair;

/**
 *
 * @author fr
 */
public class ParamEval {
    private ParameterGraph graph;
    private LinkedList<Parameter> params;
    
    public ParamEval(ParameterGraph g){
        graph = g;
        params = new LinkedList<Parameter>();
        Map<String, Parameter> pMap = graph.getParameterMap();
        Set<Map.Entry<String, Parameter>> pSet=graph.getParameterMap().entrySet();
        Iterator<Map.Entry<String,Parameter>> pit = pSet.iterator();
        while(pit.hasNext()){
            params.add(pit.next().getValue());            
        }
    }
    
    public Map<Parameter, Double> calculateParameterCoefficients(SolverConfiguration starter, 
                                                        List<SolverConfiguration> configs){ 
        /* First, we create a HashMap that contains a List useful stats for each parameter p.
         * For each config c that differs from starter in parameter p, the list will contain
         * a pair of double values:
         *  - Difference between config cost and starter cost
         *  - Normalised distance between config and starter in the parameter space
         */
        HashMap<Parameter, LinkedList<Pair<Double, Double>>> stats = 
                            new HashMap<Parameter, LinkedList<Pair<Double,Double>>>();
        double starterCost = getCost(starter);
        HashMap<Parameter, Double> normalisedStarterParams = new HashMap<Parameter, Double>();
        double d;
        for(Parameter p : params){
            d = normaliseDomain(p, starter.getParameterConfiguration().getParameterValue(p));
            normalisedStarterParams.put(p, d);
            stats.put(p, new LinkedList<Pair<Double, Double>>());
        }
        
        Pair<Double, Double> vals;        
        for(SolverConfiguration c : configs){
            Parameter p = paramDifference(c, starter);
            vals = getStats(c, p);
            vals.setFirst(vals.getFirst()-starterCost);
            vals.setSecond(vals.getSecond()-normalisedStarterParams.get(p));
            stats.get(p).add(vals);
        }
        
        /* with these lists, we now calculate (for each parameter) the variance between 
         * the previously calculated costDifference- and distance- values.
         * Then, we divide costDifference by distance, to get a measure of how strongly altering
         * the parameter affects the cost of the configuration
         */
        HashMap<Parameter, Double> coefficients = new HashMap<Parameter, Double>();
        for(Parameter p : params){
            LinkedList<Pair<Double, Double>> values = stats.get(p);
            //calculate the averages:
            double avgCostDif = 0, avgDistance = 0;
            for(Pair<Double, Double> v : values){
                avgCostDif += v.getFirst();
                avgDistance += v.getSecond();
            }
            avgCostDif = avgCostDif / ((double)values.size());
            avgDistance = avgDistance / ((double)values.size());
            
            //calculate variances
            double varCostDif=0, varDistance=0;
            for(Pair<Double, Double> v: values){
                varCostDif += Math.pow(v.getFirst()-avgCostDif, 2);
                varDistance += Math.pow(v.getSecond()-avgDistance, 2);
            }
            coefficients.put(p, varCostDif/varDistance);
        }
        // as a last step, we normalise the calculated Coefficients so that their sum equals 1
        double sum = 0;
        for(Parameter p : params){
            sum += coefficients.get(p);
        }
        double tmp;
        for(Parameter p : params){
            tmp = coefficients.remove(p);
            coefficients.put(p, (tmp/sum));
        }
        
        return coefficients;
    }
    
    /* returns (cost, normalisedPosition)
     * 
     */
    public Pair<Double, Double> getStats(SolverConfiguration c, Parameter p){        
        double dist = normaliseDomain(p, c.getParameterConfiguration().getParameterValue(p));
        return new Pair<Double, Double>(getCost(c), dist);
    }
    
    public double getCost(SolverConfiguration c){
        return ((double)c.getCost())/((double)c.getNumFinishedJobs());
    }
    
    
    
    public Parameter paramDifference(SolverConfiguration c1, SolverConfiguration c2){
        return paramDifference(c1.getParameterConfiguration(), c2.getParameterConfiguration());
    }
    
    public Parameter paramDifference(ParameterConfiguration c1, ParameterConfiguration c2){
        Parameter dif=null;
        int count = 0;
        for(Parameter p : params){
            if(!c1.getParameterValue(p).equals(c2.getParameterValue(p))){
                count++;
                dif = p;
            }
        }
        if(count!=1){
            System.out.println("ERROR: ParameterConfigs differ in more or less than 1 Parameter: "
                    +count+"!");
        }
        return dif;
    }
        
    public double normaliseDomain(Parameter p, Object o){
        return normaliseDomain(p.getDomain(), o);
    }
    
    public double normaliseDomain(Domain d, Object o){
        if(!d.contains(o))
            return -1;
        
        if(d instanceof IntegerDomain){
            IntegerDomain id = (IntegerDomain)d;
            long low = id.getLow();
            long high = id.getHigh();
            long val = (Integer)o;
            high = high - low;
            val = val -low;
            return ((double)val)/((double)high);
        }
        else if(d instanceof RealDomain){
            RealDomain rd = (RealDomain)d;
            double val = (Double)o;
            double high = rd.getHigh();
            double low = rd.getLow();
            high = high -low;
            val = val - low;
            return val/high;
        }
        else if(d instanceof FlagDomain){
            Set<FlagDomain.FLAGS> flags = ((FlagDomain)d).getValues();
            if(flags.contains(FlagDomain.FLAGS.ON))
                return 1;
            else
                return 0;
        }
        else if(d instanceof OrdinalDomain){
            List<Object> vals = ((OrdinalDomain)d).getDiscreteValues();
            double size = vals.size()-1;
            if(size==0)
                return 0;
            double pos = vals.indexOf(o);
            return pos/size;
        }
        else if(d instanceof CategoricalDomain){
            Set<String> cats = ((CategoricalDomain)d).getCategories();
            double size = cats.size()-1;
            if(size==0)
                return 0;
            double pos = 0;
            Iterator<String> it = cats.iterator();
            while(it.hasNext()){
                if(it.next().equals(o))
                    break;
                pos += 1;
            }
            return pos/size;
        }
        else if(d instanceof MixedDomain){
            List<Domain> doms = ((MixedDomain)d).getDomains();
            double size = doms.size()-1;
            if(size == 0){
                return normaliseDomain(doms.get(0), o);
            }
            double count = 0;
            for(Domain dom : doms){
                if(dom.contains(o))
                    return (normaliseDomain(dom, o)+count)/size;
                count++;
            }            
        }else if(d instanceof OptionalDomain){
            System.out.println("ERROR: OptionalDomain in parametergraph!");            
        }
        //TODO implement Error handling
        System.out.println("ERROR: Unrecognised Domain: "+d.getName());
        return 0;
    }    
}
