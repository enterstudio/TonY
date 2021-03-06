package utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import models.JobConfig;
import models.JobMetadata;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import play.Logger;
import play.libs.Json;

import static utils.HdfsUtils.*;


/**
 * The class handles parsing different file format
 */
public class ParserUtils {
  private static final Logger.ALogger LOG = Logger.of(ParserUtils.class);

  public static JobMetadata parseMetadata(FileSystem fs, Path metadataFilePath) {
    if (!pathExists(fs, metadataFilePath)) {
      return new JobMetadata();
    }

    String fileContent = contentOfHdfsFile(fs, metadataFilePath);
    JobMetadata jobMetadata = new JobMetadata();
    JsonNode jObj;
    try {
      jObj = Json.parse(fileContent);
    } catch (JsonSyntaxException e) {
      LOG.error("Couldn't parse metadata", e);
      return jobMetadata;
    }
    jobMetadata.setId(jObj.get("id").textValue());
    jobMetadata.setJobLink(jObj.get("url").textValue());
    jobMetadata.setConfigLink("/jobs/" + jobMetadata.getId());
    jobMetadata.setStarted(jObj.get("started").textValue());
    jobMetadata.setCompleted(jObj.get("completed").textValue());
    jobMetadata.setCompleted(jObj.get("completed").textValue());
    jobMetadata.setStatus(jObj.get("status").textValue());
    jobMetadata.setUser(jObj.get("user").textValue());
    LOG.debug("Successfully parsed metadata");
    return jobMetadata;
  }

  public static List<JobConfig> parseConfig(FileSystem fs, Path configFilePath) {
    if (!pathExists(fs, configFilePath)) {
      return new ArrayList<>();
    }
    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    List<JobConfig> configs = new ArrayList<>();

    try (FSDataInputStream inStrm = fs.open(configFilePath)) {
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc = dBuilder.parse(inStrm);
      doc.getDocumentElement().normalize();

      NodeList properties = doc.getElementsByTagName("property");

      for (int i = 0; i < properties.getLength(); i++) {
        Node property = properties.item(i);
        if (property.getNodeType() == Node.ELEMENT_NODE) {
          Element p = (Element) property;
          JobConfig jobConf = new JobConfig();
          jobConf.setName(p.getElementsByTagName("name").item(0).getTextContent());
          jobConf.setValue(p.getElementsByTagName("value").item(0).getTextContent());
          jobConf.setFinal(p.getElementsByTagName("final").item(0).getTextContent().equals("true"));
          jobConf.setSource(p.getElementsByTagName("source").item(0).getTextContent());
          configs.add(jobConf);
        }
      }
    } catch (SAXException e) {
      LOG.error("Failed to parse config file", e);
      return new ArrayList<>();
    } catch (ParserConfigurationException e) {
      LOG.error("Failed to init XML parser", e);
      return new ArrayList<>();
    } catch (IOException e) {
      LOG.error("Failed to read config file", e);
      return new ArrayList<>();
    }
    LOG.debug("Successfully parsed config");
    return configs;
  }

  private ParserUtils() { }
}
