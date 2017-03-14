package com.github.jlgrock.versiondepcheck;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies that a Version matches the format "${...}" for dependency management
 * and that the version is not included in the dependencies sections.
 *
 * @goal version-check
 * @phase validate
 * @requiresProject true
 * @aggregator false
 * @requiresOnline false
 * @requiresDirectInvocation false
 * @requiresReports false
 * @threadSafe
 */
public class VerifyVersionsMojo extends AbstractMojo {

    private static final String VERSION_ALLOWED_STRING = "(\\w*)(\\$\\{[a-zA-Z0-9\\-.]*\\})(.*)";
    private static final Pattern VERSION_ALLOWED_PATTERN = Pattern.compile(VERSION_ALLOWED_STRING);

    private static final String PROJECT_XPATH_PREFIX = "/project";
    private static final String PROFILE_XPATH_PREFIX = "/profiles/profile";
    private static final String DEPENDENCY_MANAGEMENT_XPATH_PREFIX = "/dependencyManagement";
    private static final String DEPENDENCY_VERSION_XPATH_LOC = "/dependencies/dependency/version";


    /**
     * The entry point for a maven mojo.  This kicks off the maven scanning against the pom file.
     *
     * @throws MojoExecutionException if there are any errors in execution
     * @throws MojoFailureException   if there are any properties invalidly used or where they shouldn't be
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        File fXmlFile = new File("pom.xml");
        checkFile(fXmlFile);
    }

    /**
     * Will allow you to check a particular file.  It expects that this file follows the maven
     * defined format.
     *
     * @param pomFile the file to scan
     * @throws MojoExecutionException if there are any errors in execution
     * @throws MojoFailureException   if there are any properties invalidly used or where they shouldn't be
     */
    protected void checkFile(final File pomFile) throws MojoExecutionException, MojoFailureException {
        try {
            getLog().debug("pom exists: " + pomFile.exists());
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(pomFile);

            //optional, but recommended
            //read this - http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
            doc.getDocumentElement().normalize();

            getLog().debug("Root element: " + doc.getDocumentElement().getNodeName());
            checkDependencyManagement(doc);
            checkDependencies(doc);
            checkProfilesDependencyManagement(doc);
            checkProfilesDependencies(doc);

        } catch (MojoFailureException mfe) {
            throw mfe;
        } catch (Exception e) {
            printDebugStackTrace(e);
            throw new MojoExecutionException(e.getMessage());
        }
    }

    /**
     * Turn a printwriter into a string for outputting to debug
     *
     * @param exception the exception to print
     */
    private void printDebugStackTrace(final Exception exception) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        getLog().debug(sw.toString());
    }

    /**
     * Check the versions tag for every dependency in each dependency list provided.
     *
     * @param versionCollection a collection of version nodes
     * @param allowProperties   whether or not properties are allowed in this dependency list.
     *                          Version numbers are never allowed.
     * @throws MojoFailureException whenever the version does not follow the defined standard.
     */
    private void checkVersions(final NodeList versionCollection, boolean allowProperties)
            throws MojoFailureException {
        if (allowProperties) {
            //dependency management sections
            boolean foundErrorsInMatch = false;
            String artifacts = "";
            for (int i = 0; i < versionCollection.getLength(); i++) {
                Node node = versionCollection.item(i);
                String version = node.getTextContent();
                Matcher m = VERSION_ALLOWED_PATTERN.matcher(version);
                if (!m.matches()) {
                    foundErrorsInMatch = true;
                    artifacts += artifactBuilder((Element) node.getParentNode());
                }
            }

            if (foundErrorsInMatch) {
                throw new MojoFailureException("dependencyManagment sections must use variables."
                        + "These should likely be referenced in the dependency management project.\n"
                        + "The following artifacts are in error:" + artifacts);
            }
        } else {
            if (versionCollection.getLength() > 0) {
                String artifacts = allArtifactsBuilder(versionCollection);
                throw new MojoFailureException("\"version\" tag found where it was not allowed "
                        + "for the following artifacts: \n"
                        + artifacts
                        + "\n Versions must be inherited from dependency management sections.");
            }
        }
    }

    /**
     * Based on the xml collection of versions, get the appropriate artifacts coordinates to print out.
     *
     * @param versionCollection the collection to print out the full artifact coordinates
     * @return the string representing a newline delimited list of artifact coordinates
     */
    private String allArtifactsBuilder(final NodeList versionCollection) {
        String artifacts = "";
        for (int i = 0; i < versionCollection.getLength(); i++) {
            Element element = (Element) versionCollection.item(i).getParentNode();
            artifacts += artifactBuilder(element);
        }
        return artifacts;
    }

    /**
     * Generate the string representing the artifact coordinates.
     *
     * @param element the element representing a single dependency
     * @return the string representing the artifact coordinates
     */
    private String artifactBuilder(final Element element) {
        String groupId = element.getElementsByTagName("groupId").item(0).getTextContent();
        String artifactId = element.getElementsByTagName("artifactId").item(0).getTextContent();
        String version = element.getElementsByTagName("version").item(0).getTextContent();
        return groupId + ":" + artifactId + ":" + version + "\n";
    }

    /**
     * Check the dependencies section.
     *
     * @param doc the document to search
     * @throws MojoExecutionException if there are any errors in execution
     * @throws MojoFailureException   if there are any properties invalidly used or where they shouldn't be
     */
    private void checkDependencies(final Document doc) throws MojoExecutionException, MojoFailureException {
        findByPath(doc, PROJECT_XPATH_PREFIX + DEPENDENCY_VERSION_XPATH_LOC, false);
    }

    /**
     * Check the dependencies management section.
     *
     * @param doc the document to search
     * @throws MojoExecutionException if there are any errors in execution
     * @throws MojoFailureException   if there are any properties invalidly used or where they shouldn't be
     */
    private void checkDependencyManagement(final Document doc) throws MojoExecutionException, MojoFailureException {
        findByPath(doc, PROJECT_XPATH_PREFIX + DEPENDENCY_MANAGEMENT_XPATH_PREFIX + DEPENDENCY_VERSION_XPATH_LOC, true);
    }

    /**
     * Check the profiles dependencies section.
     *
     * @param doc the document to search
     * @throws MojoExecutionException if there are any errors in execution
     * @throws MojoFailureException   if there are any properties invalidly used or where they shouldn't be
     */
    private void checkProfilesDependencies(final Document doc) throws MojoExecutionException, MojoFailureException {
        findByPath(doc, PROJECT_XPATH_PREFIX + PROFILE_XPATH_PREFIX + DEPENDENCY_VERSION_XPATH_LOC, false);
    }

    /**
     * Check the profiles dependencies management section.
     *
     * @param doc the document to search
     * @throws MojoExecutionException if there are any errors in execution
     * @throws MojoFailureException   if there are any properties invalidly used or where they shouldn't be
     */
    private void checkProfilesDependencyManagement(final Document doc) throws MojoExecutionException, MojoFailureException {
        findByPath(doc, PROJECT_XPATH_PREFIX + PROFILE_XPATH_PREFIX + DEPENDENCY_MANAGEMENT_XPATH_PREFIX + DEPENDENCY_VERSION_XPATH_LOC, true);
    }

    /**
     * Using an xpath expression, gather the appropriate collection of tags and pass it to checkVersions.
     *
     * @param doc             the document to search
     * @param path            the path to find the dependencies collection
     * @param allowProperties whether or not properties are allowed at this location
     * @throws MojoExecutionException if there are any errors in execution
     * @throws MojoFailureException   if there are any properties invalidly used or where they shouldn't be
     */
    private void findByPath(final Document doc, final String path, boolean allowProperties)
            throws MojoExecutionException, MojoFailureException {
        try {
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodeCollection = (NodeList) xPath.evaluate(path, doc.getDocumentElement(), XPathConstants.NODESET);
            getLog().debug("size of collection matching \"" + path + "\": " + nodeCollection.getLength());
            checkVersions(nodeCollection, allowProperties);
        } catch (XPathExpressionException e) {
            printDebugStackTrace(e);
            throw new MojoExecutionException(e.getMessage());
        }
    }
}