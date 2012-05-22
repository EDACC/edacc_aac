package edacc.configurator.aac.racing;

import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;

public abstract class RacingMethods {	
	API api;
	AAC pacc;
	Parameters parameters;
	protected Random rng;
	protected int numCompCalls;
	List<SolverConfiguration> firstSCs;
	List<SolverConfiguration> referenceSCs;
	
	public RacingMethods(AAC pacc, Random rng, API api, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) {
		this.pacc = pacc;
		this.api = api;
		this.parameters = parameters;
		this.rng = rng;
		this.numCompCalls = 0;
		this.firstSCs = firstSCs;
		this.referenceSCs = referenceSCs;
	}
	
	/**
	 * Compares solver configuration <code>sc1</code> to solver configuration <code>sc2</code> using the statistics </br>
	 * of this racing method. </br>
	 * </br>
	 * Returns -1 if <code>sc2</code> is better than <code>sc1</code>.</br>
	 * Returns 0 if there aren't any significant differences between the solver configurations.</br>
	 * Returns 1 if <code>sc1</code> is better than <code>sc2</code>.
	 * @param sc1
	 * @param sc2
	 * @return
	 */
	public abstract int compareTo(SolverConfiguration sc1, SolverConfiguration sc2);
	
	/**
	 * Returns the <code>numSC</code> best solver configurations found so far.</br>
	 * Returns an empty list if there aren't any.
	 * @param numSC
	 * @return
	 */
	public final List<SolverConfiguration> getBestSolverConfigurations(Integer numSC) {
		List<SolverConfiguration> scs = getBestSolverConfigurations();
		if (numSC != null) {
			while (scs.size() > numSC) {
				scs.remove(scs.size()-1);
			}
		}
		return scs;
	}
	
	/**
	 * Returns all best solver configurations found so far.</br>
	 * Returns an empty list if there aren't any.
	 * @param numSC
	 * @return
	 */
	public abstract List<SolverConfiguration> getBestSolverConfigurations();
	/**
	 * Called as soon as some solver configurations have completed their runs.
	 * @param scs
	 * @throws Exception
	 */
	public abstract void solverConfigurationsFinished(List<SolverConfiguration> scs) throws Exception;
	
	/**
	 * Called as soon as some solver configurations were created and should be raced.
	 * @param scs
	 * @throws Exception
	 */
	public abstract void solverConfigurationsCreated(List<SolverConfiguration> scs) throws Exception;
	/**
	 * Determines how many new solver configuration can be taken into
	 * consideration.
	 * 
	 * @throws Exception
	 */
	public abstract int computeOptimalExpansion(int computationCoreCount, int computationJobCount, int listNewSCSize);
	
	/**
	 * Stops the evaluation for the given solver configuration.
	 * 
	 * @param scs
	 * @throws Exception
	 */
	public abstract void stopEvaluation(List<SolverConfiguration> scs) throws Exception;
	
	public abstract void listParameters();
	public int getNumCompCalls(){
		return this.numCompCalls;
	}
}
