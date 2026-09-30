# keytool commands used to generate the self-signed test certificates and the trust store.
#
# The client verifies the server certificate against the host it connects to (the tests use
# wss://127.0.0.1), so the server certificate carries the loopback names as subjectAltName.
# serverKeystore-cn-only.jks has no subjectAltName and is used to test that such a certificate
# is rejected.

keytool -genkeypair -alias openicf-server -keyalg RSA -keysize 2048 -sigalg SHA256withRSA \
    -dname "CN=localhost, O=OpenICF Self-Signed Certificate" \
    -ext "SAN=dns:localhost,ip:127.0.0.1,ip:::1" -validity 10950 \
    -storetype JKS -keystore serverKeystore.jks -storepass Passw0rd -keypass Passw0rd
keytool -genkeypair -alias openicf-server-cn-only -keyalg RSA -keysize 2048 -sigalg SHA256withRSA \
    -dname "CN=localhost, O=OpenICF Self-Signed Certificate" -validity 10950 \
    -storetype JKS -keystore serverKeystore-cn-only.jks -storepass Passw0rd -keypass Passw0rd
keytool -genkeypair -alias openicf-client -keyalg RSA -keysize 2048 -sigalg SHA256withRSA \
    -dname "CN=client, O=OpenICF Self-Signed Certificate" -validity 10950 \
    -storetype JKS -keystore clientKeystore.jks -storepass Passw0rd -keypass Passw0rd

keytool -exportcert -rfc -alias openicf-server -keystore serverKeystore.jks -storepass Passw0rd > openicf-server.pem
keytool -exportcert -rfc -alias openicf-server-cn-only -keystore serverKeystore-cn-only.jks -storepass Passw0rd > openicf-server-cn-only.pem
keytool -exportcert -rfc -alias openicf-client -keystore clientKeystore.jks -storepass Passw0rd > openicf-client.pem

keytool -importcert -noprompt -alias openicf-client -file openicf-client.pem -storetype JKS -keystore truststore.jks -storepass Passw0rd
keytool -importcert -noprompt -alias openicf-server -file openicf-server.pem -storetype JKS -keystore truststore.jks -storepass Passw0rd
keytool -importcert -noprompt -alias openicf-server-cn-only -file openicf-server-cn-only.pem -storetype JKS -keystore truststore.jks -storepass Passw0rd
