package shutovich;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.dmlc.xgboost4j.Booster;

public class AddedMeasureFallbackStrategy extends CheckingFallbackStrategy {

    Booster model;
    Booster measureModel;
    int depth;
    boolean check4s = false;

    AddedMeasureFallbackStrategy(String modelFileName, String measureModelFileName, Options options, int depth) {
        super(options, 0.0);
        assert(depth >= 2);
        readThreshold(modelFileName);
        model = new Classifier(modelFileName).loadModel();
        measureModel = new Classifier(measureModelFileName).loadModel();
        this.depth = depth;
    }

    @Override
    Booster getCheckingModel() {
        return model;
    }

    @Override
    public Action chooseOptimalAction(GameField field) {
        Map<Direction, List<Entry<Map<Direction, Long>, Double>>> positionsToCheck = field.getPositionsInDepth1(check4s);
        if (positionsToCheck.isEmpty()) {
            return new Action(null, 1e6, null);
        }
        Map<Long, Double> predicts = ClassifierFallbackStrategy.getPredicts(positionsToCheck, measureModel, check4s);
        
        return MinMismatchDepthNStrategy.chooseOptimalAction(field, options, depth, check4s, predicts, depth - 1);
    }
}