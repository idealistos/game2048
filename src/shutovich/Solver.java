package shutovich;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Created by U on 1/3/2016.
 */
public abstract class Solver {

    Options defaultOptions;
    boolean debug;
    double minFactor = 0.01;
    double maxFactor = 100.0;
    List<String> header;
    Map<String, List<Double>> cache;
    Map<String, Integer> usedCounts;
    Random random = new Random();

    Solver(Options defaultOptions, boolean debug) {
        this.defaultOptions = defaultOptions;
        this.debug = debug;
        header = defaultOptions.getHeader();
        random.setSeed(1L);
        try {
            cache = Files.readAllLines(new File("cache").toPath()).stream().map(s -> s.split("\t"))
                    .collect(Collectors.groupingBy(x -> x[0],
                            Collectors.mapping(x -> Double.parseDouble(x[1]), Collectors.toList())));
            usedCounts = cache.keySet().stream().collect(Collectors.toMap(s -> s, s -> 0));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    BitSet getRandomBitSet(int size, double ratio) {
        BitSet result = new BitSet(size);
        for (int i = 0; i < size * ratio; i++) {
            while (true) {
                int j = random.nextInt(size);
                if (!result.get(j)) {
                    result.set(j);
                    break;
                }
            }
        }
        return result;
    }

    void addToCache(Options options, double value) {
        String key = options.toString();
        List<Double> values = cache.get(key);
        if (values != null) {
            values.add(value);
            usedCounts.put(key, values.size());
        } else {
            cache.put(key, new ArrayList<>(Arrays.asList(value)));
            usedCounts.put(key, 1);
        }
    }

    void saveCache() {
        try {
            Files.write(new File("cache").toPath(), (Iterable<String>) () -> (cache.entrySet().stream()
                    .<String>flatMap(x -> x.getValue().stream().map(x1 -> x.getKey() + "\t" + x1)).iterator()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    Options getModifiedOptions(int iFactor, double t) {
        double[] factors = Collections.nCopies(header.size(), 1.0).stream().mapToDouble(x -> x).toArray();
        factors[iFactor] = Math.exp(Math.log(minFactor) + t * Math.log(maxFactor / minFactor));
        return defaultOptions.scaleOptions(factors);
    }

    double getQuality(Options options, int tryCount, FieldSaver saver) {
        long time = debug? System.nanoTime() : 0;
        String key = options.toString();
        List<Double> values = cache.get(key);
        if (values != null) {
            Integer count = usedCounts.get(key);
            if (count < values.size()) {
                usedCounts.put(key, count + 1);
                return values.get(count);
            }
        }
        Strategy strategy = StrategyFactory.createStrategy(options);
        int[] maxCounts = new int[16];
        for (int p = 0; p < tryCount; p++) {
            if (debug && p % 100 == 0) {
                System.out.print("" + p + " ");
            }
            int maxValue = strategy.simulate(saver);
            maxCounts[maxValue]++;
        }
        int allSum = 0;
        int allCounts = 0;
        for (int p = 1; p < 16; p++) {
            if (debug) {
                System.out.println("" + p + ": " + maxCounts[p]);
            }
            allSum += p * maxCounts[p];
            allCounts += maxCounts[p];
        }
        double value = allSum / (allCounts + 0.0);
        addToCache(options, value);
        if (debug) {
            System.out.println("" + value + " in " + (System.nanoTime() - time) / (tryCount * 1e9) + " s ["
                    + tryCount + "]");
        }
        return value;
    }

    abstract Options optimize(int iFactor);

    Options optimizeInSequence(List<Integer> factorSequence) {
        for (int iFactor : factorSequence) {
            Options options = optimize(iFactor);
            defaultOptions = options;
        }
        return defaultOptions;
    }

}
