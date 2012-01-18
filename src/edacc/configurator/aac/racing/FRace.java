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
    private List<SolverConfiguration> raceSurvivors;
    private Map<Integer, Map<SolverConfiguration, Float>> courseResults;
    private SolverConfiguration bestSC = null;
    private int level = 0; // current level (no. of jobs per config in the race)
    private int initialRaceRuns;
    private int Nmin;
    private int numRaceConfigurations;

    double alpha = 0.05; // significance level alpha

    public FRace(AAC pacc, Random rng, API api, Parameters parameters) throws Exception {
        super(pacc, rng, api, parameters);
        this.num_instances = api.getCourse(parameters.getIdExperiment()).getInitialLength();
        this.raceConfigurations = new ArrayList<SolverConfiguration>();
        this.initialRaceConfigurations = new ArrayList<SolverConfiguration>();
        this.curFinishedConfigurations = new ArrayList<SolverConfiguration>();
        this.courseResults = new HashMap<Integer, Map<SolverConfiguration, Float>>();
        this.raceSurvivors = new ArrayList<SolverConfiguration>();
        this.Nmin = Math.round(2.5f * api.getConfigurableParameters(parameters.getIdExperiment()).size());
        this.initialRaceRuns = Math.max(1, Math.round(0.05f * num_instances));
        this.numRaceConfigurations = 10 * api.getConfigurableParameters(parameters.getIdExperiment()).size();
    }

    @Override
    public int compareTo(SolverConfiguration sc1, SolverConfiguration sc2) {
        this.numCompCalls++;
        return sc1.compareTo(sc2); // this isn't actually used within FRace but let's provide it for other search procedures
    }

    @Override
    public void initFirstSC(SolverConfiguration firstSC) throws Exception {
       // this.bestSC = firstSC;
    }

    @Override
    public SolverConfiguration getBestSC() {
        return bestSC;
    }

    @Override
    public void solverConfigurationsFinished(List<SolverConfiguration> scs) throws Exception {
        curFinishedConfigurations.addAll(scs);
        
        float budgetUsed = 0.0f;
        for (SolverConfiguration solverConfig: initialRaceConfigurations) {
            int i = 0;
            for (ExperimentResult run: solverConfig.getFinishedJobs()) { if (i++ > level) break;  budgetUsed += run.getResultTime(); }
        }
        pacc.log("CPU time used so far: " + budgetUsed);
        
        if (level+1 > parameters.getMaxParcoursExpansionFactor() * num_instances) {
            terminateRace();
            return;
        }
        
        if (curFinishedConfigurations.containsAll(raceConfigurations)) {
            pacc.log("c All "+raceConfigurations.size()+" currently racing configurations have finished their jobs (#Runs: " + (level + 1) + ")");
            // fill result tableau
            for (SolverConfiguration solverConfig : raceConfigurations) {
                int i = 0; // course entry number
                for (ExperimentResult run : api.getRuns(parameters.getIdExperiment(), solverConfig.getIdSolverConfiguration())) {
                    if (i > level) break; // only consider runs up until the current level (already existing configurations might have more)
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
                
                if (raceConfigurations.size() <= this.Nmin) {
                    // end of race since there are less than the required amount of configurations remaining
                    pacc.log("Terminating race because minimum number of remaining candidates was reached");
                    terminateRace();
                    return;
                }
            } else {
                pacc.log("family-wise comparison test indicated no significant differences between the configurations (T = " + T + " < Quantile = "+ XS.inverseCumulativeProbability(1.0 - alpha) + ")");
            }

            int numAvailableCores = Math.max(1, api.getComputationCoreCount(parameters.getIdExperiment())); // make sure there's at least one iteration
            //System.out.println("numAvailableCores: " + numAvailableCores);
            boolean firstRound = true;
            boolean createdJobs = true;
            while (numAvailableCores > 0 && createdJobs) {
                boolean allConfigsEnoughJobsForLevel = true;
                createdJobs = false;
                for (SolverConfiguration solverConfig : raceConfigurations) {
                    int numJobs = solverConfig.getJobs().size();
                    //System.out.println("SC: " + solverConfig.getIdSolverConfiguration() + " numJobs: " + numJobs);
                    if (numJobs == level + 1) {
                        //System.out.println("added a job to SC " + solverConfig.getIdSolverConfiguration());
                        // this SC needs a new job
                        pacc.expandParcoursSC(solverConfig, 1);
                        numAvailableCores--;
                        createdJobs = true;
                    }
                    if (firstRound) {
                        pacc.addSolverConfigurationToListNewSC(solverConfig);
                        solverConfig.setFinished(false);
                    }
                    allConfigsEnoughJobsForLevel &= solverConfig.getJobs().size() >= level + 1;
                }
                firstRound = false;
                if (allConfigsEnoughJobsForLevel) {
                    level++;
                    //System.out.println("incremented level");
                } else {
                    //System.out.println("not all configs have enough jobs for the level, breaking");
                    break;
                }
                //System.out.println("numAvailableCores left: " + numAvailableCores);
            }
            curFinishedConfigurations.clear();
        }
    }

    @Override
    public void solverConfigurationsCreated(List<SolverConfiguration> scs) throws Exception {
        raceSurvivors.clear();
        curFinishedConfigurations.clear();
        courseResults.clear();
        raceConfigurations.clear();
        initialRaceConfigurations.clear();
        raceConfigurations.addAll(scs);
        initialRaceConfigurations.addAll(scs);
        for (SolverConfiguration solverConfig : scs) {
            if (solverConfig.getNumFinishedJobs() == 0) {
                pacc.expandParcoursSC(solverConfig, initialRaceRuns);
            }
            pacc.addSolverConfigurationToListNewSC(solverConfig);
            solverConfig.setName(String.valueOf(solverConfig.getIdSolverConfiguration()));
        }
        level = initialRaceRuns - 1;
        pacc.log("c Starting new race with " + scs.size() + " solver configurations");
    }

    @Override
    public int computeOptimalExpansion(int computationCoreCount, int computationJobCount, int listNewSCSize) {
        if (raceConfigurations.isEmpty()) return this.numRaceConfigurations;
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
        raceSurvivors.addAll(raceConfigurations);
        raceConfigurations.clear(); // this will end the race, since the next computeOptimalExpansion call will trigger the call to solverConfigurations created
    }
    
    public List<SolverConfiguration> getRaceSurvivors() {
        List<SolverConfiguration> copy = new ArrayList<SolverConfiguration>();
        copy.addAll(raceSurvivors);
        return copy;
    }

    public int getNmin() {
        return this.Nmin;
    }
    
    public int getNumRaceConfigurations() {
        return this.numRaceConfigurations;
    }
}
