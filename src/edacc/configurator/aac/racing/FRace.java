package edacc.configurator.aac.racing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.math.distribution.ChiSquaredDistribution;
import org.apache.commons.math.distribution.ChiSquaredDistributionImpl;
import org.apache.commons.math.distribution.TDistribution;
import org.apache.commons.math.distribution.TDistributionImpl;
import org.apache.commons.math.stat.ranking.*;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.configurator.aac.search.IteratedFRace;
import edacc.model.ExperimentResult;

/**
 * FRace configuration racing method.
 * See "F-Race and iterated F-Race: An overview" by Birattari, Yuan, Balaprakash and Stützle.
 * 
 * Each configuration in the race is evaluated on the same course of instances. As soon as
 * a family-wise Friedman test indicates that at least one configuration is significantly different
 * from at least one other, every configuration that is determined to be worse in a pairwise comparison
 * with the currently best configuration is removed from the race. All surviving configurations are
 * further evaluated until <code>min_survive</code> configurations remain at which point the race is over.
 * 
 * @author daniel
 *
 */
public class FRace extends RacingMethods {
    private int num_instances;
    private List<SolverConfiguration> raceConfigurations;
    private List<SolverConfiguration> curFinishedConfigurations;
    private List<SolverConfiguration> initialRaceConfigurations;
    private Map<Integer, Map<SolverConfiguration, Float>> courseResults;
    private SolverConfiguration bestSC;
    private IteratedFRace fraceSearch;
    private int level = 0;

    double alpha = 0.05; // significance level alpha
    float raceBudget;

    public FRace(AAC pacc, Random rng, API api, Parameters parameters) throws Exception {
        super(pacc, rng, api, parameters);
        this.num_instances = api.getCourse(parameters.getIdExperiment()).getInitialLength();
        this.raceConfigurations = new ArrayList<SolverConfiguration>();
        this.initialRaceConfigurations = new ArrayList<SolverConfiguration>();
        this.curFinishedConfigurations = new ArrayList<SolverConfiguration>();
        this.courseResults = new HashMap<Integer, Map<SolverConfiguration, Float>>();
        
        // assume search method object was already created ...
        if (!(pacc.search instanceof IteratedFRace)) {
            pacc.log("FRace racing only works properly with the IteratedFRace search method. Aborting.");
            throw new IllegalArgumentException("FRace racing only works properly with the IteratedFRace search method.");
        }
        fraceSearch = (IteratedFRace)pacc.search;
    }

    @Override
    public int compareTo(SolverConfiguration sc1, SolverConfiguration sc2) {
        this.numCompCalls++;
        return 0;
    }

    @Override
    public void initFirstSC(SolverConfiguration firstSC) throws Exception {
        this.bestSC = firstSC;
    }

    @Override
    public SolverConfiguration getBestSC() {
        return bestSC;
    }

    @Override
    public void solverConfigurationsFinished(List<SolverConfiguration> scs) throws Exception {
        curFinishedConfigurations.addAll(scs);
        
        // check if budget is exhausted (sum of CPU time of the jobs of all configuration this race was started with)
        float budgetUsed = 0;
        for (SolverConfiguration solverConfig: initialRaceConfigurations) {
            for (ExperimentResult run: solverConfig.getFinishedJobs()) budgetUsed += run.getResultTime();
        }
        pacc.log("CPU time used so far: " + budgetUsed);
        if (budgetUsed > raceBudget) {
            pacc.log("Terminating race because budget was exhausted");
            terminateRace();
            return;
        }
        
        if (curFinishedConfigurations.containsAll(raceConfigurations)) {
            pacc.log("c All "+raceConfigurations.size()+" currently racing configurations have finished their jobs");
            // fill result tableau
            for (SolverConfiguration solverConfig : raceConfigurations) {
                int i = 0; // course entry number
                for (ExperimentResult run : api.getRuns(parameters.getIdExperiment(), solverConfig.getIdSolverConfiguration())) {
                    if (i > level) break;
                    if (!courseResults.containsKey(i))
                        courseResults.put(i, new HashMap<SolverConfiguration, Float>());
                    courseResults.get(i).put(solverConfig, parameters.getStatistics().getCostFunction().singleCost(run));
                    i += 1;
                }
                pacc.log(solverConfig.getName() + " - Cost: " + solverConfig.getCost());
            }

            // calculate ranks per course entry and sum of ranks of each solver
            // configuration
            double[][] ranks = new double[courseResults.size()][raceConfigurations.size()];
            double[] rankSums = new double[raceConfigurations.size()];
            NaturalRanking ranking = new NaturalRanking(NaNStrategy.MAXIMAL, TiesStrategy.AVERAGE);
            for (int i = 0; i < courseResults.size(); i++) {
                double[] data = new double[raceConfigurations.size()];
                for (int j = 0; j < raceConfigurations.size(); j++) {
                    data[j] = courseResults.get(i).get(raceConfigurations.get(j));
                }
                double[] rankedData = ranking.rank(data);
                for (int j = 0; j < raceConfigurations.size(); j++) {
                    ranks[i][j] = rankedData[j];
                    rankSums[j] += rankedData[j];
                }
            }

            /*System.out.println("results");
            for (int i = 0; i < courseResults.size(); i++) {
                for (SolverConfiguration sc: raceConfigurations)
                    System.out.print(courseResults.get(i).get(sc) + "  ");
                System.out.println();
            }
            System.out.println("ranks");
            for (int i = 0; i < courseResults.size(); i++) {
                for (int j = 0; j < raceConfigurations.size(); j++) System.out.print(ranks[i][j] + "  ");
                System.out.println();
            }
            System.out.println("rank sums");
            for (int j = 0; j < raceConfigurations.size(); j++) System.out.print(rankSums[j] + " " );
            System.out.println();*/

            // calculate test statistic T
            int m = raceConfigurations.size();
            int k = courseResults.size();
            double sum = 0;
            for (int j = 0; j < m; j++)
                sum += (rankSums[j] - (k * (m + 1)) / 2.0) * (rankSums[j] - (k * (m + 1)) / 2.0);
            double doublesum = 0;
            for (int l = 0; l < k; l++)
                for (int j = 0; j < m; j++)
                    doublesum += ranks[l][j] * ranks[l][j];
            double T = (m - 1) * sum / (doublesum - k * m * (m + 1) * (m + 1) / 4.0);
            
            ChiSquaredDistribution XS = new ChiSquaredDistributionImpl(m - 1);
            if (T > XS.inverseCumulativeProbability(1.0 - alpha)) {
                // there is evidence that there is at least one solver
                // configuration that is significantly different from the others
                // find the best one, do pairwise comparisons and remove those
                // that are significantly worse from the race
                SolverConfiguration bestConfiguration = null;
                for (SolverConfiguration solverConfig : raceConfigurations) {
                    if (bestConfiguration == null || solverConfig.compareTo(bestConfiguration) == 1) {
                        bestConfiguration = solverConfig;
                    }
                }
                if (this.bestSC == null || bestConfiguration.compareTo(bestSC) == 1) {
                    this.bestSC = bestConfiguration;
                }

                List<SolverConfiguration> worseConfigurations = new ArrayList<SolverConfiguration>();
                int bestConfigurationIx = raceConfigurations.indexOf(bestConfiguration);
                for (int j = 0; j < raceConfigurations.size(); j++) {
                    if (j == bestConfigurationIx) 
                        continue;

                    // calculate Friedman post hoc test between the two solver
                    // configuration results
                    double F = Math.abs(rankSums[j] - rankSums[bestConfigurationIx]);
                    double dsum = 0;
                    for (int l = 0; l < k; l++)
                        for (int h = 0; h < m; h++)
                            dsum += ranks[l][h] * ranks[l][h];
                    
                    F /= Math.sqrt(2 * k * (1 - T / (k * (m - 1))) * (dsum - k * m * (m + 1) * (m + 1) / 4.0) / ((k - 1) * (m - 1)));

                    // TODO: really m - 1 degrees of freedom here?
                    TDistribution tDist = new TDistributionImpl(m - 1);
                    if (F > tDist.inverseCumulativeProbability((1 - alpha / 2.0))) {
                        // the best and this configuration are significantly
                        // different enough to discard this one from the race
                        //System.out.println("Determined " + raceConfigurations.get(j) + " to be a worse configuration");
                        worseConfigurations.add(raceConfigurations.get(j));
                        pacc.log("Removing " + raceConfigurations.get(j).getName() + " ("+raceConfigurations.get(j).getCost()+") from race because it is significantly worse than the best configuration ("+bestSC.getCost()+")");
                    }
                }
                raceConfigurations.removeAll(worseConfigurations);
                
                if (raceConfigurations.size() <= fraceSearch.getNmin()) {
                    // end of race since there are less than the required amount of configurations remaining
                    pacc.log("Terminating race because minimum number of remaining candidates was reached");
                    terminateRace();
                    return;
                }
            } else {
                pacc.log("family-wise comparison test indicated no significant differences between the configurations (T = " + T + " < Quantile = "+ XS.inverseCumulativeProbability(1.0 - alpha) + ")");
            }

            // all configurations that get to the next round are evaluated on
            // additional instances
            for (SolverConfiguration solverConfig : raceConfigurations) {
                pacc.expandParcoursSC(solverConfig, 1);
                solverConfig.setFinished(false);
                pacc.addSolverConfigurationToListNewSC(solverConfig);
            }
            level += 1;
            curFinishedConfigurations.clear();

        }
    }

    @Override
    public void solverConfigurationsCreated(List<SolverConfiguration> scs) throws Exception {
        curFinishedConfigurations.clear();
        courseResults.clear();
        raceConfigurations.clear();
        initialRaceConfigurations.clear();
        raceConfigurations.addAll(scs);
        initialRaceConfigurations.addAll(scs);
        for (SolverConfiguration solverConfig : scs) {
            if (solverConfig.getNumFinishedJobs() == 0) {
                solverConfig.setName(String.valueOf(solverConfig.getIdSolverConfiguration()));
                pacc.expandParcoursSC(solverConfig, 1);
            }
            pacc.addSolverConfigurationToListNewSC(solverConfig);
        }
        level = 0;
        raceBudget = fraceSearch.getRacingComputationalBudget();
        pacc.log("c Starting new race with " + scs.size() + " solver configurations, budget: " + raceBudget);
    }

    @Override
    public int computeOptimalExpansion(int computationCoreCount, int computationJobCount, int listNewSCSize) {
        if (raceConfigurations.isEmpty()) return fraceSearch.getNumRaceCandidates();
        else return 0; // race ongoing, don't create any new
    }

    @Override
    public void listParameters() {
        // TODO Auto-generated method stub

    }

    @Override
    public String toString() {
        return "\nFRace racing method\n";
    }
    
    private void terminateRace() {
        pacc.log("The race ended with the following configurations remaining:");
        for (SolverConfiguration solverConfig: raceConfigurations) {
            pacc.log(solverConfig.getName() + " - " + solverConfig.getCost());
        }
        List<SolverConfiguration> raceSurvivors = new ArrayList<SolverConfiguration>();
        raceSurvivors.addAll(raceConfigurations);
        fraceSearch.setRaceSurvivors(raceSurvivors);
        float budgetUsed = 0;
        for (SolverConfiguration solverConfig: initialRaceConfigurations) {
            for (ExperimentResult run: solverConfig.getFinishedJobs()) budgetUsed += run.getResultTime();
        }
        fraceSearch.updateBudgetUsed(budgetUsed);
        raceConfigurations.clear(); // this will end the race, since the next computeOptimalExpansion call will trigger the call to solverConfigurations created
    }

}
