package shutovich;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

/**
 * Created by U on 12/12/2015.
 */
public class GameField {
    // 4 bit for each cell, 64 bit total
    // Swap: renumbered vertically, pos = 0, 1,.., 4, 5,.. in lines <=> 0, 4,.., 1, 5,.. in swap
    // "Swap pos" = (pos % 4) * 4 + (pos / 4)
    long lines = 0;
    long swap = 0;
    Random random = new Random();
    boolean debug = false;


    GameField() { }

    GameField(GameField source) {
        lines = source.lines;
        swap = source.swap;
        random = source.random;
        debug = source.debug;
    }

    GameField(long lines) {
        this.lines = lines;
        this.swap = GameField.swapLines(lines);
    }

    void reset() {
        lines = 0;
        swap = 0;
    }

    int getEmptyCellCount() {
        int emptyCellCount = 0;
        for (int p = 0; p < 64; p += 4) {
            if ((lines & (0xfl << p)) == 0) {
                emptyCellCount++;
            }
        }
        return emptyCellCount;
    }

    GameField setCellValue(int p, long value) {
        lines |= (value << p);
        // iSwap = 4 * ((i / 4) % 4 * 4 + (i / 4) / 4)
        int pSwap = ((p << 2) & 0x30) | ((p >> 2) & 0xFC);
        if (debug) {
            int pSwap1 = 4 * (((p / 4) % 4) * 4 + (p / 4) / 4);
            assert(pSwap == pSwap1);
        }
        // System.out.println("" + i + ", " + iSwap1);
        swap |= (value << pSwap);
        return this;
    }

    long getCellValue(int x, int y) {
        int p = (y * 4 + x) << 2;
        return (lines >>> p) & 0xFl;
    }

    GameField setEmptyCellValue(int emptyCell, long value) {
        int emptyCellIndex = 0;
        for (int p = 0; p < 64; p += 4) {
            if ((lines & (0xFl << p)) == 0) {
                if (emptyCellIndex == emptyCell) {
                    setCellValue(p, value);
                    return this;
                }
                emptyCellIndex++;
            }
        }
        return this;
    }

    void addRandomNumberWithoutCheck() {
        int emptyCellCount = getEmptyCellCount();
        if (emptyCellCount > 0) {
            int emptyCell = random.nextInt(emptyCellCount);
            long value = (random.nextInt(10) == 0) ? 2l : 1l;
            setEmptyCellValue(emptyCell, value);
        }
    }

    void addRandomNumberWithCheck() {
        int emptyCellCount = getEmptyCellCount();
        long sum = getSum();
        addRandomNumberWithoutCheck();
        if (getEmptyCellCount() != emptyCellCount - 1 || Math.abs(getSum() - sum - 3) != 1) {
            System.out.println("Mismatch after adding: " + toString());
            System.out.println("Empty cell count changed from " + emptyCellCount + " to "
                    + getEmptyCellCount());
            System.out.println("Sum changed from " + sum + " to " + getSum());
            assert(false);
        }
    }

    GameField addRandomNumber() {
        if (debug) {
            addRandomNumberWithCheck();
        } else {
            addRandomNumberWithoutCheck();
        }
        return this;
    }

    long getMaxValue() {
        int p = 0;
        long maxValue = 0;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                maxValue = Math.max(maxValue, (lines >>> p) & 0xFl);
                p += 4;
            }
        }
        return maxValue;
    }

    long getSum() {
        int p = 0;
        long sum = 0;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                long value = (lines >>> p) & 0xFl;
                if (value > 0) {
                    sum += (1 << value);
                }
                p += 4;
            }
        }
        return sum;
    }

    static boolean isFixedLine(long data, int p) {
        int p1 = p + 12;
        long v = (data >>> p) & 0xFl;
        while (p < p1) {
            p += 4;
            long v1 = (data >>> p) & 0xFl;
            if ((v != 0 || v1 != 0) && (v == v1 || v == 0 || v1 == 0)) {
                return false;
            }
            v = v1;
        }
        return true;
    }

    static boolean canShiftLine(long data, int p, boolean increasing) {
        long v = (data >>> p) & 0xFl;
        for (int x = 1; x < 4; x++) {
            p += 4;
            long v1 = (data >>> p) & 0xFl;
            if (v != 0 && v1 == v) {
                return true;
            } else if (increasing && v != 0 && v1 == 0) {
                return true;
            } else if (!increasing && v == 0 && v1 != 0) {
                return true;
            }
            v = v1;
        }
        return false;
    }

    boolean canShift(Direction d) {
        long data = (d.swap)? swap : lines;
        for (int p = 0; p < 64; p += 16) {
            if (GameField.canShiftLine(data, p, d.increasing)) {
                return true;
            }
        }
        return false;
    }

    static long swapLines(long data) {
        // Line 1: 0, 4 - 1, 8 - 2, 12 - 3
        // Line 2: 1 - 4, 5 - 5, 9 - 6, 13 - 7
        // Line 3: 2 - 8, 6 - 9, 10 - 10, 14 - 11
        // Line 4: 3 - 12, 7 - 13, 11 - 14, 15 - 15
        return (data & 0xF0000F0000F0000Fl)
                | ((data & 0xF0) << 12) | ((data & 0xF00) << 24) | ((data & 0xF000) << 36)
                | ((data & 0xF0000) >>> 12) | ((data & 0xF000000) << 12) | ((data & 0xF0000000l) << 24)
                | ((data & 0xF00000000l) >>> 24) | ((data & 0xF000000000l) >>> 12)
                        | ((data & 0xF00000000000l) << 12)
                | ((data & 0xF000000000000l) >>> 36) | ((data & 0xF0000000000000l) >>> 24)
                        | ((data & 0xF00000000000000l) >>> 12);
    }

    void checkSwap() {
        long swap1 = GameField.swapLines(lines);
        long lines1 = GameField.swapLines(swap);
        long swap2 = GameField.swapLines(lines1);
        long lines2 = GameField.swapLines(swap1);
        System.out.println(Long.toHexString(lines) + ", " + Long.toHexString(lines1) + ", " + Long.toHexString(lines2));
        System.out.println(Long.toHexString(swap) + ", " + Long.toHexString(swap1) + ", " + Long.toHexString(swap2));
    }

    void shiftWithoutCheck(Direction d) {
        // System.out.println(d);
        // checkSwap();
        long data = (d.swap)? swap : lines;
        long newData = 0;
        int p = 0;
        while (p < 64) {
            if (d.increasing) {
                int i1 = p + 12;
                int iNew = p + 12;
                long vAdded = 0;
                while (i1 >= p) {
                    long v = (data >>> i1) & 0xFl;
                    if (v != 0) {
                        if (v == vAdded) {
                            newData ^= (v ^ (v + 1)) << (iNew + 4);
                            // System.out.println(Long.toHexString(newData) + ": " + v);
                            vAdded = 0;
                        } else {
                            newData |= v << iNew;
                            // System.out.println(Long.toHexString(newData) + ": " + v);
                            iNew -= 4;
                            vAdded = v;
                        }
                    }
                    i1 -= 4;
                }
                p += 16;
            } else {
                int iNew = p;
                int iMax = p + 16;
                long vAdded = 0;

                while (p < iMax) {
                    long v = (data >>> p) & 0xFl;
                    if (v != 0) {
                        if (v == vAdded) {
                            newData ^= (v ^ (v + 1)) << (iNew - 4);
                            vAdded = 0;
                        } else {
                            newData |= v << iNew;
                            iNew += 4;
                            vAdded = v;
                        }
                    }
                    p += 4;
                }
            }

        }
        if (d.swap) {
            swap = newData;
            lines = GameField.swapLines(newData);
        } else {
            lines = newData;
            swap = GameField.swapLines(newData);
        }
        // checkSwap();
    }

    void shiftWithCheck(Direction d) {
        long sum0 = getSum();
        shiftWithoutCheck(d);
        if (sum0 != getSum()) {
            System.out.print("Invariant changed after shift: " + toString());
            assert(false);
        }

    }

    GameField shift(Direction d) {
        if (debug) {
            shiftWithCheck(d);
        } else {
            shiftWithoutCheck(d);
        }
        return this;
    }

    static int getLineIncreasingMismatch(long data, int p) {
        // Adds penalty for each 2^i followed by 2^k if i > k; 0 counts as 2
        // Penalty: i * (i - k) if k >= 2, (3 / 2) * i * (i - 1) if k = 1
        long v = Math.max((data >>> p) & 0xFl, 1);
        int penalty = 0;
        for (int x = 1; x < 4; x++) {
            p += 4;
            long v1 = Math.max((data >>> p) & 0xFl, 1);
            if (v > v1) {
                penalty += (v1 == 1)? 3 * v * (v - 1) / 2 : v * (v - v1);
            }
            v = v1;
        }
        return penalty;
    }

    int getRightDownPenalty() {
        int penalty = 0;
        for (int p = 0; p < 64; p += 16) {
            penalty += GameField.getLineIncreasingMismatch(lines, p) + GameField.getLineIncreasingMismatch(swap, p);
        }
        return penalty;
    }

    static long getTrapPositionsMask(long data, boolean increasing) {
        // If adding a number X (X = 1 or 2) on position p leads to a "trap", set the corresponding bit in the mask to 1
        int pNotFixed = -1;
        for (int p = 0; p < 64; p += 16) {
            if (canShiftLine(data, p, increasing)) {
                if (pNotFixed >= 0) {
                    return 0;
                }
                pNotFixed = p;
            }
        }
        long mask = 0;
        if (pNotFixed < 0) {
            for (int p = 0; p < 64; p += 16) {
                if (increasing) {
                    if (((data >>> p) & 0xFl) == 0) {
                        int p1 = p + 4;
                        while (p1 < p + 16 && ((data >>> p1) & 0xFl) == 0) {
                            p1 += 4;
                        }
                        long v = 3;
                        if (p1 != p + 16) {
                            long v1 = (data >>> p1) & 0xFl;
                            v = (v1 == 1 || v1 == 2) ? 3 - v1 : v;
                        }
                        mask |= v << (p1 - 4);
                    }
                } else {
                    if (((data >>> (p + 12)) & 0xFl) == 0) {
                        int p1 = p + 8;
                        while (p1 >= p && ((data >>> p1) & 0xFl) == 0) {
                            p1 -= 4;
                        }
                        long v = 3;
                        if (p1 >= p) {
                            long v1 = (data >>> p1) & 0xFl;
                            v = (v1 == 1 || v1 == 2) ? 3 - v1 : v;
                        }
                        mask |= v << (p1 + 4);
                    }

                }
            }
        } else {
            long part = (data >>> pNotFixed) & 0xFFFFl;
            for (int p = 0; p < 16; p += 4) {
                for (long v = 1; v <= 2; v++) {
                    if ((part & (0xFl << p)) == 0 && !canShiftLine(part | (v << p), 0, increasing)) {
                        mask |= v << (p + pNotFixed);
                    }
                }
            }
        }
        return mask;
    }

    int getTrapPenalty() {
        // A position is a "trap" if there is an empty place that, when filled with 2 or 4,
        // leads to a position such that all possible moves will move the "corner" (down-right cell)
        long cornerValue = (lines >>> 60);
        long maskTotal = ~0l;
        if (cornerValue > 5) {
            boolean[] allowed = new boolean[2];
            for (int i = 0; i < 2; i++) {
                allowed[i] = GameField.isFixedLine((i == 0)? lines : swap, 48);
            }
            if (allowed[0] && allowed[1]) {
                return 0;
            }
            int iMask = 0;
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < (allowed[i]? 2 : 1); j++) {
                    long mask = GameField.getTrapPositionsMask((i == 0) ? lines : swap, j == 0);
                    mask = (i == 0)? mask : swapLines(mask);
                    maskTotal &= mask;
                    if (maskTotal == 0) {
                        return 0;
                    }
                }
            }
            return Long.bitCount(maskTotal & 0xAAAAAAAAAAAAAAAAl) + 10 * Long.bitCount(maskTotal & 0x5555555555555555l);
        }
        return 0;
    }

    int locateCorner() {
        int maxXY = 0;
        long maxValue = 0;
        int bestP = 0;
        int p = 0;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                long value = (lines >>> p) & 0xFl;
                if (value > maxValue + 1 || (value >= maxValue && x + y > maxXY)) {
                    maxXY = x + y;
                    maxValue = value;
                    bestP = p;
                }
                p += 4;
            }
        }
        return bestP;
    }

    boolean cornerMoves(Direction direction) {
        int p = locateCorner();
        if (((lines >>> p) & 0xFl) <= 5) {
            return false;
        } else if (p == 60) {
            if (direction == Direction.LEFT) {
                return !GameField.isFixedLine(lines, 48);
            } else if (direction == Direction.UP) {
                return !GameField.isFixedLine(swap, 48);
            } else {
                return false;
            }
        } else {
            return (new GameField(this).shift(direction).locateCorner() == 60);
        }
    }

    int getLargeNumbersCount() {
        int count = 0;
        for (int p = 0; p < 64; p += 4) {
            if (((lines >>> p) & 0xFl) >= 5) {
                count++;
            }
        }
        return count;
    }

    void load(int[][] data) {
        lines = 0;
        int p = 0;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                lines |= ((long)(data[y][x]) << p);
                p += 4;
            }
        }
        swap = swapLines(lines);
    }

    List<Double> getFeatures() {
        double[] penalties = { getRightDownPenalty(), getTrapPenalty(),
                getEmptyCellCount() * 0.1, getLargeNumbersCount() * 0.1 };
        List<Double> features = new ArrayList<>();
        features.addAll(DoubleStream.of(penalties).mapToObj(x -> new Double(x)).collect(Collectors.toList()));
        for (int i = 0; i < 4; i++) {
            features.add(0.1 * getCellValue(3 - i, 3));
            if (i != 0) {
                features.add(0.1 * getCellValue(3, 3 - i));
            }
        }
        features.add(getSum() * 0.01);
        return features;
    }

    Map<Direction, List<Map.Entry<Map<Direction, Long>, Double>>> getPositionsInDepth1(boolean consider4s) {
        Map<Direction, List<Entry<Map<Direction, Long>, Double>>> result = new HashMap<>();
        for (Direction direction : Direction.values()) {
            if (canShift(direction)) {
                GameField field = new GameField(this).shift(direction);
                long[] cellValues = consider4s? new long[] { 1, 2 } : new long[] { 1 };
                List<Entry<Map<Direction, Long>, Double>> positionsList = new ArrayList<>();
                for (int j = 0; j < cellValues.length; j++) {
                    double weight = consider4s ? ((j == 0) ? 0.9 : 0.1) : 1.0;
                    for (int i = 0; i < field.getEmptyCellCount(); i++) {
                        GameField field2 = new GameField(field).setEmptyCellValue(i, cellValues[j]);
                        Map<Direction, Long> positions = new HashMap<>();
                        for (Direction direction2 : Direction.values()) {
                            if (field2.canShift(direction2)) {
                                positions.put(direction2, new GameField(field2).shift(direction2).lines);
                            }
                        }
                        positionsList.add(new SimpleEntry<>(positions, weight));
                    }
                }
                result.put(direction, positionsList);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        int p = 0;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                long v = (lines >>> p) & 0xFl;
                s.append(((x == 0)? "" : ", ") + v);
                p += 4;
            }
            s.append("\n");
        }
        return s.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GameField)) {
            return false;
        }
        GameField field = (GameField) o;
        return field.lines == lines;
    }

}
