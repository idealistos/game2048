package shutovich;

import java.util.Map;

/**
 * Created by U on 12/21/2015.
 */
public class MinMismatchDepthNStrategy extends MinMismatchStrategy {
    int depth;

    MinMismatchDepthNStrategy(Options options, FallbackStrategy fallbackStrategy, int depth) {
        super(options, fallbackStrategy);
        this.depth = depth;
    }
    
    static Action chooseOptimalAction(GameField field, Options options, int level, boolean check4s, Map<Long, Double> measures, int measuresLevel) {
        Action[] actions = new Action[4];
        int actionCount = MinMismatchStrategy.getActions(field, actions, options);
        Action bestAction = MinMismatchStrategy.getBestAction(actions, actionCount);
        if (bestAction.penalty <= options.minGoodPenalty + options.emptyCellPenalty * 256.0) {
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
                    double weights[] = {0.0, check4s? 0.9 : 1.0, 0.1};
                    for (int i = 0; i < emptyCellCount; i++) {
                        for (int k = 1; k <= (check4s? 2 : 1); k++) {
                            field = new GameField(actions[iAction].field).setEmptyCellValue(i, k);
                            Action bestAction1 = (level == 1)? MinMismatchStrategy.chooseOptimalAction(field, options)
                                    : chooseOptimalAction(field, options, level - 1, check4s, measures, measuresLevel);
                            penaltySum += Math.pow(bestAction1.penalty, options.combinationPower) * weights[k];
                        }
                    }
                    double averagePenalty = Math.pow(penaltySum / emptyCellCount, 1.0 / options.combinationPower);
                    if (level == measuresLevel) {
                        averagePenalty += measures.get(actions[iAction].field.lines) * options.measureWeight;
                    }
                    if (averagePenalty < bestAveragePenalty) {
                        bestAveragePenalty = averagePenalty;
                        bestAction = actions[iAction];
                        bestAction.penalty = bestAveragePenalty;
                    }
                }
            }
            return bestAction;
        }
        
    }

    @Override
    Action chooseOptimalAction() {
        Action action = MinMismatchDepthNStrategy.chooseOptimalAction(field, options, depth, false, null, -1);
        field = (action.field == null)? field : action.field;
        return action;
    }

}
