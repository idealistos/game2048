package shutovich;

/**
 * Created by U on 12/12/2015.
 */
public class SimpleStrategy extends Strategy {

    SimpleStrategy(Options options, FallbackStrategy fallbackStrategy) {
        super(options, fallbackStrategy);
    }

    @Override
    Action chooseOptimalAction() {
        // System.out.println("After adding:\n" + field.toString());
        boolean canShiftDown = field.canShift(Direction.DOWN);
        boolean canShiftRight = field.canShift(Direction.RIGHT);
        Direction direction = null;
        if (turn % 2 == 0 && canShiftDown) {
            direction = Direction.DOWN;
        } else if (turn % 2 == 1 && canShiftRight) {
            direction = Direction.RIGHT;
        } else if (canShiftDown) {
            direction = Direction.DOWN;
        } else if (canShiftRight) {
            direction = Direction.RIGHT;
        } else if (field.canShift(Direction.LEFT)) {
            direction = Direction.LEFT;
        } else if (field.canShift(Direction.UP)) {
            direction = Direction.UP;
        } else {
            return null;
        }
        field.shift(direction);
        return new Action(field, 0.0, direction);
    }
}
