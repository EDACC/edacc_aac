package edacc.configurator.math;

import org.rosuda.JRI.Rengine;

public class PCA {
    private Rengine rengine;
    
    public PCA(Rengine rengine) {
        this.rengine = rengine;
    }
    
    /**
     * Returns the first k columns with highest variance of the data transformed by PCA.
     * @param r
     * @param c
     * @param data
     * @param k
     * @return
     */
    public double[][] transform(int r, int c, double[][] data, int k) {
        if (r <= 1) return data;
        
        double[] linData = new double[r*c];
        int l = 0;
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < c; j++) {
                linData[l++] = data[i][j];
            }
        }
        
        rengine.assign("pca_data", linData);
        rengine.eval("pca_data = data.frame(matrix(pca_data, nrow=" + r + ", ncol=" + c + ", byrow=T))");
        rengine.eval("sd. = apply(pca_data, 2, sd)");
        rengine.eval("pca_data = pca_data[!is.na(sd.) & sd. > 0]");
        rengine.eval("pcaed_data = prcomp(pca_data, scale=T, center=T, retx=T)$x");
        rengine.eval("pcaed_data = pcaed_data[,1:(min("+k+", ncol(pcaed_data))]");
        return rengine.eval("pcaed_data").asDoubleMatrix();
    }
    
    
    public static void main(String ... args) throws Exception {
        Rengine rengine = new Rengine(new String[] { "--vanilla" }, false, null);;
        if (!rengine.waitForR()) {
            throw new Exception("Could not initialize Rengine");
        }
        
        
        PCA pca = new PCA(rengine);
        double[][] data = new double[][] {
                { 1.0, 3.0, 2.0},
                { 1.0, 3.0, 2.0},
                { 1.0, 2.0, 3.0},
                { 1.0, 3.0, 2.0},
                { 1.0, 3.0, 2.0},
                { 1.0, 3.0, 2.0},
                { 1.0, 10.0, 3.0},
                };
        double[][] pcaed = pca.transform(data.length, data[0].length, data, 2);
        
        System.out.println(pcaed[6][0]);
        System.out.println(pcaed[6][1]);
        
        
        
        rengine.end();
    }
}
