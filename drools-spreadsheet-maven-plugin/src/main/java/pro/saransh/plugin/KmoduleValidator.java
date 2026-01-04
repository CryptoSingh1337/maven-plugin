package pro.saransh.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Saransh Kumar
 */

public class KmoduleValidator implements Validator {

    private static final Logger LOGGER = LoggerFactory.getLogger(KmoduleValidator.class);
    private final File resourceDir;
    private final List<File> spreadsheetFiles;
    private final int poolSize;

    public KmoduleValidator(File resourceDir, List<File> spreadsheetFiles, int poolSize) {
        this.resourceDir = resourceDir;
        this.spreadsheetFiles = spreadsheetFiles;
        this.poolSize = poolSize > 0 ? poolSize : 1;
    }

    @Override
    public void validate() throws MojoExecutionException, MojoFailureException {
        try {
            File kmoduleFile = FileUtils.getKModuleFile(this.resourceDir);
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document document = db.parse(kmoduleFile);
            document.getDocumentElement().normalize();
            NodeList kbases = document.getElementsByTagName("kbase");
            Set<String> ruleSets = new HashSet<>();
            for (int i = 0; i < kbases.getLength(); i++) {
                Node kbase = kbases.item(i);
                if (kbase.getNodeType() == Node.ELEMENT_NODE && kbase instanceof Element) {
                    Element element = (Element) kbase;
                    String packages = element.getAttribute("packages").replace(" ", "");
                    String[] packagesSplitted = packages.split(",");
                    ruleSets.addAll(Arrays.asList(packagesSplitted));
                }
            }
            List<File> files;
            if (this.spreadsheetFiles == null) {
                files = FileUtils.listSpreadsheetFiles(this.resourceDir);
            } else {
                files = this.spreadsheetFiles;
            }
            final ExecutorService executor = Executors.newFixedThreadPool(this.poolSize);
            LOGGER.info("Validating {} kmodule.xml with pool size {}", files.size(),
                    this.poolSize);
            Set<String> fileRuleSets = new HashSet<>();
            try {
                List<Future<Void>> futures = new ArrayList<>();
                for (File file : files) {
                    futures.add(executor.submit(() -> {
                        String ruleSet = FileUtils.readRuleSet(file);
                        if (ruleSet == null) {
                            throw new MojoExecutionException("Invalid rule, file path - " + file.getName());
                        }
                        fileRuleSets.add(ruleSet);
                        return null;
                    }));
                }

                // Wait for all tasks to complete and propagate any unexpected exceptions
                for (Future<Void> f : futures) {
                    try {
                        f.get();
                    } catch (Exception e) {
                        throw new MojoExecutionException("Error while validating kmodule.xml", e);
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
            ruleSets.removeAll(fileRuleSets);
            if (!ruleSets.isEmpty()) {
                throw new MojoExecutionException("Invalid Kmodule.xml, mismatch between file ruleset and kmodule packages - " + ruleSets);
            }
        } catch (IOException e) {
            throw new MojoFailureException(e);
        } catch (ParserConfigurationException | SAXException e) {
            throw new MojoExecutionException(e);
        }
    }
}
