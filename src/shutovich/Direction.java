package shutovich;

/**
 * Created by U on 12/12/2015.
 */
public enum Direction {
    UP(true, false), DOWN(true, true), LEFT(false, false), RIGHT(false, true);

    boolean swap;
    boolean increasing;

    private Direction(boolean swap, boolean increasing) {
        this.swap = swap;
        this.increasing = increasing;
    }
}
