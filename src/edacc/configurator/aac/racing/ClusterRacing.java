package edacc.configurator.aac.racing;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.api.costfunctions.PARX;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.JobListener;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.configurator.aac.racing.challenge.Clustering;
import edacc.model.Experiment;
import edacc.model.ExperimentResult;
import edacc.model.Instance;
import edacc.model.InstanceDAO;

public class ClusterRacing extends RacingMethods implements JobListener {

	private Clustering clustering;
	private HashMap<Integer, List<Integer>> seeds;
	private HashMap<Integer, SolverConfigurationMetaData> scs;
	
	public ClusterRacing(AAC pacc, Random rng, API api, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
		super(pacc, rng, api, parameters, firstSCs, referenceSCs);
		pacc.addJobListener(this);
		scs = new HashMap<Integer, SolverConfigurationMetaData>();
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

		clustering = new Clustering(instances, new LinkedList<String>());
		
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
		HashMap<Integer, List<Integer>> bestSCsClustering = clustering.getClustering(false);
		
		List<SolverConfiguration> best = new LinkedList<SolverConfiguration>();
		for (int id : bestSCsClustering.keySet()) {
			best.add(scs.get(id).sc);
		}
		// TODO: sort best?
		return best;
	}

	@Override
	public void solverConfigurationsFinished(List<SolverConfiguration> configs) throws Exception {
		for (SolverConfiguration sc : configs) {
			SolverConfigurationMetaData data = scs.get(sc.getIdSolverConfiguration());
			List<Integer> copy = new LinkedList<Integer>();
			
			// race(..) can modify the racingScs list..
			copy.addAll(data.racingScs);
			for (int id : copy) {
				SolverConfiguration inc = scs.get(id).sc;
				race(inc, data);
			}
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
	
	private void addRuns(SolverConfiguration sc, int instanceid, int priority) throws Exception {
		for (int seed : seeds.get(instanceid)) {
			pacc.addJob(sc, seed, instanceid, priority);
		}
		pacc.addSolverConfigurationToListNewSC(sc);
	}
	
	private void initializeSolverConfiguration(SolverConfiguration sc) throws Exception {
		List<Integer> unsolved = new LinkedList<Integer>();
		unsolved.addAll(clustering.getNotUsedInstances());
		HashMap<Integer, List<Integer>> c = clustering.getClustering(false); // clustering.getClusteringHierarchical(Clustering.HierarchicalClusterMethod.AVERAGE_LINKAGE, 10);
		
		
		for (int i = 0; i < parameters.getMinRuns() && !unsolved.isEmpty(); i++) {
			int rand = rng.nextInt(unsolved.size());
			int instanceid = unsolved.get(rand);
			addRuns(sc, instanceid, 0);
			unsolved.remove(rand);
		}
		SolverConfigurationMetaData data = new SolverConfigurationMetaData(sc, unsolved, c);
		//if (!data.racingScs.isEmpty()) {
			scs.put(sc.getIdSolverConfiguration(), data);
			
			// race(..) can modify racingScs list
			LinkedList<Integer> copy = new LinkedList<Integer>();
			copy.addAll(data.racingScs);
			for (Integer id : copy) {
				SolverConfiguration inc = scs.get(id).sc;
				race(inc, data);
			}
		//}
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
			boolean remove = false;
			
			if (parameters.getStatistics().compare(hisJobsAll, myJobsAll) <= 0) {
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
					remove = true;
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
				remove = true;
			}
			if (remove) {
				for (int i = 0; i < data.racingScs.size(); i++) {
					if (data.racingScs.get(i) == incumbent.getIdSolverConfiguration()) {
						data.racingScs.remove(i);
						break;
					}
				}
				//if (data.racingScs.isEmpty()) {
					//pacc.log("Removing solver configuration " + data.sc.getIdSolverConfiguration() + " from list.");
					//scs.remove(data.sc.getIdSolverConfiguration());
				//}
			}
			
		}
	}
	
	
	private class SolverConfigurationMetaData {
		SolverConfiguration sc;
		List<Integer> unsolved;
		HashMap<Integer, List<Integer>> c;
		List<Integer> racingScs;
		
		public SolverConfigurationMetaData(SolverConfiguration sc, List<Integer> unsolved, HashMap<Integer, List<Integer>> c) {
			this.sc = sc;
			this.unsolved = unsolved;
			this.c = c;
			this.racingScs = new LinkedList<Integer>();
			this.racingScs.addAll(c.keySet());
		}
	}


	@Override
	public void jobFinished(ExperimentResult result) {
		SolverConfiguration sc = scs.get(result.getSolverConfigId()).sc;
		List<ExperimentResult> list = new LinkedList<ExperimentResult>();
		boolean solved = false;
		for (ExperimentResult er : sc.getJobs()) {
			if (result.getInstanceId() == er.getInstanceId()) {
				list.add(er);
				solved |= er.getResultCode().isCorrect();
			}
		}
		float cost = (solved ? new PARX(Experiment.Cost.resultTime, true, 0, 1).calculateCost(list) : Float.POSITIVE_INFINITY);
		clustering.update(sc.getIdSolverConfiguration(), result.getInstanceId(), cost);
	}

}
