package com.finddup.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import com.finddup.model.DuplicateGroup;
import com.finddup.model.ScanResult;

import lombok.extern.slf4j.Slf4j;

/**
 * Exporta el resultado de la búsqueda a un archivo CSV.
 *
 * <p>Formato pensado para abrirse directamente con Excel en Windows con
 * configuración regional en español: separador {@code ;}, codificación UTF-8
 * con BOM y fin de línea CRLF.</p>
 */
@Slf4j
public class CsvExporter {

    /** Separador de columnas. */
    public static final char SEPARATOR = ';';

    /** Encabezados del CSV, en orden. */
    public static final List<String> HEADERS = List.of(
            "Grupo", "Hash SHA-256", "Tamaño (bytes)", "Copias en grupo",
            "Nombre", "Carpeta", "Ruta completa", "Última modificación");

    private static final String BOM = "﻿";
    private static final String EOL = "\r\n";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Escribe el CSV con una fila por archivo duplicado.
     *
     * @param result resultado de la búsqueda
     * @param target archivo CSV de salida (se sobrescribe si existe)
     * @throws IOException si no se puede escribir el archivo
     */
    public void export(ScanResult result, Path target) throws IOException {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(target, "target");

        try (BufferedWriter out = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            out.write(BOM);
            writeRow(out, HEADERS);
            int groupNumber = 0;
            for (DuplicateGroup group : result.getGroups()) {
                groupNumber++;
                for (Path file : group.getFiles()) {
                    Path parent = file.getParent();
                    writeRow(out, List.of(
                            String.valueOf(groupNumber),
                            group.getHash(),
                            String.valueOf(group.getSize()),
                            String.valueOf(group.getFiles().size()),
                            String.valueOf(file.getFileName()),
                            parent != null ? parent.toString() : "",
                            file.toString(),
                            lastModified(file)));
                }
            }
        }
        log.info("CSV generado en {} ({} grupos)", target, result.getGroups().size());
    }

    private static void writeRow(BufferedWriter out, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.write(SEPARATOR);
            }
            out.write(escape(values.get(i)));
        }
        out.write(EOL);
    }

    /**
     * Aplica las reglas de RFC 4180: entrecomilla el valor si contiene el
     * separador, comillas o saltos de línea, y duplica las comillas internas.
     *
     * @param value valor de la celda
     * @return valor listo para escribir en el CSV
     */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.indexOf(SEPARATOR) >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        return needsQuotes ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }

    private static String lastModified(Path file) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault())
                    .format(DATE_FORMAT);
        } catch (IOException e) {
            log.debug("No se pudo leer la fecha de {}: {}", file, e.getMessage());
            return "";
        }
    }
}
