# <img alt="OpenICF Logo" src="https://github.com/OpenIdentityPlatform/OpenICF/raw/master/logo.png" width="300"/>
[![Latest release](https://img.shields.io/github/release/OpenIdentityPlatform/OpenICF.svg)](https://github.com/OpenIdentityPlatform/OpenICF/releases)
[![Build](https://github.com/OpenIdentityPlatform/OpenICF/actions/workflows/build.yml/badge.svg)](https://github.com/OpenIdentityPlatform/OpenICF/actions/workflows/build.yml)
[![Deploy](https://github.com/OpenIdentityPlatform/OpenICF/actions/workflows/deploy.yml/badge.svg)](https://github.com/OpenIdentityPlatform/OpenICF/actions/workflows/deploy.yml)
[![Issues](https://img.shields.io/github/issues/OpenIdentityPlatform/OpenICF.svg)](https://github.com/OpenIdentityPlatform/OpenICF/issues)
[![Last commit](https://img.shields.io/github/last-commit/OpenIdentityPlatform/OpenICF.svg)](https://github.com/OpenIdentityPlatform/OpenICF/commits/master)
[![License](https://img.shields.io/badge/license-CDDL-blue.svg)](https://github.com/OpenIdentityPlatform/OpenICF/blob/master/LICENSE.md)
[![Downloads](https://img.shields.io/github/downloads/OpenIdentityPlatform/OpenICF/total.svg)](https://github.com/OpenIdentityPlatform/OpenICF/releases)
[![Docker](https://img.shields.io/docker/pulls/openidentityplatform/openicf.svg)](https://hub.docker.com/r/openidentityplatform/openicf)
[![Top language](https://img.shields.io/github/languages/top/OpenIdentityPlatform/OpenICF.svg)](https://github.com/OpenIdentityPlatform/OpenICF)
[![Code size in bytes](https://img.shields.io/github/languages/code-size/OpenIdentityPlatform/OpenICF.svg)](https://github.com/OpenIdentityPlatform/OpenICF)

The Open Identity Connector Framework (OpenICF) project provides interoperability between identity, compliance, and risk management solutions. An OpenICF Connector enables provisioning software, such as [OpenIDM](https://github.com/OpenIdentityPlatform/OpenIDM), to manage the identities maintained by a specific identity provider.

OpenICF connectors provide a consistent layer between identity applications and target resources and expose a set of operations for the complete lifecycle of an identity. The connectors provide a way to decouple applications from the target resources to which data is provisioned.

OpenICF focuses on provisioning and identity management but also provides general-purpose capabilities, including authentication, creation, reading, updating, deletion, searching, scripting, and synchronization operations. Connector bundles rely on the OpenICF Framework, but applications remain completely separate from the connector bundles. This enables you to change and update connectors without changing your application or its dependencies.

Many connectors have been built within the OpenICF framework, and are maintained and supported by the Open Identity Platform community. However, you can also develop your own OpenICF connector, to address a requirement that is not covered by one of the existing connectors. In addition, OpenICF provides two scripted connector toolkits, that enable you to write your own connectors based on Groovy or PowerShell scripts.

Starting from version 1.5, the OpenICF framework can use OpenIDM, Sun Identity Manager, and Oracle Waveset connectors (version 1.1), as well as ConnID connectors up to version 1.4.

## License
This project is licensed under the [Common Development and Distribution License (CDDL)](https://github.com/OpenIdentityPlatform/OpenICF/blob/master/LICENSE.md). 

## Downloads 
* [OpenICF ZIP](https://github.com/OpenIdentityPlatform/OpenICF/releases)
* [OpenICF Docker](https://hub.docker.com/r/openidentityplatform/openicf/)

### OpenICF Java connectors:
* [csvfile-connector](https://github.com/OpenIdentityPlatform/OpenICF/releases) 
* [xml-connector](https://github.com/OpenIdentityPlatform/OpenICF/releases) 
* [databasetable-connector](https://github.com/OpenIdentityPlatform/OpenICF/releases) 
* [ldap-connector](https://github.com/OpenIdentityPlatform/OpenICF/releases) 
* [ssh-connector](https://github.com/OpenIdentityPlatform/OpenICF/releases) 
* [groovy-connector](https://github.com/OpenIdentityPlatform/OpenICF/releases) 
* [kerberos-connector](https://github.com/OpenIdentityPlatform/OpenICF/releases) 

Java 1.8+ required

## How-to build
For windows use:
```bash
git config --system core.longpaths true
```

```bash
git clone --recursive  https://github.com/OpenIdentityPlatform/OpenICF.git
mvn install -f OpenICF
```

## How-to run after build
```bash
unzip OpenICF-java-framework/openicf-zip/target/openicf-*.zip
openicf/bin/ConnectorServer.sh /run
```

## Support
* OpenICF Community [documentation](https://github.com/OpenIdentityPlatform/OpenICF/wiki)
* OpenICF Community [discussions](https://github.com/OpenIdentityPlatform/OpenICF/discussions)
* OpenICF Community [issues](https://github.com/OpenIdentityPlatform/OpenICF/issues)
* OpenICF [commercial support](https://github.com/OpenIdentityPlatform/.github/wiki/Approved-Vendor-List)

## Contributing
Please, make [Pull request](https://github.com/OpenIdentityPlatform/OpenICF/pulls)

## Thanks 🥰
* Sun Identity Manager
* Forgerock OpenICF
