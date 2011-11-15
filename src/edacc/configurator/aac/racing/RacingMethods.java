package edacc.configurator.aac.racing;

import java.util.List;

import edacc.api.API;
import edacc.configurator.aac.PROAR;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;

public abstract class RacingMethods {	
	API api;
	PROAR proar;
	Parameters parameters;
	public RacingMethods(PROAR proar, API api, Parameters parameters) {
		this.proar = proar;
		this.api = api;
		this.parameters = parameters;
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
}
