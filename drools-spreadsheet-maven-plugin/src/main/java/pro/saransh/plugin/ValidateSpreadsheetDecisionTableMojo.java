package pro.saransh.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * @author Saransh Kumar
 */

@Mojo(name = "validate-spreadsheet-decision-table", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ValidateSpreadsheetDecisionTableMojo extends AbstractMojo {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidateSpreadsheetDecisionTableMojo.class);

    @Parameter(property = "resourcesDir", defaultValue = "${project.basedir}/src/main/resources")
    private File resourcesDir;
    @Parameter(property = "classesDir", defaultValue = "${project.build.outputDirectory}")
    private File classesDir;
    @Parameter(property = "poolSize", defaultValue = "1")
    private int poolSize;
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!resourcesDir.exists()) {
            LOGGER.error("Resources directory not found: {}", resourcesDir.getAbsolutePath());
            throw new MojoExecutionException("Resources directory not found");
        }
        if (!classesDir.exists()) {
            LOGGER.error("Classes directory not found: {}", classesDir.getAbsolutePath());
            throw new MojoExecutionException("Classes directory not found");
        }
        new SpreadsheetDecisionTableValidator(project, classesDir, resourcesDir, null, poolSize).validate();
    }
}
