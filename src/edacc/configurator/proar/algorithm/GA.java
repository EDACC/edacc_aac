package edacc.configurator.proar.algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.proar.SolverConfiguration;
import edacc.configurator.proar.StatisticFunction;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;
import edacc.util.Pair;

public class GA extends PROARMethods {
	private ParameterGraph graph;
	private List<Individual> population;
	private List<SolverConfiguration> oldScs;
	HashSet<byte[]> checksums;
	private int limit;
	
	private float probN = 0.6f;
	private int maxAge = 10;
	private float mutationProb = 0.1f;
	private float crossoverPercentage = 0.6f;
	private int childCountLimit = 12;
	
	public GA(API api, int idExperiment, StatisticFunction statistics, Random rng, Map<String, String> params) throws Exception {
		super(api, idExperiment, statistics, rng, params);
		graph = api.loadParameterGraphFromDB(idExperiment);
		population = new ArrayList<Individual>();
		oldScs = new ArrayList<SolverConfiguration>();
		checksums = new HashSet<byte[]>();
		limit = 0;
		
		String val;
		
		if ((val = params.get("GA_probN")) != null) {
			probN = Float.parseFloat(val);
		}
		if ((val = params.get("GA_maxAge")) != null) {
			maxAge = Integer.parseInt(val);
		}
		if ((val = params.get("GA_mutationProb")) != null) {
			mutationProb = Float.parseFloat(val);
		}
		if ((val = params.get("GA_crossoverPercentage")) != null) {
			crossoverPercentage = Float.parseFloat(val);
		}
		if ((val = params.get("GA_childCountLimit")) != null) {
			childCountLimit = Integer.parseInt(val);
		}
		
		if (probN < 0.f || probN > 1.f || maxAge < 0 || mutationProb < 0.f || mutationProb > 1.f || crossoverPercentage < 0.f || crossoverPercentage > 1.f || childCountLimit < 0)  {
			throw new IllegalArgumentException();
		}
		System.out.println("[GA] probN: " + probN);
		System.out.println("[GA] maxAge: " + maxAge);
		System.out.println("[GA] mutationProb: " + mutationProb);
		System.out.println("[GA] crossoverPercentage: " + crossoverPercentage);
		System.out.println("[GA] childCountLimit: " + childCountLimit);
	}

	@Override
	public List<SolverConfiguration> generateNewSC(int num, List<SolverConfiguration> lastBestSCs, SolverConfiguration currentBestSC, int level, int currentLevel) throws Exception {
		limit = Math.max(limit, num);

		System.out.println("[GA] GA generate Solver configs: " + num);
		System.out.println("[GA] Population size: " + population.size());
		for (int i = population.size()-1; i >= 0; i--) {
			if (population.get(i).getAge(currentLevel -1) > maxAge || population.get(i).getChildCount() > population.get(i).getMaxChildCount()) {
				population.remove(i);
			}
		}
		int index = 0;

		while (oldScs.size() > index) {
			SolverConfiguration sc = oldScs.get(index);
			if (sc.getLevel() != currentLevel -1) {
				index++;
				continue;
			}
			oldScs.remove(index);

			if (sc.getNumSuccessfulJobs() < 4) {
				continue;
			}
			int maxChildCount = Math.min(sc.getNumSuccessfulJobs() / 2, childCountLimit);
			if (maxChildCount == 0) {
				continue;
			}
			Individual cur = new Individual(sc, maxChildCount);
			System.out.println("[GA] Created individual with max child count " + maxChildCount);
			if (population.size() < limit) {
				population.add(cur);
			} else {
				for (int i = 0; i < population.size(); i++) {
					if (cur.getSolverConfig().getNumSuccessfulJobs() > population.get(i).getSolverConfig().getNumSuccessfulJobs()) {
						Individual tmp = population.get(i);
						population.set(i, cur);
						cur = tmp;
					}
				}
			}
		}
		System.out.println("[GA] Sorting solver configurations");
		Collections.shuffle(population);
		
		float avg = 0.f;
		for (Individual i : population) {
			avg += statistics.getCostFunction().calculateCost(i.getSolverConfig().getFinishedJobs());
		}
		
		avg /= population.size() != 0 ? population.size() : 1;

		System.out.println("[GA] Current population contains " + population.size() + " individuals.");
		System.out.println("[GA] Average cost is " + avg + ".");

		LinkedList<Individual> best = new LinkedList<Individual>();
		best.addAll(population);
		
		System.out.println("[GA] Generating solver configurations");
		LinkedList<SolverConfiguration> res = new LinkedList<SolverConfiguration>();
		while (res.size() < num - Math.ceil((1 - crossoverPercentage) * num) && best.size() >= 2) {
			Individual m = best.pollLast();
			
			int f_index = rng.nextInt(best.size());
			Individual f = best.get(f_index);
			best.remove(f_index);

			
			Pair<ParameterConfiguration, ParameterConfiguration> configs = graph.crossover(api.getParameterConfiguration(idExperiment, m.getSolverConfig().getIdSolverConfiguration()), api.getParameterConfiguration(idExperiment, f.getSolverConfig().getIdSolverConfiguration()), rng);

			int firstMutationCount = 0;
			int secondMutationCount = 0;
			
			if (rng.nextFloat() < mutationProb) {
				graph.mutateParameterConfiguration(rng, configs.getFirst());
				firstMutationCount++;
			}
			if (rng.nextFloat() < mutationProb) {
				graph.mutateParameterConfiguration(rng, configs.getSecond());
				secondMutationCount++;
			}
			
			while (true) {
				if (!checksums.contains(configs.getFirst().getChecksum())) {
					checksums.add(configs.getFirst().getChecksum());
					break;
				}
				if (firstMutationCount > 100) {
					break;
				}
				graph.mutateParameterConfiguration(rng, configs.getFirst());
				firstMutationCount++;
			}
			
			while (true) {
				if (!checksums.contains(configs.getSecond().getChecksum())) {
					checksums.add(configs.getSecond().getChecksum());
					break;
				}
				if (secondMutationCount > 100) {
					break;
				}
				graph.mutateParameterConfiguration(rng, configs.getSecond());
				secondMutationCount++;
			}
			
			m.incrementChildCount();
			f.incrementChildCount();

			int idSolverConfig = api.createSolverConfig(idExperiment, configs.getFirst(), api.getCanonicalName(idExperiment, configs.getFirst()) + " level " + level);
			SolverConfiguration firstSC = new SolverConfiguration(idSolverConfig, api.getParameterConfiguration(idExperiment, idSolverConfig), statistics, level);
			firstSC.setName("crossover - child " + m.getChildCount() + "/" + f.getChildCount() + (firstMutationCount != 0 ? " " + firstMutationCount + " mutations" : ""));
			res.add(firstSC);

			idSolverConfig = api.createSolverConfig(idExperiment, configs.getSecond(), api.getCanonicalName(idExperiment, configs.getSecond()) + " level " + level);
			SolverConfiguration secondSC = new SolverConfiguration(idSolverConfig, api.getParameterConfiguration(idExperiment, idSolverConfig), statistics, level);
			secondSC.setName("crossover - child " + m.getChildCount() + "/" + f.getChildCount() + (secondMutationCount != 0 ? " " + secondMutationCount + " mutations" : ""));
			res.add(secondSC);
		}

		while (res.size() != num) {
			boolean random = true;
			ParameterConfiguration paramconfig;
			if (currentBestSC != null && currentBestSC.getNumSuccessfulJobs() > 0 && rng.nextFloat() < probN) {
				random = false;
				paramconfig = graph.getRandomNeighbour(currentBestSC.getParameterConfiguration(), rng);
			} else {
				paramconfig = graph.getRandomConfiguration(rng);
			}
			int mutationCount = 0;
			while (true) {
				if (!checksums.contains(paramconfig.getChecksum())) {
					checksums.add(paramconfig.getChecksum());
					break;
				}
				if (mutationCount > 100) {
					break;
				}
				graph.mutateParameterConfiguration(rng, paramconfig);
				mutationCount ++;
			}
			
			int idSolverConfig = api.createSolverConfig(idExperiment, paramconfig, api.getCanonicalName(idExperiment, paramconfig) + " level " + level);
			SolverConfiguration randomConfig = new SolverConfiguration(idSolverConfig, api.getParameterConfiguration(idExperiment, idSolverConfig), statistics, level);
			randomConfig.setName((random ? "random" : "neighbour of " + currentBestSC.getIdSolverConfiguration()) + (mutationCount != 0 ? " " + mutationCount + " mutations" : ""));
			res.add(randomConfig);
		}
		oldScs.addAll(res);
		System.out.println("[GA] done.");
		System.out.println("[GA] Solver configurations generated (overall): " + checksums.size());
		return res;
	}

	private class Individual implements Comparable<Individual> {
		private SolverConfiguration sc;
		private int childCount;
		private int maxChildCount;
		
		public Individual(SolverConfiguration sc, int maxChildCount) {
			this.sc = sc;
			this.maxChildCount = maxChildCount;
			childCount = 0;
		}
		
		public int getChildCount() {
			return childCount;
		}
		
		public void incrementChildCount() {
			childCount++;
		}
		
		public SolverConfiguration getSolverConfig() {
			return sc;
		}
		
		public int getAge(int date) {
			return date - sc.getLevel();
		}
		
		public int getMaxChildCount() {
			return maxChildCount;
		}

		@Override
		public int compareTo(Individual arg0) {
			return sc.compareTo(arg0.sc);
		}
	}
}
