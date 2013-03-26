package edacc.configurator.aac.course;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.aac.util.RInterface;
import edacc.model.Instance;
import edacc.model.InstanceDAO;

public class StratifiedClusterCourseTest {
    public static void main(String ... args) throws Exception {
        edacc.model.DatabaseConnector.getInstance().connect("host", 3306, "user", "db", "pw", false, true, 8, false, false);
        List<Instance> instances = InstanceDAO.getAllByExperimentId(404);
        
        List<String> fNames = new LinkedList<String>();
        fNames.add("Pre-featuretime");
        fNames.add("POSNEG-RATIO-CLAUSE-mean");
        fNames.add("POSNEG-RATIO-CLAUSE-coeff-variation");
        fNames.add("POSNEG-RATIO-CLAUSE-min");
        fNames.add("POSNEG-RATIO-CLAUSE-max");
        fNames.add("POSNEG-RATIO-CLAUSE-entropy");
        fNames.add("VCG-CLAUSE-mean");
        fNames.add("VCG-CLAUSE-coeff-variation");
        fNames.add("VCG-CLAUSE-min");
        fNames.add("VCG-CLAUSE-max");
        fNames.add("VCG-CLAUSE-entropy");
        fNames.add("UNARY");
        fNames.add("BINARY+");
        fNames.add("TRINARY+");
        fNames.add("Basic-featuretime");
        fNames.add("VCG-VAR-mean");
        fNames.add("VCG-VAR-coeff-variation");
        fNames.add("VCG-VAR-min");
        fNames.add("VCG-VAR-max");
        fNames.add("VCG-VAR-entropy");
        fNames.add("POSNEG-RATIO-VAR-mean");
        fNames.add("POSNEG-RATIO-VAR-stdev");
        fNames.add("POSNEG-RATIO-VAR-min");
        fNames.add("POSNEG-RATIO-VAR-max");
        fNames.add("POSNEG-RATIO-VAR-entropy");
        fNames.add("HORNY-VAR-mean");
        fNames.add("HORNY-VAR-coeff-variation");
        fNames.add("HORNY-VAR-min");
        fNames.add("HORNY-VAR-max");
        fNames.add("HORNY-VAR-entropy");
        fNames.add("horn-clauses-fraction");
        fNames.add("VG-mean");
        fNames.add("VG-coeff-variation");
        fNames.add("VG-min");
        fNames.add("VG-max");
        fNames.add("KLB-featuretime");
        fNames.add("CG-mean");
        fNames.add("CG-coeff-variation");
        fNames.add("CG-min");
        fNames.add("CG-max");
        fNames.add("CG-entropy");
        fNames.add("cluster-coeff-mean");
        fNames.add("cluster-coeff-coeff-variation");
        fNames.add("cluster-coeff-min");
        fNames.add("cluster-coeff-max");
        fNames.add("cluster-coeff-entropy");
        fNames.add("CG-featuretime");
        
        List<String> fSizeNames = new LinkedList<String>();
        fSizeNames.add("nvars");

        StratifiedClusterCourse sc = new StratifiedClusterCourse(RInterface.getRengine(), instances, fNames, fSizeNames, 1, new Random(), null, null, null);
        
        System.out.println("Stratified, clustered course:");
        for (InstanceIdSeed isp: sc.getCourse()) {
            Instance i = InstanceDAO.getById(isp.instanceId);
           System.out.println(i.getName() + " " + isp.instanceId + " " + isp.seed);
        }
        
        
        System.out.println("Random course:");
        Collections.shuffle(instances);
        for (Instance i: instances) {
            System.out.println(i.getName());
        }

    }
}
