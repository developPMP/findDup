# findDup

Aplicación de escritorio (Java 21 + Swing) que busca **archivos duplicados** dentro de una
carpeta de Windows 11 y sus subcarpetas, y genera un **CSV** con el resultado.

## Uso

1. Ejecute la aplicación (ver abajo).
2. Pulse **Examinar…** y elija la carpeta, o escriba/pegue la ruta (por ejemplo
   `C:\Users\usuario\Documentos`) y pulse **Buscar duplicados**.
3. Al terminar, la tabla muestra los grupos de duplicados y se abre el diálogo para guardar
   el CSV (por defecto en *Documentos*, con nombre `duplicados_<fecha>_<hora>.csv`).
   El botón **Exportar CSV…** permite volver a guardarlo. **Cancelar** detiene una búsqueda larga.

## Cómo detecta los duplicados

- Recorre recursivamente la carpeta y agrupa los archivos por tamaño.
- Solo a los que comparten tamaño les calcula el hash **SHA-256** del contenido completo:
  dos archivos son duplicados si tienen el mismo contenido, aunque tengan distinto nombre.
- Se ignoran los archivos vacíos (0 bytes).
- No sigue enlaces simbólicos ni uniones (*junctions*) de Windows, para no contar dos veces
  el mismo archivo.
- Las carpetas o archivos sin permiso de lectura se saltan, se cuentan como "sin acceso"
  y se registran en el log.

## Formato del CSV

Pensado para abrirse con doble clic en Excel (configuración regional en español):
separador `;`, UTF-8 con BOM, fin de línea CRLF. Una fila por archivo duplicado:

| Columna | Descripción |
|---|---|
| Grupo | Número del grupo de archivos idénticos |
| Hash SHA-256 | Hash del contenido |
| Tamaño (bytes) | Tamaño de cada archivo |
| Copias en grupo | Cantidad de archivos idénticos en el grupo |
| Nombre | Nombre del archivo |
| Carpeta | Carpeta que lo contiene |
| Ruta completa | Ruta absoluta |
| Última modificación | `yyyy-MM-dd HH:mm:ss` |

## Compilar y ejecutar

Requisito: JDK 21. No hace falta tener Maven instalado (se incluye el Maven Wrapper 3.9.11).

En Windows (PowerShell o CMD):

```bat
mvnw.cmd clean package
java -jar target\findDup-1.0.0-SNAPSHOT.jar
```

En macOS/Linux:

```bash
./mvnw clean package
java -jar target/findDup-1.0.0-SNAPSHOT.jar
```

El JAR generado incluye todas las dependencias; en Windows también puede abrirse con doble clic
si los `.jar` están asociados a Java 21.

Los logs se escriben en `%USERPROFILE%\.findDup\logs\findDup.log`.

## Estructura

```
src/main/java/com/finddup/
├── App.java                     punto de entrada
├── model/DuplicateGroup.java    grupo de archivos idénticos
├── model/ScanResult.java        resultado de la búsqueda
├── service/DuplicateFinder.java recorrido + comparación por tamaño y SHA-256
├── service/CsvExporter.java     exportación a CSV
└── ui/MainFrame.java            ventana Swing
```
