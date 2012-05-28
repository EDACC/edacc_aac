package edacc.configurator.math;

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
                linearizedData[ix++] = data[i][c] == null ? Double.NaN : data[i][c]; 
            }
        }
    }
    
    public boolean isFamilyTestSignificant(double alpha) {
        rengine.assign("SMTestData", linearizedData);
        rengine.eval("SMTestData <- matrix(SMTestData, nrow=" + n + ", ncol=" + c + ", byrow=T)");
        long e = rengine.rniParse("MS.test(SMTestData, seq("+ n + "), reps=1", 1);
        long r = rengine.rniEval(e, 0);
        REXP x = new REXP(rengine, r);
        double p_value = x.asDouble();
        return p_value <= alpha;
        
    }
    
    public static void main(String ... args) throws Exception {
        // testing ...
        Rengine re = new Rengine(new String[]{"--vanilla"}, false, null);
        
        if (!re.waitForR()) {
            throw new Exception("Could not initialize Rengine");
        }
        
        if (re.eval("library(asbio)") == null) {
            re.end();
            throw new Exception("Did not find R library asbio (try running install.packages(\"asbio\")).");
        }
        
        re.assign("M", new double[] {Double.NaN, 1.0, 2.0, 3.0, 4.0, 5.0});
        re.eval("M = matrix(M, 3, 2)");
        long e=re.rniParse("MS.test(M, seq(3), reps=1)$P", 1);
        long r=re.rniEval(e, 0);
        REXP x=new REXP(re, r);
        System.out.println(x.asDouble());
        
        re.end();
    }
}
