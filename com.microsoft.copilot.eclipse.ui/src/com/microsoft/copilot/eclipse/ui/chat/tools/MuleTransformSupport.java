// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.chat.tools;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Shared XML and DataWeave helpers for Transform Message tools.
 */
public final class MuleTransformSupport {
  public static final String EE_NS = "http://www.mulesoft.org/schema/mule/ee/core";
  public static final String DOC_NS = "http://www.mulesoft.org/schema/mule/documentation";
  public static final String TARGET_ATTRIBUTES = "attributes";
  public static final String TARGET_PAYLOAD = "payload";
  public static final String TARGET_VARIABLE_PREFIX = "variable:";

  private static final Path MULE_SOURCE_PATH = Path.of("src", "main", "mule");
  private static final Path RESOURCES_PATH = Path.of("src", "main", "resources");

  private MuleTransformSupport() {
  }

  public static Document parseXml(Path file) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    trySetFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
    trySetFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
    trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
    trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
    try (InputStream inputStream = Files.newInputStream(file)) {
      return factory.newDocumentBuilder().parse(inputStream);
    }
  }

  static void serializeDocument(Document document, Path xmlPath) throws Exception {
    String original = Files.readString(xmlPath, StandardCharsets.UTF_8);
    boolean hasXmlDeclaration = original.stripLeading().startsWith("<?xml");
    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    trySetAttribute(transformerFactory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
    trySetAttribute(transformerFactory, XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
    Transformer transformer = transformerFactory.newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, hasXmlDeclaration ? "no" : "yes");
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
    StringWriter writer = new StringWriter();
    transformer.transform(new DOMSource(document), new StreamResult(writer));
    Files.writeString(xmlPath, writer.toString(), StandardCharsets.UTF_8);
  }

  public static List<Element> findTransforms(Document document, String transformName, String transformId) {
    List<Element> matched = new ArrayList<>();
    var transforms = document.getElementsByTagNameNS(EE_NS, "transform");
    for (int i = 0; i < transforms.getLength(); i++) {
      if (transforms.item(i) instanceof Element element
          && matchesTransform(element, transformName, transformId, transformName.isBlank() && transformId.isBlank())) {
        matched.add(element);
      }
    }
    return matched;
  }

  public static List<Element> directChildren(Element parent, String localName) {
    List<Element> children = new ArrayList<>();
    for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element element && EE_NS.equals(element.getNamespaceURI())
          && localName.equals(element.getLocalName())) {
        children.add(element);
      }
    }
    return children;
  }

  public static Element firstDirectChild(Element parent, String localName) {
    List<Element> children = directChildren(parent, localName);
    return children.isEmpty() ? null : children.get(0);
  }

  public static ScriptContent readScriptContent(Element element, Path xmlPath) {
    String resource = element.getAttribute("resource");
    if (resource.isBlank()) {
      return new ScriptContent(element.getTextContent().trim(), "", null, "");
    }
    ResourceResolution resourceResolution = resolveResource(xmlPath, resource);
    if (resourceResolution.path() != null && Files.isRegularFile(resourceResolution.path())) {
      try {
        String script = Files.readString(resourceResolution.path(), StandardCharsets.UTF_8).trim();
        return new ScriptContent(script, resource, resourceResolution.path(), "resolved");
      } catch (Exception e) {
        return new ScriptContent("", resource, resourceResolution.path(), "unreadable: " + e.getMessage());
      }
    }
    return new ScriptContent("", resource, resourceResolution.path(), resourceResolution.status());
  }

  static WriteContentResult writeScriptContent(Document document, Element element, Path xmlPath, String dwlScript)
      throws Exception {
    String resource = element.getAttribute("resource");
    if (!resource.isBlank()) {
      ResourceResolution resourceResolution = resolveResource(xmlPath, resource);
      if (resourceResolution.path() == null || !Files.isRegularFile(resourceResolution.path())) {
        return new WriteContentResult(false, false, null,
            "External DWL resource could not be resolved: " + resource + " (" + resourceResolution.status() + ")");
      }
      Files.writeString(resourceResolution.path(), dwlScript, StandardCharsets.UTF_8);
      return new WriteContentResult(true, false, resourceResolution.path(),
          "Updated external DWL resource " + resourceResolution.path().getFileName());
    }

    while (element.hasChildNodes()) {
      element.removeChild(element.getFirstChild());
    }
    CDATASection cdata = document.createCDATASection(dwlScript);
    element.appendChild(cdata);
    return new WriteContentResult(true, true, xmlPath, "Updated inline DataWeave script");
  }

  public static String transformLabel(Element transform) {
    String docName = transform.getAttributeNS(DOC_NS, "name");
    if (docName.isBlank()) {
      docName = transform.getAttribute("doc:name");
    }
    String docId = transform.getAttributeNS(DOC_NS, "id");
    if (docId.isBlank()) {
      docId = transform.getAttribute("doc:id");
    }
    return docName.isBlank() ? "(unnamed)" : docName + (docId.isBlank() ? "" : " [id=" + docId + "]");
  }

  private static boolean matchesTransform(Element transform, String transformName, String transformId,
      boolean matchAll) {
    if (matchAll) {
      return true;
    }
    String docName = transform.getAttributeNS(DOC_NS, "name");
    if (docName.isBlank()) {
      docName = transform.getAttribute("doc:name");
    }
    String docId = transform.getAttributeNS(DOC_NS, "id");
    if (docId.isBlank()) {
      docId = transform.getAttribute("doc:id");
    }
    if (!transformName.isBlank() && !transformId.isBlank()) {
      return matchesName(transformName, docName) && matchesId(transformId, docId);
    }
    return (!transformName.isBlank() && matchesName(transformName, docName))
        || (!transformId.isBlank() && matchesId(transformId, docId));
  }

  private static boolean matchesName(String filter, String docName) {
    String f = filter.trim();
    String n = docName.trim();
    // Exact match first, then case-insensitive, then substring (handles partial names from AI)
    return n.equals(f) || n.equalsIgnoreCase(f) || n.toLowerCase().contains(f.toLowerCase());
  }

  private static boolean matchesId(String filter, String docId) {
    String f = filter.trim();
    String d = docId.trim();
    return d.equals(f) || d.equalsIgnoreCase(f);
  }

  private static ResourceResolution resolveResource(Path xmlPath, String resource) {
    Path projectRoot = findProjectRoot(xmlPath);
    if (projectRoot == null) {
      return new ResourceResolution(null, "projectRootNotFound");
    }
    Path resourcesRoot = projectRoot.resolve(RESOURCES_PATH).toAbsolutePath().normalize();
    Path resourcePath = Path.of(resource);
    Path candidate = resourcePath.isAbsolute() ? resourcePath.normalize()
        : resourcesRoot.resolve(resourcePath).normalize();
    if (!candidate.startsWith(resourcesRoot)) {
      return new ResourceResolution(candidate, "outsideResources");
    }
    return new ResourceResolution(candidate, Files.isRegularFile(candidate) ? "resolved" : "notFound");
  }

  private static Path findProjectRoot(Path xmlPath) {
    Path current = xmlPath.toAbsolutePath().normalize().getParent();
    while (current != null) {
      if (current.endsWith(MULE_SOURCE_PATH)) {
        Path srcMain = current.getParent();
        Path src = srcMain == null ? null : srcMain.getParent();
        return src == null ? null : src.getParent();
      }
      current = current.getParent();
    }

    current = xmlPath.toAbsolutePath().normalize().getParent();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("pom.xml"))) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }

  private static void trySetFeature(DocumentBuilderFactory factory, String feature, boolean enabled) {
    try {
      factory.setFeature(feature, enabled);
    } catch (Exception ignored) {
      // Some XML parsers do not expose every hardening feature.
    }
  }

  private static void trySetAttribute(TransformerFactory factory, String attribute, String value) {
    try {
      factory.setAttribute(attribute, value);
    } catch (Exception ignored) {
      // Some transformer implementations do not expose every hardening attribute.
    }
  }

  public record ScriptContent(String script, String resource, Path resourcePath, String resourceStatus) {
  }

  record WriteContentResult(boolean success, boolean xmlModified, Path modifiedPath, String message) {
  }

  private record ResourceResolution(Path path, String status) {
  }
}
