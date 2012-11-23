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

public class CensoredRandomForestVI {    
    public static void main(String ... args) throws Exception {        
        API api = new APIImpl();
        
        api.connect("horst", 3306, "db", "user", "pw");
        int idExperiment = 29;
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
        
        RandomForest model = new RandomForest(api, idExperiment, true, 50, rng, CPUlimit, wallLimit, instanceFeatureNames, null, null, true);
        model.learnModel(solverConfigs);
        System.out.println("RSS: " + model.getOOBRSS());
        double[] VI = model.getVI();
        int ix = 0;
        for (Parameter p: model.getConfigurableParameters()) {
            System.out.println(p.getName() + ": " + VI[ix++]);
        }
        for (String instanceFeature: model.getInstanceFeatureNames()) {
            System.out.println(instanceFeature + ": " + VI[ix++]);
        }
        
        
        RInterface.shutdown();
    }
}
