/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */


package org.forgerock.openicf.connector.ssh

import org.forgerock.openicf.connectors.ssh.CommandLineBuilder
import org.identityconnectors.common.logging.Log
import org.testng.Assert
import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test

class CmdLineBuilderTests {
    private static final Log logger = Log.getLog(CmdLineBuilderTests.class);

    @BeforeClass
    public void setUp() {

    }

    @AfterClass
    public void tearDown() {
    }

    @Test(enabled = true, groups = "basic")
    public void testCmdlineBuilderBasic1() {
        // Simple Test
        // Command is: command -p value
        CommandLineBuilder cmdlb = new CommandLineBuilder("command").p("value");
        Assert.assertEquals("command -p value", cmdlb.build())
    }

    @Test(enabled = true, groups = "basic")
    public void testCmdlineBuilderBasic2() {
        // Simple switch
        // Command is: command -p -q
        CommandLineBuilder cmdlb = new CommandLineBuilder("command").p().q();
        Assert.assertEquals("command -p -q", cmdlb.build())
    }

    @Test(enabled = true, groups = "basic")
    public void testCmdlineBuilderBasic3() {
        // Simple switch with --
        // Command is: command --param1 --param2
        CommandLineBuilder cmdlb = new CommandLineBuilder("command")
        cmdlb.dash = "--"
        cmdlb.param1().param2();
        Assert.assertEquals("command --param1 --param2", cmdlb.build())
    }

    @Test(enabled = true, groups = "basic")
    public void testCmdlineBuilderBasic4() {
        // Simple Test with --
        // Command is: command --param value
        CommandLineBuilder cmdlb = new CommandLineBuilder("command").param("value", "--");
        Assert.assertEquals("command --param value", cmdlb.build())
    }

    @Test(enabled = true, groups = "basic")
    public void testCmdlineBuilderBasic5() {
        // Command is: command -p value1 --param value2
        CommandLineBuilder cmdlb = new CommandLineBuilder("command").p("value1").param("value2", "--");
        Assert.assertEquals("command -p value1 --param value2", cmdlb.build())
    }

    @Test(enabled = true, groups = "quote")
    public void testCmdlineBuilderQuote1() {
        // Command is: command -p 'this is value1' value2
        // 'this is value1' should be quoted with the default quote '
        CommandLineBuilder cmdlb = new CommandLineBuilder("command").p("this is value1").append("value2");
        Assert.assertEquals("command -p 'this is value1' value2", cmdlb.build())
    }

    @Test(enabled = true, groups = "quote")
    public void testCmdlineBuilderQuote2() {
        // Command is: command -p 'this is value1' value2
        // 'this is value1' should be quoted with the specified quote
        CommandLineBuilder cmdlb = new CommandLineBuilder("command")
        cmdlb.quoteSymbol = "\""
        cmdlb.p("this is value1").append("value2");
        Assert.assertEquals("command -p \"this is value1\" value2", cmdlb.build())
    }

    @Test(enabled = true, groups = "quote")
    public void testCmdlineBuilderQuote3() {
        // Command is: command -p this is value1
        // quote = false
        // 'this is value1' should not be quoted
        CommandLineBuilder cmdlb = new CommandLineBuilder("command")
        cmdlb.quote = false
        cmdlb.p("this is value1");
        Assert.assertEquals("command -p this is value1", cmdlb.build())
    }

    @Test(enabled = true, groups = "trim")
    public void testCmdlineBuilderTrim1() {
        // Command is: command -p ' this is value1 '
        // 'this is value1' should be trimmed by default
        CommandLineBuilder cmdlb = new CommandLineBuilder("command").p("  this is value  ")
        Assert.assertEquals("command -p 'this is value'", cmdlb.build())
    }
    @Test(enabled = true, groups = "trim")
    public void testCmdlineBuilderTrim2() {
        // Command is: command -p ' this is value1 '
        // trim = false
        // 'this is value1' should not be trimmed
        CommandLineBuilder cmdlb = new CommandLineBuilder("command")
        cmdlb.trim = false
        cmdlb.p("  this is value  ")
        Assert.assertEquals("command -p '  this is value  '", cmdlb.build())
    }

    @Test(enabled = true, groups = "blank")
    public void testCmdlineBuilderBlank1() {
        // Test blank values
        // Command is: command -p '  '
        // allowBlank is false by default
        // trim is true by default
        CommandLineBuilder cmdlb = new CommandLineBuilder("command").p(" ");
        Assert.assertEquals("command", cmdlb.build())
    }

    @Test(enabled = true, groups = "blank")
    public void testCmdlineBuilderBlank2() {
        // Test blank values
        // Command is: command -p '  '
        // allowBlank is false by default
        // trim is set to false
        CommandLineBuilder cmdlb = new CommandLineBuilder("command")
        cmdlb.trim = false
        cmdlb.p(" ");
        Assert.assertEquals("command", cmdlb.build())
    }

    @Test(enabled = true, groups = "blank")
    public void testCmdlineBuilderBlank3() {
        // Test blank values
        // Command is: command -p ' '
        // allowBlank is set to true
        // trimming does not happen if allowBlank=true
        CommandLineBuilder cmdlb = new CommandLineBuilder("command")
        cmdlb.allowBlank = true
        cmdlb.p(" ");
        Assert.assertEquals("command -p ' '", cmdlb.build())
    }

    @Test(enabled = true, groups = "blank")
    public void testCmdlineBuilderBlank4() {
        // Test blank values
        // Command is: command -p ' '
        // allowBlank is set to true
        // quote is set to false
        // trimming does not happen if allowBlank=true
        CommandLineBuilder cmdlb = new CommandLineBuilder("command")
        cmdlb.allowBlank = true
        cmdlb.quote = false
        cmdlb.p(" ");
        Assert.assertEquals("command -p  ", cmdlb.build())
    }
}