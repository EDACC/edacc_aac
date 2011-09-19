package edacc.configurator.proar.algorithm;

import java.util.ArrayList;
import java.util.Collections;
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
	public List<SolverConfiguration> generateNewSC(int num, List<SolverConfiguration> lastBestSCs, SolverConfiguration currentBestSC, int level, int currentLevel) throws Exception {
		limit = Math.max(limit, num);

		System.out.println("[GA] GA generate Solver configs: " + num);
		int index = 0;

		while (oldScs.size() > index) {
			SolverConfiguration cur = oldScs.get(index);
			if (cur.getLevel() != currentLevel -1) {
				index++;
				continue;
			}
			oldScs.remove(index);
			if (scList.size() < limit) {
				scList.add(cur);
			} else {
				for (int i = 0; i < scList.size(); i++) {
					if (cur.getJobCount() > scList.get(i).getJobCount() && cur.compareTo(scList.get(i)) >= 0) {
						SolverConfiguration tmp = scList.get(i);
						scList.set(i, cur);
						cur = tmp;
					}
				}
			}
		}
		System.out.println("[GA] Sorting solver configurations");
		Collections.sort(scList);
		
		float avg = 0.f;
		for (SolverConfiguration sc : scList) {
			avg += sc.getCost();
		}
		
		avg /= scList.size() != 0 ? scList.size() : 1;

		System.out.println("[GA] Current best list contains " + scList.size() + " solver configurations.");
		System.out.println("[GA] Average cost is " + avg + ".");

		List<Integer> best = new LinkedList<Integer>();
		for (SolverConfiguration sc : scList) {
			best.add(0, sc.getIdSolverConfiguration());
		}
		System.out.println("[GA] Generating solver configurations");
		LinkedList<SolverConfiguration> res = new LinkedList<SolverConfiguration>();
		while (res.size() < num - Math.ceil(0.1 * num) && best.size() >= 2) {
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
		System.out.println("[GA] done.");
		return res;
	}

}
