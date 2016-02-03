package shutovich;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.AbstractMap.SimpleEntry;
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dmlc.xgboost4j.Booster;

import shutovich.Classifier.Mode;

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
                                            strategy.field.addRandomNumber();
                                            strategy.turn++;
                                            showField(strategy.field);
                                            Thread.sleep(400);
                                            if (!strategy.nextTurn()) {
                                                running = false;
                                            } else {
                                                showField(strategy.field);
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

    static Logger logger = LogManager.getLogger(Main.class);

    @SuppressWarnings("unused")
    static void mainPlaying() {
        int mode = 1;

        if (mode == 0) {
            new Window();
        } else if (mode == 1) {
            Options options = new Options();
            Solver solver = new FittedPolySolver(options, true);
            FieldSaver saver = new FieldSaver("data/input/pos.rec1p3", true, false);
            for (int i = 0; i < 16; i++) {
                System.out.println("Average: " + String.format("%.3f", solver.getQuality(options, 1 << i, saver)));
            }
        } else if (mode == 2) {
            InputPositions usedPositions = new InputPositions("data/input/pos.rec1.0.test-eval.1", false, true);
            Booster booster = new Classifier("data/models/model-rec1-d4-auc2.10.fair").loadModel();
            List<Integer> testIndices = usedPositions.getRandomTrainIndices(0, Mode.FAIR, 0.5, 0.0);
            Map<Long, Double> predicts = Classifier.predict(booster, testIndices.stream()
                    .map(j -> new SimpleEntry<>(new Long(j),
                            usedPositions.features.get(usedPositions.positions.get(j))))
                    .collect(Collectors.<Entry<Long, List<Double>>, Long, List<Double>>toMap(
                            Entry::getKey, Entry::getValue, (x1, x2) -> x1)));
            Strategy strategy = StrategyFactory.createStrategy(new Options());
            for (int i : testIndices) {
                strategy.getPositionUnsafetyMeasure(usedPositions.positions.get(i), 0.002,
                        predicts.get(new Long(i)), 200);
            }
        } else if (mode == 3) {
            InputPositions.checkAndSplit("data/input/pos.rec1.0", 0.05, 0.05, "2", false);
            InputPositions usedPositions = new InputPositions("data/input/pos.rec1.0.learn.2", false, true);
            Booster booster = new Classifier("data/models/model-rec1-d4-auc2.10.fair").loadModel();
            Map<Long, Double> predicts = Classifier.predict(booster, usedPositions.features);
            Strategy strategy = StrategyFactory.createStrategy(new Options());
            Main.logger.debug("Positions to evaluate: " + usedPositions.positions.size());
            strategy.savePositionUnsafetyMeasure(usedPositions, predicts, 0.005, 50, 0.01,
                    "data/input/pos-measure.rec1");
        } 
    }
    
    static void mainOptimizing() {
        int mode = 1;
        
        if (mode == 0) {
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
        } else if (mode == 1) {
            Options options = new Options();
            Solver solver = new LoessSolver(options, true);
            // Options options1 = solver.optimizeInSequence(Arrays.asList(3, 1, 0, 2, 4, 5, 3, 1, 0, 2, 4, 5));
            Options options1 = solver.optimizeInSequence(Arrays.asList(6, 7, 3, 1, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7));
            System.out.println(options1.toString());
            System.out.println("Average: " + String.format("%.2f",
                    solver.getQuality(options1, 5000, new FieldSaver("", true, false))));
            solver.saveCache();
        }        
    }
    
    static void mainLearning() {
        int mode = 1;
        
        if (mode == 0) {
            InputPositions.checkAndSplit("data/input/pos.rec1.0", 0.1, 0.05, "1", false);
            InputPositions usedPositions = new InputPositions("data/input/pos.rec1.0.learn.1", false, true);
            InputPositions evaluationPositions = new InputPositions("data/input/pos.rec1.0.test-eval.1", false, true);

            // new Classifier(10, "model-50.10.top", 0.5, Classifier.Mode.TOP_ONLY).trainModel(positions, features);
            // new Classifier(5, "model-50.5.top", 0.3, Classifier.Mode.TOP_ONLY).trainModel(positions, features);
            for (int d : new int[] { 2, 3, 4, 5 }) {
                new Classifier(10, "data/models/model-rec1-d" + d + "-auc2.10.fair", Classifier.Mode.FAIR, d, false)
                        .trainModel(usedPositions, evaluationPositions, false);
            }
        } else if (mode == 1) {
            InputPositions usedPositions = new InputPositions("data/input/pos-measure.rec1", true, true);
            InputPositions evaluationPositions = new InputPositions("data/input/pos.rec1.0.test-eval.2", false, true);
            for (int d : new int[] { 3, 5, 7, 8, 9 }) {
                new Classifier(-1, "data/models/model-avg-rec1-d" + d + ".logit.fair", Classifier.Mode.EVEN, d, false)
                        .trainModel(usedPositions, evaluationPositions, true);
            }
        } else if (mode == 2) {
            InputPositions inputPositions = new InputPositions("saved.0", false, true);
            Map<Long, Double> predict1 = Classifier.predict(new Classifier("model.5").loadModel(), inputPositions.features);
            Map<Long, Double> predict2 = Classifier.predict(new Classifier("model.10").loadModel(), inputPositions.features);
            new FieldSaver("saved.0", false, true).savePositionsWithValues(predict1, predict2);
        } else if (mode == 2) {
            String[] models = { "model.10", "model.5", "model-50.10", "model-50.10.top", "model-50.5", "model-50.5.fair", "model-50.5.top" };
            String[] inputs = { "pos.1-50.1", "pos.1p3m5-50.1" };
            // String[] inputs = { "pos.1-50.0", "pos.1p3m5-50.0" };
            for (String model : models) {
                for (String input : inputs) {
                    new Classifier(model).findThreshold(input);
                }
            }
        }
    }        
    

    public static void main(String[] args) {
        Main.logger.info("\n\n");
        Main.mainPlaying();
        // Main.mainOptimizing();
        // Main.mainLearning();
    }
    
}
