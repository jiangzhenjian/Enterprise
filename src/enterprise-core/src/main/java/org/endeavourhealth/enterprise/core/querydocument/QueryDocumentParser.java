package org.endeavourhealth.enterprise.core.querydocument;

import org.endeavourhealth.enterprise.core.querydocument.models.QueryDocument;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.bind.*;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

/**
 * Created by Drew on 11/03/2016.
 */
public abstract class QueryDocumentParser {


    public static QueryDocument readFromXml(String xml) throws ParserConfigurationException, JAXBException, IOException, SAXException {

        //parse XML string into DOM
        InputStream is = new ByteArrayInputStream(xml.getBytes());
        DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document document = docBuilder.parse(is);
        org.w3c.dom.Element varElement = document.getDocumentElement();

        //parse DOM into POJOs
        JAXBContext context = JAXBContext.newInstance(QueryDocument.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        JAXBElement<QueryDocument> loader = unmarshaller.unmarshal(varElement, QueryDocument.class);
        return loader.getValue();
    }

    public static String writeToXml(QueryDocument q) {

        StringWriter sw = new StringWriter();

        try {
            JAXBContext context = JAXBContext.newInstance(QueryDocument.class);
/*
            Marshaller marshaller = context.createMarshaller();
            marshaller.marshal(q, sw);
*/

            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE); //just makes output easier to read
            marshaller.marshal(new JAXBElement<QueryDocument>(new QName("uri","local"), QueryDocument.class, q), sw);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }

        //validate the XML against the schema
/*
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = factory.newSchema(new StreamSource(xsd));
        javax.xml.validation.Validator validator = schema.newValidator();
        validator.validate(new StreamSource(xml));
*/


        String ret = sw.toString();
/*
        QueryDocument q2 = null;
        try {
            q2 = readFromXml(ret);
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (JAXBException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }
*/


        return ret;
    }

}
