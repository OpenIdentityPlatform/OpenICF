/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 ForgeRock Inc. All rights reserved.
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
package org.forgerock.openicf.misc.scriptedcommon;

import java.io.File;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;

/**
 * Extends the {@link AbstractConfiguration} class to provide all the necessary
 * parameters to initialize the Scripted Connector.
 *
 * @author Gael Allioux <gael.allioux@forgerock.com>
 *
 */
public class ScriptedConfiguration extends AbstractConfiguration {

    // =======================================================================
    // Scripts
    // =======================================================================
    /**
     * Scripting language
     */
    private String scriptingLanguage = "GROOVY";

    /**
     * Return the scripting language string
     *
     * @return scriptingLanguage value
     */
    public String getScriptingLanguage() {
        return scriptingLanguage;
    }

    /**
     * Set the scripting language string
     *
     * @param value
     */
    public void setScriptingLanguage(String value) {
        this.scriptingLanguage = value;
    }
    /**
     * Should password be passed to scripts in clear text?
     */
    private boolean clearTextPasswordToScript = true;

    /**
     * Return the clearTextPasswordToScript boolean
     *
     * @return value
     */
    public boolean getClearTextPasswordToScript() {
        return clearTextPasswordToScript;
    }

    /**
     * Set the clearTextPasswordToScript value
     *
     * @param value
     */
    public void setClearTextPasswordToScript(boolean value) {
        this.clearTextPasswordToScript = value;
    }
    /**
     * By default, scripts are loaded and compiled when a connector instance is
     * created and initialized. Setting reloadScriptOnExecution to true will
     * make the connector load and compile the script every time it is called.
     * Use only for test/debug purpose since this can have a significant impact
     * on performance.
     */
    private boolean reloadScriptOnExecution = false;

    /**
     * Accessor for the reloadScriptOnExecution property
     *
     * @return the reloadScriptOnExecution
     */
    public boolean isReloadScriptOnExecution() {
        return reloadScriptOnExecution;
    }

    /**
     * Setter for the reloadScriptOnExecution property.
     *
     * @param reloadScriptOnExecution
     */
    public void setReloadScriptOnExecution(boolean reloadScriptOnExecution) {
        this.reloadScriptOnExecution = reloadScriptOnExecution;
    }
    /**
     * Authenticate script filename
     */
    private String authenticateScriptFileName = null;

    /**
     * Return the Authenticate script FileName
     *
     * @return value
     */
    public String getAuthenticateScriptFileName() {
        return authenticateScriptFileName;
    }

    /**
     * Set the Authenticate script FileName
     *
     * @param value
     */
    public void setAuthenticateScriptFileName(String value) {
        this.authenticateScriptFileName = value;
    }
    /**
     * Create script filename
     */
    private String createScriptFileName = null;

    /**
     * Return the Create script FileName
     *
     * @return value
     */
    public String getCreateScriptFileName() {
        return createScriptFileName;
    }

    /**
     * Set the Create script FileName
     *
     * @param value
     */
    public void setCreateScriptFileName(String value) {
        this.createScriptFileName = value;
    }
    /**
     * Update script FileName
     */
    private String updateScriptFileName = null;

    /**
     * Return the Update script FileName
     *
     * @return updateScriptFileName value
     */
    public String getUpdateScriptFileName() {
        return updateScriptFileName;
    }

    /**
     * Set the Update script FileName
     *
     * @param value
     */
    public void setUpdateScriptFileName(String value) {
        this.updateScriptFileName = value;
    }
    /**
     * Delete script FileName
     */
    private String deleteScriptFileName = null;

    /**
     * Return the Delete script FileName
     *
     * @return deleteScriptFileName value
     */
    public String getDeleteScriptFileName() {
        return deleteScriptFileName;
    }

    /**
     * Set the Delete script FileName
     *
     * @param value
     */
    public void setDeleteScriptFileName(String value) {
        this.deleteScriptFileName = value;
    }
    /**
     * Search script FileName
     */
    private String searchScriptFileName = null;

    /**
     * Return the Search script FileName
     *
     * @return searchScriptFileName value
     */
    public String getSearchScriptFileName() {
        return searchScriptFileName;
    }

    /**
     * Set the Search script FileName
     *
     * @param value
     */
    public void setSearchScriptFileName(String value) {
        this.searchScriptFileName = value;
    }
    /**
     * Sync script FileName
     */
    private String syncScriptFileName = null;

    /**
     * Return the Sync script FileName
     *
     * @return syncScriptFileName value
     */
    public String getSyncScriptFileName() {
        return syncScriptFileName;
    }

    /**
     * Set the Sync script FileName
     *
     * @param value
     */
    public void setSyncScriptFileName(String value) {
        this.syncScriptFileName = value;
    }
    /**
     * Schema script FileName
     */
    private String schemaScriptFileName = null;

    /**
     * Return the Schema script FileName
     *
     * @return schemaScriptFileName value
     */
    public String getSchemaScriptFileName() {
        return schemaScriptFileName;
    }

    /**
     * Set the Schema script FileName
     *
     * @param value
     */
    public void setSchemaScriptFileName(String value) {
        this.schemaScriptFileName = value;
    }
    /**
     * Test script FileName
     */
    private String testScriptFileName = null;

    /**
     * Return the Test script FileName
     *
     * @return testScriptFileName value
     */
    public String getTestScriptFileName() {
        return testScriptFileName;
    }

    /**
     * Set the Test script FileName
     *
     * @param value
     */
    public void setTestScriptFileName(String value) {
        this.testScriptFileName = value;
    }

    /**
     * {@inheritDoc}
     */
    public void validate() {
        // Validate the actions files
        checkFileIsReadable(getAuthenticateScriptFileName());
        checkFileIsReadable(getCreateScriptFileName());
        checkFileIsReadable(getUpdateScriptFileName());
        checkFileIsReadable(getDeleteScriptFileName());
        checkFileIsReadable(getSearchScriptFileName());
        checkFileIsReadable(getSyncScriptFileName());
        checkFileIsReadable(getTestScriptFileName());
        checkFileIsReadable(getSchemaScriptFileName());
    }

    private void checkFileIsReadable(String fileName) {
        if (fileName != null) {
            File f = new File(fileName);
            try {
                if (!f.canRead()) {
                    throw new ConfigurationException("Can't read " + fileName);
                }
            } catch (SecurityException e) {
                throw new ConfigurationException("Can't read " + fileName);
            }
        }
    }
}
