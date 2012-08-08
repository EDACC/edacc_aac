package edacc.configurator.aac.search;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.configurator.aac.racing.FRace;
import edacc.configurator.aac.racing.SMFRace;
import edacc.configurator.math.SamplingSequence;
import edacc.parameterspace.Parameter;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.domain.CategoricalDomain;
import edacc.parameterspace.domain.FlagDomain;
import edacc.parameterspace.domain.IntegerDomain;
import edacc.parameterspace.domain.OrdinalDomain;
import edacc.parameterspace.domain.RealDomain;
import edacc.parameterspace.graph.ParameterGraph;

public class RandomSequenceSampling extends SearchMethods {
    private ParameterGraph pspace;
    private List<Parameter> params;
    private SamplingSequence sequence;
    private double sequenceValues[][];
    private int currentSequencePosition = 0;
    private int maxSamples = 100;
    
    public RandomSequenceSampling(AAC pacc, API api, Random rng, Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception {
        super(pacc, api, rng, parameters, firstSCs, referenceSCs);
        
        String samplingPath;
        samplingPath = parameters.getSearchMethodParameters().get("RandomSequenceSampling_samplingPath");
        
        String val;
        if ((val = parameters.getSearchMethodParameters().get("RandomSequenceSampling_samplingPath")) != null)
            samplingPath = val;
        if ((val = parameters.getSearchMethodParameters().get("RandomSequenceSampling_maxSamples")) != null)
            this.maxSamples = Integer.valueOf(val);

        pspace = api.loadParameterGraphFromDB(parameters.getIdExperiment());
        params = new LinkedList<Parameter>();
        sequence = new SamplingSequence(samplingPath);
        
        for (Parameter p: api.getConfigurableParameters(parameters.getIdExperiment())) {
            params.add(p);
        }
        
        sequenceValues = sequence.getSequence(params.size(), maxSamples);
    }

    @Override
    public List<SolverConfiguration> generateNewSC(int num) throws Exception {
        List<SolverConfiguration> solverConfigs = new LinkedList<SolverConfiguration>();
        if (currentSequencePosition + 1 > maxSamples) return solverConfigs;
        
        if (pacc.racing instanceof FRace || pacc.racing instanceof SMFRace) {
            // FRace and SMFRace don't automatically use the old best configurations
            solverConfigs.addAll(pacc.racing.getBestSolverConfigurations(num));
        }
        
        for (int i = 0; i < num - solverConfigs.size(); i++) {
            if (currentSequencePosition + 1 > maxSamples) break;
            ParameterConfiguration pc = mapRealTupleToParameters(sequenceValues[currentSequencePosition++]);
            int idSC = api.createSolverConfig(parameters.getIdExperiment(), pc, "SN: " + currentSequencePosition);
            solverConfigs.add(new SolverConfiguration(idSC, pc, parameters.getStatistics()));
        }
        return solverConfigs;
    }

    @Override
    public List<String> getParameters() {
    	List<String> p = new LinkedList<String>();
    	p.add("% --- RandomSequenceSampling parameters ---");
    	p.add("RandomSequenceSampling_samplingPath = <REQUIRED> % (Path to the external sequence generating program)");
    	p.add("RandomSequenceSampling_maxSamples = "+this.maxSamples+ " % (How many configurations should be evaluated at most)");
    	p.add("% -----------------------");
        return p;
    }
    
    private ParameterConfiguration mapRealTupleToParameters(double[] values) {
        ParameterConfiguration pc = pspace.getRandomConfiguration(rng);
        int i = 0;
        for (Parameter p: params) {
            if (pc.getParameterValue(p) == null) continue;
            double v = values[i++];
            if (p.getDomain() instanceof RealDomain) {
                RealDomain dom = (RealDomain)p.getDomain();
                pc.setParameterValue(p, dom.getLow() + v * (dom.getHigh() - dom.getLow()));
            } else if (p.getDomain() instanceof IntegerDomain) {
                IntegerDomain dom = (IntegerDomain)p.getDomain();
                pc.setParameterValue(p, Math.round(dom.getLow() + v * (dom.getHigh() - dom.getLow())));
            } else if (p.getDomain() instanceof CategoricalDomain) {
                CategoricalDomain dom = (CategoricalDomain)p.getDomain();
                List<String> categories = new LinkedList<String>(dom.getCategories());
                Collections.sort(categories);
                int ix = (int) (v * categories.size());
                if (ix == categories.size()) ix = 0;
                pc.setParameterValue(p, categories.get(ix));
            } else if (p.getDomain() instanceof OrdinalDomain) {
                OrdinalDomain dom = (OrdinalDomain)p.getDomain();
                int ix = (int) (v * dom.getOrdered_list().size());
                if (ix == dom.getOrdered_list().size()) ix = 0;
                pc.setParameterValue(p, dom.getOrdered_list().get(ix));
            } else if (p.getDomain() instanceof FlagDomain) {
                if (v < 0.5) {
                    pc.setParameterValue(p, FlagDomain.FLAGS.ON);
                } else {
                    pc.setParameterValue(p, FlagDomain.FLAGS.OFF);
                }
            }
        }
        return pc;
    }

	@Override
	public void searchFinished() {
		// TODO Auto-generated method stub
		
	}

}
