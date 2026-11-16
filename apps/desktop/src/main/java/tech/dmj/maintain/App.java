package tech.dmj.maintain;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/** JavaFX desktop host for the migrated MAINTAIN AI React application. */
public final class App extends Application {
    private static final String BG = "#12161d";
    private final EngineSupervisor engine = new EngineSupervisor();
    private final BackendSupervisor backend = new BackendSupervisor();
    private final LocalFrontendServer frontend = new LocalFrontendServer();
    private StackPane root;

    @Override public void start(Stage stage) {
        root = new StackPane();
        root.setStyle("-fx-background-color:" + BG + ";");
        root.getChildren().add(splash("Starting local services", "Rust engine · migrated FastAPI · SQLite"));
        stage.setTitle("MAINTAIN AI · Native");
        stage.setMinWidth(1120);
        stage.setMinHeight(720);
        stage.setScene(new Scene(root, 1440, 900));
        stage.setOnCloseRequest(event -> shutdown());
        stage.show();

        Thread.ofVirtual().start(() -> {
            try {
                EngineIpcClient.EngineStatus engineStatus = engine.startAndPing();
                frontend.start();
                String api = backend.startAndWait();
                frontend.setBackendTarget(api);
                Platform.runLater(() -> showMigratedApplication(stage, engineStatus.engine(), api));
            } catch (Exception error) {
                Platform.runLater(() -> showStartupError(error));
            }
        });
    }

    private static VBox splash(String headline, String detail) {
        VBox panel = new VBox(14);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(36));
        Label brand = new Label("MAINTAIN AI");
        brand.setTextFill(Color.web("#e8eef7"));
        brand.setStyle("-fx-font-size:24px;-fx-font-weight:bold;-fx-letter-spacing:2px;");
        Label title = new Label(headline);
        title.setTextFill(Color.web("#dfe7f0"));
        Label subtitle = new Label(detail);
        subtitle.setTextFill(Color.web("#8996a8"));
        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(38, 38);
        panel.getChildren().addAll(brand, title, subtitle, progress);
        return panel;
    }

    private void showMigratedApplication(Stage stage, String engineName, String apiUrl) {
        WebView webView = new WebView();
        webView.setContextMenuEnabled(false);
        webView.getEngine().setJavaScriptEnabled(true);
        webView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldState, state) -> {
            if (state == Worker.State.FAILED) {
                System.err.println("React frontend failed to load: " + webView.getEngine().getLoadWorker().getException());
            }
        });
        root.getChildren().setAll(webView);
        webView.getEngine().load(frontend.origin() + "/");
        stage.setTitle("MAINTAIN AI · Native");
        System.out.println("MAINTAIN AI Native ready. Rust: " + engineName + "; local API: " + apiUrl);
        System.out.println("SQLite: " + BackendSupervisor.applicationDataDirectory().resolve("maintain-ai.sqlite3"));
    }

    private void showStartupError(Exception error) {
        VBox panel = new VBox(12);
        panel.setAlignment(Pos.CENTER_LEFT);
        panel.setMaxWidth(760);
        panel.setPadding(new Insets(30));
        panel.setStyle("-fx-background-color:#1b222d;-fx-background-radius:16;-fx-border-color:#3a4656;-fx-border-radius:16;");
        Label title = new Label("MAINTAIN AI could not start its local services");
        title.setTextFill(Color.web("#f1f4f8"));
        title.setStyle("-fx-font-size:19px;-fx-font-weight:bold;");
        Label message = new Label(error.getMessage() == null ? error.toString() : error.getMessage());
        message.setWrapText(true);
        message.setTextFill(Color.web("#d5dce7"));
        Label log = new Label("Backend log: " + BackendSupervisor.applicationDataDirectory().resolve("logs/backend.log"));
        log.setWrapText(true);
        log.setTextFill(Color.web("#8996a8"));
        Label help = new Label("Run from apps/desktop with: gradle run. First setup needs Rust, JDK 21, Node.js 20 and Python 3.");
        help.setWrapText(true);
        help.setTextFill(Color.web("#35d0bd"));
        panel.getChildren().addAll(title, message, log, help);
        root.getChildren().setAll(panel);
        error.printStackTrace(System.err);
    }

    private void shutdown() {
        frontend.close();
        backend.close();
        engine.close();
    }

    @Override public void stop() { shutdown(); }
    public static void main(String[] args) { launch(args); }
}
