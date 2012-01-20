package edacc.configurator.aac.search;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.configurator.aac.StatisticFunction;
import edacc.parameterspace.graph.ParameterGraph;
import edacc.parameterspace.*;
import edacc.util.Pair;
public class IterativeSearchSpaceSampling extends SearchMethods {

	private ParameterGraph graph;
	private List<Parameter> graphParams;
	private int numInitSamples = 5;
	private LinkedList<Object>[] paramValues;
	private LinkedList<Pair<Object, Object>>[] paramValuesPrevNext;
	private int iteration = 0;
	private HashMap<ObjectArrayWrapper, SolverConfiguration> solverConfigs;
	private LinkedList<SolverConfiguration> lastSolverConfigs;
	public IterativeSearchSpaceSampling(AAC pacc, API api, Random rng, Parameters parameters) throws Exception {
		super(pacc, api, rng, parameters);
		this.graph = api.loadParameterGraphFromDB(parameters.getIdExperiment());
		graphParams = api.getConfigurableParameters(parameters.getIdExperiment());
		paramValues = new LinkedList[graphParams.size()];
		paramValuesPrevNext = new LinkedList[graphParams.size()];
		solverConfigs = new HashMap<ObjectArrayWrapper, SolverConfiguration>();
		lastSolverConfigs = new LinkedList<SolverConfiguration>();
		for (int i = 0; i < graphParams.size(); i++) {
			paramValues[i] = new LinkedList<Object>();
			paramValues[i].addAll(graphParams.get(i).getDomain().getUniformDistributedValues(numInitSamples));
			paramValuesPrevNext[i] = new LinkedList<Pair<Object, Object>>();
			for (int j = 0; j < paramValues[i].size(); j++) {
				Pair<Object, Object> p = new Pair<Object, Object>(j > 0 ? paramValues[i].get(j-1) : null, j+1 < paramValues[i].size() ? paramValues[i].get(j+1) : null);
				paramValuesPrevNext[i].add(p);
			}
		}
	}

	@Override
	public List<SolverConfiguration> generateNewSC(int num, SolverConfiguration currentBestSC) throws Exception {
		boolean iterationFinished = true;
		for (SolverConfiguration sc : lastSolverConfigs) {
			if (!sc.isFinished()) {
				iterationFinished = false;
				break;
			}
		}
		if (!iterationFinished) {
			return new LinkedList<SolverConfiguration>();
		}
		
		
		
		LinkedList<SolverConfiguration> goodSolverConfigs = new LinkedList<SolverConfiguration>();
		if (iteration > 0) {
			long time = System.currentTimeMillis();
			if (pacc.racing instanceof edacc.configurator.aac.racing.FRace) {
				goodSolverConfigs.addAll(((edacc.configurator.aac.racing.FRace) pacc.racing).getRaceSurvivors());
			} else {
				Collections.sort(lastSolverConfigs, new Comparator<SolverConfiguration>() {

					@Override
					public int compare(SolverConfiguration arg0, SolverConfiguration arg1) {
						return pacc.racing.compareTo(arg0, arg1);

					}

				});
				int goodSolverConfigSize = 2*graphParams.size();
				if (goodSolverConfigSize == 0) {
					// finished search.
					return new LinkedList<SolverConfiguration>();
				}
				for (int i = lastSolverConfigs.size() - 1; i >= lastSolverConfigs.size() - goodSolverConfigSize && i >= 0; i--) {
					goodSolverConfigs.add(lastSolverConfigs.get(i));
					pacc.log("[ISSS] Good solver config: " + api.getCanonicalName(parameters.getIdExperiment(), lastSolverConfigs.get(i).getParameterConfiguration()));
				}
			}
			pacc.log("[IS�] Iteration #" + iteration + ": found " + goodSolverConfigs.size() + " good solver configs");
			pacc.log("[IS�] Finding good solver configs took " + (System.currentTimeMillis() - time) + " ms");
			
		} else {
			pacc.log("[IS�] Sampling the search space, first iteration");
		}
		
		ParameterConfiguration base = graph.getRandomConfiguration(rng);
		LinkedList<SolverConfiguration> newSCs = new LinkedList<SolverConfiguration>();
		int[] p_index = new int[graphParams.size()];
		for (int i = 0; i < graphParams.size(); i++) {
			p_index[i] = 0;
		}
		if (iteration == 0) {
			long time = System.currentTimeMillis();
			while (p_index[0] < paramValues[0].size()) {
				ParameterConfiguration pconfig = new ParameterConfiguration(base);
				Object[] paramVal = new Object[graphParams.size()];
				for (int i = 0; i < graphParams.size(); i++) {
					paramVal[i] = paramValues[i].get(p_index[i]);
				}

				for (int i = 0; i < graphParams.size(); i++) {
					pconfig.setParameterValue(graphParams.get(i), paramVal[i]);
				}

				int idSolverConfig = api.createSolverConfig(parameters.getIdExperiment(), pconfig, "just created");
				SolverConfiguration sc = new SolverConfiguration(idSolverConfig, pconfig, parameters.getStatistics());
				newSCs.add(sc);
				solverConfigs.put(new ObjectArrayWrapper(paramVal), sc);

				p_index[graphParams.size() - 1]++;
				for (int i = graphParams.size() - 1; i >= 0; i--) {
					if (p_index[i] < paramValues[i].size()) {
						break;
					}
					if (i > 0) {
						p_index[i - 1]++;
						p_index[i] = 0;
					}
				}
			}
			pacc.log("[IS�] Generating initial solver configurations took " + (System.currentTimeMillis() - time) + " ms");
		} else {
			long time = System.currentTimeMillis();
			
			LinkedList<HashMap<Object, Pair<Object, Object>>> paramValuesPrevNextMaps = new LinkedList<HashMap<Object, Pair<Object, Object>>>();
			for (int i = 0; i < graphParams.size(); i++) {
				HashMap<Object, Pair<Object, Object>> map = new HashMap<Object, Pair<Object, Object>>();
				paramValuesPrevNextMaps.add(map);
				for (int j = 0; j < paramValues[i].size(); j++) {
					map.put(paramValues[i].get(j), paramValuesPrevNext[i].get(j));
				}
			}
			
			for (SolverConfiguration goodSC : goodSolverConfigs) {
				Object[] paramVal = new Object[graphParams.size()];
				Pair<Object, Object>[] paramValPrevNext = new Pair[graphParams.size()];
				for (int i = 0; i < graphParams.size(); i++) {
					paramVal[i] = goodSC.getParameterConfiguration().getParameterValue(graphParams.get(i));
					paramValPrevNext[i] = paramValuesPrevNextMaps.get(i).get(paramVal[i]);
				}

				int[] state = new int[graphParams.size()];
				for (int i = 0; i < state.length; i++) {
					state[i] = 0;
				}
				// there are 3^(# params) solver configs to check

				Object[] otherScPVal = new Object[graphParams.size()];

				while (state[0] < 2) {
					boolean valid = true;
					for (int i = 0; i < graphParams.size(); i++) {
						switch (state[i]) {
						case 0: {
							otherScPVal[i] = paramValPrevNext[i].getFirst();
							break;
						}
						case 1: {
							otherScPVal[i] = paramVal[i];
							break;
						}
						case 2: {
							otherScPVal[i] = paramValPrevNext[i].getSecond();
							break;
						}
						default: {
							throw new IllegalArgumentException("case " + state[i] + " is unknown.");
						}
						}
						if (otherScPVal[i] == null) {
							valid = false;
							break;
						}
					}
					if (valid) {
						if (!solverConfigs.containsKey(new ObjectArrayWrapper(otherScPVal))) {
							// generate a new solver config.
							
							ParameterConfiguration pconfig = new ParameterConfiguration(base);
							for (int i = 0; i < graphParams.size(); i++) {
								pconfig.setParameterValue(graphParams.get(i), otherScPVal[i]);
							}
							int idSolverConfig = api.createSolverConfig(parameters.getIdExperiment(), pconfig, "just created");
							SolverConfiguration sc = new SolverConfiguration(idSolverConfig, pconfig, parameters.getStatistics());
							newSCs.add(sc);
							solverConfigs.put(new ObjectArrayWrapper(paramVal), sc);
						}
					}

					state[graphParams.size() - 1]++;
					int i = graphParams.size() - 1;
					while (i > 0 && state[i] > 2) {
						state[i] = 0;
						state[i - 1]++;
						i--;
					}
				}
			}
			pacc.log("[IS�] Generating solver configurations took " + (System.currentTimeMillis() - time) + "ms");
		}
		
		long time = System.currentTimeMillis();
		for (int i = 0; i < graphParams.size(); i++) {
			int j = 0;
			while (j+1 < paramValues[i].size()) {
				Object first = paramValues[i].get(j);
				Object second = paramValues[i].get(j+1);
				Object mid = graphParams.get(i).getDomain().getMidValueOrNull(first, second);
				j++;
				if (mid == null) {
					continue;
				}
				Pair<Object, Object> p = new Pair<Object, Object>(first, second);
				paramValues[i].add(j, mid);
				paramValuesPrevNext[i].add(j, p);
				// first index
				paramValuesPrevNext[i].get(j-1).setSecond(mid);
				// second index
				paramValuesPrevNext[i].get(j+1).setFirst(mid);
				j++;
			}
		}
		pacc.log("[IS�] Updating search space took " + (System.currentTimeMillis() - time) + " ms");
		pacc.log("[IS�] Generated " + newSCs.size() + " solver configurations in iteration " + iteration + ".");
		iteration++;
		lastSolverConfigs = newSCs;
		return newSCs;
	}

	@Override
	public void listParameters() {

	}
	
	private class ObjectArrayWrapper {
		private Object[] array;
		
		public ObjectArrayWrapper(Object[] array) {
			this.array = array;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + Arrays.hashCode(array);
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
			ObjectArrayWrapper other = (ObjectArrayWrapper) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (!Arrays.equals(array, other.array))
				return false;
			return true;
		}
		
		private IterativeSearchSpaceSampling getOuterType() {
			return IterativeSearchSpaceSampling.this;
		}
	}
}
