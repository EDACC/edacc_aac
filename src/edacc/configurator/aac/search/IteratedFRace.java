package edacc.configurator.aac.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;

public class IteratedFRace extends SearchMethods {
    private int iteration = 0;
    private List<SolverConfiguration> raceSurvivors;
    private float total_budget;
    private float budget_used = 0.0f;
    private ParameterGraph pspace;
    
    private int max_iterations;

    public IteratedFRace(AAC pacc, API api, Random rng, Parameters parameters) throws Exception {
        super(pacc, api, rng, parameters);
        total_budget = parameters.getMaxTuningTime();
        pspace = api.loadParameterGraphFromDB(parameters.getIdExperiment());
        max_iterations = (int) (2 + Math.round(Math.log(api.getConfigurableParameters(parameters.getIdExperiment()).size()) / Math.log(2)));
    }

    @Override
    public List<SolverConfiguration> generateNewSC(int num, SolverConfiguration currentBestSC) throws Exception {
        List<SolverConfiguration> newSC = new ArrayList<SolverConfiguration>();
        if (iteration > 0) {
            for (int i = 0; i < num; i++) {
                // simply sample new configurations randomly around old configurations for now
                // TODO: update probability distributions using raceSurvivors
                SolverConfiguration raceSurvivor = raceSurvivors.get(rng.nextInt(raceSurvivors.size()));
                ParameterConfiguration paramConfig = new ParameterConfiguration(raceSurvivor.getParameterConfiguration());
                pspace.mutateParameterConfiguration(rng, raceSurvivor.getParameterConfiguration(), 0.1f, 1.0f);
                int idSC = api.createSolverConfig(parameters.getIdExperiment(), paramConfig, api.getCanonicalName(parameters.getIdExperiment(), paramConfig));
                newSC.add(new SolverConfiguration(idSC, paramConfig, parameters.getStatistics()));
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
        if (total_budget == -1) return 42; // TODO: no limit was given, use something clever 
        return (total_budget - budget_used) / (max_iterations - iteration + 1);
    }
    
    public void setRaceSurvivors(List<SolverConfiguration> survivors) {
        raceSurvivors = survivors;
    }
    
    public int getNmin() throws Exception {
        long d = api.getConfigurableParameters(parameters.getIdExperiment()).size();
        return (int) (2 + Math.round(Math.log((double)d) / Math.log(2.0))); 
    }
    
    public int getNumRaceCandidates() {
        return (int) (getRacingComputationalBudget()/(5+iteration));
    }
    
    public void updateBudgetUsed(float db) {
        budget_used += db;
    }

}
