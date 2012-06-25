package edacc.configurator.aac.racing.challenge;

import java.util.HashMap;
import java.util.List;

public class MinDist {
	public static String getParameters(Clustering C, float[] features) {
		HashMap<Integer, List<Integer>> c = C.getClustering(true);
		float[] normalize = new float[features.length];
		for (int i = 0; i < normalize.length; i++) {
			normalize[i] = 0.f;
		}
		
		for (int scid : c.keySet()) {
			for (int iid : c.get(scid)) {
				float[] f = C.F.get(iid);
				for (int i = 0; i < normalize.length; i++) {
					if (normalize[i] < f[i]) {
						normalize[i] = f[i];
					}
				}
			}
		}
		
		for (int i = 0; i < features.length; i++) {
			if (normalize[i] > 1.f || normalize[i] < -1.f)
				features[i] /= normalize[i];
		}
		
		String params = null;
		float mindist = Float.POSITIVE_INFINITY;
		for (int scid : c.keySet()) {
			for (int iid : c.get(scid)) {
				float[] f = C.F.get(iid);
				for (int i = 0; i < f.length; i++) {
					if (normalize[i] > 1.f || normalize[i] < -1.f)
						f[i] /= normalize[i];
				}
				
				float d = dist(f, features);
				if (d < mindist) {
					mindist = d;
					params = C.P.get(scid);
				}
			}
		}
		System.out.println("c mindist: " + mindist);
		return params;
	}
	
	private static float dist(float[] f1, float[] f2) {
		float res = 0.f;		
		for (int i = 0; i < f1.length; i++) {
			res += dist(f1[i], f2[i]);
		}
		return res;
	}
	
	private static float dist(float f1, float f2) {
		return Math.abs(f1 - f2);
	}
	
}
