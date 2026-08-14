package com.example.tomatomall.configure;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenSurefireConfigurationTest {

    @Test
    void pomKeepsTestsInOneReusableForkedJvm() throws Exception {
        Path pom = Paths.get("pom.xml");
        assertTrue(Files.isRegularFile(pom), "test must run from the back_end Maven project directory");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        Document document = factory.newDocumentBuilder().parse(pom.toFile());

        Element surefirePlugin = findPlugin(document, "maven-surefire-plugin");
        assertEquals("3.2.5", childText(surefirePlugin, "version"));
        Element configuration = childElement(surefirePlugin, "configuration");
        assertEquals("1", childText(configuration, "forkCount"));
        assertEquals("true", childText(configuration, "reuseForks"));
    }

    private Element findPlugin(Document document, String artifactId) {
        NodeList plugins = document.getElementsByTagName("plugin");
        for (int i = 0; i < plugins.getLength(); i++) {
            Element plugin = (Element) plugins.item(i);
            if (artifactId.equals(childText(plugin, "artifactId"))) {
                return plugin;
            }
        }
        throw new AssertionError("pom.xml must explicitly configure " + artifactId);
    }

    private String childText(Element parent, String tagName) {
        Element child = childElement(parent, tagName);
        return child == null ? null : child.getTextContent().trim();
    }

    private Element childElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && tagName.equals(child.getNodeName())) {
                return (Element) child;
            }
        }
        return null;
    }
}
