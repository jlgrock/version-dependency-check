package com.github.jlgrock.versiondepcheck;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.Test;

import java.io.File;
import java.net.URL;

public class VerifyVersionsMojoTest {
    VerifyVersionsMojo verifyVersionsMojo = new VerifyVersionsMojo();

    @Test
    public void testGoodFile() throws MojoFailureException, MojoExecutionException {
        verifyVersionsMojo.checkFile(loadFile("good.xml"));
    }


    @Test(expected = MojoFailureException.class)
    public void testNonPropNotAllowedInDepMgmtFailure() throws MojoFailureException, MojoExecutionException {
        verifyVersionsMojo.checkFile(loadFile("non-prop-not-allowed-in-depmgmt.xml"));
    }

    @Test(expected = MojoFailureException.class)
    public void testVersionNotAllowedInDepsFailure() throws MojoFailureException, MojoExecutionException {
        verifyVersionsMojo.checkFile(loadFile("version-not-allowed-in-deps.xml"));
    }

    @Test(expected = MojoFailureException.class)
    public void testPropertyNotAllowedInDepsFailure() throws MojoFailureException, MojoExecutionException {
        verifyVersionsMojo.checkFile(loadFile("prop-not-allowed-in-deps.xml"));
    }

    private File loadFile(final String filename) {
        URL resource = getClass().getClassLoader().getResource(filename);
        if (resource != null) {
            String filePath = resource.getFile();
            return new File(filePath);
        }
        return null;
    }

}