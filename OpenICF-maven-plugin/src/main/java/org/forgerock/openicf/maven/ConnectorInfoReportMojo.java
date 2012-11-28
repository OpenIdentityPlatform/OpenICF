/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 ForgeRock AS. All Rights Reserved
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
import java.io.IOException;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.doxia.tools.SiteTool;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.apache.velocity.context.Context;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.exception.VelocityException;
import org.codehaus.plexus.i18n.I18N;
import org.codehaus.plexus.velocity.VelocityComponent;
import org.identityconnectors.common.l10n.CurrentLocale;
import org.identityconnectors.framework.spi.Connector;

/**
 * Goal generate a Connector Report.
 * <p/>
 * To debug execute this command:
 * {@code mvnDebug org.forgerock.maven.plugins:openicf-maven-plugin:connector-info}
 * 
 * @author Laszlo Hordos
 */
@Mojo(name = "connector-info", defaultPhase = LifecyclePhase.SITE,
        requiresDependencyResolution = ResolutionScope.TEST, requiresReports = true)
public class ConnectorInfoReportMojo extends AbstractMavenReport implements ConnectorMojoBridge {

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
     * Directory where reports will go.
     */
    @Parameter(defaultValue = "${project.reporting.outputDirectory}", required = true,
            readonly = true)
    private File outputDirectory;

    /**
     * Directory that contains the template.
     */
    @Parameter(property = "openicf.templateDirectory", defaultValue = "org/forgerock/openicf/maven")
    private String templateDirectory;

    /**
     * The Velocity template used to format the connector report.
     */
    @Parameter(property = "openicf.templateName", defaultValue = "openicf-connector.vm")
    private String templateName;

    /**
     * Map of custom parameters for the connector. This Map will be passed to
     * the template.
     */
    @Parameter
    private Map connectorParameters;

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

    /**
     */
    @Component
    private Renderer siteRenderer;

    /**
     * @return the site tool.
     */
    @Component
    private SiteTool siteTool;

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

    //

    /**
     * @return the site renderer used.
     */
    @Override
    protected Renderer getSiteRenderer() {
        return siteRenderer;
    }

    /**
     * The output directory when the mojo is run directly from the command line.
     * Implementors should use this method to return the value of a mojo
     * parameter that the user may use to customize the output directory. <br/>
     * <strong>Note:</strong> When the mojo is run as part of a site generation,
     * Maven will set the effective output directory via
     * {@link org.apache.maven.reporting.MavenReport#setReportOutputDirectory(java.io.File)}
     * . In this case, the return value of this method is irrelevant. Therefore,
     * developers should always call {@link #getReportOutputDirectory()} to get
     * the effective output directory for the report. The later method will
     * eventually fallback to this method if the mojo is not run as part of a
     * site generation.
     * 
     * @return The path to the output directory as specified in the plugin
     *         configuration for this report.
     */
    @Override
    protected String getOutputDirectory() {
        return outputDirectory.getPath();
    }

    /**
     * @return the Maven project instance.
     */
    @Override
    protected MavenProject getProject() {
        return project;
    }

    /**
     * Get the base name used to create report's output file(s).
     * 
     * @return the output name of this report.
     */
    public String getOutputName() {
        return "openicf-report";
    }

    /**
     * Get the localized report name.
     * 
     * @param locale
     *            the wanted locale to return the report's name, could be null.
     * @return the name of this report.
     */
    public String getName(Locale locale) {
        return getBundle(locale).getString("report.openicf.name");
    }

    /**
     * Get the localized report description.
     * 
     * @param locale
     *            the wanted locale to return the report's description, could be
     *            null.
     * @return the description of this report.
     */
    public String getDescription(Locale locale) {
        return getBundle(locale).getString("report.openicf.description");
    }

    private ResourceBundle getBundle(Locale locale) {
        return ResourceBundle.getBundle("openicf-report", locale, this.getClass().getClassLoader());
    }

    public void generate(ConnectorDocBuilder builder, Context context,
            Class<? extends Connector> connectorClass) throws MojoExecutionException {

        try {
            StringWriter writer = new StringWriter();
            builder.processTemplate(velocity.getEngine(), context, templateName, writer);
            writer.flush();
            writer.close();
            getSink().rawText(writer.toString());
        } catch (IOException e) {
            getLog().error(e);
            throw new MojoExecutionException("Failed to generate report", e);
        }

    }

    /**
     * Execute the generation of the report.
     * 
     * @param locale
     *            the wanted locale to return the report's description, could be
     *            null.
     * @throws org.apache.maven.reporting.MavenReportException
     *             if any
     */
    @Override
    protected void executeReport(Locale locale) throws MavenReportException {
        CurrentLocale.set(locale);
        ConnectorDocBuilder generator = new ConnectorDocBuilder(this);
        try {
            generator.executeReport();
        } catch (ResourceNotFoundException rnfe) {
            throw new MavenReportException("Resource not found.", rnfe);
        } catch (VelocityException ve) {
            throw new MavenReportException(ve.toString(), ve);
        } catch (MojoExecutionException e) {
            throw new MavenReportException(e.toString(), e);
        }
    }
}
