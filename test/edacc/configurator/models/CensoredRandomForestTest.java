package edacc.configurator.models;

import java.util.Random;

import edacc.configurator.models.rf.CensoredRandomForest;

public class CensoredRandomForestTest {
    public static void main(String ... args) {
        CensoredRandomForest rf = new CensoredRandomForest(10, 0, new Random());
        
        double[][] theta = new double[][] { {2, 2}, {1,1}, {1,2}, {3,3}, {2.1, 2.2}};
        double[][] inst = new double[][] {{1,}};
        int[][] theta_inst_idxs = new int[][] {{0, 0}, {1, 0}, {2, 0}, {3, 0}, {4, 0}};
        double[] y = new double[] {1, 10, 7, 10, 1.4};
        boolean[] cens = new boolean[y.length];
        
        rf.learnModel(theta, inst, 3, theta_inst_idxs, y, cens, 0);
        
        double[][] pred = rf.predict(new double[][] {{2, 2, 1}});
        System.out.println(pred[0][0] + ", " + pred[0][1]);
    }
}
