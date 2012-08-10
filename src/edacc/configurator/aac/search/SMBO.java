package edacc.configurator.aac.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.Instance;
import edacc.model.InstanceDAO;
import edacc.model.InstanceHasProperty;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;

public class SMBO extends SearchMethods {
    private ParameterGraph pspace;
    private List<SolverConfiguration> generatedConfigs = new ArrayList<SolverConfiguration>();
    private List<Instance> instances;
    private double[][] instanceFeatures;

    public SMBO(AAC pacc, API api, Random rng, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
        super(pacc, api, rng, parameters, firstSCs, referenceSCs);
        pspace = api.loadParameterGraphFromDB(parameters.getIdExperiment());
        
        List<String> instanceFeatureNames = new LinkedList<String>();
        instanceFeatureNames.add("nvars");
        instanceFeatureNames.add("nclauses");
        
        // Load instance features
        instances = InstanceDAO.getAllByExperimentId(parameters.getIdExperiment());
        instanceFeatures = new double[instances.size()][instanceFeatureNames.size()];
        for (Instance instance: instances) {
            Map<String, Float> featureValueByName = new HashMap<String, Float>();
            for (InstanceHasProperty ihp: instance.getPropertyValues().values()) {
                if (!instanceFeatureNames.contains(ihp.getProperty().getName())) continue;
                try {
                    featureValueByName.put(ihp.getProperty().getName(), Float.valueOf(ihp.getValue()));
                } catch (Exception e) {
                    throw new Exception("All instance features have to be numeric.");
                }
            }
            
            for (String featureName: instanceFeatureNames) {
                instanceFeatures[instances.indexOf(instance)][instanceFeatureNames.indexOf(featureName)] = featureValueByName.get(featureName);
            }
        }
    }

    @Override
    public List<SolverConfiguration> generateNewSC(int num) throws Exception {
        if (generatedConfigs.isEmpty()) {
            // start with some random configs
            for (int i = 0; i < 20; i++) {
                ParameterConfiguration paramConfig = pspace.getRandomConfiguration(rng);
                int idSC = api.createSolverConfig(parameters.getIdExperiment(), paramConfig, api.getCanonicalName(parameters.getIdExperiment(), paramConfig));
                generatedConfigs.add(new SolverConfiguration(idSC, paramConfig, parameters.getStatistics()));
            }   
            return generatedConfigs;
        } else {
            
        }
        
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<String> getParameters() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void searchFinished() {
        // TODO Auto-generated method stub
        
    }

}
