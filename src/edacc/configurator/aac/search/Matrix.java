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
import edacc.model.ExperimentResultDAO;
import edacc.model.Instance;
import edacc.model.InstanceDAO;
import edacc.model.InstanceSeed;
import edacc.model.SolverConfigurationDAO;

public class Matrix extends SearchMethods {
	private List<SolverConfiguration> solverConfigs;
	public HashMap<Integer, List<ExperimentResult>> mapResults;
	public Course course;
	public Matrix(API api, Random rng, Parameters parameters) throws Exception {
		super(api, rng, parameters);
		solverConfigs = new ArrayList<SolverConfiguration>();
		mapResults = new HashMap<Integer, List<ExperimentResult>>();
		course = null;
		int scCount = SolverConfigurationDAO.getSolverConfigurationByExperimentId(parameters.getIdExperiment()).size();
		int curSc = 0;
		System.out.println("[Matrix] Caching jobs..");
		for (ExperimentResult er : ExperimentResultDAO.getAllByExperimentId(parameters.getIdExperiment())) {
			List<ExperimentResult> results = mapResults.get(er.getSolverConfigId());
			if (results == null) {
				results = new ArrayList<ExperimentResult>();
				mapResults.put(er.getSolverConfigId(), results);
			}
			results.add(er);
		}
		for (Integer scId : api.getSolverConfigurations(parameters.getIdExperiment())) {
			System.out.println("[Matrix] Adding solver config with id: " + scId + " (" + (++curSc) + "/" + scCount + ")");
			SolverConfiguration sc = new SolverConfiguration(scId, api.getParameterConfiguration(parameters.getIdExperiment(), scId), parameters.getStatistics());
			solverConfigs.add(sc);
			List<ExperimentResult> results = mapResults.get(scId);//ExperimentResultDAO.getAllBySolverConfiguration(SolverConfigurationDAO.getSolverConfigurationById(scId));
			System.out.println("[Matrix] " + (results == null ? 0 : results.size()) + " runs.");
			if (results == null || results.isEmpty()) {
				throw new IllegalArgumentException("Found solver configuration with 0 runs!");
			}
			mapResults.put(scId, results);
			if (course == null) {
				System.out.println("[Matrix] Creating parcours");
				// create a new parcours
				course = new Course();
				List<Instance> instances = new ArrayList<Instance>();
				System.out.println("[Matrix] Getting instances and determining seeds..");
				HashMap<Integer, List<Integer>> mapInstanceSeed = new HashMap<Integer, List<Integer>>();
				for (ExperimentResult result : results) {
					List<Integer> seeds = mapInstanceSeed.get(result.getInstanceId());
					if (seeds == null) {
						seeds = new ArrayList<Integer>();
						System.out.println("[Matrix] Instance: " + result.getInstanceId());
						instances.add(InstanceDAO.getById(result.getInstanceId()));
					}
					seeds.add(result.getSeed());
					mapInstanceSeed.put(result.getInstanceId(), seeds);
				}
				System.out.println("[Matrix] " + instances.size() + " instances.");
				if (instances.isEmpty()) {
					throw new IllegalArgumentException("Instance count is 0");
				}
				System.out.println("[Matrix] Shuffling instances..");
				Collections.shuffle(instances, rng);
				boolean finished = false;
				System.out.println("[Matrix] Generating parcours..");
				while (!finished) {
					for (Instance instance: instances) {
						List<Integer> seeds = mapInstanceSeed.get(instance.getId());
						if (seeds == null || seeds.isEmpty()) {
							finished = true;
							break;
						}
						int rand = rng.nextInt(seeds.size());
						course.add(new InstanceSeed(instance, seeds.get(rand)));
						seeds.remove(rand);
					}
				}
				System.out.println("[Matrix] Created");
			}
		}
		System.out.println("[Matrix] Initialization finished");
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
