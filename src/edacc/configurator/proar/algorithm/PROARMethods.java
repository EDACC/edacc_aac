package edacc.configurator.proar.algorithm;

import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.proar.SolverConfiguration;
import edacc.configurator.proar.StatisticFunction;

public abstract class PROARMethods {
	protected API api;
	protected int idExperiment;
	protected Random rng;
	protected StatisticFunction statistics;
	
	public PROARMethods(API api, int idExperiment, StatisticFunction statistics, Random rng) {
		this.api = api;
		this.idExperiment = idExperiment;
		this.statistics = statistics;
		this.rng = rng;
	}
	
	/**
	 * Generates num new solver configurations
	 * 
	 * @return a List of the new solver configurations
	 * @throws Exception
	 */
	public abstract List<SolverConfiguration> generateNewSC(int num, List<SolverConfiguration> lastBestSCs, SolverConfiguration currentBestSC, int level) throws Exception;
}
