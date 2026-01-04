package pro.saransh.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.drools.decisiontable.InputType;
import org.drools.decisiontable.SpreadsheetCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Saransh Kumar
 */

public class GenerateDrl {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateDrl.class);
    private final File resourceDir;
    private final File outputDir;
    private final List<File> spreadsheetFiles;
    private final int poolSize;

    public GenerateDrl(File resourceDir, File outputDir, List<File> spreadsheetFiles, int poolSize) {
        this.resourceDir = resourceDir;
        this.outputDir = outputDir;
        this.spreadsheetFiles = spreadsheetFiles;
        this.poolSize = poolSize;
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            final ExecutorService executor = Executors.newFixedThreadPool(poolSize);
            List<File> files;
            if (this.spreadsheetFiles == null) {
                files = FileUtils.listSpreadsheetFiles(this.resourceDir);
            } else {
                files = this.spreadsheetFiles;
            }
            Path outputPath = Paths.get(this.outputDir.getPath(), "classes", "generated-drl");
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
            }
            SpreadsheetCompiler compiler = new SpreadsheetCompiler();
            try {
                List<Future<Void>> futures = new ArrayList<>();
                for (File file : files) {
                    futures.add(executor.submit(() -> {
                        Path drlFile = outputPath.resolve(file.getName().replaceAll("\\.xlsx?$", ".drl"));
                        LOGGER.info("Converting: {} -> {}", file, drlFile);

                        try (FileInputStream fis = new FileInputStream(file);
                             PrintWriter writer = new PrintWriter(drlFile.toFile())) {
                            String drl = compiler.compile(fis, InputType.XLS);
                            writer.write(drl);
                        } catch (Exception e) {
                            if (e.getMessage() != null && e.getMessage().contains("No RuleTable cells in spreadsheet")) {
                                LOGGER.warn("WARN: Skipping {} - No RuleTable cells found", file);
                            } else {
                                LOGGER.error("Failed to convert file: {}", file);
                                LOGGER.error(e.getMessage());
                            }
                        }
                        return null;
                    }));
                }
                for (Future<Void> f : futures) {
                    try {
                        f.get();
                    } catch (Exception e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        if (cause instanceof MojoFailureException) {
                            throw (MojoFailureException) cause;
                        }
                        throw new MojoExecutionException("Error while generating drl files", e);
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
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage());
        }
    }
}
