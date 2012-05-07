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
import edacc.parameterspace.graph.ParameterGraph;
import java.util.HashSet;


/**
 * @author balint
 *
 */
public class ILS extends SearchMethods {
        private ParameterGraph paramGraph;
        private double[] parameterCoefficients;
        private HashSet<ParameterConfiguration> usedConfigs;

	private float stdDevFactor = 0.1f;
	private boolean sampleOrdinals = true;
	private int sampleSize = 10;
        private double restartProbability = 0.001d;
        private int pertubationSteps = 3;
        
	
	
	public ILS(AAC pacc, API api, Random rng, 
                Parameters parameters, List<SolverConfiguration> firstSCs) throws Exception{
                
		super(pacc, api, rng, parameters, firstSCs);
                paramGraph = api.loadParameterGraphFromDB(parameters.getIdExperiment());
                usedConfigs = new HashSet<ParameterConfiguration>();
                
                // TODO Auto-generated constructor stub
	}

	/* (non-Javadoc)
	 * @see edacc.configurator.aac.search.SearchMethods#generateNewSC(int, edacc.configurator.aac.SolverConfiguration)
	 */
	@Override
	public List<SolverConfiguration> generateNewSC(int num) throws Exception {
		List<SolverConfiguration> bestSCs = pacc.racing.getBestSolverConfigurations(1);
		SolverConfiguration currentBestSC = (bestSCs.size() > 0 ? bestSCs.get(0) : firstSCs.get(0));
		
		//the parameter configurations generated 
		List<ParameterConfiguration> paramConfs = new ArrayList<ParameterConfiguration>();
		List<SolverConfiguration> solverConfigs = new ArrayList<SolverConfiguration>();
                
                paramConfs = api.loadParameterGraphFromDB(parameters.getIdExperiment()).getGaussianNeighbourhood(currentBestSC.getParameterConfiguration(), rng, stdDevFactor, sampleSize, sampleOrdinals);
		
                
                Collections.shuffle(paramConfs, rng);
		int i=0;
		for (ParameterConfiguration p: paramConfs) {
                        if(usedConfigs.contains(p))
                            continue;
                        usedConfigs.add(p);
			int idSolverConfig = api.createSolverConfig(parameters.getIdExperiment(), p, api.getCanonicalName(parameters.getIdExperiment(), p));
			solverConfigs.add(new SolverConfiguration(idSolverConfig, api.getParameterConfiguration(parameters.getIdExperiment(), idSolverConfig), parameters.getStatistics()));
			if (i>num)
				break;
			else 
				i++;
		}
                if(solverConfigs.size()<num){
                    //TODO: think of something...
                }
		return solverConfigs;
	}

	public void listParameters() {
            System.out.println("--- IteratedLocalSearch parameters ---");
            System.out.println("ILS_StdDevFactor = "+this.stdDevFactor);
            System.out.println("ILS_sampleSize = "+this.sampleSize);
            System.out.println("ILS_sampleOrdinals = "+this.sampleOrdinals);
            System.out.println("ILS_restartProbability = "+this.restartProbability);
            System.out.println("ILS_pertubationSteps = "+this.pertubationSteps);
            //TODO: complete list
            System.out.println("--------------------------------------\n");
	}
        
        private void updateParameterCoefficients(){
            //TODO implement
        }
}
