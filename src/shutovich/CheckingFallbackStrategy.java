package shutovich;

import org.dmlc.xgboost4j.Booster;
import org.dmlc.xgboost4j.util.XGBoostError;

import java.util.Arrays;

/**
 * Created by U on 1/26/2016.
 */
public abstract class CheckingFallbackStrategy implements FallbackStrategy {
    Options options;

    CheckingFallbackStrategy(Options options) {
        this.options = options;
    }

    abstract Booster getCheckingModel();

    @Override
    public boolean needsFallback(GameField field) {
        try {
            float[][] predict = getCheckingModel().predict(Classifier.getMatrix(Arrays.asList(field.getFeatures())));
            // System.out.println(predict[0][0]);
            return (predict[0][0] >= options.fallbackThreshold);
        } catch (XGBoostError e) {
            e.printStackTrace();
            return false;
        }
    }


}
