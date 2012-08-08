package edacc.configurator.aac.search;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;

public class InstanceBasedSearching extends SearchMethods {

	public InstanceBasedSearching(AAC pacc, API api, Random rng, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) {
		super(pacc, api, rng, parameters, firstSCs, referenceSCs);
		// TODO Auto-generated constructor stub
	}

	@Override
	public List<SolverConfiguration> generateNewSC(int num) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getParameters() {
		LinkedList<String> res = new LinkedList<String>();
		return res;
	}

	@Override
	public void searchFinished() {
		// TODO Auto-generated method stub

	}

}
