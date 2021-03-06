package edacc.configurator.aac.search;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;

public class IteratedFRace extends SearchMethods {
    private int iteration = 0;
    private List<SolverConfiguration> raceSurvivors;
    private ParameterGraph pspace;
    private float parameterStdDev;
    
    double initialParameterStdDev = 0.3f;
    double minStdDev = 1e-6;

    public IteratedFRace(AAC pacc, API api, Random rng, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
        super(pacc, api, rng, parameters, firstSCs, referenceSCs);
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
            int Nlnext = num;
            
            // Adjust the standard deviation used for sampling new configurations
            parameterStdDev *= (float)Math.pow(1.0f/Nlnext, 1.0f/(float)api.getConfigurableParameters(parameters.getIdExperiment()).size());
            if (parameterStdDev < minStdDev) {
                pacc.log("Parameter standard deviation decreased to " + parameterStdDev + " which is probably insignificant enough to stop here. Starting over.");
                this.parameterStdDev = (float)initialParameterStdDev;
                this.iteration = 0;
                if (parameters.getIdExperimentEvaluation() > 0) {
                    try {
                        for (SolverConfiguration solverConfig: pacc.racing.getBestSolverConfigurations(null)) {
                            String name = ("".equals(parameters.getEvaluationSolverConfigName()) ? "" : parameters.getEvaluationSolverConfigName() + " ") + solverConfig.getName() + " ID: " + solverConfig.getIdSolverConfiguration();
                            pacc.log("c Adding " + solverConfig.getName() + " ID: " + solverConfig.getIdSolverConfiguration() + " to evaluation experiment with name " + name);
                            api.createSolverConfig(parameters.getIdExperimentEvaluation(), solverConfig.getParameterConfiguration(), name);
                        }
                    } catch (Exception e) {
                        pacc.log("c Exception thrown when trying to add configuration to evaluation experiment: " + e.getMessage());
                    }
                }
                return generateNewSC(num);
            }
            
            raceSurvivors = pacc.racing.getBestSolverConfigurations(null);
            if (raceSurvivors.isEmpty()) {
                // this means the race terminated throwing out all configurations (they all probably only produced timeouts)
                // start over with new random configs
                this.parameterStdDev = (float)initialParameterStdDev;
                this.iteration = 0;
                return generateNewSC(num);
            }
            int Ns = raceSurvivors.size();
            newSC.addAll(raceSurvivors);
            
            RandomCollection<SolverConfiguration> roulette = new RandomCollection<SolverConfiguration>(rng);
            for (int i = 0; i < Ns; i++) roulette.add((Ns - (i+1) + 1.0)/(Ns * (Ns + 1) / 2.0), raceSurvivors.get(i));
            
            pacc.log("Generating " + (Nlnext - Ns) + " new configurations based on the " + Ns + " elite configurations from the last race, Parameters are sampled with the stdDev " + parameterStdDev);
            for (int i = 0; i < Nlnext - Ns; i++) {
                SolverConfiguration eliteConfig = roulette.next();
                ParameterConfiguration paramConfig = new ParameterConfiguration(eliteConfig.getParameterConfiguration());
                pspace.mutateParameterConfiguration(rng, paramConfig, parameterStdDev, 1.0f);
                int maxTries = 10;
                while (api.exists(parameters.getIdExperiment(), paramConfig) != 0 && maxTries-- > 0) {
                    pspace.mutateParameterConfiguration(rng, paramConfig, parameterStdDev, 1.0f);
                }
                int idSC = api.createSolverConfig(parameters.getIdExperiment(), paramConfig, "I" + iteration + " " + api.getCanonicalName(parameters.getIdExperiment(), paramConfig));
                newSC.add(new SolverConfiguration(idSC, paramConfig, parameters.getStatistics()));
                pacc.log("Created " + api.getCanonicalName(parameters.getIdExperiment(), paramConfig) + " based on the elite configuration " + api.getCanonicalName(parameters.getIdExperiment(), eliteConfig.getParameterConfiguration()));
            }
        } else {
            // Start with random configurations and firstSCs ("default"), if they don't have any runs yet
            int numRandomConfigs = num;
            for (SolverConfiguration defaultSC: firstSCs) {
                if (defaultSC.getJobCount() == 0) {
                    newSC.add(defaultSC);
                    numRandomConfigs--;
                }
            }   
            for (int i = 0; i < numRandomConfigs; i++) {
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
    public List<String> getParameters() {
    	List<String> p = new LinkedList<String>();
    	p.add("% --- IteratedFRace parameters ---");
    	p.add("IteratedFRace_initialParameterStdDev = "+this.initialParameterStdDev+ " % (Initial normalized standard deviation used to sample the second generation of configurations based on the elite configurations obtained from the race)");
    	p.add("IteratedFRace_minStdDev = "+this.minStdDev+ " % (Down to which value should the standard deviation be reduced before the search terminates)");
    	p.add("% -----------------------\n");
        return p;

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

	@Override
	public void searchFinished() {
		// TODO Auto-generated method stub
		
	}

}
