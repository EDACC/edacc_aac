package edacc.configurator.math;

import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.ChiSquaredDistribution;
import org.apache.commons.math.distribution.ChiSquaredDistributionImpl;
import org.apache.commons.math.distribution.FDistribution;
import org.apache.commons.math.distribution.FDistributionImpl;
import org.apache.commons.math.stat.ranking.NaNStrategy;
import org.apache.commons.math.stat.ranking.NaturalRanking;
import org.apache.commons.math.stat.ranking.TiesStrategy;

public class RankTransformationTest implements FamilyTest {
    private int n, c;
    private double[][] score_ranks_by_block;
    private double[][] overall_ranks;
    
    public RankTransformationTest(int n, int c, double[][] data, boolean[][] censored) throws Exception {
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
        
        // transpose matrices
        double[][] transposed_data = new double[c][n - numMissingRows];
        boolean[][] transposed_censored = new boolean[c][n - numMissingRows];
        int nCount = 0;
        for (int i = 0; i < n; i++) {
            if (missingRow[i]) continue;
            for (int j = 0; j < c; j++) {
                transposed_data[j][nCount] = data[i][j];
                transposed_censored[j][nCount] = censored[i][j];
            }
            nCount++;
        }
        int tmp;
        tmp = n;
        n = c;
        c = tmp - numMissingRows;
        
        censored = transposed_censored;
        data = transposed_data;
        
        this.n = n;
        this.c = c;
        
        if (c == 0) return;
        
        double[][] logrank_score = new double[n][c];
        double[][] ranks = new double[n][c];
        NaturalRanking ranking = new NaturalRanking(NaNStrategy.MAXIMAL, TiesStrategy.AVERAGE);
        
        // fill ranks matrix with the ranks within each block (solver config)
        for (int j = 0; j < c; j++) {
            double[] block_j_ranks = ranking.rank(col(data, j));
            for (int i = 0; i < n; i++) ranks[i][j] = block_j_ranks[i];
        }
        
        
        /*StringBuilder out = new StringBuilder();
        out.append("\n");
        for (int i = 0; i < ranks.length; i++) {
            for (int j = 0; j < ranks[i].length; j++) {
                out.append(String.format("%8.1f ", ranks[i][j]));
            }
            out.append("\n");
        }
        System.out.println("Within block ranks: \n" + out.toString());*/
        
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < c; j++) {
                // calculate logrank_score[i][j]
                logrank_score[i][j] = 0;
                for (int a = 0; a < n; a++) {
                    logrank_score[i][j] += (data[i][j] >= data[a][j] ? 1 : 0) * (censored[a][j] ? 0 : 1) / (double)(n + 1 - ranks[a][j]); 
                }
                logrank_score[i][j] -= (censored[i][j] ? 0 : 1);
                //System.out.print(logrank_score[i][j] + " ");
            }
        }
        //System.out.println();
        
        /*out = new StringBuilder();
        out.append("\n");
        for (int i = 0; i < logrank_score.length; i++) {
            for (int j = 0; j < logrank_score[i].length; j++) {
                out.append(String.format("%8.3f ", logrank_score[i][j]));
            }
            out.append("\n");
        }
        System.out.println("Logrank scores: \n" +  out.toString());*/
        
        // Rank Transformation variant:
        overall_ranks = new double[n][c];
        double[] linearized_scores = new double[n*c];
        int ix = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < c; j++) {
                linearized_scores[ix++] = logrank_score[i][j];
            }
        }
        ix = 0;
        double[] overall_linearized_ranks = ranking.rank(linearized_scores);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < c; j++) {
                overall_ranks[i][j] = overall_linearized_ranks[ix++];
            }
        }

        
        // Friedman variant: ranks of logrank scores within each block
        /*score_ranks_by_block = new double[n][c];
        for (int j = 0; j < c; j++) {
            double[] block_j_score_ranks = ranking.rank(col(logrank_score, j));
            for (int i = 0; i < n; i++) score_ranks_by_block[i][j] = block_j_score_ranks[i];
        }
        
        out = new StringBuilder();
        out.append("\n");
        for (int i = 0; i < score_ranks_by_block.length; i++) {
            for (int j = 0; j < score_ranks_by_block[i].length; j++) {
                out.append(String.format("%8.2f ", score_ranks_by_block[i][j]));
            }
            out.append("\n");
        }
        System.out.println("Score ranks by block: \n" +  out.toString());*/
        

        /*out = new StringBuilder();
        out.append("\n");
        for (int i = 0; i < overall_ranks.length; i++) {
            for (int j = 0; j < overall_ranks[i].length; j++) {
                out.append(String.format("%8.2f ", overall_ranks[i][j]));
            }
            out.append("\n");
        }
        System.out.println("Overall score ranks: \n" +  out.toString());*/
        
       // throw new Exception("Does not work yet"); // TODO
    }
    
    private double[] col(double[][] data, int j) {
        double[] col = new double[data.length];
        for (int i = 0; i < data.length; i++) col[i] = data[i][j];
        return col;
    }

    @Override
    public double familyTestStatistic() {
        if (c == 0) return 0;
        
        int N = n * c;
        
        // Ranktransform variant
        double sum1 = 0;
        for (int i = 0; i < n; i++) {
            double innersum = 0;
            for (int j = 0; j < c; j++) {
                innersum += overall_ranks[i][j];
            }
            sum1 += innersum * innersum;
        }
        
        double sum2 = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < c; j++) {
                sum2 += overall_ranks[i][j];
            }
        }
        
        double sum3 = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < c; j++) {
                sum3 += overall_ranks[i][j] * overall_ranks[i][j];
            }
        }
        
        double RT = (1.0 / c * sum1 - sum2*sum2 / N) / (n - 1);
        RT /= (sum3 - sum2*sum2 / N) / (N - n - c + 1);
        
        
        // Friedman variant
        /*double F = 12.0 * c / (N * (N + c));
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            double innersum = 0.0f;
            for (int j = 0; j < c; j++) {
                innersum += score_ranks_by_block[i][j];
            }
            innersum -= (N + c) / 2.0;
            sum += innersum*innersum;
        }
        F *= sum;*/
        
        return RT;
    }

    @Override
    public boolean isFamilyTestSignificant(double T, double alpha) throws MathException {
        //ChiSquaredDistribution XS = new ChiSquaredDistributionImpl(n - 1);
        //return T > XS.inverseCumulativeProbability(1.0 - alpha);
        if (c <= 1 || n <= 1) return false;
        FDistribution FD = new FDistributionImpl(n - 1, n*c - n - c + 1);
        return T > FD.inverseCumulativeProbability(1.0 - alpha);
    }
    
    @Override
    public double criticalValue(double alpha) throws MathException {
        //ChiSquaredDistribution XS = new ChiSquaredDistributionImpl(n - 1);
        //return XS.inverseCumulativeProbability(1.0 - alpha);
        if (c <= 1 || n <= 1) return 0;
        FDistribution FD = new FDistributionImpl(n - 1, n*c - n - c + 1);
        return FD.inverseCumulativeProbability(1.0 - alpha);
    }

    public static void main(String ... args) throws Exception {
        double[][] data = new double[][] {
                { 1, 3.0, 2.0},
                { 1, 3.0, 2.0},
                { 1, 1.0, 3.0},
                { 1, 3.0, 2.0},
                { 1, Double.NaN, 2.0},
                { 1, 3.0, 2.0},
                { 1, 104.0, 3.0},
                };
        boolean[][] censored = new boolean[][] {
                { false, false, false},
                { false, false, false},
                { false, false, false},
                { false, false, false},
                { false, false, false},
                { false, false, false},
                { true, true, false},
                };

        FamilyTest rts = new RankTransformationTest(data.length, data[0].length, data, censored );
        System.out.println("Critical value: " + rts.criticalValue(0.05));
        System.out.println("Test statistic: " + rts.familyTestStatistic());
        System.out.println(rts.isFamilyTestSignificant(rts.familyTestStatistic(), 0.05));
    }
}
