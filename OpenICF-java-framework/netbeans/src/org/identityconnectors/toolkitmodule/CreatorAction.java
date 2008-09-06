/*
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * U.S. Government Rights - Commercial software. Government users 
 * are subject to the Sun Microsystems, Inc. standard license agreement
 * and applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * Sun, Sun Microsystems, the Sun logo, Java and Project Identity 
 * Connectors are trademarks or registered trademarks of Sun 
 * Microsystems, Inc. or its subsidiaries in the U.S. and other
 * countries.
 * 
 * UNIX is a registered trademark in the U.S. and other countries,
 * exclusively licensed through X/Open Company, Ltd. 
 * 
 * -----------
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved. 
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License(CDDL) (the License).  You may not use this file
 * except in  compliance with the License. 
 * 
 * You can obtain a copy of the License at
 * http://identityconnectors.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing permissions and 
 * limitations under the License.  
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each
 * file and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * -----------
 */
package org.identityconnectors.toolkitmodule;

import java.awt.Component;
import java.awt.Dialog;
import java.io.*;
import java.text.MessageFormat;
import java.util.*;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import org.apache.tools.ant.module.api.support.ActionUtils;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.spi.project.support.ant.AntProjectHelper;
import org.netbeans.spi.project.support.ant.EditableProperties;
import org.netbeans.spi.project.support.ant.ProjectGenerator;
import org.netbeans.spi.project.support.ant.PropertyUtils;
import org.openide.DialogDisplayer;
import org.openide.WizardDescriptor;
import org.openide.execution.ExecutionEngine;
import org.openide.util.*;
import org.openide.util.actions.CallableSystemAction;
import org.openide.execution.ExecutorTask;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

public final class CreatorAction extends CallableSystemAction {

    private WizardDescriptor.Panel[] panels;

    public void performAction() {
        WizardDescriptor wizardDescriptor = new WizardDescriptor(getPanels());
        wizardDescriptor.setTitleFormat(new MessageFormat("{0}"));
        wizardDescriptor.setTitle("Connectors Toolkit");
        Dialog dialog = DialogDisplayer.getDefault().createDialog(wizardDescriptor);
        dialog.setVisible(true);
        dialog.toFront();
        boolean cancelled = wizardDescriptor.getValue() != WizardDescriptor.FINISH_OPTION;
        if (!cancelled) {
            final ProgressHandle progress = ProgressHandleFactory.createHandle("Generating Connector...");
            try {
                progress.start();

                final Properties properties = new Properties();
                properties.setProperty("bundle.dir", wizardDescriptor.getProperty("bundle.dir").toString());
                properties.setProperty("confirm.clean", "y");
                properties.setProperty("resource.name", wizardDescriptor.getProperty("resource.name").toString());
                properties.setProperty("package.name", wizardDescriptor.getProperty("package.name").toString());
                properties.setProperty("selected.ops", wizardDescriptor.getProperty("selected.ops").toString());

                final String toolkitLoc = wizardDescriptor.getProperty("toolkit.loc").toString();

                EditableProperties gProps = PropertyUtils.getGlobalProperties();
                gProps.setProperty("toolkit.loc", toolkitLoc);
                PropertyUtils.putGlobalProperties(gProps);

                Runnable task = new Runnable() {

                    public void run() {
                        try {
                            String result = runAntToolkit(toolkitLoc, properties);
                            if (result != null) {
                                JOptionPane.showMessageDialog(null, result, "Creation Wizard Error", JOptionPane.ERROR_MESSAGE);
                                return;
                            }
                            result = createAndOpenNBProject(toolkitLoc, properties);
                            if (result != null) {
                                JOptionPane.showMessageDialog(null, result, "Creation Wizard Error", JOptionPane.ERROR_MESSAGE);
                            }
                        } catch(IOException ex) {
                            Exceptions.printStackTrace(ex);
                        }finally {
                            progress.finish();
                        }
                    }
                };

                ExecutionEngine engine = ExecutionEngine.getDefault();
                engine.execute("ConnectorToolkit", task, null);

            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
                progress.finish();
            }
        }
    }

    private String runAntToolkit(String toolkitLoc, Properties properties) throws IOException {
        final FileObject toolkit = FileUtil.toFileObject(new File(toolkitLoc + "/build.xml"));
        final ExecutorTask task = ActionUtils.runTarget(toolkit, null, properties);
        int result = task.result(); //blocks this (worker) thread until Ant is finished
        if (result != 0) {
            return "Ant task did not complete successfully. See Output window for more details.";
        }
        return null;
    }

    private String createAndOpenNBProject(String toolkitLoc, Properties properties) throws IOException {
        String type = "org.netbeans.modules.java.j2seproject";

        File bundleDirFile = new File(properties.getProperty("bundle.dir"));
        FileObject bundleDir = FileUtil.toFileObject(bundleDirFile);

        ProjectManager pm = ProjectManager.getDefault();
        if (!pm.isProject(bundleDir)) {
            AntProjectHelper helper = ProjectGenerator.createProject(bundleDir, type);

            //grab template properties file
            FileObject propFile = FileUtil.toFileObject(new File(toolkitLoc + "/misc/netbeans/nbproject", "project.properties"));

            //edit the properties accordingly
            EditableProperties eProps = new EditableProperties();
            eProps.load(propFile.getInputStream());
            String resourceName = properties.getProperty("resource.name");
            eProps.setProperty("application.title", "Connector-" + resourceName);
            eProps.setProperty("application.vendor", System.getProperty("user.name"));
            eProps.setProperty("dist.jar", "${dist.dir}/Connector-" + resourceName + "-1.0.0.0.jar");

            String javadocPath = getJavadocPath(toolkitLoc);

            //Head JAR
            File jar = getFrameworkHeadJAR(toolkitLoc);
            eProps.setProperty("file.reference." + jar.getName(), jar.getAbsolutePath());
            eProps.setProperty("javadoc.reference." + jar.getName(), javadocPath);
            //set classpath
            eProps.setProperty("javac.classpath", new String[]{"${libs.junit_4.classpath}:",
                        "${file.reference." + jar.getName() + "}"
                    });

            //Internal JAR
            jar = getFrameworkInternalJAR(toolkitLoc);
            eProps.setProperty("file.reference." + jar.getName(), jar.getAbsolutePath());
            eProps.setProperty("javadoc.reference." + jar.getName(), javadocPath);
            //set classpath
            eProps.setProperty("run.classpath", new String[]{"${javac.classpath}:", "${build.classes.dir}:",
                        "${file.reference." + jar.getName() + "}"
                    });

            //Contract JAR
            File jar2 = getFrameworkContractJAR(toolkitLoc);
            eProps.setProperty("file.reference." + jar2.getName(), jar2.getAbsolutePath());
            eProps.setProperty("javadoc.reference." + jar2.getName(), javadocPath);
            //set classpath
            eProps.setProperty("run.test.classpath", new String[]{"${javac.test.classpath}:", "${build.test.classes.dir}:",
                        "${file.reference." + jar.getName() + "}:", "${file.reference." + jar2.getName() + "}"
                    });

            eProps.setProperty("test-sys-prop.project.name", "connector-" + resourceName.toLowerCase());
            helper.putProperties(AntProjectHelper.PROJECT_PROPERTIES_PATH, eProps);

            Project project = pm.findProject(bundleDir);
            pm.saveProject(project);

            //replace standard project.xml with our custom version
            File fromFile = new File(toolkitLoc + "/misc/netbeans/nbproject", "project.xml");
            //FileObject's getPath() doesn't work correctly on all systems, so we change back to a File first
            File toFile = new File(bundleDirFile.getPath() + "/nbproject", "project.xml");
            copyFile(fromFile, toFile);
            fixProjectName(toFile, "Connector-" + resourceName, toolkitLoc);

            //create nblibraries.properties
            toFile = new File(bundleDirFile.getPath() + "/lib", "nblibraries.properties");
            toFile.createNewFile();

            pm.saveProject(project);
            pm.clearNonProjectCache();
            project = pm.findProject(bundleDir);

            try { //allow filesystem to update before opening project
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                Exceptions.printStackTrace(ex);
            }

            OpenProjects op = OpenProjects.getDefault();
            op.open(new Project[]{project}, false);
            op.setMainProject(project);

        } else {
            return "There already seems to be a Netbeans project in this bundle directory.";
        }
        return null;
    }

    private String getJavadocPath(String toolkitLoc) {
        File javadoc = new File(toolkitLoc + "/javadoc");
        if (!javadoc.isDirectory()) {
            throw new IllegalStateException("Could not find the toolkit/javadoc folder.");
        }
        return javadoc.getAbsolutePath();
    }

    private File getFrameworkHeadJAR(String toolkitLoc) {
        File dist = new File(toolkitLoc + "/dist");
        FilenameFilter filter = new FilenameFilter() {

            public boolean accept(File dir, String name) {
                if (!name.contains("internal") &&
                        !name.contains("contract") &&
                        name.contains("connector-framework") &&
                        name.endsWith(".jar")) {
                    return true;
                }
                return false;
            }
        };
        File[] jars = dist.listFiles(filter);
        if (jars.length > 1) {
            throw new IllegalStateException("Found more than one Framework-Head JAR.");
        } else if (jars.length < 1) {
            throw new IllegalStateException("Could not find a suitable Framework-Head JAR.");
        }
        return jars[0];
    }

    private File getFrameworkInternalJAR(String toolkitLoc) {
        File dist = new File(toolkitLoc + "/dist");
        FilenameFilter filter = new FilenameFilter() {

            public boolean accept(File dir, String name) {
                if (name.contains("internal") && name.endsWith(".jar")) {
                    return true;
                }
                return false;
            }
        };
        File[] jars = dist.listFiles(filter);
        if (jars.length > 1) {
            throw new IllegalStateException("Found more than one Framework-Internal JAR.");
        } else if (jars.length < 1) {
            throw new IllegalStateException("Could not find a suitable Framework-Internal JAR.");
        }
        return jars[0];
    }

    private File getFrameworkContractJAR(String toolkitLoc) {
        File dist = new File(toolkitLoc + "/dist");
        FilenameFilter filter = new FilenameFilter() {

            public boolean accept(File dir, String name) {
                if (name.contains("contract") && name.endsWith(".jar")) {
                    return true;
                }
                return false;
            }
        };
        File[] jars = dist.listFiles(filter);
        if (jars.length > 1) {
            throw new IllegalStateException("Found more than one Contract-Tests JAR.");
        } else if (jars.length < 1) {
            throw new IllegalStateException("Could not find a suitable Contract-Tests JAR.");
        }
        return jars[0];
    }

    private void fixProjectName(File file, String projectName, String frameworkDir) {
        BufferedReader in = null;
        BufferedWriter out = null;
        File temp = null;
        try {
            in = new BufferedReader(new FileReader(file));
            temp = new File(file.getAbsolutePath() + ".tmp");
            out = new BufferedWriter(new FileWriter(temp));
            while (in.ready()) {
                String line = in.readLine();
                line = line.replaceAll("@@ProjectName@@", projectName);
                out.write(line);
                out.newLine();
            }
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            try {
                in.close();
                out.close();
                copyFile(temp, file);
                temp.delete();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    private void copyDirectory(File srcDir, File dstDir) throws IOException {
        if (srcDir.isDirectory()) {
            if (!dstDir.exists()) {
                dstDir.mkdir();
            }
            String[] children = srcDir.list();
            for (int i = 0; i < children.length; i++) {
                copyDirectory(new File(srcDir, children[i]), new File(dstDir, children[i]));
            }
        } else if (srcDir.isFile()) {
            copyFile(srcDir, dstDir);
        }
    }

    private void copyFile(File src, File dst) throws IOException {
        dst.delete(); //destructive copy
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);
        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    /**
     * Initialize panels representing individual wizard's steps and sets
     * various properties for them influencing wizard appearance.
     */
    private WizardDescriptor.Panel[] getPanels() {
        if (panels == null) {
            panels = new WizardDescriptor.Panel[]{
                        new inputWizardPanel1(),
                        new inputWizardPanel2()
                    };
            String[] steps = new String[panels.length];
            for (int i = 0; i < panels.length; i++) {
                Component c = panels[i].getComponent();
                // Default step name to component name of panel. Mainly useful
                // for getting the name of the target chooser to appear in the
                // list of steps.
                steps[i] = c.getName();
                if (c instanceof JComponent) { // assume Swing components
                    JComponent jc = (JComponent) c;
                    // Sets step number of a component
                    jc.putClientProperty("WizardPanel_contentSelectedIndex", new Integer(i));
                    // Sets steps names for a panel
                    jc.putClientProperty("WizardPanel_contentData", steps);
                    // Turn on subtitle creation on each step
                    jc.putClientProperty("WizardPanel_autoWizardStyle", Boolean.TRUE);
                    // Show steps on the left side with the image on the background
                    jc.putClientProperty("WizardPanel_contentDisplayed", Boolean.TRUE);
                    // Turn on numbering of all steps
                    jc.putClientProperty("WizardPanel_contentNumbered", Boolean.TRUE);
                }
            }
        }
        return panels;
    }

    public String getName() {
        return NbBundle.getMessage(CreatorAction.class, "CTL_CreatorAction");
    }

    @Override
    protected void initialize() {
        super.initialize();
        // see org.openide.util.actions.SystemAction.iconResource() Javadoc for more details
        putValue("noIconInMenu", Boolean.TRUE);
    }

    @Override
    public String iconResource() {
        return null;
    }

    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return false;
    }
}
