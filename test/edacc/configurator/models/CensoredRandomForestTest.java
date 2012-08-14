package edacc.configurator.models;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;

import edacc.configurator.models.rf.CensoredRandomForest;

public class CensoredRandomForestTest {
    public static void main(String ... args) throws Exception {
        

        /*double[][] theta = new double[][] { {2, 2}, {1,1}, {1,2}, {3,3}, {2.1, 2.2}};
        double[][] inst = new double[][] {{1,}};
        int[][] theta_inst_idxs = new int[][] {{0, 0}, {1, 0}, {2, 0}, {3, 0}, {4, 0}};
        double[] y = new double[] {1, 10, 7, 10, 1.4};
        boolean[] cens = new boolean[] {false, true, false, false, false};
        
        rf.learnModel(theta, inst, 2, 1, theta_inst_idxs, y, cens, 0);
        
        double[][] pred = rf.predict(new double[][] {{1, 1, 1}, {25,2,1}, {3,3,1}});
        for (int i = 0; i < pred.length; i++) {
            System.out.println(pred[i][0] + ", " + pred[i][1]);
        }*/
        
        
        int N = 9600;
        File file = new File("/home/daniel/download/BBO_configuration_runs.csv");
        BufferedReader bufRdr  = new BufferedReader(new FileReader(file));
        bufRdr.readLine(); // read header
        
        int nParams = 2;
        int nFeatures = 8;
        
        int[] catDomainSizes = new int[nParams+nFeatures];
        
        
        
        double data[][] = new double[N][nParams+nFeatures+1];
        
        double[] y = new double[N];
        boolean[] cens = new boolean[N];
        
        String line = null;
        int n = 0;
        while ((line = bufRdr.readLine()) != null) {
            if (n >= N) break;
            StringTokenizer st = new StringTokenizer(line, ",");
            for (int i = 0; i < nParams+nFeatures+1; i++) data[n][i] = Double.valueOf(st.nextToken());
            y[n] = data[n][nParams+nFeatures];
            cens[n] = Integer.valueOf(st.nextToken()) == 1;
            n++;
        }
        
        
        Set<List<Double>> thetas = new HashSet<List<Double>>();
        for (int i = 0; i < N; i++) {
            List<Double> theta = new LinkedList<Double>();
            for (int j = 0; j < nParams; j++) theta.add(data[i][j]);
            thetas.add(theta);
        }
        
        Set<List<Double>> xs = new HashSet<List<Double>>();
        for (int i = 0; i < N; i++) {
            List<Double> x = new LinkedList<Double>();
            for (int j = nParams; j < nParams+nFeatures; j++) {
                x.add(data[i][j]);
            }
            xs.add(x);
        }
        
        System.out.println("Unique configurations: " + thetas.size());
        System.out.println("Unique instances: " + xs.size());
        
        double[][] all_theta = new double[thetas.size()][nParams];
        int ix = 0;
        for (List<Double> theta: thetas) {
            for (int j = 0; j < theta.size(); j++) all_theta[ix][j] = theta.get(j);
            ix++;
        }
        
        double[][] all_x = new double[xs.size()][nFeatures];
        ix = 0;
        for (List<Double> x: xs) {
            for (int j = 0; j < x.size(); j++) all_x[ix][j] = x.get(j);
            ix++;
        }
        
        int[][] ixs = new int[N][2];
        for (int i = 0; i < N; i++) {
            double[] theta = new double[nParams];
            double[] inst = new double[nFeatures];
            for (int k = 0; k < nParams; k++) theta[k] = data[i][k];
            for (int k = 0; k < nFeatures; k++) inst[k] = data[i][k+nParams];
            
            for (int j = 0; j < all_theta.length; j++) {
                if (Arrays.equals(all_theta[j], theta)) {
                    ixs[i][0] = j;
                    break;
                }
            }
            
            for (int j = 0; j < all_x.length; j++) {
                if (Arrays.equals(all_x[j], inst)) {
                    ixs[i][1] = j;
                    break;
                }
            }
        }
        
        CensoredRandomForest rf = new CensoredRandomForest(20, 0, 20000000, 1, catDomainSizes, new Random());
        rf.learnModel(all_theta, all_x, nParams, nFeatures, ixs, y, cens, 0);
        System.out.println("Learned model");
        
        
        double[][] res = rf.predict(new double[][] {{-4.9, -4.52}});
        System.out.println(res[0][0] + " " + res[0][1]);
        
        /*
        double[][] preds = new double[1000*100][2];
        ix = 0;
        for (double x1 = -5; x1 < 5; x1 += 0.1) {
            for (double x2 = -5; x2 < 5; x2 += 0.1) {
                preds[ix][0] = x1;
                preds[ix][1] = x2;
                ix++;
            }
        }
        double[][] pred = rf.predict(preds);
        
        ix = 0;
        for (double x1 = -5; x1 < 5; x1 += 0.1) {
            for (double x2 = -5; x2 < 5; x2 += 0.1) {
                //System.out.println("model(" + x1 + "," + x2 + ") = " + pred[ix][0] + ", " + pred[ix][1]);
                System.out.println(x1 + " " + x2 + " " + pred[ix][0] + " " + pred[ix][1]);
                ix++;
            }
        }*/
    }
}
