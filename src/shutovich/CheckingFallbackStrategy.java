package shutovich;

import org.dmlc.xgboost4j.Booster;
import org.dmlc.xgboost4j.util.XGBoostError;

import java.util.Arrays;
import java.util.Random;

/**
 * Created by U on 1/26/2016.
 */
public abstract class CheckingFallbackStrategy implements FallbackStrategy {
    Options options;
    
    static int count = 0;

    CheckingFallbackStrategy(Options options) {
        this.options = options;
    }

    abstract Booster getCheckingModel();

    @Override
    public boolean needsFallback(GameField field) {
        try {
            float[][] predict = getCheckingModel().predict(Classifier.getMatrix(Arrays.asList(field.getFeatures())));
            if (count++ % 100000 == 0) {
                System.out.println("" + count + ", " + predict[0][0]);
            }
            // System.out.println(predict[0][0]);
            return (predict[0][0] >= options.fallbackThreshold);
        } catch (XGBoostError e) {
            e.printStackTrace();
            return false;
        }
    }


}
