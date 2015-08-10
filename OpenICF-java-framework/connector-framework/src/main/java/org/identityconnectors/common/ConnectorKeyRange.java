/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All Rights Reserved
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

package org.identityconnectors.common;

import org.identityconnectors.framework.api.ConnectorKey;

/**
 * A ConnectorKeyRange identifies a range of ConnectorKeys.
 * 
 * The {@link ConnectorKey} uniquely identifies one connector with exact
 * {@link org.identityconnectors.common.Version} meanwhile this class has a
 * {@link org.identityconnectors.common.VersionRange} which can match multiple.
 *
 * @since 1.5
 */
public final class ConnectorKeyRange {
    private final String bundleName;
    private final VersionRange bundleVersionRange;
    private final String exactVersion;
    private final String connectorName;

    public String getBundleName() {
        return bundleName;
    }

    public VersionRange getBundleVersionRange() {
        return bundleVersionRange;
    }

    public String getConnectorName() {
        return connectorName;
    }

    public boolean isInRange(final ConnectorKey connectorKey) {
        return !bundleVersionRange.isEmpty() && bundleName.equals(connectorKey.getBundleName())
                && connectorName.equals(connectorKey.getConnectorName())
                && bundleVersionRange.isInRange(Version.parse(connectorKey.getBundleVersion()));
    }

    public ConnectorKey getExactConnectorKey() {
        if (bundleVersionRange.isExact()) {
            return new ConnectorKey(bundleName, exactVersion, //bundleVersionRange.getFloor().getVersion(),
                    connectorName);
        } else {
            throw new IllegalArgumentException("BundleVersion is not exact version");
        }
    }

    private ConnectorKeyRange(String bundleName, String bundleVersion,
            String connectorName) {
        this.bundleName = bundleName;
        this.exactVersion = bundleVersion;
        this.bundleVersionRange = VersionRange.parse(exactVersion);
        this.connectorName = connectorName;
    }

    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ConnectorKeyRange))
            return false;

        ConnectorKeyRange that = (ConnectorKeyRange) o;

        return bundleName.equals(that.bundleName)
                && bundleVersionRange.equals(that.bundleVersionRange)
                && connectorName.equals(that.connectorName);
    }

    public int hashCode() {
        int result = bundleName.hashCode();
        result = 31 * result + bundleVersionRange.hashCode();
        result = 31 * result + connectorName.hashCode();
        return result;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private String bundleName;
        private String bundleVersion;
        private String connectorName;

        public Builder setBundleName(String bundleName) {
            this.bundleName = Assertions.blankChecked(bundleName, "bundleName");
            return this;
        }

        public Builder setBundleVersion(String bundleVersion) {
            this.bundleVersion = Assertions.blankChecked(bundleVersion, "bundleVersion");
            return this;
        }

        public Builder setConnectorName(String connectorName) {
            this.connectorName = Assertions.blankChecked(connectorName, "connectorName");
            return this;
        }

        public ConnectorKeyRange build() {
            return new ConnectorKeyRange(bundleName, bundleVersion,
                    connectorName);
        }
    }
}
