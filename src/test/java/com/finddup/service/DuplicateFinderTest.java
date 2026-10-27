package com.finddup.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.finddup.model.DuplicateGroup;
import com.finddup.model.ScanResult;

class DuplicateFinderTest {

    private final DuplicateFinder finder = new DuplicateFinder();

    @TempDir
    Path root;

    @Test
    void agrupaArchivosConMismoContenidoEnSubcarpetas() throws IOException {
        write("a.txt", "hola");
        write("sub/b.txt", "hola");
        write("sub/profunda/c.dat", "hola");
        write("distinto.txt", "chau");          // mismo tamaño, distinto contenido
        write("otro-tamano.txt", "hola mundo");
        write("x.bin", "1234567890");            // mismo tamaño que el anterior, distinto contenido

        ScanResult result = finder.find(root, null, null);

        assertEquals(6, result.getFilesScanned());
        assertEquals(1, result.getGroups().size());
        DuplicateGroup group = result.getGroups().get(0);
        assertEquals(4, group.getSize());
        assertEquals(List.of(root.resolve("a.txt"), root.resolve("sub/b.txt"), root.resolve("sub/profunda/c.dat")),
                group.getFiles());
        assertEquals(8, result.getWastedBytes());
    }

    @Test
    void ordenaGruposPorTamanoDescendente() throws IOException {
        write("p1.txt", "ab");
        write("p2.txt", "ab");
        write("g1.txt", "abcdef");
        write("g2.txt", "abcdef");

        ScanResult result = finder.find(root, null, null);

        assertEquals(2, result.getGroups().size());
        assertEquals(6, result.getGroups().get(0).getSize());
        assertEquals(2, result.getGroups().get(1).getSize());
    }

    @Test
    void ignoraArchivosVacios() throws IOException {
        write("vacio1.txt", "");
        write("vacio2.txt", "");

        ScanResult result = finder.find(root, null, null);

        assertTrue(result.getGroups().isEmpty());
        assertEquals(0, result.getFilesScanned());
    }

    @Test
    void rechazaRutaQueNoEsCarpeta() throws IOException {
        Path file = write("archivo.txt", "x");

        assertThrows(IllegalArgumentException.class, () -> finder.find(file, null, null));
    }

    @Test
    void respetaLaCancelacion() throws IOException {
        write("a.txt", "hola");
        write("b.txt", "hola");

        assertThrows(CancellationException.class, () -> finder.find(root, null, () -> true));
    }

    private Path write(String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
