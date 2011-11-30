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
	public RacingMethods(AAC pacc, Random rng, API api, Parameters parameters) {
		this.pacc = pacc;
		this.api = api;
		this.parameters = parameters;
		this.rng = rng;
		this.numCompCalls = 0;
		
	}
	public abstract int compareTo(SolverConfiguration sc1, SolverConfiguration sc2);
	public abstract void initFirstSC(SolverConfiguration firstSC) throws Exception;
	public abstract SolverConfiguration getBestSC();
	public abstract void solverConfigurationsFinished(List<SolverConfiguration> scs) throws Exception;
	public abstract void solverConfigurationsCreated(List<SolverConfiguration> scs) throws Exception;
	/**
	 * Determines how many new solver configuration can be taken into
	 * consideration.
	 * 
	 * @throws Exception
	 */
	public abstract int computeOptimalExpansion(int computationCoreCount, int computationJobCount, int listNewSCSize);
	
	public abstract String toString();
	public int getNumCompCalls(){
		return this.numCompCalls;
	}
}
