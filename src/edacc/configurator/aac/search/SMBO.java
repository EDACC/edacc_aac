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
import org.rosuda.JRI.Rengine;

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
    
    private Rengine rengine;
    
    private int maxSamples = 100000;
    private SamplingSequence sequence;
    private double sequenceValues[][];
    
    private CensoredRandomForest model;
    private boolean logModel = true;
    
    private int numPC = 7;

    public SMBO(AAC pacc, API api, Random rng, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
        super(pacc, api, rng, parameters, firstSCs, referenceSCs);

        rengine = RInterface.getRengine();
        
        String samplingPath = "";
        String val;
        if ((val = parameters.getSearchMethodParameters().get("SMBO_samplingPath")) != null)
            samplingPath = val;
        if ((val = parameters.getSearchMethodParameters().get("SMBO_numPC")) != null)
            numPC = Integer.valueOf(val);;
        
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
                instanceFeatures[instances.indexOf(instance)][instanceFeatureNames.indexOf(featureName)] = 2; //TODO: featureValueByName.get(featureName);
            }
        }
        
        // Project instance features into lower dimensional space using PCA
        PCA pca = new PCA(rengine);
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
        model = new CensoredRandomForest(200, logModel ? 1 : 0, 5000.0, 1.0, catDomainSizes, rng);
        
        // Initialize pseudo-random sequence for the initial sampling
        sequence = new SamplingSequence(samplingPath);
        sequenceValues = sequence.getSequence(configurableParameters.size(), maxSamples);
    }

    @Override
    public List<SolverConfiguration> generateNewSC(int num) throws Exception {
        if (generatedConfigs.isEmpty()) {
            // start with some random configs
            for (int i = 0; i < 10 * configurableParameters.size(); i++) {
                ParameterConfiguration pc = mapRealTupleToParameters(sequenceValues[i]);
                int idSC = api.createSolverConfig(parameters.getIdExperiment(), pc, "SN: " + i);
                generatedConfigs.add(new SolverConfiguration(idSC, pc, parameters.getStatistics()));
            }
            
            /*for (int i = 0; i < num; i++) {
                ParameterConfiguration paramConfig = pspace.getRandomConfiguration(rng);
                int idSC = api.createSolverConfig(parameters.getIdExperiment(), paramConfig, api.getCanonicalName(parameters.getIdExperiment(), paramConfig));
                generatedConfigs.add(new SolverConfiguration(idSC, paramConfig, parameters.getStatistics()));
            }*/
            List<SolverConfiguration> newConfigs = new ArrayList<SolverConfiguration>(generatedConfigs);
            return newConfigs;
        } else {
            int numJobs = 0;
            for (SolverConfiguration config: generatedConfigs) numJobs += config.getNumFinishedJobs();
            long start = System.currentTimeMillis();
            updateModel();
            pacc.log("c Learning the model from " + generatedConfigs.size() + " configs and " + numJobs + " runs in total took " + (System.currentTimeMillis() - start) + " ms");
            
            List<SolverConfiguration> newConfigs = new ArrayList<SolverConfiguration>();
            if (pacc.racing instanceof FRace || pacc.racing instanceof SMFRace) {
                // FRace and SMFRace don't automatically use the old best configurations
                newConfigs.addAll(pacc.racing.getBestSolverConfigurations(num));
                Collections.sort(newConfigs);
            }
            
            double f_min = newConfigs.get(0).getCost();
            if (logModel) f_min = Math.log10(f_min);
            pacc.log("c Current best configuration: " + newConfigs.get(0).toString() + " with cost " + f_min);
            
            double[][] opt_theta = new double[][] {{1.23, 1.42}};
            double[][] opt_pred = model.predict(opt_theta);
            double opt_ei = (f_min - opt_pred[0][0]) * Gaussian.Phi((f_min - opt_pred[0][0]) / Math.sqrt(opt_pred[0][1])) + Math.sqrt(opt_pred[0][1]) * Gaussian.phi((f_min - opt_pred[0][0]) / Math.sqrt(opt_pred[0][1]));
            pacc.log("Prediction of optimum theta: mu=" + opt_pred[0][0] + " sigma=" + Math.sqrt(opt_pred[0][1]) + " EI: " + opt_ei);
            
            start = System.currentTimeMillis();
            int numRandomTheta = 10000;
            double[][] randomThetas = new double[numRandomTheta][];
            ParameterConfiguration[] randomParamConfigs = new ParameterConfiguration[numRandomTheta]; 
            for (int i = 0; i < numRandomTheta; i++) {
                ParameterConfiguration paramConfig = pspace.getRandomConfiguration(rng);
                randomThetas[i] = paramConfigToTuple(paramConfig);
                randomParamConfigs[i] = paramConfig;
            }
            
            double[][] randomThetasPred = model.predict(randomThetas);
            pacc.log("c Predicting " + numRandomTheta + " random copnfigurations took " + (System.currentTimeMillis() - start) + " ms");
            ThetaPrediction[] thetaPred = new ThetaPrediction[numRandomTheta];
            for (int i = 0; i < numRandomTheta; i++) {
                thetaPred[i] = new ThetaPrediction();
                thetaPred[i].mu = randomThetasPred[i][0];
                thetaPred[i].var = randomThetasPred[i][1];
                thetaPred[i].thetaIdx = i;
                
                double sigma = Math.sqrt(thetaPred[i].var);
                double mu = thetaPred[i].mu;
                double u = (f_min - mu) / sigma;
                thetaPred[i].ei = (f_min - mu) * Gaussian.Phi(u) + sigma * Gaussian.phi(u);
                
                thetaPred[i].t1 = (f_min - mu) * Gaussian.Phi(u);
                thetaPred[i].t2 = sigma * Gaussian.phi(u);
            }
            Arrays.sort(thetaPred, new Comparator<ThetaPrediction>() {
                @Override
                public int compare(final ThetaPrediction pred1, final ThetaPrediction pred2) {
                    return Double.compare(pred1.ei, pred2.ei);
                }
            });
            

            for (int i = 0; i < num - newConfigs.size(); i++) {
                ThetaPrediction theta = thetaPred[thetaPred.length - i - 1];
                ParameterConfiguration paramConfig = randomParamConfigs[theta.thetaIdx];
                pacc.log("c Using configuration " + api.getCanonicalName(parameters.getIdExperiment(), paramConfig) + " with predicted performance: " + theta.mu + " and sigma " + Math.sqrt(theta.var) + " and EI " + theta.ei + " t1: " + theta.t1 + " " + theta.t2);
                int idSC = api.createSolverConfig(parameters.getIdExperiment(), paramConfig, api.getCanonicalName(parameters.getIdExperiment(), paramConfig));
                newConfigs.add(new SolverConfiguration(idSC, paramConfig, parameters.getStatistics()));
            }
            
            generatedConfigs.addAll(newConfigs);
            return newConfigs;
        }
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
        p.add("% -----------------------\n");
        return p;
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
        double t1, t2;
        double mu, var, ei;
        int thetaIdx;
    }
}
