package edacc.configurator.aac.clustering;

public class Costs {
	private double costSc1;
	private double costSc2;
	private int runsInCommon;
	
	public Costs(double costSc1, double costSc2 ) {
		this.costSc1 = costSc1;
		this.costSc2 = costSc2;
	}
	
	public Costs(double costSc1, double costSc2, int runsInCommon) {
		this.costSc1 = costSc1;
		this.costSc2 = costSc2;
		this.runsInCommon = runsInCommon;
	}

	public double getCostSc1() {
		return costSc1;
	}

	public void setCostSc1(double costSc1) {
		this.costSc1 = costSc1;
	}

	public double getCostSc2() {
		return costSc2;
	}

	public void setCostSc2(double costSc2) {
		this.costSc2 = costSc2;
	}

	public int getRunsInCommon() {
		return runsInCommon;
	}

	public void setRunsInCommon(int runsInCommon) {
		this.runsInCommon = runsInCommon;
	}
	
}
