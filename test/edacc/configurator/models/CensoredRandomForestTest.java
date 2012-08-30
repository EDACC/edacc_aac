package edacc.configurator.models;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.math.MathException;

import edacc.configurator.models.rf.CensoredRandomForest;
import edacc.configurator.models.rf.fastrf.utils.Gaussian;

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
        
        
        int N = 100;
        File file = new File("/home/daniel/download/BBO_1D_configuration_runs.csv");
        BufferedReader bufRdr  = new BufferedReader(new FileReader(file));
        bufRdr.readLine(); // read header
        
        int nParams = 1;
        int nFeatures = 8;
        
        int[] catDomainSizes = new int[nParams+nFeatures];
        
        int logModel = 1;
        
        double censoringThreshold = 1e100;
        
        
        double data[][] = new double[N][nParams+nFeatures+1];
        
        double[] y = new double[N];
        boolean[] cens = new boolean[N];
       
        double f_min = 1e100;
        
        String line = null;
        int n = 0;
        while ((line = bufRdr.readLine()) != null) {
            if (n >= N) break;
            StringTokenizer st = new StringTokenizer(line, ",");
            for (int i = 0; i < nParams+nFeatures+1; i++) data[n][i] = Double.valueOf(st.nextToken());
            cens[n] = Integer.valueOf(st.nextToken()) == 1;
            y[n] = data[n][nParams+nFeatures];
            
            if (y[n] >= censoringThreshold) {
                y[n] = censoringThreshold;
                cens[n] = true;
            }
            if (logModel>0) {
                if (y[n] <= 0) y[n] = 1e-20;
                y[n] = Math.log10(y[n]);
            }
            if (y[n] < f_min) f_min = y[n];
            
            n++;
        }
        bufRdr.close();

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
            for (int j = 0; j < x.size(); j++) {
                all_x[ix][j] = x.get(j);
            }
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
        
        CensoredRandomForest rf = new CensoredRandomForest(50, logModel, censoringThreshold, 1, catDomainSizes, new Random());
        rf.learnModel(all_theta, all_x, nParams, nFeatures, ixs, y, cens);
        System.out.println("Learned model");
        
        if (nParams == 1) {
            // ================ 1D ====================
            double[][] preds = new double[10001][2];
    
            ix = 0;
            for (double x1 = -5; x1 < 5; x1 += 0.01) {
                preds[ix][0] = x1;
                ix++;
            }
            
            double[][] pred = rf.predict(preds);
            
            
            ix = 0;
            for (double x1 = -5; x1 < 5; x1 += 0.01) {
                double mu = pred[ix][0]; 
                double sigma = Math.sqrt(pred[ix][1]);
                double ei = expectedImprovement(mu, sigma, f_min);
                double ocb = -mu + 1*sigma;
                System.out.println(x1 + " " + pred[ix][0] + " " + pred[ix][1] + " " + ei + " " + ocb + " " + expExpectedImprovement(mu, sigma, f_min));
                ix++;
            }
        }
        else {
            // ==================== 2D ====================
            double[][] preds = new double[100000][2];
            ix = 0;
            for (double x1 = -5; x1 < 5; x1 += 0.05) {
                for (double x2 = -5; x2 < 5; x2 += 0.05) {
                    preds[ix][0] = x1;
                    preds[ix][1] = x2;
                    ix++;
                }
            }
            double[][] pred = rf.predict(preds);
            
            FileOutputStream fos = new FileOutputStream("/home/daniel/test");
            OutputStreamWriter out = new OutputStreamWriter(fos, "UTF-8"); 
            
            ix = 0;
            for (double x1 = -5; x1 < 5; x1 += 0.05) {
                for (double x2 = -5; x2 < 5; x2 += 0.05) {
                    double mu = pred[ix][0];
                    double sigma = Math.sqrt(pred[ix][1]);

                    double ei = expectedImprovement(mu, sigma, f_min);
                    double ocb = -mu + 1 * sigma;
                    out.write(x1 + " " + x2 + " " + pred[ix][0] + " " + pred[ix][1] + " " + ei + " " + ocb + " " + expExpectedImprovement(mu, sigma, f_min) + "\n");
                    ix++;
                }
            }
            
            rf.calculateVI();
            
            out.close();
            fos.close();
        }
        
        rf.calculateVI();
        System.out.println("Done.");

        
    }
    
    private static double expectedImprovement(double mu, double sigma, double f_min) throws MathException {
        int g = 1; // TODO
        
        double x = (f_min - mu) / sigma;
        double ei;
        if (g == 1) ei = (f_min - mu) * Gaussian.Phi(x) + sigma * Gaussian.phi(x);
        else if (g == 2) ei = sigma*sigma * ((x*x + 1) * Gaussian.Phi(x) + x * Gaussian.phi(x));
        else if (g == 3) ei = sigma*sigma*sigma * ((x*x*x + 3*x) * Gaussian.Phi(x) + (2 + x*x) * Gaussian.phi(x));
        else ei = 0;
        
        return ei;
    }
    
    private static double expExpectedImprovement(double mu, double sigma, double f_min) {
        f_min = Math.log(10) * f_min;
        mu = Math.log(10) * mu;
        sigma = Math.log(10) * sigma;

        double b = Math.exp(f_min);

        return Math.exp(f_min + normcdfln((f_min - mu) / sigma))
                - Math.exp(sigma * sigma / 2.0 + mu + normcdfln((f_min - mu) / sigma - sigma));
    }
    
    
    static double normcdf(double x) {
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

    
    static double normcdfln(double x) {
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
    
}
