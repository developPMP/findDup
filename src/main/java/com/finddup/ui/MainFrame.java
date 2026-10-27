package com.finddup.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableModel;

import com.finddup.model.DuplicateGroup;
import com.finddup.model.ScanResult;
import com.finddup.service.CsvExporter;
import com.finddup.service.DuplicateFinder;

import lombok.extern.slf4j.Slf4j;

/**
 * Ventana principal: selección de carpeta, búsqueda de duplicados y exportación a CSV.
 */
@Slf4j
public class MainFrame extends JFrame {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final DuplicateFinder finder = new DuplicateFinder();
    private final CsvExporter exporter = new CsvExporter();

    private final JTextField folderField = new JTextField(40);
    private final JButton browseButton = new JButton("Examinar…");
    private final JButton searchButton = new JButton("Buscar duplicados");
    private final JButton cancelButton = new JButton("Cancelar");
    private final JButton exportButton = new JButton("Exportar CSV…");
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel statusLabel = new JLabel("Seleccione una carpeta para comenzar.");
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[] {"Grupo", "Tamaño", "Ruta"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private SwingWorker<ScanResult, String> worker;
    private ScanResult lastResult;

    /**
     * Construye la ventana (debe invocarse en el hilo de eventos de Swing).
     */
    public MainFrame() {
        super("findDup — Buscador de archivos duplicados");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(760, 480));
        buildLayout();
        bindActions();
        setRunning(false);
        pack();
        setLocationRelativeTo(null);
    }

    private void buildLayout() {
        JPanel folderPanel = new JPanel(new GridBagLayout());
        folderPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 0, 0, 6);
        folderPanel.add(new JLabel("Carpeta:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        folderField.setToolTipText("Ruta de la carpeta, por ejemplo C:\\Users\\usuario\\Documentos");
        folderPanel.add(folderField, c);
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 0, 0);
        folderPanel.add(browseButton, c);

        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actionsPanel.setBorder(BorderFactory.createEmptyBorder(0, 4, 5, 10));
        actionsPanel.add(searchButton);
        actionsPanel.add(cancelButton);
        actionsPanel.add(exportButton);

        JPanel north = new JPanel(new BorderLayout());
        north.add(folderPanel, BorderLayout.NORTH);
        north.add(actionsPanel, BorderLayout.SOUTH);

        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(60);
        table.getColumnModel().getColumn(0).setMaxWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setMaxWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(560);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 10, 0, 10), scroll.getBorder()));

        JPanel south = new JPanel(new BorderLayout(8, 0));
        south.setBorder(BorderFactory.createEmptyBorder(6, 10, 10, 10));
        progressBar.setPreferredSize(new Dimension(160, progressBar.getPreferredSize().height));
        south.add(statusLabel, BorderLayout.CENTER);
        south.add(progressBar, BorderLayout.EAST);

        getContentPane().add(north, BorderLayout.NORTH);
        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);
    }

    private void bindActions() {
        browseButton.addActionListener(e -> chooseFolder());
        folderField.addActionListener(e -> startSearch());
        searchButton.addActionListener(e -> startSearch());
        cancelButton.addActionListener(e -> {
            if (worker != null) {
                worker.cancel(false);
                statusLabel.setText("Cancelando…");
            }
        });
        exportButton.addActionListener(e -> {
            if (lastResult != null) {
                exportCsv(lastResult);
            }
        });
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Seleccione la carpeta a analizar");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        currentFolder().ifPresent(p -> chooser.setCurrentDirectory(p.toFile()));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            folderField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private Optional<Path> currentFolder() {
        String text = folderField.getText().trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        try {
            Path path = Path.of(text);
            return Files.isDirectory(path) ? Optional.of(path) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private void startSearch() {
        if (worker != null) {
            return;
        }
        Optional<Path> folder = currentFolder();
        if (folder.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "La ruta indicada no existe o no es una carpeta:\n" + folderField.getText(),
                    "Ruta no válida", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Path root = folder.get().toAbsolutePath().normalize();
        tableModel.setRowCount(0);
        lastResult = null;
        setRunning(true);
        statusLabel.setText("Buscando en " + root + " …");

        worker = new SwingWorker<>() {
            @Override
            protected ScanResult doInBackground() throws Exception {
                return finder.find(root, this::publish, this::isCancelled);
            }

            @Override
            protected void process(List<String> messages) {
                if (!isCancelled()) {
                    statusLabel.setText(messages.get(messages.size() - 1));
                }
            }

            @Override
            protected void done() {
                worker = null;
                setRunning(false);
                try {
                    showResult(get());
                } catch (CancellationException e) {
                    statusLabel.setText("Búsqueda cancelada.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("Búsqueda interrumpida.");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof CancellationException) {
                        statusLabel.setText("Búsqueda cancelada.");
                        return;
                    }
                    log.error("Error durante la búsqueda en {}", root, cause);
                    statusLabel.setText("Error durante la búsqueda.");
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "No se pudo completar la búsqueda:\n" + cause.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void showResult(ScanResult result) {
        lastResult = result;
        int groupNumber = 0;
        for (DuplicateGroup group : result.getGroups()) {
            groupNumber++;
            for (Path file : group.getFiles()) {
                tableModel.addRow(new Object[] {groupNumber, humanSize(group.getSize()), file.toString()});
            }
        }
        String summary = String.format("%,d archivos revisados · %,d grupos de duplicados · %,d archivos · %s recuperables%s",
                result.getFilesScanned(), result.getGroups().size(), result.getDuplicateFileCount(),
                humanSize(result.getWastedBytes()),
                result.getErrors() > 0 ? String.format(" · %,d sin acceso", result.getErrors()) : "");
        statusLabel.setText(summary);
        exportButton.setEnabled(!result.getGroups().isEmpty());

        if (result.getGroups().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No se encontraron archivos duplicados.",
                    "Resultado", JOptionPane.INFORMATION_MESSAGE);
        } else {
            exportCsv(result);
        }
    }

    private void exportCsv(ScanResult result) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Guardar resultado como CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("Archivo CSV (*.csv)", "csv"));
        File defaultDir = FileSystemView.getFileSystemView().getDefaultDirectory();
        chooser.setSelectedFile(new File(defaultDir, "duplicados_" + LocalDateTime.now().format(FILE_TS) + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            file = new File(file.getParentFile(), file.getName() + ".csv");
        }
        if (file.exists() && JOptionPane.showConfirmDialog(this,
                "El archivo ya existe. ¿Desea reemplazarlo?\n" + file,
                "Confirmar", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            exporter.export(result, file.toPath());
            JOptionPane.showMessageDialog(this, "CSV generado:\n" + file,
                    "Exportación completa", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            log.error("No se pudo escribir el CSV {}", file, e);
            JOptionPane.showMessageDialog(this, "No se pudo guardar el CSV:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setRunning(boolean running) {
        folderField.setEnabled(!running);
        browseButton.setEnabled(!running);
        searchButton.setEnabled(!running);
        cancelButton.setEnabled(running);
        exportButton.setEnabled(!running && lastResult != null && !lastResult.getGroups().isEmpty());
        progressBar.setIndeterminate(running);
        progressBar.setVisible(running);
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format("%.1f %s", value, units[unit]);
    }
}
