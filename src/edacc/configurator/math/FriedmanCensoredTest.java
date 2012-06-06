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
 * TODO: fix implementation for censored values
 */
public class FriedmanCensoredTest implements FamilyTest {
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
        boolean[] missingRow = new boolean[n];
        int numMissingRows = 0;
        for (int j = 0; j < n; j++) missingRow[j] = false; 
        
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < k; i++) {
                if (Double.isNaN(data[j][i])) {
                    missingRow[j] = true; 
                    numMissingRows++;
                    break;
                }
            }
        }
        
        a_score = new int[k][n - numMissingRows];
        for (int i = 0; i < k; i++) {
            int score_row = 0;
            for (int j = 0; j < n; j++) {
                if (missingRow[j]) continue; // skip rows with missing data
                a_score[i][score_row] = 0;
                for (int h = 0; h < k; h++) {
                    a_score[i][score_row] += (data[j][i] < data[j][h] ? 1 : (data[j][i] > data[j][h] ? -1 : 0));
                }
                score_row += 1;
            }
        }

        this.n = n - numMissingRows;
        this.k = k;
    }

    /**
     * Calculates the test statistic S.
     * 
     * @return
     */
    public double familyTestStatistic() {
        if (n == 0 || k == 0) return 0.0;
        
        double denom = 0.0f;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < k; i++) {
                denom += a_score[i][j] * a_score[i][j];
            }
        }

        double nom = 0;
        for (int i = 0; i < k; i++) {
            double dsum = 0.0f;
            for (int j = 0; j < n; j++) {
                dsum += a_score[i][j];
            }
            nom += dsum * dsum;
        }

        if (denom == 0)
            return 0.0f; // this should happen when all observations are equal
        return (k-1) * nom / denom;
    }
    
    public double criticalValue(double alpha) throws MathException {
        ChiSquaredDistribution XS = new ChiSquaredDistributionImpl(k - 1);
        return XS.inverseCumulativeProbability(1.0 - alpha);
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
        double[][] data = new double[][] {
                { 1.0, 3.0, 2.0},
                { 1.0, 3.0, 2.0},
                { 1.0, 2.0, 3.0},
                { 1.0, 3.0, 2.0},
                { 1.0, 3.0, 2.0},
                { 1.0, 3.0, 2.0},
                { 10.0, 10.0, 3.0},
                };
        FamilyTest f = new FriedmanCensoredTest(data.length, data[0].length, data);
        FamilyTest ft = new FriedmanTest(data.length, data[0].length, data);
        
        System.out.println("Critical value: " + ft.criticalValue(0.05));
        System.out.println("Censoring-aware:");
        System.out.println(f.familyTestStatistic());
        System.out.println(f.isFamilyTestSignificant(f.familyTestStatistic(), 0.05));
        
        System.out.println("Original:");
        System.out.println(ft.familyTestStatistic());
        System.out.println(ft.isFamilyTestSignificant(ft.familyTestStatistic(), 0.05));
    }

}
