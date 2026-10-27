package com.finddup;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.finddup.ui.MainFrame;

import lombok.extern.slf4j.Slf4j;

/**
 * Punto de entrada de findDup.
 */
@Slf4j
public final class App {

    private App() {
    }

    /**
     * Inicia la interfaz gráfica.
     *
     * @param args no se usan
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // En Windows 11 usa el aspecto nativo (diálogos de carpeta de Windows).
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                log.warn("No se pudo aplicar el Look and Feel del sistema: {}", e.getMessage());
            }
            new MainFrame().setVisible(true);
        });
    }
}
