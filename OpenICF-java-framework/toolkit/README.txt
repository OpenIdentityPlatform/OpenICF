Java Toolkit v1.0 for IdentityConnectors 
https://identityconnectors.dev.java.net/Java_Toolkit.html

This toolkit includes the latest stable Connectors Framework and several utilities
to help you get started developing Connectors. Note that this facility is targeted
at SPI developers. If you are an applications developer wanting to consume connectors, 
all you need is the framework JARs and a connector bundle JAR for your target resource.

IMPORTANT/BEFORE YOU BEGIN:
-------------------------------------------------------------------------------------------
IdentityConnectors and this toolkit require Apache Ant v1.7.x and JDK v1.5 or higher.
Additionally, the JUnit4 JAR must be on your Ant lib path. The easiest way to accomplish 
this is to download and copy junit4.jar to the <ant.home>/lib directory, where <ant.home> 
is the base install directory of Ant. You can easily find this by typing "ant -diagnostics"
at the command line.  

Basic Usage:
-------------------------------------------------------------------------------------------
To get started, unzip this package into a new folder. This will be your framework home
directory. Then simply run the toolkit using Ant. The basic usage is as follows:

Usage: ant [options] [target]

Available options: 
    Type "ant -help" to see available options.                                       

Available targets are:           
    "create"                Generates a skeleton Connector bundle in the specified directory.
                            This is the default target. 
                            
    "clean"                 Deletes the specified bundle directory and all its contents.
            
    "usage"                 Prints the usage information for this toolkit.
    

There is also a Netbeans plugin included, which uses the Ant-based toolkit to generate connectors
in your Netbeans environment as well.
    
Further Information:
-------------------------------------------------------------------------------------------
More details on how to use this toolkit and the Connector architecture in general can be 
found on the Identityconnectors development site at http://identityconnectors.dev.java.net/Java_Toolkit.html 
   