/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.gradle.tasks;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import software.amazon.smithy.cli.BuildParameterBuilder;
import software.amazon.smithy.gradle.SmithyUtils;
import software.amazon.smithy.model.validation.Severity;

/**
 * Executes the Smithy CLI {@code build} command.
 *
 * <p>This task will build all projections specified in the smithy-build configs provided
 * as task inputs.
 *
 */
public abstract class SmithyBuildTask extends AbstractSmithyCliTask {
    @Inject
    public SmithyBuildTask(ObjectFactory objectFactory, StartParameter startParameter) {
        super(objectFactory, startParameter);

        getSourceProjection().convention("source");
        getSeverity().convention(Severity.WARNING.toString());
        getOutputDir().convention(SmithyUtils.getProjectionOutputDirProperty(getProject()));
    }

    /**
     * Tags that are searched for in classpaths when determining which
     * models are projected into the created JAR.
     *
     * <p>This plugin will look through the JARs in the discovery classpath
     * to see if they contain a META-INF/MANIFEST.MF attribute named
     * "Smithy-Tags" that matches any of the given projection source tags.
     * The Smithy models found in each matching JAR are copied into the
     * JAR being projected. This allows a projection JAR to aggregate models
     * into a single JAR.
     *
     */
    @Input
    @Optional
    public abstract SetProperty<String> getProjectionSourceTags();


    /**
     * Smithy build configs to use for building models.
     *
     * @return list of smithy-build config json files
     */
    @InputFiles
    public abstract Property<FileCollection> getSmithyBuildConfigs();

    /**
     * Projection to treat as the "source" or primary projection.
     */
    @Input
    @Optional
    public abstract Property<String> getSourceProjection();

    /**
     * Output directory for Smithy build artifacts.
     */
    @OutputDirectory
    @Optional
    public abstract DirectoryProperty getOutputDir();

    /**
     * Set the minimum reported validation severity.
     *
     * <p>This value should be one of: NOTE, WARNING [default], DANGER, ERROR.
     *
     * @return minimum validator severity
     */
    @Input
    @Optional
    public abstract Property<String> getSeverity();

    /**
     * Read-only property.
     *
     * @return list of absolute paths of model files.
     */
    @Internal
    List<String> getModelAbsolutePaths() {
        return getModels().get().getFiles().stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());
    }

    /**
     * Read-only property.
     *
     * @return Returns false if the Smithy build config property is set to an explicit empty list
     *         or if least one of the specified build configs exists
     */
    @Internal
    Provider<Boolean> getSmithyBuildConfigsMissing() {
        return getSmithyBuildConfigs().map(
                files -> !files.isEmpty() && files.filter(File::exists).isEmpty()
        );
    }

    @TaskAction
    public void execute() {
        writeHeading("Running smithy build");

        if (getSmithyBuildConfigsMissing().get()) {
            throw new GradleException("No smithy-build configs found. "
                    + "If this was intentional, set the `smithyBuildConfigs` property to an empty list.");
        }

        BuildParameterBuilder builder = new BuildParameterBuilder();

        // Model discovery classpath
        builder.libClasspath(getModelDiscoveryClasspath().get().getAsPath());
        builder.buildClasspath(getCliExecutionClasspath().get().getAsPath());
        builder.projectionSourceTags(getProjectionSourceTags().get());
        builder.allowUnknownTraits(getAllowUnknownTraits().get());
        builder.output(getOutputDir().getAsFile().get().getAbsolutePath());
        builder.projectionSource(getSourceProjection().get());

        getSmithyBuildConfigs().get().forEach(config -> builder.addConfigIfExists(config.getAbsolutePath()));

        if (getModels().isPresent()) {
            builder.addSourcesIfExists(getModelAbsolutePaths());
        }

        builder.discover(true);

        // Add extra configuration options for build command
        List<String> extraArgs = new ArrayList<>();
        configureLoggingOptions(extraArgs);

        // Add validator severity option if it exists
        extraArgs.add("--severity");
        extraArgs.add(getSeverity().get());

        builder.addExtraArgs(extraArgs.toArray(new String[0]));

        BuildParameterBuilder.Result result = builder.build();
        getLogger().debug("Executing smithy build with arguments: " + result.args);
        SmithyUtils.executeCli(getExecutor(),
                result.args,
                getCliExecutionClasspath().get(),
                getFork().get()
        );
    }

    /**
     * Convenience method to get the directory containing plugin artifacts.
     *
     * @param projection projection name
     * @param plugin name of plugin to get artifact directory for
     *
     * @return provider for the plugin artifact directory
     */
    public Provider<Directory> getPluginProjectionDirectory(String projection, String plugin) {
        return getProject().getLayout().dir(
                getOutputDir().getAsFile()
                        .map(file -> SmithyUtils.getProjectionPluginPath(file, projection, plugin).toFile()));
    }

}
