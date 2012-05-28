package edacc.configurator.aac.racing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;
import org.rosuda.JRI.Rengine;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.configurator.math.FriedmanTest;
import edacc.configurator.math.SMTest;
import edacc.model.ExperimentResult;


/**
 * SMFRace configuration racing method.
 * See
 * "On the use of a Friedman-type statistic in balanced and unbalanced block designs. by Skillings JH, Mack GA."
 * and 
 * "F-Race and iterated F-Race: An overview" by Birattari, Yuan, Balaprakash and Stützle.
 * 
 * Each configuration in the race is evaluated on the same course of instances. As soon as
 * a family-wise Skillings-Mack test indicates that at least one configuration is significantly different
 * from at least one other, every configuration that is determined to be worse in a pairwise comparison
 * with the currently best configuration is removed from the race. All surviving configurations are
 * further evaluated until <code>min_survive</code> configurations remain at which point the race is over.
 * 
 * TODO: make it work
 * 
 * @author daniel
 *
 */
public class SMFRace extends RacingMethods {
    private int num_instances;
    private List<SolverConfiguration> raceConfigurations;
    private List<SolverConfiguration> curFinishedConfigurations;
    private List<SolverConfiguration> initialRaceConfigurations;
    private List<SolverConfiguration> raceSurvivors;
    private Map<Integer, Map<SolverConfiguration, Float>> courseResults;
    private Map<SolverConfiguration, Float> lastRoundCost;
    private SolverConfiguration bestSC = null;
    private int level = 0; // current level (no. of jobs per config in the race)
    private int starts = 0;
    private int race = 0;
    private int round = 0;
    
    private Rengine rengine;
    
    private int initialRaceRuns;
    private int Nmin;
    private int numRaceConfigurations;
    

    // parameters
    double alpha = 0.05; // significance level alpha
    double NminFactor = 2.5;
    double initialRunsFactor = 0.05;
    double numRaceConfigurationsFactor = 10;
    int CPUFactor = 1;
    
    public SMFRace(AAC pacc, Random rng, API api, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
        super(pacc, rng, api, parameters, firstSCs, referenceSCs);
        this.num_instances = api.getCourse(parameters.getIdExperiment()).getInitialLength();
        this.raceConfigurations = new ArrayList<SolverConfiguration>();
        this.initialRaceConfigurations = new ArrayList<SolverConfiguration>();
        this.curFinishedConfigurations = new ArrayList<SolverConfiguration>();
        this.courseResults = new HashMap<Integer, Map<SolverConfiguration, Float>>();
        this.raceSurvivors = new ArrayList<SolverConfiguration>();
        this.lastRoundCost = new HashMap<SolverConfiguration, Float>();
        
        rengine = new Rengine(new String[]{"--vanilla"}, false, null);
        
        if (!rengine.waitForR()) {
            throw new Exception("Could not initialize Rengine");
        }
        
        if (rengine.eval("library(asbio)") == null) {
            rengine.end();
            throw new Exception("Did not find R library asbio (try running install.packages(\"asbio\")).");
        }
        
        String val;
        if ((val = parameters.getRacingMethodParameters().get("SMFRace_alpha")) != null)
            this.alpha = Double.parseDouble(val);
        if ((val = parameters.getRacingMethodParameters().get("SMFRace_NminFactor")) != null)
            this.NminFactor = Double.parseDouble(val);
        if ((val = parameters.getRacingMethodParameters().get("SMFRace_initialRunsFactor")) != null)
            this.initialRunsFactor = Double.parseDouble(val);
        if ((val = parameters.getRacingMethodParameters().get("SMFRace_numRaceConfigurationsFactor")) != null)
            this.numRaceConfigurationsFactor = Double.parseDouble(val);
        if ((val = parameters.getRacingMethodParameters().get("SMFRace_CPUFactor")) != null)
            this.CPUFactor = Integer.parseInt(val);
        
        this.Nmin = (int) Math.round(NminFactor * api.getConfigurableParameters(parameters.getIdExperiment()).size());
        this.initialRaceRuns = (int) Math.max(1, Math.round(initialRunsFactor * num_instances));
        this.numRaceConfigurations = (int) Math.max(1, Math.round(numRaceConfigurationsFactor * api.getConfigurableParameters(parameters.getIdExperiment()).size()));
        
        throw new Exception("TODO: not implemented yet");
    }

    @Override
    public int compareTo(SolverConfiguration sc1, SolverConfiguration sc2) {
        this.numCompCalls++;
        return sc1.compareTo(sc2); // this isn't actually used within FRace but let's provide it for other search procedures
    }

    @Override
    public List<SolverConfiguration> getBestSolverConfigurations() {
    	// TODO: sort solver configs
        return getRaceSurvivors();
    }

    @Override
    public void solverConfigurationsFinished(List<SolverConfiguration> scs) throws Exception {
        curFinishedConfigurations.addAll(scs);
        
        for (SolverConfiguration solverConfig : raceConfigurations) {
            int i = 0; // course entry number
            for (ExperimentResult run : api.getRuns(parameters.getIdExperiment(), solverConfig.getIdSolverConfiguration())) {
                if (i > level) break; // only consider runs up until the current level (already existing configurations might have more)
                if (!courseResults.containsKey(i))
                    courseResults.put(i, new HashMap<SolverConfiguration, Float>());
                if (run.getStatus().getStatusCode() >= 1) {
                    courseResults.get(i).put(solverConfig, parameters.getStatistics().getCostFunction().singleCost(run));
                }
                else {
                    // missing values are null
                    courseResults.get(i).put(solverConfig, null);
                }
                i += 1;
            }
        }
        
        if (curFinishedConfigurations.containsAll(raceConfigurations) && raceConfigurations.size() == 2) {
            // if only 2 configurations remain perform a wilcoxon signed-rank test because it is more powerful
            // than a friedman test
            pacc.log("Only 2 configurations remaining, perform wilcoxon signed-rank test.");
            double[] c1 = new double[courseResults.size()];
            double[] c2 = new double[courseResults.size()];
            for (int i = 0; i < courseResults.size(); i++) {
                c1[i] = courseResults.get(i).get(raceConfigurations.get(0));
                c2[i] = courseResults.get(i).get(raceConfigurations.get(1));
            }
            
            WilcoxonSignedRankTest wtest = new WilcoxonSignedRankTest();
            if (wtest.wilcoxonSignedRankTest(c1, c2, false) < alpha) {
                if (raceConfigurations.get(0).compareTo(raceConfigurations.get(1)) == 1) {
                    raceConfigurations.remove(1);
                } else {
                    raceConfigurations.remove(0);
                }
            } else {
                pacc.log("wilcoxon signed-rank test didn't find a significant difference between the two solver configurations");
            }
        } else {
            Double[][] data = new Double[courseResults.size()][raceConfigurations.size()];
            for (int i = 0; i < courseResults.size(); i++) {
                for (int j = 0; j < raceConfigurations.size(); j++) {
                    data[i][j] = Double.valueOf(courseResults.get(i).get(raceConfigurations.get(j)));
                }
            }
            
            SMTest smTest = new SMTest(courseResults.size(), raceConfigurations.size(), data, rengine);
            if (smTest.isFamilyTestSignificant(alpha)) {
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

                    // calculate post hoc test between the two solver
                    // configuration results
                    // 
                    //double F = friedmanTest.postHocTestStatistic(j, bestConfigurationIx, T);
                    //if (friedmanTest.isPostHocTestSignificant(F, alpha)) {
                    //    // the best and this configuration are significantly
                    //    // different enough to discard this one from the race
                    //    worseConfigurations.add(raceConfigurations.get(j));
                    //    raceConfigurations.get(j).setFinished(true);
                    //    pacc.log("Removing " + raceConfigurations.get(j).getName() + " ("+raceConfigurations.get(j).getCost()+") from race because it is significantly worse than the best configuration ("+bestConfiguration.getCost()+")");
                    //}
                }
                raceConfigurations.removeAll(worseConfigurations);
                
            } else {
                pacc.log("family-wise comparison test indicated no significant differences between the configurations");
            }
            
            if (raceConfigurations.size() <= this.Nmin) {
                // end of race since there are less than the required amount of configurations remaining
                pacc.log("Terminating race because minimum number of remaining candidates was reached");
                terminateRace();
                return;
            }
            
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
        raceConfigurations.addAll(referenceSCs);
        initialRaceConfigurations.addAll(referenceSCs);
        lastRoundCost.clear();
        boolean allNewSCs = true;
        for (SolverConfiguration solverConfig: scs) {
            // don't consider reference configs
            allNewSCs &= !referenceSCs.contains(solverConfig) && solverConfig.getNumFinishedJobs() == 0;
        }
        if (allNewSCs) {
            starts += 1;
            race = 0;
        }
        race += 1;
        round = 1;
        for (SolverConfiguration solverConfig : raceConfigurations) {
            solverConfig.setFinished(false);
            pacc.expandParcoursSC(solverConfig, Math.max(0, initialRaceRuns - solverConfig.getNumFinishedJobs()));
            pacc.addSolverConfigurationToListNewSC(solverConfig);
            solverConfig.setNameRacing((referenceSCs.contains(solverConfig) ? "REF-":"") + starts + "-" + race + "-" + round);
        }
        level = initialRaceRuns - 1;
        pacc.log("c Starting new race with " + scs.size() + " solver configurations and " + referenceSCs.size() + " reference configurations");
    }

    @Override
    public int computeOptimalExpansion(int computationCoreCount, int computationJobCount, int listNewSCSize) {
        if (raceConfigurations.isEmpty()) return this.numRaceConfigurations;
        else return 0; // race ongoing, don't create any new
    }

    @Override
    public void listParameters() {
        System.out.println("--- SMFRace parameters ---");
        System.out.println("SMFRace_alpha = "+this.alpha+ " (Significance level alpha used in the statistical hypothesis tests)");
        System.out.println("SMFRace_NminFactor = " + this.NminFactor + " (#Solver configurations at most to survive a race: round(NminFactor * #configurable parameters) )");
        System.out.println("SMFRace_initialRunsFactor = " + this.initialRunsFactor + " (How many runs each configuration should get at the start of the race: round(initialRunsFactor * #instances) )");
        System.out.println("SMFRace_numRaceConfigurationsFactor = " + this.numRaceConfigurationsFactor + " (How many solver configurations should the racing method request from the search method: round(numRaceConfigurationsFactor * #parameters) )");
        System.out.println("SMFRace_CPUFactor = " + this.CPUFactor + " (number of jobs to generate each round: at least 1 for each racing configuration, at most in total CPUFactor * #available cores)");
        System.out.println("-----------------------\n");
    }

    @Override
    public String toString() {
        return "\nSMFRace racing method\n";
    }
    
    private void terminateRace() {
        pacc.log("The race ended with the following configurations remaining:");
        for (SolverConfiguration solverConfig: raceConfigurations) {
            solverConfig.setFinished(true);
            pacc.log(solverConfig.getName() + " ID: " + solverConfig.getIdSolverConfiguration() + " - " + solverConfig.getCost());
        }
        raceSurvivors.addAll(raceConfigurations);
        
        Collections.sort(raceSurvivors); // lowest cost last
        
        while (raceSurvivors.size() > Nmin)
        	raceSurvivors.remove(0);
        
        raceConfigurations.clear(); // this will end the race, since the next computeOptimalExpansion call will trigger the call to solverConfigurations created
    }
    
    public List<SolverConfiguration> getRaceSurvivors() {
        if (raceSurvivors.size() > 0) {
            pacc.log("getBestSolverConfigurations() called - Removing reference solvers from race survivors");
        }
        raceSurvivors.removeAll(referenceSCs);
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

	@Override
	public void stopEvaluation(List<SolverConfiguration> scs) throws Exception {
		// TODO Auto-generated method stub
		
	}
}
