package edacc.configurator.aac.search;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;

public class IDLE extends SearchMethods {

	public IDLE(AAC pacc, API api, Random rng, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
		super(pacc, api, rng, parameters, firstSCs, referenceSCs);
	}
	
	@Override
	public List<SolverConfiguration> generateNewSC(int num) throws Exception {
		Thread.sleep(1000);
		return new LinkedList<SolverConfiguration>();
	}

	@Override
	public List<String> getParameters() {
		return new LinkedList<String>();		
	}

	@Override
	public void searchFinished() {
		// TODO Auto-generated method stub
		
	}

}
