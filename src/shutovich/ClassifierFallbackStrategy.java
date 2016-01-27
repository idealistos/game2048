package shutovich;

import org.dmlc.xgboost4j.Booster;
import org.dmlc.xgboost4j.util.XGBoostError;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Created by U on 1/26/2016.
 */
public class ClassifierFallbackStrategy extends CheckingFallbackStrategy {

    Booster[] models;
    double[] weights;
    boolean consider4s = false;

    ClassifierFallbackStrategy(String[] modelFileNames, double[] weights, Options options) {
        super(options);
        assert(modelFileNames.length == weights.length);
        models = new Booster[modelFileNames.length];
        for (int i = 0; i < modelFileNames.length; i++) {
            models[i] = new Classifier(modelFileNames[i]).loadModel();
        }
        this.weights = weights;
    }

    Entry<Direction, Double> getBestDirection(
            Map<Direction, List<Entry<Map<Direction, Long>, Double>>> positionsToCheck,
            Map<Long, Double> predicts) {
        Entry<Direction, Double> bestDirection = new SimpleEntry<>(null, 1e6);
        for (Direction direction : Direction.values()) {
            List<Entry<Map<Direction, Long>, Double>> positions1 = positionsToCheck.get(direction);
            if (positions1 != null && !positions1.isEmpty()) {
                List<Entry<Double, Double>> bestPredicts = positions1.stream()
                        .map(x -> new SimpleEntry<>(x.getKey().values().stream().map(predicts::get)
                                .min(Double::compare).orElse(options.endGamePredict), x.getValue()))
                        .collect(Collectors.toList());
                double p = options.predictCombinationPower;
                double totalPredict = Math.pow(bestPredicts.stream()
                        .map(x -> Math.pow(x.getKey(), p) * x.getValue())
                        .reduce(0.0, Double::sum)
                        / bestPredicts.stream().map(Entry::getValue).reduce(0.0, Double::sum), 1.0 / p);
                if (totalPredict < bestDirection.getValue()) {
                    bestDirection = new SimpleEntry<>(direction, totalPredict);
                }
            }
        }
        return bestDirection;
    }

    @Override
    Booster getCheckingModel() {
        return models[0];
    }

    @Override
    public Action chooseOptimalAction(GameField field) {
        Map<Direction, List<Entry<Map<Direction, Long>, Double>>> positionsToCheck
                = field.getPositionsInDepth1(consider4s);
        if (positionsToCheck.isEmpty()) {
            return new Action(null, 1e6, null);
        }
        List<Long> allPositions = positionsToCheck.entrySet().stream()
                .flatMap(x -> x.getValue().stream().flatMap(y -> y.getKey().values().stream()))
                .collect(Collectors.toList());
        if (allPositions.isEmpty()) {
            Direction direction = positionsToCheck.keySet().iterator().next();
            return new Action(field.shift(direction), 1e6, direction);
        }
        Map<Long, List<Double>> features = allPositions.stream().distinct()
                .map(x -> new SimpleEntry<>(x, new GameField(x).getFeatures()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        Map<Long, Double> predicts = Classifier.predict(models, weights, features);
        Entry<Direction, Double> bestDirection = getBestDirection(positionsToCheck, predicts);
        field.shift(bestDirection.getKey());
        return new Action(field, bestDirection.getValue(), bestDirection.getKey());
    }
}