package edacc.configurator.models;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.rosuda.JRI.Rengine;

import edacc.api.API;
import edacc.api.APIImpl;
import edacc.api.costfunctions.CostFunction;
import edacc.api.costfunctions.PARX;
import edacc.configurator.aac.SolverConfiguration;
import edacc.configurator.aac.StatisticFunction;
import edacc.configurator.aac.util.RInterface;
import edacc.configurator.math.SamplingSequence;
import edacc.configurator.models.rf.RandomForest;
import edacc.model.Experiment;
import edacc.model.ExperimentDAO;
import edacc.model.ExperimentResult;
import edacc.model.ExperimentResultDAO;
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

public class ExperimentFANOVA {    
    public static void main(String ... args) throws Exception {
        Options options = new Options();
        options.addOption("host", true, "DB hostname");
        options.addOption("port", true, "DB port");
        options.addOption("database", true, "DB name");
        options.addOption("user", true, "DB username");
        options.addOption("password", true, "DB password");
        options.addOption("experimentid", true, "ID of the experiment to analyse");
        options.addOption("cpulimit", true, "CPU time limit used in the experiment");
        options.addOption("wallclocklimit", true, "Wall clock time limit used in the experiment");
        options.addOption("mcsamples", true, "Number of samples in the Monte Carlo estimation");
        options.addOption("featurefolder", true, "folder for instance properties (expects to find features.properties)");
        options.addOption("featurecachefolder", true, "folder for cached instance properties");
        options.addOption("seed", true, "random seed");
        options.addOption("samplingpath", true, "path to quasi-random sequence sampling program (see AAC contrib folder)");
        options.addOption("ntrees", true, "number of trees in the random forest model");
        options.addOption("calculaterfvi", false, "Whether to calculate the random forest variable importance");
        options.addOption("averageparamperf", true, "Name of the parameter of which to estimate the average performance");

        CommandLineParser parser = new PosixParser();
        String hostname = null;
        Integer port = 3306;
        String database = null;
        String user = null; 
        String password = null; 
        Integer idExperiment = null;
        Integer CPUlimit = 100000000;
        Integer wallLimit = 100000000;
        Integer mcSamples = 1000;
        String featureFolder = null;
        String featureCacheFolder = null;
        Integer seed = 1;
        String samplingPath = null;
        Integer nTrees = 40;
        Boolean calculateRFVI = false;
        String averageParamPerf = null;
        Integer nConfigsForAverage = 10000; // TODO
        try {
            CommandLine cmd = parser.parse(options, args);
            
            if (cmd.hasOption("host")) hostname = cmd.getOptionValue("host");
            if (cmd.hasOption("port")) port = Integer.valueOf(cmd.getOptionValue("port"));
            if (cmd.hasOption("database")) database = cmd.getOptionValue("database");
            if (cmd.hasOption("user")) user = cmd.getOptionValue("user");
            if (cmd.hasOption("password")) password = cmd.getOptionValue("password");
            if (cmd.hasOption("experimentid")) idExperiment = Integer.valueOf(cmd.getOptionValue("experimentid"));
            if (cmd.hasOption("cpulimit")) CPUlimit = Integer.valueOf(cmd.getOptionValue("cpulimit"));
            if (cmd.hasOption("wallclocklimit")) wallLimit = Integer.valueOf(cmd.getOptionValue("wallclocklimit"));
            if (cmd.hasOption("mcsamples")) mcSamples = Integer.valueOf(cmd.getOptionValue("mcsamples"));
            if (cmd.hasOption("featurefolder")) featureFolder = cmd.getOptionValue("featurefolder");
            if (cmd.hasOption("featurecachefolder")) featureCacheFolder = cmd.getOptionValue("featurecachefolder");
            if (cmd.hasOption("seed")) seed = Integer.valueOf(cmd.getOptionValue("seed"));
            if (cmd.hasOption("samplingpath")) samplingPath = cmd.getOptionValue("samplingpath");
            if (cmd.hasOption("ntrees")) nTrees = Integer.valueOf(cmd.getOptionValue("ntrees"));
            if (cmd.hasOption("calculaterfvi")) calculateRFVI = cmd.hasOption("calculaterfvi");
            if (cmd.hasOption("averageparamperf")) averageParamPerf = cmd.getOptionValue("averageparamperf"); 
        } catch (ParseException e) {
            System.out.println( "Parsing error: " + e.getMessage() + "\n");
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "FANOVA", options );
        }


        System.out.println("---- EDACC FANOVA-analysis of experiment results ----");
        API api = new APIImpl();
        try {
            api.connect(hostname, port, database, user, password);
        } catch (Exception e) {
            System.out.println("Could not connect to database");
            return;
        }
        Random rng = new edacc.util.MersenneTwister(seed);
        System.out.println("Connection to database established.");

        ParameterGraph pspace = api.loadParameterGraphFromDB(idExperiment);
        
        // Make a par1 helper cost function
        CostFunction par1CostFunc;
        if (ExperimentDAO.getById(idExperiment).getDefaultCost().equals(Cost.resultTime)) {
            par1CostFunc = new PARX(Experiment.Cost.resultTime, true, 1.0f);
        } else if (ExperimentDAO.getById(idExperiment).getDefaultCost().equals(Cost.wallTime)) {
            par1CostFunc = new PARX(Experiment.Cost.wallTime, true, 1.0f);
        } else {
            par1CostFunc = new PARX(Experiment.Cost.cost, true, 1.0f);
        }

        System.out.println("Loading configurations ...");
        List<SolverConfiguration> solverConfigs = new LinkedList<SolverConfiguration>();
        Map<Integer, SolverConfiguration> scById = new HashMap<Integer, SolverConfiguration>();
        for (edacc.model.SolverConfiguration sc: SolverConfigurationDAO.getSolverConfigurationByExperimentId(idExperiment)) {
            SolverConfiguration csc = new SolverConfiguration(sc.getId(), api.getParameterConfiguration(idExperiment, sc.getId()), new StatisticFunction(par1CostFunc, true));
            solverConfigs.add(csc);
            scById.put(csc.getIdSolverConfiguration(), csc);
        }
        System.out.println("Loaded configurations.");

        int countJobs = 0;
        for (ExperimentResult run: ExperimentResultDAO.getAllByExperimentId(idExperiment)) {
            scById.get(run.getSolverConfigId()).putJob(run);
            countJobs++;
        }
        System.out.println("Loaded " + countJobs + " runs.");
        
        // ===== Learn the random forest =====
        System.out.println("Learning random forest from data ...");
        long start = System.currentTimeMillis();
        RandomForest model = new RandomForest(api, idExperiment, true, nTrees, rng, CPUlimit, wallLimit, new LinkedList<String>(), featureFolder, featureCacheFolder, false);
        model.learnModel(solverConfigs);
        System.out.println("Learning the model took " + (System.currentTimeMillis() - start) / 1000.0f + " seconds");
        
        System.out.println("Calculating average performance for parameter " + averageParamPerf);
        for (Parameter p: model.getConfigurableParameters()) {
            if (p.getName().equals(averageParamPerf)) {
                for (Object v: p.getDomain().getUniformDistributedValues(100)) {
                    List<ParameterConfiguration> pconfigs = new LinkedList<ParameterConfiguration>();
                    for (int i = 0; i < nConfigsForAverage; i++) {
                        ParameterConfiguration config = pspace.getRandomConfiguration(rng); // TODO: base on quasi random sequences
                        config.setParameterValue(p, v); // fix parameter p
                        pconfigs.add(config);
                    }
                    double[][] preds = model.predict(pconfigs);
                    double avg = 0;
                    for (int i = 0; i < preds.length; i++) {
                        avg += preds[i][0];
                    }
                    avg /= preds.length;
                    
                    System.out.println(v + ": " + avg);
                }
            }
        }
        
        
        double[][] instanceFeaturesSizes = new double[model.getInstanceFeatureNames().size()][2];
        for (int j = 0; j < model.getInstanceFeatureNames().size(); j++) {
            double minValue = Double.POSITIVE_INFINITY;
            double maxValue = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < model.getInstanceFeatures().length; i++) {
                minValue = Math.min(minValue, model.getInstanceFeatures()[i][j]);
                maxValue = Math.max(maxValue, model.getInstanceFeatures()[i][j]);
            }
            
            instanceFeaturesSizes[j][0] = minValue;
            instanceFeaturesSizes[j][1] = maxValue;
            System.out.println("Instance feature " + model.getInstanceFeatureNames().get(j) + " ranges from " + minValue + " to " + maxValue);
        }
        
        // ===== Start Monte Carlo estimation =====
        int d = model.getConfigurableParameters().size() + model.getInstanceFeatureNames().size();
        SamplingSequence sequence = new SamplingSequence(samplingPath);
        double[][] sequenceValues = sequence.getSequence(d, (2+d)*mcSamples * 2);
        
        Rengine rengine = RInterface.getRengine();
        if (rengine.eval("library(sensitivity)") == null) {
            rengine.end();
            throw new Exception("Did not find R library sensitivity (try running install.packages(\"sensitivity\")).");
        }
        
        System.out.println("Number of model input variables is: " + d);
        double[] linearizedData = new double[mcSamples * d];
        int ix = 0;
        for (int i = 0; i < mcSamples; i++) {
            for (int j = 0; j < d; j++) {
                linearizedData[ix++] = sequenceValues[i][j]; 
            }
        }
        rengine.assign("X1", linearizedData);
        double[] linearizedData2 = new double[mcSamples * d];
        ix = 0;
        for (int i = mcSamples; i < 2 * mcSamples; i++) {
            for (int j = 0; j < d; j++) {
                linearizedData2[ix++] = sequenceValues[i][j]; 
            }
        }
        rengine.assign("X2", linearizedData2);
        
        rengine.eval("X1 <- data.frame(matrix(X1, nrow=" + mcSamples + ", ncol=" + d + ", byrow=T))");
        rengine.eval("X2 <- data.frame(matrix(X2, nrow=" + mcSamples + ", ncol=" + d + ", byrow=T))");
        rengine.eval("set.seed(1)");
        System.out.println("Calculating monte-carlo sample positions...");
        rengine.eval("x <- sobol(X1=X1, X2=X2, nboot=100, order=2)");
        double[][] MC_X = rengine.eval("as.matrix(x$X)").asDoubleMatrix();
        double[][] X = new double[MC_X.length][MC_X[0].length];
        
        // ===== Predict samples requested for MC estimation =====
        System.out.println("Predicting " + X.length + " samples");
        start = System.currentTimeMillis();
        System.out.println("generating ...");
        for (int i = 0; i < MC_X.length; i++) {
            double[] theta = model.paramConfigToTuple(mapRealTupleToParameters(pspace, rng, model.getConfigurableParameters(), MC_X[i]));
            double[] features = new double[model.getInstanceFeatureNames().size()];
            for (int j = 0; j < model.getInstanceFeatureNames().size(); j++) {
                features[j] = MC_X[i][model.getConfigurableParameters().size() + j];
            }
            features = mapRealTupleToInstanceFeatures(features, instanceFeaturesSizes);
            
            for (int j = 0; j < theta.length; j++) {
                X[i][j] = theta[j];
            }
            for (int j = 0; j < features.length; j++) {
                X[i][theta.length + j] = features[j];
            }
        }

        System.out.println("predicting ...");
        double[][] preds = model.predictDirect(X);
        System.out.println("Predicting " + preds.length + " samples took " + (System.currentTimeMillis() - start) / 1000.0 + " seconds");
        
        double[] ys = new double[preds.length];
        for (int i = 0; i < preds.length; i++) {
            ys[i] = preds[i][0];
            //System.out.println(ys[i]);
        }
        rengine.assign("y", ys);
        System.out.println("Calculating Sobol indices");
        start = System.currentTimeMillis();
        rengine.eval("tell(x, y)");
        System.out.println("Calculation took " + (System.currentTimeMillis() - start) / 1000.0f + " seconds");
        
        // ===== Write R file =====
        System.out.println("Writing effects.R file ...");
        FileOutputStream fos = new FileOutputStream("effects.R");
        OutputStreamWriter out = new OutputStreamWriter(fos, "UTF-8"); 
        BufferedWriter writer = new BufferedWriter(out);
        writer.write("library(sensitivity)\n");
        writer.write("X1 = c(");
        for (int i = 0; i < linearizedData.length; i++) {
            writer.write(String.valueOf(linearizedData[i]));
            if (i < linearizedData.length - 1) writer.write(",");
        }
        writer.write(")\n");
        writer.write("X2 = c(");
        for (int i = 0; i < linearizedData2.length; i++) {
            writer.write(String.valueOf(linearizedData2[i]));
            if (i < linearizedData2.length - 1) writer.write(",");
        }
        writer.write(")\n");
        
        writer.write("X1 <- data.frame(matrix(X1, nrow=" + mcSamples + ", ncol=" + d + ", byrow=T))\n");
        writer.write("X2 <- data.frame(matrix(X2, nrow=" + mcSamples + ", ncol=" + d + ", byrow=T))\n");
        writer.write("colnames(X1) = c(");
        for (int i = 0; i < model.getConfigurableParameters().size(); i++) {
            writer.write("\"" + model.getConfigurableParameters().get(i).getName() + "\",");
        }
        for (int i = 0; i < model.getInstanceFeatureNames().size(); i++) {
            writer.write("\"" + model.getInstanceFeatureNames().get(i) + "\"");
            if (i < model.getInstanceFeatureNames().size() - 1) writer.write(",");
        }
        writer.write(")\n");
        writer.write("colnames(X2) = c(");
        for (int i = 0; i < model.getConfigurableParameters().size(); i++) {
            writer.write("\"" + model.getConfigurableParameters().get(i).getName() + "\",");
        }
        for (int i = 0; i < model.getInstanceFeatureNames().size(); i++) {
            writer.write("\"" + model.getInstanceFeatureNames().get(i) + "\"");
            if (i < model.getInstanceFeatureNames().size() - 1) writer.write(",");
        }
        writer.write(")\n");
        
        writer.write("y = c(");
        for (int i = 0; i < ys.length; i++) {
            writer.write(String.valueOf(ys[i]));
            if (i < ys.length - 1) writer.write(",");
        }
        writer.write(")\n");
        
        writer.write("set.seed(1)\n");
        writer.write("x <- sobol(X1=X1, X2=X2, nboot=100, order=2)\n");
        writer.write("tell(x, y)\n");
        
        writer.write("topTotalEffects = x$T[with(x$T, order(-x$T[,1])),][1:7,]\n");
        writer.write("par(mar=c(4,12,1,1))\n");
        writer.write("barplot(topTotalEffects[,1], names.arg=rownames(topTotalEffects), horiz=T, las=2)\n");
        writer.close();
        
        if (calculateRFVI) {
            System.out.println("Calculating RF permutation variable importance");
            start = System.currentTimeMillis();
            double[] VI = model.getVI();
            System.out.println(".. took " + (System.currentTimeMillis() - start) / 1000.0f + " seconds");
            for (int i = 0; i < model.getConfigurableParameters().size(); i++) {
                System.out.println(model.getConfigurableParameters().get(i).getName() + ": " + VI[i]);
            }
            for (int i = 0; i < model.getInstanceFeatureNames().size(); i++) {
                System.out.println(model.getInstanceFeatureNames().get(i) + ": " + VI[model.getConfigurableParameters().size() + i]);
            }
        }
                
        RInterface.shutdown();
    }
    
    /**
     * Map a real tuple to a parameter configuration. Not the inverse of paramConfigToTuple !!
     * @param values
     * @return
     */
    private static ParameterConfiguration mapRealTupleToParameters(ParameterGraph pspace, Random rng, List<Parameter> configurableParameters, double[] values) {
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
    
    /**
     * Map a real tuple to instance features tuple
     * @param values
     * @return
     */
    private static double[] mapRealTupleToInstanceFeatures(double[] values, double[][] instanceFeaturesRange) {
        double[] features = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            features[i] = instanceFeaturesRange[i][0] + (instanceFeaturesRange[i][1] - instanceFeaturesRange[i][0]) * values[i];
        }
        return features;
    }
}
