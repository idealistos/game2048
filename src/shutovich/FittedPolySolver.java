package shutovich;

import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;

import java.util.*;

/**
 * Created by U on 1/3/2016.
 */
public class FittedPolySolver extends Solver {

    FittedPolySolver(Options options) {
        super(options);
    }

    double[] getCoefficients(List<Map.Entry<Double, Double>> data, BitSet omitted) {
        WeightedObservedPoints points = new WeightedObservedPoints();
        for (int i = 0; i < data.size(); i++) {
            if (!omitted.get(i)) {
                Map.Entry<Double, Double> entry = data.get(i);
                points.add(entry.getKey(), entry.getValue());
            }
        }
        PolynomialCurveFitter fitter = PolynomialCurveFitter.create(2);
        return fitter.fit(points.toList());
    }

    double getOptimum(double[] coefficients) {
        assert(coefficients.length == 3);
        return -coefficients[1] / (2.0 * coefficients[2]);
    }

    Options optimize(int iFactor) {
        List<Map.Entry<Double, Double>> data = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            double t = random.nextDouble();
            double quality = getQuality(getModifiedOptions(iFactor, t), 1000, null);
            data.add(new AbstractMap.SimpleEntry<>(t, quality));
            System.out.println(i);
        }
        for (Map.Entry<Double, Double> entry : (Iterable<Map.Entry<Double, Double>>)(
                () -> data.stream().sorted((x, y) -> Double.compare(x.getKey(), y.getKey())).iterator())) {
            System.out.println("" + entry.getKey() + ": " + entry.getValue());
        }
        double tBest = 0.0;
        int optimaTryCount = 20;
        while (true) {
            double[] optima = new double[optimaTryCount];
            for (int i = 0; i < optimaTryCount; i++) {
                double[] coeffs0 = getCoefficients(data, new BitSet());
                tBest = getOptimum(coeffs0);
                break;
            }


            System.out.println("Optimal t: " + tBest);
            return getModifiedOptions(iFactor, tBest);
        }
    }
}