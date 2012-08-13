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
import edacc.configurator.models.rf.CensoredRandomForest;
import edacc.model.ExperimentResult;
import edacc.model.Instance;
import edacc.model.InstanceDAO;
import edacc.model.InstanceHasProperty;
import edacc.parameterspace.Parameter;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.domain.CategoricalDomain;
import edacc.parameterspace.domain.FlagDomain;
import edacc.parameterspace.domain.IntegerDomain;
import edacc.parameterspace.domain.OrdinalDomain;
import edacc.parameterspace.domain.RealDomain;
import edacc.parameterspace.graph.ParameterGraph;

public class SMBO extends SearchMethods {
    private ParameterGraph pspace;
    private List<SolverConfiguration> generatedConfigs = new ArrayList<SolverConfiguration>();
    private List<Parameter> configurableParameters = new ArrayList<Parameter>();
    private List<String> instanceFeatureNames = new LinkedList<String>();
    private List<Instance> instances;
    private Map<Integer, Integer> instanceFeaturesIx = new HashMap<Integer, Integer>();
    private double[][] instanceFeatures;
    
    private CensoredRandomForest model;

    public SMBO(AAC pacc, API api, Random rng, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
        super(pacc, api, rng, parameters, firstSCs, referenceSCs);
        pspace = api.loadParameterGraphFromDB(parameters.getIdExperiment());
        
        // TODO: Load from configuration?
        instanceFeatureNames.add("nvars");
        instanceFeatureNames.add("nclauses");
        
        // Load instance features
        instances = InstanceDAO.getAllByExperimentId(parameters.getIdExperiment());
        instanceFeatures = new double[instances.size()][instanceFeatureNames.size()];
        for (Instance instance: instances) {
            instanceFeaturesIx.put(instance.getId(), instances.indexOf(instance));
            
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
                instanceFeatures[instances.indexOf(instance)][instanceFeatureNames.indexOf(featureName)] = 2; //featureValueByName.get(featureName);
            }
        }
        
        configurableParameters.addAll(api.getConfigurableParameters(parameters.getIdExperiment()));
        
        int[] catDomainSizes = new int[configurableParameters.size() + instanceFeatureNames.size()];
        
        for (Parameter p: configurableParameters) {
            if (p.getDomain() instanceof FlagDomain) {
                catDomainSizes[configurableParameters.indexOf(p)] = 2;
            }
            else if (p.getDomain() instanceof CategoricalDomain || p.getDomain() instanceof OrdinalDomain) {
                catDomainSizes[configurableParameters.indexOf(p)] = p.getDomain().getDiscreteValues().size();
            }
        }
        
        model = new CensoredRandomForest(20, 0, 5000.0, 1.0, catDomainSizes, rng);
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
            int numJobs = 0;
            for (SolverConfiguration config: generatedConfigs) numJobs += config.getNumFinishedJobs();
            long start = System.currentTimeMillis();
            updateModel();
            pacc.log("Learning the model from " + generatedConfigs.size() + " configs and " + numJobs + " runs in total took " + (System.currentTimeMillis() - start) + " ms");
            
            List<SolverConfiguration> newConfigs = new ArrayList<SolverConfiguration>();
            for (int i = 0; i < 20; i++) {
                ParameterConfiguration paramConfig = pspace.getRandomConfiguration(rng);
                int idSC = api.createSolverConfig(parameters.getIdExperiment(), paramConfig, api.getCanonicalName(parameters.getIdExperiment(), paramConfig));
                newConfigs.add(new SolverConfiguration(idSC, paramConfig, parameters.getStatistics()));
            }
            
            generatedConfigs.addAll(newConfigs);
            return newConfigs;
        }
    }
    
    private void updateModel() {
        double[][] theta = new double[generatedConfigs.size()][];
        Map<SolverConfiguration, Integer> solverConfigTheta = new HashMap<SolverConfiguration, Integer>();
        int countJobs = 0;
        int cIx = 0;
        for (SolverConfiguration config: generatedConfigs) {
            solverConfigTheta.put(config, cIx);
            theta[cIx] = paramConfigToTuple(config.getParameterConfiguration());
            countJobs += config.getNumFinishedJobs();
            cIx++;
        }

        int[][] theta_inst_idxs = new int[countJobs][2];
        boolean[] censored = new boolean[countJobs];
        double[] y = new double[countJobs];
        
        int jIx = 0;
        for (SolverConfiguration config: generatedConfigs) {
            for (ExperimentResult run: config.getFinishedJobs()) {
                theta_inst_idxs[jIx][0] = solverConfigTheta.get(config);
                theta_inst_idxs[jIx][1] = instanceFeaturesIx.get(run.getInstanceId());
                censored[jIx] = !run.getResultCode().isCorrect();
                y[jIx] = parameters.getStatistics().getCostFunction().singleCost(run);
                jIx++;
            }
        }
        
        model.learnModel(theta, instanceFeatures, configurableParameters.size(), instanceFeatureNames.size(), theta_inst_idxs, y, censored, 0);
    }

    @Override
    public List<String> getParameters() {
        // TODO Auto-generated method stub
        return new ArrayList<String>();
    }

    @Override
    public void searchFinished() {
        // TODO Auto-generated method stub
        
    }
    
    private double[] paramConfigToTuple(ParameterConfiguration paramConfig) {
        double[] theta = new double[configurableParameters.size()];
        for (Parameter p: configurableParameters) {
            int pIx = configurableParameters.indexOf(p);
            Object paramValue = paramConfig.getParameterValue(p);
            if (p.getDomain() instanceof RealDomain) {
                if (paramValue == null) theta[pIx] = ((RealDomain)p.getDomain()).getLow(); // TODO: handle missing values
                theta[pIx] = (Double)paramValue;
            } else if (p.getDomain() instanceof IntegerDomain) {
                if (paramValue == null) theta[pIx] = ((IntegerDomain)p.getDomain()).getLow() - 1; // TODO: handle missing values
                theta[pIx] = (Integer)paramValue;
            } else if (p.getDomain() instanceof CategoricalDomain) {
                Map<String, Integer> valueMap = new HashMap<String, Integer>();
                int intVal = 1;
                for (String val: ((CategoricalDomain)p.getDomain()).getCategories()) {
                    valueMap.put(val, intVal++);
                }
                valueMap.put(null, 0);
                
                theta[pIx] = valueMap.get((String)paramValue);
            } else if (p.getDomain() instanceof OrdinalDomain) {
                Map<String, Integer> valueMap = new HashMap<String, Integer>();
                int intVal = 1;
                for (String val: ((OrdinalDomain)p.getDomain()).getOrdered_list()) {
                    valueMap.put(val, intVal++);
                }
                valueMap.put(null, 0);
                
                theta[pIx] = valueMap.get((String)paramValue);
            } else if (p.getDomain() instanceof FlagDomain) {
                if (FlagDomain.FLAGS.ON.equals(paramValue)) theta[pIx] = 1;
                else theta[pIx] = 0;
            } else {
                if (paramValue == null) theta[pIx] = 0;
                theta[pIx] = paramValue.hashCode();
            }
        }
        
        return theta;
    }

}
