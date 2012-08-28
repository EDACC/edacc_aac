package edacc.configurator.aac.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.math.MathException;
import org.apache.commons.math3.distribution.ExponentialDistribution;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.configurator.aac.racing.FRace;
import edacc.configurator.aac.racing.SMFRace;
import edacc.configurator.aac.util.RInterface;
import edacc.configurator.math.PCA;
import edacc.configurator.math.SamplingSequence;
import edacc.configurator.models.rf.CensoredRandomForest;
import edacc.configurator.models.rf.fastrf.utils.Gaussian;
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
    private Set<SolverConfiguration> generatedConfigs = new HashSet<SolverConfiguration>();
    private List<Parameter> configurableParameters = new ArrayList<Parameter>();
    private List<String> instanceFeatureNames = new LinkedList<String>();
    private List<Instance> instances;
    private Map<Integer, Integer> instanceFeaturesIx = new HashMap<Integer, Integer>();
    private double[][] instanceFeatures;
    
    private final int maxSamples = 100000;
    private SamplingSequence sequence;
    private double sequenceValues[][];
    
    private CensoredRandomForest model;
    
    // Configurable parameters
    private boolean logModel = false;
    private String selectionCriterion = "ocb"; // ei, ocb
    private int numPC = 7;
    private int numInitialConfigurationsFactor = 50; // how many samples per parameter initially
    private int numRandomTheta = 10000; // how many random theta to predict for EI/OCB optimization
    private int maxLocalSearchSteps = 10;
    private float lsStddev = 0.01f; // stddev used in LS sampling
    private int lsSamples = 10; // how many samples per parameter for the LS neighbourhood
    private int nTrees = 40;
    private double ocbExpMu = 1.0;
    private int EIg = 1; // global search factor {1,2,3}

    
    public SMBO(AAC pacc, API api, Random rng, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
        super(pacc, api, rng, parameters, firstSCs, referenceSCs);

        String samplingPath = "";
        String val;
        if ((val = parameters.getSearchMethodParameters().get("SMBO_samplingPath")) != null)
            samplingPath = val;
        if ((val = parameters.getSearchMethodParameters().get("SMBO_numPC")) != null)
            numPC = Integer.valueOf(val);
        // TODO: all configurable parameters
        
        pspace = api.loadParameterGraphFromDB(parameters.getIdExperiment());
        
        // TODO: Load from configuration?
        instanceFeatureNames.add("POSNEG-RATIO-CLAUSE-mean");
        
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
                instanceFeatures[instances.indexOf(instance)][instanceFeatureNames.indexOf(featureName)] = 2;// TODO featureValueByName.get(featureName);
            }
        }
        
        // Project instance features into lower dimensional space using PCA
        PCA pca = new PCA(RInterface.getRengine());
        instanceFeatures = pca.transform(instanceFeatures.length, instanceFeatureNames.size(), instanceFeatures, numPC);
        
        int numFeatures = instanceFeatureNames.size();
        instanceFeatureNames.clear();
        for (int i = 0; i < Math.min(numPC, numFeatures); i++) instanceFeatureNames.add("PC" + (i+1));
        
        // Load information about the parameter space
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
        
        // Initialize the predictive model
        model = new CensoredRandomForest(nTrees, logModel ? 1 : 0, 1e20, 1.0, catDomainSizes, rng);
        
        // Initialize pseudo-random sequence for the initial sampling
        sequence = new SamplingSequence(samplingPath);
        sequenceValues = sequence.getSequence(configurableParameters.size(), maxSamples);
    }

    @Override
    public List<SolverConfiguration> generateNewSC(int num) throws Exception {
        if (generatedConfigs.isEmpty()) {
            // start with some random configs
            for (int i = 0; i < numInitialConfigurationsFactor * configurableParameters.size(); i++) {
                ParameterConfiguration pc = mapRealTupleToParameters(sequenceValues[i]);
                int idSC = api.createSolverConfig(parameters.getIdExperiment(), pc, "SN: " + i);
                generatedConfigs.add(new SolverConfiguration(idSC, pc, parameters.getStatistics()));
            }
            return new ArrayList<SolverConfiguration>(generatedConfigs);
        } else {
            int numJobs = 0;
            for (SolverConfiguration config: generatedConfigs) numJobs += config.getNumFinishedJobs();
            long start = System.currentTimeMillis();
            updateModel();
            pacc.log("c Learning the model from " + generatedConfigs.size() + " configs and " + numJobs + " runs in total took " + (System.currentTimeMillis() - start) + " ms");
            
            List<SolverConfiguration> newConfigs = new ArrayList<SolverConfiguration>();
            List<SolverConfiguration> bestConfigs = new ArrayList<SolverConfiguration>();
            bestConfigs.addAll(pacc.racing.getBestSolverConfigurations(num));
            if (pacc.racing instanceof FRace || pacc.racing instanceof SMFRace) {
                // FRace and SMFRace don't automatically use the old best configurations
                newConfigs.addAll(pacc.racing.getBestSolverConfigurations(num));
            }
            Collections.sort(bestConfigs);
            int numConfigsToGenerate = (num - newConfigs.size());
            
            double f_min = bestConfigs.get(0).getCost();
            if (logModel) f_min = Math.log10(f_min);
            //double[][] inc_theta_pred = model.predict(new double[][] {paramConfigToTuple(bestConfigs.get(0).getParameterConfiguration())});
            //f_min = inc_theta_pred[0][0];
            pacc.log("c Current best configuration: " + bestConfigs.get(0).toString() + " with cost " + f_min);
            
            /*double[][] opt_theta = new double[][] {{1,1}};
            double[][] opt_pred = model.predict(opt_theta);
            double opt_ei = expExpectedImprovement(opt_pred[0][0], Math.sqrt(opt_pred[0][1]), f_min);
            pacc.log("Prediction of optimum theta: mu=" + opt_pred[0][0] + " sigma=" + Math.sqrt(opt_pred[0][1]) + " EI: " + opt_ei);
            */
            
            start = System.currentTimeMillis();
             
            // Generate random configurations
            double[][] randomThetas = new double[numRandomTheta][];
            ParameterConfiguration[] randomParamConfigs = new ParameterConfiguration[numRandomTheta]; 
            for (int i = 0; i < numRandomTheta; i++) {
                ParameterConfiguration paramConfig = pspace.getRandomConfiguration(rng);
                randomThetas[i] = paramConfigToTuple(paramConfig);
                randomParamConfigs[i] = paramConfig;
            }
            
            // Predict runtime of random configs and calculate improvement measures
            double[][] randomThetasPred = model.predict(randomThetas);
            pacc.log("c Predicting " + numRandomTheta + " random configurations took " + (System.currentTimeMillis() - start) + " ms");
            ThetaPrediction[] thetaPred = new ThetaPrediction[numRandomTheta];
            ExponentialDistribution expDist = new ExponentialDistribution(ocbExpMu);
            double[] ocb_lambda = expDist.sample(numConfigsToGenerate);
            for (int i = 0; i < numRandomTheta; i++) {
                thetaPred[i] = new ThetaPrediction();
                thetaPred[i].mu = randomThetasPred[i][0];
                thetaPred[i].var = randomThetasPred[i][1];
                thetaPred[i].thetaIdx = i;
                thetaPred[i].ocb = new double[numConfigsToGenerate];

                double sigma = Math.sqrt(thetaPred[i].var);
                double mu = thetaPred[i].mu;
                
                for (int j = 0; j < numConfigsToGenerate; j++) thetaPred[i].ocb[j] = -mu + ocb_lambda[j] * sigma;
                if (logModel) thetaPred[i].ei = expExpectedImprovement(mu, sigma, f_min);
                else thetaPred[i].ei = expectedImprovement(mu, sigma, f_min);
            }

            if ("ocb".equals(selectionCriterion)) {
                // Optimistic confidence bound
                for (int i = 0; i < numConfigsToGenerate; i++) {
                    final int j = i;
                    Arrays.sort(thetaPred, new Comparator<ThetaPrediction>() {
                        @Override
                        public int compare(final ThetaPrediction pred1, final ThetaPrediction pred2) {
                            return -Double.compare(pred1.ocb[j], pred2.ocb[j]); // sort in decreasing order
                        }
                    });
                    
                    int ix = 0;
                    ThetaPrediction theta = thetaPred[ix];
                    while (api.exists(parameters.getIdExperiment(), randomParamConfigs[theta.thetaIdx]) != 0 && ix < thetaPred.length) {
                        theta = thetaPred[++ix];
                    }
                    ParameterConfiguration paramConfig = randomParamConfigs[theta.thetaIdx];
                    pacc.log("c OCB: Selected configuration " + api.getCanonicalName(parameters.getIdExperiment(), paramConfig) + " with predicted performance: " + theta.mu + " and sigma " + Math.sqrt(theta.var) + " and OCB: " + theta.ocb[i] + " ocb_lambda: " + ocb_lambda[i]);
                    paramConfig = optimizeLocally(paramConfig, theta.ocb[j], ocb_lambda[j], f_min);
                    int idSC = api.createSolverConfig(parameters.getIdExperiment(), paramConfig, api.getCanonicalName(parameters.getIdExperiment(), paramConfig));
                    newConfigs.add(new SolverConfiguration(idSC, paramConfig, parameters.getStatistics()));
                }
            } else {
                // EI
                Arrays.sort(thetaPred, new Comparator<ThetaPrediction>() {
                    @Override
                    public int compare(final ThetaPrediction pred1, final ThetaPrediction pred2) {
                        return -Double.compare(pred1.ei, pred2.ei);
                    }
                });
                
                for (int i = 0; i < numConfigsToGenerate; i++) {
                    ThetaPrediction theta = thetaPred[i];
                    ParameterConfiguration paramConfig = randomParamConfigs[theta.thetaIdx];
                    pacc.log("c EI: Selected configuration " + api.getCanonicalName(parameters.getIdExperiment(), paramConfig) + " with predicted performance: " + theta.mu + " and sigma " + Math.sqrt(theta.var) + " and EI: " + theta.ei);
                    paramConfig = optimizeLocally(paramConfig, theta.ei, 0.0, f_min);
                    int idSC = api.createSolverConfig(parameters.getIdExperiment(), paramConfig, api.getCanonicalName(parameters.getIdExperiment(), paramConfig));
                    newConfigs.add(new SolverConfiguration(idSC, paramConfig, parameters.getStatistics()));
                }
            }
                       
            generatedConfigs.addAll(newConfigs);
            return newConfigs;
        }
    }
    
    private ParameterConfiguration optimizeLocally(ParameterConfiguration paramConfig, double startCriterionValue, double ocb_lambda, double f_min) throws Exception {
        ParameterConfiguration incumbent = paramConfig;
        int localSearchSteps = 0;
        double incCriterionValue = startCriterionValue;
        while (localSearchSteps++ < maxLocalSearchSteps) {
            List<ParameterConfiguration> nbrs = pspace.getGaussianNeighbourhood(incumbent, rng, lsStddev, lsSamples, true);
            double[][] nbrsTheta = new double[nbrs.size()][];
            for (int i = 0; i < nbrs.size(); i++) nbrsTheta[i] = paramConfigToTuple(nbrs.get(i));
            double[][] nbrsThetaPred = model.predict(nbrsTheta);
            
            int bestIx = -1;
            double bestIxValue = startCriterionValue;

            for (int i = 0; i < nbrs.size(); i++) {
                double sigma = Math.sqrt(nbrsThetaPred[i][1]);
                double mu = nbrsThetaPred[i][0];
                
                double criterionValue;
                if ("ocb".equals(selectionCriterion)) {
                    criterionValue = -mu + ocb_lambda * sigma;
                } else {
                    if (logModel) criterionValue = expExpectedImprovement(mu, sigma, f_min);
                    else criterionValue = expectedImprovement(mu, sigma, f_min);
                }
                
                if (criterionValue > bestIxValue) {
                    bestIx = i;
                    bestIxValue = criterionValue;
                    incCriterionValue = criterionValue;
                }
            }
            
            if (bestIx == -1) return incumbent; // probably local optimum 
            
            incumbent = nbrs.get(bestIx);
        }
        pacc.log("Local search improved configuration to " + api.getCanonicalName(parameters.getIdExperiment(), incumbent) + " with criterion value: " + incCriterionValue);
        return incumbent;
    }
    
    private void updateModel() throws Exception {
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
                if (logModel) {
                    if (y[jIx] <= 0) {
                        pacc.log_db("Warning: logarithmic model used with values <= 0. Pruning to 1e-15.");
                        pacc.log("Warning: logarithmic model used with values <= 0. Pruning to 1e-15.");
                        y[jIx] = 1e-15;
                    }
                    y[jIx] = Math.log10(y[jIx]);
                }
                jIx++;
            }
        }
        
        model.learnModel(theta, instanceFeatures, configurableParameters.size(), instanceFeatureNames.size(), theta_inst_idxs, y, censored);
    }

    @Override
    public List<String> getParameters() {
        List<String> p = new LinkedList<String>();
        p.add("% --- SMBO parameters ---");
        p.add("SMBO_samplingPath = <REQUIRED> % (Path to the external sequence generating program)");
        p.add("SMBO_numPC = "+this.numPC+ " % (How many principal components of the instance features to use)");
        p.add("SMBO_selectionCriterion = "+this.selectionCriterion+ " % (Improvement criterion {ocb, ei})");
        p.add("SMBO_numInitialConfigurationsFactor = "+this.numInitialConfigurationsFactor+ " % (How many configurations to sample randomly for the initial model)");
        p.add("SMBO_numRandomTheta = "+this.numRandomTheta+ " % (How many random configurations should be predicted for criterion optimization)");
        p.add("SMBO_maxLocalSearchSteps = "+this.maxLocalSearchSteps+ " % (Up to how many steps should each configuration be optimized by LS with the model)");
        p.add("SMBO_lsStddev = "+this.lsStddev+ " % (Standard deviation to use in LS neighbourhood sampling)");
        
        p.add("% -----------------------\n");
        return p;
    }

    @Override
    public void searchFinished() {
        // TODO Auto-generated method stub
        
    }

    private double expExpectedImprovement(double mu, double sigma, double f_min) {
        f_min = Math.log(10) * f_min;
        mu = Math.log(10) * mu;
        sigma = Math.log(10) * sigma;

        return Math.exp(f_min + normcdfln((f_min - mu) / sigma))
                - Math.exp(sigma * sigma / 2.0 + mu + normcdfln((f_min - mu) / sigma - sigma));
    }
    
    double normcdf(double x) {
        double b1 = 0.319381530;
        double b2 = -0.356563782;
        double b3 = 1.781477937;
        double b4 = -1.821255978;
        double b5 = 1.330274429;
        double p = 0.2316419;
        double c = 0.39894228;

        if (x >= 0.0) {
            double t = 1.0 / (1.0 + p * x);
            return (1.0 - c * Math.exp(-x * x / 2.0) * t * (t * (t * (t * (t * b5 + b4) + b3) + b2) + b1));
        } else {
            double t = 1.0 / (1.0 - p * x);
            return (c * Math.exp(-x * x / 2.0) * t * (t * (t * (t * (t * b5 + b4) + b3) + b2) + b1));
        }
    }
    
    double normcdfln(double x) {
        double y, z, pi = 3.14159265358979323846264338327950288419716939937510;
        if (x > -6.5) {
            return Math.log(normcdf(x));
        }
        z = Math.pow(x, -2);
        y = z
                * (-1 + z
                        * (5.0 / 2 + z
                                * (-37.0 / 3 + z * (353.0 / 4 + z * (-4081.0 / 5 + z * (55205.0 / 6 + z * -854197.0 / 7))))));
        return y - 0.5 * Math.log(2 * pi) - 0.5 * x * x - Math.log(-x);
    }

    private double expectedImprovement(double mu, double sigma, double f_min) throws MathException {
        double x = (f_min - mu) / sigma;
        double ei;
        if (EIg == 1) ei = (f_min - mu) * Gaussian.Phi(x) + sigma * Gaussian.phi(x);
        else if (EIg == 2) ei = sigma*sigma * ((x*x + 1) * Gaussian.Phi(x) + x * Gaussian.phi(x));
        else if (EIg == 3) ei = sigma*sigma*sigma * ((x*x*x + 3*x) * Gaussian.Phi(x) + (2 + x*x) * Gaussian.phi(x));
        else ei = 0;
        
        return ei;
    }
    
    private double[] paramConfigToTuple(ParameterConfiguration paramConfig) {
        double[] theta = new double[configurableParameters.size()];
        for (Parameter p: configurableParameters) {
            int pIx = configurableParameters.indexOf(p);
            Object paramValue = paramConfig.getParameterValue(p);
            if (p.getDomain() instanceof RealDomain) {
                if (paramValue == null) theta[pIx] = ((RealDomain)p.getDomain()).getLow(); // TODO: handle missing values
                else theta[pIx] = (Double)paramValue;
            } else if (p.getDomain() instanceof IntegerDomain) {
                if (paramValue == null) theta[pIx] = ((IntegerDomain)p.getDomain()).getLow() - 1; // TODO: handle missing values
                else theta[pIx] = (Long)paramValue;
            } else if (p.getDomain() instanceof CategoricalDomain) {
                // map categorical parameters to integers 1 through domain.size, 0 = not set
                Map<String, Integer> valueMap = new HashMap<String, Integer>();
                int intVal = 1;
                for (String val: ((CategoricalDomain)p.getDomain()).getCategories()) {
                    valueMap.put(val, intVal++);
                }
                valueMap.put(null, 0);
                
                theta[pIx] = valueMap.get((String)paramValue);
            } else if (p.getDomain() instanceof OrdinalDomain) {
                // map ordinal parameters to integers 1 through domain.size, 0 = not set
                Map<String, Integer> valueMap = new HashMap<String, Integer>();
                int intVal = 1;
                for (String val: ((OrdinalDomain)p.getDomain()).getOrdered_list()) {
                    valueMap.put(val, intVal++);
                }
                valueMap.put(null, 0);
                
                theta[pIx] = valueMap.get((String)paramValue);
            } else if (p.getDomain() instanceof FlagDomain) {
                // map flag parametes to {0, 1}
                if (FlagDomain.FLAGS.ON.equals(paramValue)) theta[pIx] = 1;
                else theta[pIx] = 0;
            } else {
                // TODO
                if (paramValue == null) theta[pIx] = 0;
                else theta[pIx] = paramValue.hashCode();
            }
        }
        
        return theta;
    }
    
    /**
     * Map a real tuple to a parameter configuration. Not the inverse of paramConfigToTuple !!
     * @param values
     * @return
     */
    private ParameterConfiguration mapRealTupleToParameters(double[] values) {
        ParameterConfiguration pc = pspace.getRandomConfiguration(rng);
        int i = 0;
        for (Parameter p: configurableParameters) {
            if (pc.getParameterValue(p) == null) continue;
            double v = values[i++];
            if (p.getDomain() instanceof RealDomain) {
                RealDomain dom = (RealDomain)p.getDomain();
                pc.setParameterValue(p, dom.getLow() + v * (dom.getHigh() - dom.getLow()));
            } else if (p.getDomain() instanceof IntegerDomain) {
                IntegerDomain dom = (IntegerDomain)p.getDomain();
                pc.setParameterValue(p, Math.round(dom.getLow() + v * (dom.getHigh() - dom.getLow())));
            } else if (p.getDomain() instanceof CategoricalDomain) {
                CategoricalDomain dom = (CategoricalDomain)p.getDomain();
                List<String> categories = new LinkedList<String>(dom.getCategories());
                Collections.sort(categories);
                int ix = (int) (v * categories.size());
                if (ix == categories.size()) ix = 0;
                pc.setParameterValue(p, categories.get(ix));
            } else if (p.getDomain() instanceof OrdinalDomain) {
                OrdinalDomain dom = (OrdinalDomain)p.getDomain();
                int ix = (int) (v * dom.getOrdered_list().size());
                if (ix == dom.getOrdered_list().size()) ix = 0;
                pc.setParameterValue(p, dom.getOrdered_list().get(ix));
            } else if (p.getDomain() instanceof FlagDomain) {
                if (v < 0.5) {
                    pc.setParameterValue(p, FlagDomain.FLAGS.ON);
                } else {
                    pc.setParameterValue(p, FlagDomain.FLAGS.OFF);
                }
            }
        }
        return pc;
    }

    class ThetaPrediction {
        double mu, var, ei;
        int thetaIdx;
        double[] ocb;
    }
}
