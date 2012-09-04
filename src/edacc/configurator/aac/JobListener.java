package edacc.configurator.aac;

import edacc.model.ExperimentResult;

public interface JobListener {
	public void jobFinished(ExperimentResult result) throws Exception;
}
