package shutovich;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import shutovich.Classifier.Mode;

public class InputPositions {
    
    class Indices {
        List<Integer> trainIndices;
        Set<Integer> trainIndexSet;
        List<Integer> testIndices;
        
        Function<Integer, Double> getProbabilityFunction(Classifier.Mode mode) {
            if (mode == Mode.FAIR) {
                return (j -> (turnsLeft.get(j) < FieldSaver.lastCount)? FieldSaver.p : 1.0);
            } else if (mode == Mode.TOP_ONLY) {
                return (j -> (turnsLeft.get(j) < FieldSaver.lastCount)? 1.0 : 0.0);
            } else if (mode == Mode.EVEN) {
                return (j -> 1.0);
            } else {
                return null;
            }
        }
        
        Indices(Classifier.Mode mode, double pTrain, double pTest) {
            Function<Integer, Double> f = getProbabilityFunction(mode);
            trainIndices = IntStream.range(0, positions.size())
                        .filter(j -> random.nextDouble() < pTrain * f.apply(j))
                        .boxed().collect(Collectors.toList());
            trainIndexSet = new HashSet<>(trainIndices);
            testIndices = IntStream.range(0, positions.size())
                        .filter(j -> !trainIndexSet.contains(j) && random.nextDouble() < pTest * f.apply(j))
                        .boxed().collect(Collectors.toList());
            
        }
        
    }
    
    List<Long> positions = new ArrayList<>();
    List<Integer> turnsLeft;
    List<Double> measures;
    Map<Long, List<Double>> features;
    List<Indices> indices = new ArrayList<>();
    Random random = new Random();
    
    InputPositions(String inputFileName, boolean doubleValues, boolean computeFeatures) {
        Main.logger.debug("Loading " + inputFileName);
        if (doubleValues) {
            measures = new ArrayList<>();
        } else {
            turnsLeft = new ArrayList<>();
        }
        try (BufferedReader file = new BufferedReader(new FileReader(new File(inputFileName)))) {
            while (file.ready()) {
                String[] parts = file.readLine().split("\t");
                positions.add(new BigInteger(parts[doubleValues? 0 : 1], 16).longValue());
                if (doubleValues) {
                    measures.add(Double.parseDouble(parts[1]));
                } else {
                    turnsLeft.add(Integer.parseInt(parts[0]));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (computeFeatures) {
            features = positions.stream()
                    .map(x -> new AbstractMap.SimpleEntry<>(x, new GameField(x).getFeatures()))
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (x1, x2) -> x1));
        }
        Main.logger.debug("Loaded " + inputFileName);
    }
    
    static void checkAndSplit(String inputFileName, double saveRatio, double testRatio, String prefix, boolean afterNewRandom) {
        if (!new File(inputFileName + ".learn." + prefix).exists() || !new File(inputFileName + ".test-eval." + prefix).exists()) {
            Main.logger.debug("Splitting " + inputFileName);
            try (BufferedReader file = new BufferedReader(new FileReader(new File(inputFileName)));
                    Writer learnFile = new BufferedWriter(new FileWriter(new File(inputFileName + ".learn." + prefix)));
                    Writer testFile = new BufferedWriter(new FileWriter(new File(inputFileName + ".test-eval." + prefix)))) {
                int session = -1;
                boolean testSession = false;
                while (file.ready()) {
                    String line = file.readLine();
                    String[] parts = line.split("\t");
                    int session1 = Integer.parseInt(parts[0]);
                    if (session1 != session) {
                        testSession = GameField.random.nextDouble() < testRatio;
                        session = session1;
                    }
                    int turn = Integer.parseInt(parts[1]);
                    int lastTurn = Integer.parseInt(parts[2]);
                    double p = (testSession? 1.0 : saveRatio) * ((turn > lastTurn - FieldSaver.lastCount && !testSession)? FieldSaver.p : 1.0);
                    if (GameField.random.nextDouble() < p) {
                        (testSession? testFile : learnFile).write("" + (lastTurn - turn) + "\t" + parts[afterNewRandom? 4 : 3] + "\n");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    List<Integer> getRandomTrainIndices(int iTry, Classifier.Mode mode, double pTrain, double pTest) {
        while (iTry >= indices.size()) {
            indices.add(new Indices(mode, pTrain, pTest));
        }
        return indices.get(iTry).trainIndices;
    }
    
    List<Integer> getRandomTestIndices(int iTry, Classifier.Mode mode, double pTrain, double pTest) {
        while (iTry >= indices.size()) {
            indices.add(new Indices(mode, pTrain, pTest));
        }
        return indices.get(iTry).testIndices;
    }
    
    List<Integer> getTurnsLeft(List<Integer> positionIndices) {
        assert(positions != null);
        if (positionIndices == null) {
            return turnsLeft;
        } else {
            return positionIndices.stream().map(j -> turnsLeft.get(j)).collect(Collectors.toList());
        }
    }
    
}
