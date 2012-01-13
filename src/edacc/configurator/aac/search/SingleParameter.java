package edacc.configurator.aac.search;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.parameterspace.Parameter;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;

public class SingleParameter extends SearchMethods {
	boolean generated_scs;
	int numScs = 100;
	int numScs2 = 100;
	boolean numScs2_spec;
	String paramName = "";
	String paramName2 = "";
	Parameter param;
	Parameter param2;
	ParameterGraph graph;
	public SingleParameter(AAC pacc, API api, Random rng, Parameters parameters) throws Exception {
		super(pacc, api, rng, parameters);
		
		graph = api.loadParameterGraphFromDB(parameters.getIdExperiment());
		numScs2_spec = false;
		String val = null;
		if ((val = parameters.getSearchMethodParameters().get("SingleParameter_numScs")) != null) {
			numScs = Integer.valueOf(val);
		}
		if ((val = parameters.getSearchMethodParameters().get("SingleParameter_paramName")) != null) {
			paramName = val;
		}
		if ((val = parameters.getSearchMethodParameters().get("SingleParameter_paramName2")) != null) {
			paramName2 = val;
		}
		if ((val = parameters.getSearchMethodParameters().get("SingleParameter_numScs2")) != null) {
			numScs2 = Integer.valueOf(val);
			numScs2_spec = true;
		}
		if (!graph.getParameterMap().containsKey(paramName) || (!paramName2.equals("") && !graph.getParameterMap().containsKey(paramName2))) {
			throw new IllegalArgumentException("Invalid parameter name " + paramName + ".");
		}
		if (!numScs2_spec) {
			numScs2 = numScs;
		}		
		param = graph.getParameterMap().get(paramName);
		if (paramName2.equals("")) {
			param2 = null;
		} else {
			param2 = graph.getParameterMap().get(paramName2);
		}
		System.out.println("Param: " + paramName + " (" + param.getDomain().toString() + ") with " + numScs + " solver configs");
		if (param2 != null)
			System.out.println("Param2: " + paramName2 + " (" + param2.getDomain().toString() + ") with " + numScs2 + " solver configs.");
		generated_scs = false;
	}

	@Override
	public List<SolverConfiguration> generateNewSC(int num, SolverConfiguration currentBestSC) throws Exception {
		LinkedList<SolverConfiguration> scs = new LinkedList<SolverConfiguration>();
		if (generated_scs) {
			return scs;
		}
		generated_scs = true;
		if (param2 == null) {
			System.out.println("Generating " + numScs + " solver configs..");
			for (Object val : param.getDomain().getUniformDistributedValues(numScs)) {
				ParameterConfiguration pConfig = new ParameterConfiguration(currentBestSC.getParameterConfiguration());
				pConfig.setParameterValue(param, val);
				int idSc = api.createSolverConfig(parameters.getIdExperiment(), pConfig, "Value: " + val);
				scs.add(new SolverConfiguration(idSc, pConfig, parameters.getStatistics()));
			}
		} else {
			System.out.println("Generating " + numScs + "x" + numScs2 + " = " + (numScs*numScs2) + " solver configs..");
			for (Object val : param.getDomain().getUniformDistributedValues(numScs)) {
				for (Object val2 : param2.getDomain().getUniformDistributedValues(numScs2)) {
					ParameterConfiguration pConfig = new ParameterConfiguration(currentBestSC.getParameterConfiguration());
					pConfig.setParameterValue(param, val);
					pConfig.setParameterValue(param2, val2);
					int idSc = api.createSolverConfig(parameters.getIdExperiment(), pConfig, "Values: " + val + "," + val2);
					scs.add(new SolverConfiguration(idSc, pConfig, parameters.getStatistics()));
				}
			}
		}
		System.out.println(".. done.");
		return scs;
	}

	@Override
	public void listParameters() {
	}

}
