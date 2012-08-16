package edacc.configurator.models.rf.fastrf.utils;

import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.NormalDistribution;
import org.apache.commons.math.distribution.NormalDistributionImpl;

/**
 * From http://introcs.cs.princeton.edu/java/21function/Gaussian.java.html
 */
public class Gaussian {
    private static NormalDistribution nd = new NormalDistributionImpl();
    
    // return phi(x) = standard Gaussian pdf
    public static double phi(double x) {
        return Math.exp(-x*x / 2) / Math.sqrt(2 * Math.PI);
    }

    // return phi(x, mu, signma) = Gaussian pdf with mean mu and stddev sigma
    public static double phi(double x, double mu, double sigma) {
        return phi((x - mu) / sigma) / sigma;
    }

    // return Phi(z) = standard Gaussian cdf using Taylor approximation
    public static double Phi(double z) throws MathException {
        return nd.cumulativeProbability(z);
        /*
        if (z < -8.0) return 0.0;
        if (z >  8.0) return 1.0;
        double sum = 0.0, term = z;
        int iter = 0;
        for (int i = 3; sum + term != sum && iter++ < 500; i += 2) {
            sum  = sum + term;
            term = term * z * z / i;
        }
        return 0.5 + sum * phi(z);*/
    }

    // return Phi(z, mu, sigma) = Gaussian cdf with mean mu and stddev sigma
    public static double Phi(double z, double mu, double sigma) throws MathException {
        return Phi((z - mu) / sigma);
    } 
    
    // Compute z such that Phi(z) = y via bisection search
    public static double PhiInverse(double y) throws MathException {
        return nd.inverseCumulativeProbability(y);
        //return PhiInverse(y, .00000001, -8, 8);
    } 

    // bisection search
    private static double PhiInverse(double y, double delta, double lo, double hi) throws MathException {
        double mid = lo + (hi - lo) / 2;
        if (hi - lo < delta) return mid;
        if (Phi(mid) > y) return PhiInverse(y, delta, lo, mid);
        else              return PhiInverse(y, delta, mid, hi);
    }
    
    public static double errorFunctionInverse( double z ) throws MathException {
		return PhiInverse( 0.5 * z + 0.5 ) / Math.sqrt(2);
	}
}