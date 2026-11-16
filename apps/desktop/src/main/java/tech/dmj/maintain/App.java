package tech.dmj.maintain;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.beans.property.ReadOnlyStringWrapper;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Native shell with local Rust engine connection and first machine registry workflow. */
public final class App extends Application {
    private static final String BG = "#12161d";
    private static final String PANEL = "rgba(28,35,46,0.88)";
    private static final String MUTED = "#8996a8";
    private static final String TEAL = "#35d0bd";

    private final EngineSupervisor engine = new EngineSupervisor();
    private final EngineIpcClient ipc = new EngineIpcClient();
    private final StackPane pageStack = new StackPane();
    private final TableView<EngineIpcClient.MachineDto> machineTable = new TableView<>();
    private final Label engineState = new Label("ENGINE  /  CONNECTING");
    private final Label machineCount = new Label("—");
    private final VBox dashboardPage = new VBox(16);
    private final VBox machinesPage = new VBox(16);
    private final List<Label> navigationItems = new java.util.ArrayList<>();

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:" + BG + ";-fx-font-family:'IBM Plex Sans','Segoe UI';");

        VBox sidebar = buildSidebar();
        buildDashboard();
        buildMachinesPage();

        pageStack.getChildren().addAll(dashboardPage, machinesPage);
        machinesPage.setVisible(false);
        machinesPage.setManaged(false);

        root.setLeft(sidebar);
        root.setCenter(pageStack);

        Scene scene = new Scene(root, 1440, 900);
        stage.setTitle("MAINTAIN AI Native");
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> engine.close());
        stage.show();

        Thread.ofVirtual().start(() -> {
            try {
                EngineIpcClient.EngineStatus status = engine.startAndPing();
                Platform.runLater(() -> {
                    engineState.setText("ENGINE  /  ONLINE");
                    engineState.setTextFill(Color.web(TEAL));
                    engineState.setStyle(stateStyle("53,208,189"));
                    engineState.setTooltip(new Tooltip(status.engine() + " · protocol " + status.protocolVersion()));
                });
                refreshMachines();
            } catch (Exception error) {
                Platform.runLater(() -> {
                    engineState.setText("ENGINE  /  OFFLINE");
                    engineState.setTextFill(Color.web("#ef7777"));
                    engineState.setStyle(stateStyle("239,119,119"));
                    engineState.setTooltip(new Tooltip(error.getMessage()));
                });
            }
        });
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(8);
        sidebar.setPadding(new Insets(24,14,24,14));
        sidebar.setPrefWidth(236);
        sidebar.setStyle("-fx-background-color:rgba(18,23,31,0.96);-fx-border-color:#273140;-fx-border-width:0 1 0 0;");

        Label brand = new Label("MAINTAIN AI");
        brand.setTextFill(Color.web("#e8eef7"));
        brand.setFont(Font.font("IBM Plex Sans",20));
        Label edition = new Label("NATIVE  /  INDUSTRIAL");
        edition.setTextFill(Color.web(TEAL));
        edition.setStyle("-fx-font-size:10px;-fx-letter-spacing:2px;");
        sidebar.getChildren().addAll(brand,edition,new Region());

        String[] navs = {"Dashboard","Machines","Maintenance","Work Orders","Fault Log",
                "AI Assistant","Alerts","Spare Parts","Reports","AI Model Lab","History","Settings","About"};
        for (String item : navs) {
            Label nav = new Label(item);
            nav.setMaxWidth(Double.MAX_VALUE);
            nav.setPadding(new Insets(11,13,11,13));
            nav.setTextFill(Color.web(item.equals("Dashboard") ? "#eaf7f5" : MUTED));
            nav.setStyle(item.equals("Dashboard") ? activeNavStyle() : inactiveNavStyle());
            nav.setOnMouseClicked(event -> showPage(item));
            navigationItems.add(nav);
            sidebar.getChildren().add(nav);
        }
        return sidebar;
    }

    private void showPage(String page) {
        boolean dashboard = page.equals("Dashboard");
        boolean machines = page.equals("Machines");
        dashboardPage.setVisible(dashboard);
        dashboardPage.setManaged(dashboard);
        machinesPage.setVisible(machines);
        machinesPage.setManaged(machines);
        for (Label item : navigationItems) {
            boolean active = item.getText().equals(page);
            item.setStyle(active ? activeNavStyle() : inactiveNavStyle());
        }
        if (machines) {
            refreshMachines();
        }
    }

    private static String activeNavStyle() {
        return "-fx-background-color:rgba(53,208,189,0.13);-fx-border-color:transparent transparent transparent "
                + TEAL + ";-fx-border-width:0 0 0 3;-fx-background-radius:8;";
    }

    private static String inactiveNavStyle() {
        return "-fx-background-radius:8;";
    }

    private void buildDashboard() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(26,30,16,30));
        Label eyebrow = new Label("LOCAL OPERATIONS  /  OVERVIEW");
        eyebrow.setTextFill(Color.web(TEAL));
        eyebrow.setStyle("-fx-font-size:10px;-fx-letter-spacing:2px;");
        Label title = new Label("Dashboard");
        title.setTextFill(Color.web("#edf2f8"));
        title.setFont(Font.font("IBM Plex Sans",28));
        engineState.setTextFill(Color.web("#f2c879"));
        engineState.setStyle(stateStyle("242,200,121"));
        HBox titleRow = new HBox(18,title,engineState);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(eyebrow,titleRow);

        HBox metrics = new HBox(14,
                metric("REGISTERED MACHINES",machineCount,"Equipment registry"),
                metric("OPEN WORK ORDERS",new Label("—"),"Maintenance queue"),
                metric("ACTIVE ALERTS",new Label("—"),"Local analytics and rules"),
                metric("ENGINE STATUS",new Label("LOCAL"),"Rust service"));
        metrics.setPadding(new Insets(12,30,18,30));

        VBox content = new VBox(16);
        content.setPadding(new Insets(0,30,30,30));
        VBox activity = panel("Operational activity","Local events will appear here when the event service is connected.");
        VBox fleet = panel("Fleet health","Machine health distribution will appear here after assets are registered.");
        HBox lower = new HBox(14,activity,fleet);
        HBox.setHgrow(activity,Priority.ALWAYS);
        HBox.setHgrow(fleet,Priority.ALWAYS);
        content.getChildren().addAll(metrics,lower);
        dashboardPage.getChildren().addAll(header,content);
    }

    private void buildMachinesPage() {
        VBox header = new VBox(8);
        header.setPadding(new Insets(26,30,6,30));
        Label eyebrow = new Label("ASSET MANAGEMENT  /  REGISTRY");
        eyebrow.setTextFill(Color.web(TEAL));
        eyebrow.setStyle("-fx-font-size:10px;-fx-letter-spacing:2px;");
        Label title = new Label("Machines");
        title.setTextFill(Color.web("#edf2f8"));
        title.setFont(Font.font("IBM Plex Sans",28));
        header.getChildren().addAll(eyebrow,title);

        Button add = new Button("+  Add machine");
        add.setDefaultButton(true);
        add.setStyle("-fx-background-color:#237f79;-fx-text-fill:white;-fx-background-radius:8;-fx-padding:10 16 10 16;");
        add.setOnAction(event -> showCreateMachineDialog());

        HBox toolbar = new HBox(add);
        toolbar.setAlignment(Pos.CENTER_RIGHT);
        toolbar.setPadding(new Insets(0,30,8,30));

        configureMachineTable();
        VBox tableCard = new VBox(machineTable);
        tableCard.setPadding(new Insets(8));
        tableCard.setStyle("-fx-background-color:" + PANEL + ";-fx-background-radius:14;-fx-border-color:#2a3544;-fx-border-radius:14;");
        VBox.setVgrow(tableCard,Priority.ALWAYS);

        machinesPage.setPadding(new Insets(0,0,26,0));
        machinesPage.getChildren().addAll(header,toolbar,tableCard);
        VBox.setVgrow(tableCard,Priority.ALWAYS);
    }

    private void configureMachineTable() {
        machineTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        machineTable.setPlaceholder(new Label("No machines registered yet. Add your first asset to begin."));
        machineTable.setStyle("-fx-background-color:transparent;-fx-control-inner-background:#1b222d;-fx-table-cell-border-color:#2a3544;");
        TableColumn<EngineIpcClient.MachineDto,String> name = textColumn("Machine","name");
        TableColumn<EngineIpcClient.MachineDto,String> tag = textColumn("Asset tag","assetTag");
        TableColumn<EngineIpcClient.MachineDto,String> type = textColumn("Machine type","machineType");
        TableColumn<EngineIpcClient.MachineDto,String> location = textColumn("Location","location");
        TableColumn<EngineIpcClient.MachineDto,String> status = textColumn("Status","status");
        machineTable.getColumns().setAll(name,tag,type,location,status);
    }

    private static TableColumn<EngineIpcClient.MachineDto,String> textColumn(String title,String field) {
        TableColumn<EngineIpcClient.MachineDto,String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> {
            EngineIpcClient.MachineDto m = cell.getValue();
            String value = switch (field) {
                case "name" -> m.name();
                case "assetTag" -> m.assetTag();
                case "machineType" -> m.machineType();
                case "location" -> m.location();
                case "status" -> m.status();
                default -> "";
            };
            return new ReadOnlyStringWrapper(value);
        });
        return column;
    }

    private void showCreateMachineDialog() {
        Dialog<MachineInput> dialog = new Dialog<>();
        dialog.setTitle("Register machine");
        dialog.setHeaderText("Add an asset to the local registry");
        ButtonType create = new ButtonType("Register",ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(create,ButtonType.CANCEL);

        TextField name = new TextField();
        name.setPromptText("e.g. CNC Mill 01");
        TextField tag = new TextField();
        tag.setPromptText("e.g. CNC-001");
        TextField type = new TextField();
        type.setPromptText("e.g. CNC milling machine");
        TextField location = new TextField();
        location.setPromptText("e.g. Workshop A");

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.setPadding(new Insets(18,10,10,10));
        form.addRow(0,new Label("Machine name *"),name);
        form.addRow(1,new Label("Asset tag *"),tag);
        form.addRow(2,new Label("Machine type *"),type);
        form.addRow(3,new Label("Location"),location);
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().setPrefWidth(520);

        dialog.setResultConverter(button -> button == create
                ? new MachineInput(name.getText(),tag.getText(),type.getText(),location.getText()) : null);

        Optional<MachineInput> result = dialog.showAndWait();
        result.ifPresent(input -> {
            if (input.name().isBlank() || input.assetTag().isBlank() || input.machineType().isBlank()) {
                showError("Missing required fields","Name, asset tag, and machine type are required.");
                return;
            }
            Thread.ofVirtual().start(() -> {
                try {
                    ipc.createMachine(input.name(),input.assetTag(),input.machineType(),input.location());
                    List<EngineIpcClient.MachineDto> machines = ipc.listMachines();
                    Platform.runLater(() -> {
                        machineTable.setItems(FXCollections.observableArrayList(machines));
                        machineCount.setText(Integer.toString(machines.size()));
                    });
                } catch (Exception error) {
                    Platform.runLater(() -> showError("Could not register machine",error.getMessage()));
                }
            });
        });
    }

    private void refreshMachines() {
        Thread.ofVirtual().start(() -> {
            try {
                List<EngineIpcClient.MachineDto> machines = ipc.listMachines();
                Platform.runLater(() -> {
                    machineTable.setItems(FXCollections.observableArrayList(machines));
                    machineCount.setText(Integer.toString(machines.size()));
                });
            } catch (IOException error) {
                Platform.runLater(() -> machineCount.setText("—"));
            }
        });
    }

    private void showError(String title,String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null ? "An unexpected error occurred." : message);
        alert.showAndWait();
    }

    private static VBox metric(String heading,Label value,String hint) {
        VBox box = panel(heading,hint);
        value.setTextFill(Color.web("#f2f6fa"));
        value.setFont(Font.font("IBM Plex Mono",30));
        box.getChildren().add(1,value);
        box.setPrefHeight(142);
        HBox.setHgrow(box,Priority.ALWAYS);
        return box;
    }

    private static VBox panel(String heading,String subtitle) {
        VBox box = new VBox(12);
        box.setPadding(new Insets(20));
        box.setStyle("-fx-background-color:" + PANEL + ";-fx-background-radius:14;-fx-border-color:#2a3544;-fx-border-radius:14;");
        Label title = new Label(heading);
        title.setTextFill(Color.web("#dfe7f0"));
        title.setStyle("-fx-font-size:13px;-fx-font-weight:bold;");
        Label body = new Label(subtitle);
        body.setWrapText(true);
        body.setTextFill(Color.web(MUTED));
        body.setStyle("-fx-font-size:12px;");
        box.getChildren().addAll(title,body);
        return box;
    }

    private static String stateStyle(String rgb) {
        return "-fx-font-size:10px;-fx-letter-spacing:1px;-fx-padding:8 12 8 12;"
                + "-fx-background-color:rgba(" + rgb + ",0.12);-fx-background-radius:8;";
    }

    @Override public void stop() { engine.close(); }
    public static void main(String[] args) { launch(args); }

    private record MachineInput(String name,String assetTag,String machineType,String location) {}
}
