/**
 * 
 */
package edacc.configurator.aac.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.parameterspace.ParameterConfiguration;


/**
 * @author balint
 *
 */
public class ILS extends SearchMethods {

	/**
	 * @param api
	 * @param rng
	 * @param parameters
	 */
	float stdDevFactor = 0.1f;
	boolean sampleOrdinals = true;
	int sampleSize = 10;
	
	
	public ILS(AAC pacc, API api, Random rng, Parameters parameters, SolverConfiguration firstSC) {
		super(pacc, api, rng, parameters, firstSC);
		// TODO Auto-generated constructor stub
	}

	/* (non-Javadoc)
	 * @see edacc.configurator.aac.search.SearchMethods#generateNewSC(int, edacc.configurator.aac.SolverConfiguration)
	 */
	@Override
	public List<SolverConfiguration> generateNewSC(int num) throws Exception {
		List<SolverConfiguration> bestSCs = pacc.racing.getBestSolverConfigurations(1);
		SolverConfiguration currentBestSC = (bestSCs.size() > 0 ? bestSCs.get(0) : firstSC);
		
		//the parameter configurations generated 
		List<ParameterConfiguration> paramConfs = new ArrayList<ParameterConfiguration>();
		List<SolverConfiguration> solverConfigs = new ArrayList<SolverConfiguration>();
		
		paramConfs = api.loadParameterGraphFromDB(parameters.getIdExperiment()).getGaussianNeighbourhood(currentBestSC.getParameterConfiguration(), rng, stdDevFactor, sampleSize, sampleOrdinals);
		Collections.shuffle(paramConfs, rng);
		int i=0;
		for (ParameterConfiguration p: paramConfs) {
			int idSolverConfig = api.createSolverConfig(parameters.getIdExperiment(), p, api.getCanonicalName(parameters.getIdExperiment(), p));
			solverConfigs.add(new SolverConfiguration(idSolverConfig, api.getParameterConfiguration(parameters.getIdExperiment(), idSolverConfig), parameters.getStatistics()));
			if (i>num)
				break;
			else 
				i++;
		}
		return solverConfigs;
	}

	public void listParameters() {
		// TODO Auto-generated method stub

	}

}
