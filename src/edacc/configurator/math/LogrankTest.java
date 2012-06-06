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
}
