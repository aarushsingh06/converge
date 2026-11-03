package com.conveult;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import com.conveult.core.ConverterService;

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class App extends Application {
    private final ConverterService converter = new ConverterService();
    private File selectedFile;
    private Path outputDirectory = Path.of(System.getProperty("user.home"), "converge-Output");
    private final Label fileLabel = new Label("No file selected");
    private final Label outputLabel = new Label();
    private final Label statusLabel = new Label("Ready when you are.");
    private final ProgressBar progress = new ProgressBar(0);
    private final ComboBox<String> category = new ComboBox<>();
    private final ComboBox<String> format = new ComboBox<>();
    private final TextField width = new TextField();
    private final TextField height = new TextField();
    private final Slider quality = new Slider(10, 100, 85);
    private final Label qualityLabel = new Label("85%");
    private final Button convertButton = new Button("Convert file");

    @Override public void start(Stage stage) {
        stage.setTitle("converge | File Converter");
        category.getItems().addAll("Image", "Document", "Video", "Archive");
        category.setValue("Image"); updateFormats();
        category.setOnAction(e -> updateFormats());
        quality.valueProperty().addListener((o, a, b) -> qualityLabel.setText(b.intValue() + "%"));

        Label brand = new Label("converge"); brand.getStyleClass().add("brand");
        Label subtitle = new Label("One workspace for your files."); subtitle.getStyleClass().add("subtitle");
        VBox heading = new VBox(4, brand, subtitle);
        Button choose = new Button("Choose file"); choose.setOnAction(e -> chooseFile(stage));
        Button chooseOutput = new Button("Choose output folder"); chooseOutput.setOnAction(e -> chooseOutput(stage));
        fileLabel.setWrapText(true); outputLabel.setWrapText(true); refreshOutputLabel();
        VBox fileCard = card("01  INPUT FILE", fileLabel, choose);
        VBox outputCard = card("02  OUTPUT LOCATION", outputLabel, chooseOutput);

        GridPane settings = new GridPane(); settings.setHgap(14); settings.setVgap(12);
        settings.add(labelled("Conversion type", category), 0, 0); settings.add(labelled("Convert to", format), 1, 0);
        width.setPromptText("Auto"); height.setPromptText("Auto"); width.setMaxWidth(Double.MAX_VALUE); height.setMaxWidth(Double.MAX_VALUE);
        settings.add(labelled("Image width (px)", width), 0, 1); settings.add(labelled("Image height (px)", height), 1, 1);
        HBox qualityRow = new HBox(12, quality, qualityLabel); qualityRow.setAlignment(Pos.CENTER_LEFT); quality.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(quality, Priority.ALWAYS);
        settings.add(labelled("JPEG quality", qualityRow), 0, 2, 2, 1);
        Label hint = new Label("Image sizing and quality apply to image conversions. Video conversion requires FFmpeg installed on your system. Archive mode creates or extracts ZIP files."); hint.setWrapText(true); hint.getStyleClass().add("hint");
        convertButton.getStyleClass().add("primary"); convertButton.setOnAction(e -> startConversion());
        progress.setMaxWidth(Double.MAX_VALUE); progress.setPrefHeight(8);
        VBox processing = card("03  PROCESSING", statusLabel, progress, convertButton);
        VBox content = new VBox(18, heading, fileCard, outputCard, card("04  CONVERSION SETTINGS", settings, hint), processing);
        content.setPadding(new Insets(28)); content.setMaxWidth(760);
        StackPane root = new StackPane(content); root.getStyleClass().add("root");
        ScrollPane scroll = new ScrollPane(root); scroll.setFitToWidth(true); scroll.getStyleClass().add("scroll");
        Scene scene = new Scene(scroll, 850, 900); scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        stage.setMinWidth(650); stage.setMinHeight(700); stage.setScene(scene); stage.show();
    }

    private VBox card(String title, javafx.scene.Node... nodes) {
        Label heading = new Label(title); heading.getStyleClass().add("section-title");
        VBox box = new VBox(12); box.getStyleClass().add("card"); box.getChildren().add(heading); box.getChildren().addAll(nodes); return box;
    }
    private VBox labelled(String title, javafx.scene.Node control) { Label l = new Label(title); l.getStyleClass().add("field-label"); return new VBox(7, l, control); }
    private Label label(String text) { return new Label(text); }
    private void chooseFile(Stage stage) {
        FileChooser chooser = new FileChooser(); chooser.setTitle("Choose a file to convert");
        File f = chooser.showOpenDialog(stage); if (f != null) { selectedFile = f; fileLabel.setText(f.getName() + "  •  " + readableSize(f.length())); statusLabel.setText("File ready for conversion."); }
    }
    private void chooseOutput(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser(); chooser.setTitle("Choose output folder");
        File f = chooser.showDialog(stage); if (f != null) { outputDirectory = f.toPath(); refreshOutputLabel(); }
    }
    private void refreshOutputLabel() { outputLabel.setText(outputDirectory.toAbsolutePath().toString()); }
    private void updateFormats() {
        String c = category.getValue();
        List<String> values = switch (c == null ? "Image" : c) {
            case "Image" -> List.of("png", "jpg", "bmp", "gif", "tif");
            case "Document" -> List.of("pdf", "docx", "txt", "md", "html");
            case "Video" -> List.of("mp4", "mov", "avi", "mkv", "webm");
            default -> List.of("zip", "extract ZIP");
        };
        format.getItems().setAll(values); format.setValue(values.get(0));
        boolean image = "Image".equals(c); width.setDisable(!image); height.setDisable(!image); quality.setDisable(!image); qualityLabel.setDisable(!image);
    }
    private void startConversion() {
        if (selectedFile == null) { alert("Choose a file", "Please choose an input file first."); return; }
        try { java.nio.file.Files.createDirectories(outputDirectory); } catch (Exception e) { alert("Output folder error", e.getMessage()); return; }
        Integer w = parseDimension(width.getText()), h = parseDimension(height.getText());
        if ((!width.getText().isBlank() && w == null) || (!height.getText().isBlank() && h == null)) { alert("Invalid dimensions", "Width and height must be positive whole numbers."); return; }
        String kind = category.getValue(), target = format.getValue(); Path input = selectedFile.toPath(), out = outputDirectory;
        int q = (int) quality.getValue();
        Task<Path> task = new Task<>() { @Override protected Path call() throws Exception {
            updateMessage("Preparing conversion…"); updateProgress(0, 1);
            Path result = switch (kind) {
                case "Image" -> converter.convertImage(input, out, target, w, h, q);
                case "Document" -> converter.convertDocument(input, out, target);
                case "Video" -> converter.convertVideo(input, out, target, this::updateMessage);
                default -> "extract ZIP".equals(target) ? converter.extractZip(input, out) : converter.createZip(input, out);
            };
            updateProgress(1, 1); updateMessage("Finished: " + result.getFileName()); return result;
        }};
        progress.progressProperty().bind(task.progressProperty()); statusLabel.textProperty().bind(task.messageProperty());
        convertButton.disableProperty().bind(task.runningProperty());
        task.setOnSucceeded(e -> { statusLabel.textProperty().unbind(); convertButton.disableProperty().unbind(); convertButton.setDisable(false); statusLabel.setText("Success — saved to " + task.getValue()); });
        task.setOnFailed(e -> { statusLabel.textProperty().unbind(); convertButton.disableProperty().unbind(); convertButton.setDisable(false); progress.progressProperty().unbind(); progress.setProgress(0); statusLabel.setText("Conversion failed."); alert("Conversion failed", task.getException().getMessage() == null ? task.getException().toString() : task.getException().getMessage()); });
        Thread thread = new Thread(task, "converge-converter"); thread.setDaemon(true); thread.start();
    }
    private Integer parseDimension(String s) { if (s == null || s.isBlank()) return null; try { int n = Integer.parseInt(s.trim()); return n > 0 ? n : null; } catch (NumberFormatException e) { return null; } }
    private String readableSize(long bytes) { if (bytes < 1024) return bytes + " B"; if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0); return String.format("%.1f MB", bytes / (1024.0 * 1024)); }
    private void alert(String title, String message) { Alert a = new Alert(Alert.AlertType.ERROR); a.setTitle(title); a.setHeaderText(title); a.setContentText(message == null ? "Unknown error" : message); a.showAndWait(); }
    public static void main(String[] args) { launch(args); }
}
