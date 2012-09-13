package edacc.configurator.aac.clustering;

public class Costs {
	private float costSc1;
	private float costSc2;
	private int runsInCommon;
	
	public Costs(float costSc1, float costSc2 ) {
		this.costSc1 = costSc1;
		this.costSc2 = costSc2;
	}
	
	public Costs(float costSc1, float costSc2, int runsInCommon) {
		this.costSc1 = costSc1;
		this.costSc2 = costSc2;
		this.runsInCommon = runsInCommon;
	}

	public float getCostSc1() {
		return costSc1;
	}

	public void setCostSc1(float costSc1) {
		this.costSc1 = costSc1;
	}

	public float getCostSc2() {
		return costSc2;
	}

	public void setCostSc2(float costSc2) {
		this.costSc2 = costSc2;
	}

	public int getRunsInCommon() {
		return runsInCommon;
	}

	public void setRunsInCommon(int runsInCommon) {
		this.runsInCommon = runsInCommon;
	}
	
}
