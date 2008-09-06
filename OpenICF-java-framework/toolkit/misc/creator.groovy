/*
 * This is a built-in script is used by the connector creation wizard.
 * Do not run it manually!!
 */
 
import groovy.text.*

//Arguments from Ant:
BUNDLE_DIR = args[0].replaceAll("\\\\", "/")
PACKAGE_NAME = args[1]
RESOURCE_NAME = args[2]

/* 
 * Add, update, or remove SPI Operations in this map:
 */
spiOps = [ 1 : "AdvancedUpdateOp",
           2 : "AuthenticateOp",
           3 : "CreateOp",
           4 : "DeleteOp",
           5 : "SchemaOp",
           6 : "ScriptOnConnectorOp",
           7 : "ScriptOnResourceOp",
           8 : "SearchOp<String>",
           9 : "SyncOp",
          10 : "TestOp",
          11 : "UpdateOp" ]

ant.echo ""
ant.echo "Choose which SPI operations the " + RESOURCE_NAME + " connector will implement:"
ant.echo ""
ant.echo "    [0] All Operations"
for(int i in 1..spiOps.size()) {
    String opName = spiOps.get(i)
    int idx = opName.indexOf("Op")
    opName = opName.substring(0, idx)
    ant.echo "    [${i}] ${opName}" 
}
ant.echo ""
ant.input(message:"Enter your selection(s) in a comma-separated list [0-" + spiOps.size() + "]:", addproperty:"selected.ops")
selectedOps = properties['selected.ops'].tokenize(",")

//generate a set of unique operations from user input (eliminate duplicates)
operations = [] as Set
for(s in selectedOps) {
    int i = s.trim().toInteger() 
    if(i == 0) {
        selectedOps = 1..spiOps.size() //all operations
        operations.clear()
        operations.addAll(selectedOps)
        break
    }else if(i in 1..spiOps.size()) {
        operations.add(i)
    }else {
        ant.fail "You entered a value outside of the [0-${spiOps.size()}] range."
    }
}
      
//assemble list of actual interface names                   
interfaces = []
operations.each {
    iName = spiOps[it]
    interfaces.add(iName)
}

baseDir = properties["basedir"]

//extract the full version string from one of the framework jars (1.0.x.x)
filter = {File fName -> return fName.getName().endsWith(".jar") && fName.getName().startsWith("connector") } as FileFilter
jarFile = new File(baseDir, "dist").listFiles(filter)[0]
startIdx = jarFile.getName().lastIndexOf("-") + 1
endIdx = jarFile.getName().lastIndexOf(".")
jarVersion = jarFile.getName().substring(startIdx, endIdx)

//map that the template engine will use
values = ["userName":properties["user.name"], "packageName":PACKAGE_NAME, "resourceName":RESOURCE_NAME, "frameworkDir":baseDir, "bundleDir":BUNDLE_DIR, "interfaces":interfaces, "jarVersion":jarVersion]
templates = new File(baseDir, "templates").listFiles().toList()

srcPath = BUNDLE_DIR + "/src/" + PACKAGE_NAME.replaceAll("\\.", "/") + "/"

//create directories
ant.mkdir(dir:srcPath)
ant.mkdir(dir:BUNDLE_DIR + "/lib/build")
ant.mkdir(dir:BUNDLE_DIR + "/lib/test")

engine = new SimpleTemplateEngine()
templates.each {
    result = engine.createTemplate(it).make(values)
    fName = it.getName()
    File f = null
    if(fName.endsWith(".java")) {
        f = new File(srcPath, RESOURCE_NAME + fName)
    }else if(fName.startsWith("Messages")) {
        f = new File(srcPath, fName)
    }else {
        f = new File(BUNDLE_DIR, fName)
    }    
    ant.echo "Writing file: " + f?.toString()
    result.writeTo(f.newWriter())
}
