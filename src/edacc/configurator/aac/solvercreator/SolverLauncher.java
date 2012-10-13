package edacc.configurator.aac.solvercreator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import edacc.util.Pair;

public class SolverLauncher {	
	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			System.out.println("algorithm instance seed [tempdir=<tempdir>]");
			System.exit(1);
		}
		String tempdir = null;
		for (int i = 3; i < args.length; i++) {
			String[] values = args[i].split("=");
			System.out.println(Arrays.toString(values));
			if (values.length != 2) {
				System.out.println("algorithm instance seed [tempdir=<tempdir>]");
				System.exit(1);
			}
			if (values[0].equals("tempdir")) {
				tempdir = values[1];
			}
		}
		
		// load properties
		Properties properties = new Properties();
		File f = new File("solverlauncher.properties");
		InputStream in = new FileInputStream(f);
		properties.load(in);
		in.close();
		
		String features_bin = properties.getProperty("FeaturesBin");
		String features_args = properties.getProperty("FeaturesParameters");
		String data = properties.getProperty("Data");
		
		System.out.println("c calculating instance properties..");
		Process p = Runtime.getRuntime().exec(features_bin + " " + features_args + " " + args[1], null, new File("features"));
		BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
		br.readLine();
		String[] features_str = br.readLine().split(",");
		float[] features = new float[features_str.length];
		for (int i = 0; i < features_str.length; i++) {
			features[i] = Float.valueOf(features_str[i]);
		}
		br.close();
		p.destroy();
		
		System.out.println("c loading data..");
		DecisionTree tree = null;
		RandomForest forest = null;
		Clustering clustering = null;
		for (Object o : SolverCreator.deserialize(data)) {
			if (o instanceof DecisionTree) {
				tree = (DecisionTree) o;
			} else if (o instanceof RandomForest) {
				forest = (RandomForest) o;
			} else if (o instanceof Clustering) {
				clustering = (Clustering) o;
			}
		}
		
		System.out.println("c getting parameters with " + args[0] + " method..");
		Integer scid = null;
		if (args[0].equals("fuzzy")) {
			List<Integer> scids = new LinkedList<Integer>();
			scids.addAll(clustering.M.keySet());
			float rand = new Random(Integer.parseInt(args[2])).nextFloat();
			float cur = 0.f;
			for (int id : scids) {
				scid = id;
				cur += clustering.getWeight(id);
				if (cur >= rand) {
					break;
				}
			}
		} else if (args[0].equals("mindist")) {
			scid = MinDist.getScId(clustering, features);
		} else if (args[0].equals("avgdist")) {
			scid = AvgDist.getScId(clustering, features);
		} else if (args[0].equals("tree")) {
			Pair<Integer, List<Integer>> res = tree.query(features); 
			scid = res.getFirst();
		} else if (args[0].equals("randomforest")) {
			scid = forest.getSolverConfig(features);
		/*} else if (args[0].equals("regression")) {
			float membership = -1.f;
			int scid = -1;
			for (Pair<ParameterConfiguration, Integer> pc : C.tree2.pconfigs) {
				Float tmp = C.tree2.getCost(pc.getFirst(), features);
				if (tmp != null && tmp > membership) {
					membership = tmp;
					scid = pc.getSecond();
				}
			}
			System.out.println("c Regression method: Choosing " + scid + " because instance has predicted membership " + membership + " on this solver config.");
			params = C.P.get(scid);*/
		} else {
			System.out.println("Did not find algorithm: " + args[0]);
			return;
		}
		if (scid == null) {
			System.out.println("Error: could not determine solver config id. Exiting.");
			return;
		}
		Integer sbid = clustering.scToSb.get(scid);
		if (sbid == null) {
			System.out.println("Error: could not determine solver binary id. Exiting.");
			return;
		}
		String params = clustering.P.get(scid);
		String solver_bin = properties.getProperty("SolverBin_" + sbid);
		
		params = params.replaceAll("<instance>", args[1].replaceAll("\\\\", "\\\\\\\\")).replaceAll("<seed>", args[2]);
		
		if (params.contains("<tempdir>")) {
			if (tempdir == null) {
				System.out.println("no tempdir specified in parameter line.");
				System.exit(1);
			} else {
				params = params.replaceAll("<tempdir>", tempdir.replaceAll("\\\\", "\\\\\\\\"));
			}
		}
		System.out.println("c Parameters: " + params);

		p = Runtime.getRuntime().exec(solver_bin + " " + params, null, new File("binary_" + sbid));
		br = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String line;
		while ((line = br.readLine()) != null) {
			System.out.println(line);
		}
	}
}
