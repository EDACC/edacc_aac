package edacc.configurator.math;

import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.ChiSquaredDistribution;
import org.apache.commons.math.distribution.ChiSquaredDistributionImpl;

/**
 * Modified version of the Friedman hypothesis test by Schemper: "A generalized
 * Friedman test for data defined by intervals" (1982), which accounts for
 * right-censored data.
 * 
 * Caution: The proposed X² approximation to the distribution of the test
 * statistic should be used for samples with n*k >= 30 to 40 if k >= 3. For k =
 * 2 one should use e.g. a generalization of the Wilcoxon matched-pairs
 * signed-rank test (also by Schemper, 1984)
 * 
 * In this test an observation is considered right-censored if the right
 * interval value is +inf. However there is never a test for "+inf", only
 * comparisons of values so a fixed value (e.g. cost limit) should be also fine.
 * 
 * TODO: check implementation
 */
public class FriedmanCensoredTest {
    private int[][] a_score;
    private int n, k;

    /**
     * Sets up a Friedman test with censored data given in the n x k matrix
     * data.
     * 
     * @param n
     * @param k
     * @param data
     */
    public FriedmanCensoredTest(int n, int k, double[][] data) {
        a_score = new int[k][n];

        for (int i = 0; i < k; i++) {
            for (int j = 0; j < n; j++) {
                a_score[i][j] = 0;
                for (int h = 0; h < k; h++) {
                    a_score[i][j] += (data[j][i] < data[j][h] ? 1 : (data[j][i] > data[j][h] ? -1 : 0));
                }
            }
        }

        this.n = n;
        this.k = k;
    }

    /**
     * Calculates the test statistic S.
     * 
     * @return
     */
    public double familyTestStatistic() {
        double denom = 0.0f;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < k; i++) {
                denom += a_score[i][j] * a_score[i][j];
            }
        }

        double nom = (k - 1);
        for (int i = 0; i < k; i++) {
            double dsum = 0.0f;
            for (int j = 0; j < n; j++) {
                dsum += a_score[i][j];
            }
            nom += dsum * dsum;
        }

        if (denom == 0)
            return 0.0f; // this should happen when all observations are equal
        return nom / denom;
    }

    /**
     * Returns whether the test statistic S indicates a signficantly different
     * configuration given significance level alpha.
     * 
     * @param S
     * @param alpha
     * @return
     * @throws MathException
     */
    public boolean isFamilyTestSignificant(double S, double alpha) throws MathException {
        ChiSquaredDistribution XS = new ChiSquaredDistributionImpl(k - 1);
        return S > XS.inverseCumulativeProbability(1.0 - alpha);
    }

    public static void main(String... args) throws Exception {
        FriedmanCensoredTest f = new FriedmanCensoredTest(10, 3, new double[][] { { 1, 3, 5 }, { 3, 3, 4 }, { 1, 1, 22 },
                { 1, 1, 10 }, { 1, 1, 10 }, { 1, 2, 10 }, { 1, 1, 10 }, { 1, 2, 10 }, { 1, 1, 10 }, { 1, 2, 10 }, });
        System.out.println(f.familyTestStatistic());
        System.out.println(f.isFamilyTestSignificant(f.familyTestStatistic(), 0.05));
    }

}
