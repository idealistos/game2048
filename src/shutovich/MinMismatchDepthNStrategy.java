package shutovich;

/**
 * Created by U on 12/21/2015.
 */
public class MinMismatchDepthNStrategy extends MinMismatchStrategy {
    int maxLevel;

    MinMismatchDepthNStrategy(Options options, FallbackStrategy fallbackStrategy, int maxLevel) {
        super(options, fallbackStrategy);
        this.maxLevel = maxLevel;
    }

    Action chooseOptimalAction(int level) {
        Action[] actions = new Action[4];
        int actionCount = getActions(field, actions);
        Action bestAction = MinMismatchStrategy.getBestAction(actions, actionCount);
        if (bestAction.penalty <= options.minGoodPenalty) {
            bestAction = MinMismatchStrategy.getBestAction(actions, actionCount);
            field = bestAction.field;
            return bestAction;
        } else if (bestAction.direction == null) {
            return new Action(null, options.endGamePenalty, null);
        } else {
            double bestAveragePenalty = options.endGamePenalty;
            bestAction = new Action(field, options.endGamePenalty, null);
            for (int iAction = 0; iAction < actionCount; iAction++) {
                int emptyCellCount = actions[iAction].field.getEmptyCellCount();
                if (emptyCellCount > 0) {
                    double penaltySum = 0.0;
                    for (int i = 0; i < emptyCellCount; i++) {
                        field = new GameField(actions[iAction].field).setEmptyCellValue(i, 1);
                        Action bestAction1 = (level == 1)? super.chooseOptimalAction() : chooseOptimalAction(level - 1);
                        penaltySum += Math.pow(bestAction1.penalty, options.combinationPower);
                    }
                    double averagePenalty = Math.pow(penaltySum / emptyCellCount, 1.0 / options.combinationPower);
                    if (averagePenalty < bestAveragePenalty) {
                        bestAveragePenalty = averagePenalty;
                        bestAction = actions[iAction];
                        bestAction.penalty = bestAveragePenalty;
                    }
                }
            }
            field = bestAction.field;
            return bestAction;
        }
    }

    @Override
    Action chooseOptimalAction() {
        return chooseOptimalAction(maxLevel);
    }

}
