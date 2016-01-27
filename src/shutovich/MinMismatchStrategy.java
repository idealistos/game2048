package shutovich;

/**
 * Created by U on 12/20/2015.
 */
public class MinMismatchStrategy extends Strategy {

    MinMismatchStrategy(Options options, FallbackStrategy fallbackStrategy) {
        super(options, fallbackStrategy);
    }

    double getPositionPenalty(GameField field) {
        return field.getRightDownPenalty()
                + options.trapFactor * field.getTrapPenalty()
                - options.emptyCellPenalty * Math.pow(field.getEmptyCellCount(), 2.0)
                + options.largeNumberCountPenalty * field.getLargeNumbersCount();
    }

    int getActions(GameField field, Action[] actions) {
        int actionCount = 0;
        for (Direction direction : Direction.values()) {
            if (!field.cornerMoves(direction) && field.canShift(direction)) {
                GameField field1 = new GameField(field).shift(direction);
                double penalty = getPositionPenalty(field1);
                if ((turn % 2 == 0 && direction == Direction.DOWN) || (turn % 2 == 1 && direction == Direction.RIGHT)) {
                    penalty -= 0.001;
                }
                actions[actionCount++] = new Action(field1, penalty, direction);
            }
        }
        if (actionCount > 0) {
            return actionCount;
        }
        for (Direction direction : Direction.values()) {
            if (field.canShift(direction)) {
                GameField newField = new GameField(field).shift(direction);
                double penalty = getPositionPenalty(newField) + options.cornerMovesPenalty;
                actions[actionCount++] = new Action(newField, penalty, direction);
            }
        }
        return actionCount;
    }

    static Action getBestAction(Action[] actions, int actionCount) {
        int iBest = -1;
        double bestPenalty = 1e6;
        for (int i = 0; i < actionCount; i++) {
            if (actions[i].penalty < bestPenalty) {
                iBest = i;
                bestPenalty = actions[i].penalty;
            }
        }
        return (iBest < 0)? new Action(null, 1e6, null) : actions[iBest];
    }

    @Override
    Action chooseOptimalAction() {
        Action[] actions = new Action[4];
        int actionCount = getActions(field, actions);
        Action bestAction = MinMismatchStrategy.getBestAction(actions, actionCount);
        field = (bestAction.field == null)? field : bestAction.field;
        return bestAction;
    }

}
