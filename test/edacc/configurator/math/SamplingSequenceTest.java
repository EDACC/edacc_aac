package edacc.configurator.math;

import static org.junit.Assert.*;

import org.junit.Test;

public class SamplingSequenceTest {
    @Test
    public void testSamplingSequence() throws Exception {
        SamplingSequence s = new SamplingSequence("contrib/sampling/sampling");
        double[][] data = s.getSequence(2, 10);
        assertTrue(data.length == 10);
        for (int i = 0; i < data.length; i++) {
            assertTrue(data[i].length == 2);
            assertFalse(data[i][0] == Double.NaN || data[i][1] == Double.NaN);
        }
    }
}
