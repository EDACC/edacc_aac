package edacc.configurator.proar.racing;

import java.util.List;

import edacc.api.API;
import edacc.configurator.proar.PROAR;
import edacc.configurator.proar.Parameters;
import edacc.configurator.proar.SolverConfiguration;

public abstract class PROARRacing {	
	API api;
	PROAR proar;
	Parameters parameters;
	public PROARRacing(PROAR proar, API api, Parameters parameters) {
		this.proar = proar;
		this.api = api;
		this.parameters = parameters;
	}
	public abstract int compareTo(SolverConfiguration sc1, SolverConfiguration sc2);
	public abstract void initFirstSC(SolverConfiguration firstSC) throws Exception;
	public abstract SolverConfiguration getBestSC();
	public abstract void solverConfigurationsFinished(List<SolverConfiguration> scs) throws Exception;
	public abstract void solverConfigurationsCreated(List<SolverConfiguration> scs) throws Exception;
}
