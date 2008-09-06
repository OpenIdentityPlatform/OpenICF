import org.identityconnectors.framework.api.*
import org.identityconnectors.framework.api.operations.*
import org.identityconnectors.framework.common.objects.*
import org.identityconnectors.framework.common.objects.filter.*
import org.identityconnectors.common.IOUtil

import java.io.*
import java.net.URL
import java.util.List

//static properties
static final String BUNDLE_NAME = "Connector-<%= resourceName %>";
static final String BUNDLE_VERSION = "1.0.0.0"; 
static final String BUNDLE_JAR_PATH = "<%= bundleDir %>/dist";
static final String CONNECTOR_CLASS = "<%= packageName + "." + resourceName %>Connector"
    
ConnectorInfoManagerFactory fact = ConnectorInfoManagerFactory.getInstance();      
URL url = IOUtil.makeURL(new File(BUNDLE_JAR_PATH), BUNDLE_NAME + "-" + BUNDLE_VERSION + ".jar");   
System.out.println("Loading bundle: " + url.toString());     
    
//use the ConnectorInfoManager to retrieve a ConnectorInfo object for the connector    
ConnectorInfoManager manager = fact.getLocalManager(url);
assert manager != null
ConnectorKey key = new ConnectorKey(BUNDLE_NAME, BUNDLE_VERSION, CONNECTOR_CLASS);       
ConnectorInfo info = manager.findConnectorInfo(key);
assert info != null
    
//From the ConnectorInfo object, create the default APIConfiguration.
APIConfiguration apiConfig = info.createDefaultAPIConfiguration();

//From the default APIConfiguration, retrieve the ConfigurationProperties.
ConfigurationProperties properties = apiConfig.getConfigurationProperties();
       
//Print out what the properties are
List<String> propertyNames = properties.getPropertyNames();
for(String propName : propertyNames) {
    ConfigurationProperty prop = properties.getProperty(propName);
    System.out.println("PropertyName: " + prop.getName() + "\t\tPropertyType: " + prop.getType());
    properties.setPropertyValue(prop.getName(), "fakeValue");
}

//Use the ConnectorFacadeFactory's newInstance() method to get a new connector.
ConnectorFacade conn = ConnectorFacadeFactory.getInstance().newInstance(apiConfig);

//Make sure we have set up the Configuration properly
conn.validate();

//Start using the Connector
//conn.[authenticate|create|update|delete|search|...]
