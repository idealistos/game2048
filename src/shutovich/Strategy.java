package shutovich;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by U on 12/12/2015.
 */
abstract class Strategy {

    static class Averager {
        double average;
        double tolerance;
        int count;
        
        Averager(List<Double> values) {
            count = values.size();
            double sum = 0.0;
            double sum2 = 0.0;
            for (Double value : values) {
                sum += value;
                sum2 += value * value;
            }
            average = sum / count;
            tolerance = Math.sqrt((sum2 - (sum * sum) / count) / (count * (count - 1)));
        }
        
        @Override
        public String toString() {
            return "" + average + " +/- " + tolerance;
        }
        
    }
    
    FallbackStrategy fallbackStrategy;
    Options options;
    GameField field;
    int turn = 0;

    Strategy(Options options, FallbackStrategy fallbackStrategy) {
        this.options = options;
        this.fallbackStrategy = fallbackStrategy;
        field = new GameField();
    }

    abstract Action chooseOptimalAction();

    boolean nextTurn() {
        GameField oldField = null;
        if (field.debug || fallbackStrategy != null) {
            oldField = new GameField(field);
        }
        Direction direction = chooseOptimalAction().direction;
        if (fallbackStrategy != null && field.getMaxValue() >= 8 && fallbackStrategy.needsFallback(field)) {
            Action bestAction = fallbackStrategy.chooseOptimalAction(oldField);
            direction = bestAction.direction;
            field = (direction == null)? field : bestAction.field;
        }
        if (field.debug) {
            // System.out.println(direction);
            if (direction == Direction.LEFT || direction == Direction.UP) {
                field.debug = true;
            }
            assert(direction == null || !field.equals(oldField));
            assert(oldField != null && (direction == null || field.equals(oldField.shift(direction))));
        }
        return (direction != null);
    }

    void simulate(GameField startingPosition, FieldSaver saver, int maxTurns) {
        field = (startingPosition == null)? new GameField() : new GameField(startingPosition);
        turn = 0;
        if (saver != null) {
            saver.reset();
        }
        while (turn < maxTurns || maxTurns < 0) {
            long position = field.lines;
            field.addRandomNumber();
            turn++;
            if (saver != null) {
                saver.checkAndWriteField(position, field.lines, turn);
            }
            if (!nextTurn()) {
                break;
            }
        }
        if (saver != null) {
            saver.close();
        }
        if (field.getMaxValue() >= 13) {
            System.out.println(field.toString() + "\n");
        }
    }
    
    void simulate(FieldSaver saver) {
        simulate(null, saver, -1);
    }
    
    double getPositionUnsafetyMeasure(long position, double tolerance, double predict, int maxTurns) {
        int batchSize = 20;
        List<Double> lastInvTurns = new ArrayList<>();
        while (true) {
            for (int i = 0; i < batchSize; i++) {
                simulate(new GameField(position), null, maxTurns);
                lastInvTurns.add((turn == maxTurns)? 0.0 : 1.0 / turn);
            }
            Averager averager = new Averager(lastInvTurns);
            if (averager.tolerance < tolerance) {
                Main.logger.debug("Count = " + lastInvTurns.size() + " average = " + averager.toString() + " predict = " + predict);
                return averager.average;
            }
        }
    }
    
    void savePositionUnsafetyMeasure(InputPositions usedPositions, Map<Long, Double> predicts, double predictThreshold,
            int maxTurns, double tolerance, String fileName) {
        try (Writer file = new BufferedWriter(new FileWriter(new File(fileName)))) {
            for (int i = 0; i < usedPositions.positions.size(); i++) {
                long position = usedPositions.positions.get(i);
                if (predicts.get(position) < predictThreshold) {
                    file.write(Long.toHexString(position) + "\t0.0\n");
                } else {
                    double measure = getPositionUnsafetyMeasure(position, tolerance, predicts.get(position), maxTurns);
                    file.write(Long.toHexString(position) + "\t" + measure + "\n");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    
}
