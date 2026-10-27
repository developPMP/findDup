package com.finddup.model;

import java.nio.file.Path;
import java.util.List;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/**
 * Conjunto de archivos con contenido idéntico (mismo tamaño y mismo hash SHA-256).
 */
@Value
@Builder
public class DuplicateGroup {

    /** Hash SHA-256 del contenido, en hexadecimal. */
    String hash;

    /** Tamaño de cada archivo en bytes. */
    long size;

    /** Rutas de los archivos duplicados (al menos dos). */
    @Singular
    List<Path> files;

    /**
     * Espacio que se liberaría conservando una sola copia.
     *
     * @return bytes recuperables
     */
    public long getWastedBytes() {
        return size * (files.size() - 1L);
    }
}
