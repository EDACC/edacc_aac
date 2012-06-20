package edacc.configurator.aac.search;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;
import edacc.util.Pair;

public class GA extends SearchMethods {
	/**
	 * Cache the parameter graph, will be used often
	 */
	private ParameterGraph graph;

	/**
	 * Maps a solver config to an individual
	 */
	private HashMap<Integer, Individual> allIndividuals;
	/**
	 * Used to determine if a new solver config was already generated
	 */
	private HashSet<ParameterConfiguration> createdParamConfigs;
	/**
	 * If a solver configuration should be generated via random/random-neighbour search, with this probability random-neighbour search 
	 * will be used. random otherwise. (Maybe this should be adaptive)
	 */
	private float probN = 0.6f;
	/**
	 * @see time
	 */
	private int maxAge = 30;
	/**
	 * If we have individuals generated via crossover, then this is the probability whether to do a mutation afterwards or not. 
	 */
	private float mutationProb = 0.1f;
	/**
	 * a maximum of crossoverPercentage * (# solver configs to be generated) will be generated via crossover
	 * a minimum of (1-crossoverPercentage) * (# solver configs to be generated) will be generated via random/random-neighbour
	 */
	private float crossoverPercentage = 0.6f;
	/**
	 * maximum number of children for a solver configuration.
	 * calculation for each sc generated: min{(# successful runs) / 2, childCountLimit};
	 */
	private int childCountLimit = 120;
	/**
	 * Every individual has its family tree. This value determines how old the youngest common ancestor must be at a minimum.
	 */
	private int minSameAncestorAgeDiff = 70;
	
	// TODO: description + this should be a parameter
	private int numBestSCs = 10;
	
	/**
	 * Will be incremented by one for every newly generated individual. Is then used as its id and influences the hash value of
	 * the individual.
	 */
	private static int idCounter = 0;
	/**
	 * current time, used to see whether maxAge is reached.
	 * will be updated on every solver config generated. time := (#solver configs generated)/100
	 */
	private static int time = 0;
	/**
	 * Incremented by (# generated solver configs) after every generateNewSC call. 
	 */
	private static int genSC = 0;
	public GA(AAC pacc, API api, Random rng, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
		super(pacc, api, rng, parameters, firstSCs, referenceSCs);
		graph = api.loadParameterGraphFromDB(parameters.getIdExperiment());
		allIndividuals = new HashMap<Integer, Individual>();
		createdParamConfigs = new HashSet<ParameterConfiguration>();

		String val;
		if ((val = parameters.getSearchMethodParameters().get("GA_probN")) != null) {
			probN = Float.parseFloat(val);
		}
		if ((val = parameters.getSearchMethodParameters().get("GA_maxAge")) != null) {
			maxAge = Integer.parseInt(val);
		}
		if ((val = parameters.getSearchMethodParameters().get("GA_mutationProb")) != null) {
			mutationProb = Float.parseFloat(val);
		}
		if ((val = parameters.getSearchMethodParameters().get("GA_crossoverPercentage")) != null) {
			crossoverPercentage = Float.parseFloat(val);
		}
		if ((val = parameters.getSearchMethodParameters().get("GA_childCountLimit")) != null) {
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

	
	private Individual createIndividual(SolverConfiguration sc, int time, Individual m, Individual f) {
		Individual newInd = new Individual(sc,time);
		if (m != null) {
			newInd.ancestors.addAll(m.ancestors);
			newInd.ancestors.add(m);
		}
		if (f != null) {
			newInd.ancestors.addAll(f.ancestors);
			newInd.ancestors.add(f);
		}
		allIndividuals.put(sc.getIdSolverConfiguration(), newInd);
		return newInd;
	}
	
	@Override
	public List<SolverConfiguration> generateNewSC(int num) throws Exception {
		
		System.out.println("[GA] GA generate Solver configs: " + num);
		System.out.println("[GA] Population size: " + allIndividuals.size());
		
		// remove all individuals which died, i.e. they have enough children or the maxAge is reached.
		// TODO: ...
		/*for (int i = population.size()-1; i >= 0; i--) {
			if (population.get(i).getAge(time) > maxAge || population.get(i).getChildCount() > population.get(i).getMaxChildCount()) {
				population.remove(i);
			}
		}*/
		
		// determine which individuals can be used from the oldIndividuals-list. The corresponding solver config must be marked as
		// finished and they must have more than one run.
		/*int index = 0;
		while (oldIndividuals.size() > index) {
			Individual cur = oldIndividuals.get(index);
			if (!cur.getSolverConfig().isFinished()) {
				index++;
				continue;
			}
			oldIndividuals.remove(index);

			if (cur.getSolverConfig().getNumSuccessfulJobs() < 2) {
				continue;
			}
			int maxChildCount = Math.min(cur.getSolverConfig().getNumSuccessfulJobs() / 2, childCountLimit);
			if (maxChildCount == 0) {
				continue;
			}
			cur.maxChildCount = maxChildCount;
			//System.out.println("[GA] Created individual with max child count " + maxChildCount);
			if (cur.sc.getNumSuccessfulJobs() >= cur.sc.getJobCount() / 2) {
				population.add(cur);
			} 
		}
		System.out.println("[GA] Sorting solver configurations");
		// TODO: write a sort method for solver configurations!!
		// currently shuffling, but crossover "thinks" that the best solver configuration is the last one
		Collections.sort(population);
		
		float avg = 0.f;
		for (Individual i : population) {
			avg += parameters.getStatistics().getCostFunction().calculateCost(i.getSolverConfig().getFinishedJobs());
		}
		
		avg /= population.size() != 0 ? population.size() : 1;

		System.out.println("[GA] Current population contains " + population.size() + " individuals.");
		System.out.println("[GA] Average cost is " + avg + ".");
		
		
		LinkedList<Individual> best = new LinkedList<Individual>();
		best.addAll(population);*/
		
		System.out.println("[GA] Generating solver configurations");
		LinkedList<SolverConfiguration> bestSolverConfigs = new LinkedList<SolverConfiguration>(); 
		bestSolverConfigs.addAll(pacc.racing.getBestSolverConfigurations(numBestSCs));
		for (SolverConfiguration sc : bestSolverConfigs) {
			if (!allIndividuals.containsKey(sc.getIdSolverConfiguration())) {
				createIndividual(sc, time, null, null);
			}
		}
		
		LinkedList<SolverConfiguration> res = new LinkedList<SolverConfiguration>();
		
		int noPartner = 0;
		while (res.size() < Math.ceil(crossoverPercentage * num) && bestSolverConfigs.size() >= 2) {
			// use the best solver configuration and a random solver configuration for crossover
			// constraint: minSameAncestorAgeDiff must be satisfied
			Individual m = allIndividuals.get(bestSolverConfigs.poll().getIdSolverConfiguration());
			System.out.println("[GA] Looking for partner for " + m.getSolverConfig().getIdSolverConfiguration() + " which has " + m.getSolverConfig().getNumSuccessfulJobs() + " successful jobs..");
			Individual f = null;
			for (SolverConfiguration sc : bestSolverConfigs) {
				Individual ind = allIndividuals.get(sc.getIdSolverConfiguration());
				boolean goodPartner = true;
				for (Individual anc : ind.ancestors) {
					if (anc.getAge(time) < minSameAncestorAgeDiff && m.ancestors.contains(anc)) {
						goodPartner = false;
						break;
					}
					 
				}
				if (goodPartner) {
					f = ind;
					break;
				}
			}
			if (f == null) {
				// we didn't find any partner for this solver configuration
				// will do mutation on that one.
				noPartner++;
				System.out.println("[GA] Found no partner for " + m.getSolverConfig().getIdSolverConfiguration());
				ParameterConfiguration pConfig = new ParameterConfiguration(m.getSolverConfig().getParameterConfiguration()); 
				graph.mutateParameterConfiguration(rng, pConfig);
				int mutationCount = 1;
				// repeat mutation until we have a unique parameter configuration
				while (true) {
					if (!createdParamConfigs.contains(pConfig)) {
						createdParamConfigs.add(pConfig);
						break;
					}
					if (mutationCount > 100) {
						break;
					}
					graph.mutateParameterConfiguration(rng, pConfig);
					mutationCount++;
				}
				int idSolverConfig = api.createSolverConfig(parameters.getIdExperiment(), pConfig, api.getCanonicalName(parameters.getIdExperiment(), pConfig));
				SolverConfiguration sc = new SolverConfiguration(idSolverConfig, pConfig, parameters.getStatistics());
				sc.setNameSearch(mutationCount + " mutations");
				res.add(sc);
				createIndividual(sc, time, null, null);
				continue;
			}
			// TODO: works?
			bestSolverConfigs.remove(f.getSolverConfig());
			// found partner f: do crossover
			System.out.println("[GA] Crossover: " + m.getSolverConfig().getIdSolverConfiguration() + " with " + f.getSolverConfig().getIdSolverConfiguration() + " - successful runs: " + m.getSolverConfig().getNumSuccessfulJobs() + " - " + f.getSolverConfig().getNumSuccessfulJobs());
			Pair<ParameterConfiguration, ParameterConfiguration> configs = graph.crossover(m.getSolverConfig().getParameterConfiguration(), f.getSolverConfig().getParameterConfiguration(), rng);

			int firstMutationCount = 0;
			int secondMutationCount = 0;
			
			// determine if mutation should be done for each child
			if (rng.nextFloat() < mutationProb) {
				graph.mutateParameterConfiguration(rng, configs.getFirst());
				firstMutationCount++;
			}
			if (rng.nextFloat() < mutationProb) {
				graph.mutateParameterConfiguration(rng, configs.getSecond());
				secondMutationCount++;
			}
			
			// be sure that those children are unique
			while (true) {
				if (!createdParamConfigs.contains(configs.getFirst())) {
					createdParamConfigs.add(configs.getFirst());
					break;
				}
				if (firstMutationCount > 100) {
					break;
				}
				graph.mutateParameterConfiguration(rng, configs.getFirst());
				firstMutationCount++;
			}
			while (true) {
				if (!createdParamConfigs.contains(configs.getSecond())) {
					createdParamConfigs.add(configs.getSecond());
					break;
				}
				if (secondMutationCount > 100) {
					break;
				}
				graph.mutateParameterConfiguration(rng, configs.getSecond());
				secondMutationCount++;
			}
			
			// finally create the solver configurations
			// and add corresponding individuals to oldIndividuals list
			m.incrementChildCount();
			f.incrementChildCount();

			int idSolverConfig = api.createSolverConfig(parameters.getIdExperiment(), configs.getFirst(), api.getCanonicalName(parameters.getIdExperiment(), configs.getFirst()));
			SolverConfiguration firstSC = new SolverConfiguration(idSolverConfig, configs.getFirst(), parameters.getStatistics());
			
			res.add(firstSC);
			Individual newInd = createIndividual(firstSC, time, m, f);
			firstSC.setNameSearch("crossover - child " + m.getChildCount() + "/" + f.getChildCount() + (firstMutationCount != 0 ? " " + firstMutationCount + " mutations" : "") + " - " + newInd.ancestors.size() + " ancestors");
			
			idSolverConfig = api.createSolverConfig(parameters.getIdExperiment(), configs.getSecond(), api.getCanonicalName(parameters.getIdExperiment(), configs.getSecond()));
			SolverConfiguration secondSC = new SolverConfiguration(idSolverConfig, configs.getSecond(), parameters.getStatistics());
			res.add(secondSC);
			newInd = createIndividual(secondSC, time, m, f);
			secondSC.setNameSearch("crossover - child " + m.getChildCount() + "/" + f.getChildCount() + (secondMutationCount != 0 ? " " + secondMutationCount + " mutations" : "") + " - " + newInd.ancestors.size() + " ancestors");
		}
		if (noPartner > 0)
			System.out.println("[GA] Did not find a partner for " + noPartner + " individuals.");
		
		bestSolverConfigs.clear();
		bestSolverConfigs.addAll(pacc.racing.getBestSolverConfigurations(Math.min(numBestSCs, num - res.size())));
		
		while (res.size() != num) {
			// do random/random-neighbour search for other solver configs.
			ParameterConfiguration paramconfig;
			SolverConfiguration nSC = null;
			if (!bestSolverConfigs.isEmpty() && rng.nextFloat() < probN) {
				int r_index = 0;
				while (r_index < bestSolverConfigs.size()-1) {
					if (rng.nextFloat() < 0.25) {
						break;
					}
					r_index++;
				}
				nSC = bestSolverConfigs.get(r_index);
				bestSolverConfigs.remove(r_index);
				System.out.println("[GA] random neighbour of " + nSC.getIdSolverConfiguration() + " (" + (r_index+1) + ". config in best sc list)");
				paramconfig = graph.getRandomNeighbour(nSC.getParameterConfiguration(), rng);
			} else {
				System.out.println("[GA] random solver configuration");
				paramconfig = graph.getRandomConfiguration(rng);
			}
			int mutationCount = 0;
			while (true) {
				if (!createdParamConfigs.contains(paramconfig)) {
					createdParamConfigs.add(paramconfig);
					break;
				}
				if (mutationCount > 100) {
					break;
				}
				graph.mutateParameterConfiguration(rng, paramconfig);
				mutationCount ++;
			}
			
			int idSolverConfig = api.createSolverConfig(parameters.getIdExperiment(), paramconfig, api.getCanonicalName(parameters.getIdExperiment(), paramconfig));
			SolverConfiguration randomConfig = new SolverConfiguration(idSolverConfig, paramconfig, parameters.getStatistics());
			res.add(randomConfig);
			Individual newInd;
			if (nSC == null) {
				// random config
				newInd = createIndividual(randomConfig, time, null, null);
			} else {
				// neighbor of nsc
				newInd = createIndividual(randomConfig, time, allIndividuals.get(nSC.getIdSolverConfiguration()), null);
			}
			
			randomConfig.setNameSearch((nSC == null ? "random" : "neighbour of " + nSC.getIdSolverConfiguration()) 
					+ (mutationCount != 0 ? " " + mutationCount + " mutations" : "")
					+ (newInd.ancestors.size() != 0 ? " - " + newInd.ancestors.size() + " ancestors" : ""));
		}
		
		System.out.println("[GA] done.");
		System.out.println("[GA] Solver configurations generated (overall): " + createdParamConfigs.size());
		
		genSC += res.size();
		time = genSC / 100;
		return res;
	}

	private class Individual implements Comparable<Individual> {
		private int id;
		private SolverConfiguration sc;
		private int childCount;
		private int maxChildCount;
		private Set<Individual> ancestors;
		private int birthday;
		public Individual(SolverConfiguration sc, int birthday) {
			this.sc = sc;
			this.maxChildCount = 0;
			childCount = 0;
			this.ancestors = new HashSet<Individual>();
			id = idCounter++;
			this.birthday = birthday;
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
			return date - birthday;
		}
		
		public int getMaxChildCount() {
			return maxChildCount;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + id;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Individual other = (Individual) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (id != other.id)
				return false;
			return true;
		}

		@Override
		public int compareTo(Individual arg0) {
			//return sc.compareTo(arg0.sc);
			return sc.getNumSuccessfulJobs() - arg0.getSolverConfig().getNumSuccessfulJobs();
		}

		private GA getOuterType() {
			return GA.this;
		}
	}

	@Override
	public List<String> getParameters() {
		// TODO: implement
		return new LinkedList<String>();		
	}


	@Override
	public void searchFinished() {
		// TODO Auto-generated method stub
		
	}
}
