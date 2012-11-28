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
import java.io.Writer;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.WriterFactory;

/**
 * Goal generate a new POM and keeps only the minimal information.
 * <p/>
 * To debug execute this command:
 * {@code mvnDebug org.forgerock.maven.plugins:openicf-maven-plugin:reduce-pom}
 * 
 * @author Laszlo Hordos
 */
@Mojo(name = "reduce-pom", defaultPhase = LifecyclePhase.PACKAGE)
public class ReducePomMojo extends AbstractMojo {

    /**
     * The destination directory for the connector artifact.
     */
    @Parameter(defaultValue = "${project.build.directory}", required = true, readonly = true)
    private File outputDirectory;

    /**
     * The Maven Project
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * Flag whether to generate a simplified POM for the connector artifact. If
     * set to <code>false</code>, most of the build elements will be removed
     * from the generated POM. The reduced POM will be named
     * <code>reduced-pom.xml</code> and is stored into the same directory as the
     * connector artifact.
     */
    @Parameter(property = "skip", defaultValue = "false")
    private boolean skip;

    /**
     * {@inheritDoc}
     */
    public void execute() throws MojoExecutionException {
        // Check to see if we have a reduction and if so rewrite the POM.
        if (!skip) {
            try {
                Model model = project.getOriginalModel();

                // TODO Configure the removed elements
                model.setBuild(null);
                model.setDependencies(null);
                model.setDependencyManagement(null);
                model.setDistributionManagement(null);
                model.setParent(null);
                model.setProfiles(null);
                model.setProperties(null);
                model.setPluginRepositories(null);
                model.setReporting(null);
                model.setReports(null);
                model.setRepositories(null);

                /*
                 * NOTE: Be sure to create the POM in the original base
                 * directory to be able to resolve the relativePath to local
                 * parent POMs when invoking the project builder below.
                 */
                File f = new File(project.getBasedir(), "reduced-pom.xml");
                if (f.exists()) {
                    f.delete();
                }

                Writer w = null;
                try {
                    w = WriterFactory.newXmlWriter(f);
                    MavenXpp3Writer pomWriter = new MavenXpp3Writer();
                    pomWriter.write(w, model);
                } catch (IOException exception) {
                    throw new MojoExecutionException("Cannot generate reduced POM", exception);
                } finally {
                    IOUtil.close(w);
                }

                /*
                 * NOTE: Although the dependency reduced POM in the project
                 * directory is temporary build output, we have to use that for
                 * the file of the project instead of something in target to
                 * avoid messing up the base directory of the project. We'll
                 * delete this file on exit to make sure it gets cleaned up but
                 * keep a copy for inspection in the target directory as well.
                 */
                project.setFile(f);
                FileUtils.forceDeleteOnExit(f);
                File f2 = new File(outputDirectory, "reduced-pom.xml");
                FileUtils.copyFile(f, f2);
            } catch (IOException e) {
                throw new MojoExecutionException("IO Exception: ", e);
            }
        }
    }
}
