package edacc.configurator.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import edacc.api.API;
import edacc.api.APIImpl;
import edacc.api.costfunctions.CostFunction;
import edacc.api.costfunctions.PARX;
import edacc.configurator.aac.SolverConfiguration;
import edacc.configurator.aac.StatisticFunction;
import edacc.configurator.aac.util.RInterface;
import edacc.configurator.math.PCA;
import edacc.configurator.models.rf.CensoredRandomForest;
import edacc.model.Experiment;
import edacc.model.ExperimentDAO;
import edacc.model.ExperimentResult;
import edacc.model.ExperimentResultDAO;
import edacc.model.Instance;
import edacc.model.InstanceDAO;
import edacc.model.InstanceHasProperty;
import edacc.model.Experiment.Cost;
import edacc.model.SolverConfigurationDAO;
import edacc.parameterspace.Parameter;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.domain.CategoricalDomain;
import edacc.parameterspace.domain.FlagDomain;
import edacc.parameterspace.domain.IntegerDomain;
import edacc.parameterspace.domain.OrdinalDomain;
import edacc.parameterspace.domain.RealDomain;
import edacc.parameterspace.graph.ParameterGraph;

public class CensoredRandomForestValidation {

    
    public static void main(String ... args) throws Exception {        
        API api = new APIImpl();
        
        api.connect("host", 3306, "db", "user", "pw");
        int idExperiment = 24;
        int CPUlimit = 10;
        int wallLimit = 10;
        
        ParameterGraph pspace = api.loadParameterGraphFromDB(idExperiment);
        
        List<Parameter> configurableParameters = new ArrayList<Parameter>();
        List<String> instanceFeatureNames = new LinkedList<String>();
        List<Instance> instances;
        Map<Integer, Integer> instanceFeaturesIx = new HashMap<Integer, Integer>();
        double[][] instanceFeatures;
        CostFunction par1CostFunc;
        
        
        // TODO: Load from configuration?
        //instanceFeatureNames.add("POSNEG-RATIO-CLAUSE-mean");
        
        instanceFeatureNames.add("instance-index");
        
        // Load instance features
        instances = InstanceDAO.getAllByExperimentId(idExperiment);
        instanceFeatures = new double[instances.size()][instanceFeatureNames.size()];
        for (Instance instance: instances) {
            instanceFeaturesIx.put(instance.getId(), instances.indexOf(instance));
            
            Map<String, Float> featureValueByName = new HashMap<String, Float>();
            for (InstanceHasProperty ihp: instance.getPropertyValues().values()) {
                if (!instanceFeatureNames.contains(ihp.getProperty().getName())) continue;
                try {
                    featureValueByName.put(ihp.getProperty().getName(), Float.valueOf(ihp.getValue()));
                } catch (Exception e) {
                    throw new Exception("All instance features have to be numeric (convertible to a Java Float).");
                }
            }
            
            featureValueByName.put("instance-index", Float.valueOf(instances.indexOf(instance)));
            
            for (String featureName: instanceFeatureNames) {
                instanceFeatures[instances.indexOf(instance)][instanceFeatureNames.indexOf(featureName)] = featureValueByName.get(featureName);
            }
        }
        System.out.println("Loaded instance features");
        
        // Project instance features into lower dimensional space using PCA
        PCA pca = new PCA(RInterface.getRengine());
        instanceFeatures = pca.transform(instanceFeatures.length, instanceFeatureNames.size(), instanceFeatures, 7);
        
        int numFeatures = instanceFeatureNames.size();
        instanceFeatureNames.clear();
        for (int i = 0; i < Math.min(7, numFeatures); i++) instanceFeatureNames.add("PC" + (i+1)); // rename instance features to reflect PCA transformation
        
        // Load information about the parameter space
        configurableParameters.addAll(api.getConfigurableParameters(idExperiment));
        int[] catDomainSizes = new int[configurableParameters.size() + instanceFeatureNames.size()];
        for (Parameter p: configurableParameters) {
            if (p.getDomain() instanceof FlagDomain) {
                catDomainSizes[configurableParameters.indexOf(p)] = 2;
            }
            else if (p.getDomain() instanceof CategoricalDomain || p.getDomain() instanceof OrdinalDomain) {
                catDomainSizes[configurableParameters.indexOf(p)] = p.getDomain().getDiscreteValues().size();
            }
        }
        
        double kappaMax = 0;
        if (ExperimentDAO.getById(idExperiment).getDefaultCost().equals(Cost.resultTime)) {
            kappaMax = CPUlimit;
            par1CostFunc = new PARX(Experiment.Cost.resultTime, true, 1.0f);
        } else if (ExperimentDAO.getById(idExperiment).getDefaultCost().equals(Cost.wallTime)) {
            kappaMax = wallLimit;
            par1CostFunc = new PARX(Experiment.Cost.wallTime, true, 1.0f);
        } else {
            kappaMax = ExperimentDAO.getById(idExperiment).getCostPenalty();
            par1CostFunc = new PARX(Experiment.Cost.cost, true, 1.0f);
        }
        
        int[][] condParents = null;
        int[][][] condParentVals = null;
        pspace.conditionalParentsForRF(configurableParameters, condParents, condParentVals);
        
        Random rng = new edacc.util.MersenneTwister(123);
        
        int i = 0;
        List<SolverConfiguration> solverConfigs = new LinkedList<SolverConfiguration>();
        Map<Integer, SolverConfiguration> scById = new HashMap<Integer, SolverConfiguration>();
        for (edacc.model.SolverConfiguration sc: SolverConfigurationDAO.getSolverConfigurationByExperimentId(idExperiment)) {
            SolverConfiguration csc = new SolverConfiguration(sc.getId(), api.getParameterConfiguration(idExperiment, sc.getId()), new StatisticFunction(par1CostFunc, true));
            System.out.println("Loaded configuration " + sc.getId());
            solverConfigs.add(csc);
            scById.put(csc.getIdSolverConfiguration(), csc);
            //if (i++ > 10) break;
        }
        System.out.println("Loaded configurations.");
        
        
        int countJobs = 0;
        for (ExperimentResult run: ExperimentResultDAO.getAllByExperimentId(idExperiment)) {
            scById.get(run.getSolverConfigId()).putJob(run);
            countJobs++;
        }
        
        System.out.println("Loaded " + countJobs + " runs.");
        
        
        for (int nTrees = 1; nTrees <= 256; nTrees *= 2) {
            double rep_rss = 0;
            int numRep = 2;
            for (int rep = 0; rep < numRep; rep++) {
                CensoredRandomForest model = new CensoredRandomForest(nTrees, 1, kappaMax, 1.0, catDomainSizes, rng, condParents, condParentVals);
                learnModel(model, instanceFeatureNames, instanceFeatures, true, par1CostFunc, instanceFeaturesIx, configurableParameters, solverConfigs);
                rep_rss += model.calculateOobRSS();
            }
            rep_rss /= numRep;
            System.out.println(nTrees + " " + rep_rss);
        }
        
        
        RInterface.shutdown();
    }
    
    
    private static void learnModel(CensoredRandomForest model, List<String> instanceFeatureNames, double[][] instanceFeatures, boolean logModel, CostFunction par1CostFunc, Map<Integer, Integer> instanceFeaturesIx, List<Parameter> configurableParameters, List<SolverConfiguration> generatedConfigs) throws Exception {
        double[][] theta = new double[generatedConfigs.size()][];
        Map<SolverConfiguration, Integer> solverConfigTheta = new HashMap<SolverConfiguration, Integer>();
        int countJobs = 0;
        int cIx = 0;
        for (SolverConfiguration config: generatedConfigs) {
            solverConfigTheta.put(config, cIx);
            theta[cIx] = paramConfigToTuple(config.getParameterConfiguration(), configurableParameters);
            countJobs += config.getFinishedJobs().size();
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
                y[jIx] = par1CostFunc.singleCost(run);
                if (logModel) {
                    if (y[jIx] <= 0) {
                        System.out.println("WARNING log model with values <= 0");
                        y[jIx] = 1e-15;
                    }
                    y[jIx] = Math.log10(y[jIx]);
                }
                jIx++;
            }
        }
        
        model.learnModel(theta, instanceFeatures, configurableParameters.size(), instanceFeatureNames.size(), theta_inst_idxs, y, censored);
    }
    
    
    private static double[] paramConfigToTuple(ParameterConfiguration paramConfig, List<Parameter> configurableParameters) {
        double[] theta = new double[configurableParameters.size()];
        for (Parameter p: configurableParameters) {
            int pIx = configurableParameters.indexOf(p);
            Object paramValue = paramConfig.getParameterValue(p);
            if (paramValue == null) theta[pIx] = Double.NaN;
            else {
                if (p.getDomain() instanceof RealDomain) {
                    theta[pIx] = (Double)paramValue;
                } else if (p.getDomain() instanceof IntegerDomain) {
                    theta[pIx] = (Integer)paramValue;
                } else if (p.getDomain() instanceof CategoricalDomain) {
                    // map categorical parameters to integers 1 through domain.size, 0 = not set
                    Map<String, Integer> valueMap = new HashMap<String, Integer>();
                    int intVal = 1;
                    List<String> sortedValues = new LinkedList<String>(((CategoricalDomain)p.getDomain()).getCategories());
                    Collections.sort(sortedValues);
                    for (String val: sortedValues) {
                        valueMap.put(val, intVal++);
                    }
                    
                    theta[pIx] = valueMap.get((String)paramValue);
                } else if (p.getDomain() instanceof OrdinalDomain) {
                    // map ordinal parameters to integers 1 through domain.size, 0 = not set
                    Map<String, Integer> valueMap = new HashMap<String, Integer>();
                    int intVal = 1;
                    for (String val: ((OrdinalDomain)p.getDomain()).getOrdered_list()) {
                        valueMap.put(val, intVal++);
                    }
                    
                    theta[pIx] = valueMap.get((String)paramValue);
                } else if (p.getDomain() instanceof FlagDomain) {
                    // map flag parameters to {0, 1}
                    if (FlagDomain.FLAGS.ON.equals(paramValue)) theta[pIx] = 1;
                    else theta[pIx] = 0;
                } else {
                    // TODO
                    theta[pIx] = paramValue.hashCode();
                }
            }
            
        }
        
        return theta;
    }
}
