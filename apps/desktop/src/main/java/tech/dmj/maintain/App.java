package tech.dmj.maintain;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/** JavaFX-first desktop host for the native MAINTAIN AI workspace. */
public final class App extends Application {
    private static final String BG = "#12161d";

    private final EngineSupervisor engine = new EngineSupervisor();
    private final EngineIpcClient engineClient = new EngineIpcClient();
    private final BackendSupervisor backend = new BackendSupervisor();
    private final LocalFrontendServer frontend = new LocalFrontendServer();

    private StackPane root;
    private Stage primaryStage;
    private Stage compatibilityStage;
    private volatile boolean compatibilityStarting;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        root = new StackPane();
        root.setStyle("-fx-background-color:" + BG + ";");
        root.getChildren().add(splash("Starting native workspace", "JavaFX desktop · Rust engine · local SQLite"));

        stage.setTitle("MAINTAIN AI · Native");
        stage.setMinWidth(1120);
        stage.setMinHeight(720);
        stage.setScene(new Scene(root, 1440, 900));
        stage.setOnCloseRequest(event -> shutdown());
        stage.show();

        Thread.ofVirtual().start(() -> {
            try {
                EngineIpcClient.EngineStatus engineStatus = engine.startAndPing();
                Platform.runLater(() -> showNativeWorkspace(engineStatus.engine()));
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
        javafx.scene.control.ProgressIndicator progress = new javafx.scene.control.ProgressIndicator();
        progress.setPrefSize(38, 38);
        panel.getChildren().addAll(brand, title, subtitle, progress);
        return panel;
    }

    private void showNativeWorkspace(String engineName) {
        NativeWorkspaceView workspace = new NativeWorkspaceView(
                engineName,
                engineClient,
                this::openCompatibilityWorkspace
        );
        root.getChildren().setAll(workspace);
        primaryStage.setTitle("MAINTAIN AI · Native Workspace");
        System.out.println("MAINTAIN AI Native ready. Rust engine: " + engineName);
    }

    /**
     * Starts the copied React/FastAPI application only when the user opens the
     * temporary compatibility workspace. The normal desktop start path is JavaFX + Rust.
     */
    private void openCompatibilityWorkspace() {
        if (compatibilityStage != null && compatibilityStage.isShowing()) {
            compatibilityStage.toFront();
            return;
        }
        if (compatibilityStarting) return;
        compatibilityStarting = true;

        Thread.ofVirtual().start(() -> {
            try {
                String api = backend.startAndWait();
                frontend.start();
                frontend.setBackendTarget(api);
                Platform.runLater(() -> showCompatibilityWindow(api));
            } catch (Exception error) {
                Platform.runLater(() -> {
                    compatibilityStarting = false;
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Compatibility workspace unavailable");
                    alert.setHeaderText("The remaining migrated features could not be started");
                    alert.setContentText(error.getMessage() == null ? error.toString() : error.getMessage());
                    alert.showAndWait();
                });
            }
        });
    }

    private void showCompatibilityWindow(String apiUrl) {
        WebView webView = new WebView();
        webView.setContextMenuEnabled(false);
        webView.getEngine().setJavaScriptEnabled(true);
        webView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldState, state) -> {
            if (state == Worker.State.FAILED) {
                System.err.println("Compatibility frontend failed to load: "
                        + webView.getEngine().getLoadWorker().getException());
            }
        });

        Button close = new Button("←  Back to native workspace");
        close.setStyle("-fx-background-color:#263444;-fx-text-fill:#e8eef7;-fx-background-radius:8;"
                + "-fx-padding:8 12;-fx-font-weight:bold;");
        close.setOnAction(event -> compatibilityStage.close());

        Label notice = new Label("COMPATIBILITY MODE  ·  Some workflows are still being migrated from the original stack");
        notice.setStyle("-fx-text-fill:#d6c28c;-fx-font-size:10px;-fx-font-weight:bold;");

        HBox toolbar = new HBox(16, close, notice);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10, 14, 10, 14));
        toolbar.setStyle("-fx-background-color:#141c27;-fx-border-color:#303d4e;-fx-border-width:0 0 1 0;");

        BorderPane shell = new BorderPane(webView);
        shell.setTop(toolbar);
        shell.setStyle("-fx-background-color:#101720;");

        compatibilityStage = new Stage();
        compatibilityStage.initOwner(primaryStage);
        compatibilityStage.setTitle("MAINTAIN AI · Compatibility Workspace");
        compatibilityStage.setMinWidth(1120);
        compatibilityStage.setMinHeight(720);
        compatibilityStage.setScene(new Scene(shell, 1440, 900));
        compatibilityStage.setOnHidden(event -> {
            compatibilityStage = null;
            compatibilityStarting = false;
        });
        compatibilityStage.show();
        webView.getEngine().load(frontend.origin() + "/");
        System.out.println("Compatibility workspace ready. Local API: " + apiUrl);
    }

    private void showStartupError(Exception error) {
        VBox panel = new VBox(12);
        panel.setAlignment(Pos.CENTER_LEFT);
        panel.setMaxWidth(760);
        panel.setPadding(new Insets(30));
        panel.setStyle("-fx-background-color:#1b222d;-fx-background-radius:16;-fx-border-color:#3a4656;-fx-border-radius:16;");
        Label title = new Label("MAINTAIN AI could not start the native Rust engine");
        title.setTextFill(Color.web("#f1f4f8"));
        title.setStyle("-fx-font-size:19px;-fx-font-weight:bold;");
        Label message = new Label(error.getMessage() == null ? error.toString() : error.getMessage());
        message.setWrapText(true);
        message.setTextFill(Color.web("#d5dce7"));
        Label help = new Label("Build the Rust engine with: cargo build --package maintain-engine. Then run from apps/desktop with: gradle run.");
        help.setWrapText(true);
        help.setTextFill(Color.web("#35d0bd"));
        panel.getChildren().addAll(title, message, help);
        root.getChildren().setAll(panel);
        error.printStackTrace(System.err);
    }

    private void shutdown() {
        if (compatibilityStage != null) {
            compatibilityStage.close();
            compatibilityStage = null;
        }
        frontend.close();
        backend.close();
        engine.close();
    }

    @Override
    public void stop() {
        shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
