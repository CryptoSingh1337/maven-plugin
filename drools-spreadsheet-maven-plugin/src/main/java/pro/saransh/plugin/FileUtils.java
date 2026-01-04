package pro.saransh.plugin;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author Saransh Kumar
 */

public final class FileUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtils.class);

    private FileUtils() {
        // Utility class
    }

    public static List<File> listSpreadsheetFiles(File dir) throws IOException {
        List<File> spreadsheetFiles = new ArrayList<>();
        try (Stream<Path> files = Files.walk(dir.toPath())) {
            files.filter(p -> {
                        if (Files.isDirectory(p) || !Files.isRegularFile(p)) {
                            return false;
                        }
                        String fileName = p.getFileName().toString();
                        return !fileName.startsWith("~$") &&
                                (fileName.endsWith(".xlsx") || fileName.endsWith(".xls"));
                    })
                    .forEach(p -> spreadsheetFiles.add(p.toFile()));
        }
        return spreadsheetFiles;
    }

    public static File getKModuleFile(File dir) throws IOException {
        Path path = Paths.get(dir.getPath(), "META-INF", "kmodule.xml");
        return path.toFile();
    }

    public static String readRuleSet(File file) {
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);
            Row row = sheet.getRow(0);
            if (row == null) return null;
            Cell cell = row.getCell(1);
            if (cell == null) return null;

            DataFormatter fmt = new DataFormatter();
            String value = fmt.formatCellValue(cell);
            return !value.trim().isEmpty() ? value.trim() : null;
        } catch (Exception e) {
            LOGGER.error("Failed to read {}", file.getAbsolutePath(), e);
            return null;
        }
    }
}
