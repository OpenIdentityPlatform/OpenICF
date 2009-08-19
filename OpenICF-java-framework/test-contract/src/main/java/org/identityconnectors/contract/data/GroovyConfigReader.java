package org.identityconnectors.contract.data;

import groovy.util.ConfigObject;
import groovy.util.ConfigSlurper;

import java.io.File;
import java.net.URL;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.contract.exceptions.ContractException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

public class GroovyConfigReader {
	private GroovyConfigReader(){}
	
    private static final String BUILD_GROOVY = "build.groovy";
    private static final String CONFIG = "config";
    private static final String CONNECTORS_DIR = ".connectors";
    private static final Log LOG = Log.getLog(GroovyConfigReader.class); 



    static ConfigObject loadFSConfiguration() {
        /*
         * main config object, that will contain the merged result form 2
         * configuration files.
         */        
        final char FS = File.separatorChar;
        ConfigObject co = null;

        String prjName = System.getProperty("project.name");
        File projectPath = new File(".");
        File userHome = new File(System.getProperty("user.home"));
        // list of filePaths to configuration files
        List<String> configurations = new LinkedList<String>();
        
        // #1: ${bundle.dir}/build.groovy
        configurations.add(projectPath.getAbsolutePath() + FS + CONFIG + FS + BUILD_GROOVY);

        // determine the configuration property
        String cfg = System.getProperty("testConfig", null);        
        
        if (StringUtil.isNotBlank(cfg)) {
            // #2: ${bundle.dir}/${configuration}/build.groovy
            configurations.add(projectPath.getAbsolutePath() + FS + CONFIG + FS + cfg + FS + BUILD_GROOVY);
        }        
        
        if (StringUtil.isNotBlank(prjName)) {
            
            // #3: user-home/.connectors/connector-name/build.groovy
            String directoryPath = userHome.getAbsolutePath() + FS + CONNECTORS_DIR + FS + prjName;
            configurations.add(directoryPath + FS + BUILD_GROOVY);
            

            if (StringUtil.isNotBlank(cfg)) {
                // #4: user-home/.connectors/connector-name/${configuration}/build.groovy
                configurations.add(directoryPath + FS + cfg + FS + BUILD_GROOVY);
            }
        }

        for (String configFile : configurations) {
            // read the config file's contents and merge it:
            File cnfg = new File(configFile);
            if (cnfg.exists()) {
                ConfigObject lowPriorityCObj = parseConfigFile(cnfg);
                if (co != null) {
                    co = mergeConfigObjects(co, lowPriorityCObj);
                } else {
                    co = lowPriorityCObj;
                }
            }
        }
        return co;
    }
    
    static ConfigObject loadResourceConfiguration(String prefix, ClassLoader loader){
		String cfg = System.getProperty("testConfig", null);
		URL url = loader.getResource(prefix + "/public/build.groovy");
		ConfigObject co = null;
		ConfigSlurper cs = new ConfigSlurper();
		if(url != null){
			co = mergeConfigObjects(co, cs.parse(url));
		}
		if (StringUtil.isNotBlank(cfg) && !"default".equals(cfg)) {
		    url = loader.getResource(prefix + "/public/" + cfg + "/build.groovy");
		    if(url != null){
		    	co = mergeConfigObjects(co, cs.parse(url));
		    }
		}
		url = loader.getResource(prefix + "/private/build.groovy");
		if (url != null){
		    co = mergeConfigObjects(co, cs.parse(url));
		}
		if (StringUtil.isNotBlank(cfg) && !"default".equals(cfg)) {
		    url = loader.getResource(prefix + "/private/" + cfg + "/build.groovy");
		    if(url != null){
		    	co = mergeConfigObjects(co, cs.parse(url));
		    }
		}
		if(co == null || co.flatten().isEmpty()){
		    throw new ConnectorException(MessageFormat.format("No properties read from classpath with prefix [{0}] ",prefix));
		}
		return co;
    	
    }
    
    
    private static ConfigObject parseConfigFile(File file) {
        try {
            ConfigSlurper cs = new ConfigSlurper();
            // parse the configuration file once
            URL url = file.toURI().toURL();
            return cs.parse(url);
        } catch (Exception e) {
            LOG.error("Exception thrown during parsing of config file: " + e);
            throw ContractException.wrap(e);
        }
    }

    static ConfigObject mergeConfigObjects(ConfigObject lowPriorityCO, ConfigObject highPriorityCO) {
    	if(lowPriorityCO != null){
    		return highPriorityCO != null ? (ConfigObject) lowPriorityCO.merge(highPriorityCO) : lowPriorityCO;
    	}
    	return highPriorityCO;
    }

}
