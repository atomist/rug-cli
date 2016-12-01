package com.atomist.rug.cli.command.edit;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import com.atomist.rug.cli.AbstractCommandTest;

public class EditCommandIntegrationTest extends AbstractCommandTest {

    @Test
    public void testSuccessfulEdit() throws Exception {

        FileUtils.copyFile(new File("./README.md"), new File("./README.backup"));

        assertCommandLine(0, () -> {
            FileUtils.deleteQuietly(new File("./README.md"));
            FileUtils.moveFile(new File("./README.backup"), new File("./README.md"));
            FileUtils.deleteQuietly(new File("./README.backup"));
            FileUtils.deleteQuietly(new File("./.provenance.txt"));
            assertTrue(systemOutRule.getLogWithNormalizedLineSeparator()
                    .contains("README.md created"));
        }, "edit", "atomist-project-templates:common-editors:AddReadme", "projectName=TheName",
                "description=SomeDescription", "-a", "3.2.2");

    }

    @Test
    public void testUnSuccessfulEditWithInvalidParameter() throws Exception {
        assertFailure("Invalid parameter value\n  projectName = $#%$#%$#^$%$W...@432", "edit",
                "atomist-project-templates:common-editors:AddReadme",
                "projectName=$#%$#%$#^$%$W...@432", "description=Some", "-a", "3.2.2");
    }

    @Test
    public void testUnSuccessfulEditWithMissingParameters() throws Exception {
        assertFailure("Missing parameter value\n  projectName", "edit",
                "atomist-project-templates:common-editors:AddReadme", "description=Some", "-a",
                "3.2.2");
    }

    @Test
    public void testUnSuccessfulEditWithInvalidName() throws Exception {
        assertFailure("Did you mean?\n" + 
                "  AddReadme", "edit",
                "atomist-project-templates:common-editors:AddRame", "description=Some", "-a",
                "3.2.2");
    }

    @Test
    public void testUnSuccessfulEditWithMultipleMissingParameters() throws Exception {
        assertFailure("Missing parameter values\n  projectName\n  description", "edit",
                "atomist-project-templates:common-editors:AddReadme", "-a", "3.2.2");
    }
}
