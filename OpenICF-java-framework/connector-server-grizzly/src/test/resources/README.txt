#Sample keytool commands to generate two self-signed certificate and export them into a trust store.

echo changeit > keystore.pin


keytool -genkey -alias openicf-client -keyalg rsa -dname "CN=client, O=OpenICF Self-Signed Certificate" -keystore clientKeystore.jks
keytool -genkey -alias openicf-server -keyalg rsa -dname "CN=localhost, O=OpenICF Self-Signed Certificate" -keystore serverKeystore.jks

keytool -selfcert -alias openicf-client -validity 3653 -keystore clientKeystore.jks
keytool -selfcert -alias openicf-server -validity 3653 -keystore serverKeystore.jks

keytool -export -alias openicf-client -file openicf-client-cert.txt -rfc -keystore clientKeystore.jks
keytool -export -alias openicf-server -file openicf-localhost-cert.txt -rfc -keystore serverKeystore.jks


keytool -import -alias openicf-client -file openicf-client-cert.txt -trustcacerts -keystore truststore.jks -storetype JKS
keytool -import -alias openicf-server -file openicf-localhost-cert.txt -trustcacerts -keystore truststore.jks -storetype JKS
