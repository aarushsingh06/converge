# converge — Ultimate File Converter

A Java 21 + JavaFX desktop file conversion app inspired by the supplied project overview and core-engine deep dive. It includes background conversion tasks, image resizing/compression, document text extraction and export, optional FFmpeg video transcoding, and ZIP create/extract support.

## Features
- **Images:** PNG, JPG/JPEG, BMP, GIF and TIF where supported by the local Java ImageIO installation. Resize by width/height while preserving aspect ratio if only one dimension is supplied. JPEG quality control.
- **Documents:** Input: PDF, DOCX, TXT, Markdown, HTML, CSV, JSON and XML. Output: PDF, DOCX, TXT, Markdown and HTML. Document conversions preserve extracted text; they do not guarantee original layout, images, tables, comments, or advanced formatting.
- **Video:** MP4, MOV, AVI, MKV and WebM output choices via FFmpeg. Install FFmpeg separately.
- **Archives:** Create ZIP from a file or directory; safely extract ZIP files with path traversal protection and a 2 GB expanded-size limit.
- **Core engine:** Conversion runs off the JavaFX UI thread; output names avoid overwriting existing files.

## Requirements
- JDK 21 (or compatible Java 21 distribution)
- Maven 3.9+
- FFmpeg only for video conversion
- Internet access on the first Maven run to download dependencies

## Run on macOS
```bash
java -version
mvn -version
mvn clean javafx:run
```
If Maven is missing and Homebrew is installed: `brew install maven`. Install FFmpeg for video: `brew install ffmpeg`.

## Run on Windows
Install JDK 21 and Maven, then run `mvn clean javafx:run` from the project folder. Install FFmpeg separately and ensure it is on PATH for video conversion.

## Optional FFmpeg path
Set `FFMPEG_PATH` to the full FFmpeg executable path if it is not named `ffmpeg` on PATH.

## Build
```bash
mvn clean package
```
The JavaFX Maven plugin is the recommended run method because it configures JavaFX modules for the current platform.

## Architecture
- `com.conveult.App`: JavaFX UI and JavaFX `Task` lifecycle.
- `com.conveult.core.ConverterService`: conversion implementations and external-process integration.
- Apache PDFBox: PDF text extraction and PDF writing.
- Apache POI: DOCX text extraction and writing.
- Java ImageIO: raster image processing.
- `ProcessBuilder`: FFmpeg invocation without shell interpolation.
- `ZipInputStream` / `ZipOutputStream`: archive support.

## Current limitations
- “Any format to any format” is not realistic without many format-specific engines. The UI lists formats, but some codecs/formats depend on the local Java runtime or FFmpeg build.
- DOC legacy `.doc` input and `.odt` are not implemented. Convert `.doc` to `.docx` first in an office suite. PDF/DOCX conversion is text-focused and may lose formatting.
- GIF conversion exports a still frame when read by ImageIO; animated GIF preservation is not guaranteed.
- Video progress currently reports FFmpeg status text rather than a precise percentage.
- No file is uploaded to a server; processing is local to the computer.

## License
GPL-2.0-or-later. See `LICENSE`.
