import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.Preferences;

/** Local video trimmer backed by FFmpeg. It never downloads videos from YouTube. */
public class ClipEditorPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int MAX_VIDEO_SECONDS = 10 * 60;
    private static final String PREF_FFMPEG = "ffmpeg.executable";

    private final JFrame owner;
    private final JTextField sourceField = new JTextField();
    private final JTextField outputField = new JTextField();
    private final JTextField startField = new JTextField("00:00:00");
    private final JTextField endField = new JTextField("00:00:30");
    private final JLabel preview = new JLabel("Escolha um vídeo para visualizar", SwingConstants.CENTER);
    private final JLabel playheadLabel = new JLabel("00:00:00 / 00:00:00");
    private final JButton playButton = new JButton("▶ Reproduzir seleção");
    private final JButton markStartButton = new JButton("Marcar início");
    private final JButton markEndButton = new JButton("Marcar fim");
    private final Timeline timeline = new Timeline();
    private final JTextArea status = new JTextArea("Escolha um arquivo de vídeo local para começar.");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JButton exportButton = new JButton("Exportar clipe em alta qualidade");
    private final JButton cancelButton = new JButton("Cancelar");
    private final JLabel ffmpegLabel = new JLabel();
    private volatile Process currentProcess;
    private volatile boolean cancellationRequested;
    private SwingWorker<MediaInfo, Integer> worker;
    private File sourceFile;
    private File outputFile;
    private boolean outputWasChosen;
    private MediaInfo loadedMedia;
    private double playheadSeconds;
    private volatile Process previewProcess;
    private volatile Process frameProcess;
    private BufferedImage currentPreviewFrame;
    private long playbackStart;
    private int playbackFrame;
    private int frameRequestId;
    private SwingWorker<MediaInfo, Void> loadWorker;
    private SwingWorker<java.util.List<BufferedImage>, Void> thumbnailWorker;
    private volatile Process thumbnailProcess;
    private SwingWorker<BufferedImage, Void> seekWorker;
    private final Timer seekDebounce = new Timer(180, e -> requestPreviewFrame());

    public ClipEditorPanel(JFrame owner) {
        this.owner = owner;
        setLayout(new BorderLayout(0, 20));
        setBackground(new Color(36, 40, 48));
        setBorder(new EmptyBorder(28, 32, 28, 32));
        add(createHeader(), BorderLayout.NORTH);
        add(createEditorCard(), BorderLayout.CENTER);
        seekDebounce.setRepeats(false);
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && !isShowing()) {
                stopPlayback();
                Process frame = frameProcess;
                if (frame != null) frame.destroyForcibly();
            }
        });
        updateFfmpegLabel();
    }

    private JComponent createHeader() {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Editor de clipes");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(Color.WHITE);
        JLabel description = new JLabel("Corte um trecho de um arquivo local e exporte em MP4 com alta qualidade.");
        description.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        description.setForeground(new Color(165, 175, 190));
        header.add(title);
        header.add(Box.createVerticalStrut(8));
        header.add(description);
        return header;
    }

    private JComponent createEditorCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(new Color(44, 49, 60));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(55, 62, 75), 1, true),
                new EmptyBorder(24, 26, 24, 26)));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8, 8, 8, 8);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;

        addRow(card, g, 0, "Arquivo original", createFileRow(sourceField, "Selecionar vídeo…", this::chooseSource));
        addRow(card, g, 2, "Prévia e seleção visual do trecho", createPreviewEditor());
        addRow(card, g, 4, "Salvar clipe como", createFileRow(outputField, "Escolher destino…", this::chooseOutput));

        JPanel times = new JPanel(new GridLayout(1, 2, 18, 0));
        times.setOpaque(false);
        times.add(createTimeField("Início (HH:MM:SS)", startField));
        times.add(createTimeField("Fim (HH:MM:SS)", endField));
        javax.swing.event.DocumentListener rangeListener = new javax.swing.event.DocumentListener() {
            private void changed() { updateTimelineFromFields(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
        };
        startField.getDocument().addDocumentListener(rangeListener);
        endField.getDocument().addDocumentListener(rangeListener);
        addRow(card, g, 6, "Ajuste preciso do trecho (opcional)", times);

        JPanel qualityInfo = new JPanel();
        qualityInfo.setLayout(new BoxLayout(qualityInfo, BoxLayout.Y_AXIS));
        qualityInfo.setBackground(new Color(31, 42, 60));
        qualityInfo.setBorder(new EmptyBorder(14, 16, 14, 16));
        JLabel qualityTitle = new JLabel("Resolução original · H.264 CRF 18 · áudio AAC 192 kb/s");
        qualityTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        qualityTitle.setForeground(new Color(195, 215, 242));
        JLabel qualityDescription = new JLabel("Mantém as dimensões do vídeo fonte sem ampliar vídeos pequenos. O arquivo original não é alterado.");
        qualityDescription.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        qualityDescription.setForeground(new Color(165, 180, 202));
        qualityInfo.add(qualityTitle);
        qualityInfo.add(Box.createVerticalStrut(5));
        qualityInfo.add(qualityDescription);
        g.gridx = 0; g.gridy = 8; g.gridwidth = 2;
        card.add(qualityInfo, g);

        JPanel ffmpegRow = new JPanel(new BorderLayout(12, 0));
        ffmpegRow.setOpaque(false);
        ffmpegLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        ffmpegLabel.setForeground(new Color(165, 175, 190));
        JButton locateFfmpeg = new JButton("Localizar FFmpeg…");
        locateFfmpeg.addActionListener(e -> chooseFfmpeg());
        ffmpegRow.add(ffmpegLabel, BorderLayout.CENTER);
        ffmpegRow.add(locateFfmpeg, BorderLayout.EAST);
        g.gridy = 9;
        card.add(ffmpegRow, g);

        status.setEditable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setOpaque(false);
        status.setForeground(new Color(165, 175, 190));
        status.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        status.setRows(3);
        g.gridy = 10;
        card.add(status, g);

        progress.setVisible(false);
        progress.setStringPainted(true);
        progress.setString("Preparando exportação…");
        g.gridy = 11;
        card.add(progress, g);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> cancelExport());
        exportButton.setBackground(new Color(66, 133, 244));
        exportButton.setForeground(Color.WHITE);
        exportButton.setFocusPainted(false);
        exportButton.addActionListener(e -> startExport());
        actions.add(cancelButton);
        actions.add(exportButton);
        g.gridy = 12;
        card.add(actions, g);

        JLabel note = new JLabel("Use o arquivo original salvo no seu computador. A API do YouTube não fornece o arquivo original para edição.");
        note.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        note.setForeground(new Color(145, 155, 172));
        g.gridy = 13;
        card.add(note, g);
        return new JScrollPane(card, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {{
            setBorder(null);
            getViewport().setBackground(new Color(36, 40, 48));
        }};
    }

    private JComponent createPreviewEditor() {
        JPanel editor = new JPanel(new BorderLayout(0, 10));
        editor.setOpaque(false);

        preview.setOpaque(true);
        preview.setBackground(new Color(13, 16, 21));
        preview.setForeground(new Color(165, 175, 190));
        preview.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        preview.setPreferredSize(new Dimension(800, 450));
        preview.setMinimumSize(new Dimension(480, 270));
        preview.setBorder(BorderFactory.createLineBorder(new Color(60, 68, 82)));
        preview.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                if (currentPreviewFrame != null) showPreviewFrame(currentPreviewFrame);
            }
        });
        editor.add(preview, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        JButton back = new JButton("−1s");
        JButton forward = new JButton("+1s");
        JButton stop = new JButton("Parar");
        for (JButton button : new JButton[]{back, playButton, stop, forward, markStartButton, markEndButton}) {
            button.setFocusPainted(false);
            controls.add(button);
        }
        playButton.setBackground(new Color(66, 133, 244));
        playButton.setForeground(Color.WHITE);
        playButton.addActionListener(e -> togglePlayback());
        stop.addActionListener(e -> stopPlayback());
        back.addActionListener(e -> seekTo(Math.max(0, playheadSeconds - 1)));
        forward.addActionListener(e -> seekTo(Math.min(durationSeconds(), playheadSeconds + 1)));
        markStartButton.addActionListener(e -> setRangeStart((long) playheadSeconds));
        markEndButton.addActionListener(e -> setRangeEnd((long) playheadSeconds));

        JPanel timelineBox = new JPanel(new BorderLayout(0, 6));
        timelineBox.setOpaque(false);
        timeline.setPreferredSize(new Dimension(800, 76));
        timeline.setFocusable(true);
        timeline.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("LEFT"), "seek-back");
        timeline.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("RIGHT"), "seek-forward");
        timeline.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("SPACE"), "toggle-playback");
        timeline.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke('I'), "mark-in");
        timeline.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke('O'), "mark-out");
        timeline.getActionMap().put("seek-back", new AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent e) { seekTo(Math.max(0, playheadSeconds - 1)); } });
        timeline.getActionMap().put("seek-forward", new AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent e) { seekTo(Math.min(durationSeconds(), playheadSeconds + 1)); } });
        timeline.getActionMap().put("toggle-playback", new AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent e) { togglePlayback(); } });
        timeline.getActionMap().put("mark-in", new AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent e) { setRangeStart((long) playheadSeconds); } });
        timeline.getActionMap().put("mark-out", new AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent e) { setRangeEnd((long) playheadSeconds); } });
        timelineBox.add(timeline, BorderLayout.CENTER);
        JPanel timelineInfo = new JPanel(new BorderLayout());
        timelineInfo.setOpaque(false);
        playheadLabel.setForeground(new Color(185, 195, 210));
        timelineInfo.add(playheadLabel, BorderLayout.WEST);
        JLabel hint = new JLabel("Arraste as alças para escolher início e fim; clique na linha do tempo para buscar.");
        hint.setForeground(new Color(145, 155, 172));
        timelineInfo.add(hint, BorderLayout.EAST);
        timelineBox.add(timelineInfo, BorderLayout.SOUTH);

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);
        bottom.add(controls, BorderLayout.NORTH);
        bottom.add(timelineBox, BorderLayout.CENTER);
        editor.add(bottom, BorderLayout.SOUTH);
        return editor;
    }

    private void addRow(JPanel panel, GridBagConstraints g, int row, String label, JComponent input) {
        JLabel title = new JLabel(label);
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(new Color(205, 215, 230));
        g.gridx = 0; g.gridy = row; g.gridwidth = 2; g.weightx = 1;
        panel.add(title, g);
        g.gridy = row + 1;
        panel.add(input, g);
    }

    private JPanel createFileRow(JTextField field, String buttonText, Runnable action) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        field.setEditable(false);
        row.add(field, BorderLayout.CENTER);
        JButton button = new JButton(buttonText);
        button.addActionListener(e -> action.run());
        row.add(button, BorderLayout.EAST);
        return row;
    }

    private JPanel createTimeField(String label, JTextField field) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JLabel title = new JLabel(label);
        title.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        title.setForeground(new Color(165, 175, 190));
        field.setToolTipText("Formato horas:minutos:segundos. Exemplo: 00:01:25");
        panel.add(title, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private void chooseSource() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Selecione o vídeo original salvo localmente");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Vídeos (MP4, MOV, MKV, AVI, WebM)", "mp4", "mov", "mkv", "avi", "webm", "m4v"));
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return;
        sourceFile = chooser.getSelectedFile();
        File selectedSource = sourceFile;
        stopPlayback();
        seekDebounce.stop();
        frameRequestId++;
        if (loadWorker != null) loadWorker.cancel(true);
        if (thumbnailWorker != null) thumbnailWorker.cancel(true);
        Process oldThumbnailProcess = thumbnailProcess;
        if (oldThumbnailProcess != null) oldThumbnailProcess.destroyForcibly();
        if (seekWorker != null) seekWorker.cancel(true);
        Process pendingFrame = frameProcess;
        if (pendingFrame != null) pendingFrame.destroyForcibly();
        loadedMedia = null;
        currentPreviewFrame = null;
        timeline.reset();
        preview.setIcon(null);
        preview.setText("Carregando prévia do vídeo…");
        sourceField.setText(sourceFile.getAbsolutePath());
        if (!outputWasChosen) setDefaultOutputFor(sourceFile);
        setStatus("Lendo duração, resolução e quadros de prévia…", false);
        loadWorker = new SwingWorker<>() {
            @Override protected MediaInfo doInBackground() throws Exception {
                MediaInfo media = probeMedia(selectedSource);
                if (media.durationSeconds > MAX_VIDEO_SECONDS) throw new java.io.IOException(
                        "Este vídeo tem " + formatDuration(media.durationSeconds) + ". O editor aceita arquivos de até 10 minutos.");
                return media;
            }
            @Override protected void done() {
                if (!selectedSource.equals(sourceFile)) return;
                try {
                    loadedMedia = get();
                    long duration = Math.max(1, (long) Math.ceil(loadedMedia.durationSeconds));
                    long initialEnd = Math.min(30, duration);
                    startField.setText("00:00:00");
                    endField.setText(formatTime(initialEnd));
                    timeline.setMedia(duration, java.util.Collections.emptyList());
                    playheadSeconds = 0;
                    updateTimeDisplay();
                    requestPreviewFrame();
                    setStatus("Prévia pronta · " + loadedMedia.width + " × " + loadedMedia.height + " · " + formatDuration(loadedMedia.durationSeconds), false);
                    loadTimelineThumbnails(selectedSource, loadedMedia);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    preview.setText("Não foi possível abrir a prévia deste vídeo.");
                    setStatus(cause.getMessage(), true);
                }
            }
        };
        loadWorker.execute();
    }

    private void loadTimelineThumbnails(File file, MediaInfo media) {
        thumbnailWorker = new SwingWorker<>() {
            @Override protected java.util.List<BufferedImage> doInBackground() throws Exception {
                return extractTimelineFrames(file, media.durationSeconds, 12);
            }
            @Override protected void done() {
                if (!file.equals(sourceFile) || isCancelled()) return;
                thumbnailProcess = null;
                try { timeline.setFrames(get()); }
                catch (Exception ignored) { /* O quadro principal continua disponível sem miniaturas. */ }
            }
        };
        thumbnailWorker.execute();
    }

    private void updateTimelineFromFields() {
        if (loadedMedia == null) return;
        try {
            long start = parseTime(startField.getText());
            long end = parseTime(endField.getText());
            timeline.setRange(start, end);
        } catch (IllegalArgumentException ignored) {
            timeline.repaint();
        }
    }

    private void setRangeStart(long seconds) {
        long end = safeParse(endField.getText(), seconds + 1);
        seconds = Math.max(0, Math.min(seconds, end - 1));
        startField.setText(formatTime(seconds));
        updateTimelineFromFields();
    }

    private void setRangeEnd(long seconds) {
        long start = safeParse(startField.getText(), 0);
        seconds = Math.max(start + 1, Math.min(seconds, (long) Math.ceil(durationSeconds())));
        endField.setText(formatTime(seconds));
        updateTimelineFromFields();
    }

    private long safeParse(String value, long fallback) {
        try { return parseTime(value); } catch (IllegalArgumentException ignored) { return fallback; }
    }

    private double durationSeconds() {
        return loadedMedia == null ? 0 : loadedMedia.durationSeconds;
    }

    private void seekTo(double seconds) {
        if (loadedMedia == null) return;
        seekDebounce.stop();
        playheadSeconds = Math.max(0, Math.min(seconds, loadedMedia.durationSeconds));
        stopPlayback();
        timeline.setPlayhead(playheadSeconds);
        updateTimeDisplay();
        requestPreviewFrame();
    }

    private void updateTimeDisplay() {
        playheadLabel.setText(formatTime((long) playheadSeconds) + " / " +
                (loadedMedia == null ? "00:00:00" : formatDuration(loadedMedia.durationSeconds)));
        timeline.setPlayhead(playheadSeconds);
    }

    private void requestPreviewFrame() {
        if (sourceFile == null || loadedMedia == null) return;
        final File requestedFile = sourceFile;
        final double requestedTime = playheadSeconds;
        final int request = ++frameRequestId;
        Process running = frameProcess;
        if (running != null) running.destroyForcibly();
        if (seekWorker != null) seekWorker.cancel(true);
        seekWorker = new SwingWorker<>() {
            @Override protected BufferedImage doInBackground() throws Exception {
                java.util.List<String> command = java.util.Arrays.asList(ffmpegExecutable(), "-hide_banner", "-loglevel", "error",
                        "-ss", String.format(java.util.Locale.ROOT, "%.3f", requestedTime), "-i", requestedFile.getAbsolutePath(),
                        "-frames:v", "1", "-vf", "scale=960:540:force_original_aspect_ratio=decrease", "-f", "image2pipe", "-vcodec", "mjpeg", "pipe:1");
                Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                frameProcess = process;
                byte[] bytes = process.getInputStream().readAllBytes();
                int exit = process.waitFor();
                if (exit != 0 || bytes.length == 0) throw new java.io.IOException("FFmpeg não conseguiu criar o quadro de prévia.");
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (image == null) throw new java.io.IOException("O formato do quadro de prévia não é compatível.");
                return image;
            }
            @Override protected void done() {
                if (request != frameRequestId || isCancelled()) return;
                frameProcess = null;
                try {
                    currentPreviewFrame = get();
                    showPreviewFrame(currentPreviewFrame);
                } catch (Exception ignored) {
                    preview.setIcon(null);
                    preview.setText("Prévia indisponível para este formato.");
                }
            }
        };
        seekWorker.execute();
    }

    private void showPreviewFrame(BufferedImage image) {
        int maxWidth = Math.max(1, preview.getWidth() - 20);
        int maxHeight = Math.max(1, preview.getHeight() - 20);
        double scale = Math.min((double) maxWidth / image.getWidth(), (double) maxHeight / image.getHeight());
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        preview.setText("");
        preview.setIcon(new ImageIcon(image.getScaledInstance(width, height, Image.SCALE_SMOOTH)));
    }

    private java.util.List<BufferedImage> extractTimelineFrames(File file, double duration, int count) throws Exception {
        java.util.List<BufferedImage> frames = new java.util.ArrayList<>();
        for (int i = 0; i < count && !Thread.currentThread().isInterrupted(); i++) {
            double timestamp = Math.min(Math.max(0, duration - 0.1), (i + 0.5) * duration / count);
            String filter = "scale=180:102:force_original_aspect_ratio=decrease,pad=180:102:(ow-iw)/2:(oh-ih)/2";
            java.util.List<String> command = java.util.Arrays.asList(ffmpegExecutable(), "-hide_banner", "-loglevel", "error",
                    "-ss", String.format(java.util.Locale.ROOT, "%.3f", timestamp), "-i", file.getAbsolutePath(),
                    "-frames:v", "1", "-vf", filter, "-f", "image2pipe", "-vcodec", "mjpeg", "-q:v", "7", "pipe:1");
            Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            thumbnailProcess = process;
            byte[] bytes = process.getInputStream().readAllBytes();
            int exit = process.waitFor();
            if (exit == 0 && bytes.length > 0) {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (image != null) frames.add(image);
            }
        }
        if (frames.isEmpty()) throw new java.io.IOException("Não foi possível criar miniaturas para a linha do tempo.");
        return frames;
    }

    private java.util.List<BufferedImage> splitJpegFrames(byte[] bytes) throws Exception {
        java.util.List<BufferedImage> frames = new java.util.ArrayList<>();
        ByteArrayOutputStream frame = null;
        int previous = -1;
        for (byte value : bytes) {
            int current = value & 0xff;
            if (frame == null && previous == 0xff && current == 0xd8) {
                frame = new ByteArrayOutputStream();
                frame.write(0xff); frame.write(0xd8);
            } else if (frame != null) {
                frame.write(current);
                if (previous == 0xff && current == 0xd9) {
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(frame.toByteArray()));
                    if (image != null) frames.add(image);
                    frame = null;
                }
            }
            previous = current;
        }
        return frames;
    }

    private void togglePlayback() {
        if (previewProcess != null && previewProcess.isAlive()) { stopPlayback(); return; }
        if (sourceFile == null || loadedMedia == null) { showError("Selecione um vídeo para reproduzir a prévia."); return; }
        seekDebounce.stop();
        if (thumbnailWorker != null) thumbnailWorker.cancel(true);
        Process thumbnail = thumbnailProcess;
        if (thumbnail != null) thumbnail.destroyForcibly();
        frameRequestId++;
        Process pendingFrame = frameProcess;
        if (pendingFrame != null) pendingFrame.destroyForcibly();
        if (seekWorker != null) seekWorker.cancel(true);
        long start = safeParse(startField.getText(), 0);
        long end = safeParse(endField.getText(), (long) loadedMedia.durationSeconds);
        if (playheadSeconds < start || playheadSeconds >= end) playheadSeconds = start;
        playbackStart = (long) playheadSeconds;
        playbackFrame = 0;
        playButton.setText("❚❚ Pausar");
        timeline.setPlaying(true);
        final File playingFile = sourceFile;
        final long selectionEnd = end;
        new Thread(() -> {
            Process process = null;
            try {
                long remaining = Math.max(1, selectionEnd - playbackStart);
                java.util.List<String> command = java.util.Arrays.asList(ffmpegExecutable(), "-hide_banner", "-loglevel", "error",
                        "-ss", Long.toString(playbackStart), "-re", "-i", playingFile.getAbsolutePath(), "-t", Long.toString(remaining),
                        "-vf", "fps=15,scale=960:540:force_original_aspect_ratio=decrease", "-an", "-f", "image2pipe", "-vcodec", "mjpeg", "-q:v", "6", "pipe:1");
                process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                previewProcess = process;
                final Process activeProcess = process;
                byte[] bytes = new byte[8192];
                ByteArrayOutputStream frame = null;
                int previous = -1;
                int read;
                while (!Thread.currentThread().isInterrupted() && (read = process.getInputStream().read(bytes)) != -1) {
                    for (int i = 0; i < read; i++) {
                        int current = bytes[i] & 0xff;
                        if (frame == null && previous == 0xff && current == 0xd8) {
                            frame = new ByteArrayOutputStream(); frame.write(0xff); frame.write(0xd8);
                        } else if (frame != null) {
                            frame.write(current);
                            if (previous == 0xff && current == 0xd9) {
                                BufferedImage image = ImageIO.read(new ByteArrayInputStream(frame.toByteArray()));
                                frame = null;
                                if (image != null) {
                                    final BufferedImage next = image;
                                    final double position = playbackStart + playbackFrame++ / 15.0;
                                    SwingUtilities.invokeLater(() -> {
                                        if (previewProcess == activeProcess) {
                                            currentPreviewFrame = next;
                                            playheadSeconds = position;
                                            showPreviewFrame(next);
                                            updateTimeDisplay();
                                        }
                                    });
                                }
                            }
                        }
                        previous = current;
                    }
                }
                process.waitFor();
            } catch (Exception ignored) {
                if (process != null) process.destroyForcibly();
            } finally {
                Process finishedProcess = process;
                SwingUtilities.invokeLater(() -> {
                    if (previewProcess == finishedProcess) {
                        previewProcess = null;
                        playButton.setText("▶ Reproduzir seleção");
                        timeline.setPlaying(false);
                        if (loadedMedia != null) {
                            playheadSeconds = Math.min(selectionEnd, loadedMedia.durationSeconds);
                            timeline.setPlayhead(playheadSeconds);
                            updateTimeDisplay();
                        }
                    }
                });
            }
        }, "clip-preview").start();
    }

    private void stopPlayback() {
        Process process = previewProcess;
        previewProcess = null;
        if (process != null) process.destroyForcibly();
        playButton.setText("▶ Reproduzir seleção");
        timeline.setPlaying(false);
    }

    private void setPlayheadFromTimeline(double seconds) {
        playheadSeconds = Math.max(0, Math.min(seconds, durationSeconds()));
        updateTimeDisplay();
    }

    private void setRangeFromTimeline(long start, long end) {
        startField.setText(formatTime(start));
        endField.setText(formatTime(end));
        updateTimelineFromFields();
    }

    private String formatTime(long seconds) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private void chooseOutput() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Salvar clipe MP4");
        File downloads = downloadsDirectory();
        chooser.setCurrentDirectory(downloads);
        chooser.setSelectedFile(outputFile != null ? outputFile : new File(downloads, "clipe.mp4"));
        if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) return;
        File selected = chooser.getSelectedFile();
        if (!selected.getName().toLowerCase().endsWith(".mp4")) selected = new File(selected.getParentFile(), selected.getName() + ".mp4");
        outputFile = selected;
        outputWasChosen = true;
        outputField.setText(outputFile.getAbsolutePath());
    }

    private void setDefaultOutputFor(File source) {
        String name = source.getName().replaceFirst("(?i)\\.[^.]+$", "");
        outputFile = new File(downloadsDirectory(), name + "_clipe.mp4");
        outputField.setText(outputFile.getAbsolutePath());
    }

    private File downloadsDirectory() {
        File home = new File(System.getProperty("user.home", "."));
        File downloads = new File(home, "Downloads");
        if (downloads.isDirectory()) return downloads;
        try {
            Files.createDirectories(downloads.toPath());
            return downloads;
        } catch (Exception ignored) {
            return home;
        }
    }

    private void chooseFfmpeg() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Selecione ffmpeg.exe");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("FFmpeg (ffmpeg.exe)", "exe"));
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return;
        File selected = chooser.getSelectedFile();
        if (!selected.getName().equalsIgnoreCase("ffmpeg.exe") && !selected.getName().equalsIgnoreCase("ffmpeg")) {
            showError("Selecione o executável ffmpeg.exe.");
            return;
        }
        Preferences.userNodeForPackage(ClipEditorPanel.class).put(PREF_FFMPEG, selected.getAbsolutePath());
        updateFfmpegLabel();
    }

    private String ffmpegExecutable() {
        return resolveToolExecutable("ffmpeg");
    }

    private void updateFfmpegLabel() {
        String configured = Preferences.userNodeForPackage(ClipEditorPanel.class).get(PREF_FFMPEG, "").trim();
        String ffmpeg = ffmpegExecutable();
        if (isExecutableFile(ffmpeg)) {
            ffmpegLabel.setText("FFmpeg: " + ffmpeg + " · FFprobe " + (isExecutableFile(ffprobeExecutable()) ? "disponível" : "não encontrado"));
        } else {
            ffmpegLabel.setText("FFmpeg/FFprobe: " + (isOnPath() ? "disponíveis no PATH" : "não encontrados; instale e localize ffmpeg.exe"));
        }
    }

    private boolean isOnPath() {
        try {
            Process probe = new ProcessBuilder(ffmpegExecutable(), "-version").redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            boolean exited = probe.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            if (!exited) probe.destroyForcibly();
            if (!exited || probe.exitValue() != 0) return false;
            Process ffprobe = new ProcessBuilder(ffprobeExecutable(), "-version").redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            boolean ffprobeExited = ffprobe.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            if (!ffprobeExited) ffprobe.destroyForcibly();
            return ffprobeExited && ffprobe.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String ffprobeExecutable() {
        String configured = Preferences.userNodeForPackage(ClipEditorPanel.class).get(PREF_FFMPEG, "").trim();
        if (!configured.isEmpty()) {
            try {
                Path sibling = Path.of(configured).resolveSibling(toolName("ffprobe"));
                if (isExecutableFile(sibling.toString())) return sibling.toString();
            } catch (Exception ignored) { }
        }
        return resolveToolExecutable("ffprobe");
    }

    private String resolveToolExecutable(String tool) {
        String executable = toolName(tool);
        if ("ffmpeg".equals(tool)) {
            String configured = Preferences.userNodeForPackage(ClipEditorPanel.class).get(PREF_FFMPEG, "").trim();
            try {
                if (!configured.isEmpty() && isExecutableFile(configured)) return configured;
            } catch (Exception ignored) { }
        }
        String pathValue = System.getenv("PATH");
        if (pathValue != null) {
            for (String directory : pathValue.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                File candidate = new File(directory, executable);
                if (candidate.isFile()) return candidate.getAbsolutePath();
            }
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (isWindows() && localAppData != null) {
            Path packages = Path.of(localAppData, "Microsoft", "WinGet", "Packages");
            try (java.nio.file.DirectoryStream<Path> publishers = Files.newDirectoryStream(packages, "Gyan.FFmpeg_*")) {
                for (Path publisher : publishers) {
                    try (java.nio.file.DirectoryStream<Path> builds = Files.newDirectoryStream(publisher)) {
                        for (Path build : builds) {
                            Path candidate = build.resolve("bin").resolve(executable);
                            if (candidate.toFile().isFile()) return candidate.toAbsolutePath().toString();
                        }
                    }
                }
            } catch (Exception ignored) { }
        }
        return executable;
    }

    private String toolName(String tool) {
        return isWindows() ? tool + ".exe" : tool;
    }

    private boolean isExecutableFile(String path) {
        return path != null && new File(path).isFile();
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private void startExport() {
        if (sourceFile == null || !sourceFile.isFile()) { showError("Selecione um arquivo de vídeo local válido."); return; }
        if (outputFile == null) { chooseOutput(); if (outputFile == null) return; }
        long start, end;
        try {
            start = parseTime(startField.getText());
            end = parseTime(endField.getText());
        } catch (IllegalArgumentException e) { showError(e.getMessage()); return; }
        if (end <= start) { showError("O tempo final deve ser maior que o tempo inicial."); return; }
        if (end > MAX_VIDEO_SECONDS) { showError("O vídeo e o trecho selecionado devem terminar em até 10 minutos."); return; }
        if (outputFile.toPath().toAbsolutePath().normalize().equals(sourceFile.toPath().toAbsolutePath().normalize())) {
            showError("Escolha um arquivo de destino diferente do original."); return;
        }
        if (outputFile.exists() && JOptionPane.showConfirmDialog(owner, "O arquivo já existe. Deseja substituí-lo?", "Confirmar substituição", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;

        long duration = end - start;
        cancellationRequested = false;
        exportButton.setEnabled(false);
        cancelButton.setEnabled(true);
        progress.setValue(0);
        progress.setVisible(true);
        setStatus("Exportando com FFmpeg. A velocidade depende da duração, resolução e desempenho do computador.", false);
        String executable = ffmpegExecutable();
        worker = new SwingWorker<>() {
            @Override protected MediaInfo doInBackground() throws Exception {
                MediaInfo media = probeMedia(sourceFile);
                if (media.durationSeconds > MAX_VIDEO_SECONDS)
                    throw new java.io.IOException("Este vídeo tem " + formatDuration(media.durationSeconds) + ". O editor aceita arquivos de até 10 minutos.");
                if (end > media.durationSeconds)
                    throw new java.io.IOException("O tempo final informado ultrapassa a duração do vídeo (" + formatDuration(media.durationSeconds) + ").");
                java.util.List<String> command = java.util.Arrays.asList(executable, "-hide_banner", "-loglevel", "error",
                        "-y", "-i", sourceFile.getAbsolutePath(), "-ss", Long.toString(start), "-t", Long.toString(duration),
                        "-map", "0:v:0", "-map", "0:a?", "-c:v", "libx264", "-preset", "medium", "-crf", "18",
                        "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart",
                        "-progress", "pipe:1", outputFile.getAbsolutePath());
                Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
                currentProcess = process;
                if (cancellationRequested) process.destroyForcibly();
                StringBuilder errors = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("out_time_us=")) {
                            try { publish((int) Math.min(99, Long.parseLong(line.substring(12)) * 100 / (duration * 1_000_000L))); }
                            catch (NumberFormatException ignored) { }
                        } else if (!line.contains("=") && errors.length() < 2500) {
                            errors.append(line).append('\n');
                        }
                    }
                }
                int exitCode = process.waitFor();
                currentProcess = null;
                if (exitCode != 0) throw new java.io.IOException(errors.length() == 0 ? "FFmpeg encerrou com código " + exitCode : errors.toString().trim());
                return media;
            }

            @Override protected void process(java.util.List<Integer> chunks) {
                if (!chunks.isEmpty()) progress.setValue(chunks.get(chunks.size() - 1));
            }

            @Override protected void done() {
                currentProcess = null;
                exportButton.setEnabled(true);
                cancelButton.setEnabled(false);
                progress.setVisible(false);
                if (cancellationRequested) {
                    try { Files.deleteIfExists(outputFile.toPath()); } catch (Exception ignored) { }
                    setStatus("Exportação cancelada.", true);
                    return;
                }
                try {
                    MediaInfo media = get();
                    setStatus("Clipe salvo com sucesso (" + media.width + " × " + media.height + ", resolução original): " + outputFile.getAbsolutePath(), false);
                    JOptionPane.showMessageDialog(owner, "Clipe exportado mantendo a resolução original (" + media.width + " × " + media.height + ").", "Exportação concluída", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    setStatus("Não foi possível exportar: " + cause.getMessage(), true);
                }
            }
        };
        worker.execute();
    }

    private MediaInfo probeMedia(File file) throws Exception {
        java.util.List<String> command = java.util.Arrays.asList(ffprobeExecutable(), "-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=width,height:format=duration", "-of", "default=noprint_wrappers=1", file.getAbsolutePath());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append('\n');
        }
        int result = process.waitFor();
        if (result != 0) throw new java.io.IOException("Não consegui ler os dados do vídeo. Verifique se o FFprobe está na mesma pasta do FFmpeg e se o arquivo é válido. " + output.toString().trim());
        int width = -1, height = -1;
        double duration = -1;
        for (String line : output.toString().split("\\R")) {
            if (line.startsWith("width=")) width = Integer.parseInt(line.substring(6).trim());
            else if (line.startsWith("height=")) height = Integer.parseInt(line.substring(7).trim());
            else if (line.startsWith("duration=")) duration = Double.parseDouble(line.substring(9).trim());
        }
        if (width <= 0 || height <= 0 || duration <= 0) throw new java.io.IOException("O FFprobe não encontrou duração e resolução válidas neste arquivo.");
        return new MediaInfo(width, height, duration);
    }

    private String formatDuration(double seconds) {
        long total = (long) Math.ceil(seconds);
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60);
    }

    private final class Timeline extends JComponent {
        private static final long serialVersionUID = 1L;
        private java.util.List<BufferedImage> frames = java.util.Collections.emptyList();
        private double duration;
        private double inPoint;
        private double outPoint;
        private double playhead;
        private boolean playing;
        private int dragMode;

        Timeline() {
            setOpaque(true);
            setBackground(new Color(26, 30, 37));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (duration <= 0) return;
                    int inX = xFor(inPoint), outX = xFor(outPoint);
                    int x = e.getX();
                    if (Math.abs(x - inX) <= 16) dragMode = 1;
                    else if (Math.abs(x - outX) <= 16) dragMode = 2;
                    else dragMode = 3;
                    updateFromMouse(x, false);
                }
                @Override public void mouseDragged(MouseEvent e) { updateFromMouse(e.getX(), false); }
                @Override public void mouseReleased(MouseEvent e) {
                    updateFromMouse(e.getX(), true);
                    dragMode = 0;
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        void reset() {
            duration = 0; inPoint = 0; outPoint = 0; playhead = 0;
            frames = java.util.Collections.emptyList(); repaint();
        }

        void setMedia(double seconds, java.util.List<BufferedImage> images) {
            duration = Math.max(1, seconds); inPoint = 0; outPoint = duration;
            frames = images == null ? java.util.Collections.emptyList() : images;
            repaint();
        }

        void setFrames(java.util.List<BufferedImage> images) {
            frames = images == null ? java.util.Collections.emptyList() : images;
            repaint();
        }

        void setRange(double start, double end) {
            if (duration <= 0) return;
            inPoint = Math.max(0, Math.min(start, duration));
            outPoint = Math.max(inPoint, Math.min(end, duration));
            repaint();
        }

        void setPlayhead(double seconds) { playhead = Math.max(0, Math.min(seconds, duration)); repaint(); }
        void setPlaying(boolean value) { playing = value; repaint(); }

        private int xFor(double seconds) {
            int left = 10, right = Math.max(left + 1, getWidth() - 10);
            return left + (int) Math.round((right - left) * (duration <= 0 ? 0 : seconds / duration));
        }

        private double timeFor(int x) {
            int left = 10, right = Math.max(left + 1, getWidth() - 10);
            return duration * Math.max(0, Math.min(1, (double) (x - left) / (right - left)));
        }

        private void updateFromMouse(int x, boolean finished) {
            double seconds = timeFor(x);
            if (dragMode == 1) {
                inPoint = Math.max(0, Math.min(seconds, outPoint - 1));
                setRangeFromTimeline(Math.round(inPoint), Math.round(outPoint));
            } else if (dragMode == 2) {
                outPoint = Math.min(duration, Math.max(seconds, inPoint + 1));
                setRangeFromTimeline(Math.round(inPoint), Math.round(outPoint));
            } else {
                setPlayheadFromTimeline(seconds);
                if (!finished) seekDebounce.restart();
            }
            if (finished) seekTo(playhead);
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int left = 10, right = Math.max(left + 1, getWidth() - 10), top = 5, imageHeight = Math.min(48, getHeight() - 22);
            g.setColor(new Color(17, 20, 25));
            g.fillRoundRect(left, top, right - left, imageHeight, 8, 8);
            if (!frames.isEmpty()) {
                int cell = (right - left) / frames.size();
                for (int i = 0; i < frames.size(); i++) {
                    int x = left + i * cell;
                    g.drawImage(frames.get(i), x, top, i == frames.size() - 1 ? right - x : cell + 1, imageHeight, null);
                }
            }
            int inX = xFor(inPoint), outX = xFor(outPoint), playX = xFor(playhead);
            g.setColor(new Color(4, 8, 14, 145));
            g.fillRect(left, top, Math.max(0, inX - left), imageHeight);
            g.fillRect(outX, top, Math.max(0, right - outX), imageHeight);
            g.setColor(new Color(66, 133, 244, 45));
            g.fillRect(inX, top, Math.max(0, outX - inX), imageHeight);
            g.setColor(new Color(91, 161, 255));
            g.setStroke(new BasicStroke(2f));
            g.drawLine(inX, top, inX, top + imageHeight + 7);
            g.drawLine(outX, top, outX, top + imageHeight + 7);
            g.fillRoundRect(inX - 5, top - 2, 10, imageHeight + 8, 5, 5);
            g.fillRoundRect(outX - 5, top - 2, 10, imageHeight + 8, 5, 5);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2f));
            g.drawLine(playX, top - 1, playX, top + imageHeight + 7);
            g.fillOval(playX - 4, top - 4, 8, 8);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g.setColor(new Color(160, 170, 185));
            String leftText = "00:00";
            String rightText = formatTime((long) duration);
            g.drawString(leftText, left, getHeight() - 3);
            g.drawString(rightText, Math.max(left, right - g.getFontMetrics().stringWidth(rightText)), getHeight() - 3);
            g.dispose();
        }
    }

    private static final class MediaInfo {
        final int width;
        final int height;
        final double durationSeconds;
        java.util.List<BufferedImage> thumbnails = java.util.Collections.emptyList();
        MediaInfo(int width, int height, double durationSeconds) {
            this.width = width;
            this.height = height;
            this.durationSeconds = durationSeconds;
        }
    }

    private void cancelExport() {
        Process process = currentProcess;
        cancellationRequested = true;
        if (process != null) process.destroyForcibly();
        cancelButton.setEnabled(false);
        setStatus("Cancelando exportação…", true);
    }

    private long parseTime(String value) {
        String[] parts = value.trim().split(":", -1);
        if (parts.length != 3) throw new IllegalArgumentException("Informe o tempo no formato HH:MM:SS, por exemplo 00:01:25.");
        try {
            long hours = Long.parseLong(parts[0]);
            long minutes = Long.parseLong(parts[1]);
            long seconds = Long.parseLong(parts[2]);
            if (hours < 0 || minutes < 0 || minutes > 59 || seconds < 0 || seconds > 59)
                throw new NumberFormatException();
            return hours * 3600 + minutes * 60 + seconds;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Use horas, minutos e segundos válidos no formato HH:MM:SS.");
        }
    }

    private void setStatus(String message, boolean error) {
        status.setText(message);
        status.setForeground(error ? new Color(239, 115, 115) : new Color(165, 175, 190));
        status.setCaretPosition(0);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(owner, message, "Editor de clipes", JOptionPane.WARNING_MESSAGE);
    }
}
