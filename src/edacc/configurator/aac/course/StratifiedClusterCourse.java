package edacc.configurator.aac.course;

import java.util.List;

import edacc.model.Instance;
import edacc.configurator.aac.InstanceIdSeed;

public class StratifiedClusterCourse {
    List<InstanceIdSeed> course;
    
    public StratifiedClusterCourse(List<Instance> instances, float maxExpansionFactor) {
        if (instances.size() == 0) throw new RuntimeException("Course has to consists of at least one instance.");
        if (maxExpansionFactor <= 0) throw new RuntimeException("maxExpansionFactor has to be > 0.");
        
        
    }
}
