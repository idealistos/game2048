package shutovich;

import org.apache.commons.math3.analysis.interpolation.LoessInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.ml.clustering.DoublePoint;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by U on 1/3/2016.
 */
public class LoessSolver extends Solver {

    List<List<Map.Entry<Double, Double>>> dataToLog = new ArrayList<>();

    LoessSolver(Options options, boolean debug) {
        super(options, debug);
    }

    static PolynomialSplineFunction interpolate(List<Map.Entry<Double, Double>> data, BitSet omitted) {
        List<Map.Entry<Double, Double>> data1 = IntStream.range(0, data.size())
                .filter(i -> !omitted.get(i))
                .mapToObj(i -> data.get(i))
                .sorted((x, y) -> Double.compare(x.getKey(), y.getKey()))
                .collect(Collectors.toList());
        double epsilon = 1e-5;
        // int count = 0;
        List<Double> x = new ArrayList<>();
        List<Double> y = new ArrayList<>();
        for (int i = 0; i < data1.size(); i++) {
            int n = x.size();
            double x1 = data1.get(i).getKey();
            if (n == 0 || x1 > x.get(n - 1) + epsilon) {
                x.add(x1);
                y.add(data1.get(i).getValue());
                // count = 1;
            } else {
                // x.set(n - 1, (x.get(n - 1) * count + data1.get(i).getKey()) / (count + 1));
                // y.set(n - 1, (y.get(n - 1) * count + data1.get(i).getValue()) / (count + 1));
                // count++;
                x.add(x.get(n - 1) + epsilon);
                y.add(data1.get(i).getValue());
            }
        }
        LoessInterpolator interpolator = new LoessInterpolator(0.15, 4);
        return interpolator.interpolate(x.stream().mapToDouble(Double::doubleValue).toArray(),
                y.stream().mapToDouble(Double::doubleValue).toArray());
    }

    static double getOptimum(PolynomialSplineFunction function) {
        double[] knots = function.getKnots();
        double tOpt = 0.0;
        double fOpt = -1e100;
        for (int i = 0; i < 1000; i++) {
            double t = knots[0] + (knots[knots.length - 1] - knots[0]) * i / 999.0;
            double f = function.value(t);
            // System.out.println("t, f:" + t + ", " + f);
            if (f > fOpt) {
                fOpt = f;
                tOpt = t;
            }
        }
        return tOpt;
    }

    static void saveFunctionTable(PolynomialSplineFunction function, int pointCount, String fileName) {
        try {
            double[] knots = function.getKnots();
            Files.write(new File(fileName).toPath(), IntStream.range(0, pointCount)
                    .mapToDouble(i -> knots[0] + (knots[knots.length - 1] - knots[0]) * i / (pointCount - 1.0))
                    .mapToObj(x -> new AbstractMap.SimpleEntry<>(x, function.value(x)))
                    .map(p -> "" + p.getKey() + "\t" + p.getValue())
                    .collect(Collectors.toList()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void logActualData(List<Map.Entry<Double, Double>> data) {
        dataToLog.add(new ArrayList<>(data));
    }

    void logFittedData(List<Map.Entry<Double, Double>> data, PolynomialSplineFunction function) {
        List<Map.Entry<Double, Double>> points = new ArrayList<>();
        Double tLast = null;
        for (Map.Entry<Double, Double> entry : data) {
            double t = entry.getKey();
            if (tLast != null) {
                points.add(new AbstractMap.SimpleEntry<>((tLast + t) / 2, function.value((tLast + t) / 2)));
            }
            points.add(new AbstractMap.SimpleEntry<>(t, function.value(t)));
            tLast = t;
        }
        dataToLog.add(points);
   }

    void saveDataLog() {
        int i = 0;
        int writtenCount;
        List<String> lines = new ArrayList<>();
        do {
            writtenCount = 0;
            StringJoiner joiner = new StringJoiner("\t");
            for (int j = 0; j < dataToLog.size(); j++) {
                if (i < dataToLog.get(j).size()) {
                    writtenCount++;
                    joiner.add("" + dataToLog.get(j).get(i).getKey());
                    joiner.add("" + dataToLog.get(j).get(i).getValue());
                } else {
                    joiner.add("");
                    joiner.add("");
                }
            }
            if (writtenCount > 0) {
                lines.add(joiner.toString());
            }
            i++;
        } while (writtenCount != 0);
        try {
            Files.write(new File("data_log").toPath(), lines);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Saved " + i + " points to data_log");
    }

    @Override
    Options optimize(int iFactor) {
        int iterationCount = 1000;
        int initialTryCount = 75;
        int optimaTryCount = 20;
        int addCount = 10;

        System.out.println("Optimizing by " + iFactor);
        List<Map.Entry<Double, Double>> data = new ArrayList<>();
        for (int i = 0; i < initialTryCount; i++) {
            double t = random.nextDouble();
            double quality = getQuality(getModifiedOptions(iFactor, t), iterationCount, null);
            data.add(new AbstractMap.SimpleEntry<>(t, quality));
            System.out.println(i + ": " + t);
        }
        logActualData(data);
        saveCache();
        for (Map.Entry<Double, Double> entry : (Iterable<Map.Entry<Double, Double>>)(
                () -> data.stream().sorted((x, y) -> Double.compare(x.getKey(), y.getKey())).iterator())) {
            System.out.println("" + entry.getKey() + ": " + entry.getValue());
        }
        double tBestLast = 0.0;
        double bestValueLast = 0.0;
        // DBSCANClusterer<DoublePoint> clusterer = new DBSCANClusterer<>(0.1, 3);
        while (true) {
            List<Double> optima = new ArrayList<>();
            for (int i = 0; i < optimaTryCount; i++) {
                try {
                    // PolynomialSplineFunction function = interpolate(data, getRandomBitSet(data.size(), 0.5));
                    PolynomialSplineFunction function = LoessSolver.interpolate(data, new BitSet());
                    double optimum = LoessSolver.getOptimum(function);
                    System.out.println("  Optimum " + i + ": " + optimum + ", value: " + function.value(optimum));
                    double quality = getQuality(getModifiedOptions(iFactor, optimum), iterationCount, null);
                    data.add(new AbstractMap.SimpleEntry<>(optimum, quality));
                } catch (OutOfRangeException e) {

                }
            }
/*            if (optima.size() != optimaTryCount) {
                System.out.println("" + (optimaTryCount - optima.size()) + " optima skipped");
            }
            for (int i = 0; i < addCount; i++) {
                System.out.print("" + i + " ");
                double t = optima.get(random.nextInt(optima.size()));
                double quality = getQuality(getModifiedOptions(iFactor, t), iterationCount);
                data.add(new AbstractMap.SimpleEntry<>(t, quality));
            }
            System.out.println();
*/
            saveCache();
            PolynomialSplineFunction function = LoessSolver.interpolate(data, new BitSet());
            logActualData(data);
            logFittedData(data, function);
            saveDataLog();
            double tBest = LoessSolver.getOptimum(function);
            System.out.println("Calculating value (true)...");
            double bestValue = getQuality(getModifiedOptions(iFactor, tBest), iterationCount * 10, null);
            System.out.println("Optimum: " + tBest + ", value (expected): " + function.value(tBest)
                    + ", value (true): " + bestValue);

            if (bestValue < bestValueLast + 0.001) {
                System.out.println("Current optimum value (" + bestValue + ") is lower than the last optimum value ("
                        + bestValueLast + ")");
                return getModifiedOptions(iFactor, tBestLast);
            }
            tBestLast = tBest;
            bestValueLast = bestValue;
        }
    }

}