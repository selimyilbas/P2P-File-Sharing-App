package p2p;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * FileUtils
 * - Utility methods for file operations (copy, delete, rename).
 *   Not strictly required, but might be used in your code if references exist.
 */
public class FileUtils {

    public static void copyFile(File sourceFile, File destinationFile) throws IOException {
        if (!sourceFile.exists()) {
            throw new IOException("Source file does not exist: " + sourceFile.getAbsolutePath());
        }
        File parentDir = destinationFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Failed to create destination directory: " + parentDir.getAbsolutePath());
            }
        }
        Files.copy(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    public static void deleteRecursively(File file) throws IOException {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            throw new IOException("Failed to delete file: " + file.getAbsolutePath());
        }
    }

    public static void createDirectoryIfNotExists(File directory) throws IOException {
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new IOException("Failed to create directory: " + directory.getAbsolutePath());
            }
        }
    }

    public static boolean renameFile(File file, String newName) {
        File renamedFile = new File(file.getParent(), newName);
        return file.renameTo(renamedFile);
    }
}
