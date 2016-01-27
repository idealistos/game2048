package shutovich;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.ArrayUtils;
import org.dmlc.xgboost4j.Booster;
import org.dmlc.xgboost4j.DMatrix;
import org.dmlc.xgboost4j.util.Trainer;
import org.dmlc.xgboost4j.util.XGBoostError;

/**
 * Created by U on 1/25/2016.
 */
public class Classifier {
    int lastTurns;
    String fileName;
    double defaultThreshold;
    List<Entry<String, Object>> boosterParameters = new ArrayList<Entry<String, Object>>() {
        {
            add(new SimpleEntry<>("eta", 1.0));
            add(new SimpleEntry<>("max_depth", 8.0));
            add(new SimpleEntry<>("silent", 1));
            add(new SimpleEntry<>("objective", "binary:logistic"));
        }
    };

    Classifier(int lastTurns, String fileName, double defaultThreshold) {
        this.lastTurns = lastTurns;
        this.fileName = fileName;
        this.defaultThreshold = defaultThreshold;
    }

    Classifier(String fileName) {
        this(-1, fileName, 0.0);
    }

    static DMatrix getMatrix(List<List<Double>> features) {
        if (features.isEmpty()) {
            return null;
        }
        int columnCount = features.get(0).size();
        float[] data = new float[columnCount * features.size()];
        for (int i = 0; i < features.size(); i++) {
            float[] dataLine = ArrayUtils.toPrimitive(features.get(i).stream().map(x -> (float) x.doubleValue())
                    .collect(Collectors.toList()).toArray(new Float[0]));
            System.arraycopy(dataLine, 0, data, i * columnCount, columnCount);
        }
        try {
            return new DMatrix(data, features.size(), columnCount);
        } catch (XGBoostError e) {
            e.printStackTrace();
            return null;
        }
    }

    static Map<Long, Double> predict(Booster booster, Map<Long, List<Double>> features) {
        List<Entry<Long, List<Double>>> entries = new ArrayList<>(features.entrySet());
        DMatrix matrix = Classifier.getMatrix(entries.stream().map(Entry::getValue).collect(Collectors.toList()));
        try {
            float[][] predict = booster.predict(matrix);
            return IntStream.range(0, entries.size())
                    .mapToObj(i -> new SimpleEntry<>(entries.get(i).getKey(), new Double(predict[i][0])))
                    .collect(Collectors.<Entry<Long, Double>, Long, Double>toMap(Entry::getKey, Entry::getValue));
        } catch (XGBoostError e) {
            e.printStackTrace();
            return null;
        }
    }

    static Map<Long, Double> predict(Booster[] boosters, double[] weights, Map<Long, List<Double>> features) {
        assert(boosters.length == weights.length);
        List<Entry<Long, List<Double>>> entries = new ArrayList<>(features.entrySet());
        DMatrix matrix = Classifier.getMatrix(entries.stream().map(Entry::getValue).collect(Collectors.toList()));
        try {
            float[] predict = new float[features.size()];
            for (int i = 0; i < boosters.length; i++) {
                float[][] predict1 = boosters[i].predict(matrix);
                assert(predict1.length == predict.length);
                for (int j = 0; j < predict.length; j++) {
                    predict[j] += predict1[j][0] * weights[i];
                }
            }
            return IntStream.range(0, entries.size())
                    .mapToObj(i -> new SimpleEntry<>(entries.get(i).getKey(), new Double(predict[i])))
                    .collect(Collectors.<Entry<Long, Double>, Long, Double>toMap(Entry::getKey, Entry::getValue));
        } catch (XGBoostError e) {
            e.printStackTrace();
            return null;
        }
    }

    Entry<DMatrix, DMatrix> getMatrices(List<Entry<Long, Integer>> positions, Map<Long, List<Double>> features,
                                        IntPredicate conditionForTrain, IntPredicate conditionForTest) {
        int columnCount = features.entrySet().iterator().next().getValue().size();
        Set<Integer> inTrain = IntStream.range(0, positions.size()).filter(conditionForTrain).mapToObj(x -> x)
                .collect(Collectors.toSet());
        Set<Integer> inTest = IntStream.range(0, positions.size()).filter(conditionForTest).mapToObj(x -> x)
                .collect(Collectors.toSet());
        float[] trainData = new float[columnCount * inTrain.size()];
        float[] testData = new float[columnCount * inTest.size()];
        float[] trainLabels = new float[inTrain.size()];
        float[] testLabels = new float[inTest.size()];
        int trainLineIndex = 0;
        int testLineIndex = 0;
        for (int i = 0; i < positions.size(); i++) {
            List<Double> featuresLine = features.get(positions.get(i).getKey());
            float[] dataLine = ArrayUtils.toPrimitive(featuresLine.stream().map(x -> (float) x.doubleValue())
                    .collect(Collectors.toList()).toArray(new Float[0]));
            float label = (positions.get(i).getValue() < lastTurns)? 1.0f : 0.0f;
            if (inTrain.contains(i)) {
                System.arraycopy(dataLine, 0, trainData, columnCount * trainLineIndex, columnCount);
                trainLabels[trainLineIndex++] = label;
            } else if (inTest.contains(i)){
                System.arraycopy(dataLine, 0, testData, columnCount * testLineIndex, columnCount);
                testLabels[testLineIndex++] = label;
            }
        }
        assert(trainData.length == trainLineIndex * columnCount);
        assert(testData.length == testLineIndex * columnCount);
        try {
            DMatrix trainMatrix = new DMatrix(trainData, trainLineIndex, columnCount);
            trainMatrix.setLabel(trainLabels);
            System.out.println("train");
            DMatrix testMatrix = null;
            if (testLineIndex > 0) {
                testMatrix = new DMatrix(testData, testLineIndex, columnCount);
                testMatrix.setLabel(testLabels);
                System.out.println("test");
            }
            System.out.println("Train: " + trainLineIndex + ", test: " + testLineIndex);
            return new SimpleEntry<>(trainMatrix, testMatrix);
        } catch (XGBoostError e) {
            e.printStackTrace();
            return null;
        }
    }

    static double getFMeasure(float[][] predicts, float[] testLabels, double threshold, int unsafeCount) {
            List<Double> markedLabels = IntStream.range(0, predicts.length).filter(i -> predicts[i][0] > threshold)
                .mapToObj(i -> new Double(testLabels[i])).collect(Collectors.toList());
        long correct = markedLabels.stream().filter(x -> x == 1.0).count();
        double fMeasure = 2.0 * correct/ (markedLabels.size() + unsafeCount);
        System.out.println("Threshold " + threshold + ": correct " + correct + ", all marked: " + markedLabels.size()
                + ", all unsafe: " + unsafeCount + ", f: " + fMeasure);
        return fMeasure;
    }

    static void checkPredictLowFP(float[][] predicts, float[] testLabels) {
        assert(predicts.length == testLabels.length);
        double[] safePredicts = IntStream.range(0, predicts.length).filter(i -> testLabels[i] == 0.0)
                .mapToDouble(i -> predicts[i][0]).sorted().toArray();
        double threshold = safePredicts[95 * safePredicts.length / 100];
        Classifier.getFMeasure(predicts, testLabels, threshold, testLabels.length - safePredicts.length);
    }

    static void checkPredictLowTN(float[][] predicts, float[] testLabels) {
        assert(predicts.length == testLabels.length);
        double[] unsafePredicts = IntStream.range(0, predicts.length).filter(i -> testLabels[i] == 1.0)
                .mapToDouble(i -> predicts[i][0]).sorted().toArray();
        double threshold = unsafePredicts[5 * unsafePredicts.length / 100];
        Classifier.getFMeasure(predicts, testLabels, threshold, unsafePredicts.length);
    }

    void trainModel(List<Entry<Long, Integer>> positions, Map<Long, List<Double>> features) {
        double averageFMeasure = 0.0;
        for (int i = 0; i < 5; i++) {
            int i1 = i;
            Entry<DMatrix, DMatrix> matrices = getMatrices(positions, features,
                    // x -> x % 10 == i1, x -> x % 50 == i1 + 1);
                    x -> x % 5 != i1, x -> x % 5 == i1);
            DMatrix train = matrices.getKey();
            DMatrix test = matrices.getValue();
            try {
                Booster booster = Trainer.train(boosterParameters, train, 50,
                        Arrays.asList(new SimpleEntry<>("train", train), new SimpleEntry<>("test", test)), null, null);
                float[][] predicts = booster.predict(test);
                float[] testLabels = test.getLabel();
                Classifier.checkPredictLowFP(predicts, testLabels);
                Classifier.checkPredictLowTN(predicts, testLabels);
                int unsafeCount = (int) IntStream.range(0, testLabels.length).filter(j -> testLabels[j] == 1.0f).count();
                double fMeasure = getFMeasure(predicts, testLabels, defaultThreshold, unsafeCount);
                averageFMeasure += fMeasure / 5;
                if (i == 0) {
                    booster.saveModel(fileName);
                }
            } catch (XGBoostError e) {
                e.printStackTrace();
            }
        }
        System.out.println("Average fMeasure for " + defaultThreshold + ": " + averageFMeasure);
    }

    Booster loadModel() {
        try {
            return new Booster(boosterParameters, fileName);
        } catch (XGBoostError e) {
            e.printStackTrace();
            return null;
        }
    }

}
