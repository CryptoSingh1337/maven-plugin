package pro.saransh.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Saransh Kumar
 */

public class SpreadsheetRuleSetValidator implements Validator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpreadsheetRuleSetValidator.class);
    private final File resourceDir;
    private final List<File> spreadsheetFiles;
    private final int poolSize;

    public SpreadsheetRuleSetValidator(File resourceDir) {
        this(resourceDir, null, 1);
    }

    public SpreadsheetRuleSetValidator(File resourceDir, List<File> spreadsheetFiles, int poolSize) {
        this.resourceDir = resourceDir;
        this.spreadsheetFiles = spreadsheetFiles;
        this.poolSize = poolSize > 0 ? poolSize : 1;
    }

    @Override
    public void validate() throws MojoExecutionException, MojoFailureException {
        try {
            List<File> files;
            if (this.spreadsheetFiles == null) {
                files = FileUtils.listSpreadsheetFiles(this.resourceDir);
            } else {
                files = this.spreadsheetFiles;
            }
            final ConcurrentHashMap<String, String> seen = new ConcurrentHashMap<>();
            final Path resourcePath = resourceDir.toPath().toAbsolutePath();
            final AtomicBoolean error = new AtomicBoolean(false);

            final ExecutorService executor = Executors.newFixedThreadPool(this.poolSize);
            LOGGER.info("Validating {} spreadsheet ruleset files with pool size {}", files.size(), this.poolSize);
            try {
                List<Future<Void>> futures = new ArrayList<>();
                for (final File file : files) {
                    futures.add(executor.submit(() -> {
                        LOGGER.debug("Validating file: {}", file.getAbsolutePath());
                        Path filePath = file.toPath().toAbsolutePath();
                        String validPath = resourcePath.relativize(filePath).toString();
                        String fileName = file.getName();
                        validPath = validPath.replace("\\", ".")
                                .replace("/", ".")
                                .substring(0, validPath.length() - fileName.length() - 1);
                        String value = FileUtils.readRuleSet(file);
                        if (value == null) {
                            LOGGER.error("Invalid or empty B1 cell in file: {}", file.getAbsolutePath());
                            error.set(true);
                            return null;
                        }
                        if (!validPath.equals(value)) {
                            LOGGER.error("File must be in directory '{}' to match ruleset value, but found in '{}',",
                                    value, validPath);
                            error.set(true);
                            return null;
                        }
                        String previous = seen.putIfAbsent(value, file.getAbsolutePath());
                        if (previous != null) {
                            LOGGER.error("Duplicate B1 value '{}' found in:", value);
                            LOGGER.error(" - {}", previous);
                            LOGGER.error(" - {}", file.getAbsolutePath());
                            error.set(true);
                        }
                        return null;
                    }));
                }

                // Wait for all tasks to complete and propagate any unexpected exceptions
                for (Future<Void> f : futures) {
                    try {
                        f.get();
                    } catch (Exception e) {
                        throw new MojoExecutionException("Error while validating spreadsheets", e);
                    }
                }
            } finally {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            if (error.get()) {
                throw new MojoFailureException("Spreadsheet ruleset validation failed");
            }
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage());
        }
    }
}
