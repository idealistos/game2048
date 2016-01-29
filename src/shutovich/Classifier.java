package shutovich;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.AbstractMap.SimpleEntry;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
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
    
    enum Mode {
        TOP_ONLY, FAIR;
    }
    
    int lastTurns;
    String fileName;
    double defaultThreshold;
    Mode mode;
    List<Entry<String, Object>> boosterParameters = new ArrayList<Entry<String, Object>>() {
        {
            add(new SimpleEntry<>("eta", 1.0));
            add(new SimpleEntry<>("max_depth", 5));
            add(new SimpleEntry<>("silent", 1));
            add(new SimpleEntry<>("objective", "binary:logistic"));
        }
    };

    Classifier(int lastTurns, String fileName, double defaultThreshold, Mode mode) {
        this.lastTurns = lastTurns;
        this.fileName = fileName;
        this.defaultThreshold = defaultThreshold;
        this.mode = mode;
    }

    Classifier(String fileName) {
        this(-1, fileName, 0.0, Mode.FAIR);
    }

    static DMatrix getMatrix(List<List<Double>> features) throws XGBoostError {
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
        return new DMatrix(data, features.size(), columnCount);
    }
    
    static DMatrix getMatrix(List<Entry<Long, Integer>> positions, Map<Long, List<Double>> features,
                             List<Integer> indices, int lastTurns) throws XGBoostError {
        List<List<Double>> featuresList = indices.stream().map(i -> features.get(positions.get(i).getKey())).collect(Collectors.toList());
        float[] labels = ArrayUtils.toPrimitive(indices.stream().map(i -> (positions.get(i).getValue() < lastTurns)? 1.0f : 0.0f)
                .collect(Collectors.toList()).toArray(new Float[0]));
        DMatrix matrix = Classifier.getMatrix(featuresList);
        matrix.setLabel(labels);
        return matrix;
    }

    static Map<Long, Double> predict(Booster booster, Map<Long, List<Double>> features) {
        List<Entry<Long, List<Double>>> entries = new ArrayList<>(features.entrySet());
        try {
            DMatrix matrix = Classifier.getMatrix(entries.stream().map(Entry::getValue).collect(Collectors.toList()));
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
        try {
            DMatrix matrix = Classifier.getMatrix(entries.stream().map(Entry::getValue).collect(Collectors.toList()));
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

    static double getFMeasure(float[][] predicts, float[] testLabels, List<Integer> turnsLeft, double threshold, Mode mode) {
        double correct = 0.0;
        double unsafe = 0.0;
        double marked = 0.0;
        for (int i = 0; i < predicts.length; i++) {
            boolean inTop = turnsLeft.get(i) < FieldSaver.lastCount;
            double weight = (mode == Mode.FAIR)? (inTop? FieldSaver.p : 1.0) : (inTop? 1.0 : 0.0);
            correct += weight * ((predicts[i][0] >= threshold && testLabels[i] == 1.0)? 1.0 : 0.0);
            unsafe += weight * ((testLabels[i] == 1.0f)? 1.0 : 0.0);
            marked += weight * ((predicts[i][0] >= threshold)? 1.0 : 0.0);
        }
        double fMeasure = 2.0 * correct/ (marked + unsafe);
        System.out.println("Threshold " + threshold + ": correct " + correct + ", all marked: " + marked
                + ", all unsafe: " + unsafe + ", f: " + fMeasure);
        return fMeasure;
    }

    static void checkPredictLowFP(float[][] predicts, float[] testLabels) {
        assert(predicts.length == testLabels.length);
        double[] safePredicts = IntStream.range(0, predicts.length).filter(i -> testLabels[i] == 0.0)
                .mapToDouble(i -> predicts[i][0]).sorted().toArray();
        double threshold = safePredicts[95 * safePredicts.length / 100];
        // Classifier.getFMeasure(predicts, testLabels, threshold, testLabels.length - safePredicts.length);
    }

    static void checkPredictLowTN(float[][] predicts, float[] testLabels, List<Integer> turnsLeft, Mode mode) {
        assert(predicts.length == testLabels.length);
        double[] unsafePredicts = IntStream.range(0, predicts.length).filter(i -> testLabels[i] == 1.0 && turnsLeft.get(i) < FieldSaver.lastCount)
                .mapToDouble(i -> predicts[i][0]).sorted().toArray();
        double threshold = unsafePredicts[2 * unsafePredicts.length / 100];
        Classifier.getFMeasure(predicts, testLabels, turnsLeft, threshold, mode);
    }
    
    static void saveHistogram(float[][] predicts, List<Integer> turnsLeft, String fileName) {
        double delta = 0.05;
        int lineValues = (int)(1.00001 / delta) + 2;
        int[][] counts = new int[10000][lineValues];
        int maxTurnsLeft = 0;
        for (int i = 0; i < turnsLeft.size(); i++) {
           int pos = (predicts[i][0] < 0)? 0 : Math.min((int)(predicts[i][0] / delta), lineValues - 2) + 1;
           maxTurnsLeft = Math.max(maxTurnsLeft, turnsLeft.get(i));
           counts[turnsLeft.get(i)][pos]++;
        }
        try (Writer file = new BufferedWriter(new FileWriter(new File(fileName)))) {
            for (int i = 0; i <= maxTurnsLeft; i++) {
                int[] counts1 = counts[i];
                file.write("" + i + "\t" + String.join("\t",
                        (Iterable<String>)(() -> IntStream.of(counts1).mapToObj(x -> new Integer(x).toString()).iterator())) + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    void trainModel(List<Entry<Long, Integer>> positions, Map<Long, List<Double>> features) {
        int trainLinesCount = 1000000;
        int testLinesCount = 200000;
        Random random = new Random(2l);
        double pTrain = trainLinesCount / (positions.size() + 0.0);
        double pTest = testLinesCount / (positions.size() - trainLinesCount + 0.0);
        double averageFMeasure = 0.0;
        for (int i = 0; i < 5; i++) {
            System.out.println("Try " + i + " for lastTurns = " + lastTurns);
            Function<Integer, Double> probabilityFunction = (mode == Mode.FAIR)?
                    (j -> (positions.get(j).getValue() < FieldSaver.lastCount)? FieldSaver.p : 1.0)
                    : (j -> (positions.get(j).getValue() < FieldSaver.lastCount)? 1.0 : 0.0);
            List<Integer> trainIndices = IntStream.range(0, positions.size())
                        .filter(j -> random.nextDouble() < pTrain * probabilityFunction.apply(j))
                        .boxed().collect(Collectors.toList());
            Set<Integer> trainIndexSet = new HashSet<>(trainIndices);
            List<Integer> testIndices = IntStream.range(0, positions.size())
                        .filter(j -> !trainIndexSet.contains(j) && random.nextDouble() < pTest * probabilityFunction.apply(j))
                        .boxed().collect(Collectors.toList());
            List<Integer> noTrainIndices = IntStream.range(0, positions.size()).filter(x -> !trainIndexSet.contains(x))
                    .boxed().collect(Collectors.toList());
            try {
                DMatrix train = Classifier.getMatrix(positions, features, trainIndices, lastTurns);
                DMatrix test = Classifier.getMatrix(positions, features, testIndices, lastTurns);

                System.out.println("Train: " + trainIndices.size() + ", test: " + testIndices.size() + ", no train: " + noTrainIndices.size());
                Booster booster = Trainer.train(boosterParameters, train, 500,
                        Arrays.asList(new SimpleEntry<>("train", train), new SimpleEntry<>("test", test)), null, null);
                System.out.println("Trained");
                DMatrix noTrain = Classifier.getMatrix(positions, features, noTrainIndices, lastTurns);
                float[][] predicts = booster.predict(noTrain);
                float[] noTrainLabels = noTrain.getLabel();
                List<Integer> noTrainTurnsLeft = noTrainIndices.stream().map(j -> positions.get(j).getValue()).collect(Collectors.toList()); 
//                Classifier.checkPredictLowFP(predicts, noTrainLabels, noTrainTurnsLeft, Mode.FAIR);
                Classifier.checkPredictLowTN(predicts, noTrainLabels, noTrainTurnsLeft, Mode.FAIR);
                Classifier.checkPredictLowTN(predicts, noTrainLabels, noTrainTurnsLeft, Mode.TOP_ONLY);
                Classifier.saveHistogram(predicts, noTrainTurnsLeft, "hist" + lastTurns + "." + i);
                int unsafeCount = (int) IntStream.range(0, noTrainLabels.length).filter(j -> noTrainLabels[j] == 1.0f).count();
                double fMeasure = getFMeasure(predicts, noTrainLabels, noTrainTurnsLeft, defaultThreshold, mode);
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
