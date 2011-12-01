package edacc.api;

import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import edacc.api.costfunctions.CostFunction;
import edacc.model.Course;
import edacc.model.DatabaseConnector;
import edacc.model.ExperimentResult;
import edacc.model.ExperimentResultDAO;
import edacc.model.ExperimentResultHasProperty;
import edacc.model.Instance;
import edacc.model.InstanceDAO;
import edacc.model.InstanceSeed;
import edacc.model.ResultCode;
import edacc.model.StatusCode;
import edacc.parameterspace.ParameterConfiguration;

public class APISimulation extends APIImpl {

	private class Client implements Comparable<Client> {
		long idleSince;
		ExperimentResultWrapper currentJob;
		long currentJobEndTime;
		
		public Client() {
			idleSince = System.currentTimeMillis();
		}

		public void startJob(ExperimentResultWrapper ew) {
			if (currentJob != null) {
				throw new IllegalArgumentException("Can't start a job, when a job is running");
			}
			currentJob = ew;
			currentJob.status = StatusCode.RUNNING;
			if (ew.creationTime > idleSince) {
				idleSince = ew.creationTime;
			}
			currentJob.startTime = idleSince;
			float cpuTime = currentJob.getCPUTimeLimit();
			if (currentJob.er.getStatus().equals(StatusCode.SUCCESSFUL)) {
				cpuTime = currentJob.er.getResultTime();
			}
			currentJobEndTime = currentJob.startTime + Math.round(cpuTime * multiplicator);
			checkJob();
		}

		public void checkJob() {
			if (currentJob == null)
				return;
			if (System.currentTimeMillis() > currentJobEndTime) {
				currentJob.status = currentJob.er.getStatus();
				idleSince = currentJobEndTime;
			}
		}

		@Override
		public int compareTo(Client other) {
			if (other.idleSince > idleSince) {
				return -1;
			} else if (other.idleSince == idleSince) {
				return 0;
			} else {
				return 1;
			}
		}

	}

	private class ExperimentResultWrapper extends ExperimentResult implements Comparable<ExperimentResultWrapper> {
		/**
		 * 
		 */
		private static final long serialVersionUID = -7289192714098634869L;
		long creationTime, startTime;
		StatusCode status;
		ExperimentResult er;
		int priority;

		public ExperimentResultWrapper(ExperimentResult er, int priority) {
			super();
			this.er = er;
			this.status = StatusCode.NOT_STARTED;
			this.priority = priority;
			this.creationTime = System.currentTimeMillis();
			this.startTime = 0;
		}

		@Override
		public int getExperimentId() {
			return er.getExperimentId();
		}

		@Override
		public int getPriority() {
			return priority;
		}

		@Override
		public int getInstanceId() {
			return er.getInstanceId();
		}

		@Override
		public int getSolverConfigId() {
			return er.getSolverConfigId();
		}

		@Override
		public int getId() {
			return er.getId();
		}

		@Override
		public int getRun() {
			return er.getRun();
		}

		@Override
		public int getSeed() {
			return er.getSeed();
		}

		@Override
		public StatusCode getStatus() {
			return status;
		}

		@Override
		public float getResultTime() {
			if (status.equals(StatusCode.NOT_STARTED) || status.equals(StatusCode.RUNNING)) {
				return 0.f;
			} else {
				return er.getResultTime();
			}
		}

		@Override
		public int getRunningTime() {
			return 0;
		}

		@Override
		public int getComputeQueue() {
			return 0;
		}

		@Override
		public int getSolverExitCode() {
			return 0;
		}

		@Override
		public int getVerifierExitCode() {
			return 0;
		}

		@Override
		public int getWatcherExitCode() {
			return 0;
		}

		@Override
		public ResultCode getResultCode() {
			if (status.equals(StatusCode.NOT_STARTED) || status.equals(StatusCode.RUNNING)) {
				return ResultCode.UNKNOWN;
			} else {
				return er.getResultCode();
			}
		}

		@Override
		public HashMap<Integer, ExperimentResultHasProperty> getPropertyValues() {
			return null;
		}

		@Override
		public Timestamp getDatemodified() {
			return null;
		}

		@Override
		public Timestamp getStartTime() {
			return null;
		}

		@Override
		public int getCPUTimeLimit() {
			return er.getCPUTimeLimit();
		}

		@Override
		public int getMemoryLimit() {
			return er.getMemoryLimit();
		}

		@Override
		public int getStackSizeLimit() {
			return er.getStackSizeLimit();
		}

		@Override
		public int getWallClockTimeLimit() {
			return er.getWallClockTimeLimit();
		}

		@Override
		public String getComputeNode() {
			return null;
		}

		@Override
		public String getComputeNodeIP() {
			return null;
		}

		@Override
		public Integer getIdClient() {
			return null;
		}

		@Override
		public int compareTo(ExperimentResultWrapper other) {
			if (creationTime > other.creationTime) {
				return 1;
			} else if (creationTime == other.creationTime) {
				return 0;
			} else {
				return -1;
			}
		}
	}

	private float multiplicator = 100.f;
	private Course course;
	private int coreCount;
	private List<ExperimentResultWrapper> jobsWaiting;
	private Random rng;
	
	// every client has exactly one core
	private List<Client> clients;
	private Map<Integer, ExperimentResultWrapper> mapExperimentResults;
	private Map<Integer, Integer> solverConfigJobCount;

	private void checkJobs() {
		// first check current running jobs
		for (Client c : clients) {
			c.checkJob();
		}
		if (!jobsWaiting.isEmpty()) {
			// sort client list by idleSince
			Collections.sort(clients);
			// sort jobs by creation time
			Collections.sort(jobsWaiting);
			
			int job_index = 0;
			List<ExperimentResultWrapper> jobs = new LinkedList<ExperimentResultWrapper>();
			List<ExperimentResultWrapper> jobs_given = new LinkedList<ExperimentResultWrapper>();
			for (Client c : clients) {
				if (c.currentJob == null) {
					// client is currently not calculating a job
					// add jobs which are visible to this client
					while (job_index < jobsWaiting.size() && jobsWaiting.get(job_index).creationTime <= c.idleSince) {
						jobs.add(jobsWaiting.get(job_index++));
					}
					if (jobs.isEmpty()) {
						// we have to add jobs if there are jobs
						if (job_index >= jobsWaiting.size()) {
							// no more jobs
							break;
						} else {
							jobs.add(jobsWaiting.get(job_index++));
							while (job_index < jobsWaiting.size() && jobsWaiting.get(job_index).creationTime == jobs.get(jobs.size()-1).creationTime) {
								jobs.add(jobsWaiting.get(job_index++));
							}
						}
					}
					// TODO: efficiency
					List<ExperimentResultWrapper> highPrioJobs = new LinkedList<ExperimentResultWrapper>();
					int current_prio = -1;
					for (ExperimentResultWrapper ew : jobs) {
						if (ew.getPriority() > current_prio) {
							highPrioJobs.clear();
							current_prio = ew.getPriority();
						}
						if (ew.getPriority() == current_prio)
							highPrioJobs.add(ew);
					}
					ExperimentResultWrapper job = highPrioJobs.get(rng.nextInt(highPrioJobs.size()));
					jobs_given.add(job);
					// TODO: efficiency
					jobs.remove(job);
					
					c.startJob(job);
				}
			}
			for (ExperimentResultWrapper ew : jobs_given) {
				// TODO: efficiency
				jobsWaiting.remove(ew);
			}
		}
	}

	@Override
	public synchronized int createSolverConfig(int idExperiment, ParameterConfiguration config, String name) throws Exception {
		throw new IllegalArgumentException("Can't create solver configurations in read only mode.");
	}

	@Override
	public synchronized List<Integer> createSolverConfigs(int idExperiment, List<ParameterConfiguration> configs, List<String> names) throws Exception {
		throw new IllegalArgumentException("Can't create solver configurations in read only mode.");
	}

	@Override
	public synchronized int launchJob(int idExperiment, int idSolverConfig, int idInstance, BigInteger seed, int cpuTimeLimit) throws Exception {
		return launchJob(idExperiment, idSolverConfig, idInstance, seed, cpuTimeLimit, 0);
	}

	@Override
	public synchronized int launchJob(int idExperiment, int idSolverConfig, int idInstance, BigInteger seed, int cpuTimeLimit, int priority) throws Exception {
		PreparedStatement ps = DatabaseConnector.getInstance().getConn().prepareStatement("SELECT idJob FROM ExperimentResults WHERE Experiment_idExperiment = ? AND SolverConfig_idSolverConfig = ? AND Instances_idInstance = ? AND seed = ?");
		ps.setInt(1, idExperiment);
		ps.setInt(2, idSolverConfig);
		ps.setInt(3, idInstance);
		ps.setLong(4, seed.longValue());
		ResultSet rs = ps.executeQuery();
		int idJob = -1;
		if (rs.next()) {
			idJob = rs.getInt(1);
		}
		rs.close();
		ps.close();
		if (idJob == -1) {
			throw new IllegalArgumentException("No such job found.");
		}
		if (mapExperimentResults.containsKey(idJob)) {
			throw new IllegalArgumentException("Job with id " + idJob + " already started.");
		}
		ExperimentResultWrapper ew = new ExperimentResultWrapper(ExperimentResultDAO.getById(idJob), priority);
		mapExperimentResults.put(ew.getId(), ew);
		jobsWaiting.add(ew);
		Integer jobCount = solverConfigJobCount.get(idSolverConfig);
		if (jobCount == null) {
			jobCount = 0;
		}
		jobCount++;
		solverConfigJobCount.put(idSolverConfig, jobCount);
		return ew.getId();
	}

	@Override
	public synchronized int launchJob(int idExperiment, int idSolverConfig, int cpuTimeLimit, Random rng) throws Exception {
		return launchJob(idExperiment, idSolverConfig, cpuTimeLimit, 0, rng);
	}

	@Override
	public synchronized int launchJob(int idExperiment, int idSolverConfig, int cpuTimeLimit, int priority, Random rng) throws Exception {
		Integer jobCount = solverConfigJobCount.get(idSolverConfig);
		if (jobCount == null)
			jobCount = 0;
		InstanceSeed is = course.get(jobCount);
		return launchJob(idExperiment, idSolverConfig, is.instance.getId(), BigInteger.valueOf(is.seed), cpuTimeLimit, priority);
	}

	@Override
	public int getCourseLength(int idExperiment) throws Exception {
		return course.getLength();
	}

	@Override
	public synchronized List<Integer> launchJob(int idExperiment, int idSolverConfig, int[] cpuTimeLimit, int numberRuns, Random rng) throws Exception {
		int[] priority = new int[cpuTimeLimit.length];
		for (int i = 0; i < cpuTimeLimit.length; i++) {
			priority[i] = 0;
		}
		return launchJob(idExperiment, idSolverConfig, cpuTimeLimit, numberRuns, priority, rng);
	}

	@Override
	public synchronized List<Integer> launchJob(int idExperiment, int idSolverConfig, int[] cpuTimeLimit, int numberRuns, int[] priority, Random rng) throws Exception {
		if (cpuTimeLimit.length != priority.length || priority.length != numberRuns) {
			throw new IllegalArgumentException();
		}
		List<Integer> res = new LinkedList<Integer>();
		for (int i = 0; i < cpuTimeLimit.length; i++) {
			res.add(launchJob(idExperiment, idSolverConfig, cpuTimeLimit[i], priority[i], rng));
		}
		return res;
	}

	@Override
	public synchronized int getNumJobs(int idSolverConfig) throws Exception {
		Integer jobCount = solverConfigJobCount.get(idSolverConfig);
		if (jobCount == null) {
			return 0;
		} else {
			return jobCount;
		}
	}

	@Override
	public synchronized void updateSolverConfigurationCost(int idSolverConfig, float cost, CostFunction func) throws Exception {
		// TODO: implement?
	}

	@Override
	public synchronized ExperimentResult getJob(int idJob) throws Exception {
		checkJobs();
		return mapExperimentResults.get(idJob);
	}

	@Override
	public synchronized ExperimentResult killJob(int idJob) throws Exception {
		throw new IllegalArgumentException("Not implemented.");
	}

	@Override
	public void restartJob(int idJob, int CPUTimeLimit) throws Exception {
		throw new IllegalArgumentException("Not implemented.");
	}

	@Override
	public synchronized boolean deleteResult(int idJob) throws Exception {
		throw new IllegalArgumentException("Not implemented.");
	}

	@Override
	public synchronized Map<Integer, ExperimentResult> getJobsByIDs(List<Integer> ids) throws Exception {
		checkJobs();
		Map<Integer, ExperimentResult> res = new HashMap<Integer, ExperimentResult>();
		for (Integer id : ids) {
			res.put(id, mapExperimentResults.get(id));
		}
		return res;
	}

	@Override
	public synchronized List<Instance> getExperimentInstances(int idExperiment) throws Exception {
		throw new IllegalArgumentException("Not implemented.");
	}

	@Override
	public synchronized int getBestConfiguration(int idExperiment, CostFunction func) throws Exception {
		throw new IllegalArgumentException("Not implemented.");
	}

	@Override
	public List<Integer> getBestConfigurations(int idExperiment, CostFunction func, int no) throws Exception {
		throw new IllegalArgumentException("Not implemented.");
	}

	@Override
	public void updateSolverConfigurationName(int idSolverConfig, String name) throws Exception {
		// TODO: implement?
	}

	@Override
	public void removeSolverConfig(int idSolverConfig) throws Exception {
		// TODO: implement?
	}

	@Override
	public int getComputationCoreCount(int idExperiment) throws Exception {
		return coreCount;
	}

	@Override
	public int getComputationJobCount(int idExperiment) throws Exception {
		checkJobs();
		int res = jobsWaiting.size();
		for (Client c : clients) {
			if (c.currentJob != null)
				res++;
		}
		return res;
	}

	@Override
	public void setJobPriority(int idJob, int priority) throws Exception {
		checkJobs();
		mapExperimentResults.get(idJob).priority = priority;
	}

	@Override
	public Course getCourse(int idExperiment) throws Exception {
		return course;
	}

	public void generateCourse(int expId) throws SQLException {
		System.out.println("[APISimulation] Generating course..");
		course = new Course();
		PreparedStatement ps = DatabaseConnector.getInstance().getConn().prepareStatement("SELECT DISTINCT Instances_idInstance, seed FROM ExperimentResults WHERE Experiment_idExperiment = ?");
		ps.setInt(1, expId);
		ResultSet rs = ps.executeQuery();
		LinkedList<InstanceSeed> list = new LinkedList<InstanceSeed>();
		while (rs.next()) {
			Instance i = InstanceDAO.getById(rs.getInt(1));
			int seed = rs.getInt(2);
			list.add(new InstanceSeed(i, seed));
		}
		rs.close();
		ps.close();
		Collections.shuffle(list, rng);
		for (InstanceSeed is : list) {
			course.add(is);
		}
		// TODO: set initial length?
		System.out.println("[APISimulation] Done.");
	}

	public APISimulation(int coreCount, float multiplicator, Random rng) throws SQLException {
		super();
		if (coreCount <= 0 || multiplicator < 0.f) {
			throw new IllegalArgumentException("Core count must be greater than zero and multiplicator must be greater or equal zero.");
		}
		this.multiplicator = multiplicator;
		this.coreCount = coreCount;
		this.rng = rng;
		jobsWaiting = new LinkedList<ExperimentResultWrapper>();
		clients = new LinkedList<Client>();
		mapExperimentResults = new HashMap<Integer, ExperimentResultWrapper>();
		solverConfigJobCount = new HashMap<Integer, Integer>();
		System.out.println("[APISimulation] Generating " + coreCount + " clients. One core each.");
		for (int i = 0; i < coreCount; i++) {
			clients.add(new Client());
		}
	}
}
