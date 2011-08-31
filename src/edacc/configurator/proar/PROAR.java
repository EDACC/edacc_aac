package edacc.configurator.proar;

import java.io.File;
import java.util.Random;
import java.util.Scanner;

import edacc.api.API;
import edacc.api.APIImpl;

public class PROAR {
	
	/**
	 * Parses the configuration file and starts the configurator. 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Missing configuration file. Use java -jar PROAR.jar <config file path>");
            return;
        }
        Scanner scanner = new Scanner(new File(args[0]));
        String hostname = "", user = "", password = "", database = "";
        int idExperiment = 0;
        int port = 3306;
        int jobCPUTimeLimit = 13;
        long seed = System.currentTimeMillis();
        
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.trim().startsWith("%")) continue;
            String[] keyval = line.split("=");
            String key = keyval[0].trim();
            String value = keyval[1].trim();
            if ("host".equals(key)) hostname = value;
            else if ("user".equals(key)) user = value;
            else if ("password".equals(key)) password = value;
            else if ("port".equals(key)) port = Integer.valueOf(value);
            else if ("database".equals(key)) database = value;
            else if ("idExperiment".equals(key)) idExperiment = Integer.valueOf(value);
            else if ("seed".equals(key)) seed = Long.valueOf(value);
        }
        scanner.close();
        
        PROAR configurator = new PROAR(hostname, port, database, user, password, idExperiment, jobCPUTimeLimit, seed);
        configurator.start();
        configurator.shutdown();
	}
	
	private API api;
	private int idExperiment;
	private int jobCPUTimeLimit;
	private Random rng;
	
	
	public PROAR(String hostname, int port, String database, String user, String password, int idExperiment, int jobCPUTimeLimit, long seed) throws Exception {
		api = new APIImpl();
		api.connect(hostname, port, database, user, password);
		this.idExperiment = idExperiment;
		this.jobCPUTimeLimit = jobCPUTimeLimit;
		rng = new edacc.util.MersenneTwister(seed);
	}
	
	public void start() {
		// TODO: implement PROAR
	}
	
	public void shutdown() {
		api.disconnect();
	}
}
