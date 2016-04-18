package shutovich;

import org.dmlc.xgboost4j.Booster;
import org.dmlc.xgboost4j.util.XGBoostError;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Created by U on 1/26/2016.
 */
public abstract class CheckingFallbackStrategy implements FallbackStrategy {

    Options options;
    double fallbackThreshold;
    
    

    CheckingFallbackStrategy(Options options, double fallbackThreshold) {
        this.options = options;
        this.fallbackThreshold = fallbackThreshold;
    }

    abstract Booster getCheckingModel();

    void readThreshold(String modelFileName) {
        try {
            fallbackThreshold = Files.readAllLines(new File(modelFileName + ".threshold").toPath()).stream().map(s -> Double.parseDouble(s))
                    .collect(Collectors.toList()).get(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public boolean needsFallback(GameField field) {
        try {
            float[][] predict = getCheckingModel().predict(Classifier.getMatrix(Arrays.asList(field.getFeatures())));
            return (predict[0][0] >= options.fallbackThresholdFactor * fallbackThreshold);
        } catch (XGBoostError e) {
            e.printStackTrace();
            return false;
        }
    }

}
