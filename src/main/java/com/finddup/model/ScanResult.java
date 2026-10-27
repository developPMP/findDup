package com.finddup.model;

import java.nio.file.Path;
import java.util.List;

import lombok.Builder;
import lombok.Value;

/**
 * Resultado de una búsqueda de duplicados sobre una carpeta.
 */
@Value
@Builder
public class ScanResult {

    /** Carpeta analizada. */
    Path root;

    /** Grupos de duplicados, ordenados por tamaño descendente. */
    List<DuplicateGroup> groups;

    /** Archivos regulares recorridos. */
    long filesScanned;

    /** Archivos o carpetas que no se pudieron leer (permisos, bloqueos, etc.). */
    long errors;

    /**
     * @return número total de archivos que pertenecen a algún grupo de duplicados
     */
    public long getDuplicateFileCount() {
        return groups.stream().mapToLong(g -> g.getFiles().size()).sum();
    }

    /**
     * @return bytes recuperables si se conserva una sola copia por grupo
     */
    public long getWastedBytes() {
        return groups.stream().mapToLong(DuplicateGroup::getWastedBytes).sum();
    }
}
