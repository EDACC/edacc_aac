package edacc.configurator.aac.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.Course;
import edacc.model.ExperimentResult;
import edacc.model.Instance;
import edacc.model.InstanceDAO;
import edacc.model.InstanceSeed;

public class Matrix extends SearchMethods {
	private List<SolverConfiguration> solverConfigs;
	public HashMap<Integer, List<ExperimentResult>> mapResults;
	public Course course;
	public Matrix(API api, Random rng, Parameters parameters) throws Exception {
		super(api, rng, parameters);
		solverConfigs = new ArrayList<SolverConfiguration>();
		mapResults = new HashMap<Integer, List<ExperimentResult>>();
		course = null;
		for (Integer scId : api.getSolverConfigurations(parameters.getIdExperiment())) {
			SolverConfiguration sc = new SolverConfiguration(scId, api.getParameterConfiguration(parameters.getIdExperiment(), scId), parameters.getStatistics());
			solverConfigs.add(sc);
			mapResults.put(scId, api.getRuns(parameters.getIdExperiment(), scId));
			if (course == null) {
				// create a new parcours
				course = new Course();
				List<ExperimentResult> results = mapResults.get(scId);
				List<Instance> instances = new ArrayList<Instance>();
				HashMap<Integer, List<Integer>> mapInstanceSeed = new HashMap<Integer, List<Integer>>();
				for (ExperimentResult result : results) {
					List<Integer> seeds = mapInstanceSeed.get(result.getInstanceId());
					if (seeds == null) {
						seeds = new ArrayList<Integer>();
						instances.add(InstanceDAO.getById(result.getInstanceId()));
					}
					seeds.add(result.getSeed());
					mapInstanceSeed.put(result.getInstanceId(), seeds);
				}
				Collections.shuffle(instances, rng);
				boolean finished = false;
				while (!finished) {
					for (Instance instance: instances) {
						List<Integer> seeds = mapInstanceSeed.get(instance.getId());
						if (seeds == null) {
							finished = true;
							break;
						}
						int rand = rng.nextInt(seeds.size());
						course.add(new InstanceSeed(instance, seeds.get(rand)));
						seeds.remove(rand);
					}
				}
			}
		}
	}
	
	public ExperimentResult getJob(int scId, int instanceId, int seed) {
		// todo: efficiency
		for (ExperimentResult result : mapResults.get(scId)) {
			if (result.getInstanceId() == instanceId && result.getSeed() == seed) {
				return result;
			}
		}
		return null;
	}
	
	public SolverConfiguration getFirstSC() throws Exception {
		List<SolverConfiguration> scs = generateNewSC(1, null);
		if (!scs.isEmpty()) {
			return scs.get(0);
		}
		return null;
	}

	@Override
	public List<SolverConfiguration> generateNewSC(int num,
			SolverConfiguration currentBestSC) throws Exception {
		List<SolverConfiguration> scs = new ArrayList<SolverConfiguration>();
		for (int i = 0; i < num; i++) {
			if (solverConfigs.isEmpty())
				break;
			int rand = rng.nextInt(solverConfigs.size());
			scs.add(solverConfigs.get(rand));
			solverConfigs.remove(rand);
		}
		return scs;
	}
}
