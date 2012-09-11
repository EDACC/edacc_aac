package edacc.configurator.aac.racing;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

import edacc.api.API;
import edacc.api.costfunctions.PARX;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.JobListener;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.configurator.aac.solvercreator.Clustering;
import edacc.model.Experiment;
import edacc.model.ExperimentResult;
import edacc.model.Instance;
import edacc.model.InstanceDAO;
import edacc.model.StatusCode;

public class ClusterRacing extends RacingMethods implements JobListener {

	private Clustering clustering;
	private HashMap<Integer, List<Integer>> seeds;
	private HashMap<Integer, SolverConfigurationMetaData> scs;
	private HashSet<Integer> removedSCIds;
	private HashMap<Integer, List<ExperimentResult>> instanceJobs;
	private HashSet<Integer> instanceJobsLowerLimit;
	
	private PARX par1 = new PARX(Experiment.Cost.resultTime, true, 0, 1);
	
	private boolean useAdaptiveInstanceTimeouts = true;
	private int instanceTimeoutsMinNumJobs = 11;
	private float instanceTimeoutFactor = 1.5f;
	private int limitCPUTimeMaxCPUTime = 600;
	
	private float clusteringThreshold = 0.9f;
	
	public ClusterRacing(AAC pacc, Random rng, API api, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
		super(pacc, rng, api, parameters, firstSCs, referenceSCs);
		pacc.addJobListener(this);
		scs = new HashMap<Integer, SolverConfigurationMetaData>();
		instanceJobs = new HashMap<Integer, List<ExperimentResult>>();
		instanceJobsLowerLimit = new HashSet<Integer>();
		removedSCIds = new HashSet<Integer>();
		List<Integer> instances = new LinkedList<Integer>();
		seeds = new HashMap<Integer, List<Integer>>();
		
		for (Instance i : InstanceDAO.getAllByExperimentId(parameters.getIdExperiment())) {
			instances.add(i.getId());
			List<Integer> seedList = new LinkedList<Integer>();
			for (int j = 0; j < parameters.getMaxParcoursExpansionFactor(); j++) {
				seedList.add(rng.nextInt(Integer.MAX_VALUE-1));
			}
			seeds.put(i.getId(), seedList);
		}
		
		if (instances.isEmpty()) {
			throw new IllegalArgumentException("No instances selected.");
		}

		clustering = new Clustering(instances, new HashMap<Integer, float[]>());
		
		for (SolverConfiguration sc : firstSCs) {
			initializeSolverConfiguration(sc);
		}
	}

	@Override
	public int compareTo(SolverConfiguration sc1, SolverConfiguration sc2) {
		// TODO Auto-generated method stub
		return 0;
	}

	//float lastBestSCsClusteringUpdate = 0.f;
	//HashMap<Integer, List<Integer>> bestSCsClustering = null;
	@Override
	public List<SolverConfiguration> getBestSolverConfigurations() {
		/*if (bestSCsClustering == null || pacc.getWallTime() - lastBestSCsClusteringUpdate > 120) {
			bestSCsClustering = clustering.getClusteringHierarchical(Clustering.HierarchicalClusterMethod.AVERAGE_LINKAGE, 10);
			lastBestSCsClusteringUpdate = pacc.getWallTime();
		}*/
		HashMap<Integer, List<Integer>> bestSCsClustering = clustering.getClustering(false, clusteringThreshold);
		
		List<SolverConfiguration> best = new LinkedList<SolverConfiguration>();
		for (int id : bestSCsClustering.keySet()) {
			best.add(scs.get(id).sc);
		}
		// TODO: sort best?
		return best;
	}

	@Override
	public void solverConfigurationsFinished(List<SolverConfiguration> configs) throws Exception {
		HashMap<Integer, List<Integer>> c = clustering.getClustering(false);
		
		for (SolverConfiguration sc : configs) {			
			SolverConfigurationMetaData data = scs.get(sc.getIdSolverConfiguration());
			List<Integer> copy = new LinkedList<Integer>();
			
			boolean racedAll = true;
			// race(..) can modify the racingScs list..
			copy.addAll(data.racingScs);
			for (int id : copy) {
				SolverConfiguration inc = scs.get(id).sc;
				if (inc.getNumRunningJobs() + inc.getNumNotStartedJobs() > 0) {
					racedAll = false;
				} else {
					race(inc, data);
				}
			}
			if (!racedAll) {
				// add solver configuration to list new sc, and try to race in next iteration
				pacc.addSolverConfigurationToListNewSC(sc);
			} 

			if (data.racingScs.isEmpty() && data.competitors.isEmpty() && !c.containsKey(data.sc.getIdSolverConfiguration())) {
				pacc.log("Removing " + data.sc.getIdSolverConfiguration() + ".");
				removedSCIds.add(data.sc.getIdSolverConfiguration());
				clustering.remove(data.sc.getIdSolverConfiguration());
			}
			
			updateName(data);
		}

	}

	@Override
	public void solverConfigurationsCreated(List<SolverConfiguration> scs) throws Exception {
		for (SolverConfiguration sc : scs) {
			if (!this.scs.containsKey(sc.getIdSolverConfiguration()))
				initializeSolverConfiguration(sc);
		}

	}

	@Override
	public int computeOptimalExpansion(int coreCount, int jobs, int listNewSCSize) {
		int res = 0;
		if (coreCount < parameters.getMinCPUCount() || coreCount > parameters.getMaxCPUCount()) {
			pacc.log("w Warning: Current core count is " + coreCount);
		}
		
		int min_sc = (Math.max(Math.round(2.f * coreCount), 8) - jobs) / (parameters.getMinRuns() * parameters.getMaxParcoursExpansionFactor());
		if (min_sc > 0) {
			res = (Math.max(Math.round(3.f * coreCount), 8) - jobs) / (parameters.getMinRuns() * parameters.getMaxParcoursExpansionFactor());
		}
		if (res == 0 && coreCount - jobs > 0) {
			res = 1;
		}

		if (listNewSCSize == 0 && res == 0) {
			res = 1;
		}
		return res;
	}

	@Override
	public void stopEvaluation(List<SolverConfiguration> scs) throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public List<String> getParameters() {
		// TODO Auto-generated method stub
		return new LinkedList<String>();
	}

	@Override
	public void raceFinished() {
		// TODO Auto-generated method stub

	}
	
	public void updateName(SolverConfigurationMetaData data) {		
		data.sc.setNameRacing("Num races: " + data.racingScs.size() + " Num competitors: " + data.competitors.size() + (removedSCIds.contains(data.sc.getIdSolverConfiguration()) ? " (removed)" : ""));
	}
	
	private void addRuns(SolverConfiguration sc, int instanceid, int priority) throws Exception {
		for (int seed : seeds.get(instanceid)) {
			pacc.addJob(sc, seed, instanceid, priority);
		}
		pacc.addSolverConfigurationToListNewSC(sc);
	}
	
	private void initializeSolverConfiguration(SolverConfiguration sc) throws Exception {
		List<Integer> unsolved = new LinkedList<Integer>();
		unsolved.addAll(clustering.getNotUsedInstances());
		HashMap<Integer, List<Integer>> c = clustering.getClustering(false, clusteringThreshold); // clustering.getClusteringHierarchical(Clustering.HierarchicalClusterMethod.AVERAGE_LINKAGE, 10);
		
		
		for (int i = 0; i < parameters.getMinRuns() && !unsolved.isEmpty(); i++) {
			int rand = rng.nextInt(unsolved.size());
			int instanceid = unsolved.get(rand);
			addRuns(sc, instanceid, 0);
			unsolved.remove(rand);
		}
		SolverConfigurationMetaData data = new SolverConfigurationMetaData(sc, unsolved, c);
		//if (!data.racingScs.isEmpty()) {
			scs.put(sc.getIdSolverConfiguration(), data);
			
			boolean racedAll = true;
			// race(..) can modify racingScs list
			LinkedList<Integer> copy = new LinkedList<Integer>();
			copy.addAll(data.racingScs);
			for (Integer id : copy) {
				SolverConfiguration inc = scs.get(id).sc;
				if (inc.getNumRunningJobs() + inc.getNumNotStartedJobs() > 0) {
					racedAll = false;
				} else {
					race(inc, data);
				}
			}
			if (!racedAll) {
				pacc.addSolverConfigurationToListNewSC(sc);
			}
		//}
			updateName(data);
	}
	
	private void race(SolverConfiguration incumbent, SolverConfigurationMetaData data) throws Exception {
		List<Integer> instanceIds = data.c.get(incumbent.getIdSolverConfiguration());
		
		HashMap<Integer, List<ExperimentResult>> hisJobs = new HashMap<Integer, List<ExperimentResult>>();
		HashMap<Integer, List<ExperimentResult>> myJobs = new HashMap<Integer, List<ExperimentResult>>();
		
		List<ExperimentResult> hisJobsAll = new LinkedList<ExperimentResult>();
		List<ExperimentResult> myJobsAll = new LinkedList<ExperimentResult>();
		
		for (int id : instanceIds) {
			hisJobs.put(id, new LinkedList<ExperimentResult>());
			myJobs.put(id, new LinkedList<ExperimentResult>());
		}
		
		for (ExperimentResult er : incumbent.getJobs()) {
			if (hisJobs.containsKey(er.getInstanceId())) {
				hisJobsAll.add(er);
				hisJobs.get(er.getInstanceId()).add(er);
			}
		}
		for (ExperimentResult er : data.sc.getJobs()) {
			if (myJobs.containsKey(er.getInstanceId())) {
				myJobsAll.add(er);
				myJobs.get(er.getInstanceId()).add(er);
			}
		}
		
		if (myJobsAll.isEmpty()) {
			int instanceid = instanceIds.get(rng.nextInt(instanceIds.size()));
			addRuns(data.sc, instanceid, Integer.MAX_VALUE - data.sc.getIdSolverConfiguration());
		} else {
			boolean removeScFromRace = false;
			if (parameters.getStatistics().compare(hisJobsAll, myJobsAll) <= 0) {
				// race goes on..
				
				LinkedList<Integer> possibleInstances = new LinkedList<Integer>();
				int icount = 0;
				for (int id : instanceIds) {
					if (myJobs.get(id).isEmpty()) {
						possibleInstances.add(id);
					} else {
						icount++;
					}
				}
				
				if (possibleInstances.isEmpty()) {
					pacc.log("No more instances for " + data.sc.getIdSolverConfiguration() + " vs " + incumbent.getIdSolverConfiguration());
					removeScFromRace = true;
				} else {
					for (int i = 0; i < icount && !possibleInstances.isEmpty(); i++) {
						int rand = rng.nextInt(possibleInstances.size());
						addRuns(data.sc, possibleInstances.get(rand), Integer.MAX_VALUE - data.sc.getIdSolverConfiguration());
						possibleInstances.remove(rand);
					}
				}
			} else {
				// lost.
				
				SolverConfigurationMetaData d = scs.get(incumbent.getIdSolverConfiguration());
				if (!d.unsolved.isEmpty()) {
					int rand = rng.nextInt(d.unsolved.size());
					addRuns(incumbent, d.unsolved.get(rand), Integer.MAX_VALUE - incumbent.getIdSolverConfiguration());
					d.unsolved.remove(rand);
				}
				pacc.log("Solver Configuration " + data.sc.getIdSolverConfiguration() + " lost against " + incumbent.getIdSolverConfiguration());
				removeScFromRace = true;
			}
			if (removeScFromRace) {
				data.racingScs.remove(new Integer(incumbent.getIdSolverConfiguration()));
				/*for (int i = 0; i < data.racingScs.size(); i++) {
					if (data.racingScs.get(i) == incumbent.getIdSolverConfiguration()) {
						data.racingScs.remove(i);
						break;
					}
				}*/
				scs.get(incumbent.getIdSolverConfiguration()).competitors.remove(new Integer(data.sc.getIdSolverConfiguration()));
			}
			
			
			// TODO: delete config? (inc and/or racing)
			/*
			if (data.racingScs.isEmpty()) {
				pacc.log("Removing solver configuration " + data.sc.getIdSolverConfiguration() + " from list.");
				removedSCIds.add(data.sc.getIdSolverConfiguration());
				clustering.remove(data.sc.getIdSolverConfiguration());
			}*/
		}
	}
	
	
	private class SolverConfigurationMetaData {
		SolverConfiguration sc;
		List<Integer> unsolved;
		HashMap<Integer, List<Integer>> c;
		List<Integer> racingScs;
		List<Integer> competitors;
		
		public SolverConfigurationMetaData(SolverConfiguration sc, List<Integer> unsolved, HashMap<Integer, List<Integer>> c) {
			this.sc = sc;
			this.unsolved = unsolved;
			this.c = c;
			this.racingScs = new LinkedList<Integer>();
			this.racingScs.addAll(c.keySet());			
			this.competitors = new LinkedList<Integer>();
			
			for (Integer i : racingScs) {
				scs.get(i).competitors.add(sc.getIdSolverConfiguration());
			}
		}
	}
	
	private int median(List<ExperimentResult> jobs) {
		Collections.sort(jobs, new Comparator<ExperimentResult>() {

			@Override
			public int compare(ExperimentResult arg0, ExperimentResult arg1) {
				if (arg0.getResultCode().isCorrect() && !arg1.getResultCode().isCorrect()) {
					return -1;
				} else if (!arg0.getResultCode().isCorrect() && arg1.getResultCode().isCorrect()) {
					return 1;
				} else if (!arg0.getResultCode().isCorrect() && !arg1.getResultCode().isCorrect()) {
					return 0;
				} else {
					if (arg0.getResultTime() < arg1.getResultTime()) {
						return -1;
					} else if (arg1.getResultTime() < arg0.getResultTime()) {
						return 1;
					} else {
						return 0;
					}
				}
			}
			
		});
		
		ExperimentResult m = jobs.get(jobs.size() >> 1);
		if (m.getResultCode().isCorrect()) {
			return Math.round(m.getResultTime());
		} else {
			return pacc.getCPUTimeLimit(m.getInstanceId());
		}
	}
	
	public void updateModel(SolverConfiguration sc, int instanceId) {
		List<ExperimentResult> list = new LinkedList<ExperimentResult>();
		boolean solved = false;
		for (ExperimentResult er : sc.getJobs()) {
			if (instanceId == er.getInstanceId()) {
				list.add(er);
				solved |= er.getResultCode().isCorrect();
			}
		}
		float cost = (solved ? par1.calculateCost(list) : Float.POSITIVE_INFINITY);
		clustering.update(sc.getIdSolverConfiguration(), instanceId, cost);
	}


	@Override
	public void jobsFinished(List<ExperimentResult> results) throws Exception {
		for (ExperimentResult result: results) {
			updateModel(scs.get(result.getSolverConfigId()).sc, result.getInstanceId());
		}
		
		if (useAdaptiveInstanceTimeouts) {
			HashSet<Integer> instanceIds = new HashSet<Integer>();
			for (ExperimentResult result : results) {
				instanceIds.add(result.getInstanceId());

				List<ExperimentResult> jobs = instanceJobs.get(result.getInstanceId());
				if (jobs == null) {
					jobs = new LinkedList<ExperimentResult>();
					instanceJobs.put(result.getInstanceId(), jobs);
				}
				jobs.add(result);
			}
			
			for (int instanceId : instanceIds) {
				List<ExperimentResult> jobs = instanceJobs.get(instanceId);
				for (int i = jobs.size() - 1; i >= 0; i--) {
					if (removedSCIds.contains(jobs.get(i).getSolverConfigId())) {
						jobs.remove(i);
					}
				}
				
				if (jobs.size() > instanceTimeoutsMinNumJobs) {
					int current_limit = pacc.getCPUTimeLimit(instanceId);
					int m = Math.round(median(jobs) * instanceTimeoutFactor);
					if (m < 1)
						m = 1;
					if (m < current_limit) {
						List<SolverConfiguration> tmp = new LinkedList<SolverConfiguration>();
						for (SolverConfigurationMetaData data : scs.values()) {
							if (!removedSCIds.contains(data.sc.getIdSolverConfiguration())) {
								tmp.add(data.sc);
							}
						}
						pacc.log("Setting CPU Timelimit of " + instanceId + " to " + m + " (lower).");
						for (SolverConfiguration config : pacc.changeCPUTimeLimit(instanceId, m, tmp, true, true)) {
							pacc.addSolverConfigurationToListNewSC(config);
						}
						
						for (SolverConfiguration sc : tmp) {
							updateModel(sc, instanceId);
						}

						instanceJobsLowerLimit.add(instanceId);
					} else if (!instanceJobsLowerLimit.contains(instanceId)) {
						List<SolverConfiguration> tmp = new LinkedList<SolverConfiguration>();
						for (SolverConfigurationMetaData data : scs.values()) {
							if (!removedSCIds.contains(data.sc.getIdSolverConfiguration())) {
								tmp.add(data.sc);
							}
						}
						m = Math.min(limitCPUTimeMaxCPUTime, Math.round(current_limit * instanceTimeoutFactor));
						if (m > current_limit) {
							pacc.log("Setting CPU Timelimit of " + instanceId + " to " + m + " (higher).");
							for (SolverConfiguration config : pacc.changeCPUTimeLimit(instanceId, m, tmp, true, true)) {
								pacc.addSolverConfigurationToListNewSC(config);
							}
							
							for (SolverConfiguration sc : tmp) {
								updateModel(sc, instanceId);
							}
						}
					}

					// remove restarted jobs
					for (int i = jobs.size() - 1; i >= 0; i--) {
						if (jobs.get(i).getStatus().equals(StatusCode.NOT_STARTED)) {
							jobs.remove(i);
						}
					}
				}
			}
		}
	}

}
