package edacc.configurator.math;

import org.rosuda.JRI.Rengine;

public class ClusterSilhouette {
    private double[] linearizedData;
    private int r, c;
    private Rengine rengine;
    
    public ClusterSilhouette(Rengine rengine, int r, int c, double[][] X) throws Exception {
        if (rengine.eval("library(cluster)") == null) {
            rengine.end();
            throw new Exception("Did not find R library cluster (try running install.packages(\"cluster\")).");
        }
        
        linearizedData = new double[r*c];
        int ix = 0;
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < c; j++) {
                linearizedData[ix++] = X[i][j]; 
            }
        }
        this.r = r;
        this.c = c;
        this.rengine = rengine;
    }
    
    public int findNumberOfClusters(int maxK) {
        rengine.assign("data", linearizedData);
        rengine.eval("data <- matrix(data, nrow=" + r + ", ncol=" + c + ", byrow=T)");
        rengine.eval("data_dist = dist(data)");
        rengine.eval("hc = hclust(data_dist)");
        double maxSil = 0.0;
        int bestK = 2;
        for (int k = 2; k < maxK; k++) {
            double sil = rengine.eval("summary(silhouette(cutree(hc, " + k + "), data_dist))$avg.width").asDouble();
            if (sil > maxSil) {
                maxSil = sil;
                bestK = k;
            }
        }
        return bestK;
    }
    
    public int[] clusterData(int k) {
        rengine.assign("data", linearizedData);
        rengine.eval("data <- matrix(data, nrow=" + r + ", ncol=" + c + ", byrow=T)");
        rengine.eval("data_dist = dist(data)");
        rengine.eval("hc = hclust(data_dist)");
        rengine.eval("cl = cutree(hc, " + k + ")");
        return rengine.eval("cl").asIntArray();
    }
}
