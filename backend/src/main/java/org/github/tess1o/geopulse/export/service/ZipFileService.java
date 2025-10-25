package org.github.tess1o.geopulse.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service responsible for ZIP file operations during export.
 * Handles adding files and entries to ZIP archives.
 */
@ApplicationScoped
@Slf4j
public class ZipFileService {

    @Inject
    ObjectMapper objectMapper;

    /**
     * Adds a JSON file to the ZIP archive by serializing the provided data object.
     *
     * @param zos      the ZIP output stream
     * @param fileName the name of the file to add
     * @param data     the data object to serialize as JSON
     * @throws IOException if an I/O error occurs
     */
    public void addJsonFileToZip(ZipOutputStream zos, String fileName, Object data) throws IOException {
        ZipEntry entry = new ZipEntry(fileName);
        zos.putNextEntry(entry);

        String json = objectMapper.writeValueAsString(data);
        zos.write(json.getBytes());

        zos.closeEntry();
        log.debug("Added JSON file to ZIP: {}", fileName);
    }

    /**
     * Adds a binary file to the ZIP archive.
     *
     * @param zos      the ZIP output stream
     * @param fileName the name of the file to add
     * @param content  the file content as bytes
     * @throws IOException if an I/O error occurs
     */
    public void addFileToZip(ZipOutputStream zos, String fileName, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(fileName);
        zos.putNextEntry(entry);
        zos.write(content);
        zos.closeEntry();
        log.debug("Added file to ZIP: {} ({} bytes)", fileName, content.length);
    }

    /**
     * Creates a new ZIP entry in the output stream.
     *
     * @param zos      the ZIP output stream
     * @param fileName the name of the entry to create
     * @throws IOException if an I/O error occurs
     */
    public void createZipEntry(ZipOutputStream zos, String fileName) throws IOException {
        ZipEntry entry = new ZipEntry(fileName);
        zos.putNextEntry(entry);
        log.debug("Created ZIP entry: {}", fileName);
    }
}
