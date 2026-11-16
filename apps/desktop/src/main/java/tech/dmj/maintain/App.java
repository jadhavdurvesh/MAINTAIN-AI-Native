package tech.dmj.maintain;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

/** Native shell with a real Rust engine startup and IPC health check. */
public final class App extends Application {
    private static final String BG = "#12161d";
    private static final String PANEL = "rgba(28,35,46,0.88)";
    private static final String MUTED = "#8996a8";
    private static final String TEAL = "#35d0bd";
    private final EngineSupervisor engine = new EngineSupervisor();

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:" + BG + ";-fx-font-family:'IBM Plex Sans','Segoe UI';");

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
        for (int i=0;i<navs.length;i++) {
            Label item = new Label(navs[i]);
            item.setMaxWidth(Double.MAX_VALUE);
            item.setPadding(new Insets(11,13,11,13));
            item.setTextFill(Color.web(i==0 ? "#eaf7f5" : MUTED));
            item.setStyle(i==0
                    ? "-fx-background-color:rgba(53,208,189,0.13);-fx-border-color:transparent transparent transparent " + TEAL + ";-fx-border-width:0 0 0 3;-fx-background-radius:8;"
                    : "-fx-background-radius:8;");
            sidebar.getChildren().add(item);
        }

        VBox header = new VBox(10);
        header.setPadding(new Insets(26,30,16,30));
        Label eyebrow = new Label("LOCAL OPERATIONS  /  OVERVIEW");
        eyebrow.setTextFill(Color.web(TEAL));
        eyebrow.setStyle("-fx-font-size:10px;-fx-letter-spacing:2px;");
        Label title = new Label("Dashboard");
        title.setTextFill(Color.web("#edf2f8"));
        title.setFont(Font.font("IBM Plex Sans",28));
        Label engineState = new Label("ENGINE  /  CONNECTING");
        engineState.setTextFill(Color.web("#f2c879"));
        engineState.setStyle("-fx-font-size:10px;-fx-letter-spacing:1px;-fx-padding:8 12 8 12;-fx-background-color:rgba(242,200,121,0.10);-fx-background-radius:8;");
        HBox titleRow = new HBox(18,title,engineState);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(eyebrow,titleRow);

        HBox metrics = new HBox(14,
                metric("REGISTERED MACHINES","—","Equipment registry"),
                metric("OPEN WORK ORDERS","—","Maintenance queue"),
                metric("ACTIVE ALERTS","—","Local analytics and rules"),
                metric("ENGINE STATUS","LOCAL","Rust service"));
        metrics.setPadding(new Insets(12,30,18,30));

        VBox content = new VBox(16);
        content.setPadding(new Insets(0,30,30,30));
        VBox activity = panel("Operational activity","Local events will appear here when the event service is connected.");
        VBox fleet = panel("Fleet health","Machine health distribution will appear here after assets are registered.");
        HBox lower = new HBox(14,activity,fleet);
        HBox.setHgrow(activity,Priority.ALWAYS);
        HBox.setHgrow(fleet,Priority.ALWAYS);
        content.getChildren().addAll(metrics,lower);

        BorderPane main = new BorderPane();
        main.setTop(header);
        main.setCenter(content);
        root.setLeft(sidebar);
        root.setCenter(main);

        Scene scene = new Scene(root,1440,900);
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
                    engineState.setStyle("-fx-font-size:10px;-fx-letter-spacing:1px;-fx-padding:8 12 8 12;-fx-background-color:rgba(53,208,189,0.12);-fx-background-radius:8;");
                    engineState.setTooltip(new javafx.scene.control.Tooltip(status.engine()
                            + " · protocol " + status.protocolVersion()));
                });
            } catch (Exception error) {
                Platform.runLater(() -> {
                    engineState.setText("ENGINE  /  OFFLINE");
                    engineState.setTextFill(Color.web("#ef7777"));
                    engineState.setStyle("-fx-font-size:10px;-fx-letter-spacing:1px;-fx-padding:8 12 8 12;-fx-background-color:rgba(239,119,119,0.12);-fx-background-radius:8;");
                    engineState.setTooltip(new javafx.scene.control.Tooltip(error.getMessage()));
                });
            }
        });
    }

    private static VBox metric(String heading,String value,String hint) {
        VBox box=panel(heading,hint);
        Label number=new Label(value);
        number.setTextFill(Color.web("#f2f6fa"));
        number.setFont(Font.font("IBM Plex Mono",30));
        box.getChildren().add(1,number);
        box.setPrefHeight(142);
        HBox.setHgrow(box,Priority.ALWAYS);
        return box;
    }

    private static VBox panel(String heading,String subtitle) {
        VBox box=new VBox(12);
        box.setPadding(new Insets(20));
        box.setStyle("-fx-background-color:"+PANEL+";-fx-background-radius:14;-fx-border-color:#2a3544;-fx-border-radius:14;");
        Label title=new Label(heading);
        title.setTextFill(Color.web("#dfe7f0"));
        title.setStyle("-fx-font-size:13px;-fx-font-weight:bold;");
        Label body=new Label(subtitle);
        body.setWrapText(true);
        body.setTextFill(Color.web(MUTED));
        body.setStyle("-fx-font-size:12px;");
        box.getChildren().addAll(title,body);
        return box;
    }

    @Override
    public void stop() {
        engine.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
