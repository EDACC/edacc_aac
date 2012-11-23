package edacc.configurator.aac.racing;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.Vector;

import edacc.api.API;
import edacc.api.costfunctions.Median;
import edacc.api.costfunctions.PARX;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.JobListener;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.configurator.aac.search.ibsutils.SolverConfigurationIBS;
import edacc.configurator.aac.solvercreator.Clustering;
import edacc.model.Experiment;
import edacc.model.ExperimentDAO;
import edacc.model.ExperimentResult;
import edacc.model.ExperimentResultDAO;
import edacc.model.Instance;
import edacc.model.InstanceClass;
import edacc.model.InstanceClassDAO;
import edacc.model.InstanceDAO;
import edacc.model.InstanceHasInstanceClassDAO;
import edacc.model.StatusCode;
import edacc.model.Experiment.Cost;

public class ClusterRacing extends RacingMethods implements JobListener {

	private Clustering clustering;
	private HashMap<Integer, List<Integer>> seeds;
	private HashMap<Integer, SolverConfigurationMetaData> scs;
	private HashSet<Integer> removedSCIds;
	private HashMap<Integer, List<ExperimentResult>> instanceJobs;
	private HashMap<Integer, Double> instanceMedianTime;
	private HashMap<Integer, Double> instanceMedianTimeCorrect;
	private HashSet<Integer> instanceJobsLowerLimit;
	private HashMap<Integer, Integer> incumbentPoints;
	
	
	private PARX par1;
	private Median median;
	private int unsolvedInstancesMaxJobs = 10;
	private int unsolvedInstancesMinPoints = 10;
	private int incumbentWinnerInstances = 1;
	private int initialRandomJobs = 3;
	
	private boolean useAdaptiveInstanceTimeouts = true;
	private int instanceTimeoutsExpId = -1;
	private int instanceTimeoutsMinNumJobs = 11;
	private float instanceTimeoutFactor = 1.5f;
	private int limitCPUTimeMaxCPUTime = 600;
	
	private int fixClusteringCPUTime = -1;
	
	private Integer fixedSeed = null;
	
	private float clusteringThreshold = 0.9f;
	private int maxRacingClusters = 12;
	
	private Double maxCost = null;
	
	private boolean useInstanceClassClusters = false;
	
	private int maxParcoursExpansionFactor;
	private boolean clusteringChanged = true;
	private HashMap<Integer, List<Integer>> cachedClustering = null;
	
	
	boolean initialRunsFinished = false;
	List<SolverConfiguration> solverConfigurationsToInitialize = new LinkedList<SolverConfiguration>();
	
	
	List<Set<Integer>> instanceClassClusters;
	int usedInstanceClassCluster;
	
	HashMap<Integer, List<Integer>> lastClustering;
	BufferedWriter debugWriter = null;
	
	public ClusterRacing(AAC pacc, Random rng, API api, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
		super(pacc, rng, api, parameters, firstSCs, referenceSCs);
		pacc.addJobListener(this);
		
		//debugWriter = new BufferedWriter(new FileWriter(new File("./debug_output")));
		
		lastClustering = null;
		
		maxParcoursExpansionFactor = parameters.getMaxParcoursExpansionFactor();
		String val;
		if ((val = parameters.getRacingMethodParameters().get("ClusterRacing_useAdaptiveInstanceTimeouts")) != null)
			useAdaptiveInstanceTimeouts = Boolean.parseBoolean(val);
		if ((val = parameters.getRacingMethodParameters().get("ClusterRacing_instanceTimeoutsExpId")) != null)
			instanceTimeoutsExpId = Integer.parseInt(val);
		if ((val = parameters.getRacingMethodParameters().get("ClusterRacing_instanceTimeoutsMinNumJobs")) != null)
			instanceTimeoutsMinNumJobs = Integer.parseInt(val);
		if ((val = parameters.getRacingMethodParameters().get("ClusterRacing_instanceTimeoutFactor")) != null)
			instanceTimeoutFactor = Float.parseFloat(val);
		if ((val = parameters.getRacingMethodParameters().get("ClusterRacing_limitCPUTimeMaxCPUTime")) != null)
			limitCPUTimeMaxCPUTime = Integer.parseInt(val);
		if ((val = parameters.getRacingMethodParameters().get("ClusterRacing_clusteringThreshold")) != null)
			clusteringThreshold = Float.parseFloat(val);
		if ((val = parameters.getRacingMethodParameters().get("ClusterRacing_unsolvedInstancesMaxJobs")) != null)
			unsolvedInstancesMaxJobs = Integer.parseInt(val);
		if ((val = parameters.getRacingMethodParameters().get("ClusterRacing_unsolvedInstancesMinPoints")) != null)
			unsolvedInstancesMinPoints = Integer.parseInt(val);
		if ((val = parameters.getRacingMethodParameters().get("ClusterRacing_incumbentWinnerInstances")) != null)
			incumbentWinnerInstances = Integer.parseInt(val);
		if ((val = parameters.getRacingMethodParameters().get("ClusterRacing_maxRacingClusters")) != null)
			maxRacingClusters = Integer.parseInt(val);
		if ((val = parameters.getRacingMethodParameters().get("ClusterRacing_maxCost")) != null) 
			maxCost = Double.parseDouble(val);
		if ((val = parameters.getRacingMethodParameters().get("ClusterRacing_initialRandomJobs")) != null) 
			initialRandomJobs = Integer.parseInt(val);
		if ((val = parameters.getRacingMethodParameters().get("ClusterRacing_fixedSeed")) != null) 
			fixedSeed = Integer.parseInt(val);
		if ((val = parameters.getRacingMethodParameters().get("ClusterRacing_fixClusteringCPUTime")) != null) 
			fixClusteringCPUTime = Integer.parseInt(val);
		if ((val = parameters.getRacingMethodParameters().get("ClusterRacing_useInstanceClassClusters")) != null)
			useInstanceClassClusters = Boolean.parseBoolean(val);
		
        if (ExperimentDAO.getById(parameters.getIdExperiment()).getDefaultCost().equals(Cost.resultTime)) {
            par1 = new PARX(Experiment.Cost.resultTime, true, 1.0f);
            median = new Median(Experiment.Cost.resultTime, true);
        } else if (ExperimentDAO.getById(parameters.getIdExperiment()).getDefaultCost().equals(Cost.wallTime)) {
            par1 = new PARX(Experiment.Cost.wallTime, true, 1.0f);
            median = new Median(Experiment.Cost.wallTime, true);
        } else {
            par1 = new PARX(Experiment.Cost.cost, true, 1.0f);
            median = new Median(Experiment.Cost.cost, true);
        }
		
        if (useInstanceClassClusters) {
        	instanceClassClusters = new LinkedList<Set<Integer>>();
        	usedInstanceClassCluster = 0;
        	HashMap<Integer, Set<Integer>> tmp = new HashMap<Integer, Set<Integer>>();
        	for (Instance i : InstanceDAO.getAllByExperimentId(parameters.getIdExperiment())) {
        		Vector<InstanceClass> v = InstanceHasInstanceClassDAO.getInstanceClassElements(i);
        		for (InstanceClass ic : v) {
        			Set<Integer> c = tmp.get(ic.getId());
        			if (c == null) {
        				c = new HashSet<Integer>();
        				tmp.put(ic.getId(), c);
        			}
        			c.add(i.getId());
        		}
        	}
        	instanceClassClusters.addAll(tmp.values());
        	Collections.sort(instanceClassClusters, new Comparator<Set<Integer>>() {

				@Override
				public int compare(Set<Integer> arg0, Set<Integer> arg1) {
					return arg1.size() - arg0.size();
				}
        		
        	});
        	
        	for (int i = 0; i < instanceClassClusters.size(); i++) {
        		Set<Integer> current = instanceClassClusters.get(i);
        		for (int j = instanceClassClusters.size()-1; j > i; j--) {
        			Set<Integer> tmpC = instanceClassClusters.get(j);
        			tmpC.removeAll(current);
        			if (tmpC.isEmpty()) {
        				instanceClassClusters.remove(j);
        			}
        		}
        	}
        	pacc.log("[ClusterRacing] Initial instance class clustering size: " + instanceClassClusters.size());
        }
        
		scs = new HashMap<Integer, SolverConfigurationMetaData>();
		seeds = new HashMap<Integer, List<Integer>>();
		
		for (SolverConfiguration sc : firstSCs) {
			scs.put(sc.getIdSolverConfiguration(), new SolverConfigurationMetaData(sc, null, null));
			
			if (useAdaptiveInstanceTimeouts) {
				for (ExperimentResult j : sc.getJobs()) {
					if (pacc.getCPUTimeLimit(j.getInstanceId()) < j.getCPUTimeLimit()) {
						// todo: max cpu time limit?? might be difficult to implement.
						pacc.changeCPUTimeLimit(j.getInstanceId(), j.getCPUTimeLimit(), null, false, false);
					}
					
					List<Integer> seedList = seeds.get(j.getInstanceId());
					if (seedList == null) {
						seedList = new LinkedList<Integer>();
						seeds.put(j.getInstanceId(), seedList);
					}
					if (!seedList.contains(j.getSeed())) {
						seedList.add(j.getSeed());
					}
					if (seedList.size() > maxParcoursExpansionFactor) {
						maxParcoursExpansionFactor = seedList.size();
						pacc.log("[ClusterRacing] Warning: maxParcoursExpansionFactor is now " + seedList.size());
					}
				}
			}
		}
		HashSet<Integer> instanceIds = null;
		if (instanceTimeoutsExpId != -1) {
			instanceIds = new HashSet<Integer>();
		}
		
		instanceJobs = new HashMap<Integer, List<ExperimentResult>>();
		instanceMedianTime = new HashMap<Integer, Double>();
		instanceMedianTimeCorrect = new HashMap<Integer, Double>();
		instanceJobsLowerLimit = new HashSet<Integer>();
		removedSCIds = new HashSet<Integer>();
		List<Integer> instances = new LinkedList<Integer>();
		
				
		for (Instance i : InstanceDAO.getAllByExperimentId(parameters.getIdExperiment())) {
			instances.add(i.getId());
			List<Integer> seedList = seeds.get(i.getId());
			boolean addToInstanceIds = seedList == null;
			if (seedList == null) {
				seedList = new LinkedList<Integer>();
				seeds.put(i.getId(), seedList);
			}
			
			if (fixedSeed != null && maxParcoursExpansionFactor == 1) {
				if (seedList.isEmpty()) {
					seedList.add(fixedSeed);
				}
			}
			
			for (int j = seedList.size(); j < maxParcoursExpansionFactor; j++) {
				seedList.add(rng.nextInt(Integer.MAX_VALUE-1));
			}
			if (instanceIds != null && addToInstanceIds)
				instanceIds.add(i.getId());
		}
		
		clustering = new Clustering(instances, new HashMap<Integer, float[]>());
		
		if (instanceIds != null) {
			HashMap<Integer, Integer> timeouts = new HashMap<Integer, Integer>();
			for (ExperimentResult er : ExperimentResultDAO.getAllByExperimentId(instanceTimeoutsExpId)) {
				Integer v = timeouts.get(er.getInstanceId());
				if (v == null) {
					timeouts.put(er.getInstanceId(), Math.min(er.getCPUTimeLimit(), limitCPUTimeMaxCPUTime));
				} else {
					if (er.getCPUTimeLimit() < v) {
						timeouts.put(er.getInstanceId(), er.getCPUTimeLimit());
					}
				}
			}
			for (Entry<Integer, Integer> e : timeouts.entrySet()) {
				pacc.changeCPUTimeLimit(e.getKey(), e.getValue(), null, false, false);
			}
		}
		
		if (instances.isEmpty()) {
			throw new IllegalArgumentException("No instances selected.");
		}
		
		boolean oldUseAdaptiveInstanceTimouts = useAdaptiveInstanceTimeouts;
		useAdaptiveInstanceTimeouts = false;
		for (SolverConfiguration sc : firstSCs) {
			jobsFinished(sc.getFinishedJobs());
		}
		useAdaptiveInstanceTimeouts = oldUseAdaptiveInstanceTimouts;
		List<Integer> unsolved = new LinkedList<Integer>();
		unsolved.addAll(clustering.getNotUsedInstances());
		HashMap<Integer, List<Integer>> c = getClustering(); // clustering.getClusteringHierarchical(Clustering.HierarchicalClusterMethod.AVERAGE_LINKAGE, 10);
		
		for (SolverConfiguration sc : firstSCs) {
			Set<Integer> unsolved_copy = new HashSet<Integer>();
			unsolved_copy.addAll(unsolved);
			HashMap<Integer, List<Integer>> c_copy = new HashMap<Integer, List<Integer>>();
			for (Entry<Integer, List<Integer>> e : c.entrySet()) {
				List<Integer> copy = new LinkedList<Integer>();
				copy.addAll(e.getValue());
				c_copy.put(e.getKey(), copy);
			}
			
			scs.get(sc.getIdSolverConfiguration()).update(unsolved_copy, c_copy);
		}
		
		incumbentPoints = new HashMap<Integer, Integer>();
		
		for (SolverConfiguration sc : firstSCs) {
			initializeSolverConfiguration(sc);
			updateName(scs.get(sc.getIdSolverConfiguration()));
		}
	}
	
	public HashMap<Integer, List<Integer>> getClustering() {
		if (clusteringChanged) {
			if (useInstanceClassClusters) {
				if (cachedClustering == null) {
					cachedClustering = new HashMap<Integer, List<Integer>>();
				}
			} else {
				if (fixClusteringCPUTime == -1 || pacc.getCumulatedCPUTime() < fixClusteringCPUTime) {
					cachedClustering = clustering.getClustering(false, false, clusteringThreshold);
					// if (useInstanceClassClusters && cachedClustering.size() >
					// maxRacingClusters) {
					// cachedClustering =
					// clustering.getClusteringByClusters(instanceClassClusters);
					// }
				} else {
					pacc.log("[ClusterRacing] Clustering changed but clustering is locked now.");
				}
			}
			clusteringChanged = false;
			int count = 0;
			for (List<Integer> l : cachedClustering.values())
				count += l.size();
			pacc.log("[ClusterRacing] Clustering - size: " + cachedClustering.size() + " cost: " + clustering.getCost(cachedClustering) + " instances_used: " + count);
			
			if (debugWriter != null) {
				if (!cachedClustering.equals(lastClustering)) {
					lastClustering = getClustering();
					try {
						for (Entry<Integer, List<Integer>> e : cachedClustering.entrySet()) {

							debugWriter.write("" + e.getKey() + "," + e.getValue().toString() + '\n');

						}
						debugWriter.write("---------------------------------\n");
						debugWriter.flush();
					} catch (Exception ex) {
						// ex.printStackTrace();
					}
				}
			}
		
		}
		HashMap<Integer, List<Integer>> res = new HashMap<Integer, List<Integer>>();
		for (Entry<Integer, List<Integer>> e : cachedClustering.entrySet()) {
			List<Integer> copy = new LinkedList<Integer>();
			copy.addAll(e.getValue());
			res.put(e.getKey(), copy);
		}
		
		return res;
	}
	

	@Override
	public int compareTo(SolverConfiguration sc1, SolverConfiguration sc2) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public List<SolverConfiguration> getBestSolverConfigurations() {
		HashMap<Integer, List<Integer>> bestSCsClustering = getClustering();
		
		List<SolverConfiguration> best = new LinkedList<SolverConfiguration>();
		for (int id : bestSCsClustering.keySet()) {
			best.add(scs.get(id).sc);
		}
		// TODO: sort best?
		return best;
	}

	@Override
	public void solverConfigurationsFinished(List<SolverConfiguration> configs) throws Exception {
		HashMap<Integer, List<Integer>> c = clustering.getClustering(false, false);
		
		for (SolverConfiguration sc : configs) {			
			SolverConfigurationMetaData data = scs.get(sc.getIdSolverConfiguration());
			List<Integer> copy = new LinkedList<Integer>();
			
			boolean racedAll = true;
			// race(..) can modify the racingScs list..
			copy.addAll(data.racingScs);
			for (int id : copy) {
				SolverConfiguration inc = scs.get(id).sc;
				racedAll &= race(inc, data);
			}
			if (!racedAll) {
				// add solver configuration to list new sc, and try to race in next iteration
				pacc.addSolverConfigurationToListNewSC(sc);
			} 

			if (data.racingScs.isEmpty() && data.competitors.isEmpty() && !c.containsKey(data.sc.getIdSolverConfiguration()) && !getClustering().containsKey(data.sc.getIdSolverConfiguration())) {
				pacc.log("[ClusterRacing] Removing " + data.sc.getIdSolverConfiguration() + ".");
				removedSCIds.add(data.sc.getIdSolverConfiguration());
				clustering.remove(data.sc.getIdSolverConfiguration());
			}
			
			updateName(data);
		}

		for (SolverConfigurationMetaData data : scs.values()) {
			if (data.racingScs.isEmpty() && data.competitors.isEmpty() && !c.containsKey(data.sc.getIdSolverConfiguration())) {
				if (!removedSCIds.contains(data.sc.getIdSolverConfiguration())) {
					pacc.addSolverConfigurationToListNewSC(data.sc); // will be marked as removed in next iteration
				}
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
		if (!initialRunsFinished) {
			pacc.log("[ClusterRacing] initial not finished");
			initialRunsFinished = true;
			for (SolverConfigurationMetaData data : scs.values()) {
				if (data.sc.getJobCount() != data.sc.getFinishedJobs().size()) {
					initialRunsFinished = false;
					break;
				}
			}
			if (!initialRunsFinished) {
				pacc.log("[ClusterRacing] Waiting for initial runs to finish..");
				return initialRandomJobs;
			}
		}
		int res = 0;
		if (coreCount < parameters.getMinCPUCount() || coreCount > parameters.getMaxCPUCount()) {
			pacc.log("w Warning: Current core count is " + coreCount);
		}
		
		int min_sc = (Math.max(Math.round(2.f * coreCount), 8) - jobs) / (parameters.getMinRuns() * maxParcoursExpansionFactor);
		if (min_sc > 0) {
			res = (Math.max(Math.round(2.5f * coreCount), 8) - jobs) / (parameters.getMinRuns() * maxParcoursExpansionFactor);
		}
		if (res == 0 && coreCount - jobs > 0) {
			res = 1;
		}

		if (listNewSCSize == 0 && res == 0) {
			res = 1;
		}
		
		if (res > 10) {
			res = 10;
		}
		return res;
	}

	@Override
	public void stopEvaluation(List<SolverConfiguration> scs) throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public List<String> getParameters() {
		LinkedList<String> res = new LinkedList<String>();
		res.add("% instance time limit specific settings");
		res.add("ClusterRacing_useAdaptiveInstanceTimeouts = " + useAdaptiveInstanceTimeouts);
		res.add("ClusterRacing_instanceTimeoutsExpId = " + instanceTimeoutsExpId);
		res.add("ClusterRacing_instanceTimeoutsMinNumJobs = " + instanceTimeoutsMinNumJobs);
		res.add("ClusterRacing_instanceTimeoutFactor = " + instanceTimeoutFactor);
		res.add("ClusterRacing_limitCPUTimeMaxCPUTime = " + limitCPUTimeMaxCPUTime);
		res.add("% racing specific settings");
		res.add("ClusterRacing_clusteringThreshold = " + clusteringThreshold);
		res.add("ClusterRacing_unsolvedInstancesMaxJobs = " + unsolvedInstancesMaxJobs);
		res.add("ClusterRacing_unsolvedInstancesMinPoints = " + unsolvedInstancesMinPoints);
		res.add("ClusterRacing_incumbentWinnerInstances = " + incumbentWinnerInstances);
		res.add("ClusterRacing_maxRacingClusters = " + maxRacingClusters);
		res.add("ClusterRacing_maxCost = " + maxCost);
		res.add("ClusterRacing_useInstanceClassClusters = " + useInstanceClassClusters);
		res.add("ClusterRacing_fixClusteringCPUTime = " + fixClusteringCPUTime);
		res.add("% misc");
		res.add("ClusterRacing_fixedSeed = " + fixedSeed);
		return res;
	}

	@Override
	public void raceFinished() {
		// TODO Auto-generated method stub
		pacc.log("[ClusterRacing] race finished - Clustering: ");
		for (Entry<Integer, List<Integer>> e : this.getClustering().entrySet()) {
			System.out.println(e.getKey() + "," + e.getValue().toString());
		}
	}
	
	public void updateName(SolverConfigurationMetaData data) {
		HashMap<Integer, List<Integer>> c = getClustering();
		data.sc.setNameRacing("csize: "+ (c.containsKey(data.sc.getIdSolverConfiguration()) ? c.get(data.sc.getIdSolverConfiguration()).size() : 0) + " races: " + data.racingScs.size() + " competitors: " + data.competitors.size() + " points: " + incumbentPoints.get(data.sc.getIdSolverConfiguration()) + (removedSCIds.contains(data.sc.getIdSolverConfiguration()) ? " (removed)" : ""));
	}
	
	private void addRuns(SolverConfiguration sc, int instanceid, int priority) throws Exception {
		SolverConfigurationMetaData data = scs.get(sc.getIdSolverConfiguration());
		if (data != null) {
			data.unsolved.remove(instanceid);
		}
		for (int seed : seeds.get(instanceid)) {
			pacc.addJob(sc, seed, instanceid, priority);
		}
		pacc.addSolverConfigurationToListNewSC(sc);
	}
	
	private void initializeSolverConfiguration(SolverConfiguration sc) throws Exception {
		Set<Integer> unsolved = new HashSet<Integer>();
		unsolved.addAll(clustering.getNotUsedInstances());
		HashMap<Integer, List<Integer>> c = getClustering(); // clustering.getClusteringHierarchical(Clustering.HierarchicalClusterMethod.AVERAGE_LINKAGE, 10);
		
		List<Integer> possibleInstanceIds = new LinkedList<Integer>();
		
		if (useInstanceClassClusters) {
			if (usedInstanceClassCluster < instanceClassClusters.size()) {
				possibleInstanceIds.addAll(instanceClassClusters.get(usedInstanceClassCluster++));
				cachedClustering.put(sc.getIdSolverConfiguration(), new LinkedList<Integer>());
			}
		} else if (initialRandomJobs > 0) {
			if (useInstanceClassClusters) {
				int rand = rng.nextInt(instanceClassClusters.size());
				possibleInstanceIds.addAll(instanceClassClusters.get(rand));
			} else {
				for (int iid : unsolved) {
					if (instanceJobs.get(iid) == null || instanceJobs.get(iid).size() < unsolvedInstancesMaxJobs) {
						possibleInstanceIds.add(iid);
					}
				}
			}
		}
		
		pacc.log("[ClusterRacing] Initialize Solver Configuration, possible instance ids: " + possibleInstanceIds.size() + " clustering size: " + c.size());
		if (!possibleInstanceIds.isEmpty()) {
			for (int i = 0; i < parameters.getMinRuns() - sc.getJobCount() && !possibleInstanceIds.isEmpty(); i++) {
				int rand = rng.nextInt(possibleInstanceIds.size());
				int instanceid = possibleInstanceIds.get(rand);
				addRuns(sc, instanceid, 0);
				unsolved.remove(instanceid);
				possibleInstanceIds.remove(rand);
				initialRandomJobs--;
				
				if (useInstanceClassClusters) {
					List<Integer> cluster;
					if ((cluster = cachedClustering.get(sc.getIdSolverConfiguration())) != null) {
						cluster.add(instanceid);
					}
				}
			}
		} else if (c.isEmpty()) {
			solverConfigurationsToInitialize.add(sc);
			return ;
		} else if (!solverConfigurationsToInitialize.isEmpty()) {
			List<SolverConfiguration> list = new LinkedList<SolverConfiguration>();
			list.addAll(solverConfigurationsToInitialize);
			solverConfigurationsToInitialize.clear();
			for (SolverConfiguration tmp : list) {
				initializeSolverConfiguration(tmp);
			}
		}
		if (!(sc instanceof SolverConfigurationIBS) && c.size() > maxRacingClusters) {
			pacc.log("[ClusterRacing] Initialize Solver Configuration: too many clusters " + c.size() + ", removing some.");
			List<Integer> scids = new LinkedList<Integer>();
			scids.addAll(c.keySet());
			while (c.size() > maxRacingClusters) {
				int rand = rng.nextInt(scids.size());
				c.remove(scids.get(rand));
				scids.remove(rand);
			}
		}
		
		SolverConfigurationMetaData data = null;
		if (scs.containsKey(sc.getIdSolverConfiguration())) {
			data = scs.get(sc.getIdSolverConfiguration());
		} else {
			data = new SolverConfigurationMetaData(sc, unsolved, c);
			scs.put(sc.getIdSolverConfiguration(), data);
		}
		//if (!data.racingScs.isEmpty()) {
			
		if (useInstanceClassClusters && cachedClustering.containsKey(sc.getIdSolverConfiguration())) {
			// don't race
			if (!data.racingScs.isEmpty()) {
				for (int scid : data.racingScs) {
					scs.get(scid).competitors.remove(new Integer(sc.getIdSolverConfiguration()));
				}
				data.racingScs.clear();
			}
		} else {
			boolean racedAll = true;
			// race(..) can modify racingScs list
			LinkedList<Integer> copy = new LinkedList<Integer>();
			copy.addAll(data.racingScs);
			for (Integer id : copy) {
				SolverConfiguration inc = scs.get(id).sc;
				racedAll &= race(inc, data);
			}
			if (!racedAll) {
				pacc.addSolverConfigurationToListNewSC(sc);
			}
		}
		//}
			updateName(data);
	}
	
	private void updatePoints(int scid, int num) {
		Integer numPoints = incumbentPoints.get(scid);
		if (numPoints == null) {
			numPoints = 0;
		}
		numPoints+=num;
		incumbentPoints.put(scid, numPoints);
		pacc.log("[ClusterRacing] Solver config " + scid + " has now " + numPoints + " points.");
	}
	
	private void addUnsolvedJobs(SolverConfigurationMetaData data) throws Exception {
		if (fixClusteringCPUTime != -1 && pacc.getCumulatedCPUTime() >= fixClusteringCPUTime) {
			return ;
		}
		Integer numPoints = incumbentPoints.get(data.sc.getIdSolverConfiguration());
		if (numPoints == null) {
			numPoints = 0;
		}
		pacc.log("[ClusterRacing] Solver Configuration " + data.sc.getIdSolverConfiguration() + " wants unsolved jobs, has " + numPoints + " points.");
		List<Integer> possibleInstances = new LinkedList<Integer>();
		
		if (useInstanceClassClusters) {
			List<Integer> cluster = cachedClustering.get(data.sc.getIdSolverConfiguration());
			if (cluster != null) {
				for (Set<Integer> icluster : instanceClassClusters) {
					if (icluster.contains(cluster.get(0))) {
						possibleInstances.addAll(icluster);
						possibleInstances.removeAll(cluster);
						break;
					}
				}
			}
		} else {
			if (numPoints > unsolvedInstancesMinPoints) {
				Set<Integer> unsolvedInstances = clustering.getNotUsedInstances();
				// data.unsolved
				// if (!unsolvedInstances.isEmpty()) {

				// TODO: cache next info!
				HashSet<Integer> hasInstanceRuns = new HashSet<Integer>();
				for (ExperimentResult er : data.sc.getJobs()) {
					hasInstanceRuns.add(er.getInstanceId());
				}
				// TODO: use 1.0-clustering??
				boolean racedSome = false;
				boolean racedAll = true;

				for (Entry<Integer, List<Integer>> e : getClustering().entrySet()) {
					// TODO: can this happen? + inefficient?
					if (data.racingScs.contains(new Integer(e.getKey()))) {
						continue;
					}
					Set<Integer> instanceIds = new HashSet<Integer>();
					instanceIds.addAll(e.getValue());

					List<ExperimentResult> hisJobs = new LinkedList<ExperimentResult>();
					List<ExperimentResult> myJobs = new LinkedList<ExperimentResult>();

					for (ExperimentResult er : data.sc.getJobs()) {
						if (instanceIds.contains(er.getInstanceId())) {
							myJobs.add(er);
						}
					}
					boolean incHasMoreRuns = false;
					SolverConfigurationMetaData inc = scs.get(e.getKey());
					for (ExperimentResult er : inc.sc.getJobs()) {
						if (instanceIds.contains(er.getInstanceId())) {
							if (hasInstanceRuns.contains(er.getInstanceId())) {
								hisJobs.add(er);
							} else {
								incHasMoreRuns = true;
							}
						}
					}

					if (incHasMoreRuns && parameters.getStatistics().compare(hisJobs, myJobs) <= 0) {
						pacc.log("[ClusterRacing] Racing against " + e.getKey());
						data.c.put(e.getKey(), e.getValue());
						data.racingScs.add(e.getKey());

						inc.competitors.add(data.sc.getIdSolverConfiguration());

						racedAll &= this.race(inc.sc, data);
						racedSome = true;
					}
				}
				if (racedSome && !racedAll) {
					pacc.addSolverConfigurationToListNewSC(data.sc);
				}
				// } else {
				possibleInstances.addAll(unsolvedInstances);
				// }
			} else {
				/*
				 * HashSet<Integer> currentUnsolved =
				 * clustering.getNotUsedInstances(); for (Integer iid :
				 * data.unsolved) { if (!currentUnsolved.contains(iid)) {
				 * possibleInstances.add(iid); } }
				 */
			}
		}
		for (ExperimentResult er : data.sc.getJobs()) {
			possibleInstances.remove(new Integer(er.getInstanceId()));
		}
		
		int numInstances = incumbentWinnerInstances;
		
		while (numInstances > 0 && !possibleInstances.isEmpty()) {
			int rand = rng.nextInt(possibleInstances.size());
			int iid = possibleInstances.get(rand);
			pacc.log("[ClusterRacing] Gets " + iid);
			addRuns(data.sc, iid, Integer.MAX_VALUE - data.sc.getIdSolverConfiguration());
			possibleInstances.remove(rand);
			numInstances--;
			
			if (useInstanceClassClusters) {
				cachedClustering.get(data.sc.getIdSolverConfiguration()).add(iid);
			}
		}
	}
	
	/*private int compareToMedian(List<ExperimentResult> list) {
		float median_sum = 0.f;
		float sum = 0.f;
		for (ExperimentResult er : list) {
			if (!instanceMedianTimeCorrect.containsKey(er.getInstanceId())) {
				continue;
			}
			// TODO: cost + resultTime + etc.
			if (er.getResultCode().isCorrect()) {
				sum += er.getCost();
			} else {
				//TODO: cost
				sum += er.getCPUTimeLimit();
			}
			median_sum += instanceMedianTimeCorrect.get(er.getInstanceId());
		}
		pacc.log("[ClusterRacing] Median_sum = " + median_sum + " sum = " + sum);
		if (sum < median_sum) {
			return -1;
		} else if (sum > median_sum) {
			return 1;
		} else {
			return 0;
		}
	}*/
	
	private boolean race(SolverConfiguration incumbent, SolverConfigurationMetaData data) throws Exception {
		pacc.log("[ClusterRacing] Race - incumbent: " + incumbent.getIdSolverConfiguration() + ", competitor: " + data.sc.getIdSolverConfiguration());
		List<Integer> instanceIds = new LinkedList<Integer>();
		instanceIds.addAll(data.c.get(incumbent.getIdSolverConfiguration()));
		
		HashMap<Integer, List<ExperimentResult>> myJobs = new HashMap<Integer, List<ExperimentResult>>();
		
		List<ExperimentResult> hisJobsAll = new LinkedList<ExperimentResult>();
		List<ExperimentResult> myJobsAll = new LinkedList<ExperimentResult>();
		
		for (int id : instanceIds) {
			myJobs.put(id, new LinkedList<ExperimentResult>());
		}
		
		for (ExperimentResult er : data.sc.getJobs()) {
			if (myJobs.containsKey(er.getInstanceId())) {
				myJobsAll.add(er);
				myJobs.get(er.getInstanceId()).add(er);
			}
		}
		
		boolean jobsFinished = true;
		for (ExperimentResult er : incumbent.getJobs()) {
			if (myJobs.containsKey(er.getInstanceId()) && !myJobs.get(er.getInstanceId()).isEmpty()) {
				if (er.getStatus().equals(StatusCode.RUNNING) || er.getStatus().equals(StatusCode.NOT_STARTED)) {
					jobsFinished = false;
					break;
				}
				hisJobsAll.add(er);
			} 
		}
		if (!jobsFinished) {
			pacc.log("[ClusterRacing] Can't race right now, incumbent has jobs running..");
			return false;
		}
		
		if (myJobsAll.isEmpty()) {
			pacc.log("[ClusterRacing] This is the first race iteration! Num instance ids: " + instanceIds.size());
			//for (int i = 0; i < 4; i++) {
				int rand = rng.nextInt(instanceIds.size());
				int instanceid = instanceIds.get(rand);
			//	instanceIds.remove(rand);
				addRuns(data.sc, instanceid, Integer.MAX_VALUE - data.sc.getIdSolverConfiguration());
				pacc.log("Competitor gets random job (" + instanceid + ")");
			//	if (instanceIds.isEmpty())
			//		break;
			//}
		} else {
			List<Integer> instances = new LinkedList<Integer>();
			for (Entry<Integer, List<ExperimentResult>> e : myJobs.entrySet()) {
				if (!e.getValue().isEmpty()) {
					instances.add(e.getKey());
				}
			}
			pacc.log("[ClusterRacing] Inc (" + incumbent.getIdSolverConfiguration() + ") Cost: " + parameters.getStatistics().getCostFunction().calculateCost(hisJobsAll) + " - Competitor (" + data.sc.getIdSolverConfiguration() + ") Cost: " + parameters.getStatistics().getCostFunction().calculateCost(myJobsAll));
			pacc.log("[ClusterRacing] Instances: " + instances);
			boolean removeScFromRace = false;
			//int comp = compareToMedian(myJobsAll);
			int comp = parameters.getStatistics().compare(hisJobsAll, myJobsAll);
			
			if (comp <= 0) {
				// race goes on..
				pacc.log("=> better than incumbent.. trying to find more instances..");
				
				
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
					pacc.log("[ClusterRacing] No more instances for " + data.sc.getIdSolverConfiguration() + " vs " + incumbent.getIdSolverConfiguration());

					if (useInstanceClassClusters || (fixClusteringCPUTime != -1 && pacc.getCumulatedCPUTime() >= fixClusteringCPUTime && cachedClustering != null)) {
						pacc.log("[ClusterRacing] Clustering is fixed.");
						Set<Integer> tmpClustering = new HashSet<Integer>();
						tmpClustering.addAll(data.c.get(incumbent.getIdSolverConfiguration()));
						List<Integer> realClustering = getClustering().get(incumbent.getIdSolverConfiguration());
						
						if (realClustering == null) {
							pacc.log("[ClusterRacing] Cluster doesn't exist anymore, searching for new clusters");
							Set<Integer> iids = new HashSet<Integer>();
							iids.addAll(data.c.get(incumbent.getIdSolverConfiguration()));
							
							HashMap<Integer, List<Integer>> _c = new HashMap<Integer, List<Integer>>();
							for (Entry<Integer, List<Integer>> e : getClustering().entrySet()) {
								for (int iid : e.getValue()) {
									if (iids.contains(iid)) {
										_c.put(e.getKey(), e.getValue());
										break;
									}
								}
							}
							
							for (Entry<Integer, List<Integer>> e : _c.entrySet()) {
								iids.addAll(e.getValue());
							}
							
							for (int i = data.racingScs.size()-1; i >= 0; i--) {
								for (int iid : data.c.get(data.racingScs.get(i))) {
									if (iids.contains(iid)) {
										data.racingScs.remove(i);
										break;
									}
								}
							}
							
							for (Entry<Integer, List<Integer>> e : _c.entrySet()) {
								data.c.put(e.getKey(), e.getValue());
								data.racingScs.add(e.getKey());
							}
							pacc.log("[ClusterRacing] Found " + _c.size() + " clusters. Racing incumbents.");
							
							boolean res = true;
							for (Integer scid : _c.keySet()) {
								res &= race(scs.get(scid).sc, data);
							}
							return res;
						} else {
							Set<Integer> realClusteringSet = new HashSet<Integer>();
							realClusteringSet.addAll(realClustering);
							realClusteringSet.removeAll(tmpClustering);

							if (realClusteringSet.isEmpty()) {
								pacc.log("[ClusterRacing] DEBUG " + tmpClustering.size() + " " + realClustering.size() + " " + data.sc.getJobs().size() + " " + incumbent.getJobs().size());
								pacc.log("[ClusterRacing] Competitor is now incumbent of this cluster.");
								this.cachedClustering.remove(incumbent.getIdSolverConfiguration());
								
								List<Integer> tmpCluster = cachedClustering.get(data.sc.getIdSolverConfiguration());
								if (tmpCluster != null) {
									tmpCluster.addAll(realClustering);
								} else {
									this.cachedClustering.put(data.sc.getIdSolverConfiguration(), realClustering);
								}
							} else {
								pacc.log("[ClusterRacing] Cluster of incumbent changed, using new cluster to race.");
								data.c.get(incumbent.getIdSolverConfiguration()).clear();
								data.c.get(incumbent.getIdSolverConfiguration()).addAll(realClustering);
								return race(incumbent, data);
							}
						}
					}// else {
						// comp = parameters.getStatistics().compare(hisJobsAll,
						// myJobsAll);
						//if (comp < 0) {
						//	updatePoints(incumbent.getIdSolverConfiguration(), -1);
						//} else if (comp > 0 && icount >= 2) {
						if (comp == 0) {
							// TODO: icount adaptive?
							updatePoints(incumbent.getIdSolverConfiguration(), 1);
							addUnsolvedJobs(scs.get(incumbent.getIdSolverConfiguration()));
						} else {
							updatePoints(incumbent.getIdSolverConfiguration(), -1);
						}

						addUnsolvedJobs(data);
					//}
					removeScFromRace = true;
				} else {
					pacc.log("[ClusterRacing] Found instances to race: " + possibleInstances);
					for (int i = 0; i < icount && !possibleInstances.isEmpty(); i++) {
						int rand = rng.nextInt(possibleInstances.size());
						addRuns(data.sc, possibleInstances.get(rand), Integer.MAX_VALUE - data.sc.getIdSolverConfiguration());
						possibleInstances.remove(rand);
					}
				}
			} else {
				// lost.
				
				int icount = 0;
				for (int id : instanceIds) {
					if (!myJobs.get(id).isEmpty()) {
						icount++;
					}
				}
				
				pacc.log("[ClusterRacing] Solver Configuration " + data.sc.getIdSolverConfiguration() + " lost against " + incumbent.getIdSolverConfiguration());
				
				// TODO: really?! Parameter? Adaptive?
				if (icount >= 2) {
					updatePoints(incumbent.getIdSolverConfiguration(), 1);
				}
				addUnsolvedJobs(scs.get(incumbent.getIdSolverConfiguration()));
				
				removeScFromRace = true;
			}
			if (removeScFromRace) {
				data.racingScs.remove(new Integer(incumbent.getIdSolverConfiguration()));
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
		return true;
	}
	
	
	private class SolverConfigurationMetaData {
		SolverConfiguration sc;
		Set<Integer> unsolved;
		HashMap<Integer, List<Integer>> c;
		List<Integer> racingScs;
		List<Integer> competitors;
		
		public SolverConfigurationMetaData(SolverConfiguration sc, Set<Integer> unsolved, HashMap<Integer, List<Integer>> c) {
			this.sc = sc;
			this.competitors = new LinkedList<Integer>();
			update(unsolved, c);
		}
		
		public void update(Set<Integer> unsolved, HashMap<Integer, List<Integer>> c) {
			this.unsolved = unsolved;
			this.c = c;
			this.racingScs = new LinkedList<Integer>();
			if (c != null) {
				if (sc instanceof SolverConfigurationIBS && !((SolverConfigurationIBS) sc).preferredInstanceIds.isEmpty()) {
					
					for (Entry<Integer, List<Integer>> e : c.entrySet()) {
						if (e.getKey().equals(sc.getIdSolverConfiguration())) {
							continue;
						}
						
						boolean add = false;
						for (Integer iid : e.getValue()) {
							if (((SolverConfigurationIBS) sc).preferredInstanceIds.contains(iid)) {
								add = true;
								break;
							}
						}
						if (add) {
							this.racingScs.add(e.getKey());
						}
					}
					
					pacc.log("[ClusterRacing] Num racing scs for new solver configuration with preferred instances: " + this.racingScs.size());
				} else {
					this.racingScs.addAll(c.keySet());
					this.racingScs.remove(new Integer(sc.getIdSolverConfiguration()));
				}
			}
			for (Integer i : racingScs) {
				scs.get(i).competitors.add(sc.getIdSolverConfiguration());
			}
		}
	}
	
	private Double median(List<ExperimentResult> jobs, boolean onlyCorrect) {
		List<ExperimentResult> results;
		if (onlyCorrect) {
			results = new LinkedList<ExperimentResult>();
			for (ExperimentResult er : jobs) {
				if (er.getResultCode().isCorrect()) {
					if (maxCost != null && maxCost > 0) {
						if (par1.singleCost(er) <= maxCost) {
							results.add(er);
						}
					} else {
						results.add(er);
					}
				}
			}
		} else {
			results = jobs;
		}
		
		if (results.isEmpty()) {
			return null;
		}
		return median.calculateCost(results);
		/*Collections.sort(results, new Comparator<ExperimentResult>() {

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
		
		ExperimentResult m = results.get(results.size() >> 1);
		if (m.getResultCode().isCorrect()) {
			return m.getResultTime();
		} else {
			return new Float(pacc.getCPUTimeLimit(m.getInstanceId()));
		}*/
	}
	
	public void updateModel(SolverConfiguration sc, int instanceId) {
		List<ExperimentResult> list = new LinkedList<ExperimentResult>();
		boolean solved = false;
		for (ExperimentResult er : sc.getJobs()) {
			if (instanceId == er.getInstanceId()) {
				list.add(er);
				solved |= (er.getResultCode().isCorrect());
			}
		}
		double cost = (solved ? par1.calculateCost(list) : Double.POSITIVE_INFINITY);
		if (maxCost != null && maxCost > 0 && cost > maxCost) {
			cost = Double.POSITIVE_INFINITY;
		}
		clustering.update(sc.getIdSolverConfiguration(), instanceId, cost);
		clusteringChanged = true;
	}


	@Override
	public void jobsFinished(List<ExperimentResult> results) throws Exception {
		pacc.log("[ClusterRacing] " + results.size() + " jobs finished!");
		if (results.isEmpty()) {
			return ;
		}
		for (ExperimentResult result: results) {
			if (!removedSCIds.contains(result.getSolverConfigId())) {
				updateModel(scs.get(result.getSolverConfigId()).sc, result.getInstanceId());
			}
		}
		
		HashSet<Integer> instanceIds = new HashSet<Integer>();
		for (ExperimentResult result : results) {
			instanceIds.add(result.getInstanceId());

			List<ExperimentResult> jobs = instanceJobs.get(result.getInstanceId());
			if (jobs == null) {
				jobs = new LinkedList<ExperimentResult>();
				instanceJobs.put(result.getInstanceId(), jobs);
			}
			boolean found = false;
			for (int i = 0; i < jobs.size(); i++) {
				if (jobs.get(i).getId() == result.getId()) {
					jobs.set(i, result);
					found = true;
					break;
				}
			}
			if (!found) {
				jobs.add(result);
			}
		}
		
		for (int instanceId : instanceIds) {
			List<ExperimentResult> jobs = instanceJobs.get(instanceId);
			for (int i = jobs.size() - 1; i >= 0; i--) {
				if (removedSCIds.contains(jobs.get(i).getSolverConfigId())) {
					jobs.remove(i);
				}
			}
			Double m = median(jobs, false);
			Double mc = median(jobs, true);
			
			instanceMedianTime.put(instanceId, m == null ? new Double(pacc.getCPUTimeLimit(instanceId)) : m);
			instanceMedianTimeCorrect.put(instanceId, mc == null ? new Double(pacc.getCPUTimeLimit(instanceId)) : mc);
		}
		
		if (useAdaptiveInstanceTimeouts) {
			
			for (int instanceId : instanceIds) {
				List<ExperimentResult> jobs = instanceJobs.get(instanceId);
			
				if (jobs.size() > instanceTimeoutsMinNumJobs) {
					int current_limit = pacc.getCPUTimeLimit(instanceId);
					int m = (int) Math.round(instanceMedianTime.get(instanceId) * instanceTimeoutFactor);
					if (m < 1)
						m = 1;
					if (m < current_limit) {
						List<SolverConfiguration> tmp = new LinkedList<SolverConfiguration>();
						for (SolverConfigurationMetaData data : scs.values()) {
							if (!removedSCIds.contains(data.sc.getIdSolverConfiguration())) {
								tmp.add(data.sc);
							}
						}
						pacc.log("[ClusterRacing] Setting CPU Timelimit of " + instanceId + " to " + m + " (lower).");
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
							pacc.log("[ClusterRacing] Setting CPU Timelimit of " + instanceId + " to " + m + " (higher).");
							for (SolverConfiguration config : pacc.changeCPUTimeLimit(instanceId, m, tmp, true, true)) {
								pacc.addSolverConfigurationToListNewSC(config);
							}
							
							for (SolverConfiguration sc : tmp) {
								updateModel(sc, instanceId);
							}
						}
					}
					boolean jobsRemoved = false;
					// remove restarted jobs
					for (int i = jobs.size() - 1; i >= 0; i--) {
						if (jobs.get(i).getStatus().equals(StatusCode.NOT_STARTED)) {
							jobs.remove(i);
							jobsRemoved = true;
						}
					}
					
					if (jobsRemoved) {
						Double me = median(jobs, false);
						Double mc = median(jobs, true);

						instanceMedianTime.put(instanceId, me == null ? new Double(pacc.getCPUTimeLimit(instanceId)) : me);
						instanceMedianTimeCorrect.put(instanceId, mc == null ? new Double(pacc.getCPUTimeLimit(instanceId)) : mc);
					}
				}
			}
		}
	}

	
	public double getPerformance() {
		return clustering.getCost(clustering.getClustering(false, false));
	}
}
