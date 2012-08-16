package edacc.configurator.aac.util;

import org.rosuda.JRI.Rengine;

/**
 * Rengine can only be instantiated once it seems. So this class provides access to a single Rengine instance.
 */
public class RInterface {
    private static Rengine rengine = null;
    
    public static Rengine getRengine() throws RuntimeException {
        if (rengine == null) {
            rengine = new Rengine(new String[] { "--vanilla" }, false, null);
            if (!rengine.waitForR()) {
                throw new RuntimeException("Could not initialize Rengine");
            }
        }
        return rengine;
    }
}
