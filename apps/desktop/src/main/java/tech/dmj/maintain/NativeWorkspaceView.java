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
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
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
        content.getChildren().addAll(heading, metrics, registry);
        VBox.setVgrow(registry, Priority.ALWAYS);
        return content;
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
                    updateMetrics();
                    status.setText("Connected · " + result.size() + " native machine record(s)");
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
