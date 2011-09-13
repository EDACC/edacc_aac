package edacc.configurator.proar;

import java.util.List;

import edacc.api.API;
import edacc.api.API.COST_FUNCTIONS;
import edacc.model.ExperimentResult;
public class StatisticFunction {
	private API.COST_FUNCTIONS func;
	private boolean minimize;
	
	/**
	 * Constructs new StatisticFunction object. Throws IllegalArgumentException if <code>func</code> is <code>null</code>.
	 * @param func
	 * @param minimize
	 */
	public StatisticFunction(API.COST_FUNCTIONS func, boolean minimize) {
		if (func == null) {
			throw new IllegalArgumentException("COST_FUNCTION 'null' is invalid!");
		}
		this.func = func;
		this.minimize = minimize;
	}
	
	/**
	 * Compares the first list of <code>ExperimentResult</code> with the second list of <code>ExperimentResult</code>.<br/>
	 * Returns -1, 0, 1, if the calculated cost of the first list is worse, equal, or better compared to the calculated cost of<br/>
	 * the second list under consideration of the <code>COST_FUNCTION</code> and the <code>minimize</code>-attribute.
	 * @param first
	 * @param second
	 * @return
	 */
	public int compare(List<ExperimentResult> first, List<ExperimentResult> second) {
		float first_cost = func.calculateCost(first);
		float second_cost = func.calculateCost(second);
		if (first_cost == second_cost) {
			return 0;
		} else if (first_cost > second_cost) {
			return minimize ? 0 : 1;
		} else {
			return minimize ? 1 : 0;
		}
	}

	public COST_FUNCTIONS getCostFunction() {
		return func;
	}
}
