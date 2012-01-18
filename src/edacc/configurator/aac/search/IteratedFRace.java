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
import edacc.configurator.aac.racing.FRace;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;
import edacc.parameterspace.Parameter;

public class IteratedFRace extends SearchMethods {
    private int iteration = 0;
    private List<SolverConfiguration> raceSurvivors;
    private ParameterGraph pspace;
    //private Map<Parameter, Float> parameterStdDev;
    private float parameterStdDev;
    private FRace race = null;

    public IteratedFRace(AAC pacc, API api, Random rng, Parameters parameters) throws Exception {
        super(pacc, api, rng, parameters);
        pspace = api.loadParameterGraphFromDB(parameters.getIdExperiment());
        /*parameterStdDev = new HashMap<Parameter, Float>();
        for (Parameter p: api.getConfigurableParameters(parameters.getIdExperiment())) {
            parameterStdDev.put(p, 1.0f);
        }*/
        this.parameterStdDev = 1.0f; // initial standard deviation for sampling
    }

    @Override
    public List<SolverConfiguration> generateNewSC(int num, SolverConfiguration currentBestSC) throws Exception {
        pacc.log("Starting new iteration of I/F-Race");
        List<SolverConfiguration> newSC = new ArrayList<SolverConfiguration>();
        if (iteration > 0) {
            if (race == null) {
                if (!(pacc.racing instanceof FRace)) throw new Exception("Iterated FRace can't be used with any other racing method than FRace");
                race = (FRace)pacc.racing;
            }
            
            int Nlnext = race.getNumRaceConfigurations();
            parameterStdDev *= (float)Math.pow(1.0f/Nlnext, 1.0f/(float)api.getConfigurableParameters(parameters.getIdExperiment()).size());
            if (parameterStdDev < 0.000001f) {
                pacc.log("Parameter standard deviation reduced to " + parameterStdDev + " which is probably insignificant enough to stop here.");
                return newSC;
            }
            
            raceSurvivors = race.getRaceSurvivors();
            Collections.sort(raceSurvivors);
            Collections.reverse(raceSurvivors);
            int Ns = Math.min(raceSurvivors.size(), race.getNmin());
            for (int i = 0; i < Ns; i++) newSC.add(raceSurvivors.get(i));
            
            RandomCollection<SolverConfiguration> roulette = new RandomCollection<SolverConfiguration>(rng);
            for (int i = 0; i < Ns; i++) roulette.add((Ns - (i+1) + 1.0)/(Ns * (Ns + 1) / 2.0), raceSurvivors.get(i));
            
            pacc.log("Generating " + (Nlnext - Ns) + " new configurations based on the " + Ns + " elite configurations from the last race, Parameters are sampled with the stdDev " + parameterStdDev);
            /*for (Parameter p: parameterStdDev.keySet()) {
                pacc.log(p.getName() + ": " + parameterStdDev.get(p));
            }*/
            for (int i = 0; i < Nlnext - Ns; i++) {
                SolverConfiguration eliteConfig = roulette.next();
                ParameterConfiguration paramConfig = pspace.getGaussianRandomNeighbour(eliteConfig.getParameterConfiguration(), rng, parameterStdDev, 1, true);
                int idSC = api.createSolverConfig(parameters.getIdExperiment(), paramConfig, "I" + iteration + " " + api.getCanonicalName(parameters.getIdExperiment(), paramConfig));
                newSC.add(new SolverConfiguration(idSC, paramConfig, parameters.getStatistics()));
            }

            /*for (Parameter p: parameterStdDev.keySet()) {
                float sigma = parameterStdDev.get(p);
                parameterStdDev.put(p, sigma*(float)Math.pow(1.0f/Nlnext, 1.0f/(float)parameterStdDev.size()));
            }*/
            
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
