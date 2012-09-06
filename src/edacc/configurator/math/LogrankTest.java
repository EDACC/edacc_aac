package edacc.configurator.math;

import org.rosuda.JRI.Rengine;
import org.rosuda.JRI.REXP;

/**
 * Logrank (aka Mantel-Cox test) 2-sample test.
 * 
 * @author daniel
 * 
 */
public class LogrankTest {
    private Rengine rengine;

    public LogrankTest(Rengine rengine) {
        this.rengine = rengine;
    }

    public double pValue(double[] x, double[] y, boolean[] x_censored, boolean[] y_censored) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("x and y have to be of the same length.");
        }
        double[] combinedData = new double[x.length + y.length];
        int[] group = new int[x.length + y.length];
        int[] combinedCensored = new int[x.length + y.length];

        for (int i = 0; i < x.length; i++) {
            combinedData[i] = x[i];
            combinedData[x.length + i] = y[i];
            group[i] = 1;
            group[x.length + i] = 2;
            combinedCensored[i] = x_censored[i] ? 0 : 1;
            combinedCensored[x.length + i] = y_censored[i] ? 0 : 1;
        }

        rengine.assign("combinedData", combinedData);
        rengine.assign("group", group);
        rengine.assign("combinedCensored", combinedCensored);
        REXP res = rengine.eval("1 - pchisq(survdiff(Surv(combinedData, combinedCensored) ~ group)$chisq, 1)");
        
        if (res == null) return 1.0;
        return res.asDouble();
    }
    
    public static void main(String ... args) throws Exception {
        Rengine rengine = new Rengine(new String[] { "--vanilla" }, false, null);

        if (!rengine.waitForR()) {
            throw new Exception("Could not initialize Rengine");
        }

        if (rengine.eval("library(asbio)") == null) {
            rengine.end();
            throw new Exception("Did not find R library asbio (try running install.packages(\"asbio\")).");
        }

        if (rengine.eval("library(survival)") == null) {
            rengine.end();
            throw new Exception("Did not find R library survival (should come with R though).");
        }
     
        
        double[][] data = new double[][] {
                { 14.0529, 8.0258, 20.0000, 10.8883, 14.3998, 11.1753, 7.2069 },
                { 1.4008, 1.5558, 1.1308, 2.2377, 1.2768, 1.0938, 2.3126 },
                { 15.8646, 18.3512, 6.4770, 20.0000, 9.9655, 20.0000, 20.0000 },
                { 1.0398, 1.7137, 0.7949, 1.3318, 1.3318, 1.0298, 1.4838 },
                { 1.9127, 2.2607, 1.9077, 1.7307, 4.6773, 1.4038, 3.4375 },
                { 9.4316, 11.9912, 8.8107, 7.3149, 20.0000, 8.0458, 20.0000 },
                { 12.6331, 15.0207, 20.0000, 20.0000, 20.0000, 11.2303, 19.4190 },
                { 1.1388, 1.7977, 0.9569, 1.2418, 1.1178, 1.5078, 1.2428 },
                { 15.8016, 17.3934, 9.6395, 12.2351, 20.0000, 9.1326, 20.0000 },
                { 9.1556, 3.1325, 1.5868, 2.1987, 6.3770, 2.9146, 2.3066 },
                { 1.9547, 5.1412, 3.9104, 12.1032, 2.5736, 10.7674, 13.4570 },
                { 6.4070, 20.0000, 20.0000, 10.0855, 20.0000, 1.0000, 3.5455 },
        };
        
        for (int i = 0; i < data[0].length; i++) {
            double sum = 0.0;
            for (int j = 0; j < data.length; j++) {
                sum += data[j][i];
            }
            System.out.println(sum / data.length);
        }
        
        double[] x = new double[data.length];
        double[] y = new double[data.length];
        boolean[] xc = new boolean[data.length];
        boolean[] yc = new boolean[data.length];
        
        for (int i = 0; i < data.length; i++) {
            x[i] = data[i][5];
            y[i] = data[i][6];
            xc[i] = x[i] >= 20;
            yc[i] = y[i] >= 20;
        }
        
        LogrankTest lr = new LogrankTest(rengine);
        System.out.println("p: " + lr.pValue(x, y, xc, yc));

        rengine.end();
    }
    
    
}
