package com.ecoland;

import com.ecoland.entity.Entity;
import com.ecoland.entity.SpeciesType;
import com.ecoland.model.Tile;
import com.ecoland.simulation.Simulation;
import com.ecoland.ui.WorldRenderer;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

public class EcolandApplication extends Application {

    private static final int WINDOW_WIDTH = 1000;
    private static final int WINDOW_HEIGHT = 800;
    private static final int CANVAS_SIZE = 750; // Make canvas slightly smaller than window

    private Simulation simulation;
    private WorldRenderer renderer;
    private Canvas worldCanvas;
    private Label statsLabel;
    private Label inspectorLabel;
    private AnimationTimer gameLoop;
    private Stage primaryStage;

    private boolean isRunning = false;
    private long lastUpdate = 0; // For controlling update frequency
    private double targetUpdatesPerSecond = 10.0; // Initial speed

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        simulation = new Simulation(); // Use default size for now

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Center: World View Canvas
        worldCanvas = new Canvas(CANVAS_SIZE, CANVAS_SIZE);
        renderer = new WorldRenderer(worldCanvas, simulation.getWorld());
        root.setCenter(worldCanvas);

        // Right: Controls and Info Panel
        VBox rightPanel = createControlPanel(root);
        root.setRight(rightPanel);

        // Add event handler for inspector tool
        addInspectorHandler();

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);

        primaryStage.setTitle("Ecoland: AI Ecosystem Simulator");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> stopSimulation()); // Ensure timer stops on close
        primaryStage.show();

        setupGameLoop(root);
        // Start paused initially
        updateUI(); // Initial render
    }

    private VBox createControlPanel(BorderPane root) {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-border-color: lightgray; -fx-border-width: 1;");

        // --- Controls ---
        HBox controls = new HBox(10);
        Button startPauseButton = new Button("Start");
        startPauseButton.setOnAction(e -> toggleSimulation(root));
        Button stepButton = new Button("Step");
        stepButton.setOnAction(e -> {
            if (!isRunning) { // Only allow step when paused
                simulation.tick();
                updateUI();
            }
        });
        controls.getChildren().addAll(startPauseButton, stepButton);

        // --- Save Log Button ---
        Button saveLogButton = new Button("Save Log");
        saveLogButton.setOnAction(e -> saveSimulationLog());

        // --- Speed Slider ---
        HBox speedControl = new HBox(5);
        Label speedLabel = new Label("Speed:");
        Slider speedSlider = new Slider(1, 60, targetUpdatesPerSecond); // 1 to 60 TPS
        speedSlider.setShowTickLabels(true);
        speedSlider.setShowTickMarks(true);
        speedSlider.setMajorTickUnit(10);
        speedSlider.setBlockIncrement(5);
        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            targetUpdatesPerSecond = newVal.doubleValue();
            updateSpeedLabel(speedLabel, newVal.doubleValue());
        });
        updateSpeedLabel(speedLabel, targetUpdatesPerSecond);
        speedControl.getChildren().addAll(new Label("Sim Speed:"), speedSlider, speedLabel);
        HBox.setHgrow(speedSlider, Priority.ALWAYS);

        // --- Stats Panel ---
        statsLabel = new Label("Initializing stats...");
        statsLabel.setWrapText(true);
        statsLabel.setMinHeight(60);

        // --- Inspector Panel ---
        inspectorLabel = new Label("Click on map to inspect tile/entity.");
        inspectorLabel.setWrapText(true);
        inspectorLabel.setMinHeight(100);
        inspectorLabel.setStyle("-fx-border-color: gray; -fx-border-width: 1; -fx-padding: 5;");

        panel.getChildren().addAll(controls, saveLogButton, speedControl, new Label("\n--- Statistics ---"), statsLabel, new Label("\n--- Inspector ---"), inspectorLabel);

        return panel;
    }

    private void saveSimulationLog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Simulation Log");
        fileChooser.setInitialFileName("ecoland_log.csv");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File selectedFile = fileChooser.showSaveDialog(primaryStage);

        if (selectedFile != null) {
            try {
                simulation.getDataLogger().saveData(selectedFile.getAbsolutePath());
                System.out.println("Log saved to: " + selectedFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("Error saving log file: " + e.getMessage());
            }
        }
    }

    private void updateSpeedLabel(Label label, double value) {
         label.setText(String.format("%.1f tps", value));
    }

    private void addInspectorHandler() {
        worldCanvas.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            int[] coords = renderer.getTileCoordinates(event.getX(), event.getY());
            if (coords != null) {
                inspectTile(coords[0], coords[1]);
            }
        });
    }

    private void inspectTile(int x, int y) {
        Entity entity = simulation.getEntityManager().getEntityAt(x, y);
        Tile tile = simulation.getWorld().getTile(x, y);
        StringBuilder info = new StringBuilder();
        info.append(String.format("Tile (%d, %d):\n", x, y));
        if (tile != null) {
            info.append(String.format(" Type: %s\n", tile.getTerrainType()));
            info.append(String.format(" Food: %.2f\n", tile.getPlantFoodValue()));
            info.append(String.format(" Fertility: %.2f\n", tile.getFertility()));
            // Add more tile info (elevation, etc.) if needed
        } else {
             info.append(" Invalid Tile Data\n");
        }

        if (entity != null) {
            info.append(String.format("\nEntity: %s\n", entity.getSpeciesType()));
            info.append(String.format(" Energy: %.1f / %.1f\n", entity.getEnergy(), 100.0)); // Assuming max 100 for now
            info.append(String.format(" Health: %.1f\n", entity.getHealth()));
            // Add more entity info (state, target?) if needed
            // Example for Herbivore state (requires adding getter)
            // if (entity instanceof Herbivore) {
            //    info.append(String.format(" State: %s\n", ((Herbivore) entity).getCurrentState()));
            // }
        } else {
             info.append("\nNo entity on this tile.");
        }

        inspectorLabel.setText(info.toString());
    }

    private void setupGameLoop(BorderPane root) {
        gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!isRunning) {
                    lastUpdate = now; // Reset lastUpdate when paused
                    return;
                }

                double intervalNanos = 1_000_000_000.0 / targetUpdatesPerSecond;
                if (now - lastUpdate >= intervalNanos) {
                    simulation.tick();
                    updateUI();
                    lastUpdate = now;
                }
            }
        };
    }

    private void toggleSimulation(BorderPane root) {
        isRunning = !isRunning;
        Button startPauseButton = findStartPauseButton(root);
        if (startPauseButton != null) {
             startPauseButton.setText(isRunning ? "Pause" : "Start");
        }

        if (isRunning) {
            lastUpdate = System.nanoTime(); // Set initial time when starting
            gameLoop.start();
        } else {
            gameLoop.stop();
        }
    }

    private Button findStartPauseButton(BorderPane root) {
        try {
            VBox rightPanel = (VBox) root.getRight();
            HBox controls = (HBox) rightPanel.getChildren().get(0);
            return (Button) controls.getChildren().get(0);
        } catch (Exception e) {
            System.err.println("Could not find Start/Pause button for update.");
            return null;
        }
    }

    private void stopSimulation() {
        if (gameLoop != null) {
            gameLoop.stop();
        }
        isRunning = false;
        System.out.println("Simulation stopped.");
    }

    private void updateUI() {
        // Update statistics
        long tick = simulation.getCurrentTick();
        int herbivoreCount = simulation.getEntityManager().getPopulationCount(SpeciesType.HERBIVORE);
        int carnivoreCount = simulation.getEntityManager().getPopulationCount(SpeciesType.CARNIVORE);
        int totalPop = simulation.getEntityManager().getTotalPopulation();
        statsLabel.setText(String.format("Tick: %d\nTotal Pop: %d\nHerbivores: %d\nCarnivores: %d",
                tick, totalPop, herbivoreCount, carnivoreCount));

        // Re-render the world
        renderer.render(simulation.getEntityManager().getAllEntities());
    }

    public static void main(String[] args) {
        launch(args);
    }
} 