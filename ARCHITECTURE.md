# Core engine architecture

```text
JavaFX UI
  -> validate input, output folder, and conversion options
  -> JavaFX Task (background thread; UI remains responsive)
  -> ConverterService
       -> ImageIO pipeline (decode -> optional resize -> encode/compress)
       -> PDFBox / Apache POI document pipeline (extract text -> output writer)
       -> ProcessBuilder -> FFmpeg (video transcode, optional dependency)
       -> ZIP streams (create/extract with path safety checks)
  -> result path or actionable exception
  -> UI updates progress/status and shows result
```

## State and concurrency
- UI controls are read on the JavaFX application thread before the task starts.
- Conversion work runs on a daemon worker thread through JavaFX `Task`.
- Task message/progress properties are bound to the UI.
- The app does not block the JavaFX thread while image/document/video work runs.

## Memory constraints
- Raster images are decoded to a `BufferedImage`; extremely large images can use substantial heap. For large images, increase JVM heap or use smaller input files.
- Document processing extracts text into memory in this starter version. Very large files may require streaming conversion.
- ZIP extraction limits expanded content to 2 GiB and prevents archive entries from escaping the destination directory.

## External process safety
- FFmpeg is invoked with `ProcessBuilder` and a list of arguments, not a shell command string.
- FFmpeg must be installed separately. Its output is read while the process runs to avoid a blocked process pipe.

## Future work
- Streaming document pipelines and cancellation/timeout controls.
- Rich-format office conversion via LibreOffice headless mode.
- Better progress percentages using media duration and FFmpeg timestamps.
- Native image codecs (e.g. WebP/HEIC) through optional dependencies.
- Queue/multiple-file conversion, drag-and-drop, and a conversion history panel.
