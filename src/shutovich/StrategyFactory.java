package shutovich;

/**
 * Created by U on 1/26/2016.
 */
public class StrategyFactory {

    static Strategy createStrategy(Options options) {
        return new MinMismatchDepthNStrategy(options, StrategyFactory.createFallbackStrategy(options), 1);
        // return new MinMismatchDepthNStrategy(options, null, 1);
    }

    static FallbackStrategy createFallbackStrategy(Options options) {
        int mode = 9;
        if (mode == 0) {
            String[] modelFileNames = {"data/models/model.10", "data/models/model.5"};
            double[] weights = {1.0, options.closeFallbackWeight};
            return new ClassifierFallbackStrategy(modelFileNames, weights, options);
        } else if (mode == 1) {
            return new IncreasedDepthFallbackStrategy("data/models/model.10", options, 3);
        } else if (mode == 2) {
            return new IncreasedDepthFallbackStrategy("data/models/model.5", options, 3);
        } else if (mode == 3) {
            return new ClassifierFallbackStrategy(new String[] { "data/models/model.10", "data/models/model.5"},
                    new double[] { 1.0, options.closeFallbackWeight}, options);
        } else if (mode == 5) {
            return new IncreasedDepthFallbackStrategy("data/models/model-50.10.top", options, 3);
        } else if (mode == 6) {
            return new IncreasedDepthFallbackStrategy("data/models/model-50.5.fair", options, 3);
        } else if (mode == 7) {
            return new IncreasedDepthFallbackStrategy("data/models/model-rec1-d2-auc2.10.fair", options, 2);
        } else if (mode == 8) {
            return new IncreasedDepthFallbackStrategy("data/models/model-avg-rec1-d7.logit.fair", options, 3);
        } else if (mode == 9) {
            return new AddedMeasureFallbackStrategy("data/models/model-avg-rec1-d7.logit.fair",
                    "data/models/model-avg-rec1p3-d2.logit.fair", options, 3);
        } else {
            return null;
        }
    }
}
