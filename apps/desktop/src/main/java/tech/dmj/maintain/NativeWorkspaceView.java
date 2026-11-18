package tech.dmj.maintain;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

/** JavaFX-first native workspace backed by the local Rust engine. */
public final class NativeWorkspaceView extends BorderPane {
    private static final String PANEL = "#171f2a";
    private static final String TEXT = "#e8eef7";
    private static final String MUTED = "#91a0b3";
    private static final String ACCENT = "#38d6c0";

    private final EngineIpcClient engine;
    private final Runnable openCompatibilityWorkspace;
    private final ObservableList<EngineIpcClient.MachineDto> machines = FXCollections.observableArrayList();
    private final FilteredList<EngineIpcClient.MachineDto> visibleMachines =
            new FilteredList<>(machines, machine -> true);
    private final TableView<EngineIpcClient.MachineDto> table = new TableView<>();
    private final ComboBox<EngineIpcClient.MachineDto> telemetryMachine = new ComboBox<>();
    private final TableView<EngineIpcClient.TelemetryDto> telemetryTable = new TableView<>();
    private final TableView<EngineIpcClient.AnomalyDto> anomalyTable = new TableView<>();
    private final Label anomalyStatus = new Label("No anomaly events loaded.");
    private final TextField telemetryFilter = new TextField();
    private final CategoryAxis telemetryTimeAxis = new CategoryAxis();
    private final NumberAxis telemetryValueAxis = new NumberAxis();
    private final LineChart<String, Number> telemetryChart =
            new LineChart<>(telemetryTimeAxis, telemetryValueAxis);
    private final TextField telemetryMetric = new TextField();
    private final TextField telemetryValue = new TextField();
    private final TextField telemetryUnit = new TextField();
    private final Label telemetryStatus = new Label("Select a machine to view telemetry.");
    private final TextField search = new TextField();
    private final Label status = new Label("Connecting to local Rust engine…");
    private final Label totalAssets = new Label("—");
    private final Label offlineAssets = new Label("—");

    public NativeWorkspaceView(
            String engineName,
            EngineIpcClient engine,
            Runnable openCompatibilityWorkspace
    ) {
        this.engine = engine;
        this.openCompatibilityWorkspace = openCompatibilityWorkspace;
        setStyle("-fx-background-color:#0e141c;");
        setTop(buildHeader(engineName));
        setCenter(buildMainContent());
        setBottom(buildStatusBar());
        refreshMachines();
    }

    private Node buildHeader(String engineName) {
        HBox header = new HBox(18);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(18, 28, 18, 28));
        header.setStyle("-fx-background-color:#121a24;-fx-border-color:#293444;-fx-border-width:0 0 1 0;");

        Label mark = new Label("M");
        mark.setMinSize(42, 42);
        mark.setAlignment(Pos.CENTER);
        mark.setStyle("-fx-background-color:#173a3a;-fx-background-radius:11;-fx-text-fill:" + ACCENT
                + ";-fx-font-size:22px;-fx-font-weight:bold;");

        VBox identity = new VBox(3);
        Label brand = new Label("MAINTAIN AI");
        brand.setStyle("-fx-text-fill:" + TEXT + ";-fx-font-size:17px;-fx-font-weight:bold;-fx-letter-spacing:1.8px;");
        Label edition = new Label("NATIVE WORKSPACE");
        edition.setStyle("-fx-text-fill:" + MUTED + ";-fx-font-size:10px;-fx-letter-spacing:2px;");
        identity.getChildren().addAll(brand, edition);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label engineBadge = new Label("●  " + (engineName == null || engineName.isBlank() ? "RUST ENGINE CONNECTED" : engineName));
        engineBadge.setStyle("-fx-text-fill:" + ACCENT
                + ";-fx-background-color:#15302f;-fx-background-radius:20;-fx-padding:8 12;"
                + "-fx-font-size:10px;-fx-font-weight:bold;");

        Button compatibility = new Button(openCompatibilityWorkspace == null
                ? "Legacy features · run gradle runCompatibility"
                : "Open remaining features  ↗");
        compatibility.setStyle(buttonStyle(openCompatibilityWorkspace != null));
        compatibility.setDisable(openCompatibilityWorkspace == null);
        compatibility.setOnAction(event -> {
            if (openCompatibilityWorkspace != null) openCompatibilityWorkspace.run();
        });

        header.getChildren().addAll(mark, identity, spacer, engineBadge, compatibility);
        return header;
    }

    private Node buildMainContent() {
        VBox content = new VBox(22);
        content.setPadding(new Insets(30, 34, 28, 34));

        VBox heading = new VBox(6);
        Label eyebrow = new Label("ASSET OPERATIONS");
        eyebrow.setStyle("-fx-text-fill:" + ACCENT + ";-fx-font-size:10px;-fx-font-weight:bold;-fx-letter-spacing:2px;");
        Label title = new Label("Machine registry");
        title.setStyle("-fx-text-fill:" + TEXT + ";-fx-font-size:27px;-fx-font-weight:bold;");
        Label description = new Label("Manage local assets through the JavaFX client and Rust domain engine.");
        description.setStyle("-fx-text-fill:" + MUTED + ";-fx-font-size:13px;");
        heading.getChildren().addAll(eyebrow, title, description);

        HBox metrics = new HBox(14,
                metricCard("REGISTERED ASSETS", totalAssets, "Persisted in the native SQLite store"),
                metricCard("OFFLINE / UNREPORTED", offlineAssets, "Default state until telemetry is migrated"));
        metrics.setAlignment(Pos.CENTER_LEFT);

        VBox registry = new VBox(14);
        registry.setPadding(new Insets(20));
        registry.setStyle("-fx-background-color:" + PANEL + ";-fx-background-radius:14;"
                + "-fx-border-color:#293444;-fx-border-radius:14;");

        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        VBox toolbarTitle = new VBox(4);
        Label tableTitle = new Label("All machines");
        tableTitle.setStyle("-fx-text-fill:" + TEXT + ";-fx-font-size:16px;-fx-font-weight:bold;");
        Label tableHint = new Label("Native records stored by the Rust engine");
        tableHint.setStyle("-fx-text-fill:" + MUTED + ";-fx-font-size:11px;");
        toolbarTitle.getChildren().addAll(tableTitle, tableHint);

        Region toolbarSpacer = new Region();
        HBox.setHgrow(toolbarSpacer, Priority.ALWAYS);

        search.setPromptText("Search name, asset tag or type");
        search.setPrefWidth(260);
        search.setStyle("-fx-background-color:#101720;-fx-text-fill:" + TEXT
                + ";-fx-prompt-text-fill:#6f7d8f;-fx-background-radius:8;"
                + "-fx-border-color:#354253;-fx-border-radius:8;-fx-padding:9 12;");
        search.textProperty().addListener((observable, oldValue, newValue) -> applySearch(newValue));

        Button refresh = new Button("Refresh");
        refresh.setStyle(buttonStyle(false));
        refresh.setOnAction(event -> refreshMachines());

        Button add = new Button("+  Add machine");
        add.setStyle(buttonStyle(true));
        add.setOnAction(event -> showCreateMachineDialog());

        toolbar.getChildren().addAll(toolbarTitle, toolbarSpacer, search, refresh, add);

        configureTable();
        table.setItems(visibleMachines);
        table.setPlaceholder(emptyTableMessage());
        table.setPrefHeight(490);

        registry.getChildren().addAll(toolbar, table);
        TabPane workspaceTabs = new TabPane();
        Tab machinesTab = new Tab("Machine registry", registry);
        machinesTab.setClosable(false);
        Tab telemetryTab = new Tab("Telemetry", buildTelemetryPanel());
        telemetryTab.setClosable(false);
        Tab anomaliesTab = new Tab("Anomalies", buildAnomalyPanel());
        anomaliesTab.setClosable(false);
        workspaceTabs.getTabs().addAll(machinesTab, telemetryTab, anomaliesTab);
        workspaceTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        workspaceTabs.setStyle("-fx-background-color:" + PANEL + ";");
        content.getChildren().addAll(heading, metrics, workspaceTabs);
        VBox.setVgrow(workspaceTabs, Priority.ALWAYS);
        return content;
    }


    private Node buildTelemetryPanel() {
        VBox panel = new VBox(14);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color:" + PANEL + ";-fx-background-radius:14;"
                + "-fx-border-color:#293444;-fx-border-radius:14;");

        VBox heading = new VBox(5);
        Label title = new Label("Telemetry");
        title.setStyle("-fx-text-fill:" + TEXT + ";-fx-font-size:17px;-fx-font-weight:bold;");
        Label hint = new Label("Record readings into native SQLite and review the history through the Rust engine.");
        hint.setStyle("-fx-text-fill:" + MUTED + ";-fx-font-size:11px;");
        heading.getChildren().addAll(title, hint);

        HBox filterRow = new HBox(10);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        telemetryMachine.setPromptText("Select machine");
        telemetryMachine.setPrefWidth(260);
        telemetryMachine.setStyle("-fx-background-color:#101720;-fx-text-fill:" + TEXT + ";");
        telemetryMachine.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(EngineIpcClient.MachineDto machine) {
                return machine == null ? "" : machine.name() + "  ·  " + machine.assetTag();
            }
            @Override public EngineIpcClient.MachineDto fromString(String value) { return null; }
        });
        telemetryMachine.setOnAction(event -> {
            refreshTelemetry();
            refreshAnomalies();
        });

        telemetryFilter.setPromptText("Filter signal (e.g. temperature)");
        telemetryFilter.setPrefWidth(245);
        Button applyFilter = new Button("Apply filter");
        applyFilter.setStyle(buttonStyle(false));
        applyFilter.setOnAction(event -> refreshTelemetry());
        Button reload = new Button("Refresh");
        reload.setStyle(buttonStyle(false));
        reload.setOnAction(event -> refreshTelemetry());
        filterRow.getChildren().addAll(telemetryMachine, telemetryFilter, applyFilter, reload);

        HBox entryRow = new HBox(10);
        entryRow.setAlignment(Pos.CENTER_LEFT);
        telemetryMetric.setPromptText("Signal name");
        telemetryMetric.setPrefWidth(220);
        telemetryValue.setPromptText("Numeric value");
        telemetryValue.setPrefWidth(130);
        telemetryUnit.setPromptText("Unit (°C)");
        telemetryUnit.setPrefWidth(105);
        Button recordButton = new Button("+  Record reading");
        recordButton.setStyle(buttonStyle(true));
        recordButton.setOnAction(event -> recordTelemetry());
        entryRow.getChildren().addAll(telemetryMetric, telemetryValue, telemetryUnit, recordButton);

        telemetryTimeAxis.setLabel("Recorded time");
        telemetryValueAxis.setLabel("Measured value");
        telemetryChart.setTitle("Signal trend");
        telemetryChart.setAnimated(false);
        telemetryChart.setLegendVisible(false);
        telemetryChart.setCreateSymbols(true);
        telemetryChart.setMinHeight(210);
        telemetryChart.setPrefHeight(245);
        telemetryChart.setStyle("-fx-background-color:transparent;-fx-text-fill:" + MUTED + ";");

        telemetryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        telemetryTable.setStyle("-fx-background-color:#111923;-fx-control-inner-background:#111923;"
                + "-fx-table-cell-border-color:#263241;-fx-text-background-color:" + TEXT
                + ";-fx-selection-bar:#1a4846;-fx-selection-bar-non-focused:#263b43;");
        telemetryTable.getColumns().setAll(
                telemetryColumn("RECORDED AT", 1.5, sample -> sample.observedAt()),
                telemetryColumn("SIGNAL", 1.1, sample -> sample.metric()),
                telemetryColumn("VALUE", 0.8, sample -> String.format(java.util.Locale.ROOT, "%.4f", sample.value())),
                telemetryColumn("UNIT", 0.55, sample -> sample.unit()),
                telemetryColumn("SOURCE", 0.7, sample -> sample.source())
        );
        telemetryTable.setPlaceholder(new Label("No readings match the selected machine and signal."));
        telemetryTable.setPrefHeight(310);

        telemetryStatus.setStyle("-fx-text-fill:" + MUTED + ";-fx-font-size:11px;");
        panel.getChildren().addAll(heading, filterRow, entryRow, telemetryChart, telemetryTable, telemetryStatus);
        VBox.setVgrow(telemetryTable, Priority.ALWAYS);
        return panel;
    }

    private Node buildAnomalyPanel() {
        VBox panel = new VBox(14);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color:" + PANEL + ";-fx-background-radius:14;"
                + "-fx-border-color:#293444;-fx-border-radius:14;");

        HBox heading = new HBox(12);
        heading.setAlignment(Pos.CENTER_LEFT);
        VBox titleBox = new VBox(5);
        Label title = new Label("Detected anomalies");
        title.setStyle("-fx-text-fill:" + TEXT + ";-fx-font-size:17px;-fx-font-weight:bold;");
        Label hint = new Label("Rust's adaptive detector learns a baseline per machine and signal.");
        hint.setStyle("-fx-text-fill:" + MUTED + ";-fx-font-size:11px;");
        titleBox.getChildren().addAll(title, hint);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button refresh = new Button("Refresh");
        refresh.setStyle(buttonStyle(false));
        refresh.setOnAction(event -> refreshAnomalies());
        heading.getChildren().addAll(titleBox, spacer, refresh);

        anomalyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        anomalyTable.setStyle("-fx-background-color:#111923;-fx-control-inner-background:#111923;"
                + "-fx-table-cell-border-color:#263241;-fx-text-background-color:" + TEXT
                + ";-fx-selection-bar:#1a4846;-fx-selection-bar-non-focused:#263b43;");
        anomalyTable.getColumns().setAll(
                anomalyColumn("DETECTED AT", 1.3, anomaly -> anomaly.observedAt()),
                anomalyColumn("SIGNAL", 0.8, anomaly -> anomaly.metric()),
                anomalyColumn("SEVERITY", 0.7, anomaly -> anomaly.severity().toUpperCase(java.util.Locale.ROOT)),
                anomalyColumn("SCORE", 0.5, anomaly -> String.format(java.util.Locale.ROOT, "%.2f", anomaly.score())),
                anomalyColumn("EXPLANATION", 2.2, anomaly -> anomaly.explanation())
        );
        anomalyTable.setPlaceholder(new Label("No anomaly events for this machine."));
        anomalyTable.setPrefHeight(480);
        anomalyStatus.setStyle("-fx-text-fill:" + MUTED + ";-fx-font-size:11px;");
        panel.getChildren().addAll(heading, anomalyTable, anomalyStatus);
        VBox.setVgrow(anomalyTable, Priority.ALWAYS);
        return panel;
    }

    private TableColumn<EngineIpcClient.AnomalyDto, String> anomalyColumn(
            String title,
            double widthWeight,
            Function<EngineIpcClient.AnomalyDto, String> value
    ) {
        TableColumn<EngineIpcClient.AnomalyDto, String> column = new TableColumn<>(title);
        column.setMinWidth(90 * widthWeight);
        column.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(safe(value.apply(cell.getValue()))));
        column.setStyle("-fx-text-fill:#aebac9;-fx-font-size:10px;");
        return column;
    }

    private void refreshAnomalies() {
        EngineIpcClient.MachineDto selected = telemetryMachine.getValue();
        if (selected == null) {
            anomalyTable.getItems().clear();
            anomalyStatus.setText("Select a machine to view anomaly events.");
            return;
        }
        anomalyStatus.setText("Loading anomaly events from the Rust engine…");
        Thread.ofVirtual().start(() -> {
            try {
                var anomalies = engine.listAnomalies(selected.id(), 100);
                Platform.runLater(() -> {
                    anomalyTable.getItems().setAll(anomalies);
                    anomalyStatus.setText(anomalies.size() + " persisted anomaly event(s) for "
                            + selected.name() + ".");
                });
            } catch (IOException error) {
                Platform.runLater(() -> anomalyStatus.setText(
                        "Anomaly history failed: " + safe(error.getMessage())));
            }
        });
    }

    private TableColumn<EngineIpcClient.TelemetryDto, String> telemetryColumn(
            String title,
            double widthWeight,
            Function<EngineIpcClient.TelemetryDto, String> value
    ) {
        TableColumn<EngineIpcClient.TelemetryDto, String> column = new TableColumn<>(title);
        column.setMinWidth(90 * widthWeight);
        column.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(safe(value.apply(cell.getValue()))));
        column.setStyle("-fx-text-fill:#aebac9;-fx-font-size:10px;");
        return column;
    }

    private void refreshTelemetry() {
        EngineIpcClient.MachineDto selected = telemetryMachine.getValue();
        if (selected == null) {
            telemetryTable.getItems().clear();
            telemetryStatus.setText("Register or select a machine before adding telemetry.");
            return;
        }
        telemetryStatus.setText("Loading local telemetry history…");
        Thread.ofVirtual().start(() -> {
            try {
                String filter = telemetryFilter.getText() == null ? "" : telemetryFilter.getText().trim();
                var records = engine.listTelemetry(selected.id(), filter.isBlank() ? null : filter, 100);
                Platform.runLater(() -> {
                    telemetryTable.getItems().setAll(records);
                    updateTelemetryChart(records, filter);
                    telemetryStatus.setText("Showing " + records.size()
                            + " newest reading(s) for " + selected.name()
                            + (filter.isBlank() ? "." : " · signal: " + filter));
                });
            } catch (IOException error) {
                Platform.runLater(() ->
                        telemetryStatus.setText("Telemetry history failed: " + safe(error.getMessage())));
            }
        });
    }

    private void updateTelemetryChart(
            java.util.List<EngineIpcClient.TelemetryDto> records,
            String filter
    ) {
        telemetryChart.getData().clear();
        if (filter == null || filter.isBlank()) {
            telemetryChart.setTitle("Enter a signal filter to plot its trend");
            return;
        }
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(filter);
        java.util.List<EngineIpcClient.TelemetryDto> chronological = new java.util.ArrayList<>(records);
        java.util.Collections.reverse(chronological);
        int start = Math.max(0, chronological.size() - 40);
        for (int i = start; i < chronological.size(); i++) {
            EngineIpcClient.TelemetryDto sample = chronological.get(i);
            String timeLabel;
            try {
                timeLabel = java.time.OffsetDateTime.parse(sample.observedAt())
                        .toLocalDateTime()
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            } catch (java.time.format.DateTimeParseException error) {
                timeLabel = sample.observedAt();
            }
            series.getData().add(new XYChart.Data<>(timeLabel, sample.value()));
        }
        telemetryChart.setTitle("Trend · " + filter);
        if (!series.getData().isEmpty()) telemetryChart.getData().add(series);
    }

    private void recordTelemetry() {
        EngineIpcClient.MachineDto selected = telemetryMachine.getValue();
        if (selected == null) {
            telemetryStatus.setText("Select a machine before recording a reading.");
            return;
        }
        String metric = telemetryMetric.getText() == null ? "" : telemetryMetric.getText().trim();
        String rawValue = telemetryValue.getText() == null ? "" : telemetryValue.getText().trim();
        String unit = telemetryUnit.getText() == null ? "" : telemetryUnit.getText().trim();
        if (metric.isBlank() || rawValue.isBlank()) {
            telemetryStatus.setText("Signal name and numeric value are required.");
            return;
        }

        final double value;
        try {
            value = Double.parseDouble(rawValue);
            if (!Double.isFinite(value)) throw new NumberFormatException("Value must be finite.");
        } catch (NumberFormatException error) {
            telemetryStatus.setText("Enter a valid finite numeric reading.");
            return;
        }

        telemetryStatus.setText("Writing telemetry to the Rust engine…");
        Thread.ofVirtual().start(() -> {
            try {
                EngineIpcClient.TelemetryResult saved =
                        engine.recordTelemetry(selected.id(), metric, value, unit, "manual", null);
                String filter = telemetryFilter.getText() == null ? "" : telemetryFilter.getText().trim();
                var records = engine.listTelemetry(selected.id(), filter.isBlank() ? null : filter, 100);
                var anomalies = engine.listAnomalies(selected.id(), 100);
                Platform.runLater(() -> {
                    telemetryTable.getItems().setAll(records);
                    updateTelemetryChart(records, filter);
                    anomalyTable.getItems().setAll(anomalies);
                    telemetryValue.clear();
                    if (saved.anomaly() != null) {
                        EngineIpcClient.AnomalyDto event = saved.anomaly();
                        telemetryStatus.setText("Anomaly detected · " + event.severity()
                                + " · score " + String.format(java.util.Locale.ROOT, "%.2f", event.score())
                                + " · " + event.explanation());
                        anomalyStatus.setText(anomalies.size() + " persisted anomaly event(s) · latest: "
                                + event.metric() + " / " + event.severity());
                    } else {
                        telemetryStatus.setText("Reading saved in native SQLite · " + metric + " = "
                                + value + (unit.isBlank() ? "" : " " + unit)
                                + " · baseline learning continues until enough samples are collected.");
                        anomalyStatus.setText(anomalies.size() + " persisted anomaly event(s) for "
                                + selected.name() + ".");
                    }
                });
            } catch (IOException error) {
                Platform.runLater(() ->
                        telemetryStatus.setText("Could not record reading: " + safe(error.getMessage())));
            }
        });
    }

    private Node metricCard(String title, Label value, String detail) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(16, 18, 16, 18));
        card.setMinWidth(245);
        card.setStyle("-fx-background-color:" + PANEL + ";-fx-background-radius:12;"
                + "-fx-border-color:#293444;-fx-border-radius:12;");
        Label label = new Label(title);
        label.setStyle("-fx-text-fill:" + MUTED + ";-fx-font-size:9px;-fx-font-weight:bold;-fx-letter-spacing:1.4px;");
        value.setStyle("-fx-text-fill:" + TEXT + ";-fx-font-size:25px;-fx-font-weight:bold;");
        Label hint = new Label(detail);
        hint.setStyle("-fx-text-fill:#738195;-fx-font-size:10px;");
        card.getChildren().addAll(label, value, hint);
        return card;
    }

    private void configureTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setStyle("-fx-background-color:#111923;-fx-control-inner-background:#111923;"
                + "-fx-table-cell-border-color:#263241;-fx-text-background-color:" + TEXT
                + ";-fx-selection-bar:#1a4846;-fx-selection-bar-non-focused:#263b43;");

        table.getColumns().setAll(
                column("MACHINE", 1.6, MachineDto -> MachineDto.name()),
                column("ASSET TAG", 1.0, MachineDto -> MachineDto.assetTag()),
                column("TYPE", 1.2, MachineDto -> MachineDto.machineType()),
                column("LOCATION", 1.2, MachineDto -> MachineDto.location()),
                column("STATUS", 0.85, MachineDto -> MachineDto.status())
        );
    }

    private TableColumn<EngineIpcClient.MachineDto, String> column(
            String title,
            double widthWeight,
            Function<EngineIpcClient.MachineDto, String> value
    ) {
        TableColumn<EngineIpcClient.MachineDto, String> column = new TableColumn<>(title);
        column.setMinWidth(90 * widthWeight);
        column.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(safe(value.apply(cell.getValue()))));
        column.setStyle("-fx-text-fill:#aebac9;-fx-font-size:10px;");
        return column;
    }

    private Node emptyTableMessage() {
        VBox message = new VBox(8);
        message.setAlignment(Pos.CENTER);
        Label title = new Label("No native machines yet");
        title.setStyle("-fx-text-fill:" + TEXT + ";-fx-font-size:14px;-fx-font-weight:bold;");
        Label detail = new Label("Add the first asset to the Rust-backed local registry.");
        detail.setStyle("-fx-text-fill:" + MUTED + ";-fx-font-size:11px;");
        message.getChildren().addAll(title, detail);
        return message;
    }

    private Node buildStatusBar() {
        HBox footer = new HBox(10, status);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(12, 28, 12, 28));
        footer.setStyle("-fx-background-color:#121a24;-fx-border-color:#293444;-fx-border-width:1 0 0 0;");
        status.setStyle("-fx-text-fill:" + MUTED + ";-fx-font-size:11px;");
        return footer;
    }

    private void refreshMachines() {
        status.setText("Reading machine registry from the Rust engine…");
        Thread.ofVirtual().start(() -> {
            try {
                var result = engine.listMachines();
                Platform.runLater(() -> {
                    machines.setAll(result);
                    EngineIpcClient.MachineDto previous = telemetryMachine.getValue();
                    telemetryMachine.getItems().setAll(result);
                    if (previous != null) {
                        telemetryMachine.getItems().stream()
                                .filter(machine -> machine.id().equals(previous.id()))
                                .findFirst().ifPresent(telemetryMachine::setValue);
                    }
                    if (telemetryMachine.getValue() == null && !result.isEmpty()) {
                        telemetryMachine.setValue(result.get(0));
                    }
                    updateMetrics();
                    status.setText("Connected · " + result.size() + " native machine record(s)");
                    refreshTelemetry();
                    refreshAnomalies();
                });
            } catch (IOException error) {
                Platform.runLater(() -> status.setText("Registry read failed: " + safe(error.getMessage())));
            }
        });
    }

    private void showCreateMachineDialog() {
        Dialog<MachineDraft> dialog = new Dialog<>();
        dialog.setTitle("Register machine");
        dialog.setHeaderText("Create an asset in the local Rust registry");
        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE),
                new ButtonType("Create machine", ButtonBar.ButtonData.OK_DONE)
        );

        TextField name = field("e.g. Cooling water pump 01");
        TextField assetTag = field("e.g. PUMP-001");
        TextField type = field("e.g. Centrifugal pump");
        TextField location = field("e.g. Line 2 · Utility bay");

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.setPadding(new Insets(16));
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(105);
        ColumnConstraints inputColumn = new ColumnConstraints();
        inputColumn.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(labelColumn, inputColumn);
        addFormRow(form, 0, "Machine name", name);
        addFormRow(form, 1, "Asset tag", assetTag);
        addFormRow(form, 2, "Machine type", type);
        addFormRow(form, 3, "Location", location);
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().setMinWidth(480);

        dialog.setResultConverter(button -> {
            if (button.getButtonData() != ButtonBar.ButtonData.OK_DONE) return null;
            return new MachineDraft(name.getText(), assetTag.getText(), type.getText(), location.getText());
        });

        Optional<MachineDraft> result = dialog.showAndWait();
        result.ifPresent(draft -> createMachine(draft));
    }

    private static TextField field(String placeholder) {
        TextField input = new TextField();
        input.setPromptText(placeholder);
        input.setPrefColumnCount(24);
        return input;
    }

    private static void addFormRow(GridPane form, int row, String title, Node input) {
        Label label = new Label(title);
        label.setStyle("-fx-text-fill:#425064;-fx-font-size:11px;-fx-font-weight:bold;");
        form.add(label, 0, row);
        form.add(input, 1, row);
    }

    private void createMachine(MachineDraft draft) {
        if (blank(draft.name()) || blank(draft.assetTag()) || blank(draft.machineType())) {
            status.setText("Machine name, asset tag and type are required.");
            return;
        }
        status.setText("Creating machine in the Rust engine…");
        Thread.ofVirtual().start(() -> {
            try {
                engine.createMachine(draft.name().trim(), draft.assetTag().trim(),
                        draft.machineType().trim(), draft.location() == null ? "" : draft.location().trim());
                var result = engine.listMachines();
                Platform.runLater(() -> {
                    machines.setAll(result);
                    updateMetrics();
                    status.setText("Machine registered in the native SQLite store.");
                });
            } catch (IOException error) {
                Platform.runLater(() -> status.setText("Could not create machine: " + safe(error.getMessage())));
            }
        });
    }

    private void applySearch(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        visibleMachines.setPredicate(machine -> needle.isEmpty()
                || safe(machine.name()).toLowerCase().contains(needle)
                || safe(machine.assetTag()).toLowerCase().contains(needle)
                || safe(machine.machineType()).toLowerCase().contains(needle)
                || safe(machine.location()).toLowerCase().contains(needle));
    }

    private void updateMetrics() {
        totalAssets.setText(Integer.toString(machines.size()));
        long offline = machines.stream().filter(machine ->
                "offline".equalsIgnoreCase(safe(machine.status()))).count();
        offlineAssets.setText(Long.toString(offline));
    }

    private static String buttonStyle(boolean primary) {
        return primary
                ? "-fx-background-color:#1d5550;-fx-text-fill:#e6fffb;-fx-font-weight:bold;"
                    + "-fx-background-radius:8;-fx-padding:9 14;-fx-cursor:hand;"
                : "-fx-background-color:#273444;-fx-text-fill:#d8e1ec;-fx-font-weight:bold;"
                    + "-fx-background-radius:8;-fx-padding:9 14;-fx-cursor:hand;";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record MachineDraft(String name, String assetTag, String machineType, String location) {}
}
