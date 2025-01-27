package shutovich;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.ArrayUtils;
import org.dmlc.xgboost4j.Booster;
import org.dmlc.xgboost4j.DMatrix;
import org.dmlc.xgboost4j.util.Trainer;
import org.dmlc.xgboost4j.util.XGBoostError;
import org.dmlc.xgboost4j.wrapper.XgboostJNI;

/**
 * Created by U on 1/25/2016.
 */
public class Classifier {
    
    enum Mode {
        TOP_ONLY, FAIR, EVEN;
    }
    
    int lastTurns;
    String fileName;
    Mode mode;
    boolean afterNewRandom;
    Map<String, Object> binaryBoosterParameters = new HashMap<String, Object>() {
        {
            put("eta", 0.3);
            put("silent", 1);
            put("objective", "binary:logistic");
            put("subsample", 0.8);
            put("eval_metric", "auc");
        }
    };
    Map<String, Object> regressionBoosterParameters = new HashMap<String, Object>() {
        {
            put("eta", 0.3);
            put("silent", 1);
            put("objective", "reg:logistic");
            put("subsample", 0.8);
            put("eval_metric", "rmse");
        }
    };
    Map<String, Object> boosterParameters;

    Classifier(int lastTurns, String fileName, Mode mode, int depth, boolean afterNewRandom) {
        this.lastTurns = lastTurns;
        this.fileName = fileName;
        this.mode = mode;
        this.afterNewRandom = afterNewRandom;
        boosterParameters = (lastTurns >= 0)? binaryBoosterParameters : regressionBoosterParameters;
        boosterParameters.put("max_depth", depth);
    }

    Classifier(String fileName) {
        this(-1, fileName, Mode.FAIR, 0, false);
    }

    static void printFeatureStrengths(Booster booster) throws XGBoostError {
        Map<String, Integer> featureScore = booster.getFeatureScore();
        List<String> featureNames = GameField.getFeatureNames();
        Map<String, String> renameMap = IntStream.range(0, featureNames.size()).mapToObj(j -> new SimpleEntry<>("f" + j, featureNames.get(j)))
                .collect(Collectors.<Entry<String, String>, String, String>toMap(Entry::getKey, Entry::getValue));
        assert(featureScore.size() <= featureNames.size());
        List<String> sortedFeatures = featureScore.entrySet().stream().sorted((x, y) -> Integer.compare(x.getValue(), y.getValue()))
                .map(x -> renameMap.get(x.getKey())).collect(Collectors.toList());
        Main.logger.debug("Best features: " + String.join(", ", sortedFeatures.subList(sortedFeatures.size() - 3,  sortedFeatures.size()))
                + ", worst features: " + String.join(", ", sortedFeatures.subList(0, 3)));
    }

    static Double getEvalError(String evalInfo, String name) {
        for (String part : evalInfo.split("\t")) {
            if (part.startsWith(name)) {
                return Double.parseDouble(part.substring(name.length() + 1));
            }
        }
        return -1e100;
    }
    
    static Booster train(Map<String, Object> params, DMatrix train, DMatrix test,
            int stopIfNoImprovementRounds) throws XGBoostError {
        
        String[] evalNames = { "train", "test" }; 
        DMatrix[] evalMats = { train, test };
        Booster booster = new Booster(params.entrySet(), evalMats);
        String metric = (String) params.get("eval_metric");
        double increasingFactor = metric.equals("auc")? -1.0 : 1.0;
        
        int iteration = 0;
        double bestError = 1e100;
        int bestIteration = -1;
        byte[] modelRaw = null;
        while (iteration < 1000) {
            booster.update(train, iteration);
            String evalInfo = booster.evalSet(evalMats, evalNames, iteration);
            Main.logger.debug(evalInfo);
            double error = Classifier.getEvalError(evalInfo, "test-" + metric) * increasingFactor;
            if (error < bestError) {
                bestIteration = iteration;
                bestError = error;
                modelRaw = booster.getModelRaw();
            } else if (iteration > bestIteration + stopIfNoImprovementRounds) {
                Main.logger.info("Stopping at " + iteration + " iterations, error = " + error
                        + ", best error: " + bestError + " at " + bestIteration);
                break;
            }
            iteration++;
        }
        booster = new Booster(params.entrySet(), modelRaw);
        String evalInfo = booster.evalSet(evalMats, evalNames, iteration);
        Main.logger.debug("Best evalInfo: " + evalInfo);
        Main.logger.debug("Best: " + Classifier.getEvalError(evalInfo, "test-" + metric));
        Classifier.printFeatureStrengths(booster);
        return booster;
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
    
    static DMatrix getMatrix(InputPositions inputPositions, List<Integer> indices, int lastTurns) throws XGBoostError {
        List<List<Double>> featuresList;
        if (indices == null) {
            featuresList = inputPositions.positions.stream()
                    .map(x -> inputPositions.features.get(x)).collect(Collectors.toList());
        } else {
            featuresList = indices.stream()
                    .map(i -> inputPositions.features.get(inputPositions.positions.get(i)))
                    .collect(Collectors.toList());
        }            
        DMatrix matrix = Classifier.getMatrix(featuresList);
        if (lastTurns >= 0) {
            float[] labels;
            if (indices == null) {
                labels = ArrayUtils.toPrimitive(inputPositions.turnsLeft.stream().map(x -> (x < lastTurns)? 1.0f : 0.0f)
                        .collect(Collectors.toList()).toArray(new Float[0]));
            } else {
                labels = ArrayUtils.toPrimitive(indices.stream().map(i -> (inputPositions.turnsLeft.get(i) < lastTurns)? 1.0f : 0.0f)
                        .collect(Collectors.toList()).toArray(new Float[0]));
            }
            matrix.setLabel(labels);
        } else if (inputPositions.measures != null) {
            float[] labels;
            if (indices == null) {
                labels = ArrayUtils.toPrimitive(inputPositions.measures.stream()
                        .collect(Collectors.toList()).toArray(new Float[0]));
            } else {
                labels = ArrayUtils.toPrimitive(indices.stream()
                        .map(i -> new Float(inputPositions.measures.get(i)))
                        .collect(Collectors.toList()).toArray(new Float[0]));
            }
            matrix.setLabel(labels);
        }
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
        // double[] safePredicts = IntStream.range(0, predicts.length).filter(i -> testLabels[i] == 0.0)
        //        .mapToDouble(i -> predicts[i][0]).sorted().toArray();
        // double threshold = safePredicts[95 * safePredicts.length / 100];
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
    
    static double getThresholdForMarkedPercent(float[][] predicts, List<Integer> turnsLeft, double percent) {
        List<Double> topPredicts = IntStream.range(0, turnsLeft.size()).filter(j -> turnsLeft.get(j) < FieldSaver.lastCount)
                .mapToObj(j -> new Double(-predicts[j][0])).sorted().collect(Collectors.toList());
        List<Double> nonTopPredicts = IntStream.range(0, turnsLeft.size()).filter(j -> turnsLeft.get(j) >= FieldSaver.lastCount)
                .mapToObj(j -> new Double(-predicts[j][0])).sorted().collect(Collectors.toList());
        double countToReach = percent * (topPredicts.size() * FieldSaver.p + nonTopPredicts.size());
        int iTop = 0;
        int iNonTop = 0;
        while (iTop < topPredicts.size() || iNonTop < nonTopPredicts.size()) {
            if (iTop < topPredicts.size() && topPredicts.get(iTop) < nonTopPredicts.get(iNonTop)) {
                iTop++;
            } else {
                iNonTop++;
            }
            if (iTop * FieldSaver.p + iNonTop >= countToReach) {
                // System.out.println("Top " + iTop + " of " + topPredicts.size());
                // System.out.println("Non-top " + iNonTop + " of " + nonTopPredicts.size());
                return Math.min((iTop < topPredicts.size())? -topPredicts.get(iTop) : 1e100,
                        (iNonTop < nonTopPredicts.size())? -nonTopPredicts.get(iNonTop) : 1e100);
            }
        }
        return 1.0;
    }
    
    static double getMissedForMarkedPercent(float[][] predicts, List<Integer> turnsLeft, double threshold) {
        int markedTopCount = (int) IntStream.range(0, turnsLeft.size())
                .filter(j -> turnsLeft.get(j) < FieldSaver.lastCount && predicts[j][0] >= threshold).count();
        int markedNonTopCount = (int) IntStream.range(0, turnsLeft.size())
                .filter(j -> turnsLeft.get(j) >= FieldSaver.lastCount && predicts[j][0] >= threshold).count();
        int topCount = (int) IntStream.range(0, turnsLeft.size())
                .filter(j -> turnsLeft.get(j) < FieldSaver.lastCount).count();
        int nonTopCount = (int) IntStream.range(0, turnsLeft.size())
                .filter(j -> turnsLeft.get(j) >= FieldSaver.lastCount).count();
        Main.logger.info("Top: marked " + markedTopCount + " of " + topCount);
        Main.logger.info("Non-top: marked " + markedNonTopCount + " of " + nonTopCount);
        double missedOverall = 0.0;
        for (int top : new int[] { 5, 10, 20 }) {
            int unsafeCount = (int) turnsLeft.stream().filter(x -> x < top).count();
            int markedUnsafeCount = (int) IntStream.range(0, turnsLeft.size()).filter(j -> turnsLeft.get(j) < top && predicts[j][0] >= threshold).count();
            Main.logger.info("Top " + top + ": unsafe: " + unsafeCount + " missed: " + (unsafeCount - markedUnsafeCount));
            missedOverall += 100.0 * (unsafeCount - markedUnsafeCount) / ((unsafeCount + 0.0) * top);
        }
        Main.logger.info("Overall missed percent: " + missedOverall);
        return missedOverall;
    }

    void saveThreshold(double threshold) {
        try {
            Files.write(new File(fileName + ".threshold").toPath(), Arrays.asList("" + threshold));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    void trainModel(DMatrix train, DMatrix test, DMatrix evaluation, List<Integer> evaluationTurnsLeft) throws XGBoostError {
        double minMissedOverall = 1e100;
        int bestRounds = 0;
        for (int rounds = 5; rounds <= 320; rounds *= 4) {
            Main.logger.info(toString() + ", rounds = " + rounds);
            Booster booster = Trainer.train(boosterParameters.entrySet(), train, rounds,
                    Arrays.asList(new SimpleEntry<>("train", train), new SimpleEntry<>("test", test)), null, null);
            Main.logger.debug("Trained");
            float[][] predicts = booster.predict(evaluation);
//                Classifier.checkPredictLowFP(predicts, noTrainLabels, noTrainTurnsLeft, Mode.FAIR);
//                Classifier.checkPredictLowTN(predicts, noTrainLabels, noTrainTurnsLeft, Mode.FAIR);
//                Classifier.checkPredictLowTN(predicts, noTrainLabels, noTrainTurnsLeft, Mode.TOP_ONLY);
//                Classifier.saveHistogram(predicts, noTrainTurnsLeft, "hist" + lastTurns + "." + i);
            double threshold = Classifier.getThresholdForMarkedPercent(predicts, evaluationTurnsLeft, 0.1);
            double missedOverall = Classifier.getMissedForMarkedPercent(predicts, evaluationTurnsLeft, threshold);
//                double fMeasure = getFMeasure(predicts, noTrainLabels, noTrainTurnsLeft, defaultThreshold, mode);
//                averageFMeasure += fMeasure / 5;
            if (missedOverall < minMissedOverall) {
                minMissedOverall = missedOverall;
                bestRounds = rounds;
                booster.saveModel(fileName);
                saveThreshold(threshold);
            }
        }
        System.out.println("Best rounds " + bestRounds + " for missed overall " + minMissedOverall);
    }
    
    void trainModelUsingTest(DMatrix train, DMatrix test, DMatrix evaluation, List<Integer> evaluationTurnsLeft) throws XGBoostError {
        Main.logger.info(toString());
        Booster booster = Classifier.train(boosterParameters, train, test, 20);
        Main.logger.debug("Trained");
        float[][] predicts = booster.predict(evaluation);
        double threshold = Classifier.getThresholdForMarkedPercent(predicts, evaluationTurnsLeft, 0.1);
        double missedOverall = Classifier.getMissedForMarkedPercent(predicts, evaluationTurnsLeft, threshold);
        booster.saveModel(fileName);
        saveThreshold(threshold);
    }
    
    void trainModel(InputPositions usedPositions, InputPositions evaluationPositions, boolean splitUsed) {
        try {
            DMatrix evaluation = Classifier.getMatrix(evaluationPositions, null, -1);
            for (int i = 0; i < 1; i++) {
                DMatrix train;
                DMatrix test;
                if (splitUsed) {
                    List<Integer> trainIndices = usedPositions.getRandomTrainIndices(i, mode, 0.8, 1.0);
                    List<Integer> testIndices = usedPositions.getRandomTestIndices(i, mode, 0.8, 1.0);
                    Main.logger.debug("Train: " + trainIndices.size() + ", test: " + testIndices.size());
                    train = Classifier.getMatrix(usedPositions, trainIndices, lastTurns);
                    test = Classifier.getMatrix(usedPositions, testIndices, lastTurns);
                } else {
                    List<Integer> trainIndices = IntStream.range(0, usedPositions.positions.size()).boxed().collect(Collectors.toList());
                    List<Integer> testIndices = evaluationPositions.getRandomTrainIndices(i, mode, 0.5, 0.0);
                    Main.logger.debug("Train: " + trainIndices.size() + ", test: " + testIndices.size());
                    train = Classifier.getMatrix(usedPositions, trainIndices, lastTurns);
                    test = Classifier.getMatrix(evaluationPositions, testIndices, lastTurns);
                }
                
                List<Integer> evaluationTurnsLeft = evaluationPositions.getTurnsLeft(null);
                trainModelUsingTest(train, test, evaluation, evaluationTurnsLeft);

            }
        } catch (XGBoostError e) {
            e.printStackTrace();
        }
    }
    
    

    Booster loadModel() {
        try {
            Main.logger.debug("Loading model " + fileName);
            return new Booster(boosterParameters.entrySet(), fileName);
        } catch (XGBoostError e) {
            e.printStackTrace();
            return null;
        }
    }

    void findThreshold(String inputFileName) {
        InputPositions inputPositions = new InputPositions(inputFileName, false, true);
        Booster booster = loadModel();
        try {
            System.out.println("Calculating predict");
            DMatrix complete = Classifier.getMatrix(inputPositions, null, -1);
            float[][] predicts = booster.predict(complete);
            List<Integer> turnsLeft = inputPositions.getTurnsLeft(null);
            double threshold = Classifier.getThresholdForMarkedPercent(predicts, turnsLeft, 0.1);
            saveThreshold(threshold);
            System.out.println("Model " + fileName + ", input " + inputFileName + ": threshold " + threshold);
            Classifier.getMissedForMarkedPercent(predicts, turnsLeft, threshold);
        } catch (XGBoostError e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "Model: " + fileName + ", depth: " + boosterParameters.get("max_depth")
                + ", lastTurns: " + lastTurns;
    }

}
