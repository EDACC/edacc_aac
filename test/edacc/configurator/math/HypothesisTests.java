package edacc.configurator.math;

import static org.junit.Assert.*;

import org.apache.commons.math.MathException;
import org.junit.Test;
import org.rosuda.JRI.REXP;
import org.rosuda.JRI.Rengine;


public class HypothesisTests {

    @Test
    public void testFriedmanTest() throws MathException {
        double[][] data = new double[][] {
                { 1.0, 3.0, 2.0},
                { 1.0, 3.0, 2.0},
                { 1.0, 2.0, 3.0},
                { 1.0, 3.0, 2.0},
                { 1.0, 3.0, 2.0},
                { 1.0, 3.0, 2.0},
                { 10.0, 10.0, 3.0},
                };
        FamilyTest ft = new FriedmanTest(data.length, data[0].length, data);
        
        assertTrue(ft.familyTestStatistic() == 8.962962962962964);
        assertTrue(ft.isFamilyTestSignificant(ft.familyTestStatistic(), 0.05));
        assertFalse(ft.isFamilyTestSignificant(ft.familyTestStatistic(), 0.005));
    }
    
    @Test
    public void testLogrankTest() throws Exception {
        Rengine rengine = new Rengine(new String[] { "--vanilla" }, false, null);;
        if (!rengine.waitForR()) {
            throw new Exception("Could not initialize Rengine");
        }

        if (rengine.eval("library(survival)") == null) {
            rengine.end();
            throw new Exception("Did not find R library survival (should come with R though).");
        }

        LogrankTest lr = new LogrankTest(rengine);
        System.out.println(lr.pValue(new double[] { 1.01, 2.01, 3.0, Double.NaN, 2.0 }, new double[] { 10.0, 7.0, Double.NaN,
                6.0, 10.0 }, new boolean[] { false, false, false, false, false },
                new boolean[] { true, false, false, false, true }));

        rengine.end();
    }
    
    @Test
    public void testSMTest() throws Exception {
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
        REXP e = re.eval("MS.test(M, seq(3), reps=1)$P");
        System.out.println(e.asDouble());
        
        SMTest t = new SMTest(3, 3, new Double[][] {{1.0,2.0,3.0}, {2.0, 2.0, 4.0}, {3.0, 3.0, 1.0}}, re);
        System.out.println(t.pValue());
        
        re.end();
    }
}
