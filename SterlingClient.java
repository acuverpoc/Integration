package com.mozu.sterling.service;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.ibm.sterling.afc.xapiclient.japi.XApi;
import com.ibm.sterling.afc.xapiclient.japi.XApiClientFactory;
import com.ibm.sterling.afc.xapiclient.japi.XApiEnvironment;
import com.mozu.sterling.model.Setting;

@Service
public class SterlingClient {
    private static final Logger logger = LoggerFactory.getLogger(SterlingClient.class);
    public final static String STERLING_BOOLEAN_VALUE_YES = "Y";
    public final static String STERLING_BOOLEAN_VALUE_NO = "N";

    private DocumentBuilder docBuilder;

    public SterlingClient () throws Exception {
        DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
        try {
            docBuilder = fac.newDocumentBuilder();
        } catch (ParserConfigurationException pce){
            logger.error("Unable to create DocumentBuilder: " + pce.getMessage());
            throw pce;
        }
    }

    public Object convertXmlToObject(Document doc, Class<?> clazz) throws Exception {
        JAXBContext jc = JAXBContext.newInstance(clazz);
        Unmarshaller unmarshaller = jc.createUnmarshaller();

        Object obj = unmarshaller.unmarshal(doc);
        
        return obj;
    }

    public Document convertObjectToXml  (Object obj, Class<?> clazz) throws Exception {
        DocumentBuilder docBuilder = getDocumentBuilder();
        JAXBContext jc = JAXBContext.newInstance(clazz);
        Marshaller marshaller = jc.createMarshaller();
        Document document = docBuilder.newDocument();

        marshaller.marshal(obj, document);
        
        return document;
    }

    /**
     * invoke the sterling service using the given service name.
     * @param serviceName
     * @param inputDoc
     * @return
     * @throws Exception
     */
    public Document invoke(String serviceName, Document inputDoc, Setting setting) throws Exception {
        Document outputDocument = null;
        // create url, you can also put yif.httpapi.url in your
        // yifclient.properties in the resources.jar
        Map<String, String> props = new HashMap<>();
        props.put("yif.httpapi.url", setting.getSterlingUrl());
        XApi api = XApiClientFactory.getInstance().getApi("HTTP", props);

        Document environmentDoc = docBuilder.newDocument();
        Element envElement = environmentDoc.createElement("YFSEnvironment");
        envElement.setAttribute("userId", setting.getSterlingUserId());
        envElement.setAttribute("progId", setting.getSterlingUserId());
        environmentDoc.appendChild(envElement);

        XApiEnvironment env = null;
        String sessionId = null;
        try {
            env = api.createEnvironment(environmentDoc);
            Document loginInput = docBuilder.newDocument();
            Element loginElement = loginInput.createElement("Login");
            loginElement.setAttribute("LoginID", setting.getSterlingUserId());
            loginElement.setAttribute("Password", setting.getSterlingPassword());
            loginInput.appendChild(loginElement);

            // Using api.invoke to call login api
            Document loginDoc = api.invoke(env, "login", loginInput);
            
            env.setTokenID(loginDoc.getDocumentElement().getAttribute("UserToken"));
            sessionId = loginDoc.getDocumentElement().getAttribute("SessionId");
            if (logger.isDebugEnabled()) {
                logger.debug("Sending XML Input to Sterling...");
                logger.debug(printXmlDocument(inputDoc));
            }
            outputDocument = api.invoke(env, serviceName, inputDoc);
            if (logger.isDebugEnabled()) {
                logger.debug("Received XML Output from Sterling:");
                if(outputDocument!=null)
                	logger.debug(printXmlDocument(outputDocument));
            }
        } catch (Exception e) {
            logger.error("Unable to complete transaction to Sterling: " + e.getMessage());
            throw e;
        } finally {
            if (env != null) {
                Document logoutDoc = docBuilder.newDocument();
                Element logoutElement = logoutDoc.createElement("registerLogout");
                logoutElement.setAttribute("UserId", env.getUserId());
                logoutElement.setAttribute("SessionId", sessionId);
                logoutDoc.appendChild(logoutElement);
    
                // Using api.invoke to call registerLogout api
                api.invoke(env, "registerLogout", logoutDoc);
                api.releaseEnvironment(env);
            }
        }

        return outputDocument;
    }
    
    public DocumentBuilder getDocumentBuilder () {
        return this.docBuilder;
    }
    
    public String printXmlDocument (Document doc) {
        OutputFormat format = new OutputFormat(doc, "UTF-8", true);
        StringWriter stringWriter = new StringWriter();
        XMLSerializer serializer = new XMLSerializer(stringWriter, format);
        try {
            serializer.serialize(doc);
        } catch (Exception e) {
            logger.debug("Unable to print document to a string: " + e.getMessage());
        }
        return stringWriter.toString();
    }
}
