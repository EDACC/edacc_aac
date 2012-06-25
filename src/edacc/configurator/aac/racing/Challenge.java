package edacc.configurator.aac.racing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.api.costfunctions.CostFunction;
import edacc.api.costfunctions.Median;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.JobListener;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.configurator.aac.racing.challenge.Clustering;
import edacc.model.Experiment;
import edacc.model.ExperimentResult;
import edacc.model.Instance;
import edacc.model.InstanceDAO;
import edacc.util.Pair;

public class Challenge extends RacingMethods implements JobListener {
	private ArrayList<SolverConfiguration> solverConfigsReadyForQualification;
	private ArrayList<SolverConfiguration> solverConfigsReadyForTournament;
	private ArrayList<Integer> instances;
	private HashSet<Integer> jobIds;
	private HashMap<Integer, List<ExperimentResult>> allJobs;
	private List<Qualification> qualifications;
	private List<Tournament> tournaments;
	private HashMap<Integer, SolverConfigurationMetaData> allSolverConfigs;
	private List<SolverConfiguration> bestSolverConfigs;

	private HashSet<Integer> instancesSolved;
	
	private Clustering clustering;
	
	// parameters	
	private float initialRunsInstanceSolvedPercentage = 1.f;
	private float instanceSolvedThresholdPercentage = 0.2f;
	private int numTournamentWinnerInstances = 40;
	
	private int minBestSCs = 10;
	private int scInitialPoints = 5;
	private int scQualificationWinnerPoints = 1;
	private int scQualificationLoserPoints = -3;
	private int scTournamentWinnerPoints = 5;
	private int scTournamentLoserPoints = -1;
	private int qualificationSCCount = 8;
	private int minQualificationWinners = 2;
	private int tournamentSCCount = 16;
	private int numChallengeInstancesQualification = 7;
	private int numChallengeInstancesTournament = 10;
	private float minInitialSolvedPerc = 0.7f;
	
	// adaptive instance timeouts
	private boolean useAdaptiveInstanceTimeouts = true;
	private float limitCPUTimeFactor = 1.5f;
	private int limitCPUTimeMaxCPUTime = 100;
	private int limitCPUTimeMinRuns = 10;
	
	private class SolverConfigurationMetaData {
		SolverConfiguration solverConfig;
		int points;
		Qualification qualification;
		Tournament tournament;
		int qWinnerCount;
		int qLoserCount;
		int tWinnerCount;
		int tLoserCount;
		
		int state = -1;
		
		public SolverConfigurationMetaData(SolverConfiguration sc) {
			solverConfig = sc;
			points = scInitialPoints;
			qualification = null;
			qWinnerCount = 0;
			qLoserCount = 0;
			tWinnerCount = 0;
			tLoserCount = 0;
		}
	}
	
	public Challenge(AAC pacc, Random rng, API api, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
		super(pacc, rng, api, parameters, firstSCs, referenceSCs);
		pacc.addJobListener(this);
		
		String val;
		if ((val = parameters.getRacingMethodParameters().get("Challenge_initialRunsInstanceSolvedPercentage")) != null)
			initialRunsInstanceSolvedPercentage = Float.parseFloat(val);
		else if ((val = parameters.getRacingMethodParameters().get("Challenge_instanceSolvedThresholdPercentage")) != null)
			instanceSolvedThresholdPercentage = Float.parseFloat(val);
		else if ((val = parameters.getRacingMethodParameters().get("Challenge_numTournamentWinnerInstances")) != null)
			numTournamentWinnerInstances = Integer.parseInt(val);
		else if ((val = parameters.getRacingMethodParameters().get("Challenge_minBestSCs")) != null)
			minBestSCs = Integer.parseInt(val);
		else if ((val = parameters.getRacingMethodParameters().get("Challenge_scInitialPoints")) != null)
			scInitialPoints = Integer.parseInt(val);
		else if ((val = parameters.getRacingMethodParameters().get("Challenge_scQualificationWinnerPoints")) != null)
			scQualificationWinnerPoints = Integer.parseInt(val);
		else if ((val = parameters.getRacingMethodParameters().get("Challenge_scQualificationLoserPoints")) != null)
			scQualificationLoserPoints = Integer.parseInt(val);
		else if ((val = parameters.getRacingMethodParameters().get("Challenge_scTournamentWinnerPoints")) != null)
			scTournamentWinnerPoints = Integer.parseInt(val);
		else if ((val = parameters.getRacingMethodParameters().get("Challenge_scTournamentLoserPoints")) != null)
			scTournamentLoserPoints = Integer.parseInt(val);
		else if ((val = parameters.getRacingMethodParameters().get("Challenge_qualificationSCCount")) != null)
			qualificationSCCount = Integer.parseInt(val);
		else if ((val = parameters.getRacingMethodParameters().get("Challenge_minQualificationWinners")) != null)
			minQualificationWinners = Integer.parseInt(val);
		else if ((val = parameters.getRacingMethodParameters().get("Challenge_tournamentSCCount")) != null)
			tournamentSCCount = Integer.parseInt(val);
		else if ((val = parameters.getRacingMethodParameters().get("Challenge_numChallengeInstancesQualification")) != null)
			numChallengeInstancesQualification = Integer.parseInt(val);
		else if ((val = parameters.getRacingMethodParameters().get("Challenge_numChallengeInstancesTournament")) != null)
			numChallengeInstancesTournament = Integer.parseInt(val);
		else if ((val = parameters.getRacingMethodParameters().get("Challenge_minInitialSolvedPerc")) != null)
			minInitialSolvedPerc = Float.parseFloat(val);
		else if ((val = parameters.getRacingMethodParameters().get("Challenge_useAdaptiveInstanceTimeouts")) != null)
			useAdaptiveInstanceTimeouts = Boolean.parseBoolean(val);
		else if ((val = parameters.getRacingMethodParameters().get("Challenge_limitCPUTimeFactor")) != null)
			limitCPUTimeFactor = Float.parseFloat(val);
		else if ((val = parameters.getRacingMethodParameters().get("Challenge_limitCPUTimeMaxCPUTime")) != null)
			limitCPUTimeMaxCPUTime = Integer.parseInt(val);
		else if ((val = parameters.getRacingMethodParameters().get("Challenge_limitCPUTimeMinRuns")) != null)
			limitCPUTimeMinRuns = Integer.parseInt(val);
			
		solverConfigsReadyForQualification = new ArrayList<SolverConfiguration>();
		solverConfigsReadyForTournament = new ArrayList<SolverConfiguration>();
		instances = new ArrayList<Integer>();
		jobIds = new HashSet<Integer>();
		allJobs = new HashMap<Integer, List<ExperimentResult>>();
		qualifications = new LinkedList<Qualification>();
		tournaments = new LinkedList<Tournament>();
		allSolverConfigs = new HashMap<Integer, SolverConfigurationMetaData>();
		bestSolverConfigs = new ArrayList<SolverConfiguration>();
		
		instancesSolved = new HashSet<Integer>();
		
		for (Instance i : InstanceDAO.getAllByExperimentId(parameters.getIdExperiment())) {
			instances.add(i.getId());
		}
		if (instances.isEmpty()) {
			throw new IllegalArgumentException("No instances selected.");
		}
		for (SolverConfiguration sc : firstSCs) {
			allSolverConfigs.put(sc.getIdSolverConfiguration(), new SolverConfigurationMetaData(sc));
			addInitialRuns(sc);
		}

		clustering = new Clustering(instances, new LinkedList<String>());
	}
	
	private void updateBestSolverConfigs() {
		List<SolverConfigurationMetaData> scData = new ArrayList<SolverConfigurationMetaData>();
		scData.addAll(allSolverConfigs.values());
		Collections.sort(scData, new Comparator<SolverConfigurationMetaData>() {

			@Override
			public int compare(SolverConfigurationMetaData o1, SolverConfigurationMetaData o2) {
				return o1.points - o2.points;
			}
			
		});
		bestSolverConfigs.clear();
		
		for (int i = scData.size() -1; i >= 0; i--) {
			if (scData.get(i).points <= scInitialPoints) {
				break;
			}
			
			if (bestSolverConfigs.size() >= minBestSCs) {
				if (scData.get(i).points != scData.get(i+1).points) {
					break;
				}
			}
			bestSolverConfigs.add(scData.get(i).solverConfig);
		}
	}
	
	private void updateSolverConfigName(SolverConfiguration sc) {
		SolverConfigurationMetaData data = allSolverConfigs.get(sc.getIdSolverConfiguration());
		sc.setNameRacing("points: " + data.points + " tw: " + data.tWinnerCount + " tl: " + data.tLoserCount + " qw: " + data.qWinnerCount + " ql: " + data.qLoserCount);
	}
	
	private void addTournamentWinnerRuns(SolverConfiguration sc) throws Exception {
		
		ArrayList<Pair<Integer, Integer>> instanceCPUTimeLimit = new ArrayList<Pair<Integer, Integer>>();
		
		HashMap<Integer, Integer> instanceNumJobs = new HashMap<Integer, Integer>();
		for (ExperimentResult er : sc.getJobs()) {
			Integer count = instanceNumJobs.get(er.getInstanceId());
			if (count == null) {
				count = 0;
			}
			count++;
			instanceNumJobs.put(er.getInstanceId(), count);
		}
		
		for (Integer i : instances) {
			if (instanceNumJobs.get(i) == null || instanceNumJobs.get(i) < parameters.getParcoursExpansion()) {
				instanceCPUTimeLimit.add(new Pair<Integer, Integer>(i, pacc.getCPUTimeLimit(i)));
			}
		}
		Collections.sort(instanceCPUTimeLimit, new Comparator<Pair<Integer, Integer>>() {

			@Override
			public int compare(Pair<Integer, Integer> arg0, Pair<Integer, Integer> arg1) {
				return arg0.getSecond() - arg1.getSecond();
			}
			
		});
		
		HashMap<Integer, Integer> runCount = new HashMap<Integer, Integer>();
		for (ExperimentResult er : sc.getJobs()) {
			Integer count = runCount.get(er.getInstanceId());
			if (count == null)
				count = 0;
			count++;
			runCount.put(er.getInstanceId(), count);
		}
		
		int num = 0;
		for (int i = instanceCPUTimeLimit.size()-1; i >= 0 && num < numTournamentWinnerInstances; i--) {
			Integer c = runCount.get(instanceCPUTimeLimit.get(i).getFirst());
			if (c == null) c = 0;
			if (c < parameters.getParcoursExpansion()) {
				num++;
				pacc.addJob(sc, Math.abs(rng.nextInt()), instanceCPUTimeLimit.get(i).getFirst(), Integer.MAX_VALUE);
			}
		}
		pacc.addSolverConfigurationToListNewSC(sc);
	}
	
	private void addInitialRuns(SolverConfiguration sc) throws Exception { 		
		HashSet<Integer> instanceIds = new HashSet<Integer>();
		/*if (instancesSolved.size() > instances.size()*instanceSolvedThresholdPercentage) {
			ArrayList<Integer> instancesSolvedList = new ArrayList<Integer>();
			instancesSolvedList.addAll(instancesSolved);
			while (instanceIds.size() < parameters.getMinRuns() * initialRunsInstanceSolvedPercentage && instanceIds.size() < instancesSolved.size() && instanceIds.size() < parameters.getMinRuns()) {
				instanceIds.add(instancesSolvedList.get(rng.nextInt(instancesSolvedList.size())));
			}
		}*/
		HashMap<Integer, List<Integer>> c = clustering.getClustering(false);
		List<Integer> unsolved = new LinkedList<Integer>();
		unsolved.addAll(clustering.getNotUsedInstances());
		
		do {
			if (unsolved.size() > 0.5f * instances.size()) {
				for (int i = 0; i < parameters.getMinRuns() * 0.5f; i++) {
					instanceIds.add(unsolved.get(rng.nextInt(unsolved.size())));
					if (instanceIds.size() == parameters.getMinRuns())
						break;
				}
			}
			
			if (instanceIds.size() == parameters.getMinRuns())
				break;
			for (List<Integer> l : c.values()) {
				instanceIds.add(l.get(rng.nextInt(l.size())));
				if (instanceIds.size() >= parameters.getMinRuns()) {
					break;
				}
			}
			instanceIds.add(unsolved.get(rng.nextInt(unsolved.size())));
		} while (instanceIds.size() < parameters.getMinRuns());
		
		while (instanceIds.size() < parameters.getMinRuns()) {
			instanceIds.add(instances.get(rng.nextInt(instances.size())));
		}
		
		for (Integer instanceId: instanceIds) {
			pacc.addJob(sc, Math.abs(rng.nextInt()), instanceId, 0);
		}
		pacc.addSolverConfigurationToListNewSC(sc);
	}
	

	@Override
	public int compareTo(SolverConfiguration sc1, SolverConfiguration sc2) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public List<SolverConfiguration> getBestSolverConfigurations() {
		List<SolverConfiguration> res = new LinkedList<SolverConfiguration>();
		res.addAll(bestSolverConfigs);
		return res;
	}

	@Override
	public void solverConfigurationsFinished(List<SolverConfiguration> scs) throws Exception {
		for (int i = scs.size()-1; i >= 0; i--) {
			if (!allSolverConfigs.containsKey(scs.get(i).getIdSolverConfiguration())) {
				scs.remove(i);
			}
		}
		
		if (scs.isEmpty()) {
			return;
		}
		
		for (int i = scs.size()-1; i >= 0; i--) {
			SolverConfiguration sc = scs.get(i);
			SolverConfigurationMetaData data = allSolverConfigs.get(sc.getIdSolverConfiguration());
			if (data.state == -1) {
				data.state = 0;
				int successful = 0;
				for (ExperimentResult er : sc.getJobs()) {
					if (er.getResultCode().isCorrect()) {
						successful++;
					}
				}
				if (sc.getJobCount() * minInitialSolvedPerc < successful) {
					// solver config did not solve enough instances..
					scs.remove(i);
					removeSolverConfig(sc.getIdSolverConfiguration());
				}
			}
		}
		
		// add jobs to allJobs which finished here
		for (SolverConfiguration sc : scs) {
			for (ExperimentResult er : sc.getJobs()) {
				if (!jobIds.contains(er.getId())) {
					jobIds.add(er.getId());
					List<ExperimentResult> list = allJobs.get(er.getInstanceId());
					if (list == null) {
						list = new LinkedList<ExperimentResult>();
						allJobs.put(er.getInstanceId(), list);
					}
					list.add(er);
				}
			}
		}
		
		if (useAdaptiveInstanceTimeouts) {
			// determine new limits for instances
			HashMap<Integer, Integer> newLimitsHigher = new HashMap<Integer, Integer>();
			HashMap<Integer, Integer> newLimitsLower = new HashMap<Integer, Integer>();
			for (Integer instanceId : allJobs.keySet()) {
				List<ExperimentResult> results = allJobs.get(instanceId);
				if (results.size() < limitCPUTimeMinRuns) {
					continue;
				}
				boolean instanceSolved = false;
				for (ExperimentResult er : results) {
					if (String.valueOf(er.getResultCode().getResultCode()).startsWith("1")) {
						instanceSolved = true;
						break;
					}
				}

				final Integer currentLimit = pacc.getCPUTimeLimit(instanceId);

				// we have to override the method for a single cost because
				// there may be different time limits used for the runs.
				CostFunction median = new Median(Experiment.Cost.resultTime, true) {

					@Override
					public float singleCost(edacc.model.ExperimentResult job) {
						if (!String.valueOf(job.getResultCode().getResultCode()).startsWith("1")) {
							return currentLimit;
						} else {
							return job.getResultTime();
						}
					}
				};
				int newLimit = Math.round(median.calculateCost(results) * limitCPUTimeFactor);
				if (newLimit < 1) {
					newLimit = 1;
				}
				if (newLimit > limitCPUTimeMaxCPUTime) {
					newLimit = limitCPUTimeMaxCPUTime;
				}

				if (newLimit < currentLimit) {
					newLimitsLower.put(instanceId, newLimit);
				} else if (newLimit != currentLimit && !instanceSolved) {
					newLimitsHigher.put(instanceId, newLimit);
				}
			}

			if (!newLimitsHigher.isEmpty()) {
				// change the cpu time limit only.. we may restart the jobs as
				// soon as the instances are solved.
				for (Integer instanceId : newLimitsHigher.keySet()) {
					pacc.changeCPUTimeLimit(instanceId, newLimitsHigher.get(instanceId), null, false, false);
				}
			}
			if (!newLimitsLower.isEmpty()) {
				// we have to restart jobs here because some jobs may had lower
				// cpu time limit as the limit is now
				List<SolverConfiguration> scsToBeUpdated = new LinkedList<SolverConfiguration>();
				for (SolverConfigurationMetaData data : allSolverConfigs.values()) {
					// TODO: data.points < 0 shouldn't exist?
					if (data.points >= 0) {
						scsToBeUpdated.add(data.solverConfig);
					}
				}

				for (Integer instanceId : newLimitsLower.keySet()) {
					// this method restarts jobs if the cpu time limit for the
					// affected jobs was lower than it is now
					// if it was higher, the jobs are marked as time limit
					// exceeded (only in local cache)
					pacc.changeCPUTimeLimit(instanceId, newLimitsLower.get(instanceId), scsToBeUpdated, true, true);
				}
				for (SolverConfiguration sc : solverConfigsReadyForQualification) {
					allSolverConfigs.get(sc.getIdSolverConfiguration()).state = 0;
				}
				solverConfigsReadyForQualification.clear();
				for (SolverConfiguration sc : solverConfigsReadyForTournament) {
					allSolverConfigs.get(sc.getIdSolverConfiguration()).state = 1;
				}
				solverConfigsReadyForTournament.clear();

				for (SolverConfiguration sc : scsToBeUpdated) {
					pacc.addSolverConfigurationToListNewSC(sc);
				}
				return;
			}
		}
		
		for (SolverConfiguration sc : scs) {
			
			if (!allSolverConfigs.containsKey(sc.getIdSolverConfiguration())) {
				pacc.log("WARNING: Discarding solver config " + sc.getIdSolverConfiguration() + " .. was it removed?");
				continue;
			}
			// TODO: NullPointerException next line!
			Qualification q = allSolverConfigs.get(sc.getIdSolverConfiguration()).qualification;
			Tournament t = allSolverConfigs.get(sc.getIdSolverConfiguration()).tournament;
			if (q != null) {
				q.finished(sc);
			} else if (t != null) {
				t.finished(sc);
			} else {
				int state = allSolverConfigs.get(sc.getIdSolverConfiguration()).state;
				if (state == 0) {
					solverConfigsReadyForQualification.add(sc);
				} else if (state == 1) {
					solverConfigsReadyForTournament.add(sc);
					allSolverConfigs.get(sc.getIdSolverConfiguration()).state = 0;
				}
			}
		}
		
		if (solverConfigsReadyForQualification.size() >= qualificationSCCount) {
			List<SolverConfiguration> qscs = new LinkedList<SolverConfiguration>();
			while (qscs.size() < qualificationSCCount) {
				qscs.add(solverConfigsReadyForQualification.get(0));
				solverConfigsReadyForQualification.remove(0);
			}
			qualifications.add(new Qualification(qscs));
		}
		if (solverConfigsReadyForTournament.size() >= tournamentSCCount) {
			List<SolverConfiguration> tscs = new LinkedList<SolverConfiguration>();
			while (tscs.size() < tournamentSCCount) {
				tscs.add(solverConfigsReadyForTournament.get(0));
				solverConfigsReadyForTournament.remove(0);
			}
			tournaments.add(new Tournament(tscs));
		}
	}

	@Override
	public void solverConfigurationsCreated(List<SolverConfiguration> scs) throws Exception {
		for (SolverConfiguration sc : scs) {
			sc.setFinished(false);
			allSolverConfigs.put(sc.getIdSolverConfiguration(), new SolverConfigurationMetaData(sc));
			addInitialRuns(sc);
		}
		
	}

	@Override
	public int computeOptimalExpansion(int coreCount, int jobs, int listNewSCSize) {
		int res = 0;
		if (coreCount < parameters.getMinCPUCount() || coreCount > parameters.getMaxCPUCount()) {
			pacc.log("w Warning: Current core count is " + coreCount);
		}
		int min_sc = (Math.max(Math.round(4.f * coreCount), 8) - jobs) / parameters.getMinRuns();
		if (min_sc > 0) {
			res = (Math.max(Math.round(6.f * coreCount), 8) - jobs) / parameters.getMinRuns();
		}
		if (listNewSCSize == 0 && res == 0) {
			res = 1;
		}
		return res;
	}
	
	private void startMatch(Match m, int priority) throws Exception {
		HashMap<Integer, Integer> firstRunCount = new HashMap<Integer, Integer>();
		HashMap<Integer, Integer> secondRunCount = new HashMap<Integer, Integer>();
		
		for (ExperimentResult er : m.first.getJobs()) {
			Integer count = firstRunCount.get(er.getInstanceId());
			if (count == null)
				count = 0;
			count++;
			firstRunCount.put(er.getInstanceId(), count);
		}
		
		for (ExperimentResult er : m.second.getJobs()) {
			Integer count = secondRunCount.get(er.getInstanceId());
			if (count == null)
				count = 0;
			count++;
			secondRunCount.put(er.getInstanceId(), count);
		}
		
		for (Integer instanceId : m.instances) {
			if (firstRunCount.get(instanceId) == null || firstRunCount.get(instanceId) < parameters.getParcoursExpansion())
				pacc.addJob(m.first, Math.abs(rng.nextInt()), instanceId, priority);
			if (secondRunCount.get(instanceId) == null || secondRunCount.get(instanceId) < parameters.getParcoursExpansion())
				pacc.addJob(m.second, Math.abs(rng.nextInt()), instanceId, priority);
		}
		pacc.addSolverConfigurationToListNewSC(m.first);
		pacc.addSolverConfigurationToListNewSC(m.second);
		m.setRunning();
	}

	@Override
	public List<String> getParameters() {
		List<String> p = new LinkedList<String>();
		p.add("% initial sc parameters");
		p.add("Challenge_initialRunsInstanceSolvedPercentage = " + initialRunsInstanceSolvedPercentage);
		p.add("Challenge_instanceSolvedThresholdPercentage = " + instanceSolvedThresholdPercentage);
		p.add("Challenge_scInitialPoints = " + scInitialPoints);
		p.add("Challenge_minInitialSolvedPerc = " + minInitialSolvedPerc);
		p.add("% tournament parameters");
		p.add("Challenge_tournamentSCCount = " + tournamentSCCount);
		p.add("Challenge_scTournamentWinnerPoints = " + scTournamentWinnerPoints);
		p.add("Challenge_scTournamentLoserPoints = " + scTournamentLoserPoints);
		p.add("Challenge_numTournamentWinnerInstances = " + numTournamentWinnerInstances);
		p.add("Challenge_numChallengeInstancesTournament = " + numChallengeInstancesTournament);
		p.add("% qualification parameters");
		p.add("Challenge_qualificationSCCount = " + qualificationSCCount);
		p.add("Challenge_scQualificationWinnerPoints = " + scQualificationWinnerPoints);
		p.add("Challenge_scQualificationLoserPoints = " + scQualificationLoserPoints);
		p.add("Challenge_minQualificationWinners = " + minQualificationWinners);
		p.add("Challenge_numChallengeInstancesQualification = " + numChallengeInstancesQualification);
		p.add("% adaptive instance timeout parameters");
		p.add("Challenge_useAdaptiveInstanceTimeouts = " + useAdaptiveInstanceTimeouts);
		p.add("Challenge_limitCPUTimeFactor = " + limitCPUTimeFactor);
		p.add("Challenge_limitCPUTimeMaxCPUTime = " + limitCPUTimeMaxCPUTime);
		p.add("Challenge_limitCPUTimeMinRuns = " + limitCPUTimeMinRuns);
		p.add("% misc parameters");
		p.add("Challenge_minBestSCs = " + minBestSCs);
		return p;
	}

	private static int qidCounter = 0;
	
	private class Qualification {
		HashMap<Integer, Integer> scIdPoints = new HashMap<Integer, Integer>();
		List<Match> matches = new ArrayList<Match>();
		
		int qid;
		public Qualification(List<SolverConfiguration> scs) throws Exception {
			qid = qidCounter++;
			log("Starting qualification with " + scs.size() + " solver configs.");
			for (SolverConfiguration sc : scs) {
				allSolverConfigs.get(sc.getIdSolverConfiguration()).qualification = this;
				scIdPoints.put(sc.getIdSolverConfiguration(), 0);
			}
			log("Determining pairing..");
			Collections.shuffle(scs, rng);
			for (int i = 0; i < scs.size(); i++) {
				for (int j = i+1; j < scs.size(); j++) {
					matches.add(new Match(scs.get(i), scs.get(j)));
				}
			}
			log("Starting matches..");
			checkMatches();
		}
		
		public void checkMatches() throws Exception {
			for (Match match : matches) {
				if (!match.state.equals(State.NEW))
					continue;
				if (!pacc.listNewSCContains(match.first) && !pacc.listNewSCContains(match.second)) {
					List<Integer> instanceIds = getChallengeInstances(match.first, numChallengeInstancesQualification);
					instanceIds.addAll(getChallengeInstances(match.second, numChallengeInstancesQualification));
					match.instances.addAll(instanceIds);
					startMatch(match, (Integer.MAX_VALUE >> 1) - qid);
				}
			}
		}
		
		public void finished(SolverConfiguration sc) throws Exception {
			for (int i = matches.size()-1; i >= 0; i--) {			
				Match m = matches.get(i);
				if (!m.state.equals(State.RUNNING)) {
					continue;
				}
				if (sc == m.first) {
					m.firstFinished = true;
				} else if (sc == m.second) {
					m.secondFinished = true;
				}
				if (m.firstFinished && m.secondFinished) {
					m.matchFinished();
					//log("Match " + m.first.getIdSolverConfiguration() + " vs " + m.second.getIdSolverConfiguration() + " finished; score " + m.firstPoints + " : " + m.secondPoints);
					int firstPoints = scIdPoints.get(m.first.getIdSolverConfiguration());
					int secondPoints = scIdPoints.get(m.second.getIdSolverConfiguration());
					if (m.getFirstPoints() < m.getSecondPoints()) {
						secondPoints += 3;
						scIdPoints.put(m.second.getIdSolverConfiguration(), secondPoints);
						//log("" + m.second.getIdSolverConfiguration() + " gets 3 points for winning the match.");
					} else if (m.getSecondPoints() < m.getFirstPoints()) {
						firstPoints += 3;
						scIdPoints.put(m.first.getIdSolverConfiguration(), firstPoints);
						//log("" + m.first.getIdSolverConfiguration() + " gets 3 points for winning the match.");
					} else {
						firstPoints += 1;
						secondPoints += 1;
						scIdPoints.put(m.second.getIdSolverConfiguration(), secondPoints);
						scIdPoints.put(m.first.getIdSolverConfiguration(), firstPoints);
						//log("Both get 1 point for draw.");
					}
					//log("" + m.first.getIdSolverConfiguration() + " has " + firstPoints + " points.");
					//log("" + m.second.getIdSolverConfiguration() + " has " + secondPoints + " points.");
					
					matches.remove(i);
				}
			}
			if (matches.isEmpty()) {
				log("All matches finished.");
				log("Determining the best " + minQualificationWinners + " solver configurations..");
				List<Pair<Integer, Integer>> solverConfigPointList = new ArrayList<Pair<Integer, Integer>>();
				for (Integer scId : scIdPoints.keySet()) {
					solverConfigPointList.add(new Pair<Integer, Integer>(scId, scIdPoints.get(scId)));
				}
				
				Collections.sort(solverConfigPointList, new Comparator<Pair<Integer, Integer>>() {

					@Override
					public int compare(Pair<Integer, Integer> arg0, Pair<Integer, Integer> arg1) {
						return arg0.getSecond() - arg1.getSecond();
					}
					
				});
				log("Final table:");
				for (int i = solverConfigPointList.size()-1; i >= 0; i--) {
					log((solverConfigPointList.size()-i) + ".) " + solverConfigPointList.get(i).getFirst() + ": " + solverConfigPointList.get(i).getSecond());
				}
				
				
				List<Integer> scWinners = new ArrayList<Integer>();
				for (int i = solverConfigPointList.size() -1; i >= 0; i--) {
					if (scWinners.size() >= minQualificationWinners) {
						if (!solverConfigPointList.get(i+1).getSecond().equals(solverConfigPointList.get(i).getSecond())) {
							break;
						}
					}
					scWinners.add(solverConfigPointList.get(i).getFirst());
					log("Adding " + solverConfigPointList.get(i).getFirst() + " with " + solverConfigPointList.get(i).getSecond() + " points.");
				}
				HashSet<Integer> scLosers = new HashSet<Integer>();
				for (Integer scId : scIdPoints.keySet()) {
					scLosers.add(scId);
					allSolverConfigs.get(scId).qualification = null;
				}
				for (Integer scId : scWinners) {
					scLosers.remove(scId);
					allSolverConfigs.get(scId).points += scQualificationWinnerPoints;
					allSolverConfigs.get(scId).qWinnerCount++;
					
					updateSolverConfigName(allSolverConfigs.get(scId).solverConfig);
					solverConfigsReadyForTournament.add(allSolverConfigs.get(scId).solverConfig);
				}
				for (Integer scId : scLosers) {
					allSolverConfigs.get(scId).points += scQualificationLoserPoints;
					allSolverConfigs.get(scId).qLoserCount++;
					updateSolverConfigName(allSolverConfigs.get(scId).solverConfig);
					if (allSolverConfigs.get(scId).points < 0) {
						removeSolverConfig(scId);
					} else {
						solverConfigsReadyForQualification.add(allSolverConfigs.get(scId).solverConfig);
					}
				}
				updateBestSolverConfigs();
			}
			checkMatches();
		}
		
		private void log(String message) {
			pacc.log("[Qualification " + qid + "] " + message);
		}
	}
	
	private void removeSolverConfig(int scId) {
		pacc.log("[Challenge] Solver configuration " + scId + " has < 0 points -> removing");
		// we remove the jobs from allJobs for this solver config
		for (ExperimentResult er : allSolverConfigs.get(scId).solverConfig.getJobs()) {
			boolean found = false;
			List<ExperimentResult> results = allJobs.get(er.getInstanceId());
			if (results == null) {
				continue;
			}
			for (int i = 0; i < results.size(); i++) {
				if (results.get(i).getId() == er.getId()) {
					results.remove(i);
					found = true;
					break;
				}
			}
			if (!found) {
				pacc.log("[Challenge] (DEBUG) ERROR removeSolverConfig() scid: " + scId + " didn't find job " + er.getId());
			}
		}
		// remove the solver config
		allSolverConfigs.remove(scId);
		clustering.remove(scId);
	}
	
	private List<Integer> getChallengeInstances(SolverConfiguration sc, int num) {
		List<Integer> res = new ArrayList<Integer>();
		HashMap<Integer, List<ExperimentResult>> instanceJobs = new HashMap<Integer, List<ExperimentResult>>();
		for (ExperimentResult er : sc.getJobs()) {
			if (!String.valueOf(er.getResultCode().getResultCode()).startsWith("1")) {
				continue;
			}
			List<ExperimentResult> list = instanceJobs.get(er.getInstanceId());
			if (list == null) {
				list = new LinkedList<ExperimentResult>();
				instanceJobs.put(er.getInstanceId(), list);
			}
			list.add(er);
		}
		
		List<Pair<Integer, List<ExperimentResult>>> instanceJobList = new ArrayList<Pair<Integer, List<ExperimentResult>>>();
		for (Integer instanceId : instanceJobs.keySet()) {
			instanceJobList.add(new Pair<Integer, List<ExperimentResult>>(instanceId, instanceJobs.get(instanceId)));
		}
		
		Collections.sort(instanceJobList, new Comparator<Pair<Integer, List<ExperimentResult>>>() {

			@Override
			public int compare(Pair<Integer, List<ExperimentResult>> o1, Pair<Integer, List<ExperimentResult>> o2) {
				float firstVal = parameters.getStatistics().getCostFunction().calculateCost(o1.getSecond());
				float secondVal = parameters.getStatistics().getCostFunction().calculateCost(o2.getSecond());
				
				firstVal -= pacc.getCPUTimeLimit(o1.getFirst()); //parameters.getStatistics().getCostFunction().calculateCost(allJobs.get(o1.getFirst()));
				secondVal -= pacc.getCPUTimeLimit(o2.getFirst()); //parameters.getStatistics().getCostFunction().calculateCost(allJobs.get(o2.getFirst()));
				
				// normalize
				firstVal /= pacc.getCPUTimeLimit(o1.getFirst());
				secondVal /= pacc.getCPUTimeLimit(o2.getFirst());
				
				if (firstVal < secondVal) {
					return (parameters.getStatistics().getCostFunction().getMinimize() ? 1 : -1);
				} else if (firstVal > secondVal) {
					return (parameters.getStatistics().getCostFunction().getMinimize() ? -1 : 1);
				} else {
					return 0;
				}
			}
			
		});
		
		for (int i = 0; i < num && i < instanceJobList.size(); i++) {
			res.add(instanceJobList.get(i).getFirst());
		}
		
		return res;
	}
	
	private static int tidCounter = 0;
	
	private class Tournament {
		int tid;
		Match[] matches;
		List<SolverConfiguration> solverConfigs;
		
		public Tournament(List<SolverConfiguration> scs) throws Exception {
			tid = tidCounter++;
			int check = scs.size();
			this.solverConfigs = scs;
			while (check != 1) {
				if ((check & 1) != 0) {
					throw new IllegalArgumentException("Invalid size for tournament.");
				}
				check >>= 1;
			}
			log("Starting tournament with " + scs.size() + " solver configs.");
			for (SolverConfiguration sc : scs) {
				allSolverConfigs.get(sc.getIdSolverConfiguration()).tournament = this;
			}
			Collections.shuffle(scs, rng);
			matches = new Match[scs.size() >> 1];
			for (int i = 0; i < scs.size() >> 1; i++) {
				matches[i] = new Match(scs.get(2*i), scs.get(2*i+1));
			}
			checkMatches();
		}
		
		public void checkMatches() throws Exception {
			for (Match match : matches) {
				if (!match.state.equals(State.NEW))
					continue;
				if (!pacc.listNewSCContains(match.first) && !pacc.listNewSCContains(match.second)) {
					List<Integer> instanceIds = getChallengeInstances(match.first, numChallengeInstancesTournament);
					instanceIds.addAll(getChallengeInstances(match.second, numChallengeInstancesTournament));
					match.instances.addAll(instanceIds);
					startMatch(match, Integer.MAX_VALUE - tid);
				}
			}
		}
		
		public void finished(SolverConfiguration sc) throws Exception {
			for (Match m : matches) {
				if (!m.state.equals(State.RUNNING)) {
					continue;
				}
				if (m.first == sc) {
					m.firstFinished = true;
				} else if (m.second == sc) {
					m.secondFinished = true;
				}
				if (m.firstFinished && m.secondFinished) {
					m.matchFinished();
					log("Match " + m.first.getIdSolverConfiguration() + " vs " + m.second.getIdSolverConfiguration() + " finished; score " + m.firstPoints + " : " + m.secondPoints);
				}
			}
			boolean allFinished = true;
			for (Match m : matches) {
				if (!m.state.equals(State.FINISHED)) {
					allFinished = false;
					break;
				}
			}
			if (allFinished) {
				log("All solver configurations have finished their matches.");
				if (matches.length == 1) {
					log("Tournament finished ...");
					Match m = matches[0];
					SolverConfiguration winner;
					log("Match: " + m.first.getIdSolverConfiguration() + " vs " + m.second.getIdSolverConfiguration() + " ended " + m.firstPoints + " : " + m.secondPoints);
					if (m.getFirstPoints() > m.getSecondPoints()) {
						winner = m.first;
					} else if (m.getSecondPoints() > m.getFirstPoints()) {
						winner = m.second;
					} else {
						log("no winner detected.. choosing randomly.");
						if (rng.nextDouble() < 0.5) {
							winner = m.first;
						} else {
							winner = m.second;
						}
					}
							
					for (SolverConfiguration config : solverConfigs) {
						allSolverConfigs.get(config.getIdSolverConfiguration()).tournament = null;
						if (config != winner) {
							allSolverConfigs.get(config.getIdSolverConfiguration()).points += scTournamentLoserPoints;
							allSolverConfigs.get(config.getIdSolverConfiguration()).tLoserCount++;
						} else {
							allSolverConfigs.get(winner.getIdSolverConfiguration()).points += scTournamentWinnerPoints;
							allSolverConfigs.get(winner.getIdSolverConfiguration()).tWinnerCount++;
						}
						updateSolverConfigName(config);
						if (allSolverConfigs.get(config.getIdSolverConfiguration()).points < 0) {
							removeSolverConfig(config.getIdSolverConfiguration());
						} else {
							// winner will be added later
							if (config != winner) {
								solverConfigsReadyForQualification.add(allSolverConfigs.get(config.getIdSolverConfiguration()).solverConfig);
							}
						}
					}
					addTournamentWinnerRuns(winner);
					updateBestSolverConfigs();
				} else {
					Match[] newMatches = new Match[matches.length >> 1];
					for (int i = 0; i < newMatches.length; i++) {
						Match firstMatch = matches[2*i];
						Match secondMatch = matches[2*i+1];
						SolverConfiguration firstSC, secondSC;
						log("Match: " + firstMatch.first.getIdSolverConfiguration() + " vs " + firstMatch.second.getIdSolverConfiguration() + " ended " + firstMatch.firstPoints + " : " + firstMatch.secondPoints);
						if (firstMatch.getFirstPoints() > firstMatch.getSecondPoints()) {
							firstSC = firstMatch.first;
						} else if (firstMatch.getSecondPoints() > firstMatch.getFirstPoints()) {
							firstSC = firstMatch.second;
						} else {
							log("no winner detected.. choosing randomly.");
							if (rng.nextDouble() < 0.5) {
								firstSC = firstMatch.first;
							} else {
								firstSC = firstMatch.second;
							}
						}
						
						log("Match: " + secondMatch.first.getIdSolverConfiguration() + " vs " + secondMatch.second.getIdSolverConfiguration() + " ended " + secondMatch.firstPoints + " : " + secondMatch.secondPoints);
						if (secondMatch.getFirstPoints() > secondMatch.getSecondPoints()) {
							secondSC = secondMatch.first;
						} else if (secondMatch.getSecondPoints() > secondMatch.getFirstPoints()) {
							secondSC = secondMatch.second;
						} else {
							log("no winner detected.. choosing randomly.");
							if (rng.nextDouble() < 0.5) {
								secondSC = secondMatch.first;
							} else {
								secondSC = secondMatch.second;
							}
						}
						newMatches[i] = new Match(firstSC, secondSC);
					}
					matches = newMatches;
					checkMatches();
					
				}
				
			}
		}
		
		private void log(String message) {
			pacc.log("[Tournament " + tid + "] " + message);
		}
	}
	private enum State {
		NEW, RUNNING, FINISHED
	}
	
	private class Match {
		
		private State state = State.NEW;
		SolverConfiguration first;
		SolverConfiguration second;
		boolean firstFinished = false;
		boolean secondFinished = false;
		HashSet<Integer> instances;
		
		private int firstPoints = 0;
		private int secondPoints = 0;
		
		public Match(SolverConfiguration first, SolverConfiguration second) {
			this.first = first;
			this.second = second;
			this.instances = new HashSet<Integer>();
		}
		
		public void setRunning() {
			if (!state.equals(State.NEW)) {
				throw new IllegalArgumentException("Match is not new.");
			}
			state = State.RUNNING;
		}
		
		public int getFirstPoints() {
			if (!state.equals(State.FINISHED)) {
				throw new IllegalArgumentException("Match is not finished!");
			}
			return firstPoints;
		}
		
		public int getSecondPoints() {
			if (!state.equals(State.FINISHED)) {
				throw new IllegalArgumentException("Match is not finished!");
			}
			return secondPoints;
		}
		
		public void matchFinished() {
			if (!state.equals(State.RUNNING)) {
				throw new IllegalArgumentException("Match wasn't running.");
			}
			HashMap<Integer, List<ExperimentResult>> firstRuns = new HashMap<Integer, List<ExperimentResult>>();
			HashMap<Integer, List<ExperimentResult>> secondRuns = new HashMap<Integer, List<ExperimentResult>>();
			for (Integer instanceId : instances) {
				firstRuns.put(instanceId, new LinkedList<ExperimentResult>());
				secondRuns.put(instanceId, new LinkedList<ExperimentResult>());
			}
			for (ExperimentResult er : first.getJobs()) {
				if (instances.contains(er.getInstanceId())) {
					firstRuns.get(er.getInstanceId()).add(er);
				}
			}
			
			for (ExperimentResult er : second.getJobs()) {
				if (instances.contains(er.getInstanceId())) {
					secondRuns.get(er.getInstanceId()).add(er);
				}
			}
			
			for (Integer instanceId : instances) {
				int numFirstSuccessful = 0, numSecondSuccessful = 0;
				for (ExperimentResult er : firstRuns.get(instanceId)) {
					if (String.valueOf(er.getResultCode().getResultCode()).startsWith("1")) {
						numFirstSuccessful++;
					}
				}
				for (ExperimentResult er : secondRuns.get(instanceId)) {
					if (String.valueOf(er.getResultCode().getResultCode()).startsWith("1")) {
						numSecondSuccessful++;
					}
				}
				
				float firstCost = parameters.getStatistics().getCostFunction().getMinimize() ? -numFirstSuccessful : numFirstSuccessful;
				float secondCost = parameters.getStatistics().getCostFunction().getMinimize() ? -numSecondSuccessful : numSecondSuccessful;
				if (numFirstSuccessful == numSecondSuccessful) {
					firstCost = parameters.getStatistics().getCostFunction().calculateCost(firstRuns.get(instanceId));
					secondCost = parameters.getStatistics().getCostFunction().calculateCost(secondRuns.get(instanceId));
				}
				
				if (firstCost < secondCost) {
					if (parameters.getStatistics().getCostFunction().getMinimize()) {
						firstPoints++;
					} else {
						secondPoints++;
					}
				} else if (secondCost < firstCost) {
					if (parameters.getStatistics().getCostFunction().getMinimize()) {
						secondPoints++;
					} else {
						firstPoints++;
					}
				}
			}
			state = State.FINISHED;
		}
	}

	@Override
	public void stopEvaluation(List<SolverConfiguration> scs) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void raceFinished() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void jobFinished(ExperimentResult result) {
		SolverConfigurationMetaData sc = allSolverConfigs.get(result.getSolverConfigId());
		if (sc == null) {
			return;
		}
		List<ExperimentResult> results = new LinkedList<ExperimentResult>(); 
		boolean hasCost = false;
		for (ExperimentResult r : sc.solverConfig.getJobs()) {
			if (r.getInstanceId() == result.getInstanceId()) {
				results.add(r);
				if (r.getResultCode().isCorrect()) {
					hasCost = true;
				}
			}
		}
		float cost = hasCost ? parameters.getStatistics().getCostFunction().calculateCost(results) : Float.POSITIVE_INFINITY;
		clustering.update(result.getSolverConfigId(), result.getInstanceId(), cost);
	}
}
