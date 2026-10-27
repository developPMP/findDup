package com.finddup.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.finddup.model.DuplicateGroup;
import com.finddup.model.ScanResult;

import lombok.extern.slf4j.Slf4j;

/**
 * Busca archivos duplicados por contenido dentro de una carpeta y sus subcarpetas.
 *
 * <p>Estrategia: primero agrupa por tamaño (barato) y solo calcula el hash SHA-256
 * de los archivos que comparten tamaño con otro. Los archivos vacíos se ignoran.
 * No sigue enlaces simbólicos ni uniones (junctions) de Windows, para no contar
 * dos veces el mismo archivo ni entrar en ciclos.</p>
 */
@Slf4j
public class DuplicateFinder {

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int PROGRESS_EVERY = 500;

    /**
     * Ejecuta la búsqueda.
     *
     * @param root      carpeta a analizar (por ejemplo {@code C:\Users\ana\Documentos})
     * @param progress  recibe mensajes de avance; puede ser {@code null}
     * @param cancelled devuelve {@code true} si el usuario canceló; puede ser {@code null}
     * @return grupos de duplicados encontrados
     * @throws IOException             si la carpeta raíz no se puede recorrer
     * @throws CancellationException   si se canceló la búsqueda
     * @throws IllegalArgumentException si {@code root} no es una carpeta
     */
    public ScanResult find(Path root, Consumer<String> progress, BooleanSupplier cancelled) throws IOException {
        Objects.requireNonNull(root, "root");
        Consumer<String> report = progress != null ? progress : msg -> { };
        BooleanSupplier isCancelled = cancelled != null ? cancelled : () -> false;

        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("No es una carpeta válida: " + root);
        }
        log.info("Iniciando búsqueda de duplicados en {}", root);

        SizeCollector collector = new SizeCollector(root, report, isCancelled);
        Files.walkFileTree(root, collector);
        checkCancelled(isCancelled);

        List<Map.Entry<Long, List<Path>>> candidates = collector.bySize.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .toList();
        long toHash = candidates.stream().mapToLong(e -> e.getValue().size()).sum();
        log.info("Archivos recorridos: {}. Candidatos a comparar por contenido: {}", collector.scanned, toHash);

        List<DuplicateGroup> groups = new ArrayList<>();
        long errors = collector.errors;
        long hashed = 0;
        for (Map.Entry<Long, List<Path>> sameSize : candidates) {
            long size = sameSize.getKey();
            Map<String, List<Path>> byHash = new LinkedHashMap<>();
            for (Path file : sameSize.getValue()) {
                checkCancelled(isCancelled);
                hashed++;
                if (hashed % 50 == 0 || hashed == toHash) {
                    report.accept("Comparando contenido: " + hashed + " de " + toHash);
                }
                try {
                    byHash.computeIfAbsent(sha256(file), k -> new ArrayList<>()).add(file);
                } catch (IOException e) {
                    errors++;
                    log.warn("No se pudo leer {}: {}", file, e.getMessage());
                }
            }
            byHash.forEach((hash, files) -> {
                if (files.size() > 1) {
                    files.sort(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER));
                    groups.add(DuplicateGroup.builder().hash(hash).size(size).files(files).build());
                }
            });
        }

        groups.sort(Comparator.comparingLong(DuplicateGroup::getSize).reversed()
                .thenComparing(g -> g.getFiles().get(0).toString(), String.CASE_INSENSITIVE_ORDER));

        ScanResult result = ScanResult.builder()
                .root(root)
                .groups(List.copyOf(groups))
                .filesScanned(collector.scanned)
                .errors(errors)
                .build();
        log.info("Búsqueda terminada: {} grupos, {} archivos duplicados, {} errores",
                result.getGroups().size(), result.getDuplicateFileCount(), result.getErrors());
        return result;
    }

    private static void checkCancelled(BooleanSupplier isCancelled) {
        if (isCancelled.getAsBoolean()) {
            throw new CancellationException("Búsqueda cancelada por el usuario");
        }
    }

    /**
     * Calcula el hash SHA-256 del contenido del archivo.
     *
     * @param file archivo a leer
     * @return hash en hexadecimal (minúsculas)
     * @throws IOException si el archivo no se puede leer
     */
    static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Recorre el árbol y agrupa los archivos regulares no vacíos por tamaño. */
    private static final class SizeCollector extends SimpleFileVisitor<Path> {

        private final Path root;
        private final Consumer<String> report;
        private final BooleanSupplier isCancelled;
        private final Map<Long, List<Path>> bySize = new HashMap<>();
        private long scanned;
        private long errors;

        private SizeCollector(Path root, Consumer<String> report, BooleanSupplier isCancelled) {
            this.root = root;
            this.report = report;
            this.isCancelled = isCancelled;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (isCancelled.getAsBoolean()) {
                return FileVisitResult.TERMINATE;
            }
            // En Windows una unión (junction) se ve como carpeta "other": no se sigue.
            if (attrs.isOther() && !dir.equals(root)) {
                log.debug("Se omite punto de reanálisis/unión: {}", dir);
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (isCancelled.getAsBoolean()) {
                return FileVisitResult.TERMINATE;
            }
            if (attrs.isRegularFile() && attrs.size() > 0) {
                bySize.computeIfAbsent(attrs.size(), k -> new ArrayList<>()).add(file);
                scanned++;
                if (scanned % PROGRESS_EVERY == 0) {
                    report.accept("Archivos recorridos: " + scanned);
                }
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            errors++;
            log.warn("Sin acceso a {}: {}", file, exc.getMessage());
            return FileVisitResult.CONTINUE;
        }
    }
}
