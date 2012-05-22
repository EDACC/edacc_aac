/**
 * 
 */
package edacc.configurator.aac.search;

import edacc.api.API;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.Parameters;
import edacc.configurator.aac.SolverConfiguration;
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
        
        private SolverConfiguration currentBest;
        private SolverConfiguration referenceSolver;
        
	
	
	public ILS(AAC pacc, API api, Random rng, 
                Parameters parameters, List<SolverConfiguration> firstSCs, List<SolverConfiguration> referenceSCs) throws Exception{
                
		super(pacc, api, rng, parameters, firstSCs, referenceSCs);
                aac = pacc;
                paramGraph = api.loadParameterGraphFromDB(parameters.getIdExperiment());
                usedConfigs = new HashSet<ParameterConfiguration>();
                completedNeighbourhoods = new LinkedList<ILSNeighbourhood>();
                activeNeighbourhoods = new LinkedList<ILSNeighbourhood>();
                
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
                
                //first neighbourhood
                SolverConfiguration starterConfig;
                if(firstSCs.size()>0){
                    starterConfig = firstSCs.get(0);
                }else{
                    //TODO: find a better way to start without default configs
                    aac.log("ILS: No default config found: Using random config instead!");
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
            int availableConfigs = currentNeighbourhood.getNumberOfAvailableConfigs(requiredConfigs);
            if(requiredConfigs <= availableConfigs){
                newConfigs = currentNeighbourhood.getConfigs(requiredConfigs);
                requiredConfigs = 0;
            }
            else{
                requiredConfigs -= availableConfigs;
                newConfigs = currentNeighbourhood.getConfigs(availableConfigs);
                if(secondaryNeighbourhood == null){
                                       
                    ParameterConfiguration p;
                    if(rng.nextDouble() <= restartProbability){
                        p = paramGraph.getRandomConfiguration(rng);
                        aac.log("ILS: Possible local minimum: Trying to escape with random configuration!"); 
                    }else{
                        p = currentNeighbourhood.getStarter().getParameterConfiguration();
                        for(int i=0; i<pertubationSteps; i++){
                            p = paramGraph.getRandomNeighbour(p, rng);
                        }
                        aac.log("ILS: Possible local minimum: Trying to escape with "+pertubationSteps+" pertubation steps"); 
                    }                    
                    SolverConfiguration s = createSolverConfig(p);
                    secondaryNeighbourhood = new ILSNeighbourhood(s, this);
                }
                availableConfigs = secondaryNeighbourhood.getNumberOfAvailableConfigs(requiredConfigs);
                if(requiredConfigs <= availableConfigs){
                    requiredConfigs = 0;
                    newConfigs.addAll(secondaryNeighbourhood.getConfigs(requiredConfigs));
                }else{
                     //neither current-, nor secondaryNeighbourhood have enough configs
                    requiredConfigs -= availableConfigs;
                    newConfigs.addAll(secondaryNeighbourhood.getConfigs(availableConfigs));
                }
               
            }
            /* at this point, newConfigs might contain fewer configurations than requested
             * there is nothing to be done about this, however, as there are no more configs
             * in either neighbourhood. The racing methods can cope with it.
             */
            
            
            aac.log("ILS: Fetching "+num+" configs: "+newConfigs.size()+" configs delivered!");
            return newConfigs;
        }
        
        private void update(){
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
                currentNeighbourhood = 
                        new ILSNeighbourhood(currentNeighbourhood.getNewIncumbent(), this);
                aac.log("ILS: New incumbent found, searching new neighbourhood!");
                if(secondaryNeighbourhood != null){
                    secondaryNeighbourhood.killHard();
                    activeNeighbourhoods.add(secondaryNeighbourhood);
                    secondaryNeighbourhood = null;
                    aac.log("ILS: Killing secondary neighbourhood!");
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
        
       
}




/**
 *
 * @author mugrauer
 */
class ILSNeighbourhood {
    private ILS ils;
    private SolverConfiguration starter; //this is the initial config that was used
                                            //to create this neighbourhood
    
    private LinkedList<ParameterConfiguration> pendingConfigs; //waiting to be evaluated
    private LinkedList<SolverConfiguration>
                    runningConfigs,         //currently being evaluated
                    completedConfigs;       //evaluation complete
    
    private SolverConfiguration currentBest;
    private int stage = 1;
    
    public ILSNeighbourhood(SolverConfiguration starter, ILS ils){
        this.starter = starter;
        this.ils = ils;
        pendingConfigs = new LinkedList<ParameterConfiguration>();
        runningConfigs = new LinkedList<SolverConfiguration>();
        completedConfigs = new LinkedList<SolverConfiguration>();
        currentBest = null;
        
        List<ParameterConfiguration> tmpConfigList;
        tmpConfigList = ils.getNeighbourhood(starter.getParameterConfiguration(), 1);        
        //remove configs that have previously been evaluated
        for(ParameterConfiguration p: tmpConfigList){
            if(!ils.isConfigAlreadyEvaluated(p))
                pendingConfigs.add(p);
        }
        
    }
    
    /* takes and returns the specified number of configurations from this neighbourhood if
     * and only if there are enough configurations left. Check this by calling 
     * numberOfAvailableConfigs first
     * 
     */
    public List<SolverConfiguration> getConfigs(int num) throws Exception{
        if(num > getNumberOfAvailableConfigs(0))
            return null; //fail fast
        LinkedList<SolverConfiguration> configs = new LinkedList<SolverConfiguration>();
        SolverConfiguration c;
        for(int i=0; i<num; i++){
            c = ils.createSolverConfig(pendingConfigs.remove(0));
            runningConfigs.add(c);
            configs.add(c);
        }        
        return configs;
    }
    
    /* Removes all evaluated configs from the list of running configs, and inserts
     * them into the list of completed configs.
     * Updates the currentBest config in the process, if needed.
     * 
     */
    public void update(){
        if(runningConfigs.isEmpty())
            return;
        SolverConfiguration c,
                first=null; //first config that needs to be retained in runningConfigs
        //remove all finished configs, put the ones that haven't finished yet back in
        while(runningConfigs.peekFirst() != first){
            c = runningConfigs.poll();
            
            if(c.isFinished()){
                completedConfigs.add(c);
                if(currentBest == null){
                    //new incumbent found, no need to evaluate any more configs in here
                    currentBest = c;
                    pendingConfigs.clear();
                }
                else if(ils.compare(c, currentBest))
                    currentBest = c;
            }            
            else{
                if(first == null) first = c;
                runningConfigs.offerLast(c);
            }
        }
    }
    
    /* returns true if and only if the entire neighbourhood has been evaluated,
     * i.e. there are no more configurations in the neighbourhood whose evaluation
     * a) is currently running, or b) has not been started yet
     * 
     */
    public boolean isEvaluationComplete(){
        if(isActive())
            return false;
        else
            return !pendingConfigs.isEmpty();
    }
    
    /* returns true if and only if there are configurations in this neighbourhood
     * whose evaluation has been started, but is not yet complete
     * 
     */
    public boolean isActive(){
        return !runningConfigs.isEmpty();
    }
    
    /* returns the number of configurations in this neighbourhood
     * whose evaluation has not been started yet
     * A desired number of configurations can be specified. This method will attempt to
     * meet that number, readying more configurations if neccessary and possible. To avoid
     * this behaviour, and just get the number of currently ready configs, desiredNumber 
     * should be set to 0.
     */
    public int getNumberOfAvailableConfigs(int desiredNumber){
        LinkedList<ParameterConfiguration> toRemove= new LinkedList<ParameterConfiguration>();
        for(ParameterConfiguration p : pendingConfigs){
            if(ils.isConfigAlreadyEvaluated(p))
                toRemove.add(p);
        }
        pendingConfigs.removeAll(toRemove);
        while(pendingConfigs.size() < desiredNumber){
            if(nextStage()==false);
                break;
        }
        return pendingConfigs.size();
    }
    
    private boolean nextStage(){
        if(stage == 3)
            return false;
        int quality = ils.assessQuality(starter);
        if(quality>stage){
            stage++;
            List<ParameterConfiguration> neighbours = 
                    ils.getNeighbourhood(starter.getParameterConfiguration(), stage);
            List<ParameterConfiguration> retain = new LinkedList<ParameterConfiguration>();
            for(ParameterConfiguration p : neighbours){
                if(!ils.isConfigAlreadyEvaluated(p))
                    retain.add(p);
            }
            pendingConfigs.removeAll(retain);   //this is done to avoid duplicates in
            pendingConfigs.addAll(retain);      //pendingConfigs
            return true;
        }
        return false;
    }
    
    public int getNeighbourhoodSize(){
        return pendingConfigs.size() + runningConfigs.size() + completedConfigs.size();
    }
    
    public int getNumberOfEvaluatedConfigs(){
        return completedConfigs.size();
    }
    
    public List<SolverConfiguration> getEvaluatedConfigs(){
        return completedConfigs;
    }
    
    //prevent this neighbourhood from creating new configurations
    public void kill(){
        pendingConfigs.clear();
    }
    
    /* prevents this neighbourhood from creating new configurations and tries to abort the
     * evaluation of currently running configs
     */
    public void killHard(){
        kill();
        for(SolverConfiguration s : runningConfigs){
            ils.killConfig(s);            
        }
        runningConfigs.clear();
    }
    
    /* returns the best known config in this neighbourhood (drawing from the pool of configurations
     * that have already been evaluated).
     * returns null if no configs have been evaluated yet 
     */
    public SolverConfiguration getBestConfig(){
        return currentBest;
    }
    /* see getBestConfig()
     * Additionally, this method will also return null if the best known config could not
     * beat the starerConfig for this neighbourhood
     */
    public SolverConfiguration getNewIncumbent(){
        if(currentBest == null)
            return null;
        return (ils.compare(currentBest, starter)) ? currentBest : null;
    }
    
    /* returns whether or not there is a config in this neighbourhood that was able to beat the
     * starter config
     */
    public boolean hasNewIncumbent(){
        return getNewIncumbent() != null;
    }
    
    
    
    public SolverConfiguration getStarter(){
        return starter;
    }
}
