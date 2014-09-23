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
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
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
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.velocity.context.Context;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.codehaus.plexus.i18n.I18N;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.velocity.VelocityComponent;
import org.identityconnectors.common.l10n.CurrentLocale;
import org.identityconnectors.framework.api.RemoteFrameworkConnectionInfo;

/**
 * A DocBookResourceMojo generate the DocBook xml.
 * <p/>
 * To debug execute this command:
 * {@code mvnDebug org.forgerock.maven.plugins:openicf-maven-plugin:docbkx}
 *
 * @author Laszlo Hordos
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

    public File getBuildOutputDirectory() {
        return buildOutputDirectory;
    }

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

    public String[] getIncludes() {
        return includes;
    }

    /**
     * A list of files to exclude. Can contain ant-style wildcards and double
     * wildcards.
     */
    @Parameter
    private String[] excludes;

    public String[] getExcludes() {
        return excludes;
    }

    /**
     * Set this to 'true' to bypass artifact deploy
     *
     * @since 1.0.0
     */
    @Parameter(property = "maven.openicf.skip", defaultValue = "false")
    private boolean skip;

    @Parameter
    private PropertyBag configurationProperties;

    public PropertyBag getConfigurationProperties() {
        return configurationProperties;
    }

    @Parameter
    private RemoteFrameworkConnectionInfo remoteFrameworkConnectionInfo;

    public RemoteFrameworkConnectionInfo getRemoteFrameworkConnectionInfo() {
        return remoteFrameworkConnectionInfo;
    }

    @Parameter(defaultValue = "${mojoExecution}")
    MojoExecution execution;

    // ----------------------------------------------------------------------
    // Mojo components
    // ----------------------------------------------------------------------

    /**
     * Used for attaching the artifact in the project.
     */
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * The Jar achiver.
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

    @Component()
    private MavenFileFilter fileFilter;

    /**
     * @plexus.requirement
     */
    private I18N i18n;

    public File getBasedir() {
        return basedir;
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

    public void generate(ConnectorDocBuilder builder, Context context, String connectorName)
            throws MojoExecutionException {
        try {
            File rootDirectory =
                    new File(buildDirectory, "openicf-docbkx/" + getTargetDirectoryName());

            Map<String, String> fileSetDictionary = new LinkedHashMap<String, String>();
            fileSetDictionary.put("sec-reference.xml.vm", "sec-reference-%s.xml");
            fileSetDictionary.put("sec-implemented-interfaces.xml.vm",
                    "sec-implemented-interfaces-%s.xml");
            fileSetDictionary.put("sec-config-properties.xml.vm", "sec-config-properties-%s.xml");
            fileSetDictionary.put("sec-schema.xml.vm", "sec-schema-%s.xml");
            fileSetDictionary.put("chap-config.xml.vm", "chap-config-%s.xml");

            for (Map.Entry<String, String> entry : fileSetDictionary.entrySet()) {
                generateDocument(builder, context, connectorName, rootDirectory, entry.getKey());
            }
        } catch (IOException e) {
            getLog().error(e);
            throw new MojoExecutionException("Failed to generate DocBook", e);
        }
    }

    protected void generateDocument(ConnectorDocBuilder builder, Context context,
            String connectorName, File rootDirectory, String templateFile) throws IOException,
            MojoExecutionException {
        if (null == context.get("schema") && templateFile.contains("-schema")) {
            return;
        }
        String key =
                templateFile
                        .substring(templateFile.lastIndexOf("/") + 1, templateFile.indexOf('.'));
        String fileName = key + "-" + connectorName + ".xml";
        File configDoc = new File(rootDirectory, fileName);

        FileUtils.mkdir(configDoc.getParentFile().getAbsolutePath());

        FileWriter writer = new FileWriter(configDoc);
        builder.processTemplate(velocity.getEngine(), context, templateFile, writer);
        writer.flush();
        writer.close();
        context.put(key, fileName);
    }

    protected String getTargetDirectoryName() {
        if (null == execution || execution.getExecutionId().equals("default")) {
            return artifact.getArtifactId() + "-" + artifact.getVersion();
        } else {
            return artifact.getArtifactId() + "-" + artifact.getVersion() + "/"
                    + execution.getExecutionId();
        }
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping DocBook generation");
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
                        "Not executing DocBook report as the project does not have DocBook source");
                return;
            }

            File rootDirectory =
                    new File(buildDirectory, "openicf-docbkx/" + artifact.getArtifactId() + "-"
                            + artifact.getVersion());
            try {
                FileUtils.mkdir(rootDirectory.getAbsolutePath());

                MavenResourcesExecution mre = new MavenResourcesExecution();
                mre.setMavenProject(getMavenProject());
                mre.setEscapeWindowsPaths(true);
                mre.setMavenSession(session);
                mre.setInjectProjectBuildFilters(true);

                List<FileUtils.FilterWrapper> filterWrappers = null;
                try {
                    filterWrappers = fileFilter.getDefaultFilterWrappers(mre);
                } catch (MavenFilteringException e) {
                    filterWrappers = Collections.emptyList();
                }

                if (docbkxDirectory.exists()) {
                    final List<String> includes =
                            FileUtils.getFileAndDirectoryNames(docbkxDirectory, "**", StringUtils
                                    .join(DirectoryScanner.DEFAULTEXCLUDES, ",")
                                    + ",**/*.xml", true, false, true, true);

                    org.apache.commons.io.FileUtils.copyDirectory(docbkxDirectory, rootDirectory,
                            new FileFilter() {
                                public boolean accept(File pathname) {
                                    return includes.contains(pathname.getPath());
                                }
                            });

                    List<File> files = FileUtils.getFiles(docbkxDirectory, "**/*.xml", null);

                    for (File file : files) {
                        try {
                            fileFilter.copyFile(file, new File(rootDirectory, file.getName()),
                                    true, filterWrappers, getSourceEncoding());
                        } catch (MavenFilteringException e) {
                            throw new MojoExecutionException(e.getMessage(), e);
                        }
                    }
                }

                File sharedRoot = rootDirectory.getParentFile();

                CodeSource src = getClass().getProtectionDomain().getCodeSource();
                if (src != null) {
                    final ZipInputStream zip = new ZipInputStream(src.getLocation().openStream());
                    ZipEntry entry = null;
                    while ((entry = zip.getNextEntry()) != null) {
                        String name = entry.getName();
                        if (entry.getName().startsWith("shared")) {

                            File destination = new File(sharedRoot, name);
                            if (entry.isDirectory()) {
                                if (!destination.exists()) {
                                    destination.mkdirs();
                                }
                            } else {
                                if (!destination.exists()) {
                                    FileOutputStream output = null;
                                    try {
                                        output = new FileOutputStream(destination);
                                        IOUtil.copy(zip, output);
                                    } finally {
                                        IOUtil.close(output);
                                    }
                                }
                            }
                        }
                    }
                    zip.closeEntry();
                    zip.close();
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Error copy DocBook resources.", e);
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

            if (attach) {
                RemoteResourcesBundle remoteResourcesBundle = new RemoteResourcesBundle();
                remoteResourcesBundle.setSourceEncoding(getSourceEncoding());

                DirectoryScanner scanner = new DirectoryScanner();
                scanner.setBasedir(new File(buildDirectory, "openicf-docbkx/"));
                // scanner.setIncludes(new String[] { docFolder + "/**" });
                scanner.addDefaultExcludes();
                scanner.scan();

                List<String> includedFiles = Arrays.asList(scanner.getIncludedFiles());

                if (resourcesDirectory.exists()) {
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
                    includedFiles.addAll(Arrays.asList(scanner.getIncludedFiles()));
                }
                for (String resource : includedFiles) {
                    remoteResourcesBundle.addRemoteResource(StringUtils
                            .replace(resource, '\\', '/'));
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

                File outputFile =
                        generateArchive(new File(buildDirectory, "openicf-docbkx/"), finalName
                                + "-docbkx.jar");

                projectHelper.attachArtifact(project, "jar", "docbkx", outputFile);
            } else {
                getLog().info("NOT adding DocBook to attached artifacts list.");
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

        if (!docbkxFiles.exists()) {
            getLog().warn("JAR will be empty - no content was marked for inclusion!");
        } else {
            archiver.getArchiver().addDirectory(docbkxFiles);
        }

        List<Resource> resources = project.getBuild().getResources();

        for (Resource r : resources) {
            if (r.getDirectory().endsWith("maven-shared-archive-resources")) {
                archiver.getArchiver().addDirectory(new File(r.getDirectory()));
            }
        }

        try {
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
