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
	private List<SolverConfiguration> oldScs;
	private int limit;

	public GA(API api, int idExperiment, StatisticFunction statistics, Random rng) throws Exception {
		super(api, idExperiment, statistics, rng);
		graph = api.loadParameterGraphFromDB(idExperiment);
		scList = new ArrayList<SolverConfiguration>();
		oldScs = new ArrayList<SolverConfiguration>();
		limit = 0;
	}

	@Override
	public List<SolverConfiguration> generateNewSC(int num, List<SolverConfiguration> lastBestSCs, SolverConfiguration currentBestSC, int level) throws Exception {
		limit = Math.max(limit, num);

		List<Integer> best = new LinkedList<Integer>();
		System.out.println("GA generate Solver configs: " + num);
		int index = 0;

		int minLevel = level;
		for (SolverConfiguration sc : oldScs) {
			minLevel = Math.min(sc.getLevel(), level);
		}
		if (minLevel != level) {
			while (oldScs.size() > index) {
				SolverConfiguration cur = oldScs.get(index);
				if (cur.getLevel() != minLevel) {
					index++;
					continue;
				}
				oldScs.remove(index);
				if (scList.size() < limit) {
					scList.add(cur);
				} else {
					for (int i = 0; i < scList.size(); i++) {
						if (cur.compareTo(scList.get(i)) >= 0) {
							SolverConfiguration tmp = scList.get(i);
							scList.set(i, cur);
							cur = tmp;
						}
					}
				}
			}
		}
		float avg = 0.f;
		for (SolverConfiguration sc : scList) {
			avg += sc.getCost();
		}
		avg /= scList.size() != 0 ? scList.size() : 1;

		System.out.println("Current best list contains " + scList.size() + " solver configurations.");
		System.out.println("Average cost is " + avg + ".");

		for (SolverConfiguration sc : scList) {
			best.add(sc.getIdSolverConfiguration());
		}

		LinkedList<SolverConfiguration> res = new LinkedList<SolverConfiguration>();
		while (res.size() < num - 2 && best.size() >= 2) {
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
		oldScs.addAll(res);
		return res;
	}

}
