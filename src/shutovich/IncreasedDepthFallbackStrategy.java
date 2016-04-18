package shutovich;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

import org.dmlc.xgboost4j.Booster;

/**
 * Created by U on 1/26/2016.
 */
public class IncreasedDepthFallbackStrategy extends CheckingFallbackStrategy {

    Booster model;
    int depth;

    IncreasedDepthFallbackStrategy(String modelFileName, Options options, int depth) {
        super(options, 0.0);
        readThreshold(modelFileName);
        model = new Classifier(modelFileName).loadModel();
        this.depth = depth;
    }

    @Override
    Booster getCheckingModel() {
        return model;
    }

    @Override
    public Action chooseOptimalAction(GameField field) {
        return MinMismatchDepthNStrategy.chooseOptimalAction(field, options, depth, false, null, -1);
    }
}
