package edacc.configurator.models.rf;

import java.util.Random;

import edacc.configurator.models.rf.fastrf.RandomForest;
import edacc.configurator.models.rf.fastrf.RegtreeBuildParams;
import edacc.configurator.models.rf.fastrf.RegtreeFit;

public class CensoredRandomForest {
    RandomForest rf;
    Random rng;
    
    public CensoredRandomForest(int nTrees, int logModel, Random rng) {
        rf = new RandomForest(nTrees, logModel);
        this.rng = rng;
    }
    
    public void learnModel(double[][] theta, double[][] instance_features, int nVars,
            int[][] theta_inst_idxs, double[] y, boolean[] censored, int logModel) {
        RegtreeBuildParams params = new RegtreeBuildParams();
        params.ratioFeatures = 0.2;
        params.catDomainSizes = new int[nVars];
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
        }
        

    }
}
