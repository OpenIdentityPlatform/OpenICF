/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

package org.forgerock.openicf.maven;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.resources.remote.BundleRemoteResourcesMojo;
import org.apache.maven.plugin.resources.remote.RemoteResourcesBundle;
import org.apache.maven.plugin.resources.remote.io.xpp3.RemoteResourcesBundleXpp3Writer;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.velocity.context.Context;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.codehaus.plexus.i18n.I18N;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.velocity.VelocityComponent;
import org.identityconnectors.common.l10n.CurrentLocale;
import org.identityconnectors.framework.spi.Connector;

/**
 * A DocBookResourceMojo generate the DocBook xml.
 * <p/>
 * To debug execute this command:
 * {@code mvnDebug org.forgerock.maven.plugins:openicf-maven-plugin:docbkx}
 *
 * @author Laszlo Hordos
 * @configurator override
 */
@Mojo(name = "docbkx", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true,
        requiresDependencyResolution = ResolutionScope.TEST, configurator = "override")
public class DocBookResourceMojo extends AbstractMojo implements ConnectorMojoBridge {

    private static final String[] DEFAULT_INCLUDES = new String[] { "**/*.txt", "**/*.vm", };

    /**
     * The Maven Session Object
     */
    @Parameter(property = "session", readonly = true)
    protected MavenSession session;

    /**
     */
    @Parameter(defaultValue = "${project.artifact}", required = true, readonly = true)
    private Artifact artifact;

    /**
     */
    @Parameter(defaultValue = "${project.version}", required = true, readonly = true)
    private String version;

    /**
     * Encoding of the bundle.
     */
    @Parameter(defaultValue = "${project.build.sourceEncoding}")
    private String sourceEncoding;

    /**
     * The current project base directory.
     */
    @Parameter(defaultValue = "${basedir}", readonly = true, required = true)
    protected File basedir;

    /**
     * The directory which contains the resources you want packaged up in this
     * resource bundle.
     */
    @Parameter(defaultValue = "${basedir}/src/main/resources")
    private File resourcesDirectory;

    /**
     * The directory where you want the resource xml-s written to.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
    private File buildOutputDirectory;

    /**
     * Directory that contains the template.
     */
    @Parameter(property = "openicf.templateDirectory", defaultValue = "org/forgerock/openicf/maven")
    private String templateDirectory;

    /**
     * The directory which contains the resources you want packaged up in this
     * resource bundle.
     */
    @Parameter(defaultValue = "${basedir}/src/main/docbkx")
    private File docbkxDirectory;

    @Parameter(property = "openicf.failOnError", defaultValue = "true")
    protected boolean failOnError;

    /**
     * Specifies the directory where the generated jar file will be put.
     */
    @Parameter(property = "project.build.directory")
    private String buildDirectory;

    /**
     * Specifies the filename that will be used for the generated jar file.
     * Please note that <code>-docbkx</code> will be appended to the file name.
     */
    @Parameter(property = "project.build.finalName")
    private String finalName;

    /**
     * Specifies whether to attach the generated artifact to the project helper. <br/>
     */
    @Parameter(property = "attach", defaultValue = "true")
    private boolean attach;

    /**
     * The archive configuration to use. See <a
     * href="http://maven.apache.org/shared/maven-archiver/index.html">Maven
     * Archiver Reference</a>.
     */
    @Parameter
    private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

    /**
     * A list of files to include. Can contain ant-style wildcards and double
     * wildcards. The default includes are
     * <code>**&#47;*.txt   **&#47;*.vm</code>
     */
    @Parameter
    private String[] includes;

    /**
     * A list of files to exclude. Can contain ant-style wildcards and double
     * wildcards.
     */
    @Parameter
    private String[] excludes;

    /**
     * Set this to 'true' to bypass artifact deploy
     *
     * @since 1.0.0
     */
    @Parameter(property = "maven.remoteresource.skip", defaultValue = "false")
    private boolean skip;

    @Parameter
    private PropertyBag configurationProperties;

    // ----------------------------------------------------------------------
    // Mojo components
    // ----------------------------------------------------------------------

    /**
     * Used for attaching the artifact in the project.
     */
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * The Jar archiver.
     */
    @Component(role = Archiver.class, hint = "jar")
    private JarArchiver jarArchiver;

    /**
     * The Maven Project
     */
    @Component
    private MavenProject project;

    /**
     * Velocity Component.
     */
    @Component()
    private VelocityComponent velocity;

    /**
     * @plexus.requirement
     */
    private I18N i18n;

    // ConnectorMojoBridge

    public File getBasedir() {
        return basedir;
    }

    public File getBuildOutputDirectory() {
        return buildOutputDirectory;
    }

    public String getSourceEncoding() {
        return sourceEncoding;
    }

    public String getTemplateDirectory() {
        return templateDirectory;
    }

    public I18N getI18N() {
        return i18n;
    }

    public MavenProject getMavenProject() {
        return project;
    }

    public PropertyBag getConfigurationProperties() {
        return configurationProperties;
    }

    public void generate(ConnectorDocBuilder builder, Context context,
            Class<? extends Connector> connectorClass) throws MojoExecutionException {
        try {
            File rootDirectory =
                    new File(buildDirectory, "openicf-docbkx/" + getTargetDirectoryName());

            File configDoc =
                    new File(rootDirectory, "sec-config-"
                            + connectorClass.getSimpleName().toLowerCase() + ".xml");

            FileUtils.mkdir(configDoc.getParentFile().getAbsolutePath());

            FileWriter writer = new FileWriter(configDoc);
            builder.processTemplate(velocity.getEngine(), context, "openicf-connector-config.vm",
                    writer);
            writer.flush();
            writer.close();

            if (null != context.get("schema")) {

                File schemaDoc =
                        new File(rootDirectory, "sec-schema-"
                                + connectorClass.getSimpleName().toLowerCase() + ".xml");

                writer = new FileWriter(schemaDoc);
                builder.processTemplate(velocity.getEngine(), context,
                        "openicf-connector-schema.vm", writer);
                writer.flush();
                writer.close();
            }

        } catch (IOException e) {
            getLog().error(e);
            throw new MojoExecutionException("Failed to generate docbook", e);
        }
    }

    protected String getTargetDirectoryName() {
        return artifact.getArtifactId() + "-" + version;
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping docbook generation");
            return;
        }

        if (!("pom".equalsIgnoreCase(project.getPackaging()))) {
            ArtifactHandler artifactHandler = project.getArtifact().getArtifactHandler();
            if (!"java".equals(artifactHandler.getLanguage())) {
                getLog().info(
                        "Not executing DocBook report as the project is not a Java classpath-capable package");
                return;
            }
        }

        try {

            if (!docbkxDirectory.exists()) {
                getLog().info(
                        "Not executing DocBook report as the project does not have docbkx source");
                return;
            }

            String docFolder = getTargetDirectoryName();

            File rootDirectory = new File(buildDirectory, "openicf-docbkx/" + docFolder);

            try {
                // File f = new File(rootDirectory, "index.xml");

                FileUtils.mkdir(rootDirectory.getAbsolutePath());

                FileUtils.copyDirectory(docbkxDirectory, rootDirectory);

            } catch (IOException e) {
                throw new MojoExecutionException("Error copy remote resources manifest.", e);
            }

            // Generate Config and Schema DocBook Chapter
            CurrentLocale.set(Locale.ENGLISH);
            ConnectorDocBuilder generator = new ConnectorDocBuilder(this);
            generator.executeReport();

            // Look at the content of the resourcesDirectory and create a
            // manifest
            // of the files
            // so that velocity can easily process any resources inside the JAR
            // that
            // need to be processed.

            RemoteResourcesBundle remoteResourcesBundle = new RemoteResourcesBundle();
            remoteResourcesBundle.setSourceEncoding(sourceEncoding);

            DirectoryScanner scanner = new DirectoryScanner();
            scanner.setBasedir(new File(buildDirectory, "openicf-docbkx/"));
            scanner.setIncludes(new String[] { docFolder + "/**" });
            scanner.addDefaultExcludes();
            scanner.scan();

            List<String> includedFiles = Arrays.asList(scanner.getIncludedFiles());

            if (skip) {
                scanner = new DirectoryScanner();
                scanner.setBasedir(resourcesDirectory);
                if (includes != null && includes.length != 0) {
                    scanner.setIncludes(includes);
                } else {
                    scanner.setIncludes(DEFAULT_INCLUDES);
                }

                if (excludes != null && excludes.length != 0) {
                    scanner.setExcludes(excludes);
                }

                scanner.addDefaultExcludes();
                scanner.scan();
                // TODO Copy to new archive
                includedFiles.addAll(Arrays.asList(scanner.getIncludedFiles()));
            }

            for (String resource : includedFiles) {
                remoteResourcesBundle.addRemoteResource(StringUtils.replace(resource, '\\', '/'));
            }

            RemoteResourcesBundleXpp3Writer w = new RemoteResourcesBundleXpp3Writer();

            try {
                File f =
                        new File(buildDirectory, "openicf-docbkx/"
                                + BundleRemoteResourcesMojo.RESOURCES_MANIFEST);

                FileUtils.mkdir(f.getParentFile().getAbsolutePath());

                Writer writer = new FileWriter(f);

                w.write(writer, remoteResourcesBundle);
            } catch (IOException e) {
                throw new MojoExecutionException("Error creating remote resources manifest.", e);
            }

            // executeReport( Locale.getDefault() );

            if (rootDirectory.exists()) {
                File outputFile =
                        generateArchive(new File(buildDirectory, "openicf-docbkx/"), finalName
                                + "-docbkx.jar");

                if (!attach) {
                    getLog().info("NOT adding javadoc to attached artifacts list.");
                } else {
                    projectHelper.attachArtifact(project, "jar", "docbkx", outputFile);
                }
            }
        } catch (ArchiverException e) {
            failOnError("ArchiverException: Error while creating archive", e);
        } catch (IOException e) {
            failOnError("IOException: Error while creating archive", e);
        } catch (RuntimeException e) {
            failOnError("RuntimeException: Error while creating archive", e);
        }

    }

    /**
     * Method that creates the jar file
     *
     * @param docbkxFiles
     *            the directory where the generated jar file will be put
     * @param jarFileName
     *            the filename of the generated jar file
     * @return a File object that contains the generated jar file
     * @throws ArchiverException
     *             if any
     * @throws IOException
     *             if any
     */
    private File generateArchive(File docbkxFiles, String jarFileName) throws ArchiverException,
            IOException {
        File docbkxJar = new File(buildDirectory, jarFileName);

        if (docbkxJar.exists()) {
            docbkxJar.delete();
        }

        MavenArchiver archiver = new MavenArchiver();
        archiver.setArchiver(jarArchiver);
        archiver.setOutputFile(docbkxJar);

        File contentDirectory = docbkxFiles;
        if (!contentDirectory.exists()) {
            getLog().warn("JAR will be empty - no content was marked for inclusion!");
        } else {
            archiver.getArchiver().addDirectory(contentDirectory);
        }

        List<Resource> resources = project.getBuild().getResources();

        for (Resource r : resources) {
            if (r.getDirectory().endsWith("maven-shared-archive-resources")) {
                archiver.getArchiver().addDirectory(new File(r.getDirectory()));
            }
        }

        try {
            // archive.setManifestFile( defaultManifestFile );
            // we don't want Maven stuff
            archive.setAddMavenDescriptor(false);
            archiver.createArchive(session, project, archive);
        } catch (ManifestException e) {
            throw new ArchiverException("ManifestException: " + e.getMessage(), e);
        } catch (DependencyResolutionRequiredException e) {
            throw new ArchiverException("DependencyResolutionRequiredException: " + e.getMessage(),
                    e);
        }

        return docbkxJar;
    }


    protected void failOnError(String prefix, Exception e) throws MojoExecutionException {
        if (failOnError) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new MojoExecutionException(prefix + ": " + e.getMessage(), e);
        }

        getLog().error(prefix + ": " + e.getMessage(), e);
    }
}
