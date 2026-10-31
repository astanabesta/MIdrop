package com.astanabesta.midrop;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
public class ProgressTest {
    @Test public void computesAndBoundsProgressWithoutLongOverflow() {
        assertEquals(0, Progress.percent(1, 0));
        assertEquals(0, Progress.percent(-1, 10));
        assertEquals(50, Progress.percent(5, 10));
        assertEquals(100, Progress.percent(11, 10));
        assertEquals(50, Progress.percent(Long.MAX_VALUE / 2, Long.MAX_VALUE));
    }
}
