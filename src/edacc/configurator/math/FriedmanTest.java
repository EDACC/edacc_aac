package edacc.configurator.math;

import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.ChiSquaredDistribution;
import org.apache.commons.math.distribution.ChiSquaredDistributionImpl;
import org.apache.commons.math.distribution.TDistribution;
import org.apache.commons.math.distribution.TDistributionImpl;
import org.apache.commons.math.stat.ranking.NaNStrategy;
import org.apache.commons.math.stat.ranking.NaturalRanking;
import org.apache.commons.math.stat.ranking.TiesStrategy;

public class FriedmanTest {
    private double[][] ranks;
    private double[] rankSums;
    private int m, k;

    /**
     * Set up Friedman family and post-hoc tests given <code>n</code>
     * observations for <code>c</code> solver configurations in the n x c data
     * matrix.
     * 
     * @param n
     *            Number of observations for each solver configuration
     * @param c
     *            Number of solver configurations
     * @param data
     *            n x c matrix of the observed values
     */
    public FriedmanTest(int n, int c, double[][] data) {
        double[][] ranks = new double[n][c];
        double[] rankSums = new double[c];

        NaturalRanking ranking = new NaturalRanking(NaNStrategy.MAXIMAL, TiesStrategy.AVERAGE);
        for (int i = 0; i < n; i++) {
            double[] rankedData = ranking.rank(data[i]);
            for (int j = 0; j < c; j++) {
                ranks[i][j] = rankedData[j];
                rankSums[j] += rankedData[j];
            }
        }

        m = c;
        k = n;
    }

    /**
     * Calculates the family-wise test statistic value T
     * 
     * @return
     */
    public double familyTestStatistic() {
        double sum = 0;
        for (int j = 0; j < m; j++)
            sum += (rankSums[j] - (k * (m + 1)) / 2.0) * (rankSums[j] - (k * (m + 1)) / 2.0);
        double doublesum = 0;
        for (int l = 0; l < k; l++)
            for (int j = 0; j < m; j++)
                doublesum += ranks[l][j] * ranks[l][j];
        double T = (m - 1) * sum / (doublesum - k * m * (m + 1) * (m + 1) / 4.0);
        return T;
    }

    /**
     * Return whether the family-wise test statistic value T indicates
     * significant differences at level alpha.
     * 
     * @param T
     *            test statistic value returned by
     *            <code>familyTestStatistic()</code>
     * @param alpha
     *            significance level
     * @return
     * @throws MathException
     */
    public boolean isFamilyTestSignificant(double T, double alpha) throws MathException {
        ChiSquaredDistribution XS = new ChiSquaredDistributionImpl(m - 1);
        return T > XS.inverseCumulativeProbability(1.0 - alpha);
    }

    /**
     * Calculates the post-hoc test statistic F between two solver
     * configurations indexed by <code>c1</code> and <code>c2</code> given the
     * family-wise test statistic T returned by
     * <code>familyTestStatistic()</code>.
     * 
     * @param c1
     *            index (column) of the first solver configuration in the data
     *            matrix
     * @param c2
     *            index (column) of the second solver configuration in the data
     *            matrix
     * @param T
     *            test statistic value of the family-wise test returned by
     *            <code>familyTestStatistic()</code>
     * @return
     */
    public double postHocTestStatistic(int c1, int c2, double T) {
        double F = Math.abs(rankSums[c1] - rankSums[c2]);
        double dsum = 0;
        for (int l = 0; l < k; l++)
            for (int h = 0; h < m; h++)
                dsum += ranks[l][h] * ranks[l][h];
        F /= Math.sqrt(2 * k * (1 - T / (k * (m - 1))) * (dsum - k * m * (m + 1) * (m + 1) / 4.0) / ((k - 1) * (m - 1)));
        return F;
    }

    /**
     * Returns whether the post-hoc test statistic F as returned by
     * <code>postHocTestStatistic()</code> indicates a significant difference
     * between the two solver configurations at level alpha.
     * 
     * @param F
     *            test statistic value returned by
     *            <code>postHocTestStatistic()</code>
     * @param alpha
     *            significance level
     * @return
     * @throws MathException
     */
    public boolean isPostHocTestSignificant(double F, double alpha) throws MathException {
        // TODO: really m - 1 degrees of freedom here?
        TDistribution tDist = new TDistributionImpl(m - 1);
        return F > tDist.inverseCumulativeProbability((1 - alpha / 2.0));
    }
}
