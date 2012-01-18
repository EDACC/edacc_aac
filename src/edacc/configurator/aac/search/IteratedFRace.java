package edacc.configurator.aac.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;
import edacc.parameterspace.Parameter;

public class IteratedFRace extends SearchMethods {
    private int iteration = 0;
    private List<SolverConfiguration> raceSurvivors;
    private float total_budget;
    private float budget_used = 0.0f;
    private ParameterGraph pspace;
    private Map<Parameter, Float> parameterStdDev;
    
    private int max_iterations;

    public IteratedFRace(AAC pacc, API api, Random rng, Parameters parameters) throws Exception {
        super(pacc, api, rng, parameters);
        total_budget = parameters.getMaxTuningTime();
        pspace = api.loadParameterGraphFromDB(parameters.getIdExperiment());
        max_iterations = 3 + (int) (2 + Math.round(Math.log(api.getConfigurableParameters(parameters.getIdExperiment()).size()) / Math.log(2)));
        parameterStdDev = new HashMap<Parameter, Float>();
        for (Parameter p: api.getConfigurableParameters(parameters.getIdExperiment())) {
            parameterStdDev.put(p, 1.0f);
        }
    }

    @Override
    public List<SolverConfiguration> generateNewSC(int num, SolverConfiguration currentBestSC) throws Exception {
        if (iteration > max_iterations) return new ArrayList<SolverConfiguration>();
        
        pacc.log("Starting new iteration of I/F-Race");
        List<SolverConfiguration> newSC = new ArrayList<SolverConfiguration>();
        if (iteration > 0) {
            Collections.sort(raceSurvivors);
            Collections.reverse(raceSurvivors);
            int Ns = Math.min(raceSurvivors.size(), getNmin());
            for (int i = 0; i < Ns; i++) newSC.add(raceSurvivors.get(i));
            iteration++;
            int Nlnext = getNumRaceCandidates();
            iteration--; // ...
            
            RandomCollection<SolverConfiguration> roulette = new RandomCollection<SolverConfiguration>(rng);
            for (int i = 0; i < Ns; i++) roulette.add((Ns - (i+1) + 1.0)/(Ns * (Ns + 1) / 2.0), raceSurvivors.get(i));
            
            pacc.log("Generating " + (Nlnext - Ns) + " new configurations based on the " + Ns + " elite configurations from the last race, Parameters are sampled with the following stddev:");
            for (Parameter p: parameterStdDev.keySet()) {
                pacc.log(p.getName() + ": " + parameterStdDev.get(p));
            }
            for (int i = 0; i < Nlnext - Ns; i++) {
                SolverConfiguration eliteConfig = roulette.next();
                ParameterConfiguration paramConfig = pspace.getGaussianRandomNeighbour(eliteConfig.getParameterConfiguration(), rng, parameterStdDev, 1000, true);
                int idSC = api.createSolverConfig(parameters.getIdExperiment(), paramConfig, "I" + iteration + " " + api.getCanonicalName(parameters.getIdExperiment(), paramConfig));
                newSC.add(new SolverConfiguration(idSC, paramConfig, parameters.getStatistics()));
            }

            for (Parameter p: parameterStdDev.keySet()) {
                float sigma = parameterStdDev.get(p);
                parameterStdDev.put(p, sigma*(float)Math.pow(1.0f/Nlnext, 1.0f/(float)parameterStdDev.size()));
            }
        } else {
            for (int i = 0; i < num; i++) {
                ParameterConfiguration paramConfig = pspace.getRandomConfiguration(rng);
                int idSC = api.createSolverConfig(parameters.getIdExperiment(), paramConfig, api.getCanonicalName(parameters.getIdExperiment(), paramConfig));
                newSC.add(new SolverConfiguration(idSC, paramConfig, parameters.getStatistics()));
            }
        }
        iteration++;
        return newSC;
    }

    @Override
    public String toString() {
        return "\nIteratedFRace\n";
    }

    @Override
    public void listParameters() {
        // TODO Auto-generated method stub

    }
    
    public float getRacingComputationalBudget() {
        if (total_budget == -1) return 20000; // TODO: no limit was given, use something clever 
        return (total_budget - budget_used) / (max_iterations - iteration + 1);
    }
    
    public void setRaceSurvivors(List<SolverConfiguration> survivors) {
        raceSurvivors = survivors;
    }
    
    public int getNmin() throws Exception {
        long d = api.getConfigurableParameters(parameters.getIdExperiment()).size();
        return 2 * (int) (2 + Math.round(Math.log((double)d) / Math.log(2.0))); 
    }
    
    public int getNumRaceCandidates() {
        return (int) (getRacingComputationalBudget()/(500+5+iteration));
    }
    
    public void updateBudgetUsed(float db) {
        budget_used += db;
    }
    
    private class RandomCollection<E> {
        private final NavigableMap<Double, E> map = new TreeMap<Double, E>();
        private final Random random;
        private double total = 0;

        public RandomCollection(Random rng) {
            this.random = rng;
        }

        public void add(double weight, E result) {
            if (weight <= 0) return;
            total += weight;
            map.put(total, result);
        }

        public E next() {
            double value = random.nextDouble() * total;
            return map.ceilingEntry(value).getValue();
        }
    }

}
