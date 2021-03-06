package edacc.configurator.aac.solvercreator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import java.io.SequenceInputStream;
import java.util.Arrays;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import edacc.api.API;
import edacc.api.APIImpl;
import edacc.api.costfunctions.CostFunction;
import edacc.api.costfunctions.Median;
import edacc.api.costfunctions.PARX;
import edacc.configurator.aac.AAC;
import edacc.configurator.aac.solvercreator.Clustering.HierarchicalClusterMethod;
import edacc.manageDB.FileInputStreamList;
import edacc.manageDB.Util;
import edacc.model.Experiment;
import edacc.model.ExperimentDAO;
import edacc.model.ExperimentResult;
import edacc.model.ExperimentResultDAO;
import edacc.model.Instance;
import edacc.model.InstanceDAO;
import edacc.model.InstanceHasProperty;
import edacc.model.Parameter;
import edacc.model.ParameterDAO;
import edacc.model.ParameterInstance;
import edacc.model.ParameterInstanceDAO;
import edacc.model.Solver;
import edacc.model.SolverBinaries;
import edacc.model.SolverBinariesDAO;
import edacc.model.SolverConfiguration;
import edacc.model.SolverConfigurationDAO;
import edacc.model.SolverDAO;
import edacc.model.Experiment.Cost;
import edacc.util.Pair;

public class SolverCreator {
	public static void main(String[] args) throws Exception {
		API api = new APIImpl();
		
		if (args.length < 1) {
			System.out.println("Usage: SolverCreator.jar <settings.properties file> [[[key=value] key=value] ...]");
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
		for (int i = 1; i < args.length; i++) {
			String[] keyvalue = args[i].split("=");
			if (keyvalue.length != 2) {
				System.out.println("Usage: SolverCreator.jar <settings.properties file> [[[key=value] key=value] ...]");
				return;
			}
			properties.setProperty(keyvalue[0], keyvalue[1]);
		}
		
		System.out.println("Config:");
		System.out.println(properties.toString());
		
		Integer evalExpId = Integer.parseInt(properties.getProperty("EvaluationExperimentId"));
		
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
		
		String[] expid_str = properties.getProperty("ExperimentId").split(",");
		
		int[] expids = new int[expid_str.length];
		for (int i = 0; i < expids.length; i++)
			expids[i] = Integer.parseInt(expid_str[i]);
		
		// connect to database
		api.connect(properties.getProperty("DBHost"), Integer.parseInt(properties.getProperty("DBPort")), properties.getProperty("DB"), properties.getProperty("DBUser"), properties.getProperty("DBPassword"), Boolean.parseBoolean(properties.getProperty("DBCompress")));
		
		float clustering_threshold = Float.parseFloat(properties.getProperty("ClusteringThreshold"));
		
		List<Object> data = new LinkedList<Object>();
		
		LinkedList<Instance> instances = null;
		Clustering C = null;
		if (!loadClustering) {
			// load instances and determine instance ids
			HashSet<Integer> instanceIdsSet = new HashSet<Integer>();
			instances = new LinkedList<Instance>();
			for (int expid : expids) {
				for (Instance i : InstanceDAO.getAllByExperimentId(expid)) {
					if (!instanceIdsSet.contains(i.getId())) {
						instances.add(i);
						instanceIdsSet.add(i.getId());
					}
				}
			}
			
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
				
				for (int i = 0; i < cores; i++) {
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
				}
				System.out.println("Done.");
			}

			// create clustering
			C = new Clustering(instanceIds, featureMapping);
			CostFunction f = null;
	        if (ExperimentDAO.getById(expids[0]).getDefaultCost().equals(Cost.resultTime)) {
	            f = new PARX(Experiment.Cost.resultTime, true, 1.0f);
	        } else if (ExperimentDAO.getById(expids[0]).getDefaultCost().equals(Cost.wallTime)) {
	            f = new PARX(Experiment.Cost.wallTime, true, 1.0f);
	        } else {
	            f = new PARX(Experiment.Cost.cost, true, 1.0f);
	        }
			
			// load experiment results
			HashMap<Pair<Integer, Integer>, List<ExperimentResult>> results = new HashMap<Pair<Integer, Integer>, List<ExperimentResult>>();
			
			for (int expid : expids) {
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
			}
			HashSet<Integer> scIdsToUse = new HashSet<Integer>();
			if (properties.getProperty("scids") != null) {
				String[] scids = properties.getProperty("scids").split(",");
				for (String scid : scids) {
					scIdsToUse.add(Integer.parseInt(scid));
				}
			}
			
			// update clustering
			for (Pair<Integer, Integer> p : results.keySet()) {
				if (!scIdsToUse.isEmpty() && !scIdsToUse.contains(p.getFirst())) {
					continue;
				}
				
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
					if (!inf && c > 1e8) {
						c = Double.NaN;
					}
					C.update(p.getFirst(), p.getSecond(), c);
				}
			}
		} else {
			//C = Clustering.deserialize(properties.getProperty("SerializeFilename"));
		}
		
		Clustering C_orig = new Clustering(C);
		
		
		if (Boolean.parseBoolean(properties.getProperty("RemoveNotBest"))) {
			List<Integer> scids = new LinkedList<Integer>();
			scids.addAll(C.getSolverConfigIds());
			for (int scid : scids) {
				if (!SolverConfigurationDAO.getSolverConfigurationById(scid).getName().contains("BEST")) {
					C.remove(scid);
				}
			}
		}
		
		if (Boolean.parseBoolean(properties.getProperty("RemoveRemoved"))) {
			List<Integer> scids = new LinkedList<Integer>();
			scids.addAll(C.getSolverConfigIds());
			for (int scid : scids) {
				if (SolverConfigurationDAO.getSolverConfigurationById(scid).getName().contains("(removed)")) {
					C.remove(scid);
				}
			}
		}
		
		String val;
		if ((val = properties.getProperty("MinRuns")) != null) {
			int minRuns = Integer.parseInt(val);
			List<Integer> scids = new LinkedList<Integer>();
			scids.addAll(C.getSolverConfigIds());
			for (int scid : scids) {
				if (ExperimentResultDAO.getAllBySolverConfiguration(SolverConfigurationDAO.getSolverConfigurationById(scid)).size() < minRuns) {
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
			HashMap<Integer, List<Integer>> c = C.getClustering(false, false, clustering_threshold);
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
		
		System.out.println("Generating clustering from " + C.getSolverConfigIds().size() + " possible solver configurations..");
		String clustering_filename = properties.getProperty("LoadClusteringLogFilename");
		HashMap<Integer, List<Integer>> the_clustering = null;
		if (clustering_filename == null || "".equals(clustering_filename)) {
			the_clustering = C.getClustering(false, false, clustering_threshold);
			//C.getClustering(false, false, clustering_threshold); //C.getClusteringTest(Integer.parseInt(properties.getProperty("k"))); //C.getClustering(true, false, clustering_threshold); //C.getClusteringHierarchical(HierarchicalClusterMethod.AVERAGE_LINKAGE, 15);
		} else {
			BufferedReader br = new BufferedReader(new FileReader(new File(clustering_filename)));
			String line;
			Pattern pattern = Pattern.compile("([0-9]+),\\[([0-9]+)(, ([0-9]+))*\\]");
			the_clustering = new HashMap<Integer, List<Integer>>();
			while ((line = br.readLine()) != null) {
				Matcher m = pattern.matcher(line);
				if (m.matches()) {
					System.out.println("MATCH! " + m.groupCount());
					int scid = Integer.parseInt(m.group(1));
					line = line.substring(line.indexOf("[") +1);
					//System.out.println("LINE = " + line);
					List<Integer> iids = new LinkedList<Integer>();
					while (true) {
						if (line.contains(",")) {
						
							int iid = Integer.parseInt(line.substring(0, line.indexOf(",")));
							line = line.substring(line.indexOf(",")+2);
						//	System.out.println("LINE = " + line);
							iids.add(iid);
						} else {
							int iid = Integer.parseInt(line.substring(0, line.indexOf("]")));
							iids.add(iid);
							break;
						}
					}
					the_clustering.put(scid, iids);
					System.out.println("Found: " + scid + "," + iids.toString());
				}
			}
			br.close();
		}
				
		for (Entry<Integer, List<Integer>> e : the_clustering.entrySet()) {
			C.clusterPerformance.put(e.getKey(), C.getCost(e.getKey(), e.getValue()));
			C.clusterSize.put(e.getKey(), e.getValue().size());
		}
		
		System.out.println("Generated.");
		System.out.println("Performance(C) = " + C_orig.performance(the_clustering));
		System.out.println("Clusters: " + the_clustering.size());
		
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
		
		if (Boolean.parseBoolean(properties.getProperty("BuildDecisionTree"))) {
			System.out.println("Calculating clustering..");

			System.out.println("Building decision tree..");
			DecisionTree tree = new DecisionTree(the_clustering, DecisionTree.ImpurityMeasure.valueOf(properties.getProperty("DecisionTree_ImpurityMeasure")), C_orig, -1, new Random(), 0.2f);
			System.out.println("Performance(T) = " + tree.performance);
			data.add(tree);
			
		}
		
		
		if (Boolean.parseBoolean(properties.getProperty("CalculateCost"))) {						
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
			
			/*BufferedReader br = new BufferedReader(new FileReader(new File("../test/exp_25.msc")));
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
						if (!(Double.isInfinite(C.getCost(scid, iid)) || Double.isNaN(C.getCost(scid, iid))) && C.getCost(scid, iid) < mincost) {
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
			}*/
			
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
			RandomForest forest = new RandomForest(C_orig, the_clustering, new Random(), Integer.parseInt(properties.getProperty("RandomForestTreeCount")), 200, clustering_threshold);
			data.add(forest);
			System.out.println("done.");
		}
		
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
	            	bw.write("SolverBin_" + binary.getId() + " = " + ((binary.getRunCommand() == "" || binary.getRunCommand() == null) ? "" : binary.getRunCommand() + " ") + "./" + binary.getRunPath() + "\n");
	            }
	            bw.write("Data = ./data\n");
	            bw.close();
	            fw.close();
	            
	            System.out.println("Creating start script..");
	            fw = new FileWriter(new File(solverFolder, "start.sh").getAbsoluteFile());
	            bw = new BufferedWriter(fw);
	            bw.write("#!/bin/bash\njava -Xmx1024M -jar SolverLauncher.jar $@\n");
	            bw.close();
	            fw.close();
	            String solverName = properties.getProperty("SolverName");
	            if (solverName != null && !"".equals(solverName)) {
	            	Solver s = null;
	            	for (Solver tmp : SolverDAO.getAll()) {
	            		if (solverName.equals(tmp.getName())) {
	            			s = tmp;
	            			break;
	            		}
					}
					if (s != null) {
						System.out.println("Saving solver to database..");
						SolverBinaries binary = new SolverBinaries(s.getId());
						List<File> binaryFiles = getFilesOfDirectory(solverFolder);
						File[] fileArrayOrig = binaryFiles.toArray(new File[0]);
						Arrays.sort(fileArrayOrig);
						File[] fileArray = Arrays.copyOf(fileArrayOrig, fileArrayOrig.length);
						transformFileArray(fileArray, solverFolder);
						binary.setBinaryArchive(fileArray);
						binary.setRootDir(solverFolder.getCanonicalPath());
						
						String binaryName = "" + expids[0];
						for (int i = 1; i < expids.length; i++)
							binaryName += "," + expids[i];
						binary.setBinaryName(binaryName);
						binary.setRunCommand("");
						binary.setRunPath("/start.sh");
						binary.setVersion("");
						
						FileInputStreamList is = new FileInputStreamList(fileArrayOrig);
			            SequenceInputStream seq = new SequenceInputStream(is);
			            binary.setMd5(Util.calculateMD5(seq));
						SolverBinariesDAO.save(binary);
						System.out.println("Saved.");
						
						if (evalExpId != null && evalExpId != -1) {
							String nameSuffix = properties.getProperty("EvaluationConfigNameSuffix");
							String name = binaryName + (nameSuffix != null ? nameSuffix : "");
							SolverConfiguration sc = SolverConfigurationDAO.createSolverConfiguration(binary, evalExpId, 0, name, "");
							
							List<ParameterInstance> pis = new LinkedList<ParameterInstance>();
							for (Parameter p : ParameterDAO.getParameterFromSolverId(s.getId())) {
								ParameterInstance pi = new ParameterInstance();
								pi.setSolverConfiguration(sc);
								pi.setParameter_id(p.getId());
								pi.setValue(p.getDefaultValue());
								pis.add(pi);
							}
							ParameterInstanceDAO.saveBulk(pis);
						}
					}
	            }
			} else {
				System.err.println("Could not create directory: " + folder);
			}
		}	
		if (Boolean.parseBoolean(properties.getProperty("ExportClusterData"))) {
			BufferedWriter bw = new BufferedWriter(new FileWriter(new File(properties.getProperty("ExportClusterDataFilename"))));
			for (Entry<Integer, List<Integer>> e : the_clustering.entrySet()) {
				bw.write(e.getKey() + ",");
				for (int i = 0; i < e.getValue().size(); i++) {
					bw.write(e.getValue().get(i).toString());
					if (i != e.getValue().size() - 1) {
						bw.write(',');
					}
				}
				bw.write('\n');
			}
			bw.close();
		}
		
		if (Boolean.parseBoolean(properties.getProperty("SaveUsedConfigurationsToEvaluationExperiment"))) {

			
			List<SolverConfiguration> scs = new LinkedList<SolverConfiguration>();
			List<ParameterInstance> pis = new LinkedList<ParameterInstance>();
			int sccount = 0;
			
			String namePref = "" + expids[0];
			for (int i = 1; i < expids.length; i++)
				namePref += "," + expids[i];
			for (Entry<Integer, List<Integer>> e : the_clustering.entrySet()) {
				SolverConfiguration sc = SolverConfigurationDAO.getSolverConfigurationById(e.getKey());
				SolverConfiguration newSc = new SolverConfiguration(sc);
				newSc.setNew();
				newSc.setExperiment_id(evalExpId);
				
				String nameSuffix = properties.getProperty("EvaluationConfigNameSuffix");
				String name = ""+namePref + (nameSuffix != null ? nameSuffix : "")+ "," + (sccount++) + "," + sc.getId();
				
				newSc.setName(name);
				scs.add(newSc);
				
				for (ParameterInstance pi : ParameterInstanceDAO.getBySolverConfig(sc)) {
					ParameterInstance newPi = new ParameterInstance(pi);
					newPi.setNew();
					newPi.setSolverConfiguration(newSc);
					pis.add(newPi);
				}
			}
			SolverConfigurationDAO.saveAll(scs);
			ParameterInstanceDAO.saveBulk(pis);
		}
		
		if (Boolean.parseBoolean(properties.getProperty("Interactive"))) {
			interactiveMode(C, new File(featureDirectory),data);
		}
		
	}
	
	private static float showCost(HashMap<Integer, List<Integer>> clustering, Clustering C) {
		int instanceCount = 0;
		float res = 0.f;
		for (Entry<Integer, List<Integer>> e : clustering.entrySet()) {
			for (int iid : e.getValue()) {
				double cost = C.getCost(e.getKey(), iid);
				if (Double.isInfinite(cost) || Double.isNaN(cost)) {
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
	
	
	public static void interactiveMode(Clustering C, File featuresFolder, List<Object> data) throws Exception {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String line;
		RandomForest rf = null;
		for (Object o : data) {
			if (o instanceof RandomForest)
				rf = (RandomForest)o;
		}
		while ((line = br.readLine()) != null) {
			if ("q".equals(line)) {
				System.out.println("Query Mode");
				System.out.println("Method?");
				String method = br.readLine();
				System.out.println("id?");
				String id = br.readLine();
				if (method == null || id == null)
					break;
				
				float[] features = AAC.calculateFeatures(Integer.valueOf(id), featuresFolder, null);
				if ("randomforest".equals(method)) {
					String params = C.P.get(rf.getSolverConfig(features));
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
	
	public static void transformFileArray(File[] array, File baseDirectory) throws IOException {
		for (int i = 0; i < array.length; i++) {
			String tmp = array[i].getCanonicalPath().replace(baseDirectory.getCanonicalPath(), "");
			if (tmp.startsWith("/") || tmp.startsWith("\\")) {
				tmp = tmp.substring(1, tmp.length());
			}
			array[i] = new File(tmp);
		}
	}
	
	public static List<File> getFilesOfDirectory(File directory) {
		List<File> res = new LinkedList<File>();
		for (File f : directory.listFiles()) {
			if (f.isDirectory()) {
				res.addAll(getFilesOfDirectory(f));
			} else {
				res.add(f);
			}
		}
		return res;
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
