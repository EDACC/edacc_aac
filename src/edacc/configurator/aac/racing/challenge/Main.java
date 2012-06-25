package edacc.configurator.aac.racing.challenge;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Main {
	static String features_bin = "SAT12_submission/bin/featuresSAT12";
	static String solver_bin = "./probSATc";
	static String clustering = "./clustering";
	
	public static void main(String[] args) throws Exception {
		
		
		
		if (args.length != 3) {
			System.out.println("algorithm instance seed");
			return;
		}
		
		Process p = Runtime.getRuntime().exec(features_bin + " -base " + args[1]);
		BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
		br.readLine();
		String[] features_str = br.readLine().split(",");
		float[] features = new float[features_str.length];
		for (int i = 0; i < features_str.length; i++) {
			features[i] = Float.valueOf(features_str[i]);
		}
		br.close();
		p.destroy();
		
		
		Clustering C = Clustering.deserialize(clustering);
		
		String params;
		if (args[0].equals("mindist")) {
			params = MinDist.getParameters(C, features);
		} else if (args[0].equals("tree")) {
			params = C.P.get(C.tree.query(features));
		} else {
			System.out.println("Did not find algorithm: " + args[0]);
			return;
		}
		
		params = params.replaceAll("<instance>", args[1].replaceAll("\\\\", "\\\\\\\\")).replaceAll("<seed>", args[2]);
		
		System.out.println("c Parameters: " + params);
		
		p = Runtime.getRuntime().exec(solver_bin + " " + params);
		br = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String line;
		while ((line = br.readLine()) != null) {
			System.out.println(line);
		}
	}
}
