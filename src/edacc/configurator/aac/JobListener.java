package edacc.configurator.aac;

import java.util.List;

import edacc.model.ExperimentResult;

public interface JobListener {
	public void jobsFinished(List<ExperimentResult> result) throws Exception;
}
