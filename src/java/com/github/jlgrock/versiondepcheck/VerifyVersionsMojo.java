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
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Echos an object string to the output screen.
 * @goal version-check
 * @threadSafe
 */
public class VerifyVersionsMojo extends AbstractMojo {

    private static final String VERSION_ALLOWED_STRING = "(\\w*)(\\$\\{[a-zA-Z0-9\\-.]*\\})(.*)";
    private static final Pattern VERSION_ALLOWED_PATTERN = Pattern.compile(VERSION_ALLOWED_STRING);

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {

            File fXmlFile = new File("pom.xml");
            getLog().debug("pom exists: " + fXmlFile.exists());
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(fXmlFile);

            //optional, but recommended
            //read this - http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
            doc.getDocumentElement().normalize();
            getLog().debug("Root element: " + doc.getDocumentElement().getNodeName());
            checkDependencyManagement(doc);
            checkDependencies(doc);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkDependenciesForProperties(final NodeList dependenciesCollection, boolean allowProperties)
            throws MojoFailureException {
        getLog().debug("dependencies length: " + dependenciesCollection.getLength());
        for (int i = 0; i < dependenciesCollection.getLength(); i++) {
            Node dependencyCollectionItem = dependenciesCollection.item(i);
            if (dependencyCollectionItem.getNodeType() == Node.ELEMENT_NODE) {
                Element dependencyCollectionElement = (Element) dependencyCollectionItem;
                NodeList dependencyCollection = dependencyCollectionElement.getElementsByTagName("dependency");
                for (int j = 0; j < dependencyCollection.getLength(); j++) {
                    Node dependencyItem = dependencyCollection.item(j);
                    if (dependencyItem.getNodeType() == Node.ELEMENT_NODE) {
                        Element dependencyElement = (Element) dependencyItem;

                        String groupId = dependencyElement.getElementsByTagName("groupId").item(0).getTextContent();
                        String artifactId = dependencyElement.getElementsByTagName("artifactId").item(0).getTextContent();
                        NodeList versionCollection = dependencyElement.getElementsByTagName("version");
                        if (!allowProperties && versionCollection.getLength() > 0) {
                            String version = versionCollection.item(0).getTextContent();
                            throw new MojoFailureException("\"version\" tag found where it was not allowed "
                                    + "for artifact \"" + groupId + ":" + artifactId + ":" + version
                                    + "\".  Please make sure you are checking in versions in the dependency management project.");
                        }
                        String version = versionCollection.item(0).getTextContent();
                        getLog().debug("child version: " + version);
                        Matcher m = VERSION_ALLOWED_PATTERN.matcher(version);
                        if (!m.matches()) {
                            throw new MojoFailureException("\"version\" tag with value of \"" + version
                                    + "\" is not using a variable.  "
                                    + "Please make sure you are checking in versions in the dependency management project.");
                        }
                    }
                }
            }
        }
    }

    private void checkDependencies(final Document doc) throws MojoExecutionException, MojoFailureException {
        NodeList dependenciesCollection = doc.getElementsByTagName("dependencies");
        getLog().debug("dependencies size: " + dependenciesCollection.getLength());
        for (int i = 0; i < dependenciesCollection.getLength(); i++) {
            Node dependencies = dependenciesCollection.item(i);
            NodeList dependenciesChildren = dependencies.getChildNodes();
            checkDependenciesForProperties(dependenciesChildren, false);
        }
    }

    private void checkDependencyManagement(final Document doc) throws MojoExecutionException, MojoFailureException {
        NodeList dependencyManagementCollection = doc.getElementsByTagName("dependencyManagement");
        getLog().debug("dependencyManagement size: " + dependencyManagementCollection.getLength());
        for (int i = 0; i < dependencyManagementCollection.getLength(); i++) {
            Element dependencyManagement = (Element) dependencyManagementCollection.item(i);
            NodeList dependenciesCollection = dependencyManagement.getElementsByTagName("dependencies");
            checkDependenciesForProperties(dependenciesCollection, true);
        }
    }
}