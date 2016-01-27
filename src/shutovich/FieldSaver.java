package shutovich;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.stream.Collectors;

/**
 * Created by U on 1/21/2016.
 */
public class FieldSaver {
    String fileName;
    BufferedWriter file;
    Random random = new Random();
    int fieldIndex = random.nextInt();
    static int lastCount = 10;
    int requiredMaxValue = 9;
    double p = 0.01;
    long[] positionStack = new long[FieldSaver.lastCount];
    long lastSavedPosition;
    int lastSavedTurn;
    int lastTurn;


    FieldSaver(String fileName, boolean createNew) {
        if (!fileName.isEmpty()) {
            this.fileName = fileName;
        } else {
            String baseFileName = "d:\\programming\\java\\game2048\\saved.";
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
        lastSavedTurn = -1;
        try {
            file = new BufferedWriter(new FileWriter(new File(fileName), true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void writeLine(long position, int turn, int lastTurn) {
        try {
            file.write("" + fieldIndex + "\t" + turn + "\t" + lastTurn + "\t" + Long.toHexString(position) + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void checkAndWriteField(GameField field, int turn) {
        positionStack[turn % FieldSaver.lastCount] = field.lines;
        if (field.getMaxValue() >= requiredMaxValue && random.nextDouble() < p) {
            if (lastSavedTurn >= 0 && lastSavedTurn < turn - FieldSaver.lastCount) {
                writeLine(lastSavedPosition, lastSavedTurn, 0);
            }
            lastSavedTurn = turn;
            lastSavedPosition = field.lines;
        }
        lastTurn = turn;
    }

    void close() {
        if (lastSavedTurn >= 0 && lastSavedTurn < lastTurn - FieldSaver.lastCount) {
            writeLine(lastSavedPosition, lastSavedTurn, 0);
        }
        for (int turn = lastTurn - FieldSaver.lastCount + 1; turn <= lastTurn; turn++) {
            writeLine(positionStack[turn % FieldSaver.lastCount], turn, lastTurn);
        }
        try {
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    List<Map.Entry<Long, Integer>> loadPositions() {
        try {
            return Files.readAllLines(new File(fileName).toPath())
                    .stream().map(s -> s.split("\t"))
                    .map(x -> new SimpleEntry<>(new BigInteger(x[3], 16).longValue(),
                            Integer.parseInt(x[2]) - Integer.parseInt(x[1])))
                    .map(x -> new SimpleEntry<>(x.getKey(), (x.getValue() < 0) ? 10 : x.getValue()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
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
