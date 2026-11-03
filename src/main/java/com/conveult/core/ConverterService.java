package com.conveult.core;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

public class ConverterService {
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "bmp", "gif", "tif", "tiff");
    private static final long MAX_EXTRACT_BYTES = 2L * 1024 * 1024 * 1024;

    public Path convertImage(Path input, Path outputDir, String target, Integer width, Integer height, int quality) throws IOException {
        BufferedImage source = ImageIO.read(input.toFile());
        if (source == null) throw new IOException("This image format is not supported by the installed Java ImageIO readers.");
        int w = width == null ? source.getWidth() : width, h = height == null ? source.getHeight() : height;
        if (width != null && height == null) h = Math.max(1, (int) Math.round((double) source.getHeight() * w / source.getWidth()));
        if (height != null && width == null) w = Math.max(1, (int) Math.round((double) source.getWidth() * h / source.getHeight()));
        String ext = target.toLowerCase(Locale.ROOT); if (ext.equals("jpeg")) ext = "jpg";
        BufferedImage rendered = source;
        if (w != source.getWidth() || h != source.getHeight()) {
            int type = ext.equals("jpg") || ext.equals("bmp") ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
            rendered = new BufferedImage(w, h, type); Graphics2D g = rendered.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC); g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY); g.drawImage(source, 0, 0, w, h, null); g.dispose();
        }
        if (ext.equals("jpg") && rendered.getType() != BufferedImage.TYPE_INT_RGB) {
            BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB); Graphics2D g = rgb.createGraphics(); g.drawImage(rendered, 0, 0, java.awt.Color.WHITE, null); g.dispose(); rendered = rgb;
        }
        Path output = unique(outputDir.resolve(baseName(input) + "-converted." + ext));
        if (ext.equals("jpg")) writeJpeg(rendered, output, quality); else if (!ImageIO.write(rendered, ext, output.toFile())) throw new IOException("No ImageIO writer is installed for ." + ext + ". Try PNG, JPG, BMP, GIF, or TIF.");
        return output;
    }
    private void writeJpeg(BufferedImage image, Path output, int quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg"); if (!writers.hasNext()) throw new IOException("JPEG writer unavailable.");
        ImageWriter writer = writers.next(); try (ImageOutputStream stream = ImageIO.createImageOutputStream(output.toFile())) {
            writer.setOutput(stream); ImageWriteParam p = writer.getDefaultWriteParam(); if (p.canWriteCompressed()) { p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); p.setCompressionQuality(Math.max(0.1f, Math.min(1f, quality / 100f))); }
            writer.write(null, new IIOImage(image, null, null), p);
        } finally { writer.dispose(); }
    }

    public Path convertDocument(Path input, Path outputDir, String target) throws Exception {
        String text = readDocument(input); String ext = target.toLowerCase(Locale.ROOT);
        Path output = unique(outputDir.resolve(baseName(input) + "-converted." + ext));
        switch (ext) {
            case "txt" -> Files.writeString(output, text, StandardCharsets.UTF_8);
            case "md" -> Files.writeString(output, "# " + baseName(input) + "\n\n" + text, StandardCharsets.UTF_8);
            case "html" -> Files.writeString(output, "<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\"><title>" + escapeHtml(baseName(input)) + "</title></head><body><pre>" + escapeHtml(text) + "</pre></body></html>", StandardCharsets.UTF_8);
            case "pdf" -> writePdf(text, output);
            case "docx" -> writeDocx(text, output);
            default -> throw new IOException("Unsupported document output format: " + ext);
        }
        return output;
    }
    private String readDocument(Path input) throws Exception {
        String ext = extension(input);
        return switch (ext) {
            case "txt", "md", "html", "htm", "csv", "json", "xml" -> {
                String raw = Files.readString(input, StandardCharsets.UTF_8);
                if (ext.equals("html") || ext.equals("htm")) yield raw.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ").replaceAll("(?s)<[^>]+>", " ").replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replaceAll("[ \\t]+", " ");
                yield raw;
            }
            case "pdf" -> { try (PDDocument doc = Loader.loadPDF(input.toFile())) { yield new PDFTextStripper().getText(doc); } }
            case "docx" -> { try (InputStream in = Files.newInputStream(input); XWPFDocument doc = new XWPFDocument(in)) { StringJoiner j = new StringJoiner("\n"); for (XWPFParagraph p : doc.getParagraphs()) j.add(p.getText()); yield j.toString(); } }
            case "doc" -> throw new IOException("Legacy .doc reading is not enabled in this starter build. Save it as .docx first.");
            default -> throw new IOException("Unsupported input document type: ." + ext + ". Supported inputs: PDF, DOCX, TXT, MD, HTML, CSV, JSON and XML.");
        };
    }
    private void writePdf(String text, Path output) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER); doc.addPage(page); float margin = 50, y = page.getMediaBox().getHeight() - margin, leading = 14;
            PDPageContentStream stream = new PDPageContentStream(doc, page); stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10); stream.newLineAtOffset(margin, y);
            for (String para : text.replace("\r", "").split("\n", -1)) {
                String remaining = para;
                do {
                    String line = fitLine(remaining, 95); stream.showText(safePdf(line)); stream.newLineAtOffset(0, -leading); y -= leading;
                    remaining = remaining.substring(Math.min(line.length(), remaining.length()));
                    if (y < margin) { stream.endText(); stream.close(); page = new PDPage(PDRectangle.LETTER); doc.addPage(page); stream = new PDPageContentStream(doc, page); stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10); y = page.getMediaBox().getHeight() - margin; stream.newLineAtOffset(margin, y); }
                } while (!remaining.isEmpty());
            }
            stream.endText(); stream.close(); doc.save(output.toFile());
        }
    }
    private String fitLine(String s, int limit) { if (s.length() <= limit) return s; int cut = s.lastIndexOf(' ', limit); return s.substring(0, cut > 0 ? cut : limit); }
    private String safePdf(String s) { StringBuilder b = new StringBuilder(); for (char c : s.toCharArray()) b.append(c >= 32 && c <= 255 ? c : '?'); return b.toString(); }
    private void writeDocx(String text, Path output) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) { for (String line : text.split("\\R", -1)) { XWPFParagraph p = doc.createParagraph(); XWPFRun r = p.createRun(); r.setText(line); } try (OutputStream out = Files.newOutputStream(output)) { doc.write(out); } }
    }
    private String escapeHtml(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

    public Path convertVideo(Path input, Path outputDir, String target, Consumer<String> status) throws Exception {
        String executable = System.getenv().getOrDefault("FFMPEG_PATH", "ffmpeg");
        Path output = unique(outputDir.resolve(baseName(input) + "-converted." + target));
        List<String> command = List.of(executable, "-y", "-i", input.toAbsolutePath().toString(), "-progress", "pipe:1", "-nostats", output.toAbsolutePath().toString());
        Process process;
        try { process = new ProcessBuilder(command).redirectErrorStream(true).start(); }
        catch (IOException e) { throw new IOException("FFmpeg was not found. Install FFmpeg and ensure `ffmpeg` is on PATH, or set FFMPEG_PATH to its executable.", e); }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) { if (line.startsWith("out_time=")) status.accept("Video conversion running — " + line.substring(9)); else if (line.startsWith("progress=end")) status.accept("Finalizing video…"); }
        }
        int exit = process.waitFor(); if (exit != 0) { Files.deleteIfExists(output); throw new IOException("FFmpeg exited with code " + exit + ". Check that the input is a valid video and the selected output container is supported."); }
        return output;
    }

    public Path createZip(Path input, Path outputDir) throws IOException {
        Path output = unique(outputDir.resolve(baseName(input) + ".zip"));
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(output)))) {
            if (Files.isDirectory(input)) {
                try (var paths = Files.walk(input)) { for (Path p : paths.filter(Files::isRegularFile).toList()) { Path rel = input.relativize(p); zip.putNextEntry(new ZipEntry(rel.toString().replace('\\', '/'))); Files.copy(p, zip); zip.closeEntry(); } }
            } else { zip.putNextEntry(new ZipEntry(input.getFileName().toString())); Files.copy(input, zip); zip.closeEntry(); }
        } catch (IOException | RuntimeException e) { Files.deleteIfExists(output); throw e; }
        return output;
    }
    public Path extractZip(Path input, Path outputDir) throws IOException {
        if (!extension(input).equals("zip")) throw new IOException("Choose a .zip file to extract.");
        Path destination = Files.createTempDirectory(outputDir, "converge-extract-").normalize(); long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(input)))) {
            ZipEntry entry; byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                Path target = destination.resolve(entry.getName()).normalize();
                if (!target.startsWith(destination)) throw new IOException("Unsafe ZIP entry path detected.");
                if (entry.isDirectory()) Files.createDirectories(target); else {
                    Files.createDirectories(target.getParent()); try (OutputStream out = Files.newOutputStream(target)) { int n; while ((n = zip.read(buffer)) > 0) { total += n; if (total > MAX_EXTRACT_BYTES) throw new IOException("Extraction stopped: archive expands beyond the 2 GB safety limit."); out.write(buffer, 0, n); } }
                } zip.closeEntry();
            }
        } catch (IOException | RuntimeException e) { deleteTree(destination); throw e; }
        return destination;
    }
    private void deleteTree(Path root) { try (var paths = Files.walk(root)) { paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} }); } catch (IOException ignored) {} }
    private String extension(Path p) { String n = p.getFileName().toString(); int i = n.lastIndexOf('.'); return i < 0 ? "" : n.substring(i + 1).toLowerCase(Locale.ROOT); }
    private String baseName(Path p) { String n = p.getFileName().toString(); int i = n.lastIndexOf('.'); return i > 0 ? n.substring(0, i) : n; }
    private Path unique(Path p) { if (!Files.exists(p)) return p; String name = p.getFileName().toString(); int i = name.lastIndexOf('.'); String stem = i > 0 ? name.substring(0, i) : name, ext = i > 0 ? name.substring(i) : ""; int n = 2; Path candidate; do { candidate = p.resolveSibling(stem + "-" + n++ + ext); } while (Files.exists(candidate)); return candidate; }
}
