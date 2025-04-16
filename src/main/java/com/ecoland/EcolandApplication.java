package com.ecoland;

import com.ecoland.entity.Entity;
import com.ecoland.entity.Herbivore;
import com.ecoland.entity.Carnivore;
import com.ecoland.entity.SpeciesType;
import com.ecoland.entity.ApexPredator;
import com.ecoland.entity.Omnivore;
import com.ecoland.entity.Decomposer;
import com.ecoland.model.TerrainType;
import com.ecoland.model.Tile;
import com.ecoland.model.World;
import com.ecoland.simulation.EntityManager;
import com.ecoland.simulation.Simulation;
import com.ecoland.simulation.Simulation.SimulationState;
import com.ecoland.ui.WorldRenderer;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.shape.Rectangle;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EcolandApplication extends Application {

    private static final int WINDOW_WIDTH = 1200;
    private static final int WINDOW_HEIGHT = 800;
    private static final int CANVAS_SIZE = 750; // Make canvas slightly smaller than window

    private Simulation simulation;
    private WorldRenderer renderer;
    private Canvas worldCanvas;
    private Label statsLabel;
    private Label inspectorLabel;
    private AnimationTimer gameLoop;
    private Stage primaryStage;
    
    // UI Charts for statistics
    private VBox populationChartPane;
    
    // Simulation control parameters
    private boolean isRunning = false;
    private long lastUpdate = 0; // For controlling update frequency
    private double targetUpdatesPerSecond = 10.0; // Initial speed
    
    // Zoom and pan controls
    private double zoomLevel = 1.0;
    private double viewportX = 0.0;
    private double viewportY = 0.0;
    private boolean isPanning = false;
    private double lastMouseX = 0;
    private double lastMouseY = 0;
    
    // Entity placement tool
    private enum PlacementTool {
        NONE, HERBIVORE, CARNIVORE, PLANT, FOOD, DECOMPOSER, APEX_PREDATOR, OMNIVORE
    }
    private PlacementTool currentTool = PlacementTool.NONE;
    private ToggleGroup toolToggleGroup;
    
    // Initial simulation parameters
    private int initialHerbivoreCount = 50;
    private int initialCarnivoreCount = 5;
    private int initialDecomposerCount = 5;
    private int initialApexPredatorCount = 2;
    private int initialOmnivoreCount = 5;
    private int worldWidth = 100;
    private int worldHeight = 100;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        
        // Show initial setup dialog
        if (showSetupDialog()) {
            // Create simulation with chosen parameters
            simulation = new Simulation(worldWidth, worldHeight, initialHerbivoreCount, initialCarnivoreCount, 
                         initialOmnivoreCount, 0, initialApexPredatorCount, initialDecomposerCount, null);
            
            BorderPane root = new BorderPane();
            root.setPadding(new Insets(10));
            
            // Center: World View Canvas with scrolling container
            StackPane canvasContainer = new StackPane();
            canvasContainer.setStyle("-fx-background-color: #1a1a1a;");
            worldCanvas = new Canvas(CANVAS_SIZE, CANVAS_SIZE);
            renderer = new WorldRenderer(worldCanvas, simulation.getWorld());
            canvasContainer.getChildren().add(worldCanvas);
            
            // Add scroll pane to support larger worlds with pan/zoom
            ScrollPane scrollPane = new ScrollPane(canvasContainer);
            scrollPane.setPannable(false); // Disable built-in panning, we'll handle it ourselves
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);
            root.setCenter(scrollPane);
            
            // Right: Controls and Info Panel
            VBox rightPanel = createControlPanel();
            ScrollPane rightScrollPane = new ScrollPane(rightPanel);
            rightScrollPane.setFitToWidth(true);
            rightScrollPane.setPrefWidth(320); // Slightly wider to account for scrollbar
            rightScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); // No horizontal scrollbar
            root.setRight(rightScrollPane);
            
            // Bottom: Statistics and charts
            HBox bottomPanel = createBottomPanel();
            root.setBottom(bottomPanel);
            
            // Add event handlers
            // Setup event handlers after scene creation to avoid null
            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            primaryStage.setTitle("Ecoland: AI Ecosystem Simulator");
            primaryStage.setScene(scene);
            primaryStage.setOnCloseRequest(e -> stopSimulation()); // Ensure timer stops on close
            primaryStage.show();
            
            // Now add event handlers after the scene is attached to the canvas
            setupEventHandlers(worldCanvas, scrollPane);
            
            setupGameLoop();
            // Start paused initially
            updateUI(); // Initial render
        } else {
            // User cancelled setup dialog
            primaryStage.close();
        }
    }
    
    /**
     * Shows a dialog for setting up initial simulation parameters
     * @return true if the user confirmed setup, false if cancelled
     */
    private boolean showSetupDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(primaryStage);
        dialog.setTitle("Simulation Setup");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        // World size controls
        Label worldSizeLabel = new Label("World Size:");
        worldSizeLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        grid.add(worldSizeLabel, 0, 0, 2, 1);
        
        grid.add(new Label("Width:"), 0, 1);
        Spinner<Integer> widthSpinner = new Spinner<>(20, 500, worldWidth, 10);
        widthSpinner.setEditable(true);
        widthSpinner.valueProperty().addListener((obs, oldVal, newVal) -> worldWidth = newVal);
        grid.add(widthSpinner, 1, 1);
        
        grid.add(new Label("Height:"), 0, 2);
        Spinner<Integer> heightSpinner = new Spinner<>(20, 500, worldHeight, 10);
        heightSpinner.setEditable(true);
        heightSpinner.valueProperty().addListener((obs, oldVal, newVal) -> worldHeight = newVal);
        grid.add(heightSpinner, 1, 2);
        
        // Initial population controls
        Label populationLabel = new Label("Initial Population:");
        populationLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        grid.add(populationLabel, 0, 3, 2, 1);
        
        grid.add(new Label("Herbivores:"), 0, 4);
        Spinner<Integer> herbivoreSpinner = new Spinner<>(0, 500, initialHerbivoreCount, 5);
        herbivoreSpinner.setEditable(true);
        herbivoreSpinner.valueProperty().addListener((obs, oldVal, newVal) -> initialHerbivoreCount = newVal);
        grid.add(herbivoreSpinner, 1, 4);
        
        grid.add(new Label("Carnivores:"), 0, 5);
        Spinner<Integer> carnivoreSpinner = new Spinner<>(0, 100, initialCarnivoreCount, 1);
        carnivoreSpinner.setEditable(true);
        carnivoreSpinner.valueProperty().addListener((obs, oldVal, newVal) -> initialCarnivoreCount = newVal);
        grid.add(carnivoreSpinner, 1, 5);
        
        grid.add(new Label("Decomposers:"), 0, 6);
        Spinner<Integer> decomposerSpinner = new Spinner<>(0, 100, initialDecomposerCount, 1);
        decomposerSpinner.setEditable(true);
        decomposerSpinner.valueProperty().addListener((obs, oldVal, newVal) -> initialDecomposerCount = newVal);
        grid.add(decomposerSpinner, 1, 6);
        
        grid.add(new Label("Apex Predators:"), 0, 7);
        Spinner<Integer> apexPredatorSpinner = new Spinner<>(0, 20, initialApexPredatorCount, 1);
        apexPredatorSpinner.setEditable(true);
        apexPredatorSpinner.valueProperty().addListener((obs, oldVal, newVal) -> initialApexPredatorCount = newVal);
        grid.add(apexPredatorSpinner, 1, 7);
        
        grid.add(new Label("Omnivores:"), 0, 8);
        Spinner<Integer> omnivoreSpinner = new Spinner<>(0, 100, initialOmnivoreCount, 1);
        omnivoreSpinner.setEditable(true);
        omnivoreSpinner.valueProperty().addListener((obs, oldVal, newVal) -> initialOmnivoreCount = newVal);
        grid.add(omnivoreSpinner, 1, 8);
        
        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        Button cancelButton = new Button("Cancel");
        Button startButton = new Button("Start Simulation");
        startButton.setDefaultButton(true);
        
        buttonBox.getChildren().addAll(cancelButton, startButton);
        grid.add(buttonBox, 0, 9, 2, 1);
        
        // Event handlers
        boolean[] confirmed = new boolean[1];
        cancelButton.setOnAction(e -> {
            confirmed[0] = false;
            dialog.close();
        });
        
        startButton.setOnAction(e -> {
            confirmed[0] = true;
            dialog.close();
        });
        
        Scene scene = new Scene(grid);
        dialog.setScene(scene);
        dialog.showAndWait();
        
        return confirmed[0];
    }

    private VBox createControlPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-border-color: lightgray; -fx-border-width: 1;");
        panel.setPrefWidth(300);
        
        // Section Title
        Label controlsTitle = new Label("Simulation Controls");
        controlsTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        // --- Main Controls ---
        HBox controls = new HBox(10);
        Button startPauseButton = new Button("Start");
        startPauseButton.setOnAction(e -> toggleSimulation());
        Button stepButton = new Button("Step");
        stepButton.setOnAction(e -> {
            if (!isRunning) { // Only allow step when paused
                simulation.tick();
                updateUI();
            }
        });
        controls.getChildren().addAll(startPauseButton, stepButton);
        
        // --- Save/Load Controls ---
        HBox saveLoadControls = new HBox(10);
        Button saveButton = new Button("Save Simulation");
        saveButton.setOnAction(e -> saveSimulationState());
        Button loadButton = new Button("Load Simulation");
        loadButton.setOnAction(e -> loadSimulationState());
        saveLoadControls.getChildren().addAll(saveButton, loadButton);
        
        // --- Save Log Button ---
        Button saveLogButton = new Button("Save Data Log");
        saveLogButton.setOnAction(e -> saveSimulationLog());
        
        // --- Color Legend ---
        VBox legendBox = new VBox(5);
        Label legendTitle = new Label("Entity Types:");
        legendTitle.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        legendBox.getChildren().add(legendTitle);
        
        // Add color squares with labels for each entity type
        addColorLegendItem(legendBox, "Herbivore", Color.BLUE);
        addColorLegendItem(legendBox, "Carnivore", Color.RED);
        addColorLegendItem(legendBox, "Plant", Color.DARKGREEN);
        addColorLegendItem(legendBox, "Omnivore", Color.ORANGE);
        addColorLegendItem(legendBox, "Apex Predator", Color.DARKRED);
        addColorLegendItem(legendBox, "Decomposer", Color.PURPLE);
        addColorLegendItem(legendBox, "Scavenger", Color.BROWN);
        
        // --- Neural Network Controls ---
        VBox aiControls = new VBox(5);
        Label aiTitle = new Label("AI Controls:");
        aiTitle.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        CheckBox herbivoreNeuralToggle = new CheckBox("Neural Herbivores");
        herbivoreNeuralToggle.setSelected(true);
        herbivoreNeuralToggle.setOnAction(e -> toggleEntityNeuralBehavior(SpeciesType.HERBIVORE, herbivoreNeuralToggle.isSelected()));
        
        CheckBox carnivoreNeuralToggle = new CheckBox("Neural Carnivores");
        carnivoreNeuralToggle.setSelected(true);
        carnivoreNeuralToggle.setOnAction(e -> toggleEntityNeuralBehavior(SpeciesType.CARNIVORE, carnivoreNeuralToggle.isSelected()));
        
        CheckBox decomposerNeuralToggle = new CheckBox("Neural Decomposers");
        decomposerNeuralToggle.setSelected(true);
        decomposerNeuralToggle.setOnAction(e -> toggleEntityNeuralBehavior(SpeciesType.DECOMPOSER, decomposerNeuralToggle.isSelected()));
        
        CheckBox apexPredatorNeuralToggle = new CheckBox("Neural Apex Predators");
        apexPredatorNeuralToggle.setSelected(true);
        apexPredatorNeuralToggle.setOnAction(e -> toggleEntityNeuralBehavior(SpeciesType.APEX_PREDATOR, apexPredatorNeuralToggle.isSelected()));
        
        CheckBox omnivoreNeuralToggle = new CheckBox("Neural Omnivores");
        omnivoreNeuralToggle.setSelected(true);
        omnivoreNeuralToggle.setOnAction(e -> toggleEntityNeuralBehavior(SpeciesType.OMNIVORE, omnivoreNeuralToggle.isSelected()));
        
        aiControls.getChildren().addAll(aiTitle, herbivoreNeuralToggle, carnivoreNeuralToggle, 
                               decomposerNeuralToggle, apexPredatorNeuralToggle, omnivoreNeuralToggle);
        
        // --- Speed Slider ---
        VBox speedControl = new VBox(5);
        Label speedTitle = new Label("Simulation Speed:");
        Slider speedSlider = new Slider(1, 60, targetUpdatesPerSecond); // 1 to 60 TPS
        speedSlider.setShowTickLabels(true);
        speedSlider.setShowTickMarks(true);
        speedSlider.setMajorTickUnit(10);
        speedSlider.setBlockIncrement(5);
        Label speedValueLabel = new Label(String.format("%.1f ticks/sec", targetUpdatesPerSecond));
        
        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            targetUpdatesPerSecond = newVal.doubleValue();
            speedValueLabel.setText(String.format("%.1f ticks/sec", targetUpdatesPerSecond));
        });
        
        speedControl.getChildren().addAll(speedTitle, speedSlider, speedValueLabel);
        
        // --- Zoom Controls ---
        VBox zoomControl = new VBox(5);
        Label zoomLabel = new Label("Zoom Level:");
        Slider zoomSlider = new Slider(0.5, 3.0, zoomLevel);
        zoomSlider.setShowTickLabels(true);
        zoomSlider.setMajorTickUnit(0.5);
        Label zoomValueLabel = new Label(String.format("%.1fx", zoomLevel));
        
        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            zoomLevel = newVal.doubleValue();
            zoomValueLabel.setText(String.format("%.1fx", zoomLevel));
            applyZoom();
        });
        
        Button resetViewButton = new Button("Reset View");
        resetViewButton.setOnAction(e -> {
            zoomLevel = 1.0;
            viewportX = 0;
            viewportY = 0;
            zoomSlider.setValue(1.0);
            applyZoom();
        });
        
        zoomControl.getChildren().addAll(zoomLabel, zoomSlider, zoomValueLabel, resetViewButton);
        
        // --- Entity Placement Tools ---
        VBox toolsControl = new VBox(5);
        Label toolsLabel = new Label("Placement Tools:");
        toolsLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        toolToggleGroup = new ToggleGroup();
        
        RadioButton noToolRadio = new RadioButton("No Tool");
        noToolRadio.setToggleGroup(toolToggleGroup);
        noToolRadio.setSelected(true);
        noToolRadio.setUserData(PlacementTool.NONE);
        
        RadioButton herbivoreToolRadio = new RadioButton("Place Herbivore");
        herbivoreToolRadio.setToggleGroup(toolToggleGroup);
        herbivoreToolRadio.setUserData(PlacementTool.HERBIVORE);
        
        RadioButton carnivoreToolRadio = new RadioButton("Place Carnivore");
        carnivoreToolRadio.setToggleGroup(toolToggleGroup);
        carnivoreToolRadio.setUserData(PlacementTool.CARNIVORE);
        
        RadioButton plantToolRadio = new RadioButton("Place Plant");
        plantToolRadio.setToggleGroup(toolToggleGroup);
        plantToolRadio.setUserData(PlacementTool.PLANT);
        
        RadioButton foodToolRadio = new RadioButton("Add Food");
        foodToolRadio.setToggleGroup(toolToggleGroup);
        foodToolRadio.setUserData(PlacementTool.FOOD);
        
        RadioButton decomposerToolRadio = new RadioButton("Place Decomposer");
        decomposerToolRadio.setToggleGroup(toolToggleGroup);
        decomposerToolRadio.setUserData(PlacementTool.DECOMPOSER);
        
        RadioButton apexPredatorToolRadio = new RadioButton("Place Apex Predator");
        apexPredatorToolRadio.setToggleGroup(toolToggleGroup);
        apexPredatorToolRadio.setUserData(PlacementTool.APEX_PREDATOR);
        
        RadioButton omnivoreToolRadio = new RadioButton("Place Omnivore");
        omnivoreToolRadio.setToggleGroup(toolToggleGroup);
        omnivoreToolRadio.setUserData(PlacementTool.OMNIVORE);
        
        toolToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                currentTool = PlacementTool.NONE;
            } else {
                currentTool = (PlacementTool) newVal.getUserData();
            }
        });
        
        toolsControl.getChildren().addAll(
            toolsLabel, noToolRadio, herbivoreToolRadio, 
            carnivoreToolRadio, apexPredatorToolRadio, omnivoreToolRadio,
            plantToolRadio, foodToolRadio, decomposerToolRadio
        );
        
        // --- Current Stats ---
        VBox statsBox = new VBox(5);
        Label statsTitle = new Label("Current Statistics:");
        statsTitle.setFont(Font.font("System", FontWeight.BOLD, 12));
        statsLabel = new Label("Initializing stats...");
        statsLabel.setWrapText(true);
        statsLabel.setMinHeight(60);
        statsBox.getChildren().addAll(statsTitle, statsLabel);

        // --- Inspector Panel ---
        VBox inspectorBox = new VBox(5);
        Label inspectorTitle = new Label("Inspector:");
        inspectorTitle.setFont(Font.font("System", FontWeight.BOLD, 12));
        inspectorLabel = new Label("Click on map to inspect tile/entity.");
        inspectorLabel.setWrapText(true);
        inspectorLabel.setMinHeight(100);
        inspectorLabel.setStyle("-fx-border-color: gray; -fx-border-width: 1; -fx-padding: 5;");
        inspectorBox.getChildren().addAll(inspectorTitle, inspectorLabel);

        // Add all components to the panel
        panel.getChildren().addAll(
            controlsTitle, controls, saveLoadControls, saveLogButton, 
            legendBox, aiControls, speedControl, new Separator(),
            zoomControl, new Separator(),
            toolsControl, new Separator(),
            statsBox, new Separator(), inspectorBox
        );

        return panel;
    }
    
    private HBox createBottomPanel() {
        HBox bottomPanel = new HBox(10);
        bottomPanel.setPadding(new Insets(10));
        bottomPanel.setPrefHeight(200); // Increase height for chart visibility
        
        // Create a TabPane for charts and visualizations
        TabPane chartTabPane = new TabPane();
        chartTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        chartTabPane.setPrefWidth(WINDOW_WIDTH - 100);
        
        // Population chart tab
        Tab populationTab = new Tab("Population Trends");
        populationChartPane = new VBox();
        populationChartPane.setPadding(new Insets(10));
        populationChartPane.setAlignment(Pos.CENTER);
        Label placeholderLabel = new Label("Population chart will appear as simulation progresses");
        placeholderLabel.setAlignment(Pos.CENTER);
        populationChartPane.getChildren().add(placeholderLabel);
        populationTab.setContent(populationChartPane);
        
        // Gene stats tab
        Tab geneStatsTab = new Tab("Gene Statistics");
        VBox geneStatsPane = new VBox(10);
        geneStatsPane.setPadding(new Insets(10));
        
        // Species selection for gene stats
        HBox speciesSelectionBox = new HBox(10);
        speciesSelectionBox.setAlignment(Pos.CENTER);
        Label speciesLabel = new Label("Species:");
        ComboBox<SpeciesType> speciesComboBox = new ComboBox<>();
        speciesComboBox.getItems().addAll(SpeciesType.HERBIVORE, SpeciesType.CARNIVORE, 
                                         SpeciesType.DECOMPOSER, SpeciesType.APEX_PREDATOR, 
                                         SpeciesType.OMNIVORE);
        speciesComboBox.setValue(SpeciesType.HERBIVORE);
        speciesComboBox.setOnAction(e -> updateGeneStats(speciesComboBox.getValue()));
        
        Button refreshButton = new Button("Refresh Stats");
        refreshButton.setOnAction(e -> updateGeneStats(speciesComboBox.getValue()));
        
        speciesSelectionBox.getChildren().addAll(speciesLabel, speciesComboBox, refreshButton);
        
        // Table for gene stats display
        TableView<GeneStatRow> geneStatsTable = new TableView<>();
        geneStatsTable.setId("geneStatsTable");
        geneStatsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        TableColumn<GeneStatRow, String> geneNameCol = new TableColumn<>("Gene");
        geneNameCol.setCellValueFactory(new PropertyValueFactory<>("geneName"));
        
        TableColumn<GeneStatRow, Double> minCol = new TableColumn<>("Min");
        minCol.setCellValueFactory(new PropertyValueFactory<>("min"));
        minCol.setCellFactory(col -> new TableCell<GeneStatRow, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f", item));
                }
            }
        });
        
        TableColumn<GeneStatRow, Double> maxCol = new TableColumn<>("Max");
        maxCol.setCellValueFactory(new PropertyValueFactory<>("max"));
        maxCol.setCellFactory(col -> new TableCell<GeneStatRow, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f", item));
                }
            }
        });
        
        TableColumn<GeneStatRow, Double> avgCol = new TableColumn<>("Average");
        avgCol.setCellValueFactory(new PropertyValueFactory<>("avg"));
        avgCol.setCellFactory(col -> new TableCell<GeneStatRow, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f", item));
                }
            }
        });
        
        TableColumn<GeneStatRow, Double> stdDevCol = new TableColumn<>("Std Dev");
        stdDevCol.setCellValueFactory(new PropertyValueFactory<>("stdDev"));
        stdDevCol.setCellFactory(col -> new TableCell<GeneStatRow, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f", item));
                }
            }
        });
        
        geneStatsTable.getColumns().addAll(geneNameCol, minCol, maxCol, avgCol, stdDevCol);
        
        geneStatsPane.getChildren().addAll(speciesSelectionBox, geneStatsTable);
        geneStatsTab.setContent(geneStatsPane);
        
        // Add tabs to tab pane
        chartTabPane.getTabs().addAll(populationTab, geneStatsTab);
        
        bottomPanel.getChildren().add(chartTabPane);
        
        return bottomPanel;
    }
    
    /**
     * Helper class for gene statistics table
     */
    public static class GeneStatRow {
        private String geneName;
        private double min;
        private double max;
        private double avg;
        private double stdDev;
        
        public GeneStatRow(String geneName, double min, double max, double avg, double stdDev) {
            this.geneName = geneName;
            this.min = min;
            this.max = max;
            this.avg = avg;
            this.stdDev = stdDev;
        }
        
        public String getGeneName() { return geneName; }
        public double getMin() { return min; }
        public double getMax() { return max; }
        public double getAvg() { return avg; }
        public double getStdDev() { return stdDev; }
    }
    
    /**
     * Updates the gene statistics table view based on selected species type
     */
    private void updateGeneStats(SpeciesType speciesType) {
        // Get the gene stats table from the scene
        Scene scene = primaryStage.getScene();
        TableView<GeneStatRow> geneStatsTable = (TableView<GeneStatRow>) scene.lookup("#geneStatsTable");
        
        if (geneStatsTable == null) {
            return;
        }
        
        // Clear existing data
        geneStatsTable.getItems().clear();
        
        // Get gene statistics from data logger
        Map<String, double[]> geneStats = simulation.getDataLogger()
            .getGeneStats(simulation.getEntityManager(), speciesType);
        
        // If no stats available, show placeholder message
        if (geneStats.isEmpty()) {
            geneStatsTable.setPlaceholder(new Label("No " + speciesType + " entities to analyze"));
            return;
        }
        
        // Add gene stats to table
        for (Map.Entry<String, double[]> entry : geneStats.entrySet()) {
            String geneName = entry.getKey();
            double[] stats = entry.getValue(); // [min, max, avg, stdDev]
            
            geneStatsTable.getItems().add(new GeneStatRow(
                geneName, stats[0], stats[1], stats[2], stats[3]
            ));
        }
    }
    
    private void setupEventHandlers(Canvas canvas, ScrollPane scrollPane) {
        // Mouse click events
        canvas.setOnMouseClicked(event -> {
            if (currentTool != PlacementTool.NONE) {
                placeTool(event.getX(), event.getY());
            } else {
                // Inspect mode
                inspectTileAtPosition(event.getX(), event.getY());
            }
        });
        
        // Mouse drag events for panning
        canvas.setOnMousePressed(event -> {
            // Enable panning with right button (secondary) or middle button (middle)
            if (event.isSecondaryButtonDown() || event.isMiddleButtonDown()) {
                lastMouseX = event.getX();
                lastMouseY = event.getY();
                isPanning = true;
                event.consume();
            }
        });
        
        canvas.setOnMouseDragged(event -> {
            if (isPanning) {
                double dx = (event.getX() - lastMouseX);
                double dy = (event.getY() - lastMouseY);
                viewportX -= dx;
                viewportY -= dy;
                lastMouseX = event.getX();
                lastMouseY = event.getY();
                applyZoom();
                event.consume();
            }
        });
        
        canvas.setOnMouseReleased(event -> {
            // End panning on any mouse button release if we were panning
            if (isPanning) {
                isPanning = false;
                event.consume();
            }
        });
        
        // Mouse wheel for zooming
        canvas.setOnScroll(event -> {
            double zoomFactor = event.getDeltaY() > 0 ? 1.1 : 0.9;
            zoomLevel *= zoomFactor;
            zoomLevel = Math.max(0.1, Math.min(5.0, zoomLevel));
            applyZoom();
        });
        
        // Keyboard shortcuts
        Scene scene = canvas.getScene();
        if (scene != null) {
            scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.SPACE) {
                    toggleSimulation();
                } else if (event.getCode() == KeyCode.RIGHT && !isRunning) {
                    // Step forward with right arrow when paused
                    simulation.tick();
                    updateUI();
                } else if (event.getCode() == KeyCode.ESCAPE) {
                    // Clear current tool
                    toolToggleGroup.selectToggle(null);
                    currentTool = PlacementTool.NONE;
                }
            });
        }
    }
    
    private void applyZoom() {
        renderer.setZoom(zoomLevel, viewportX, viewportY);
        updateUI();
    }
    
    private void placeTool(double canvasX, double canvasY) {
        int[] worldCoords = renderer.canvasToWorldCoordinates(canvasX, canvasY);
        if (worldCoords == null) return;
        
        int x = worldCoords[0];
        int y = worldCoords[1];
        
        if (!simulation.getWorld().isValidCoordinate(x, y)) return;
        
        switch (currentTool) {
            case HERBIVORE:
                if (!simulation.getEntityManager().isTileOccupied(x, y)) {
                    Entity herbivore = new com.ecoland.entity.Herbivore(x, y);
                    simulation.getEntityManager().addEntity(herbivore);
                    simulation.getEntityManager().updateEntityList();
                    updateUI();
                }
                break;
                
            case CARNIVORE:
                if (!simulation.getEntityManager().isTileOccupied(x, y)) {
                    Entity carnivore = new com.ecoland.entity.Carnivore(x, y);
                    simulation.getEntityManager().addEntity(carnivore);
                    simulation.getEntityManager().updateEntityList();
                    updateUI();
                }
                break;
                
            case PLANT:
                if (!simulation.getEntityManager().isTileOccupied(x, y)) {
                    Entity plant = new com.ecoland.entity.Plant(x, y);
                    simulation.getEntityManager().addEntity(plant);
                    simulation.getEntityManager().updateEntityList();
                    updateUI();
                }
                break;
                
            case FOOD:
                // Add food to the tile
                Tile tile = simulation.getWorld().getTile(x, y);
                if (tile != null && tile.getTerrainType() != TerrainType.WATER) {
                    double currentFood = tile.getPlantFoodValue();
                    double newFood = currentFood + 2.0;
                    tile.forceSetPlantFoodValue(newFood);
                    
                    // Show visual feedback in inspector
                    StringBuilder feedback = new StringBuilder();
                    feedback.append(String.format("Added food to (%d, %d):\n", x, y));
                    feedback.append(String.format("Previous food: %.2f\n", currentFood));
                    feedback.append(String.format("New food: %.2f\n", newFood));
                    inspectorLabel.setText(feedback.toString());
                    
                    updateUI();
                }
                break;
                
            case DECOMPOSER:
                if (!simulation.getEntityManager().isTileOccupied(x, y)) {
                    Entity decomposer = new com.ecoland.entity.Decomposer(x, y);
                    simulation.getEntityManager().addEntity(decomposer);
                    simulation.getEntityManager().updateEntityList();
                    updateUI();
                }
                break;
                
            case APEX_PREDATOR:
                if (!simulation.getEntityManager().isTileOccupied(x, y)) {
                    Entity apexPredator = new com.ecoland.entity.ApexPredator(x, y);
                    simulation.getEntityManager().addEntity(apexPredator);
                    simulation.getEntityManager().updateEntityList();
                    updateUI();
                }
                break;
                
            case OMNIVORE:
                if (!simulation.getEntityManager().isTileOccupied(x, y)) {
                    Entity omnivore = new com.ecoland.entity.Omnivore(x, y);
                    simulation.getEntityManager().addEntity(omnivore);
                    simulation.getEntityManager().updateEntityList();
                    updateUI();
                }
                break;
                
            default:
                break;
        }
    }

    /**
     * Saves the data log from the simulation using a file chooser dialog.
     */
    private void saveSimulationLog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Simulation Data Log");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        fileChooser.setInitialFileName("ecoland_simulation_log.csv");
        
        File file = fileChooser.showSaveDialog(primaryStage);
        if (file != null) {
            try {
                simulation.getDataLogger().saveData(file.getAbsolutePath());
                showAlert(Alert.AlertType.INFORMATION, "Data Log Saved", 
                          "The simulation data log was successfully saved to:\n" + file.getAbsolutePath());
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Error Saving Data Log", 
                          "Failed to save the simulation data log: " + e.getMessage());
            }
        }
    }
    
    private void saveSimulationState() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Simulation State");
        fileChooser.setInitialFileName("ecoland_state.dat");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Simulation Files", "*.dat"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File selectedFile = fileChooser.showSaveDialog(primaryStage);

        if (selectedFile != null) {
            boolean success = simulation.saveStateToFile(selectedFile.getAbsolutePath());
            if (success) {
                showAlert(Alert.AlertType.INFORMATION, "Save Successful", 
                          "Simulation state saved to: " + selectedFile.getAbsolutePath());
            } else {
                showAlert(Alert.AlertType.ERROR, "Save Failed", 
                          "Failed to save simulation state");
            }
        }
    }
    
    private void loadSimulationState() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Simulation State");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Simulation Files", "*.dat"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File selectedFile = fileChooser.showOpenDialog(primaryStage);

        if (selectedFile != null) {
            // Pause simulation if running
            boolean wasRunning = isRunning;
            if (isRunning) {
                toggleSimulation();
            }
            
            // Load state
            SimulationState state = Simulation.loadStateFromFile(selectedFile.getAbsolutePath());
            
            if (state != null) {
                // Create new simulation with loaded state
                simulation = new Simulation(state);
                
                // Update renderer with new world
                renderer.setWorld(simulation.getWorld());
                
                // Reset view
                zoomLevel = 1.0;
                viewportX = 0;
                viewportY = 0;
                
                updateUI();
                
                showAlert(Alert.AlertType.INFORMATION, "Load Successful", 
                          "Simulation state loaded from tick " + state.getTick());
                
                // Restart if it was running before
                if (wasRunning) {
                    toggleSimulation();
                }
            } else {
                showAlert(Alert.AlertType.ERROR, "Load Failed", 
                          "Failed to load simulation state. The file may be corrupted or incompatible.");
            }
        }
    }

    private void inspectTileAtPosition(double canvasX, double canvasY) {
        int[] worldCoords = renderer.canvasToWorldCoordinates(canvasX, canvasY);
        if (worldCoords != null) {
            int x = worldCoords[0];
            int y = worldCoords[1];
            inspectTile(x, y);
        }
    }
    
    private void inspectTile(int x, int y) {
        Entity entity = simulation.getEntityManager().getEntityAt(x, y);
        Tile tile = simulation.getWorld().getTile(x, y);
        StringBuilder info = new StringBuilder();
        info.append(String.format("Tile (%d, %d):\n", x, y));
        if (tile != null) {
            info.append(String.format(" Terrain: %s\n", tile.getTerrainType()));
            info.append(String.format(" Biome: %s\n", tile.getBiomeType()));
            info.append(String.format(" Food: %.2f\n", tile.getPlantFoodValue()));
            info.append(String.format(" Fertility: %.2f\n", tile.getFertility()));
            info.append(String.format(" Elevation: %.2f\n", tile.getElevation()));
            info.append(String.format(" Temperature: %.2f\n", tile.getTemperature()));
            info.append(String.format(" Moisture: %.2f\n", tile.getMoisture()));
        } else {
             info.append(" Invalid Tile Data\n");
        }

        if (entity != null) {
            info.append(String.format("\n--- Entity Info ---\n"));
            info.append(String.format(" Species: %s\n", entity.getSpeciesType()));
            info.append(String.format(" Energy: %.1f / %.1f\n", entity.getEnergy(), entity.getMaxEnergy()));
            info.append(String.format(" Health: %.1f / %.1f\n", entity.getHealth(), entity.getMaxHealth()));
            info.append(" Genes: ").append(entity.getGenes().toString()).append("\n");
        } else {
             info.append("\nNo entity on this tile.");
        }

        inspectorLabel.setText(info.toString());
    }

    private void setupGameLoop() {
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

    private void toggleSimulation() {
        isRunning = !isRunning;
        
        // Find and update the start/pause button
        Scene scene = primaryStage.getScene();
        for (javafx.scene.Node node : scene.getRoot().lookupAll("Button")) {
            if (node instanceof Button) {
                Button button = (Button) node;
                if (button.getText().equals("Start") || button.getText().equals("Pause")) {
                    button.setText(isRunning ? "Pause" : "Start");
                    break;
                }
            }
        }

        if (isRunning) {
            lastUpdate = System.nanoTime(); // Set initial time when starting
            gameLoop.start();
        } else {
            gameLoop.stop();
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
        int plantCount = simulation.getEntityManager().getPopulationCount(SpeciesType.PLANT);
        int decomposerCount = simulation.getEntityManager().getPopulationCount(SpeciesType.DECOMPOSER);
        int apexPredatorCount = simulation.getEntityManager().getPopulationCount(SpeciesType.APEX_PREDATOR);
        int omnivoreCount = simulation.getEntityManager().getPopulationCount(SpeciesType.OMNIVORE);
        int totalPop = simulation.getEntityManager().getTotalPopulation();
        
        statsLabel.setText(String.format(
            "Tick: %d\n" +
            "Total Population: %d\n" +
            "Herbivores: %d\n" +
            "Carnivores: %d\n" +
            "Apex Predators: %d\n" +
            "Omnivores: %d\n" +
            "Plants: %d\n" +
            "Decomposers: %d",
            tick, totalPop, herbivoreCount, carnivoreCount, apexPredatorCount, 
            omnivoreCount, plantCount, decomposerCount
        ));

        // Re-render the world
        renderer.render(simulation.getEntityManager().getAllEntities());
        
        // Update charts every 10 ticks to avoid performance issues
        if (tick % 10 == 0) {
            updateCharts();
            
            // Update gene stats less frequently (every 50 ticks) to improve performance
            if (tick % 50 == 0) {
                // Get current selected species from the combobox
                Scene scene = primaryStage.getScene();
                if (scene != null) {
                    @SuppressWarnings("unchecked")
                    ComboBox<SpeciesType> speciesComboBox = (ComboBox<SpeciesType>) 
                        scene.lookup(".combo-box");
                    
                    if (speciesComboBox != null) {
                        SpeciesType selectedSpecies = speciesComboBox.getValue();
                        if (selectedSpecies != null) {
                            updateGeneStats(selectedSpecies);
                        }
                    }
                }
            }
        }
    }
    
    private void updateCharts() {
        // Get population data from the data logger (last 100 entries max)
        List<long[]> populationData = simulation.getDataLogger().getPopulationData(100);
        
        if (populationData.isEmpty()) {
            // If no data yet, just show placeholder
            populationChartPane.getChildren().clear();
            Label chartTitle = new Label("Population History");
            chartTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
            Label placeholderLabel = new Label("Population chart will appear as simulation progresses");
            populationChartPane.getChildren().addAll(chartTitle, placeholderLabel);
            return;
        }
        
        // Create axes for the chart
        final NumberAxis xAxis = new NumberAxis();
        final NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Tick");
        yAxis.setLabel("Population");
        
        // Create the chart
        final LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Population Trends");
        lineChart.setAnimated(false); // Disable animation for better performance
        lineChart.setCreateSymbols(false); // No symbols on data points for cleaner look
        lineChart.setLegendVisible(true);
        
        // Create series for each population type
        XYChart.Series<Number, Number> seriesHerbivores = new XYChart.Series<>();
        seriesHerbivores.setName("Herbivores");
        
        XYChart.Series<Number, Number> seriesCarnivores = new XYChart.Series<>();
        seriesCarnivores.setName("Carnivores");
        
        XYChart.Series<Number, Number> seriesPlants = new XYChart.Series<>();
        seriesPlants.setName("Plants");
        
        XYChart.Series<Number, Number> seriesTotalPop = new XYChart.Series<>();
        seriesTotalPop.setName("Total Population");
        
        // Add data points to each series
        for (long[] entry : populationData) {
            long tick = entry[0];
            long herbivores = entry[1];
            long carnivores = entry[2];
            long plants = entry[3];
            long totalPop = entry[4];
            
            seriesHerbivores.getData().add(new XYChart.Data<>(tick, herbivores));
            seriesCarnivores.getData().add(new XYChart.Data<>(tick, carnivores));
            seriesPlants.getData().add(new XYChart.Data<>(tick, plants));
            seriesTotalPop.getData().add(new XYChart.Data<>(tick, totalPop));
        }
        
        // Apply CSS styles for lines to make them distinct
        String herbivoreStyle = "-fx-stroke: blue;";
        String carnivoreStyle = "-fx-stroke: red;";
        String plantStyle = "-fx-stroke: green;";
        String totalStyle = "-fx-stroke: black; -fx-stroke-dash-array: 2 2;"; // Dashed line for total
        
        // Add all series to the chart
        lineChart.getData().addAll(seriesTotalPop, seriesHerbivores, seriesCarnivores, seriesPlants);
        
        // Apply styles to the lines
        seriesHerbivores.getNode().setStyle(herbivoreStyle);
        seriesCarnivores.getNode().setStyle(carnivoreStyle);
        seriesPlants.getNode().setStyle(plantStyle);
        seriesTotalPop.getNode().setStyle(totalStyle);
        
        // Update the chart pane
        populationChartPane.getChildren().clear();
        lineChart.setPrefHeight(180);
        populationChartPane.getChildren().add(lineChart);
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Toggle neural network behavior for all entities of a specific type
     * @param type The species type to affect
     * @param useNeural Whether to use neural behavior or not
     */
    private void toggleEntityNeuralBehavior(SpeciesType type, boolean useNeural) {
        if (simulation == null) return;
        
        EntityManager entityManager = simulation.getEntityManager();
        for (Entity entity : entityManager.getAllEntities()) {
            if (entity.getSpeciesType() == type) {
                if (type == SpeciesType.HERBIVORE && entity instanceof Herbivore) {
                    ((Herbivore) entity).setUseNeuralBehavior(useNeural);
                } 
                else if (type == SpeciesType.CARNIVORE && entity instanceof Carnivore) {
                    ((Carnivore) entity).setUseNeuralBehavior(useNeural);
                }
                else if (type == SpeciesType.DECOMPOSER && entity instanceof Decomposer) {
                    ((Decomposer) entity).setUseNeuralBehavior(useNeural);
                }
                else if (type == SpeciesType.APEX_PREDATOR && entity instanceof ApexPredator) {
                    ((ApexPredator) entity).setUseNeuralBehavior(useNeural);
                }
                else if (type == SpeciesType.OMNIVORE && entity instanceof Omnivore) {
                    ((Omnivore) entity).setUseNeuralBehavior(useNeural);
                }
            }
        }
    }

    /**
     * Helper method to add a color item to the legend
     */
    private void addColorLegendItem(VBox container, String label, Color color) {
        HBox item = new HBox(10);
        item.setAlignment(Pos.CENTER_LEFT);
        
        // Create color square
        Rectangle colorSquare = new Rectangle(15, 15, color);
        colorSquare.setStroke(Color.BLACK);
        colorSquare.setStrokeWidth(0.5);
        
        // Create label
        Label textLabel = new Label(label);
        
        item.getChildren().addAll(colorSquare, textLabel);
        container.getChildren().add(item);
    }

    public static void main(String[] args) {
        launch(args);
    }
} 