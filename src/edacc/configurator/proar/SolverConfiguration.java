package edacc.configurator.proar;

import edacc.parameterspace.ParameterConfiguration;

public class SolverConfiguration implements Comparable<SolverConfiguration>{
	/**the parameter configuration of the solver configuration*/
	private ParameterConfiguration pConfig;
	
	/**id of the solver configuration from the DB*/
	private int idSolverConfiguration;
	
	/**the cost of the configuration with regards to a statistic function and a metric (cost or runtime); 
	*/
	private Float cost;
	
	/**the name of the configuration*/
	private String name;
	
	/**iteration of the configurator where this configuration was created; 	useful for debuging*/ 
	private int level;
	
	/**List of all jobs that a solver configuration has been executed so far*/
	//TODO definiere datenstruktur für jobs[idSolverConfig]
	//man muss schnell über die idSolverConfig auf die jobs zugreifen und mann muss schnell die 
	//disjunktion von zwei solchen mengen machen können um zu sehen welche jobs man für die neuen config noch erstellen muss
	//muss auch eine size methode haben
	

	public SolverConfiguration(ParameterConfiguration pc, int level){
		this.pConfig = pc;
		this.idSolverConfiguration = 0; 
		this.cost = null;
		this.name = null;
		this.level = level;
	}

	public SolverConfiguration(SolverConfiguration sc){
		this.pConfig = new ParameterConfiguration(sc.pConfig);
		this.idSolverConfiguration = sc.idSolverConfiguration; 
		this.cost = sc.cost;
		this.name = sc.name;
		this.level = sc.level;
	}
	
	public final ParameterConfiguration getParameterConfiguration(){
		return this.pConfig;
	}
	
	public final void setParameterConfiguration(ParameterConfiguration pc){
		this.pConfig = pc;
	}
	
	public final int getIdSolverConfiguration(){
		return this.idSolverConfiguration;
	}
	
	public final void setIdSolverConfiguration(int id){
		this.idSolverConfiguration = id;
	}
	
	public final Float getCost(){
		return this.cost;
	}
	
	public final void setCost(Float cost){
		this.cost = cost;
	}
	
	public final String getName(){
		return this.name;
	}
	
	public final void setName(String name){
		this.name = name;
	}

	@Override
	public int compareTo(SolverConfiguration other) {
		//TODO nicht klar ob man das braucht, aber er waere ein einfache art die Statistik und die metrik
		//hier reinzupacken ohne dass man sich noch drum kuemmern muss
		
		return 0;
	}
	
	
}
