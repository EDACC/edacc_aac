package edacc.configurator.proar.algorithm;

import java.util.List;
import java.util.Map;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.proar.SolverConfiguration;
import edacc.configurator.proar.StatisticFunction;

public abstract class PROARMethods {
	protected API api;
	protected int idExperiment;
	protected Random rng;
	protected StatisticFunction statistics;
	protected Map<String, String> params;
	
	public PROARMethods(API api, int idExperiment, StatisticFunction statistics, Random rng, Map<String, String> params) {
		this.api = api;
		this.idExperiment = idExperiment;
		this.statistics = statistics;
		this.rng = rng;
		this.params = params;
	}
	
	/**
	 * Generates num new solver configurations
	 * 
	 * @return a List of the new solver configurations
	 * @throws Exception
	 */
	public abstract List<SolverConfiguration> generateNewSC(int num, List<SolverConfiguration> lastBestSCs, SolverConfiguration currentBestSC, int level, int currentLevel) throws Exception;
}
