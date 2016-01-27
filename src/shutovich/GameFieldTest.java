package shutovich;

import org.junit.Test;

import static org.junit.Assert.*;

public class GameFieldTest {

    @Test
    public void testGetTrapPenalty() throws Exception {
        GameField field = new GameField();
        field.load(new int[][] {{0, 0, 0, 0}, {0, 2, 3, 4}, {2, 3, 4, 5}, {3, 4, 5, 6}});
        assertTrue("Trap penalty", field.getTrapPenalty() == 10);
        field.load(new int[][] {{0, 0, 0, 0}, {0, 7, 3, 4}, {5, 3, 4, 5}, {3, 4, 5, 6}});
        assertTrue("Trap penalty", field.getTrapPenalty() == 11);
        field.load(new int[][] {{0, 0, 0, 0}, {1, 0, 0, 4}, {2, 3, 4, 5}, {3, 4, 5, 6}});
        assertTrue("No trap penalty", field.getTrapPenalty() == 0);
        field.load(new int[][] {{0, 0, 0, 0}, {1, 1, 3, 4}, {2, 3, 4, 5}, {3, 4, 5, 6}});
        assertTrue("No trap penalty", field.getTrapPenalty() == 0);
    }

    @Test
    public void testLocateCorner() throws Exception {
        GameField field = new GameField();
        field.load(new int[][] {{0, 0, 0, 0}, {0, 2, 3, 4}, {2, 3, 4, 5}, {3, 4, 5, 6}});
        assertTrue(field.locateCorner() == 60);
        field.load(new int[][] {{0, 0, 0, 0}, {6, 2, 3, 4}, {2, 3, 4, 5}, {3, 4, 5, 6}});
        assertTrue(field.locateCorner() == 60);
        field.load(new int[][] {{0, 0, 0, 0}, {8, 2, 3, 4}, {2, 3, 4, 5}, {3, 4, 5, 6}});
        assertTrue(field.locateCorner() == 16);


    }


}