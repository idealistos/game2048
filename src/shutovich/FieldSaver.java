package shutovich;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Created by U on 1/21/2016.
 */
public class FieldSaver {
    static int lastCount = 50;
    static double p = 0.05;
    String fileName;
    Random random = new Random();
    int fieldIndex = random.nextInt();
    int requiredMaxValue = 8;
    long[] positionStack = new long[FieldSaver.lastCount];
    List<Entry<Long, Integer>> positions = new ArrayList<>();
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

    void writeLine(Writer file, long position, int turn, int lastTurn) {
        try {
            file.write("" + fieldIndex + "\t" + turn + "\t" + lastTurn + "\t" + Long.toHexString(position) + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void checkAndWriteField(GameField field, int turn) {
        if (field.getMaxValue() >= requiredMaxValue) {
            firstTurn = (firstTurn < 0)? turn : firstTurn;
            lastTurn = turn;
            positionStack[turn % FieldSaver.lastCount] = field.lines;
            if (random.nextDouble() < FieldSaver.p) {
                positions.add(new SimpleEntry<>(field.lines, turn));
            }
        }
    }

    void close() {
        try (BufferedWriter file = new BufferedWriter(new FileWriter(new File(fileName), true))) {
            for (Entry<Long, Integer> entry : positions) {
                if (entry.getValue() <= lastTurn - FieldSaver.lastCount) {
                    writeLine(file, entry.getKey(), entry.getValue(), lastTurn);
                }
            }
            if (firstTurn >= 0) {
                int turn1 = Math.max(firstTurn, lastTurn - FieldSaver.lastCount + 1);
                for (; turn1 <= lastTurn; turn1++) {
                    writeLine(file, positionStack[turn1 % FieldSaver.lastCount], turn1, lastTurn);
                }
            }
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
//                    .map(x -> new SimpleEntry<>(x.getKey(), (x.getValue() < 0) ? 10 : x.getValue()))
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
