package edacc.configurator.aac.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

public class IteratedFRace extends SearchMethods {
    private int iteration = 0;
    private List<SolverConfiguration> raceSurvivors;
    private ParameterGraph pspace;
    private float parameterStdDev;
    private FRace race = null;
    
    double initialParameterStdDev = 0.3f;
    double minStdDev = 1e-6;

    public IteratedFRace(AAC pacc, API api, Random rng, Parameters parameters, SolverConfiguration firstSC) throws Exception {
        super(pacc, api, rng, parameters, firstSC);
        pspace = api.loadParameterGraphFromDB(parameters.getIdExperiment());

        String val;
        if ((val = parameters.getSearchMethodParameters().get("IteratedFRace_initialParameterStdDev")) != null)
            this.initialParameterStdDev = Double.parseDouble(val);
        if ((val = parameters.getSearchMethodParameters().get("IteratedFRace_minStdDev")) != null)
            this.minStdDev = Double.parseDouble(val);

        this.parameterStdDev = (float) initialParameterStdDev; // initial standard deviation for sampling
    }

    @Override
    public List<SolverConfiguration> generateNewSC(int num) throws Exception {
        pacc.log("Starting new iteration of I/F-Race");
        List<SolverConfiguration> newSC = new ArrayList<SolverConfiguration>();
        if (iteration > 0) {
            if (race == null) {
                // Only at this point we can be sure that pacc.racing is instantiated
                if (!(pacc.racing instanceof FRace)) throw new Exception("Iterated FRace can't be used with any other racing method than FRace");
                race = (FRace)pacc.racing;
            }
            
            int Nlnext = race.getNumRaceConfigurations();
            
            // Adjust the standard deviation used for sampling new configurations
            parameterStdDev *= (float)Math.pow(1.0f/Nlnext, 1.0f/(float)api.getConfigurableParameters(parameters.getIdExperiment()).size());
            if (parameterStdDev < minStdDev) {
                pacc.log("Parameter standard deviation decreased to " + parameterStdDev + " which is probably insignificant enough to stop here. Starting over.");
                this.parameterStdDev = (float)initialParameterStdDev;
                this.iteration = 0;
                if (parameters.getIdExperimentEvaluation() > 0) {
                    try {
                        for (SolverConfiguration solverConfig: race.getRaceSurvivors()) {
                            String name = ("".equals(parameters.getEvaluationSolverConfigName()) ? "" : parameters.getEvaluationSolverConfigName() + " ") + solverConfig.getName() + " ID: " + solverConfig.getIdSolverConfiguration();
                            pacc.log("c Adding " + solverConfig.getName() + " ID: " + solverConfig.getIdSolverConfiguration() + " to evaluation experiment with name " + name);
                            int idSC = api.createSolverConfig(parameters.getIdExperimentEvaluation(), solverConfig.getParameterConfiguration(), name);
                            int CPUTimeLimit[] = new int[parameters.getMaxParcoursExpansionFactor() * api.getCourseLength(parameters.getIdExperimentEvaluation())];
                            int wallClockTimeLimit[] = new int[parameters.getMaxParcoursExpansionFactor() * api.getCourseLength(parameters.getIdExperimentEvaluation())];
                            for (int i = 0; i < CPUTimeLimit.length; i++) {
                                CPUTimeLimit[i] = parameters.getJobCPUTimeLimit();
                                wallClockTimeLimit[i] = parameters.getJobWallClockTimeLimit();
                            }
                            api.launchJob(parameters.getIdExperimentEvaluation(), idSC, CPUTimeLimit, wallClockTimeLimit, CPUTimeLimit.length, new Random(parameters.getRacingSeed()));
                        }
                    } catch (Exception e) {
                        pacc.log("c Exception thrown when trying to add configuration to evaluation experiment: " + e.getMessage());
                    }
                }
                return generateNewSC(num);
            }
            
            raceSurvivors = race.getRaceSurvivors();
            if (raceSurvivors.isEmpty()) {
                // this means the race terminated throwing out all configurations (they all probably only produced timeouts)
                // start over with new random configs
                this.parameterStdDev = (float)initialParameterStdDev;
                this.iteration = 0;
                return generateNewSC(num);
            }
            Collections.sort(raceSurvivors);
            Collections.reverse(raceSurvivors); // lowest cost first
            int Ns = Math.min(raceSurvivors.size(), race.getNmin());
            for (int i = 0; i < Ns; i++) newSC.add(raceSurvivors.get(i));
            
            RandomCollection<SolverConfiguration> roulette = new RandomCollection<SolverConfiguration>(rng);
            for (int i = 0; i < Ns; i++) roulette.add((Ns - (i+1) + 1.0)/(Ns * (Ns + 1) / 2.0), raceSurvivors.get(i));
            
            pacc.log("Generating " + (Nlnext - Ns) + " new configurations based on the " + Ns + " elite configurations from the last race, Parameters are sampled with the stdDev " + parameterStdDev);
            for (int i = 0; i < Nlnext - Ns; i++) {
                SolverConfiguration eliteConfig = roulette.next();
                ParameterConfiguration paramConfig = new ParameterConfiguration(eliteConfig.getParameterConfiguration());
                pspace.mutateParameterConfiguration(rng, paramConfig, parameterStdDev, 1.0f);
                int idSC = api.createSolverConfig(parameters.getIdExperiment(), paramConfig, "I" + iteration + " " + api.getCanonicalName(parameters.getIdExperiment(), paramConfig));
                newSC.add(new SolverConfiguration(idSC, paramConfig, parameters.getStatistics()));
                pacc.log("Created " + api.getCanonicalName(parameters.getIdExperiment(), paramConfig) + " based on the elite configuration " + api.getCanonicalName(parameters.getIdExperiment(), eliteConfig.getParameterConfiguration()));
            }
        } else {
            // Start with random configurations
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
        System.out.println("--- IteratedFRace parameters ---");
        System.out.println("IteratedFRace_initialParameterStdDev = "+this.initialParameterStdDev+ " (Initial normalized standard deviation used to sample the second generation of configurations based on the elite configurations obtained from the race)");
        System.out.println("IteratedFRace_minStdDev = "+this.minStdDev+ " (Down to which value should the standard deviation be reduced before the search terminates)");
        System.out.println("-----------------------\n");

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
