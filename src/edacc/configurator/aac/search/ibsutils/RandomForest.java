package edacc.configurator.aac.search.ibsutils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import edacc.api.costfunctions.CostFunction;
import edacc.parameterspace.ParameterConfiguration;
import edacc.util.Pair;
import edacc.model.ExperimentResult;

public class RandomForest {
	List<DecisionTree> forest;
	CostFunction costFunc;
	Random rng;
	public RandomForest(CostFunction costFunc, Random rng, int treeCount) {
		this.costFunc = costFunc;
		forest = new LinkedList<DecisionTree>();
		this.rng = rng;
	}
	
	public Double getCost(ParameterConfiguration paramConfig) {
		List<ExperimentResult> results = new LinkedList<ExperimentResult>();
		/*for (RandomTree tree : forest) {
			List<ExperimentResult> tmp = tree.getResults(paramConfig);
			if (tmp != null) {
				results.addAll(tmp);
			}
		}
		if (results.isEmpty()) {
			return null;
		}
		System.out.println("RETURNING PREDICTION FOR " + results.size() + " results: " + costFunc.calculateCost(results));*/
		return costFunc.calculateCost(results);
	}
	
	public void train(ArrayList<Pair<ParameterConfiguration, List<ExperimentResult>>> trainData) {
		//for (int i = 0; i < 10; i++)
	//	forest.add(new RandomTree(rng, 100, trainData));
	}
}
