package edacc.configurator.models.rf;

import java.util.Random;

import edacc.configurator.models.rf.fastrf.RandomForest;
import edacc.configurator.models.rf.fastrf.RegtreeBuildParams;
import edacc.configurator.models.rf.fastrf.RegtreeFit;
import edacc.configurator.models.rf.fastrf.utils.Gaussian;

public class CensoredRandomForest {
    public RandomForest rf;
    
    private Random rng;
    private double kappaMax;
    private double cutoffPenaltyFactor;
    private int[] catDomainSizes;
    private double[][] instanceFeatures;

    private int numImputationIterations = 5;
    
    public CensoredRandomForest(int nTrees, int logModel, double kappaMax, double cutoffPenaltyFactor, int[] catDomainSizes, Random rng) {
        rf = new RandomForest(nTrees, logModel);
        this.rng = rng;
        this.kappaMax = kappaMax;
        this.cutoffPenaltyFactor = cutoffPenaltyFactor;
        this.catDomainSizes = catDomainSizes;
    }
    
    public void learnModel(double[][] theta, double[][] instance_features, int nParams, int nFeatures,
            int[][] theta_inst_idxs, double[] y, boolean[] censored, int logModel) {
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
        internalLearnModel(theta, instance_features, nVars, theta_inst_idxs_noncens, y_noncens, censored_noncens, logModel);
        
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
            double[] oldMeanImputed = new double[numCensored];
            for (int impIteration = 0; impIteration < numImputationIterations; impIteration++) {
                double[][] cens_pred = predict(X_cens);
                
                if (impIteration == 0) {
                    for (int i = 0; i < numCensored; i++) oldMeanImputed[i] = cens_pred[i][0];
                }
                
                double[] mu = new double[numCensored];
                double[] sigma = new double[numCensored];
                double[] alpha = new double[numCensored];
                double[] single_y_hal = new double[numCensored];
                for (int i = 0; i < numCensored; i++) {
                    mu[i] = cens_pred[i][0];
                    sigma[i] = Math.sqrt(cens_pred[i][1]);
                    alpha[i] = (y_cens[i] - mu[i]) / sigma[i];
                    single_y_hal[i] = Math.min(mu[i] + sigma[i] * Gaussian.phi(alpha[i]) / (1-Gaussian.PhiInverse(alpha[i])), maxValue);
                }
                
                double[] imp_y = new double[y.length];
                ix = 0;
                for (int i = 0; i < y.length; i++) {
                    if (!censored[i]) imp_y[i] = y[i];
                    else imp_y[i] = single_y_hal[ix++];
                }
    
                internalLearnModel(theta, instance_features, nVars, theta_inst_idxs, imp_y, new boolean[censored.length], logModel);
            }
        }
    }
    
    protected void internalLearnModel(double[][] theta, double[][] instance_features, int nVars,
            int[][] theta_inst_idxs, double[] y, boolean[] censored, int logModel) {
        RegtreeBuildParams params = new RegtreeBuildParams();
        params.ratioFeatures = 1;
        params.catDomainSizes = catDomainSizes;
        params.logModel = logModel;
        params.storeResponses = true;
        params.splitMin = 10;

        for (int i = 0; i < rf.numTrees; i++) {
            int[] sample = new int[y.length];
            for (int j = 0; j < sample.length; j++) sample[j] = rng.nextInt(y.length);
            
            int[][] tree_theta_inst_idxs = new int[y.length][2];
            double[] tree_y = new double[y.length];
            for (int j = 0; j < sample.length; j++) {
                tree_theta_inst_idxs[j][0] = theta_inst_idxs[sample[j]][0];
                tree_theta_inst_idxs[j][1] = theta_inst_idxs[sample[j]][1];
                tree_y[j] = y[sample[j]];
            }
            
            rf.Trees[i] = RegtreeFit.fit(theta, instance_features, tree_theta_inst_idxs, tree_y, params);
            System.gc();
        }
    }
    
    public double[][] predict(double[][] theta_inst) {
        int[] tree_used_idxs = new int[rf.numTrees];
        for (int i = 0; i < rf.numTrees; i++) tree_used_idxs[i] = i;
        return RandomForest.applyMarginal(this.rf, tree_used_idxs, theta_inst, instanceFeatures);
    }
}
