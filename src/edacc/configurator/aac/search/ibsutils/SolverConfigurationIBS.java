package edacc.configurator.aac.search.ibsutils;

import java.util.Set;

import edacc.configurator.aac.SolverConfiguration;
import edacc.configurator.aac.StatisticFunction;
import edacc.parameterspace.ParameterConfiguration;

public class SolverConfigurationIBS extends SolverConfiguration {

	public Set<Integer> preferredInstanceIds;
	
	public SolverConfigurationIBS(int idSolverConfiguration, ParameterConfiguration pc, StatisticFunction statFunc, Set<Integer> preferredInstanceIds) {
		super(idSolverConfiguration, pc, statFunc);
		
		this.preferredInstanceIds = preferredInstanceIds;
	}
}
