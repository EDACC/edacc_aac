package edacc.configurator.math;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Wrapper around the external C program that uses
 * the GNU scientific library to generate quasi-random
 * sequences of numbers in d dimensions.
 * 
 * @author daniel
 *
 */
public class SamplingSequence {
    private ProcessBuilder builder;
    private String samplingPath;
    
    /**
     * Initialize the class with the path of the executable sampling program.
     * The program is expected to take two integers as command line arguments,
     * the number of dimensions D and the number of samples N to generate.
     * 
     * The output of the program has to be N lines in which the numbers of each D-tuple are
     * separated by whitespace.
     * 
     * @param samplingPath Path pointing to the executable sampling program
     */
    public SamplingSequence(String samplingPath) {
        this.samplingPath = samplingPath;
    }
    
    public double[][] getSequence(int dimensions, int numSamples) throws IOException, InterruptedException {
        builder = new ProcessBuilder(samplingPath, String.valueOf(dimensions), String.valueOf(numSamples));
        Process process = builder.start();
        InputStream stdout = process.getInputStream ();
        BufferedReader reader = new BufferedReader (new InputStreamReader(stdout));

        String line;
        double data[][] = new double[numSamples][dimensions];
        int i = 0;
        while ((line = reader.readLine ()) != null) {
            if (i > numSamples) break;
            String[] vals = line.split("\\s"); // split on whitespace
            for (int d = 0; d < dimensions; d++) {
                data[i][d] = Double.valueOf(vals[d]);
            }
            i++;
        }
        process.waitFor();
        return data;
    }
    
    public static void main(String ... args) throws IOException, InterruptedException {
        // Testing
        SamplingSequence s = new SamplingSequence("contrib/sampling/sampling");
        double[][] data = s.getSequence(4, 100);
        for (int i = 0; i < 100; i++) {
            for (int d = 0; d < 4; d++) {
                System.out.print(data[i][d] + " ");
            }
            System.out.println();
        }
    }
}
