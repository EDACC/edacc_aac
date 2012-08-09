package edacc.configurator.aac.search;

import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.parameterspace.graph.ParameterGraph;

public class SMBO extends SearchMethods {
    private ParameterGraph pspace;

    public SMBO(AAC pacc, API api, Random rng, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
        super(pacc, api, rng, parameters, firstSCs, referenceSCs);
        pspace = api.loadParameterGraphFromDB(parameters.getIdExperiment());

    }

    @Override
    public List<SolverConfiguration> generateNewSC(int num) throws Exception {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<String> getParameters() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void searchFinished() {
        // TODO Auto-generated method stub
        
    }

}
