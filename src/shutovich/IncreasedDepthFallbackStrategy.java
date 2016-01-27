package shutovich;

import org.dmlc.xgboost4j.Booster;

/**
 * Created by U on 1/26/2016.
 */
public class IncreasedDepthFallbackStrategy extends CheckingFallbackStrategy {

    Booster model;
    int depth;

    IncreasedDepthFallbackStrategy(String modelFileName, Options options, int depth) {
        super(options);
        model = new Classifier(modelFileName).loadModel();
        this.depth = depth;
    }

    @Override
    Booster getCheckingModel() {
        return model;
    }

    @Override
    public Action chooseOptimalAction(GameField field) {
        Strategy strategy = new MinMismatchDepthNStrategy(options, null, depth);
        strategy.field = field;
        return strategy.chooseOptimalAction();
    }
}
