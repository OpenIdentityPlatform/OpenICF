/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.     
 * 
 * The contents of this file are subject to the terms of the Common Development 
 * and Distribution License("CDDL") (the "License").  You may not use this file 
 * except in compliance with the License.
 * 
 * You can obtain a copy of the License at 
 * http://IdentityConnectors.dev.java.net/legal/license.txt
 * See the License for the specific language governing permissions and limitations 
 * under the License. 
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 */
package org.identityconnectors.toolkitmodule;

import java.awt.Component;
import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;

public class inputWizardPanel1 implements WizardDescriptor.Panel, DocumentListener {

    /**
     * The visual component that displays this panel. If you need to access the
     * component from this class, just use getComponent().
     */
    private inputVisualPanel1 component;
    private WizardDescriptor wizardDescriptor;
    private final Set<ChangeListener> listeners = new HashSet<ChangeListener>(1);
    private boolean isValid = false;

    // Get the visual component for the panel. In this template, the component
    // is kept separate. This can be more efficient: if the wizard is created
    // but never displayed, or not all panels are displayed, it is better to
    // create only those which really need to be visible.
    public Component getComponent() {
        if (component == null) {
            component = new inputVisualPanel1();
            component.getTxtToolkitLoc().getDocument().addDocumentListener(this);
            component.getTxtBundleDir().getDocument().addDocumentListener(this);
            component.getTxtResourceName().getDocument().addDocumentListener(this);
            component.getTxtPackageName().getDocument().addDocumentListener(this);
        }
        return component;
    }

    public HelpCtx getHelp() {
        return new HelpCtx("org.identityconnectors.toolkitmodule.about");
    }

    public boolean isValid() {
        return isValid;
    }

    public final void addChangeListener(ChangeListener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    public final void removeChangeListener(ChangeListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    protected final void fireChangeEvent() {
        Iterator<ChangeListener> it;
        synchronized (listeners) {
            it = new HashSet<ChangeListener>(listeners).iterator();
        }
        ChangeEvent ev = new ChangeEvent(this);
        while (it.hasNext()) {
            it.next().stateChanged(ev);
        }
    }

    private void change() {
        String toolkitLoc = component.getTxtToolkitLoc().getText();
        File f = new File(toolkitLoc);
        if (!f.isDirectory()) {
            component.getLblError().setText("Toolkit Location does not point to a valid directory.");
            setValid(false);
            //throw new WizardValidationException(component.getTxtToolkitLoc(), "Toolkit Location Error", "This is not a valid toolkit folder.");
            return;
        } else {
            String[] files = f.list();
            boolean b = false;
            for (String fName : files) {
                if (fName.equals("connector_build.xml")) {
                    b = true;
                    break;
                }
            }
            if (!b) {
                component.getLblError().setText("Toolkit Location does not point to a valid toolkit.");
                setValid(false);
                return;
            }
        }

        String bundleDir = component.getTxtBundleDir().getText();
        String resourceName = component.getTxtResourceName().getText();
        String packageName = component.getTxtPackageName().getText();

        if (bundleDir.length() == 0) {
            component.getLblError().setText("You must specify a bundle directory.");
            setValid(false);
        } else if (resourceName.length() == 0) {
            component.getLblError().setText("You must specify a resource name.");
            setValid(false);
        } else if (packageName.length() == 0) {
            component.getLblError().setText("You must specify a package name.");
            setValid(false);
        } else {
            component.getLblError().setText(null);
            setValid(true);
        }
    }

    private void setValid(boolean val) {
        if (isValid != val) {
            isValid = val;
            fireChangeEvent();  // must do this to enable next/finish button
        }
    }

    public void insertUpdate(DocumentEvent arg0) {
        change();
    }

    public void removeUpdate(DocumentEvent arg0) {
        change();
    }

    public void changedUpdate(DocumentEvent arg0) {
        change();
    }

    // You can use a settings object to keep track of state. Normally the
    // settings object will be the WizardDescriptor, so you can use
    // WizardDescriptor.getProperty & putProperty to store information entered
    // by the user.
    public void readSettings(Object settings) {
        wizardDescriptor = (WizardDescriptor) settings;
    }

    public void storeSettings(Object settings) {
        wizardDescriptor = (WizardDescriptor) settings;

        String toolkitLoc = component.getTxtToolkitLoc().getText();
        String bundleDir = component.getTxtBundleDir().getText();
        String resourceName = component.getTxtResourceName().getText();
        String packageName = component.getTxtPackageName().getText();

        wizardDescriptor.putProperty("toolkit.loc", toolkitLoc);
        wizardDescriptor.putProperty("bundle.dir", bundleDir);
        wizardDescriptor.putProperty("resource.name", resourceName);
        wizardDescriptor.putProperty("package.name", packageName);
    }
}

