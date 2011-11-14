package edacc.configurator.proar.algorithm;

import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.proar.Parameters;
import edacc.configurator.proar.SolverConfiguration;

public abstract class PROARMethods {
	protected API api;
	protected Random rng;
	protected Parameters parameters;
	
	public PROARMethods(API api, Random rng, Parameters parameters) {
		this.api = api;
		this.parameters = parameters;
		this.rng = rng;
	}
	
	/**
	 * Generates num new solver configurations
	 * 
	 * @return a List of the new solver configurations
	 * @throws Exception
	 */
	public abstract List<SolverConfiguration> generateNewSC(int num, SolverConfiguration currentBestSC) throws Exception;
}
