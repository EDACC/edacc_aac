package edacc.configurator.models.rf;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ca.ubc.cs.beta.models.fastrf.RandomForest;
import ca.ubc.cs.beta.models.fastrf.RegtreeFit;
import ca.ubc.cs.beta.models.fastrf.RegtreeBuildParams;
import edacc.configurator.models.rf.fastrf.utils.Gaussian;
import edacc.configurator.models.rf.fastrf.utils.Utils;

public class CensoredRandomForest implements java.io.Serializable {
    private static final long serialVersionUID = 3243815546509104702L;

    public RandomForest rf;
    
    private Random rng;
    private double kappaMax;
    private double cutoffPenaltyFactor;
   // private int[] catDomainSizes;
    private double[][] instanceFeatures;
    
    private double[][] rf_theta;
    private double[] rf_y;
    private int[][] rf_theta_inst_idxs;
    private int rf_nVars;

    private final int numImputationIterations = 1;
    
    //private int[][] condParents = null;
    //private int[][][] condParentVals = null;
    private int[][] tree_oob_samples;
    
    public static boolean parallelizeInternally = false;
    public static int parallelizationProcs = 1;
    
    final RegtreeBuildParams params = new RegtreeBuildParams();
    
    /*public CensoredRandomForest(int nTrees, int logModel, double kappaMax, double cutoffPenaltyFactor, int[] catDomainSizes, Random rng) {
        this.rng = rng;
        this.kappaMax = kappaMax;
        this.cutoffPenaltyFactor = cutoffPenaltyFactor;
        this.catDomainSizes = catDomainSizes;

        params.ratioFeatures = 5.0 / 6.0;
        params.catDomainSizes = catDomainSizes;
        params.logModel = logModel;
        params.storeResponses = true;
        params.splitMin = 10;
        rf = new RandomForest(nTrees, params);
        
    }*/
    
    public CensoredRandomForest(int nTrees, int logModel, double kappaMax, double cutoffPenaltyFactor, int[] catDomainSizes, Random rng, int[][] condParents, int[][][] condParentVals) {
        this.rng = rng;
        this.kappaMax = kappaMax;
        this.cutoffPenaltyFactor = cutoffPenaltyFactor;
        //this.catDomainSizes = catDomainSizes;
        tree_oob_samples = new int[nTrees][];
        
        params.ratioFeatures = 5.0 / 6.0;
        params.catDomainSizes = catDomainSizes;
        params.logModel = logModel;
        params.storeResponses = false;
        params.splitMin = 10;
        params.condParents = condParents;
        params.condParentVals = condParentVals;
        
        rf = new RandomForest(nTrees, params);
    }
    
    public void learnModel(double[][] theta, double[][] instance_features, int nParams, int nFeatures,
            int[][] theta_inst_idxs, double[] y, boolean[] censored) throws Exception {
        int nVars = nParams + nFeatures;
        
        this.instanceFeatures = instance_features;
        
        // count number of censored observations
        int numCensored = 0;
        for (int i = 0; i < censored.length; i++) if (censored[i]) numCensored++;
        
        // non-censored subset of observations
        int[][] theta_inst_idxs_noncens = new int[y.length - numCensored][2];
        double[] y_noncens = new double[y.length - numCensored];
        boolean[] censored_noncens = new boolean[y.length - numCensored];
        int ix = 0;
        for (int i = 0; i < y.length; i++) {
            if (!censored[i]) {
                theta_inst_idxs_noncens[ix][0] = theta_inst_idxs[i][0];
                theta_inst_idxs_noncens[ix][1] = theta_inst_idxs[i][1];
                y_noncens[ix] = y[i];
                censored_noncens[ix] = false;
                ix++;
            }
        }
        // learn model on uncensored data
        internalLearnModel(theta, instance_features, nVars, theta_inst_idxs_noncens, y_noncens, censored_noncens, rf.logModel);
        if (numCensored > 0) {
            // censored subset of observations
            int[][] theta_inst_idxs_cens = new int[numCensored][2];
            double[] y_cens = new double[numCensored];
            boolean[] censored_cens = new boolean[numCensored];
            ix = 0;
            for (int i = 0; i < y.length; i++) {
                if (censored[i]) {
                    theta_inst_idxs_cens[ix][0] = theta_inst_idxs[i][0];
                    theta_inst_idxs_cens[ix][1] = theta_inst_idxs[i][1];
                    y_cens[ix] = y[i];
                    censored_cens[ix] = true;
                    ix++;
                }
            }
            
            double[][] X_cens = new double[numCensored][nVars];
            ix = 0;
            for (int i = 0; i < numCensored; i++) {
                for (int j = 0; j < nParams; j++) X_cens[i][j] = theta[theta_inst_idxs_cens[i][0]][j];
                for (int j = nParams; j < nParams + nFeatures; j++) X_cens[i][j] = instance_features[theta_inst_idxs_cens[i][1]][j - nParams];
            }
            
            double maxValue = kappaMax * cutoffPenaltyFactor;
            if (rf.logModel > 0) maxValue = Math.log10(maxValue);
            
            double[] oldMeanImputed = new double[numCensored];
            for (int i = 0; i < numCensored; i++) oldMeanImputed[i] = 0;
            
            for (int impIteration = 0; impIteration < numImputationIterations; impIteration++) {
                double[][] cens_pred = predict(X_cens);
                
                if (impIteration > 0) {
                    boolean oldMeanEqNew = true;
                    for (int i = 0; i < numCensored; i++) {
                        if (Math.abs(oldMeanImputed[i] - cens_pred[i][0]) > 1e-10) { oldMeanEqNew = false; break; }
                    }
                    if (oldMeanEqNew) break;
                }
                for (int i = 0; i < numCensored; i++) oldMeanImputed[i] = cens_pred[i][0];

                double[] mu = new double[numCensored];
                double[] sigma = new double[numCensored];
                double[] alpha = new double[numCensored];
                double[] single_y_hal = new double[numCensored];
                for (int i = 0; i < numCensored; i++) {
                    mu[i] = cens_pred[i][0];
                    sigma[i] = Math.sqrt(cens_pred[i][1]);
                    alpha[i] = (y_cens[i] - mu[i]) / sigma[i];
                    //System.out.println(y_cens[i] + " " + mu[i] + " " + sigma[i] + " " +  alpha[i] + " " + Gaussian.phi(alpha[i]) + " " + (1-Gaussian.Phi(alpha[i])));
                    double a = Gaussian.normcdf(alpha[i]);
                    double b = Gaussian.normcdf((Double.POSITIVE_INFINITY - mu[i]) / sigma[i]);
                    double sample = mu[i] + sigma[i] * Math.sqrt(2) * (2 * (a + (b - a) * rng.nextDouble() - 1)); 
                    single_y_hal[i] = Math.max(sample, y_cens[i]);
                    //System.out.println("Predicted " + mu[i] + " sigma " + sigma[i] + " a " + a + " b " + b + " Imputed " + y_cens[i] + " to " + single_y_hal[i]);
                    //single_y_hal[i] = Math.max(mu[i] + sigma[i] * Gaussian.phi(alpha[i]) / (1-Gaussian.Phi(alpha[i])), maxValue);
                }
                
                double[] imp_y = new double[y.length];
                ix = 0;
                for (int i = 0; i < y.length; i++) {
                    if (!censored[i]) imp_y[i] = y[i];
                    else imp_y[i] = single_y_hal[ix++];
                }
    
                internalLearnModel(theta, instance_features, nVars, theta_inst_idxs, imp_y, new boolean[y.length], rf.logModel);
            }
        }
    }
    
    protected void internalLearnModel(final double[][] theta, final double[][] instance_features, final int nVars,
            final int[][] theta_inst_idxs, final double[] y, final boolean[] censored, final int logModel) throws Exception {

        // Remember last RF build data
        rf_theta = theta;
        rf_theta_inst_idxs = theta_inst_idxs;
        rf_y = y;
        rf_nVars = nVars;

        for (int tr = 0; tr < rf.numTrees; tr++) {
            final int i = tr;

            int[] sample = new int[y.length];
            Set<Integer> sampleset = new HashSet<Integer>();
            // bootstrap sample y.length values for each tree with replacement
            for (int j = 0; j < sample.length; j++) {
                sample[j] = rng.nextInt(y.length);
                sampleset.add(sample[j]);
            }

            int[][] tree_theta_inst_idxs = new int[y.length][2];
            double[] tree_y = new double[y.length];
            for (int j = 0; j < sample.length; j++) {
                tree_theta_inst_idxs[j][0] = theta_inst_idxs[sample[j]][0];
                tree_theta_inst_idxs[j][1] = theta_inst_idxs[sample[j]][1];
                tree_y[j] = y[sample[j]];
            }

            int[] oob_samples = new int[y.length - sampleset.size()];
            int ix = 0;
            for (int j = 0; j < y.length; j++) {
                if (!sampleset.contains(j)) { // OOB sample
                    oob_samples[ix++] = j;
                }
            }

            rf.Trees[i] = RegtreeFit.fit(theta, instance_features, tree_theta_inst_idxs, tree_y, params);
            tree_oob_samples[i] = oob_samples;
        }

        System.gc();
    }
    
    public double[][] predict(double[][] theta_inst) {
        int[] tree_used_idxs = new int[rf.numTrees];
        for (int i = 0; i < rf.numTrees; i++) tree_used_idxs[i] = i;
        return RandomForest.applyMarginal(this.rf, tree_used_idxs, theta_inst, instanceFeatures);
    }
    
    public double[][] predictMarginal(double[][] theta_inst, int[] instance_idxs) {
        int[] tree_used_idxs = new int[rf.numTrees];
        for (int i = 0; i < rf.numTrees; i++) tree_used_idxs[i] = i;
        double[][] instance_features = new double[instance_idxs.length][instanceFeatures[0].length];
        for (int i = 0; i < instance_idxs.length; i++) {
            for (int j = 0; j < instanceFeatures[0].length; j++) instance_features[i][j] = instanceFeatures[instance_idxs[i]][j];
        }
        return RandomForest.applyMarginal(this.rf, tree_used_idxs, theta_inst, instance_features);
    }
    
    public double[][] predictDirect(final double[][] thetaX) {
        if (parallelizeInternally) {
            final int N = thetaX.length;
            final int D = thetaX[0].length;
            final int C = N / parallelizationProcs;
            final double[][] res = new double[N][2];
            ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            try {
                for (int j = 0; j < parallelizationProcs; j++) {
                    final int ix = j * C;
                    exec.submit(new Runnable() {
                        @Override
                        public void run() {
                            System.out.println("Thread " + Thread.currentThread().getId() + " computes " + ix + " to " + Math.min(ix + C, N));
                            double[][] myThetaX = new double[Math.min(ix + C, N) - ix + 1][];
                            for (int i = ix; i < Math.min(ix + C, N); i++) {
                                myThetaX[i - ix] = thetaX[ix];
                            }
                            double[][] myRes = RandomForest.apply(rf, myThetaX);
                            for (int i = ix; i < Math.min(ix + C, N); i++) {
                                res[i][0] = myRes[i - ix][0];
                                res[i][0] = myRes[i - ix][1];
                            }
                        }
                    });
                }
                exec.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace(); // TODO Auto-generated catch block
            } finally {
                exec.shutdown();
            }  
            return res;
        }
        return RandomForest.apply(rf, thetaX);
    }
    
    public double[] calculateVI() {
        double[] RSS_t = new double[rf.numTrees];
        double[][] RSS_v_t = new double[rf_nVars][rf.numTrees];
        
        for (int t = 0; t < rf.numTrees; t++) {
            int[] oob_samples = tree_oob_samples[t];
            double[] oob_y = new double[oob_samples.length];
            double[][] X = new double[oob_samples.length][rf_nVars];
            for (int i = 0; i < oob_samples.length; i++) {
                for (int j = 0; j < rf_theta[0].length; j++) X[i][j] = rf_theta[rf_theta_inst_idxs[oob_samples[i]][0]][j];
                for (int j = 0; j < instanceFeatures[0].length; j++) X[i][rf_theta[0].length + j] = instanceFeatures[rf_theta_inst_idxs[oob_samples[i]][1]][j];
                oob_y[i] = rf_y[oob_samples[i]];
            }
            
            // calculate out of bag error of the tree (residual sum of squares)
            double[][] oob_pred = RandomForest.apply(rf, X);
            RSS_t[t] = 0;
            for (int i = 0; i < oob_samples.length; i++) {
                //System.out.println(oob_y[i] + " " + oob_pred[i][0]);
                RSS_t[t] += (oob_y[i] - oob_pred[i][0]) * (oob_y[i] - oob_pred[i][0]);
            }
            
            for (int v = 0; v < rf_nVars; v++) {
                List<Integer> perm = new ArrayList<Integer>();
                for (int i = 0; i < oob_samples.length; i++) perm.add(i);
                Collections.shuffle(perm, rng);
                
                double[][] perm_X = new double[oob_samples.length][rf_nVars];
                for (int i = 0; i < oob_samples.length; i++) {
                    for (int j = 0; j < rf_theta[0].length; j++) perm_X[i][j] = rf_theta[rf_theta_inst_idxs[oob_samples[i]][0]][j];
                    for (int j = 0; j < instanceFeatures[0].length; j++) perm_X[i][rf_theta[0].length + j] = instanceFeatures[rf_theta_inst_idxs[oob_samples[i]][1]][j];
                    
                    perm_X[perm.get(i)][v] = X[i][v];
                    oob_y[i] = rf_y[oob_samples[i]];
                }
                
                double[][] oob_pred_v = RandomForest.apply(rf, perm_X);
                RSS_v_t[v][t] = 0;
                for (int i = 0; i < oob_samples.length; i++) {
                    RSS_v_t[v][t] += (oob_y[i] - oob_pred_v[i][0]) * (oob_y[i] - oob_pred_v[i][0]);
                }
            }
        }

        double[] VI = new double[rf_nVars];
        for (int v = 0; v < rf_nVars; v++) {
            VI[v] = 0;
            for (int t = 0; t < rf.numTrees; t++) {
                VI[v] += (RSS_v_t[v][t] - RSS_t[t]);
            }
            VI[v] = VI[v] * 1.0 / rf.numTrees;
        }
        return VI;
    }
    
    public double calculateOobRSS() {
        double[] RSS_t = new double[rf.numTrees];
        
        for (int t = 0; t < rf.numTrees; t++) {
            int[] oob_samples = tree_oob_samples[t];
            double[] oob_y = new double[oob_samples.length];
            double[][] X = new double[oob_samples.length][rf_nVars];
            for (int i = 0; i < oob_samples.length; i++) {
                for (int j = 0; j < rf_theta[0].length; j++) X[i][j] = rf_theta[rf_theta_inst_idxs[oob_samples[i]][0]][j];
                for (int j = 0; j < instanceFeatures[0].length; j++) X[i][rf_theta[0].length + j] = instanceFeatures[rf_theta_inst_idxs[oob_samples[i]][1]][j];
                oob_y[i] = rf_y[oob_samples[i]];
            }
            
            // calculate out of bag error of the tree (residual sum of squares)
            double[][] oob_pred = RandomForest.apply(rf, X);
            RSS_t[t] = 0;
            for (int i = 0; i < oob_samples.length; i++) {
                RSS_t[t] += (oob_y[i] - oob_pred[i][0]) * (oob_y[i] - oob_pred[i][0]);
            }
        }
        
        return Utils.mean(RSS_t);
    }
    
    public double calculateAvgOobRSS() {
        double[] RSS_t = new double[rf.numTrees];
        
        for (int t = 0; t < rf.numTrees; t++) {
            int[] oob_samples = tree_oob_samples[t];
            double[] oob_y = new double[oob_samples.length];
            double[][] X = new double[oob_samples.length][rf_nVars];
            for (int i = 0; i < oob_samples.length; i++) {
                for (int j = 0; j < rf_theta[0].length; j++) X[i][j] = rf_theta[rf_theta_inst_idxs[oob_samples[i]][0]][j];
                for (int j = 0; j < instanceFeatures[0].length; j++) X[i][rf_theta[0].length + j] = instanceFeatures[rf_theta_inst_idxs[oob_samples[i]][1]][j];
                oob_y[i] = rf_y[oob_samples[i]];
            }
            
            // calculate out of bag error of the tree (residual sum of squares)
            double[][] oob_pred = RandomForest.apply(rf, X);
            RSS_t[t] = 0;
            for (int i = 0; i < oob_samples.length; i++) {
                RSS_t[t] += (oob_y[i] - oob_pred[i][0]) * (oob_y[i] - oob_pred[i][0]);
            }
            RSS_t[t] /= oob_samples.length;
        }
        
        return Utils.mean(RSS_t);
    }
    
    public static void writeToFile(CensoredRandomForest rf, File f) throws IOException {
        OutputStream buffer = new BufferedOutputStream(new FileOutputStream(f));
        ObjectOutput output = new ObjectOutputStream(buffer);
        output.writeObject(rf);
        output.close();
    }
    
    public static CensoredRandomForest loadFromFile(File f) throws IOException, ClassNotFoundException {
        InputStream buffer = new BufferedInputStream(new FileInputStream(f));
        ObjectInput input = new ObjectInputStream(buffer);
        return (CensoredRandomForest)input.readObject();
    }
}
