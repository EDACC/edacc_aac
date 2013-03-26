package edacc.configurator.aac.course;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.rosuda.JRI.Rengine;

import edacc.model.Instance;
import edacc.model.InstanceDAO;
import edacc.model.InstanceHasProperty;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.InstanceIdSeed;
import edacc.configurator.math.ClusterSilhouette;

/**
 * Builds a racing course of instance-seed pairs where seeds are drawn at random.
 * All instances are first clustered based on instance-features that are thought to
 * capture the characteristic properties of the class (or distribution) of each instance.
 * 
 * Within each cluster instances are sorted according to a property that reflects their size,
 * i.e. a property that probably highly correlates with the time it takes to solve that instance.
 *
 * The course is then constructed by sampling one instance from each cluster and from smaller
 * to larger instances.
 * 
 * TODO:
 * - use g-means to find the number of clusters automagically
 * - Sample stratified (proportional to cluster sizes) instead of round-robin (?)
 */
public class StratifiedClusterCourse {
    private List<InstanceIdSeed> course;
    private int k;

    public StratifiedClusterCourse(Rengine rengine, List<Instance> instances, List<String> instanceFeatureNames, List<String> instanceSizeFeatureNames, int maxExpansionFactor, Random rng, String featureFolder, String featureCacheFolder, final Map<Integer, Double> instanceHardness) throws Exception {
        if (instances.size() == 0) throw new IllegalArgumentException("List of instances has to contain at least one instance.");
        if (maxExpansionFactor <= 0) throw new IllegalArgumentException("maxExpansionFactor has to be >= 1.");
        this.course = new ArrayList<InstanceIdSeed>(maxExpansionFactor * instances.size());
        
        if ((instanceFeatureNames == null || instanceFeatureNames.size() == 0) && featureFolder == null) {
            // no features given, shuffle instances
            List<Instance> shuffledInstances = new LinkedList<Instance>(instances);
            Collections.shuffle(shuffledInstances);
            for (int i = 0; i < maxExpansionFactor; i++) {
                for (int j = 0; j < shuffledInstances.size(); j++) {
                    course.add(new InstanceIdSeed(shuffledInstances.get(j).getId(), rng.nextInt(Integer.MAX_VALUE)));
                }
            }
            return;
        }
        
        Collections.shuffle(instances, rng);
       
        if (instanceFeatureNames == null) instanceFeatureNames = new LinkedList<String>();
        if (instanceSizeFeatureNames == null) instanceSizeFeatureNames = new LinkedList<String>();
        
        if (featureFolder != null) {
            for (String feature: AAC.getFeatureNames(new File(featureFolder))) instanceFeatureNames.add(feature);
        } else {
            // TODO: Load from configuration?
            //instanceFeatureNames.add("POSNEG-RATIO-CLAUSE-mean");
        }

        double[][] instanceFeatures = new double[instances.size()][instanceFeatureNames.size()];
        final double[][] instanceSizeFeatures = new double[instances.size()][instanceSizeFeatureNames.size()];
        for (Instance instance: instances) {
            Map<String, Float> featureValueByName = new HashMap<String, Float>();
            Map<String, Float> sizeFeatureValueByName = new HashMap<String, Float>();
            
            if (featureFolder != null) {
                float[] featureValues = AAC.calculateFeatures(instance.getId(), new File(featureFolder), new File(featureCacheFolder));
                for (int i = 0; i < featureValues.length; i++) {
                    featureValueByName.put(instanceFeatureNames.get(i), featureValues[i]);
                }
            } else {
                for (InstanceHasProperty ihp: instance.getPropertyValues().values()) {
                    if (instanceFeatureNames.contains(ihp.getProperty().getName())) {
                        try {
                            featureValueByName.put(ihp.getProperty().getName(), Float.valueOf(ihp.getValue()));
                        } catch (Exception e) {
                            throw new RuntimeException("All instance features have to be numeric (convertible to a Java Float).");
                        }
                    } else if (instanceSizeFeatureNames.contains(ihp.getProperty().getName())) {
                        try {
                            sizeFeatureValueByName.put(ihp.getProperty().getName(), Float.valueOf(ihp.getValue()));
                        } catch (Exception e) {
                            throw new RuntimeException("All instance features have to be numeric (convertible to a Java Float).");
                        }
                    }
                }
            }

            for (String featureName: instanceFeatureNames) {
                instanceFeatures[instances.indexOf(instance)][instanceFeatureNames.indexOf(featureName)] = featureValueByName.get(featureName);
            }
            for (String featureName: instanceSizeFeatureNames) {
                instanceSizeFeatures[instances.indexOf(instance)][instanceSizeFeatureNames.indexOf(featureName)] = sizeFeatureValueByName.get(featureName);
            }
        }
        
        boolean[] skipFeature = new boolean[instanceFeatureNames.size()];
        int numSkippedFeatures = 0;
        
        // scale features to [-1, 1]
        for (int j = 0; j < instanceFeatureNames.size(); j++) {
            double minValue = Double.POSITIVE_INFINITY;
            double maxValue = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < instances.size(); i++) {
                minValue = Math.min(minValue, instanceFeatures[i][j]);
                maxValue = Math.max(maxValue, instanceFeatures[i][j]);
            }
            
            if (maxValue == minValue) {
                skipFeature[j] = true;
                numSkippedFeatures++;
                continue;
            }
            
            for (int i = 0; i < instances.size(); i++) {
                instanceFeatures[i][j] = (instanceFeatures[i][j] - minValue) / (maxValue - minValue) * 2.0 - 1.0;
            }
        }
        
        final double[][] cleanedFeatures = new double[instances.size()][instanceFeatureNames.size() - numSkippedFeatures];
        for (int i = 0; i < instances.size(); i++) {
            int fix = 0;
            for (int j = 0; j < instanceFeatureNames.size(); j++) {
                if (skipFeature[j]) continue;
                cleanedFeatures[i][fix++] = instanceFeatures[i][j];
            }
        }
        
        
        ClusterSilhouette sc = new ClusterSilhouette(rengine, cleanedFeatures.length, cleanedFeatures[0].length, cleanedFeatures);
        int k = sc.findNumberOfClusters((int)Math.sqrt(instances.size()));
        this.k = k;
        int[] classigns = sc.clusterData(k);
        int[] count_by_cluster = new int[k];
        for (int i = 0; i < instances.size(); i++) {
            classigns[i] -= 1; // relabel clusters from 1 through k to 0 through k - 1
            count_by_cluster[classigns[i]]++;
        }
        int[][] S = new int[k][];
        for (int c = 0; c < k; c++) {
            S[c] = new int[count_by_cluster[c]];
        }
        
        count_by_cluster = new int[k];
        for (int i = 0; i < instances.size(); i++) {
            S[classigns[i]][count_by_cluster[classigns[i]]++] = i;
        }
        
        /*Object[] clustering = kMeans(cleanedFeatures, k, rng);
        double C[][] = (double[][])clustering[0];
        int S[][] = (int[][])clustering[1];*/
        
        if (instanceHardness != null) {
            // Sort instances within each cluster by instance hardness, easiest first
            for (int c = 0; c < k; c++) {
                final Integer[] sorted = new Integer[S[c].length];
                for (int i = 0; i < sorted.length; i++) sorted[i] = S[c][i];
                Arrays.sort(sorted, new Comparator<Integer>() {
                    @Override
                    public int compare(Integer o1, Integer o2) {
                        double i1 = instanceHardness.get(o1);
                        double i2 = instanceHardness.get(o2);
                        return Double.compare(i1, i2);
                        
                        /*for (int i = 0; i < i1.length; i++) {
                            if (i1[i] > i2[i]) return 1;
                            else if (i1[i] < i2[i]) return -1;
                            // otherwise check the next value to break ties
                        }
                        return 0;*/
                    }
                });
                for (int i = 0; i < sorted.length; i++) S[c][i] = sorted[i];
            }
        }
        
        // build course
        for (int e = 0; e < maxExpansionFactor; e++) {
            int curInstance[] = new int[k];
            int i = 0;
            while (i < instances.size()) {
                for (int c = 0; c < k; c++) {
                    if (curInstance[c] >= S[c].length) continue;
                    InstanceIdSeed isp = new InstanceIdSeed(instances.get(S[c][curInstance[c]]).getId(), rng.nextInt(Integer.MAX_VALUE));
                    course.add(isp);
                    curInstance[c]++;
                    i++;
                }
            }
        }
    }
    
    public InstanceIdSeed getEntry(int ix) {
        return course.get(ix);
    }
    
    public List<InstanceIdSeed> getCourse() {
        return course;
    }

    /**
     * k-means algorithm. Clusters the D-dimensional vectors given as rows in X around k centers.
     * Returns the cluster centers C and the cluster membership S as Object[] {C, S}.
     * @param X
     * @param k
     * @param rng
     * @return
     */
    public static Object[] kMeans(double[][] X, int k, Random rng) {
        final double eps = 1e-9;
        final int maxIter = 100;
        
        double[][] C = new double[k][];
        int[][] S = new int[k][];
        for (int i = 0; i < k; i++) {
            C[i] = Arrays.copyOf(X[rng.nextInt(X.length)], X[0].length); // initial cluster centers are random
        }
        
        int iter = 0;
        while (true && iter < maxIter) {
            int closestMean[] = new int[X.length];
            int countMembers[] = new int[k];
            for (int j = 0; j < X.length; j++) {
                double dist = Double.MAX_VALUE;
                for (int i = 0; i < k; i++) {
                    if (euclideanDistanceSquared(X[j], C[i]) < dist) {
                        dist = euclideanDistanceSquared(X[j], C[i]);
                        closestMean[j] = i;
                    }
                }
                countMembers[closestMean[j]]++;
            }
            
            for (int i = 0; i < k; i++) S[i] = new int[countMembers[i]];
            int numAssigned[] = new int[k];
            for (int j = 0; j < X.length; j++) {
                S[closestMean[j]][numAssigned[closestMean[j]]++] = j;
            }
            
            boolean anyChanges = false;
            for (int i = 0; i < k; i++) {
                double[] C_new = new double[C[i].length];
                for (int l = 0; l < countMembers[i]; l++) {
                    C_new = vectorSum(C_new, X[S[i][l]]);
                }
                
                for (int u = 0; u < C_new.length; u++) {
                    C_new[u] /= countMembers[i];
                }
                
                if (euclideanDistanceSquared(C[i], C_new) > eps*eps) {
                    anyChanges = true;
                }
                C[i] = C_new;
            }
            
            
            if (!anyChanges) break;
            iter++;
        }
        return new Object[] {C, S};
    }
    
    
    private static double[] vectorSum(double[] x, double[] y) {
        double[] res = new double[x.length];
        for (int i = 0; i < x.length; i++) res[i] = x[i] + y[i];
        return res;
    }
    
    private static double euclideanDistanceSquared(double[] x, double[] y) {
        double dist = 0;
        for (int i = 0; i < x.length; i++) dist += (x[i] - y[i]) * (x[i] - y[i]);
        return dist;
    }
    
    public int getK() {
        return k;
    }
}
