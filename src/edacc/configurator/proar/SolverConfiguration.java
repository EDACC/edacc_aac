package edacc.configurator.proar;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import edacc.parameterspace.ParameterConfiguration;
import edacc.model.ExperimentResult;
import edacc.model.ExperimentResultDAO;
import edacc.model.InstanceSeed;
import edacc.model.StatusCode;
public class SolverConfiguration implements Comparable<SolverConfiguration>{
	/**the parameter configuration of the solver configuration*/
	private ParameterConfiguration pConfig;
	
	/**id of the solver configuration from the DB*/
	private int idSolverConfiguration;
	
	/**the cost of the configuration with regards to a statistic function and a metric (cost or runtime); 
	*/
	private Float cost;
	
	/**the name of the configuration*/
	private String name;
	
	/**iteration of the configurator where this configuration was created; 	useful for debuging*/ 
	private int level;
	
	/**List of all jobs that a solver configuration has been executed so far*/
	//TODO definiere datenstruktur für jobs[idSolverConfig]
	//man muss schnell über die idSolverConfig auf die jobs zugreifen und mann muss schnell die 
	//disjunktion von zwei solchen mengen machen können um zu sehen welche jobs man für die neuen config noch erstellen muss
	//muss auch eine size methode haben
	private List<ExperimentResult> jobs;

	/**
	 * Common initialization
	 */
	private SolverConfiguration() {
		jobs = new LinkedList<ExperimentResult>();
	}
	
	public SolverConfiguration(int idSolverConfiguration, ParameterConfiguration pc, int level){
		this();
		
		this.pConfig = pc;
		this.idSolverConfiguration = idSolverConfiguration; 
		this.cost = null;
		this.name = null;
		this.level = level;
	}

	public SolverConfiguration(SolverConfiguration sc){
		this();
		
		this.pConfig = new ParameterConfiguration(sc.pConfig);
		this.idSolverConfiguration = sc.idSolverConfiguration; 
		this.cost = sc.cost;
		this.name = sc.name;
		this.level = sc.level;
	}
	
	public final ParameterConfiguration getParameterConfiguration(){
		return this.pConfig;
	}
	
	public final void setParameterConfiguration(ParameterConfiguration pc){
		this.pConfig = pc;
	}
	
	public final int getIdSolverConfiguration(){
		return this.idSolverConfiguration;
	}
	
	public final void setIdSolverConfiguration(int id){
		this.idSolverConfiguration = id;
	}
	
	public final Float getCost(){
		return this.cost;
	}
	
	public final void setCost(Float cost){
		this.cost = cost;
	}
	
	public final String getName(){
		return this.name;
	}
	
	public final void setName(String name){
		this.name = name;
	}
	
	public void putJob(ExperimentResult job) {
		jobs.add(job);
	}
	
	public List<ExperimentResult> getRunningJobs() {
		LinkedList<ExperimentResult> res = new LinkedList<ExperimentResult>();
		for (ExperimentResult j: jobs) {
			if (j.getStatus().equals(StatusCode.RUNNING)) {
				res.add(j);
			}
		}
		return res;
	}
	
	public List<ExperimentResult> getFinishedJobs() {
		LinkedList<ExperimentResult> res = new LinkedList<ExperimentResult>();
		for (ExperimentResult j: jobs) {
			if (!j.getStatus().equals(StatusCode.NOT_STARTED) && !j.getStatus().equals(StatusCode.RUNNING)) {
				res.add(j);
			}
		}
		return res;
	}
	
	public List<ExperimentResult> getNotStartedJobs() {
		LinkedList<ExperimentResult> res = new LinkedList<ExperimentResult>();
		for (ExperimentResult j: jobs) {
			if (j.getStatus().equals(StatusCode.NOT_STARTED)) {
				res.add(j);
			}
		}
		return res;
	}
	
	public int getJobCount() {
		return jobs.size();
	}
	
	public void updateJobs() throws Exception {
		LinkedList<Integer> ids = new LinkedList<Integer>();
		for (ExperimentResult j: jobs) {
			ids.add(j.getId());
		}
		jobs = ExperimentResultDAO.getByIds(ids);
	}
	
	public List<InstanceIdSeed> getInstanceIdSeed(SolverConfiguration other, int num) {
		LinkedList<InstanceIdSeed> res = new LinkedList<InstanceIdSeed>();
		HashSet<InstanceIdSeed> ownInstanceIdSeed = new HashSet<InstanceIdSeed>();
		for (ExperimentResult j : jobs) {
			ownInstanceIdSeed.add(new InstanceIdSeed(j.getInstanceId(), j.getSeed()));
		}
		for (ExperimentResult j: other.getFinishedJobs()) {
			InstanceIdSeed tmp = new InstanceIdSeed(j.getInstanceId(), j.getSeed());
			if (!ownInstanceIdSeed.contains(tmp)) {
				res.add(tmp);
				if (res.size() == num) {
					break;
				}
			}
		}
		return res;
	}

	public int getLevel() {
		return level;
	}
	
	@Override
	public int compareTo(SolverConfiguration other) {
		HashMap<InstanceIdSeed, ExperimentResult> ownJobs = new HashMap<InstanceIdSeed, ExperimentResult>();
		for (ExperimentResult job: getFinishedJobs()) {
			ownJobs.put(new InstanceIdSeed(job.getInstanceId(), job.getSeed()), job);
		}
		boolean allEqual = true;
		for (ExperimentResult job: other.getFinishedJobs()) {
			InstanceIdSeed tmp = new InstanceIdSeed(job.getInstanceId(), job.getSeed());
			ExperimentResult ownJob;
			if ((ownJob = ownJobs.get(tmp)) != null) {
				if (ownJob.getStatus().equals(StatusCode.SUCCESSFUL) && job.getStatus().equals(StatusCode.SUCCESSFUL)) {
				
					// TODO: Also use cost
					float ownCost = ownJob.getResultTime();
					float otherCost = job.getResultTime();

					if (ownCost < otherCost) {
						allEqual = false;
						continue;
					} else if (ownCost == otherCost) {
						continue;
					} else {
						return -1;
					}
				} else if (ownJob.getStatus().equals(StatusCode.SUCCESSFUL) && !job.getStatus().equals(StatusCode.SUCCESSFUL)) {
					allEqual = false;
					continue;
				} else if (!ownJob.getStatus().equals(StatusCode.SUCCESSFUL) && job.getStatus().equals(StatusCode.SUCCESSFUL)) {
					return -1;
				} else {
					continue;
				}
			}
		}
		return allEqual ? 0 : 1;
	}
}
