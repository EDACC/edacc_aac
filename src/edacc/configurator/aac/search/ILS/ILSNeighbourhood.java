/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edacc.configurator.aac.search.ILS;

import edacc.configurator.aac.SolverConfiguration;
import edacc.parameterspace.ParameterConfiguration;
import java.util.LinkedList;
import java.util.List;

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