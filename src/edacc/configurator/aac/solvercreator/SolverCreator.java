package edacc.configurator.aac.solvercreator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import edacc.api.API;
import edacc.api.APIImpl;
import edacc.api.costfunctions.CostFunction;
import edacc.api.costfunctions.PARX;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.solvercreator.Clustering.HierarchicalClusterMethod;
import edacc.model.ExperimentResult;
import edacc.model.ExperimentResultDAO;
import edacc.model.Instance;
import edacc.model.InstanceDAO;
import edacc.model.InstanceHasProperty;
import edacc.model.SolverConfiguration;
import edacc.model.SolverConfigurationDAO;
import edacc.model.Experiment.Cost;
import edacc.util.Pair;

public class SolverCreator {
	public static void main(String[] args) throws Exception {
		API api = new APIImpl();
		
		if (args.length < 1) {
			System.out.println("Usage: SolverCreator.jar <settings.properties file>");
			return;
		}
		
		// load user specified properties
		Properties properties = new Properties();
		// general settings
		File settingsFile = new File(args[0]);
		if (settingsFile.exists()) {
			if (!settingsFile.exists()) {
				System.err.println("settings.properties not found.");
				return;
			}
			InputStream in = new FileInputStream(settingsFile);
			properties.load(in);
			in.close();
		} else {
			InputStream in = Clustering.class.getResourceAsStream("settings.properties");
			if (in != null) {
				// starting within eclipse
				properties.load(in);
				in.close();
				// private settings (not in repository)
				in = Clustering.class.getResourceAsStream("private.properties");
				if (in != null) {
					properties.load(in);
					in.close();
				}
			}

		}
		
		// feature specific settings
		final String featureDirectory = properties.getProperty("FeatureDirectory");
		InputStream in = new FileInputStream(new File(new File(featureDirectory), "features.properties"));
		if (in != null) {
			properties.load(in);
			in.close();
		}
		String featuresName = properties.getProperty("FeaturesName");
		String featuresCacheDirectory = properties.getProperty("FeatureCacheDirectory");
		final File featuresCacheFolder = new File(new File(featuresCacheDirectory), featuresName);
		
		boolean loadClustering = Boolean.parseBoolean(properties.getProperty("LoadSerializedClustering"));
		
		// extract feature names
		String[] feature_names = properties.getProperty("Features").split(",");
		
		int expid = Integer.parseInt(properties.getProperty("ExperimentId"));
		
		// connect to database
		api.connect(properties.getProperty("DBHost"), Integer.parseInt(properties.getProperty("DBPort")), properties.getProperty("DB"), properties.getProperty("DBUser"), properties.getProperty("DBPassword"), Boolean.parseBoolean(properties.getProperty("DBCompress")));
		
		float clustering_threshold = Float.parseFloat(properties.getProperty("ClusteringThreshold"));
		
		List<Object> data = new LinkedList<Object>();
		
		LinkedList<Instance> instances = null;
		Clustering C = null;
		if (!loadClustering) {
			// load instances and determine instance ids
			instances = InstanceDAO.getAllByExperimentId(expid);
			
			/*for (int i = instances.size()-1; i>= 0; i--) {
				if (!instances.get(i).getName().contains("k4")) {
					instances.remove(i);
				}
			}*/
			
			List<Integer> instanceIds = new LinkedList<Integer>();
			for (Instance i : instances) {
				instanceIds.add(i.getId());
			}

			final HashMap<Integer, float[]> featureMapping = new HashMap<Integer, float[]>();

			// calculate features
			if (Boolean.parseBoolean(properties.getProperty("UseFeaturesFromDB"))) {
				for (Integer id : instanceIds) {
					float[] f = new float[feature_names.length];
					HashMap<String, Float> tmp = new HashMap<String, Float>();
					Instance instance = InstanceDAO.getById(id);
					for (InstanceHasProperty ihp : instance.getPropertyValues().values()) {
						try {
							tmp.put(ihp.getProperty().getName(), Float.parseFloat(ihp.getValue()));
						} catch (Exception ex) {
						}
					}
					for (int i = 0; i < feature_names.length; i++) {
						Float val = tmp.get(feature_names[i]);
						if (val == null) {
							System.err.println("WARNING: Did not find feature value for name " + feature_names[i] + " and instance " + instance.getId());
							f[i] = 0.f;
						} else {
							f[i] = val;
						}
					}
					featureMapping.put(id, f);
				}
			} else {
				final int size = instanceIds.size();
				final LinkedList<Integer> instanceIdsToGo = new LinkedList<Integer>();
				instanceIdsToGo.addAll(instanceIds);
				int cores = Runtime.getRuntime().availableProcessors();
				Thread[] threads = new Thread[cores];
				System.out.println("Starting " + cores + " threads for property calculation");
				
			/*	for (int i = 0; i < cores; i++) {
					threads[i] = new Thread(new Runnable() {
						
						@Override
						public void run() {
							while (true) {
								int id;
								synchronized (instanceIdsToGo) {
									if (instanceIdsToGo.isEmpty()) {
										break;
									}
									id = instanceIdsToGo.poll();
								}
								try {
									float[] features = AAC.calculateFeatures(id, new File(featureDirectory), featuresCacheFolder);
									synchronized (instanceIdsToGo) {
										featureMapping.put(id, features);
										System.out.println("Calculated feature vector " + featureMapping.size() + " / " + size);
									}
								} catch (Exception ex) {
									ex.printStackTrace();
									System.err.println("Error while calculating features for instance " + id);
								}
							}
						}
						
					});
				}
				
				for (Thread thread : threads) {
					thread.start();
				}
				
				for (Thread thread : threads) {
					thread.join();
				}*/
				System.out.println("Done.");
				
				// remove me
				
			/*	float[] normalize = new float[featureMapping.entrySet().iterator().next().getValue().length];
				for (int i = 0; i < normalize.length; i++) {
					normalize[i] = 0.f;
				}
				
				for (int iid : instanceIds) {
					float[] f = featureMapping.get(iid);
					for (int i = 0; i < normalize.length; i++) {
						if (normalize[i] < f[i]) {
							normalize[i] = f[i];
						}
					}
				}
				
				for (int id : instanceIds) {
					List<Integer> ids = new LinkedList<Integer>();
					ids.add(id);
					float[] f1 = featureMapping.get(id);
					f1 = Arrays.copyOf(f1, f1.length);
					for (int i = 0; i < f1.length; i++) {
						if (normalize[i] > 1.f || normalize[i] < -1.f)
							f1[i] /= normalize[i];
					}
					for (int id2: instanceIds) {
						if (id != id2) {
							float[] f2 = featureMapping.get(id2);
							f2 = Arrays.copyOf(f2, f2.length);
							for (int i = 0; i < f2.length; i++) {
								if (normalize[i] > 1.f || normalize[i] < -1.f)
									f2[i] /= normalize[i];
							}
							
							float dist = 0.f;
							for (int i = 0; i < f1.length; i++) {
								dist += Math.abs(f1[i] - f2[i]);
							}
							
							if (dist < 0.05f) {
								ids.add(id2);
							}
						}
					}
					
					if (ids.size() >= 10) {
						System.out.println("FOUND!");
						for (int iid : ids) {
							System.out.print(InstanceDAO.getById(iid).getName() + "|");
						}
						//System.out.println(ids.toString());
					}
					
				}
				return;*/
				// end of remove me
			}

			// create clustering
			C = new Clustering(instanceIds, featureMapping);
			CostFunction f = new PARX(Cost.cost, true, 0, 1);
			
			// load experiment results
			HashMap<Pair<Integer, Integer>, List<ExperimentResult>> results = new HashMap<Pair<Integer, Integer>, List<ExperimentResult>>();
			for (ExperimentResult er : ExperimentResultDAO.getAllByExperimentId(expid)) {
				if (!instanceIds.contains(er.getInstanceId()))
					continue;
				
				List<ExperimentResult> tmp = results.get(new Pair<Integer, Integer>(er.getSolverConfigId(), er.getInstanceId()));
				if (tmp == null) {
					tmp = new LinkedList<ExperimentResult>();
					results.put(new Pair<Integer, Integer>(er.getSolverConfigId(), er.getInstanceId()), tmp);
				}
				tmp.add(er);
			}
			// update clustering
			for (Pair<Integer, Integer> p : results.keySet()) {
				List<ExperimentResult> r = results.get(p);
				if (!r.isEmpty()) {
					boolean inf = true;
					for (ExperimentResult res : r) {
						if (res.getResultCode().isCorrect()) {
							inf = false;
							break;
						}
					}
					double c = inf ? Double.POSITIVE_INFINITY : f.calculateCost(r);
					C.update(p.getFirst(), p.getSecond(), c);
				}
			}
		} else {
			//C = Clustering.deserialize(properties.getProperty("SerializeFilename"));
		}
		
		Clustering C_orig = new Clustering(C);
		
		
		if (Boolean.parseBoolean(properties.getProperty("RemoveNotBest"))) {
			for (int scid : C.getSolverConfigIds()) {
				if (!SolverConfigurationDAO.getSolverConfigurationById(scid).getName().contains("BEST")) {
					C.remove(scid);
				}
			}
		}
		
		if (Boolean.parseBoolean(properties.getProperty("RemoveRemoved"))) {
			for (int scid : C.getSolverConfigIds()) {
				if (SolverConfigurationDAO.getSolverConfigurationById(scid).getName().contains("(removed)")) {
					C.remove(scid);
				}
			}
		}
		
		List<Pair<Integer, Double>> scidWeight = new LinkedList<Pair<Integer, Double>>();
		
		for (int scid : C.getSolverConfigIds()) {
			scidWeight.add(new Pair<Integer, Double>(scid, C.getWeight(scid)));
		}
		
		Collections.sort(scidWeight, new Comparator<Pair<Integer, Double>>() {

			@Override
			public int compare(Pair<Integer, Double> arg0, Pair<Integer, Double> arg1) {
				if (arg0.getSecond() - 0.000001f < arg1.getSecond() && arg0.getSecond() + 0.000001f > arg1.getSecond()) {
					return 0;
				} else if (arg0.getSecond() > arg1.getSecond()) {
					return 1;
				} else if (arg1.getSecond() > arg0.getSecond()) {
					return -1;
				}
				return 0;
			}
			
		});
		
		int numConfigs =  Integer.parseInt(properties.getProperty("NumConfigs"));
		if (numConfigs != -1) {
			while (scidWeight.size() > numConfigs) {
				Pair<Integer, Double> p = scidWeight.get(0);
				System.out.println("Removing " + p.getFirst() + " with weight " + p.getSecond());
				C.remove(p.getFirst());
				scidWeight.remove(0);
			}
		}
		
		data.add(C);
		
		if (Boolean.parseBoolean(properties.getProperty("ShowClustering"))) {
			System.out.println("Generating clustering for single decision tree using default method..");
			HashMap<Integer, List<Integer>> c = C.getClustering(false, clustering_threshold);
			System.out.println("Clustering size: " + c.size());
			List<Pair<String, List<Integer>>> clustering = new LinkedList<Pair<String, List<Integer>>>();
			for (Entry<Integer, List<Integer>> entry : c.entrySet()) {
				clustering.add(new Pair<String, List<Integer>>(SolverConfigurationDAO.getSolverConfigurationById(entry.getKey()).getName(), entry.getValue()));

			}
			Collections.sort(clustering, new Comparator<Pair<String, List<Integer>>>() {

				@Override
				public int compare(Pair<String, List<Integer>> arg0, Pair<String, List<Integer>> arg1) {
					return arg0.getFirst().compareTo(arg1.getFirst());
				}
				
			});
			
			for (Pair<String, List<Integer>> p : clustering) {
				System.out.print(p.getFirst() + ": ");
				Collections.sort(p.getSecond());
				System.out.println(p.getSecond());
			}
			
			for (Integer i : c.keySet()) {
				System.out.print("(.*ID: " + i + ".*)|");
			}
			System.out.println();
		}
		
		List<Integer> scids = C.getSolverConfigIds();
		
		if (Boolean.parseBoolean(properties.getProperty("ShowWeightedRanking"))) {
			// weighted ranking
			List<Pair<Integer, Double>> scweights = new LinkedList<Pair<Integer, Double>>();
			for (int scid : scids) {
				scweights.add(new Pair<Integer, Double>(scid, C.getWeight(scid)));
			}
			Collections.sort(scweights, new Comparator<Pair<Integer, Double>>() {

				@Override
				public int compare(Pair<Integer, Double> arg0, Pair<Integer, Double> arg1) {
					if (arg0.getSecond() < arg1.getSecond()) {
						return 1;
					} else if (arg1.getSecond() < arg0.getSecond()) {
						return -1;
					} else {
						return 0;
					}
				}

			});
			int count = 1;
			for (Pair<Integer, Double> p : scweights) {
				SolverConfiguration sc = SolverConfigurationDAO.getSolverConfigurationById(p.getFirst());
				System.out.println((count++) + ") " + sc.getName() + "   W: " + p.getSecond());
			}
		}		
		
		//System.out.println("Performance(C) = " + C.performance(c));
		/*BufferedWriter writer = new BufferedWriter(new FileWriter(new File("D:\\dot\\test.dat")));
		for (Entry<Integer, List<Integer>> e : C.getClustering(false).entrySet()) {
			for (int i : e.getValue()) {
				for (float f : C.F.get(i)) {
					writer.write(f + ", ");
				}
				writer.write(e.getKey() + "\n");
			}
		}
		// data <- read.csv(file="D:\\dot\\test.dat", head =FALSE, sep=",")
		// randomForest(data[1:54], factor(data[,55]), ntree=500)
		writer.close();
		if (true)
			return;*/

		
		if (Boolean.parseBoolean(properties.getProperty("BuildDecisionTree"))) {
			System.out.println("Calculating clustering..");

			HashMap<Integer, List<Integer>> c = C.getClustering(false, clustering_threshold);//, 0.9f);
			System.out.println("Building decision tree..");
			DecisionTree tree = new DecisionTree(c, DecisionTree.ImpurityMeasure.valueOf(properties.getProperty("DecisionTree_ImpurityMeasure")), C_orig, C, -1, new Random(), 0.2f);
			System.out.println("Performance(C) = " + C_orig.performance(c));
			System.out.println("Performance(T) = " + tree.performance);
			data.add(tree);
			
		}
		
		
		if (Boolean.parseBoolean(properties.getProperty("CalculateCost"))) {			
			/*HashMap<Integer, List<Integer>> c = C.getClustering(false, clustering_threshold);
			System.out.println("Original - Threshold 1.0:");
			showCost(C_orig.getClustering(false), C_orig);
			System.out.println("Original - Threshold " + clustering_threshold + ":");
			showCost(C_orig.getClustering(false, clustering_threshold), C_orig);
			System.out.println("Modified - Threshold 1.0:");
			showCost(C.getClustering(false), C);
			System.out.println("Modified - Threshold " + clustering_threshold + ":");
			showCost(C.getClustering(false, clustering_threshold), C);*/
		/*	System.out.println("Hierarchical (AVG_LINKAGE):");
			showCost(C.getClusteringHierarchical(HierarchicalClusterMethod.AVERAGE_LINKAGE, 10), C);			
			System.out.println("Hierarchical (COMPLETE_LINKAGE):");
			showCost(C.getClusteringHierarchical(HierarchicalClusterMethod.COMPLETE_LINKAGE, 10), C);
			System.out.println("Hierarchical (SINGLE_LINKAGE):");
			showCost(C.getClusteringHierarchical(HierarchicalClusterMethod.SINGLE_LINKAGE, 10), C);*/
			
			Set<Integer> instanceIds = new HashSet<Integer>();
			HashMap<Integer, Set<Integer>> blablub = new HashMap<Integer, Set<Integer>>();
			for (int scid : C.getSolverConfigIds()) {
				List<Integer> theInstances = C.getInstancesForSC(scid, 100000000.f);
				instanceIds.addAll(theInstances);
				if (!theInstances.isEmpty()) {
					Set<Integer> tmp = new HashSet<Integer>();
					tmp.addAll(theInstances);
					blablub.put(scid, tmp);
				}
			}
			List<Integer> instanceIdList = new LinkedList<Integer>();
			instanceIdList.addAll(instanceIds);
			Collections.sort(instanceIdList);
			for (int i = 0; i < instanceIdList.size(); i++) {
				System.out.print(instanceIdList.get(i));
				if (i != instanceIdList.size()-1) {
					System.out.print(" ");
				}
			}
			System.out.println();
			for (Entry<Integer, Set<Integer>> e : blablub.entrySet()) {
				System.out.print(e.getKey() + " ");
				List<Integer> list = new LinkedList<Integer>();
				list.addAll(e.getValue());
				Collections.sort(list);
				for (int i = 0; i < list.size(); i++) {
					System.out.print(list.get(i));
					if (i != list.size()-1){
						System.out.print(" ");
					}
				}
				System.out.println();
			}
			
		BufferedReader br = new BufferedReader(new FileReader(new File("../test/exp_25.msc")));
			String line;
			HashMap<Integer, List<Integer>> minclustering = null;
			float minmin = Float.POSITIVE_INFINITY;
			while ((line = br.readLine()) != null) {
				String[] scidsStr = line.split(" ");
				if (scidsStr.length == 0) {
					continue;
				}
				HashMap<Integer, List<Integer>> clustering = new HashMap<Integer, List<Integer>>();
				for (String scid : scidsStr) {
					int scidInt = Integer.valueOf(scid);
					clustering.put(scidInt, new LinkedList<Integer>());
				}
				for (int iid : C.I.keySet()) {
					double mincost = Float.POSITIVE_INFINITY;
					int thescid = -1;
					for (int scid: clustering.keySet()) {
						if (!Double.isInfinite(C.getCost(scid, iid)) && C.getCost(scid, iid) < mincost) {
							thescid = scid;
							mincost = C.getCost(scid, iid);
						}
					}
					if (thescid != -1)
						clustering.get(thescid).add(iid);
				}
				System.out.println(clustering.keySet() + ": ");
				float tmp = showCost(clustering, C);
				if (tmp < minmin) {
					minmin = tmp;
					minclustering = clustering;
				}
			}
			System.out.println(minmin);
			System.out.println(minclustering.keySet());
			showCost(minclustering, C);
			for (Entry<Integer, List<Integer>> e : minclustering.entrySet()) {
				System.out.println(e.getKey() + ": " + e.getValue());
			}
			
			for (int scid : C.getSolverConfigIds()) {
				if (!minclustering.containsKey(scid)) {
					C.remove(scid);
				}
			}
		/*	int csize = 0;
			HashMap<Integer, List<Integer>> clustering = new HashMap<Integer, List<Integer>>();
			while (!instanceIds.isEmpty()) {
				int scid = -1;
				int count = -1;
				for (Entry<Integer, Set<Integer>> e : blablub.entrySet()) {
					if (e.getValue().size() > count) {
						count = e.getValue().size();
						scid = e.getKey();
					}
				}
				List<Integer> tmp = new LinkedList<Integer>();
				tmp.addAll(blablub.get(scid));
				csize ++;
				
				for (Set<Integer> s : blablub.values()) {
					s.removeAll(tmp);
					
				}
				instanceIds.removeAll(tmp);
				clustering.put(scid, tmp);
			}
			showCost(clustering, C);*/
			/*Clustering C_tmp = new Clustering(C);
			for (int iid : C_tmp.I.keySet()) {
				Set<Integer> best = new HashSet<Integer>();
				best.addAll(C_tmp.getBestSCsOnInstance(iid, 2.f));
				for (int scid : C_tmp.getSolverConfigIds()) {
					if (!best.contains(scid)) {
						C_tmp.update(scid, iid, Float.POSITIVE_INFINITY);
					}
				}
			}
			
			System.out.println("5 best - Threshold 1.0:");
			showCost(C_tmp.getClustering(false), C_tmp);
			System.out.println("5 best - Threshold " + clustering_threshold + ":");
			showCost(C_tmp.getClustering(false, clustering_threshold), C_tmp);*/
			
		}
		
		if (Boolean.parseBoolean(properties.getProperty("BuildRandomForest"))) {
			System.out.println("Generating random forest..");
			RandomForest forest = new RandomForest(C_orig, C, new Random(), Integer.parseInt(properties.getProperty("RandomForestTreeCount")), 384);
			data.add(forest);
			System.out.println("done.");
		}
		
		/*if (Boolean.parseBoolean(properties.getProperty("BuildRegressionTree"))) {
			
			List<Pair<Integer, Float>> scidWeight = new LinkedList<Pair<Integer, Float>>();
			
			for (int scid : C.getSolverConfigIds()) {
				scidWeight.add(new Pair<Integer, Float>(scid, C.getWeight(scid)));
			}
			
			Collections.sort(scidWeight, new Comparator<Pair<Integer, Float>>() {

				@Override
				public int compare(Pair<Integer, Float> arg0, Pair<Integer, Float> arg1) {
					if (arg0.getSecond() - 0.000001f < arg1.getSecond() && arg0.getSecond() + 0.000001f > arg1.getSecond()) {
						return 0;
					} else if (arg0.getSecond() > arg1.getSecond()) {
						return 1;
					} else if (arg1.getSecond() > arg0.getSecond()) {
						return -1;
					}
					return 0;
				}
				
			});
			
			Clustering C_orig = new Clustering(C);
			
			int numConfigs =  Integer.parseInt(properties.getProperty("DecisionTree_numConfigs"));
			if (numConfigs != -1) {
				while (scidWeight.size() > numConfigs) {
					Pair<Integer, Float> p = scidWeight.get(0);
					System.out.println("Removing " + p.getFirst() + " with weight " + p.getSecond());
					C.remove(p.getFirst());
					scidWeight.remove(0);
				}
			}
			
			C.updateData();
		
			
			List<Pair<ParameterConfiguration, List<Pair<Integer, Float>>>> trainData = new LinkedList<Pair<ParameterConfiguration, List<Pair<Integer, Float>>>>();
			List<Pair<ParameterConfiguration, Integer>> p_scids = new LinkedList<Pair<ParameterConfiguration, Integer>>();
			for (Entry<Integer, float[]> e : C.M.entrySet()) {
				ParameterConfiguration c = api.getParameterConfiguration(expid, e.getKey());
				p_scids.add(new Pair<ParameterConfiguration, Integer>(c, e.getKey()));
				List<Pair<Integer, Float>> list = new LinkedList<Pair<Integer, Float>>();
				for (Entry<Integer, Integer> ee : C.I.entrySet()) {
					list.add(new Pair<Integer, Float>(ee.getKey(), e.getValue()[ee.getValue()]));
				}
				trainData.add(new Pair<ParameterConfiguration, List<Pair<Integer, Float>>>(c, list));
			}
			
			
			C.tree2 = new edacc.configurator.aac.racing.challenge.test.DecisionTree(0.00001f, 20, p_scids, trainData, api.getConfigurableParameters(expid), C.F, C.F.values().iterator().next().length, false, C, C_orig);
		}*/
		
		System.out.println("# instances not used: " + C.getNotUsedInstances().size());
		if (Boolean.parseBoolean(properties.getProperty("Serialize"))) {
			serialize(properties.getProperty("SerializeFilename"), data);
		}
		
		if (Boolean.parseBoolean(properties.getProperty("CreateSolver"))) {
			System.out.println("Exporting solver..");
			String folder = properties.getProperty("SolverDirectory");
			File solverFolder = new File(folder);
			if (solverFolder.mkdirs()) {
				List<edacc.model.SolverBinaries> binaries = new LinkedList<edacc.model.SolverBinaries>();
				HashSet<Integer> sbIds = new HashSet<Integer>();
				sbIds.addAll(C.scToSb.values());
				for (int sbid : sbIds) {
					binaries.add(edacc.model.SolverBinariesDAO.getById(sbid));
				}
				
				for (edacc.model.SolverBinaries binary : binaries) {
					System.out.println("Exporting solver binary " + binary.getBinaryName());
					
					File binaryFolder = new File(solverFolder, "binary_"+ binary.getId());
					binaryFolder.mkdir();
					
					InputStream binStream = edacc.model.SolverBinariesDAO.getZippedBinaryFile(binary);
					ZipInputStream zis = new ZipInputStream(binStream);
					ZipEntry entry;
					while ((entry = zis.getNextEntry()) != null) {
						File file = new File(binaryFolder, entry.getName());
						if (entry.isDirectory()) {
							file.mkdirs();
						} else {
							file.getParentFile().mkdirs();
							byte[] b = new byte[2048];
							int n;
							
							OutputStream os = new FileOutputStream(file);
							while ((n = zis.read(b)) > 0) {
								os.write(b, 0, n);
							}
							os.close();
							zis.closeEntry();
						}
					}
					zis.close();
					binStream.close();
				}
	            System.out.println("Exporting feature binary..");
	            File featureFolder = new File(featureDirectory);
	            File featureDestFolder = new File(solverFolder, "features");
	            copyFiles(featureFolder, featureDestFolder);
	            System.out.println("Exporting solver launcher..");
	            File solverLauncherFolder = new File(properties.getProperty("SolverLauncherDirectory"));
	            copyFiles(solverLauncherFolder, solverFolder);
	            System.out.println("Saving clustering..");
	            C.updateData();
	            serialize(new File(solverFolder, "data").getAbsolutePath(), data);
	            System.out.println("Creating solverlauncher.properties file..");
	            FileWriter fw = new FileWriter(new File(solverFolder, "solverlauncher.properties").getAbsoluteFile());
	            BufferedWriter bw = new BufferedWriter(fw);
	            bw.write("FeaturesBin = " + properties.getProperty("FeaturesRunCommand") + "\n");
	            bw.write("FeaturesParameters = " + properties.getProperty("FeaturesParameters") + "\n");
	            for (edacc.model.SolverBinaries binary : binaries) {
	            	bw.write("SolverBin_" + binary.getId() + " = ./" + binary.getRunPath() + "\n");
	            }
	            bw.write("Data = ./data\n");
	            bw.close();
	            fw.close();
	            
	            System.out.println("Creating start script..");
	            fw = new FileWriter(new File(solverFolder, "start.sh").getAbsoluteFile());
	            bw = new BufferedWriter(fw);
	            bw.write("#!/bin/bash\njava -Xmx1024M -jar SolverLauncher.jar $1 $2 $3\n");
	            bw.close();
	            fw.close();
			} else {
				System.err.println("Could not create directory: " + folder);
			}
		}		
		
		if (Boolean.parseBoolean(properties.getProperty("Interactive"))) {
			interactiveMode(C, new File(featureDirectory));
		}
		
	}
	
	private static float showCost(HashMap<Integer, List<Integer>> clustering, Clustering C) {
		int instanceCount = 0;
		float res = 0.f;
		for (Entry<Integer, List<Integer>> e : clustering.entrySet()) {
			for (int iid : e.getValue()) {
				double cost = C.getCost(e.getKey(), iid);
				if (Double.isInfinite(cost)) {
					// impossible..
				} else {
					res += cost;
					instanceCount++;
				}
			}
		}
		
		System.out.println("Clustering size: " + clustering.size());
		System.out.println("Cost: " + res);
		System.out.println("Instances: " + instanceCount);
		System.out.println("Avg: " + (res / instanceCount));
		System.out.println();
		return (res / instanceCount);
	}
	
	
	public static void interactiveMode(Clustering C, File featuresFolder) throws Exception {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String line;
		while ((line = br.readLine()) != null) {
			if ("q".equals(line)) {
				System.out.println("Query Mode");
				System.out.println("Method?");
				String method = br.readLine();
				System.out.println("id?");
				String id = br.readLine();
				if (method == null || id == null)
					break;
				
				//float[] features = AAC.calculateFeatures(Integer.valueOf(id), featuresFolder, null);
				if ("randomforest".equals(method)) {
					//String params = C.P.get(C.forest.getSolverConfig(features));
					//System.out.println("Params: " + params);
				} else if ("tree".equals(method)) {
					//String params = C.P.get(C.tree.query(features).getFirst());
					//System.out.println("Params: " + params);
				}
			}
		}
		br.close();
	}
	
	
	/**
	 * This function will copy files or directories from one location to
	 * another. note that the source and the destination must be mutually
	 * exclusive. This function can not be used to copy a directory to a sub
	 * directory of itself. The function will also have problems if the
	 * destination files already exist.
	 * 
	 * @param src
	 *            -- A File object that represents the source for the copy
	 * @param dest
	 *            -- A File object that represnts the destination for the copy.
	 * @throws IOException
	 *             if unable to copy.
	 */
	public static void copyFiles(File src, File dest) throws IOException {
		// Check to ensure that the source is valid...
		if (!src.exists()) {
			throw new IOException("copyFiles: Can not find source: " + src.getAbsolutePath() + ".");
		} else if (!src.canRead()) { // check to ensure we have rights to the
										// source...
			throw new IOException("copyFiles: No right to source: " + src.getAbsolutePath() + ".");
		}
		// is this a directory copy?
		if (src.isDirectory()) {
			if (!dest.exists()) { // does the destination already exist?
				// if not we need to make it exist if possible (note this is
				// mkdirs not mkdir)
				if (!dest.mkdirs()) {
					throw new IOException("copyFiles: Could not create direcotry: " + dest.getAbsolutePath() + ".");
				}
			}
			// get a listing of files...
			String list[] = src.list();
			// copy all the files in the list.
			for (int i = 0; i < list.length; i++) {
				File dest1 = new File(dest, list[i]);
				File src1 = new File(src, list[i]);
				copyFiles(src1, dest1);
			}
		} else {
			// This was not a directory, so lets just copy the file
			FileInputStream fin = null;
			FileOutputStream fout = null;
			byte[] buffer = new byte[4096]; // Buffer 4K at a time (you can
											// change this).
			int bytesRead;
			try {
				// open the files for input and output
				fin = new FileInputStream(src);
				fout = new FileOutputStream(dest);
				// while bytesRead indicates a successful read, lets write...
				while ((bytesRead = fin.read(buffer)) >= 0) {
					fout.write(buffer, 0, bytesRead);
				}
			} catch (IOException e) { // Error copying file...
				IOException wrapper = new IOException("copyFiles: Unable to copy file: " + src.getAbsolutePath() + "to" + dest.getAbsolutePath() + ".");
				wrapper.initCause(e);
				wrapper.setStackTrace(e.getStackTrace());
				throw wrapper;
			} finally { // Ensure that the files are closed (if they were open).
				if (fin != null) {
					fin.close();
				}
				if (fout != null) {
					fout.close();
				}
			}
		}
	}
	
	public static void serialize(String file, List<Object> data) throws FileNotFoundException, IOException {
		ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(new File(file)));
		os.writeUnshared(data);
		os.close();
	}
	
	@SuppressWarnings("unchecked")
	public static List<Object> deserialize(String file) throws FileNotFoundException, IOException, ClassNotFoundException {
		ObjectInputStream is = new ObjectInputStream(new FileInputStream(new File(file)));
		List<Object> data = (List<Object>) is.readUnshared();
		is.close();
		return data;
	}
}
