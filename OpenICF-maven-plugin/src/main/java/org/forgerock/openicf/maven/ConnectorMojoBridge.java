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

import org.apache.maven.plugin.ContextEnabled;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.velocity.context.Context;
import org.codehaus.plexus.i18n.I18N;
import org.identityconnectors.framework.spi.Connector;

/**
 * A ConnectorMojoBridge is a common interface for different report Mojos using
 * the same ConnectorInfo.
 *
 * @author Laszlo Hordos
 */
public interface ConnectorMojoBridge extends Mojo, ContextEnabled {

    public File getBasedir();

    public File getBuildOutputDirectory();

    public String getSourceEncoding();

    public String getTemplateDirectory();

    public I18N getI18N();

    public MavenProject getMavenProject();

    public PropertyBag getConfigurationProperties();

    public void generate(ConnectorDocBuilder builder, Context context,
            Class<? extends Connector> connectorClass) throws MojoExecutionException;

}
