package shutovich;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Created by U on 1/21/2016.
 */
public class FieldSaver {
    
    static class Position {
        long position;
        long position1;
        int turn;
        
        Position(long position, long position1, int turn) {
            this.position = position;
            this.position1 = position1;
            this.turn = turn;            
        }
        
        String toString(int lastTurn) {
            return turn + "\t" + lastTurn + "\t" + Long.toHexString(position) + "\t" + Long.toHexString(position1); 
        }
    }
    
    static int lastCount = 50;
    static double p = 0.05;
    String fileName;
    Random random = new Random();
    int fieldIndex = random.nextInt();
    int requiredMaxValue = 8;
    Position[] positionStack = new Position[FieldSaver.lastCount];
    List<Position> positions = new ArrayList<>();
    int firstTurn;
    int lastTurn;


    FieldSaver(String fileName, boolean createNew, boolean exactName) {
        this.fileName = fileName;
        if (!exactName) {
            String baseFileName = fileName + ".";
            int i = 0;
            for (; ; i++) {
                if (!new File(baseFileName + i).exists()) {
                    break;
                }
            }
            this.fileName = (createNew || i == 0)? baseFileName + i : baseFileName + (i - 1);
        }
    }

    void reset() {
        fieldIndex++;
        positions.clear();
        firstTurn = -1;
        lastTurn = 0;
    }

    void checkAndWriteField(long position, long position1, int turn) {
        if (new GameField(position1).getMaxValue() >= requiredMaxValue) {
            firstTurn = (firstTurn < 0)? turn : firstTurn;
            lastTurn = turn;
            Position fieldPosition = new Position(position, position1, turn);
            positionStack[turn % FieldSaver.lastCount] = fieldPosition;
            if (random.nextDouble() < FieldSaver.p) {
                positions.add(fieldPosition);
            }
        }
    }

    void close() {
        try (BufferedWriter file = new BufferedWriter(new FileWriter(new File(fileName), true))) {
            for (Position position : positions) {
                if (position.turn <= lastTurn - FieldSaver.lastCount) {
                    file.write("" + fieldIndex + "\t" + position.toString(lastTurn) + "\n");
                }
            }
            if (firstTurn >= 0) {
                int turn1 = Math.max(firstTurn, lastTurn - FieldSaver.lastCount + 1);
                for (; turn1 <= lastTurn; turn1++) {
                    file.write("" + fieldIndex + "\t" + positionStack[turn1 % FieldSaver.lastCount].toString(lastTurn) + "\n");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SafeVarargs
    final void savePositionsWithValues(Map<Long, Double>... values) {
        try (BufferedWriter newFile = new BufferedWriter(new FileWriter(new File(fileName + ".with_values")))) {
            for (String s : Files.readAllLines(new File(fileName).toPath())) {
                List<String> parts = new ArrayList<>(Arrays.asList(s.split("\t")));
                Long position = new BigInteger(parts.get(3), 16).longValue();
                for (Map<Long, Double> values1 : values) {
                    parts.add(values1.get(position).toString());
                }
                newFile.write(String.join("\t", parts) + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
