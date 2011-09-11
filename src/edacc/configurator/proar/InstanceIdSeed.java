package edacc.configurator.proar;

public class InstanceIdSeed {
	int instanceId;
	int seed;
	
	public InstanceIdSeed(int instanceId, int seed) {
		this.instanceId = instanceId;
		this.seed = seed;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + instanceId;
		result = prime * result + seed;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		InstanceIdSeed other = (InstanceIdSeed) obj;
		if (instanceId != other.instanceId)
			return false;
		if (seed != other.seed)
			return false;
		return true;
	}	
}