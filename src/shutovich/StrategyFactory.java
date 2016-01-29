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
        int mode = 5;
        if (mode == 0) {
            String[] modelFileNames = {"model.10", "model.5"};
            double[] weights = {1.0, options.closeFallbackWeight};
            return new ClassifierFallbackStrategy(modelFileNames, weights, options);
        } else if (mode == 1) {
            return new IncreasedDepthFallbackStrategy("model.10", options, 3);
        } else if (mode == 2) {
            return new IncreasedDepthFallbackStrategy("model.5", options, 3);
        } else if (mode == 3) {
            return new ClassifierFallbackStrategy(new String[] { "model.10", "model.5"},
                    new double[] { 1.0, options.closeFallbackWeight}, options);
        } else if (mode == 5) {
            return new IncreasedDepthFallbackStrategy("model-50.10.top", options, 3);
        } else {
            return null;
        }
    }
}
