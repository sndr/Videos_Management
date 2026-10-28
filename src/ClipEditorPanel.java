import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
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
    private static final int MAX_CLIP_SECONDS = 5 * 60;
    private static final String PREF_FFMPEG = "ffmpeg.executable";

    private final JFrame owner;
    private final JTextField sourceField = new JTextField();
    private final JTextField outputField = new JTextField();
    private final JTextField startField = new JTextField("00:00:00");
    private final JTextField endField = new JTextField("00:00:30");
    private final JTextArea status = new JTextArea("Escolha um arquivo de vídeo local para começar.");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JButton exportButton = new JButton("Exportar clipe em alta qualidade");
    private final JButton cancelButton = new JButton("Cancelar");
    private final JLabel ffmpegLabel = new JLabel();
    private volatile Process currentProcess;
    private volatile boolean cancellationRequested;
    private SwingWorker<Integer, Integer> worker;
    private File sourceFile;
    private File outputFile;

    public ClipEditorPanel(JFrame owner) {
        this.owner = owner;
        setLayout(new BorderLayout(0, 20));
        setBackground(new Color(36, 40, 48));
        setBorder(new EmptyBorder(28, 32, 28, 32));
        add(createHeader(), BorderLayout.NORTH);
        add(createEditorCard(), BorderLayout.CENTER);
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
        addRow(card, g, 2, "Salvar clipe como", createFileRow(outputField, "Escolher destino…", this::chooseOutput));

        JPanel times = new JPanel(new GridLayout(1, 2, 18, 0));
        times.setOpaque(false);
        times.add(createTimeField("Início (HH:MM:SS)", startField));
        times.add(createTimeField("Fim (HH:MM:SS)", endField));
        addRow(card, g, 4, "Trecho do vídeo", times);

        JPanel qualityInfo = new JPanel();
        qualityInfo.setLayout(new BoxLayout(qualityInfo, BoxLayout.Y_AXIS));
        qualityInfo.setBackground(new Color(31, 42, 60));
        qualityInfo.setBorder(new EmptyBorder(14, 16, 14, 16));
        JLabel qualityTitle = new JLabel("Exportação H.264 · CRF 18 · áudio AAC 192 kb/s");
        qualityTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        qualityTitle.setForeground(new Color(195, 215, 242));
        JLabel qualityDescription = new JLabel("Recodificação de alta qualidade para cortes precisos. O arquivo original não é alterado.");
        qualityDescription.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        qualityDescription.setForeground(new Color(165, 180, 202));
        qualityInfo.add(qualityTitle);
        qualityInfo.add(Box.createVerticalStrut(5));
        qualityInfo.add(qualityDescription);
        g.gridx = 0; g.gridy = 6; g.gridwidth = 2;
        card.add(qualityInfo, g);

        JPanel ffmpegRow = new JPanel(new BorderLayout(12, 0));
        ffmpegRow.setOpaque(false);
        ffmpegLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        ffmpegLabel.setForeground(new Color(165, 175, 190));
        JButton locateFfmpeg = new JButton("Localizar FFmpeg…");
        locateFfmpeg.addActionListener(e -> chooseFfmpeg());
        ffmpegRow.add(ffmpegLabel, BorderLayout.CENTER);
        ffmpegRow.add(locateFfmpeg, BorderLayout.EAST);
        g.gridy = 7;
        card.add(ffmpegRow, g);

        status.setEditable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setOpaque(false);
        status.setForeground(new Color(165, 175, 190));
        status.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        status.setRows(3);
        g.gridy = 8;
        card.add(status, g);

        progress.setVisible(false);
        progress.setStringPainted(true);
        progress.setString("Preparando exportação…");
        g.gridy = 9;
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
        g.gridy = 10;
        card.add(actions, g);

        JLabel note = new JLabel("Use o arquivo original salvo no seu computador. A API do YouTube não fornece o arquivo original para edição.");
        note.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        note.setForeground(new Color(145, 155, 172));
        g.gridy = 11;
        card.add(note, g);
        return new JScrollPane(card, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {{
            setBorder(null);
            getViewport().setBackground(new Color(36, 40, 48));
        }};
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
        sourceField.setText(sourceFile.getAbsolutePath());
        if (outputFile == null) {
            String name = sourceFile.getName().replaceFirst("(?i)\\.[^.]+$", "");
            outputFile = new File(sourceFile.getParentFile(), name + "_clipe.mp4");
            outputField.setText(outputFile.getAbsolutePath());
        }
        setStatus("Arquivo selecionado. Escolha os tempos de início e fim para um trecho de até 5 minutos.", false);
    }

    private void chooseOutput() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Salvar clipe MP4");
        chooser.setSelectedFile(outputFile != null ? outputFile : new File("clipe.mp4"));
        if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) return;
        File selected = chooser.getSelectedFile();
        if (!selected.getName().toLowerCase().endsWith(".mp4")) selected = new File(selected.getParentFile(), selected.getName() + ".mp4");
        outputFile = selected;
        outputField.setText(outputFile.getAbsolutePath());
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
        String configured = Preferences.userNodeForPackage(ClipEditorPanel.class).get(PREF_FFMPEG, "").trim();
        if (!configured.isEmpty() && Files.isExecutable(Path.of(configured))) return configured;
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? "ffmpeg.exe" : "ffmpeg";
    }

    private void updateFfmpegLabel() {
        String configured = Preferences.userNodeForPackage(ClipEditorPanel.class).get(PREF_FFMPEG, "").trim();
        if (!configured.isEmpty() && Files.isExecutable(Path.of(configured))) {
            ffmpegLabel.setText("FFmpeg: " + configured);
        } else {
            ffmpegLabel.setText("FFmpeg: " + (isOnPath() ? "disponível no PATH" : "não encontrado; instale e localize ffmpeg.exe"));
        }
    }

    private boolean isOnPath() {
        try {
            Process probe = new ProcessBuilder(ffmpegExecutable(), "-version").redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            boolean exited = probe.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            if (!exited) probe.destroyForcibly();
            return exited && probe.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
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
        if (end - start > MAX_CLIP_SECONDS) { showError("O clipe deve ter no máximo 5 minutos."); return; }
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
            @Override protected Integer doInBackground() throws Exception {
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
                return exitCode;
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
                    get();
                    setStatus("Clipe salvo com sucesso em: " + outputFile.getAbsolutePath(), false);
                    JOptionPane.showMessageDialog(owner, "Seu clipe foi exportado em alta qualidade.", "Exportação concluída", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    setStatus("Não foi possível exportar: " + cause.getMessage(), true);
                }
            }
        };
        worker.execute();
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
