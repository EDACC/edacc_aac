package edacc.configurator.math;

import org.apache.commons.math.MathException;
import org.rosuda.JRI.REXP;
import org.rosuda.JRI.Rengine;

/**
 * Skillings-Mack hypothesis test.
 * 
 * @author daniel
 *
 */
public class SMTest {
    private double[] linearizedData;
    private Rengine rengine;
    private int n, c;
   
    /**
     * Set up a Skillings-Mack test for n observations, c configurations, the n x c data array
     * and the rengine which is assumed to be instantiated and the "asbio" library already loaded.
     * @param n
     * @param c
     * @param data
     * @param rengine
     */
    public SMTest(int n, int c, Double[][] data, Rengine rengine) {
        linearizedData = new double[n*c];
        this.rengine = rengine;
        this.n = n;
        this.c = c;
        
        int ix = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < c; j++) {
                linearizedData[ix++] = data[i][j] == null ? Double.NaN : data[i][j]; 
            }
        }
    }
    
    public double pValue() {
        rengine.assign("SMTestData", linearizedData);
        rengine.eval("SMTestData <- data.frame(matrix(SMTestData, nrow=" + n + ", ncol=" + c + ", byrow=T))");
        
        // throw out columns with less than 2 observations
        rengine.eval("SMTestData <- SMTestData[, apply(SMTestData, 2, function(x) sum(!is.na(x)) > (length(x) / 2))]");
        REXP x = rengine.eval("if (ncol(SMTestData) == 0) 1.0 else MS.test(SMTestData, seq("+ n + "), reps=1)$P");
        if (x == null) return 1.0;
        return x.asDouble();
        
    }
}
