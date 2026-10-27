package com.finddup.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.finddup.model.DuplicateGroup;
import com.finddup.model.ScanResult;

class CsvExporterTest {

    private final CsvExporter exporter = new CsvExporter();

    @TempDir
    Path dir;

    @Test
    void escribeUnaFilaPorArchivoConBomYSeparadorPuntoYComa() throws IOException {
        // Solo caracteres válidos en Windows (no admite " en nombres); el escape de comillas
        // se prueba en escapaSoloCuandoEsNecesario.
        Path a = Files.writeString(dir.resolve("a;1.txt"), "hola");
        Path b = Files.writeString(dir.resolve("b copia.txt"), "hola");
        ScanResult result = ScanResult.builder()
                .root(dir)
                .groups(List.of(DuplicateGroup.builder().hash("abc").size(4).file(a).file(b).build()))
                .filesScanned(2)
                .build();
        Path csv = dir.resolve("salida.csv");

        exporter.export(result, csv);

        String content = Files.readString(csv, StandardCharsets.UTF_8);
        assertTrue(content.startsWith("\uFEFF"), "Debe empezar con BOM para Excel");
        List<String> lines = content.substring(1).lines().toList();
        assertEquals(3, lines.size());
        assertEquals(String.join(";", CsvExporter.HEADERS), lines.get(0));
        assertTrue(lines.get(1).startsWith("1;abc;4;2;\"a;1.txt\";"), lines.get(1));
        assertTrue(lines.get(2).startsWith("1;abc;4;2;b copia.txt;"), lines.get(2));
        assertTrue(content.contains("\r\n"), "Fin de línea Windows");
    }

    @Test
    void escapaSoloCuandoEsNecesario() {
        assertEquals("simple", CsvExporter.escape("simple"));
        assertEquals("C:\\Users\\ana\\foto.jpg", CsvExporter.escape("C:\\Users\\ana\\foto.jpg"));
        assertEquals("\"a;b\"", CsvExporter.escape("a;b"));
        assertEquals("\"di \"\"hola\"\"\"", CsvExporter.escape("di \"hola\""));
        assertEquals("", CsvExporter.escape(null));
    }
}
