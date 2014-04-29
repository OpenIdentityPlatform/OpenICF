/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
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
 * " Portions Copyrighted [year] [name of copyright owner]"
 *
 */

package org.forgerock.openicf.connectors.scriptedsql

import org.apache.tomcat.jdbc.pool.DataSource
import org.apache.tomcat.jdbc.pool.PoolProperties
import org.forgerock.openicf.misc.scriptedcommon.ScriptedConfiguration
import org.identityconnectors.common.StringUtil
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.spi.ConfigurationClass

/**
 * Extends the {@link org.forgerock.openicf.misc.scriptedcommon.ScriptedConfiguration} class to provide all the necessary
 * parameters to initialize the ScriptedSQL Connector.
 *
 * The Driver class. The jdbcDriver is located by connector framework to
 * connect to database. Required configuration property, and should be
 * validated.
 *
 * <pre>
 *     <ul>
 *         <li>Oracle: oracle.jdbc.driver.OracleDriver</li>
 *         <li>MySQL: com.mysql.jdbc.Driver</li>
 *         <li>DB2: COM.ibm.db2.jdbc.net.DB2Driver</li>
 *         <li>MSSQL: com.microsoft.sqlserver.jdbc.SQLServerDriver</li>
 *         <li>Sybase: com.sybase.jdbc2.jdbc.SybDriver</li>
 *         <li>Derby: org.apache.derby.jdbc.ClientDriver</li>
 *         <li>Derby embedded: org.apache.derby.jdbc.EmbeddedDriver</li>
 *         <li></li>
 *     </ul>
 * </pre>
 * <p>
 *
 * The new connection validation query. The query can be empty. Then the
 * auto commit true/false command is applied by default. This can be
 * insufficient on some database drivers because of caching Then the
 * validation query is required.
 *
 * Database validationQuery notes:
 * </p>
 *
 * <pre>
 * <ul>
 *     <li>hsqldb - select 1 from INFORMATION_SCHEMA.SYSTEM_USERS</li>
 *     <li>Oracle - select 1 from dual</li>
 *     <li>DB2 - select 1 from sysibm.sysdummy1</li>
 *     <li>mysql - select 1</li>
 *     <li>microsoft SQL Server - select 1 (tested on SQL-Server 9.0, 10.5 [2008])</li>
 *     <li>postgresql - select 1</li>
 *     <li>ingres - select 1</li>
 *     <li>derby - values 1</li>
 *     <li>H2 - select 1</li>
 *     <li>Firebird - select 1 from rdb$database</li>
 * </ul>
 * </pre>
 *
 * @author Gael Allioux <gael.allioux@forgerock.com>
 *
 */
@ConfigurationClass(skipUnsupported = true)
public class ScriptedSQLConfiguration extends ScriptedConfiguration {

    /**
     * Setup logging for the {@link ScriptedSQLConfiguration}.
     */
    static final Log log = Log.getLog(ScriptedSQLConfiguration.class);

    @Delegate
    private final PoolProperties poolProperties = new PoolProperties();

    // =======================================================================
    // Configuration Interface
    // =======================================================================

    /**
     * Attempt to validate the arguments added to the Configuration.
     */
    @Override
    public void validate() {
        super.validate();
        log.info("Validate ScriptedSQLConfiguration");
        // check the url is configured
        if (StringUtil.isNotBlank(getUrl())) {
            log.info("Validate driver configuration.");

            // determine if you can get a connection to the database..
            if (getUsername() == null) {
                throw new IllegalArgumentException(getMessage("MSG_USER_BLANK"));
            }
            // check that there is a pwd to query..
            if (getPassword() == null) {
                throw new IllegalArgumentException(getMessage("MSG_PASSWORD_BLANK"));
            }

            // make sure the jdbcDriver is in the class path..
            if (StringUtil.isBlank(getDriverClassName())) {
                throw new IllegalArgumentException(getMessage("MSG_JDBC_DRIVER_BLANK"));
            }
            try {
                Class.forName(getDriverClassName());
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(getMessage("MSG_JDBC_DRIVER_NOT_FOUND"));
            }
            log.ok("driver configuration is ok");
        } else {
            throw new IllegalArgumentException(getMessage("MSG_JDBC_TEMPLATE_BLANK"));
        }
        log.ok("Configuration is valid");
    }

    private DataSource dataSource = null;

    DataSource getDataSource() {
        if (null == dataSource) {
            synchronized (this) {
                if (null == dataSource) {

                    dataSource = new DataSource();
                    dataSource.setPoolProperties(poolProperties);

                }
            }
        }
        return dataSource;
    }

    @Override
    public void release() {
        synchronized (this) {
            super.release();
            if (null != dataSource) {
                dataSource.close();
                dataSource = null;
            }
        }
    }

    /**
     * Format the connector message
     *
     * @param key
     *            key of the message
     * @return return the formated message
     */
    public String getMessage(String key) {
        final String fmt = getConnectorMessages().format(key, key);
        log.ok("Get for a key {0} connector message {1}", key, fmt);
        return fmt;
    }
}
