opendj/setup --cli --hostname localhost --ldapPort 1389 --rootUserDN "cn=Directory Manager" --rootUserPassword password --adminConnectorPort 4444 --baseDN dc=com --acceptLicense --addBaseEntry --no-prompt --quiet

opendj/bin/dsconfig create-replication-server --hostname localhost --port 4444 --bindDN "cn=Directory Manager" --bindPassword password --provider-name "Multimaster Synchronization" --set replication-port:8989 --set replication-server-id:2 --type generic --trustAll --no-prompt

opendj/bin/dsconfig create-replication-domain --hostname localhost --port 4444 --bindDN "cn=Directory Manager" --bindPassword password --provider-name "Multimaster Synchronization" --domain-name example_com --set base-dn:dc=example,dc=com --set replication-server:localhost:8989 --set server-id:3 --type generic --trustAll --no-prompt

opendj/bin/dsconfig set-connection-handler-prop --hostname localhost --port 4444 --bindDN "cn=Directory Manager" --bindPassword password --handler-name "HTTP Connection Handler" --set enabled:true --set listen-port:8090 --no-prompt --trustAll

opendj/bin/dsconfig set-log-publisher-prop --hostname localhost --port 4444 --bindDN "cn=Directory Manager" --bindPassword password --publisher-name "File-Based HTTP Access Logger" --set enabled:true --no-prompt --trustAll

opendj/bin/ldapmodify --bindDN "cn=Directory Manager" --bindPassword password --hostname localhost --port 1389 --filename ./ldap.ldif

copy http-config.json to opendj/config