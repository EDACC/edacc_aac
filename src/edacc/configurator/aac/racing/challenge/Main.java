package edacc.configurator.aac.racing.challenge;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import edacc.util.Pair;

public class Main {	
	public static void main(String[] args) throws Exception {
		if (args.length != 3) {
			System.out.println("algorithm instance seed");
			return;
		}
		
		// load properties
		Properties properties = new Properties();
		File f = new File("solverlauncher.properties");
		InputStream in = new FileInputStream(f);
		properties.load(in);
		in.close();
		
		String features_bin = properties.getProperty("FeaturesBin");
		String features_args = properties.getProperty("FeaturesParameters");
		String solver_bin = properties.getProperty("SolverBin");
		String clustering = properties.getProperty("Clustering");
		
		System.out.println("c calculating instance properties..");
		Process p = Runtime.getRuntime().exec(features_bin + " " + features_args + " " + args[1]);
		BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
		br.readLine();
		String[] features_str = br.readLine().split(",");
		float[] features = new float[features_str.length];
		for (int i = 0; i < features_str.length; i++) {
			features[i] = Float.valueOf(features_str[i]);
		}
		br.close();
		p.destroy();
		
		System.out.println("c loading clustering..");
		Clustering C = Clustering.deserialize(clustering);
		System.out.println("c getting parameters with " + args[0] + " method..");
		String params;
		Integer seed = null;
		if (args[0].equals("mindist")) {
			params = MinDist.getParameters(C, features);
		} else if (args[0].equals("tree")) {
			Random rng = new Random(Integer.parseInt(args[2]));
			Pair<Integer, List<Integer>> res = C.tree.query(features); 
			params = C.P.get(res.getFirst());
			List<Integer> seeds = new LinkedList<Integer>();
			Integer ins = null;
			float cost = Float.POSITIVE_INFINITY;
			for (int instanceId : res.getSecond()) {
				float tmp = C.C.get(res.getFirst())[C.I.get(instanceId)];
				if (tmp < cost) {
					cost = tmp;
					ins = instanceId;
				}
			}
			
			if (ins != null) {
				seeds.addAll(C.seeds.get(ins));
				seed = seeds.get(rng.nextInt(seeds.size()));
				System.out.println("c using seed " + seed + " from instance " + ins + " with cost on instance " + cost + ".");
			}
		} else if (args[0].equals("randomforest")) {
			params = C.P.get(C.forest.getSolverConfig(features));
		} else {
			System.out.println("Did not find algorithm: " + args[0]);
			return;
		}
		if (seed != null) {
			System.out.println("c using seed from configuration experiment!");
			params = params.replaceAll("<instance>", args[1].replaceAll("\\\\", "\\\\\\\\")).replaceAll("<seed>", seed.toString());
		} else {
			params = params.replaceAll("<instance>", args[1].replaceAll("\\\\", "\\\\\\\\")).replaceAll("<seed>", args[2]);
		}
		System.out.println("c Parameters: " + params);
		
		p = Runtime.getRuntime().exec(solver_bin + " " + params);
		br = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String line;
		while ((line = br.readLine()) != null) {
			System.out.println(line);
		}
	}
}
