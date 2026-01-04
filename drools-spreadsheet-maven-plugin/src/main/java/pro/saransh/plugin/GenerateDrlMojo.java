package pro.saransh.plugin;


import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * @author Saransh Kumar
 */

@Mojo(name = "generate-drl", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenerateDrlMojo extends AbstractMojo {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateDrlMojo.class);
    @Parameter(defaultValue = "${project.basedir}/src/main/resources", property = "resourcesDir")
    private File resourcesDir;
    @Parameter(defaultValue = "${project.build.directory}", property = "outputDir")
    private File outputDir;
    @Parameter(property = "poolSize", defaultValue = "1")
    private int poolSize;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!resourcesDir.exists()) {
            LOGGER.error("Resources directory not found: {}", resourcesDir.getAbsolutePath());
            throw new MojoExecutionException("Resources directory not found");
        }
        new GenerateDrl(resourcesDir, outputDir, null, poolSize).execute();
    }
}
