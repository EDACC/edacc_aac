package edacc.configurator.aac.search;

import java.util.LinkedList;
import java.util.List;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;

/**
 * Generates random configurations
 */
public class RandomSearch extends SearchMethods {
    private ParameterGraph pspace;

    public RandomSearch(AAC pacc, API api, java.util.Random rng, Parameters parameters, List<SolverConfiguration> firstSCs,
            List<SolverConfiguration> referenceSCs) throws Exception {
        super(pacc, api, rng, parameters, firstSCs, referenceSCs);
        pspace = api.loadParameterGraphFromDB(parameters.getIdExperiment());
    }

    @Override
    public List<SolverConfiguration> generateNewSC(int num) throws Exception {
        List<SolverConfiguration> configs = new LinkedList<SolverConfiguration>();
        for (int i = 0; i < num; i++) {
            ParameterConfiguration config = pspace.getRandomConfiguration(rng);
            int idSC = api.createSolverConfig(parameters.getIdExperiment(), config, "random");
            configs.add(new SolverConfiguration(idSC, config, parameters.getStatistics()));
        }
        return configs;
    }

    @Override
    public List<String> getParameters() {
        return new LinkedList<String>();
    }

    @Override
    public void searchFinished() {
    }

}
