package edacc.configurator.proar.algorithm;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.proar.SolverConfiguration;
import edacc.configurator.proar.StatisticFunction;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;
import edacc.util.Pair;

public class GA extends PROARMethods {
	private ParameterGraph graph;
	private List<SolverConfiguration> scList;
	public GA(API api, int idExperiment, StatisticFunction statistics, Random rng) throws Exception {
		super(api, idExperiment, statistics, rng);
		graph = api.loadParameterGraphFromDB(idExperiment);
		scList = new ArrayList<SolverConfiguration>();
	}

	@Override
	public List<SolverConfiguration> generateNewSC(int num, List<SolverConfiguration> lastBestSCs, SolverConfiguration currentBestSC, int level) throws Exception {
		List<Integer> best = new LinkedList<Integer>();
		System.out.println("GA generate Solver configs: " + num);
		while (!lastBestSCs.isEmpty()) {
			SolverConfiguration cur = lastBestSCs.get(0);
			lastBestSCs.remove(0);
			if (scList.size() < num) {
				scList.add(cur);
			} else {
				for (int i = 0; i < scList.size(); i++) {
					if (cur.compareTo(scList.get(i)) >= 0) {
						scList.set(i, cur);
						break;
					}
				}
			}
		}
		for (SolverConfiguration sc : scList) {
			best.add(sc.getIdSolverConfiguration());
		}
		
		LinkedList<SolverConfiguration> res = new LinkedList<SolverConfiguration>();
		while (res.size() < num-2 && best.size() >= 2) {
			Pair<ParameterConfiguration, ParameterConfiguration> configs = graph.crossover(api.getParameterConfiguration(idExperiment, best.get(0)), api.getParameterConfiguration(idExperiment, best.get(1)), rng);
			
			if (rng.nextFloat() < 0.01) {
				graph.mutateParameterConfiguration(rng, configs.getFirst());
			}
			if (rng.nextFloat() < 0.01) {
				graph.mutateParameterConfiguration(rng, configs.getSecond());
			}
			while (api.exists(idExperiment, configs.getFirst()) != 0) {
				graph.mutateParameterConfiguration(rng, configs.getFirst());
			}
			while (api.exists(idExperiment, configs.getSecond()) != 0) {
				graph.mutateParameterConfiguration(rng, configs.getSecond());
			}
			
			int idSolverConfig = api.createSolverConfig(idExperiment, configs.getFirst(), api.getCanonicalName(idExperiment, configs.getFirst()) + " level " + level);
			res.add(new SolverConfiguration(idSolverConfig, api.getParameterConfiguration(idExperiment, idSolverConfig), statistics, level));
			
			idSolverConfig = api.createSolverConfig(idExperiment, configs.getSecond(), api.getCanonicalName(idExperiment, configs.getSecond()) + " level " + level);
			res.add(new SolverConfiguration(idSolverConfig, api.getParameterConfiguration(idExperiment, idSolverConfig), statistics, level));
				
			best.remove(0);
			best.remove(0);
		}
		
		while (res.size() != num) {
			ParameterConfiguration paramconfig = graph.getRandomConfiguration(rng);
			int idSolverConfig = api.createSolverConfig(idExperiment, paramconfig, api.getCanonicalName(idExperiment, paramconfig) + " level " + level);
			res.add(new SolverConfiguration(idSolverConfig, api.getParameterConfiguration(idExperiment, idSolverConfig), statistics, level));
		}
		return res;
	}

}
