/**
 * 
 */
package edacc.configurator.aac.search;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
import edacc.configurator.aac.search.ilsutils.ILSNeighbourhood;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;
import java.util.*;



/**
 * @author mugrauer, schulte, balint
 *
 */
public class ILS extends SearchMethods {
        private AAC aac;
        private ParameterGraph paramGraph;
        //private double[] parameterCoefficients;   //not in use (yet)
        private HashSet<ParameterConfiguration>     //contains all configurations the
                                    usedConfigs;    //search has encountered so far
        
        
        private LinkedList<ILSNeighbourhood> completedNeighbourhoods,
                                                activeNeighbourhoods;
        private ILSNeighbourhood currentNeighbourhood, secondaryNeighbourhood;
                            

	private float stdDevFactor = 0.3f;
	private boolean sampleOrdinals = true;
	private int sampleSize = 40;
        private double restartProbability = 0.001d;
        private int pertubationSteps = 3;
        private boolean debug = false; //used to generate debug log entries
        
        private SolverConfiguration currentBest;
        private SolverConfiguration referenceSolver=null;
        
	
	
	public ILS(AAC pacc, API api, Random rng, 
                Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception{
                
		super(pacc, api, rng, parameters, firstSCs, referenceSCs);
                aac = pacc;
                paramGraph = api.loadParameterGraphFromDB(parameters.getIdExperiment());
                usedConfigs = new HashSet<ParameterConfiguration>();
                completedNeighbourhoods = new LinkedList<ILSNeighbourhood>();
                activeNeighbourhoods = new LinkedList<ILSNeighbourhood>();
                if(!referenceSCs.isEmpty())
                    referenceSolver = referenceSCs.get(0);
                
                //TODO: initialise referenceSolver
                
                // parameters:
                HashMap<String, String> params = parameters.getSearchMethodParameters();
                if(params.containsKey("ILS_stdDevFactor")){
                    stdDevFactor = Float.parseFloat(params.get("ILS_stdDevFactor"));
                }
                if(params.containsKey("ILS_sampleOrdinals")){
                    sampleOrdinals = Boolean.parseBoolean(params.get("ILS_sampleOrdinals"));
                }
                if(params.containsKey("ILS_sampleSize")){
                    sampleSize = Integer.parseInt(params.get("ILS_sampleSize"));
                }
                if(params.containsKey("ILS_restartProbability")){
                    restartProbability = Double.parseDouble(params.get("ILS_restartProbability"));
                }
                if(params.containsKey("ILS_pertubationSteps")){
                    pertubationSteps = Integer.parseInt(params.get("ILS_pertubationSteps"));
                }
                if(params.containsKey("ILS_debug")){
                    debug = Boolean.parseBoolean(params.get("ILS_debug"));
                }
                
                //first neighbourhood
                SolverConfiguration starterConfig;
                if(firstSCs.size()>0){
                    starterConfig = firstSCs.get(0);
                }else{
                    //TODO: find a better way to start without default configs
                    log("No default config found: Using random config instead!");
                    starterConfig = createSolverConfig(paramGraph.getRandomConfiguration(rng));
                }
                currentNeighbourhood = new ILSNeighbourhood(starterConfig, this);
                currentBest = starterConfig;
	}
        
        @Override
        public List<SolverConfiguration> generateNewSC(int num) throws Exception {
            update(); //need to get the most recent information before we can decide on anything
            List<SolverConfiguration> newConfigs;
            int requiredConfigs = num; 
            int currentAvailableConfigs = currentNeighbourhood.getNumberOfAvailableConfigs(requiredConfigs);
            int secondaryAvailableConfigs = -1;
            int prim = 0, sec = 0; //counts the number of delivered configs by current and secondary neighbourhood
            if(requiredConfigs <= currentAvailableConfigs){
                if(debug)
                    debugLog("current neighbourhood could satisfy need for new configs!");
                newConfigs = currentNeighbourhood.getConfigs(requiredConfigs);
                requiredConfigs = 0;
                prim = newConfigs.size();
            }
            else{
                requiredConfigs -= currentAvailableConfigs;                
                newConfigs = currentNeighbourhood.getConfigs(currentAvailableConfigs);    
                prim = newConfigs.size();
                /*if(debug){
                    debugLog("current neighbourhood can supply "+currentAvailableConfigs
                            +" of "+num+" configs ("+newConfigs.size()+" configs delivered. "
                            +requiredConfigs+" more configs needed");
                }*/
                
                if(secondaryNeighbourhood == null){ 
                    startSecondaryNeighbourhood(currentNeighbourhood.getStarter());
                }
                secondaryAvailableConfigs = secondaryNeighbourhood.getNumberOfAvailableConfigs(requiredConfigs);
                List<SolverConfiguration> tmpConfigs;
                if(requiredConfigs <= secondaryAvailableConfigs){                    
                    tmpConfigs = secondaryNeighbourhood.getConfigs(requiredConfigs);
                    newConfigs = mergeLists(newConfigs, tmpConfigs);
                    requiredConfigs = 0;
                }else{
                     //neither current-, nor secondaryNeighbourhood have enough configs                    
                    tmpConfigs = secondaryNeighbourhood.getConfigs(secondaryAvailableConfigs);
                    newConfigs = mergeLists(newConfigs, tmpConfigs);
                    requiredConfigs -= secondaryAvailableConfigs;
                }
                sec = newConfigs.size()-prim;
                /*if(debug){
                    debugLog("secondary neighbourhood could supply "+secondaryAvailableConfigs
                                    +" configs ("+tmpConfigs.size()+" configs delivered). "+newConfigs.size()+" configs in total.");
                }*/
            }
            /* at this point, newConfigs might contain fewer configurations than requested
             * there is nothing to be done about this, however, as there are no more configs
             * in either neighbourhood. The racing methods can cope with it.
             */
            aac.log("ILS: Fetching "+num+" configs: "+newConfigs.size()+" configs delivered! ("+prim+" from primary neighbourhood, "+sec+"from secondary)");
            if(debug && newConfigs.isEmpty()){
                currentNeighbourhood.debugOutput(false);
                secondaryNeighbourhood.debugOutput(true);
            }
            return newConfigs;
        }
        
        private void startSecondaryNeighbourhood(SolverConfiguration starter) throws Exception{
            if(secondaryNeighbourhood != null){
                secondaryNeighbourhood.killHard();
                activeNeighbourhoods.add(secondaryNeighbourhood);
                secondaryNeighbourhood = null;
            }
            ParameterConfiguration p;
            if(rng.nextDouble()<restartProbability){
                p = paramGraph.getRandomConfiguration(rng);
                aac.log("ILS: Possible local minimum: Trying to escape with random configuration!"); 
            }else{
                p = starter.getParameterConfiguration();
                for(int i=0; i<pertubationSteps; i++){
                    p = paramGraph.getRandomNeighbour(p, rng);
                }
                aac.log("ILS: Possible local minimum: Trying to escape with "+pertubationSteps+" pertubation steps"); 
            }
            secondaryNeighbourhood = new ILSNeighbourhood(createSolverConfig(p), this);
            if(debug)
                aac.log("ILS_Debug: new secondary neighbourhood has "+secondaryNeighbourhood.getNumberOfAvailableConfigs(0)+" configs available!");
        }
        
        private void update() throws Exception{
            //Get the latest results on config performance calculated by the racing method
            //sort out all Neighbourhoods that have been evaluated
            LinkedList<ILSNeighbourhood> retain = new LinkedList<ILSNeighbourhood>();
            for(ILSNeighbourhood n : activeNeighbourhoods){
                n.update();
                if(n.isActive())
                    retain.add(n);
                else
                    completedNeighbourhoods.add(n);
            }
            activeNeighbourhoods = retain;
            
            currentNeighbourhood.update();
            if(secondaryNeighbourhood != null)
                secondaryNeighbourhood.update();
            
            
            //Check if the racing procedure has found new incumbents,
            //and deal with them accordingly
            if(currentNeighbourhood.hasNewIncumbent()){
                SolverConfiguration newIncumbent = currentNeighbourhood.getNewIncumbent();
                currentBest = compare(newIncumbent, currentBest) ? newIncumbent : currentBest;
                //TODO: implement way to let the racing procedure decide to keep promising configs
                currentNeighbourhood.killHard();
                activeNeighbourhoods.add(currentNeighbourhood);
                currentNeighbourhood = new ILSNeighbourhood(newIncumbent, this);
                aac.log("ILS: New incumbent found, searching new neighbourhood!");
                if(secondaryNeighbourhood != null){
                    secondaryNeighbourhood.killHard();
                    activeNeighbourhoods.add(secondaryNeighbourhood);
                    secondaryNeighbourhood = null;
                    aac.log("ILS: Killing secondary neighbourhood!");
                }
            }else if(currentNeighbourhood.isEvaluationComplete()){
                //true local minimum
                activeNeighbourhoods.add(currentNeighbourhood);
                if(secondaryNeighbourhood == null){
                    //ideally, this should never happen
                    //but if it does, it won't cause any problems (other than some wasted cpu time)
                    if(debug){
                        debugLog("Primary neighbourhood evaluation is complete, but no secondary neighbourhood is presen!");
                    }
                    startSecondaryNeighbourhood(currentNeighbourhood.getStarter());
                }
                currentNeighbourhood = secondaryNeighbourhood;
                secondaryNeighbourhood = null;        
            }else{
                //Primary Neighbourhood has produced no new incumbent, but is still running
                //check for new incumbents in secondary neighbourhood aswell (if ther is one)
                if(secondaryNeighbourhood != null){
                    if(secondaryNeighbourhood.hasNewIncumbent()){
                        SolverConfiguration newIncumbent = secondaryNeighbourhood.getNewIncumbent();
                        if(compare(newIncumbent, currentBest)){
                            currentBest = newIncumbent;
                            currentNeighbourhood.killHard();
                            activeNeighbourhoods.add(currentNeighbourhood);
                            secondaryNeighbourhood.killHard();
                            activeNeighbourhoods.add(secondaryNeighbourhood);
                            currentNeighbourhood = new ILSNeighbourhood(newIncumbent, this);
                            secondaryNeighbourhood = null;
                        }else{
                            secondaryNeighbourhood.killHard();
                            activeNeighbourhoods.add(secondaryNeighbourhood);
                            secondaryNeighbourhood = new ILSNeighbourhood(newIncumbent, this);
                        }
                    }else if(secondaryNeighbourhood.isEvaluationComplete()){
                        //escape the local minimum
                        startSecondaryNeighbourhood(secondaryNeighbourhood.getStarter());
                    }
                }
            }
            
            //use these results to update the estimated importance of each parameter
            updateParameterCoefficients();
        }

	/* (non-Javadoc)
	 * @see edacc.configurator.aac.search.SearchMethods#generateNewSC(int, edacc.configurator.aac.SolverConfiguration)
	 */
        /* OUTDATED
	@Override
	public List<SolverConfiguration> generateNewSC(int num) throws Exception {
		List<SolverConfiguration> bestSCs = pacc.racing.getBestSolverConfigurations(1);
		SolverConfiguration currentBestSC = (bestSCs.size() > 0 ? bestSCs.get(0) : firstSCs.get(0));
		
		//the parameter configurations generated 
		List<ParameterConfiguration> paramConfs = new ArrayList<ParameterConfiguration>();
		List<SolverConfiguration> solverConfigs = new ArrayList<SolverConfiguration>();
                
                paramConfs = paramGraph.getGaussianNeighbourhood(currentBestSC.getParameterConfiguration(), rng, stdDevFactor, sampleSize, sampleOrdinals);
		
                
                Collections.shuffle(paramConfs, rng);
		int i=1;
		for (ParameterConfiguration p: paramConfs) {
                        if(usedConfigs.contains(p))
                            continue;
                        usedConfigs.add(p);
			int idSolverConfig = api.createSolverConfig(parameters.getIdExperiment(), p, api.getCanonicalName(parameters.getIdExperiment(), p));
			solverConfigs.add(new SolverConfiguration(idSolverConfig, api.getParameterConfiguration(parameters.getIdExperiment(), idSolverConfig), parameters.getStatistics()));
			if (i==num)
				break;
			else 
				i++;
		}
                if(solverConfigs.size()<num){
                    //TODO: think of something...
                }
		return solverConfigs;
	}*/

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
        
                
        /* checks if the specified config has already been evaluated by this configuration run
         * 
         */
        public boolean isConfigAlreadyEvaluated(ParameterConfiguration p){
            return usedConfigs.contains(p);
        }
        
        /* turns a ParameterConfiguration into a Solverconfiguration
         * (also creates an entry in the DB for this configuration)
        */
        public final SolverConfiguration createSolverConfig(ParameterConfiguration p) throws Exception{
            int idSolverConfig = api.createSolverConfig(parameters.getIdExperiment(), p, api.getCanonicalName(parameters.getIdExperiment(), p));
            return new SolverConfiguration(idSolverConfig, api.getParameterConfiguration(parameters.getIdExperiment(), idSolverConfig), parameters.getStatistics());
        }
        
        /* creates the neighbourhood of the given ParameterConfiguration
         * higher values for the stage parameter will result in a wider spread neighbourhood
         * (default for stage is 1)
         * 
         */
        public List<ParameterConfiguration> getNeighbourhood(ParameterConfiguration p,
                                                                            int stage){
            float stdDev;
            switch(stage){
                case 2: stdDev = (1f+stdDevFactor)/2f; break;
                case 3: stdDev = 1f; break;
                default: stdDev = stdDevFactor;                    
            }
            List<ParameterConfiguration> configs =
                        paramGraph.getGaussianNeighbourhood(p, rng, stdDev, sampleSize, 
                sampleOrdinals);
            sortParameterPriority(p, configs);
            //System.out.println("New Neighbourhood: "+configs.size()+" configs!");
            return configs;
        }
        
        /* Sorts the list according to the calculated priority of parameters. This means that
         * a neighbour that differs from the start config in a parameter deemed to be important,
         * will end up at the start of the list, rather than at the end.
         */
        private void sortParameterPriority(ParameterConfiguration start, 
                                    List<ParameterConfiguration> neighbours){
            //TODO: Implement
            Collections.shuffle(neighbours);
        }
        
        /* signals the racing procedure that evaluating the specified configuration
         * is no longer necessary, and should be aborted to save resources.
         */
        public void killConfig(SolverConfiguration s){
            //TODO: implement functionality once the racing procedures are updated
        }
        
        /* compares two configs
         * works only if both configs have the "finished" flag set
         * works for roar and completeEvaluation racing methods
         * 
         * @return true, if a beats b
         */
        public boolean compare(SolverConfiguration a, SolverConfiguration b){
            if(a.getNumFinishedJobs()>b.getNumFinishedJobs())
                return true;
            if(a.getNumFinishedJobs()==b.getNumFinishedJobs())
                if(a.getCost() < b.getCost())
                    return true;        
            return false;
        }
        
        /* assesses the quality of the given config
         * (this should be used to decide whether or not it is justified to enlarge a config's
         *  neighbourhood)
         * 
         * @return  3, if the config can beat the reference solver
         *          2, if the config cannot beat the reference solver, but is the best known
         *                  config in the parameter space
         *          1, otherwise
         */
        public int assessQuality(SolverConfiguration s){            
            if(s.equals(currentBest) || compare(s, currentBest)){
                if(referenceSolver == null || compare(s, referenceSolver))
                    return 3;
                return 2;
            }
            return 1;
        }
        
        public void log(String msg){
            aac.log("ILS: "+msg);
        }
        public void debugLog(String msg){
            aac.log("ILS_debug: "+msg);
        }
        
        public boolean isDebug(){
            return debug;
        }
        
        public List<SolverConfiguration> mergeLists(
                List<SolverConfiguration> target, List<SolverConfiguration> source){
            for(SolverConfiguration s : source)
                target.add(s);
            return target;
        }
}
