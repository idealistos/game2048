package shutovich;

/**
 * Created by U on 12/12/2015.
 */
public abstract class Strategy {
    FallbackStrategy fallbackStrategy;
    Options options;
    GameField field;
    int turn = 0;

    Strategy(Options options, FallbackStrategy fallbackStrategy) {
        this.options = options;
        this.fallbackStrategy = fallbackStrategy;
        field = new GameField();
    }

    GameField getField() {
        return field;
    }

    abstract Action chooseOptimalAction();

    void nextTurn1() {
        field.addRandomNumber();
        turn++;
    }

    boolean nextTurn2() {
        GameField oldField = null;
        if (field.debug || fallbackStrategy != null) {
            oldField = new GameField(field);
        }
        Direction direction = chooseOptimalAction().direction;
        if (fallbackStrategy != null && field.getMaxValue() >= 9 && fallbackStrategy.needsFallback(field)) {
            Action bestAction = fallbackStrategy.chooseOptimalAction(oldField);
            direction = bestAction.direction;
            field = (direction == null)? field : bestAction.field;
        }
        if (field.debug) {
            // System.out.println(direction);
            if (direction == Direction.LEFT || direction == Direction.UP) {
                field.debug = true;
            }
            assert(direction == null || !field.equals(oldField));
            assert(oldField != null && (direction == null || field.equals(oldField.shift(direction))));
        }
        return (direction != null);
    }

    boolean nextTurn() {
        nextTurn1();
        return nextTurn2();
    }

    int simulate(FieldSaver saver) {
        field.reset();
        turn = 0;
        if (saver != null) {
            saver.reset();
        }
        while (true) {
            if (saver != null) {
                saver.checkAndWriteField(field, turn);
            }
            if (!nextTurn()) {
                break;
            }
        }
        if (saver != null) {
            saver.close();
        }
        if (field.getMaxValue() >= 13) {
            System.out.println(field.toString() + "\n");
        }
        return (int) field.getMaxValue();
    }

}
