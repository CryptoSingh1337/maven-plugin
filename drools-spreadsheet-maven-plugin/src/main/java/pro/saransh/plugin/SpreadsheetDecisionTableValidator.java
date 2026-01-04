package pro.saransh.plugin;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.io.Resource;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Saransh Kumar
 */

public class SpreadsheetDecisionTableValidator implements Validator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpreadsheetDecisionTableValidator.class);
    private final MavenProject project;
    private final File resourceDir;
    private final List<File> spreadsheetFiles;
    private final File classesDir;
    private final int poolSize;

    public SpreadsheetDecisionTableValidator(MavenProject project, File classesDir, File resourceDir) {
        this(project, classesDir, resourceDir, null, 1);
    }

    public SpreadsheetDecisionTableValidator(MavenProject project, File classesDir, File resourceDir,
                                             List<File> spreadsheetFiles, int poolSize) {
        this.project = project;
        this.classesDir = classesDir;
        this.resourceDir = resourceDir;
        this.spreadsheetFiles = spreadsheetFiles;
        this.poolSize = poolSize > 0 ? poolSize : 1;
    }

    @Override
    public void validate() throws MojoExecutionException, MojoFailureException {
        try {
            addProjectClassesToContextClassLoader(classesDir);
            KieServices kieServices = KieServices.Factory.get();
            List<File> files;
            if (this.spreadsheetFiles == null) {
                files = FileUtils.listSpreadsheetFiles(this.resourceDir);
            } else {
                files = this.spreadsheetFiles;
            }
            final Queue<Message> errors = new ConcurrentLinkedQueue<>();
            final AtomicReference<Exception> failureException = new AtomicReference<>();
            final AtomicBoolean hadError = new AtomicBoolean(false);

            final ExecutorService executor = Executors.newFixedThreadPool(this.poolSize);
            LOGGER.info("Validating {} spreadsheet decision tables with pool size {}", files.size(),
                    this.poolSize);
            try {
                List<Future<Void>> futures = new ArrayList<>();
                for (final File file : files) {
                    futures.add(executor.submit(() -> {
                        LOGGER.debug("Validating spreadsheet rule: {}", file.getAbsolutePath());
                        try {
                            KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
                            Resource dt = ResourceFactory.newFileResource(file);
                            kieFileSystem.write(dt);
                            KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
                            kieBuilder.buildAll();
                            kieBuilder.getResults().getMessages()
                                    .forEach(msg -> {
                                        if (msg.getLevel() == Message.Level.ERROR) {
                                            LOGGER.error(msg.toString());
                                            errors.add(msg);
                                            hadError.set(true);
                                        } else {
                                            LOGGER.debug(msg.toString());
                                        }
                                    });
                        } catch (Exception e) {
                            String m = e.getMessage() == null ? "" : e.getMessage();
                            if (!m.contains("No RuleTable")) {
                                failureException.compareAndSet(null, e);
                                hadError.set(true);
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
                        throw new MojoExecutionException("Error while validating decision table spreadsheets", e);
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

            if (failureException.get() != null) {
                throw new MojoFailureException(failureException.get().getMessage());
            }

            if (hadError.get() || !errors.isEmpty()) {
                throw new MojoFailureException("Spreadsheet validation failed!");
            }
        } catch (IOException | DependencyResolutionRequiredException e) {
            throw new MojoExecutionException(e.getMessage());
        }
    }

    private void addProjectClassesToContextClassLoader(File classesDir) throws IOException, DependencyResolutionRequiredException {
        List<URL> urls = new ArrayList<>();

        // Add target/classes
//        if (classesDir.exists()) {
//            urls.add(classesDir.toURI().toURL());
//            LOGGER.info("Added classes directory to classloader: {}", classesDir);
//        }
//
//        if (project.getArtifacts() != null) {
//            for (Artifact artifact : project.getArtifacts()) {
//                File f = artifact.getFile();
//                if (f != null && f.exists()) {
//                    urls.add(f.toURI().toURL());
//                }
//            }
//        }

        List<String> elements = project.getRuntimeClasspathElements();
        for (String path : elements) {
            File f = new File(path);
            if (f.exists()) {
                urls.add(f.toURI().toURL());
                LOGGER.debug("Added classpath element: {}", f);
            }
        }

        ClassLoader current = Thread.currentThread().getContextClassLoader();
        URLClassLoader projectClassLoader = new URLClassLoader(urls.toArray(new URL[0]), current);
        Thread.currentThread().setContextClassLoader(projectClassLoader);
        LOGGER.debug("Added {} classpath elements to Drools classloader", urls.size());
    }
}
