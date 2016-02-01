package shutovich;

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
        
        Indices(Classifier.Mode mode, double pTrain, double pTest) {
            Function<Integer, Double> probabilityFunction = (mode == Mode.FAIR)?
                    (j -> (positions.get(j).getValue() < FieldSaver.lastCount)? FieldSaver.p : 1.0)
                    : (j -> (positions.get(j).getValue() < FieldSaver.lastCount)? 1.0 : 0.0);
            trainIndices = IntStream.range(0, positions.size())
                        .filter(j -> random.nextDouble() < pTrain * probabilityFunction.apply(j))
                        .boxed().collect(Collectors.toList());
            trainIndexSet = new HashSet<>(trainIndices);
            testIndices = IntStream.range(0, positions.size())
                        .filter(j -> !trainIndexSet.contains(j) && random.nextDouble() < pTest * probabilityFunction.apply(j))
                        .boxed().collect(Collectors.toList());
            
        }
    }
    
    List<Entry<Long, Integer>> positions;
    Map<Long, List<Double>> features;
    List<Indices> indices = new ArrayList<>();
    Random random = new Random();

    InputPositions(String inputFileName) {
        Main.logger.debug("Loading " + inputFileName);
        positions = new FieldSaver(inputFileName, false, true).loadPositions();
        features = positions.stream()
                .map(x -> new AbstractMap.SimpleEntry<>(x.getKey(), new GameField(x.getKey()).getFeatures()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (x1, x2) -> x1));
        Main.logger.debug("Loaded " + inputFileName);
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
        if (positionIndices == null) {
            return positions.stream().map(Entry::getValue).collect(Collectors.toList());
        } else {
            return positionIndices.stream().map(j -> positions.get(j).getValue()).collect(Collectors.toList());
        }
    }
    
}
