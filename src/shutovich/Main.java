package shutovich;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.LineBorder;

public class Main {

    static class Window extends JFrame {

        JTextArea[][] cells = new JTextArea[4][4];
        boolean running = false;

        void showField(GameField field) {
            int p = 0;
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    long value = (field.lines >>> p) & 0xFl;
                    cells[y][x].setText((value == 0)? "" : "" + (1 << value));
                    p += 4;
                }
            }
        }

        Window() {
            super("Demo");
            setBounds(100, 100, 500, 600);
            setLayout(new GridLayout(2, 1));
            JPanel cellPanel = new JPanel();
            cellPanel.setLayout(new GridLayout(4, 4));

            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    cells[y][x] = new JTextArea(1, 4);
                    cells[y][x].setSize(100, 100);
                    cells[y][x].setBorder(new LineBorder(Color.gray, 2));
                    cells[y][x].setFont(new Font("Verdana", 0, 50));
                    cellPanel.add(cells[y][x]);
                }
            }

            // showField(new GameField().addRandomNumber());
            add(cellPanel);
            add(new JButton() {{
                setText("Start");
                setSize(200, 100);
                addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (running) {
                            running = false;
                        } else {
                            running = true;
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    Options options = new Options();
                                    Strategy strategy = StrategyFactory.createStrategy(options);
                                    while (running) {
                                        try {
                                            strategy.nextTurn1();
                                            showField(strategy.getField());
                                            Thread.sleep(400);
                                            if (!strategy.nextTurn2()) {
                                                running = false;
                                            } else {
                                                showField(strategy.getField());
                                            }
                                            Thread.sleep(100);
                                        } catch (InterruptedException e) {
                                            // Empty
                                        }
                                    }
                                }
                            }).start();
                        }
                    }
                });
            }});
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent windowEvent) {
                    System.exit(0);
                }
            });
            setVisible(true);

        }
    }


    @SuppressWarnings("unused")
    public static void main(String[] args) {
        int mode = 1;
        // if (true) {
        if (mode == 0) {
            new Window();
        } else if (mode == 1) {
            Options options = new Options();
            Solver solver = new FittedPolySolver(options, true);
            FieldSaver saver = new FieldSaver("pos.1-50", true, false);
            for (int i = 0; i < 14; i++) {
                System.out.println("Average: " + String.format("%.2f", solver.getQuality(options, 1 << i, saver)));
            }
        } else if (mode == 2) {
            Options options = new Options();
            List<String> header = options.getHeader();
            double[] factors = new double[header.size()];
            double[] multipliers = {0.2, 1.0, 5.0};
            for (int i = 0; i < Math.pow(multipliers.length, factors.length); i++) {
                int n = i;
                for (int j = 0; j < factors.length; j++) {
                    factors[j] = multipliers[n % multipliers.length];
                    n /= multipliers.length;
                }
                Options options1 = options.scaleOptions(factors);
                for (int iTry = 0; iTry < 3; iTry++) {
                    double quality = new FittedPolySolver(options, true).getQuality(options1, 500, null);
                    System.out.println("" + quality + "\t" + options1.getValues().stream().collect(Collectors.joining("\t")));
                }
            }

        } else if (mode == 3) {
            Options options = new Options();
            Solver solver = new LoessSolver(options, true);
            // Options options1 = solver.optimizeInSequence(Arrays.asList(3, 1, 0, 2, 4, 5, 3, 1, 0, 2, 4, 5));
            Options options1 = solver.optimizeInSequence(Arrays.asList(6, 7, 3, 1, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7));
            System.out.println(options1.toString());
            System.out.println("Average: " + String.format("%.2f",
                    solver.getQuality(options1, 5000, new FieldSaver("", true, false))));
            solver.saveCache();
        } else if (mode == 4) {
            List<Entry<Long, Integer>> positions = new FieldSaver("pos.1-50.1", false, true).loadPositions();
            Map<Long, List<Double>> features = positions.stream()
                    .map(x -> new AbstractMap.SimpleEntry<>(x.getKey(), new GameField(x.getKey()).getFeatures()))
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (x1, x2) -> x1));
            new Classifier(10, "model-50.10.top", 0.5, Classifier.Mode.TOP_ONLY).trainModel(positions, features);
            new Classifier(5, "model-50.5.top", 0.3, Classifier.Mode.TOP_ONLY).trainModel(positions, features);
            new Classifier(10, "model-50.10.fair", 0.5, Classifier.Mode.FAIR).trainModel(positions, features);
            new Classifier(5, "model-50.5.fair", 0.3, Classifier.Mode.FAIR).trainModel(positions, features);
        } else if (mode == 5) {
            FieldSaver saver = new FieldSaver("d:\\programming\\java\\game2048\\saved.0", false, false);
            List<Entry<Long, Integer>> positions = saver.loadPositions();
            Map<Long, List<Double>> features = positions.stream()
                    .map(x -> new AbstractMap.SimpleEntry<>(x.getKey(), new GameField(x.getKey()).getFeatures()))
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (x1, x2) -> x1));
            Map<Long, Double> predict1 = Classifier.predict(new Classifier("model.5").loadModel(), features);
            Map<Long, Double> predict2 = Classifier.predict(new Classifier("model.10").loadModel(), features);
            saver.savePositionsWithValues(predict1, predict2);
        }
    }
}
