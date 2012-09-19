package edacc.configurator.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import edacc.api.API;
import edacc.api.APIImpl;
import edacc.api.costfunctions.CostFunction;
import edacc.api.costfunctions.PARX;
import edacc.configurator.aac.SolverConfiguration;
import edacc.configurator.aac.StatisticFunction;
import edacc.configurator.aac.util.RInterface;
import edacc.configurator.math.PCA;
import edacc.configurator.models.rf.CensoredRandomForest;
import edacc.configurator.models.rf.RandomForest;
import edacc.model.Experiment;
import edacc.model.ExperimentDAO;
import edacc.model.ExperimentResult;
import edacc.model.ExperimentResultDAO;
import edacc.model.Instance;
import edacc.model.InstanceDAO;
import edacc.model.InstanceHasProperty;
import edacc.model.Experiment.Cost;
import edacc.model.SolverConfigurationDAO;
import edacc.parameterspace.Parameter;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.domain.CategoricalDomain;
import edacc.parameterspace.domain.FlagDomain;
import edacc.parameterspace.domain.IntegerDomain;
import edacc.parameterspace.domain.OrdinalDomain;
import edacc.parameterspace.domain.RealDomain;
import edacc.parameterspace.graph.ParameterGraph;

public class CensoredRandomForestValidation {    
    public static void main(String ... args) throws Exception {        
        API api = new APIImpl();
        
        api.connect("edacc3", 3306, "daniel", "daniel", "edaccteam");
        int idExperiment = 4;
        int CPUlimit = 10;
        int wallLimit = 10;

        CostFunction par1CostFunc;
        if (ExperimentDAO.getById(idExperiment).getDefaultCost().equals(Cost.resultTime)) {
            par1CostFunc = new PARX(Experiment.Cost.resultTime, true, 1.0f);
        } else if (ExperimentDAO.getById(idExperiment).getDefaultCost().equals(Cost.wallTime)) {
            par1CostFunc = new PARX(Experiment.Cost.wallTime, true, 1.0f);
        } else {
            par1CostFunc = new PARX(Experiment.Cost.cost, true, 1.0f);
        }

        Random rng = new edacc.util.MersenneTwister(123);
        
        List<SolverConfiguration> solverConfigs = new LinkedList<SolverConfiguration>();
        Map<Integer, SolverConfiguration> scById = new HashMap<Integer, SolverConfiguration>();
        for (edacc.model.SolverConfiguration sc: SolverConfigurationDAO.getSolverConfigurationByExperimentId(idExperiment)) {
            SolverConfiguration csc = new SolverConfiguration(sc.getId(), api.getParameterConfiguration(idExperiment, sc.getId()), new StatisticFunction(par1CostFunc, true));
            System.out.println("Loaded configuration " + sc.getId());
            solverConfigs.add(csc);
            scById.put(csc.getIdSolverConfiguration(), csc);
        }
        System.out.println("Loaded configurations.");

        int countJobs = 0;
        for (ExperimentResult run: ExperimentResultDAO.getAllByExperimentId(idExperiment)) {
            scById.get(run.getSolverConfigId()).putJob(run);
            countJobs++;
        }
        
        System.out.println("Loaded " + countJobs + " runs.");
        
        List<String> instanceFeatureNames = new LinkedList<String>();
        
        
        for (int nTrees = 1; nTrees <= 256; nTrees *= 2) {
            double rep_rss = 0;
            int numRep = 2;
            for (int rep = 0; rep < numRep; rep++) {
                RandomForest model = new RandomForest(api, idExperiment, true, nTrees, rng, CPUlimit, wallLimit, instanceFeatureNames);
                model.learnModel(solverConfigs);
                //CensoredRandomForest model = new CensoredRandomForest(nTrees, 1, kappaMax, 1.0, catDomainSizes, rng, condParents, condParentVals);
                //learnModel(model, instanceFeatureNames, instanceFeatures, true, par1CostFunc, instanceFeaturesIx, configurableParameters, solverConfigs);
                //rep_rss += model.calculateOobRSS();
                rep_rss += model.getOOBRSS();
            }
            rep_rss /= numRep;
            System.out.println(nTrees + " " + rep_rss);
        }
        
        
        RInterface.shutdown();
    }
}
