package com.ketra.docexport;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.documentum.fc.client.DfQuery;
import com.documentum.fc.client.IDfCollection;
import com.documentum.fc.client.IDfDocument;
import com.documentum.fc.client.IDfPersistentObject;
import com.documentum.fc.client.IDfQuery;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.common.DfException;
import com.documentum.fc.common.DfLogger;
import com.documentum.fc.common.IDfAttr;
import com.documentum.fc.common.IDfValue;

public class DocumentExport extends Export {

	private static final String NAME = "name";
	private static final String DOS_EXTENSION = "dos_extension";
	private static final String R_OBJECT_ID = "r_object_id";
	private static final String XML_FILENAME = "MET-%s.xml";
	private static final String XML_FILENAME_AUDITORIA = "AUDIT-%s.xml";
	private static final String XML_TAG_DOCUMENTO = "documento";
	private static final String TIPO_ATTR = "tipo";
	private static final String DATE_PATTERN = "dd/MM/yyyy HH:mm:ss";
	private static final DateFormat dtFormat = new SimpleDateFormat(DATE_PATTERN);
	private static final String FOLDER = "/Temp/Teste Doctrans";
	private static final String DQL_QUERY_DOCUMENTS = String
			.format("select * from dm_document where FOLDER('%s', DESCEND)", FOLDER);
	private static final String DQL_QUERY_AUDITTRAIL = "select * from dm_audittrail where audited_obj_id = '%s'";
	private static final String QUERY_DOS_EXTENSION = String.format("select %s, %s from dm_format where %s <> ''", NAME,
			DOS_EXTENSION, DOS_EXTENSION);

	private final Map<String, String> FORMATS_MAP = new HashMap<String, String>();
	private IDfSession session;

	public DocumentExport(IDfSession session) {
		super(session);	
	}

	@Override
	public void doExport() {
		IDfCollection collection = null;
		try {
			setMapFormats();
			IDfQuery query = new DfQuery(DQL_QUERY_DOCUMENTS);
			collection = query.execute(session, IDfQuery.READ_QUERY);
			while (collection.next()) {
				IDfDocument document = (IDfDocument) session.getObject(collection.getId(R_OBJECT_ID));
				String documentId = document.getObjectId().getId();
				DfLogger.info(this,
						String.format("Exportando metadados do documento %s-%s.", documentId, document.getObjectName()),null, null);
				Document xmlDocument = getXmlDocument();
				Element rootElement = xmlDocument.createElement(XML_TAG_DOCUMENTO);
				xmlDocument.appendChild(rootElement);
				addAttrIntoXml(xmlDocument, rootElement, document);
				TransformerFactory transformerFactory = TransformerFactory.newInstance();
				Transformer transformer = transformerFactory.newTransformer();
				DOMSource dom = new DOMSource(xmlDocument);
				Result outputTarget = new StreamResult(String.format(XML_FILENAME, documentId));
				transformer.transform(dom, outputTarget);
				exportContent(document);
				exportAudittrail(documentId);
			}

		} catch (Exception e) {
			DfLogger.error(this, String.format("Erro %s ao exportar documentos.", e.getMessage()), null, e);
		} finally {
			fecharCollection(collection);
		}
	}

	private void setMapFormats() throws DfException {
		IDfQuery queryFormats = new DfQuery(QUERY_DOS_EXTENSION);
		IDfCollection collectionFormats = null;
		try {
			collectionFormats = queryFormats.execute(session, DfQuery.DF_READ_QUERY);
			while (collectionFormats.next()) {
				FORMATS_MAP.put(collectionFormats.getString(NAME), collectionFormats.getString(DOS_EXTENSION));
			}
		} finally {
			fecharCollection(collectionFormats);
		}
	}

	private Document getXmlDocument() throws ParserConfigurationException {
		DocumentBuilderFactory xmlBuilderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = xmlBuilderFactory.newDocumentBuilder();
		Document xmlDocument = builder.newDocument();
		return xmlDocument;
	}

	private void exportAudittrail(String documentId) {
		IDfCollection collection = null;
		DfQuery queryAudittrail = new DfQuery(String.format(DQL_QUERY_AUDITTRAIL, documentId));
		DfLogger.info(this, String.format("Exportando auditoria do documento %s.", documentId), null, null);
		try {
			Document xmlDocument = getXmlDocument();
			Element rootElement = xmlDocument.createElement("auditoria");
			xmlDocument.appendChild(rootElement);
			collection = queryAudittrail.execute(session, DfQuery.DF_READ_QUERY);
			while (collection.next()) {
				IDfPersistentObject object = session.getObject(collection.getId(R_OBJECT_ID));
				addAttrIntoXml(xmlDocument, rootElement, object);
			}
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource dom = new DOMSource(xmlDocument);
			Result outputTarget = new StreamResult(String.format(XML_FILENAME_AUDITORIA, documentId));
			transformer.transform(dom, outputTarget);
		} catch (Exception e) {
			DfLogger.error(this, String.format("Erro %s ao exportar auditoria.", e.getMessage()), null, e);
		} finally {
			fecharCollection(collection);
		}
	}

	private void exportContent(IDfDocument document) throws DfException {
		IDfCollection renditions = null;
		try {
			renditions = document.getRenditions("full_format");
			while (renditions.next()) {
				String documentId = document.getObjectId().getId();
				String fullFormat = renditions.getString("full_format");
				String dosExtension = FORMATS_MAP.get(fullFormat);
				DfLogger.info(this, String.format("Exportando %s no formato %s", documentId, fullFormat), null, null);
				document.getFileEx(String.format("%s-%s.%s", documentId, fullFormat, dosExtension), fullFormat, 0,
						false);
			}
		} finally {
			fecharCollection(renditions);
		}
	}

	private void addAttrIntoXml(Document xmlDocument, Element rootElement, IDfPersistentObject object)
			throws DfException {
		int qtdAttr = object.getAttrCount();
		for (int i = 1; i <= qtdAttr; i++) {
			IDfAttr attr = object.getAttr(i);
			String attrname = attr.getName();
			IDfValue value = object.getValue(attrname);
			int dataType = value.getDataType();
			String attrTagType = "null";
			switch (dataType) {
			case IDfValue.DF_BOOLEAN:
				attrTagType = Boolean.TYPE.getTypeName();
				for (int valueIndex = 0; valueIndex < object.getValueCount(attrname); valueIndex++) {
					String tagValue = Boolean.toString(object.getRepeatingBoolean(attrname, valueIndex));
					addTagValueIntoXml(xmlDocument, rootElement, attrname, attrTagType, tagValue);
				}
				break;
			case IDfValue.DF_STRING:
				attrTagType = "string";
				for (int attridx = 0; attridx < object.getValueCount(attrname); attridx++) {
					String tagValue = object.getRepeatingString(attrname, attridx);
					addTagValueIntoXml(xmlDocument, rootElement, attrname, attrTagType, tagValue);
				}
				break;
			case IDfValue.DF_ID:
				attrTagType = "id";
				for (int attridx = 0; attridx < object.getValueCount(attrname); attridx++) {
					String tagValue = object.getRepeatingId(attrname, attridx).getId();
					addTagValueIntoXml(xmlDocument, rootElement, attrname, attrTagType, tagValue);
				}
				break;
			case IDfValue.DF_INTEGER:
				attrTagType = Integer.TYPE.getTypeName();
				for (int attridx = 0; attridx < object.getValueCount(attrname); attridx++) {
					String tagValue = Integer.toString(object.getRepeatingInt(attrname, attridx));
					addTagValueIntoXml(xmlDocument, rootElement, attrname, attrTagType, tagValue);
				}
				break;
			case IDfValue.DF_TIME:
				attrTagType = "datetime";
				String data = "NULL";
				for (int attridx = 0; attridx < object.getValueCount(attrname); attridx++) {
					Date date = object.getRepeatingTime(attrname, attridx).getDate();
					if (date != null)
						data = dtFormat.format(date);
					addTagValueIntoXml(xmlDocument, rootElement, attrname, attrTagType, data);
				}
				break;

			case IDfValue.DF_DOUBLE:
				attrTagType = Double.TYPE.getTypeName();
				for (int attridx = 0; attridx < object.getValueCount(attrname); attridx++) {
					String tagValue = Double.toString(object.getRepeatingDouble(attrname, attridx));
					addTagValueIntoXml(xmlDocument, rootElement, attrname, attrTagType, tagValue);
				}
				break;
			}
		}
	}

	private void addTagValueIntoXml(Document xmlDocument, Element rootElement, String attrname, String attrTagType, String tagValue) {
		Element elementAttDoc = xmlDocument.createElement(attrname);
		rootElement.appendChild(elementAttDoc);
		elementAttDoc.setTextContent(tagValue);
		elementAttDoc.setAttribute(TIPO_ATTR, attrTagType);
		rootElement.appendChild(elementAttDoc);

	}
}