package shutovich;

/**
 * Created by U on 12/20/2015.
 */
public class MinMismatchStrategy extends Strategy {

    MinMismatchStrategy(Options options, FallbackStrategy fallbackStrategy) {
        super(options, fallbackStrategy);
    }

    static double getPositionPenalty(GameField field, Options options) {
        return field.getRightDownPenalty()
                + options.trapFactor * field.getTrapPenalty()
                + options.emptyCellPenalty * (256.0 - Math.pow(field.getEmptyCellCount(), 2.0))
                + options.largeNumberCountPenalty * field.getLargeNumbersCount();
    }

    static int getActions(GameField field, Action[] actions, Options options) {
        int actionCount = 0;
        for (Direction direction : Direction.values()) {
            if (!field.cornerMoves(direction) && field.canShift(direction)) {
                GameField field1 = new GameField(field).shift(direction);
                double penalty = MinMismatchStrategy.getPositionPenalty(field1, options);
                long sum = field1.getSum();
                if ((sum % 4 == 0 && direction == Direction.DOWN) || (sum % 4 == 1 && direction == Direction.RIGHT)) {
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
                double penalty = MinMismatchStrategy.getPositionPenalty(newField, options) + options.cornerMovesPenalty;
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
    
    static Action chooseOptimalAction(GameField field, Options options) {
        Action[] actions = new Action[4];
        int actionCount = MinMismatchStrategy.getActions(field, actions, options);
        return MinMismatchStrategy.getBestAction(actions, actionCount);
    }

    @Override
    Action chooseOptimalAction() {
        Action action = MinMismatchStrategy.chooseOptimalAction(field, options);
        field = (action.field == null)? field : action.field;
        return action;
    }

}
