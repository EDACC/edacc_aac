package edacc.configurator.models;

import java.util.Random;

import org.apache.commons.math.MathException;

import edacc.configurator.models.rf.CensoredRandomForest;
import edacc.configurator.models.rf.fastrf.utils.Gaussian;

public class RFExample {
    private static double levy(double x) {
        double z = 1 + (x - 1) / 4.0;
        double s = Math.sin(Math.PI * z);
        s = s*s;
        s = s + (z - 1) * (z - 1) * (1 + 10*(Math.pow(Math.sin(Math.PI * z + 1), 2)));
        return s + (z - 1) * (z - 1) * (1 + (Math.pow(Math.sin(2* Math.PI * z ), 2)));
    }
    
    public static void main(String ... args) throws Exception {
        Random rng = new Random(1234);
        /*for (double x = -10; x < 10; x += 0.01) {
            System.out.println(x + "," + levy(x));
        }*/

        int nProbe = 20;
        int nNext = 1;
        
        double[][] theta = new double[nProbe + nNext][1];
        double[][] inst  = new double[][] { {0} };
        int[][] theta_inst_idxs = new int[nProbe + nNext][2];
        boolean[] censored = new boolean[nProbe + nNext];
        double[] y = new double[nProbe + nNext];
        double f_min = Double.MAX_VALUE;
        for (int i = 0; i < nProbe; i++) {
            theta_inst_idxs[i][0] = i;
            theta_inst_idxs[i][1] = 0;
            double x = rng.nextDouble() * 20 - 10;
            theta[i][0] = x;
            y[i] = levy(x);
            f_min = Math.min(f_min, y[i]);
            System.out.println(x + "," + y[i]);
        }
        
        theta[nProbe][0] = 6.47;
        theta_inst_idxs[nProbe][0] = nProbe;
        theta_inst_idxs[nProbe][1] = 0;
        y[nProbe] = levy(6.47);
        System.out.println(6.47 + "," + y[nProbe]);
        
        
        CensoredRandomForest rf = new CensoredRandomForest(100, 0, 100000, 1, new int[2], rng, null, null);
        rf.learnModel(theta, inst, 1, 1, theta_inst_idxs, y, censored);
        
        for (double x = -10; x < 10; x += 0.01) {
            double[][] pred = rf.predict(new double[][] { { x }});
            double ocb = -pred[0][0] + 1 * Math.sqrt(pred[0][1]);
            double ei = expectedImprovement(pred[0][0], Math.sqrt(pred[0][1]), f_min);
            System.out.println(x + "," + pred[0][0] + "," + pred[0][1] + "," + ocb + "," + ei);
        }
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
}
