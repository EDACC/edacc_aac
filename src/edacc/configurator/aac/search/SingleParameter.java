package edacc.configurator.aac.search;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.parameterspace.Parameter;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;

public class SingleParameter extends SearchMethods {
	boolean generated_scs;
	int numScs = 100;
	String paramName = "";
	Parameter param;
	ParameterGraph graph;
	public SingleParameter(API api, Random rng, Parameters parameters) throws Exception {
		super(api, rng, parameters);
		graph = api.loadParameterGraphFromDB(parameters.getIdExperiment());
		String val = null;
		if ((val = parameters.getSearchMethodParameters().get("SingleParameter_numScs")) != null) {
			numScs = Integer.valueOf(val);
		}
		if ((val = parameters.getSearchMethodParameters().get("SingleParameter_paramName")) != null) {
			paramName = val;
		}
		if (!graph.getParameterMap().containsKey(paramName)) {
			throw new IllegalArgumentException("Invalid parameter name " + paramName + ".");
		}
		System.out.println("Param: " + paramName);
		param = graph.getParameterMap().get(paramName);
		System.out.println("Domain: " + param.getDomain());
		generated_scs = false;
	}

	@Override
	public List<SolverConfiguration> generateNewSC(int num, SolverConfiguration currentBestSC) throws Exception {
		LinkedList<SolverConfiguration> scs = new LinkedList<SolverConfiguration>();
		if (generated_scs) {
			return scs;
		}
		generated_scs = true;
		for (Object val : param.getDomain().getUniformDistributedValues(numScs)) {
			ParameterConfiguration pConfig = new ParameterConfiguration(currentBestSC.getParameterConfiguration());
			pConfig.setParameterValue(param, val);
			int idSc = api.createSolverConfig(parameters.getIdExperiment(), pConfig, "Value: " + val);
			scs.add(new SolverConfiguration(idSc, pConfig, parameters.getStatistics()));
		}
		return scs;
	}

	@Override
	public void listParameters() {
	}

}
