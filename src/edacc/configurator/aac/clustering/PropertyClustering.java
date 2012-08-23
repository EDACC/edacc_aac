/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edacc.configurator.aac.clustering;

import edacc.api.API;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.model.Instance;
import java.util.List;
import java.util.Random;

/**
 *
 * @author fr
 */
public class PropertyClustering extends ClusteringTemplate implements ClusterMethods{
    
    
    public PropertyClustering(Parameters params, API api, Random rng, List<SolverConfiguration> scs) throws Exception{
        super(params, api, rng, scs);
        
        List<Instance> instanceList = api.getExperimentInstances(params.getIdExperiment());
    }
    
}
