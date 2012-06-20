package edacc.configurator.math;

import org.apache.commons.math.MathException;

/**
 * Interface for family-wise hypothesis tests.
 *
 */
public interface FamilyTest {
    /**
     * @return the value of the test statistic
     */
    public double familyTestStatistic();
    
    /**
     * Calculates whether the test statistic is statistically significant at level alpha
     * @param statistic
     * @param alpha
     * @return
     * @throws MathException
     */
    public boolean isFamilyTestSignificant(double statistic, double alpha) throws MathException;
    
    /**
     * If the test is one-sided, return the critical value of the test statistic (for debugging/logging)
     * @param alpha
     * @return
     * @throws MathException
     */
    public double criticalValue(double alpha) throws MathException;
}
