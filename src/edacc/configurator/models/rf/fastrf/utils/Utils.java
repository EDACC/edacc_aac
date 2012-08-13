package edacc.configurator.models.rf.fastrf.utils;

import java.util.*;

public class Utils {    
	public static final double l10e = Math.log10(Math.E);
    public static int sum(int[] arr) {
        if (arr == null || arr.length == 0) return 0;
        int l = arr.length;
        int res = 0;
        for (int i=0; i < l; i++) {
            res += arr[i];
        }
        return res;
    }
    
    public static double sum(double[] arr) {
        if (arr == null || arr.length == 0) return 0;
        int l = arr.length;
        double res = 0;
        for (int i=0; i < l; i++) {
            res += arr[i];
        }
        return res;
    }
    
    public static double mean(double[] arr) {
        if (arr == null || arr.length == 0) return 0;
        return sum(arr)/arr.length;
    }
    
    public static double var(double[] arr) {
        if (arr == null || arr.length == 0) return 0;
        int l = arr.length;
        
        double sum = 0, sumSq = 0;
        for (int i=0; i < l; i++) {
            sum += arr[i];
            sumSq += arr[i] * arr[i];
        }
        return (sumSq - sum*sum/l)/Math.max(l-1, 1);
    }
    
    public static double median(double[] arr) {
        if (arr == null || arr.length == 0) return Double.NaN;
        int l = arr.length;
        Arrays.sort(arr);
        return arr[(int)Math.floor(l/2.0)] / 2 + arr[(int)Math.ceil(l/2.0)] / 2;
    }
    
    public static double prod(double[] arr, int start, int end) {
        double result = 1;
        for (int i=start; i < end; i++) {
            result *= arr[i];
        }
        return result;
    }
    
    public static boolean any(boolean[] b) {
        for (int i = 0; i < b.length; i++) if (b[i]) return true;
        return false;
    }
}
