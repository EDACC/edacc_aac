package edacc.configurator.math;

import java.util.List;

import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.ChiSquaredDistribution;
import org.apache.commons.math.distribution.ChiSquaredDistributionImpl;
import org.apache.commons.math.distribution.TDistribution;
import org.apache.commons.math.distribution.TDistributionImpl;
import org.apache.commons.math.stat.ranking.NaNStrategy;
import org.apache.commons.math.stat.ranking.NaturalRanking;
import org.apache.commons.math.stat.ranking.TiesStrategy;

import edacc.configurator.aac.SolverConfiguration;

/**
 * Friedman family-wise test for significant differences.
 * 
 * @author daniel
 *
 */
public class FriedmanTest implements FamilyTest {
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
        boolean[] missingRow = new boolean[n];
        int numMissingRows = 0;
        for (int j = 0; j < n; j++) missingRow[j] = false; 
        
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < c; i++) {
                if (Double.isNaN(data[j][i])) {
                    missingRow[j] = true; 
                    numMissingRows++;
                    break;
                }
            }
        }
        
        ranks = new double[n - numMissingRows][c];
        rankSums = new double[c];

        NaturalRanking ranking = new NaturalRanking(NaNStrategy.MAXIMAL, TiesStrategy.AVERAGE);
        int row = 0;
        for (int i = 0; i < n; i++) {
            if (missingRow[i]) continue;
            double[] rankedData = ranking.rank(data[i]);
            for (int j = 0; j < c; j++) {
                ranks[row][j] = rankedData[j];
                rankSums[j] += rankedData[j];
            }
            
            row++;
        }
        

        /*System.out.println("Friedman test will be calculated on:");
        StringBuilder out = new StringBuilder();
        out.append("\n");
        for (int i = 0; i < ranks.length; i++) {
            for (int j = 0; j < ranks[i].length; j++) {
                out.append(String.format("%5.4f ", ranks[i][j]));
            }
            out.append("\n");
        }
        System.out.println(out.toString());*/

        m = c;
        k = n - numMissingRows;
    }

    /**
     * Calculates the family-wise test statistic value T
     * 
     * @return
     */
    public double familyTestStatistic() {
        if (k == 0 || m == 0) return 0;
        
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
        //System.out.println("T-value: " + T + " >? Quantile: " + XS.inverseCumulativeProbability(1.0 - alpha));
        return T > XS.inverseCumulativeProbability(1.0 - alpha);
    }
    
    public double criticalValue(double alpha) throws MathException {
        ChiSquaredDistribution XS = new ChiSquaredDistributionImpl(m - 1);
        return XS.inverseCumulativeProbability(1.0 - alpha);
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
        TDistribution tDist = new TDistributionImpl((m-1)*(k-1));
        return F > tDist.inverseCumulativeProbability(1 - alpha / 2.0);
    }
}
