package edacc.api;

import java.io.IOException;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import edacc.api.costfunctions.CostFunction;
import edacc.model.ComputationMethodDoesNotExistException;
import edacc.model.Course;
import edacc.model.DatabaseConnector;
import edacc.model.ExpResultHasSolvPropertyNotInDBException;
import edacc.model.ExperimentResult;
import edacc.model.ExperimentResultDAO;
import edacc.model.ExperimentResultHasProperty;
import edacc.model.ExperimentResultNotInDBException;
import edacc.model.Instance;
import edacc.model.InstanceDAO;
import edacc.model.InstanceSeed;
import edacc.model.NoConnectionToDBException;
import edacc.model.PropertyNotInDBException;
import edacc.model.ResultCode;
import edacc.model.ResultCodeNotInDBException;
import edacc.model.StatusCode;
import edacc.model.StatusCodeNotInDBException;
import edacc.parameterspace.ParameterConfiguration;
import edacc.properties.PropertyTypeNotExistException;

public class APISimulation extends APIImpl {

	private class Client implements Comparable<Client> {
		long idleSince;
		ExperimentResultWrapper currentJob;
		long currentJobEndTime;
		public Client() {
			idleSince = currentTime; //System.currentTimeMillis();
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
			currentJobEndTime = currentJob.startTime + Math.round(cpuTime * 1000);
			checkJob();
		}

		public void checkJob() {
			if (currentJob == null)
				return;
			//if (System.currentTimeMillis() >= currentJobEndTime) {
			if (currentTime >= currentJobEndTime) {
				currentJob.status = currentJob.er.getStatus();
				idleSince = currentJobEndTime;
				currentJob = null;
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
			// we have to fill run, solverconfig id, experiment id, instance id fields for equals method!
			super(er.getRun(), 0, 0, null, 0, null, 0.f, er.getSolverConfigId(), er.getExperimentId(), er.getInstanceId(), null, 0, 0, 0, 0);
			this.er = er;
			this.status = StatusCode.NOT_STARTED;
			this.priority = priority;
			this.creationTime = currentTime; // System.currentTimeMillis();
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

	private class JobIdentifier {
		int idSolverConfig, idInstance, idExperiment;
		long seed;
		public JobIdentifier(int idExperiment, int idSolverConfig, int idInstance, long seed) {
			this.idExperiment = idExperiment;
			this.idSolverConfig = idSolverConfig;
			this.idInstance = idInstance;
			this.seed = seed;
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + idExperiment;
			result = prime * result + idInstance;
			result = prime * result + idSolverConfig;
			result = prime * result + (int) (seed ^ (seed >>> 32));
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
			JobIdentifier other = (JobIdentifier) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (idExperiment != other.idExperiment)
				return false;
			if (idInstance != other.idInstance)
				return false;
			if (idSolverConfig != other.idSolverConfig)
				return false;
			if (seed != other.seed)
				return false;
			return true;
		}
		private APISimulation getOuterType() {
			return APISimulation.this;
		}
		
	}
	private HashMap<JobIdentifier, ExperimentResult> dbJobs = null;

	private long overhead_overall, overhead_launchjob;
	private Course course;
	private int coreCount;
	private List<ExperimentResultWrapper> jobsWaiting;
	private Random rng;
	long currentTime;
	
	// every client has exactly one core
	private List<Client> clients;
	private Map<Integer, ExperimentResultWrapper> mapExperimentResults;
	private Map<Integer, Integer> solverConfigJobCount;

	//int last_running_jobs = 0;
	//int last_running_jobs_before = 0;
	private void checkJobs() {
	//	int cur_running_jobs = 0;
	//	int cur_running_jobs_before = 0;
		
		// first check current running jobs
		for (Client c : clients) {
	//		if (c.currentJob != null)
	//			cur_running_jobs_before++;
			c.checkJob();
	//		if (c.currentJob != null) 
	//			cur_running_jobs++;
		}
	/*	if (last_running_jobs != cur_running_jobs || last_running_jobs_before != cur_running_jobs) {
			System.out.println("[APISimulation] Finished " + (cur_running_jobs_before - cur_running_jobs) + " jobs. There are currently " + cur_running_jobs + " running.");
			last_running_jobs = cur_running_jobs;
			last_running_jobs_before = cur_running_jobs_before;
		}*/
		if (!jobsWaiting.isEmpty()) {
			// sort client list by idleSince
			Collections.sort(clients);
			// sort jobs by creation time
			Collections.sort(jobsWaiting);
			
			int job_index = 0;
			List<ExperimentResultWrapper> jobs = new LinkedList<ExperimentResultWrapper>();
			List<ExperimentResultWrapper> jobs_given = new LinkedList<ExperimentResultWrapper>();
			for (int client_index = 0; client_index < clients.size(); client_index++) {
				Client c = clients.get(client_index);
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
					if (c.currentJob == null) {
						// job calculated in time diff: currentTime - c.idleSince 
						for (int tmp = client_index+1; tmp < clients.size(); tmp++) {
							Client tmpClient = clients.get(tmp);
							if (tmpClient.idleSince > c.idleSince) {
								clients.add(tmp, c);
								clients.remove(client_index);
								break;
							}
						}
						client_index--;
					}
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
		long time = System.currentTimeMillis();
		ExperimentResult er;
		if (dbJobs != null) {
			er = dbJobs.get(new JobIdentifier(idExperiment, idSolverConfig, idInstance, seed.longValue()));
		} else {
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
				throw new IllegalArgumentException("No such job found. (idExperiment, idSolverConfig, idInstance, seed) = (" + idExperiment + "," + idSolverConfig + "," + idInstance + "," + seed.longValue() + ")");
			}
			if (mapExperimentResults.containsKey(idJob)) {
				throw new IllegalArgumentException("Job with id " + idJob + " already started.");
			}
			er = ExperimentResultDAO.getById(idJob);
		}
		if (er == null) {
			throw new IllegalArgumentException("No such job found. (idExperiment, idSolverConfig, idInstance, seed) = (" + idExperiment + "," + idSolverConfig + "," + idInstance + "," + seed.longValue() + ")");
		}
		ExperimentResultWrapper ew = new ExperimentResultWrapper(er, priority);
		mapExperimentResults.put(ew.getId(), ew);
		jobsWaiting.add(ew);
		Integer jobCount = solverConfigJobCount.get(idSolverConfig);
		if (jobCount == null) {
			jobCount = 0;
		}
		jobCount++;
		solverConfigJobCount.put(idSolverConfig, jobCount);
		overhead_overall += System.currentTimeMillis() - time;
		overhead_launchjob += System.currentTimeMillis() - time;
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
		long time = System.currentTimeMillis();
		checkJobs();
		overhead_overall += System.currentTimeMillis() - time;
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
		long time = System.currentTimeMillis();
		checkJobs();
		Map<Integer, ExperimentResult> res = new HashMap<Integer, ExperimentResult>();
		for (Integer id : ids) {
			res.put(id, mapExperimentResults.get(id));
		}
		overhead_overall += System.currentTimeMillis() - time;
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
		super.updateSolverConfigurationName(idSolverConfig, name);
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
		long time = System.currentTimeMillis();
		checkJobs();
		int res = jobsWaiting.size();
		for (Client c : clients) {
			if (c.currentJob != null)
				res++;
		}
		overhead_overall += System.currentTimeMillis() - time;
		return res;
	}

	@Override
	public void setJobPriority(int idJob, int priority) throws Exception {
		long time = System.currentTimeMillis();
		checkJobs();
		mapExperimentResults.get(idJob).priority = priority;
		overhead_overall += System.currentTimeMillis() - time;
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
	
	public void cacheJobs(int expId) throws PropertyTypeNotExistException, PropertyNotInDBException, NoConnectionToDBException, ComputationMethodDoesNotExistException, ExpResultHasSolvPropertyNotInDBException, ExperimentResultNotInDBException, StatusCodeNotInDBException, ResultCodeNotInDBException, SQLException, IOException {
		System.out.println("[APISimulation] Caching jobs..");
		dbJobs = new HashMap<JobIdentifier, ExperimentResult>();
		for (ExperimentResult er : ExperimentResultDAO.getAllByExperimentId(expId)) {
			dbJobs.put(new JobIdentifier(er.getExperimentId(), er.getSolverConfigId(), er.getInstanceId(), er.getSeed()), er);
		}
		System.out.println("[APISimulation] Done.");
	}

	public APISimulation(int coreCount, Random rng) throws SQLException {
		super();
		if (coreCount <= 0) {
			throw new IllegalArgumentException("Core count must be greater than zero.");
		}
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
		overhead_overall = 0;
		overhead_launchjob = 0;
		currentTime = 0;
	}
	
	public void incrementTime(long time) {
		currentTime += time;
	}
	
	public void printStats() {
		Formatter f = new Formatter();
		System.out.println("[APISimulation] Overhead time: " + overhead_overall);
		System.out.println("[APISimulation] Overhead launch job: " + overhead_launchjob);
		System.out.println("[APISimulation] Real wall time: " + f.format("%.3f sec", currentTime / 1000.f));
	}
}
