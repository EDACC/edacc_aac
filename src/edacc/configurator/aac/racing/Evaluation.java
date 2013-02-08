package edacc.configurator.aac.racing;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.JobListener;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.ExperimentResult;
import edacc.model.StatusCode;

public class Evaluation extends RacingMethods implements JobListener {

	List<SolverConfiguration> scs;
	
	SolverConfiguration current_sc;
	
	double budget = 0.;
	public Evaluation(AAC pacc, Random rng, API api, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
		super(pacc, rng, api, parameters, firstSCs, referenceSCs);
		pacc.addJobListener(this);
		
		String val;
		if ((val = parameters.getRacingMethodParameters().get("Evaluation_budget")) != null)
            budget = Double.valueOf(val);
		
		scs = new LinkedList<SolverConfiguration>();
		scs.addAll(referenceSCs);
		start_new_sc();
	}

	@Override
	public int compareTo(SolverConfiguration sc1, SolverConfiguration sc2) {
		return 0;
	}

	@Override
	public List<SolverConfiguration> getBestSolverConfigurations() {
		return new LinkedList<SolverConfiguration>();
	}

	@Override
	public void solverConfigurationsFinished(List<SolverConfiguration> scs) throws Exception {
	}

	@Override
	public void solverConfigurationsCreated(List<SolverConfiguration> scs) throws Exception {
	}

	@Override
	public int computeOptimalExpansion(int computationCoreCount, int computationJobCount, int listNewSCSize) {
		return 0;
	}

	@Override
	public void stopEvaluation(List<SolverConfiguration> scs) throws Exception {
	}

	@Override
	public List<String> getParameters() {
		List<String> params = new LinkedList<String>();
		params.add("Evaluation_budget = " + budget + " # wall time budget");
		return params;
	}

	@Override
	public void raceFinished() {
		pacc.log("Race finished.");
	}

	private void start_new_sc() throws Exception {
		current_sc = null;
		if (!scs.isEmpty()) {
			current_sc = scs.get(0);
			scs.remove(0);
			pacc.expandParcoursSC(current_sc, api.getExperimentInstances(parameters.getIdExperiment()).size());
			pacc.addSolverConfigurationToListNewSC(current_sc);
		}
	}
	
	@Override
	public void jobsFinished(List<ExperimentResult> result) throws Exception {
		if (current_sc == null) {
			return;
		}
		double current_walltime = 0.;
		long current_time = System.currentTimeMillis();
		for (ExperimentResult res : current_sc.getJobs()) {
			if (res.getStartTime() != null) {
				if (res.getStatus().equals(StatusCode.RUNNING)) {
					current_walltime += current_time - res.getStartTime().getTime();
				} else {
					current_walltime += res.getWallTime()*1000;
				}
			}
		}
		current_walltime /= 1000.;
		if (current_walltime >= budget) {
			pacc.log("Budget for solver config " + current_sc.getIdSolverConfiguration() + " reached.");
			int not_started = 0;
			for (ExperimentResult res : current_sc.getJobs()) {
				if (res.getStatus().equals(StatusCode.RUNNING)) {
					api.killJob(res.getId());
				} else if (res.getStatus().equals(StatusCode.NOT_STARTED)) {
					api.setJobPriority(res.getId(), -1);
					// maybe started:
					api.killJob(res.getId());
					
					not_started++;
				}
				
				// Update cpu time limit for statistics
				api.updateCPUTimeLimit(res.getId(), (int) (System.currentTimeMillis() - res.getStartTime().getTime()) / 1000, res.getStatus(), res.getResultCode());
			}
			if (not_started > 0) {
				pacc.log("Warning: " + not_started + " jobs not started. Maybe you don't have enough cores?");
			}
			start_new_sc();
		}
	}

}
