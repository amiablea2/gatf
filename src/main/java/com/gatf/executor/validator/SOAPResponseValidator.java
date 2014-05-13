package com.gatf.executor.validator;

/*
Copyright 2013-2014, Sumeet Chhetri

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

import java.io.ByteArrayInputStream;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Assert;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.executor.report.TestCaseReport.TestStatus;
import com.ning.http.client.Response;

/**
 * @author Sumeet Chhetri
 * The validator that handles soap level node validations after test case execution
 */
public class SOAPResponseValidator implements ResponseValidator {

	public void validate(Response response, TestCase testCase, TestCaseReport testCaseReport, AcceptanceTestContext context) 
	{
		try
		{
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document xmlDocument = db.parse(new ByteArrayInputStream(response.getResponseBody().getBytes()));

			if(testCase.getAexpectedNodes()!=null && !testCase.getAexpectedNodes().isEmpty())
			{
				for (String node : testCase.getAexpectedNodes()) {
					String[] nodeCase = node.split(",");
					Node envelope = getNodeByNameCaseInsensitive(xmlDocument.getFirstChild(), "envelope");
					Node body = getNodeByNameCaseInsensitive(envelope, "body");
					Node requestBody = getNextElement(body);
					Node returnBody = getNextElement(requestBody);
					String expression = createXPathExpression(nodeCase[0], envelope, body, requestBody, returnBody);
					if(expression.indexOf("/[")!=-1)
						expression = expression.replaceAll("/[", "[");
					XPath xPath =  XPathFactory.newInstance().newXPath();
					NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
					Assert.assertTrue("Expected Node " + nodeCase[0] + " is null", 
							xmlNodeList!=null && xmlNodeList.getLength()>0);
					
					String xmlValue = XMLResponseValidator.getNodeValue(xmlNodeList.item(0));
					
					Assert.assertNotNull("Expected Node " + nodeCase[0] + " is null", xmlValue);
					if(nodeCase.length==2) {
						XMLResponseValidator.doNodeLevelValidation(xmlValue, nodeCase[1], context);
					}
				}
			}
			
			context.getWorkflowContextHandler().extractSoapWorkflowVariables(testCase, xmlDocument);
			
			if(context.getGatfExecutorConfig().isSoapAuthEnabled() && context.getGatfExecutorConfig().isSoapAuthTestCase(testCase)) {
				Node envelope = getNodeByNameCaseInsensitive(xmlDocument.getFirstChild(), "envelope");
				Node body = getNodeByNameCaseInsensitive(envelope, "body");
				Node requestBody = getNextElement(body);
				Node returnBody = getNextElement(requestBody);
				String expression = createXPathExpression(context.getGatfExecutorConfig().getSoapAuthExtractAuthParams()[0], envelope, body, requestBody, returnBody);
				if(expression.indexOf("/[")!=-1)
					expression = expression.replaceAll("/[", "[");
				XPath xPath =  XPathFactory.newInstance().newXPath();
				NodeList xmlNodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
				Assert.assertTrue("Authentication token is null", 
						xmlNodeList!=null && xmlNodeList.getLength()>0);
				
				String xmlValue = XMLResponseValidator.getNodeValue(xmlNodeList.item(0));
				context.setSessionIdentifier(xmlValue, testCase);
				
				String identifier = expression;
				if(identifier.indexOf("/")!=-1)
					identifier = identifier.substring(identifier.lastIndexOf("/")+1);
				context.getWorkflowContextHandler().getSuiteWorkflowContext(testCase).put(identifier, 
						xmlValue);
				
				Assert.assertNotNull("Authentication token is null", 
						context.getSessionIdentifier(testCase));
			}
			testCaseReport.setStatus(TestStatus.Success.status);
		} catch (Throwable e) {
			testCaseReport.setStatus(TestStatus.Failed.status);
			testCaseReport.setError(e.getMessage());
			testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
			if(e.getMessage()==null && testCaseReport.getErrorText()!=null && testCaseReport.getErrorText().indexOf("\n")!=-1) {
				testCaseReport.setError(testCaseReport.getErrorText().substring(0, testCaseReport.getErrorText().indexOf("\n")));
			}
			e.printStackTrace();
		}
	}
	
	public static String getLocalNodeName(String nodeName) {
		if(nodeName!=null && nodeName.indexOf(":")!=-1) {
			return nodeName.substring(nodeName.indexOf(":")+1);
		}
		return nodeName;
	}
	
	public static Node getNodeByNameCaseInsensitive(Node node, String nodeName) {
		if(node.getNodeName().equalsIgnoreCase(nodeName) ||
				getLocalNodeName(node.getNodeName()).equalsIgnoreCase(nodeName))
			return node;
		for (int i=0;i<node.getChildNodes().getLength();i++) {
			if(node.getChildNodes().item(i).getNodeType()==Node.ELEMENT_NODE) {
				if(node.getChildNodes().item(i).getNodeName().equalsIgnoreCase(nodeName) ||
						getLocalNodeName(node.getChildNodes().item(i).getNodeName()).equalsIgnoreCase(nodeName)) {
					return node.getChildNodes().item(i);
				}
			}
		}
		return null;
	}
	
	public static Node getNextElement(Node node) {
		for (int i=0;i<node.getChildNodes().getLength();i++) {
			if(node.getChildNodes().item(i).getNodeType()==Node.ELEMENT_NODE) {
				return node.getChildNodes().item(i);
			}
		}
		return null;
	}
	
	public static String createXPathExpression(String suffix, Node... nodes) {
		StringBuilder build = new StringBuilder();
		for (Node node : nodes) {
			build.append("/");
			build.append(getLocalNodeName(node.getNodeName()));
		}
		build.append("/");
		build.append(suffix.replaceAll("\\.", "\\/"));
		return build.toString();
	}
	
	public static void processSOAPRequest(Document soapMessage, TestCase testCase) throws Exception
	{
		for (Map.Entry<String, String> entry : testCase.getSoapParameterValues().entrySet()) {
			Node envelope = getNodeByNameCaseInsensitive(soapMessage.getFirstChild(), "envelope");
			Node body = getNodeByNameCaseInsensitive(envelope, "body");
			Node requestBody = getNextElement(body);
			String expression = createXPathExpression(entry.getKey(), envelope, body, requestBody);
			XPath xPath = XPathFactory.newInstance().newXPath();
			NodeList nodelist = (NodeList) xPath.compile(expression).evaluate(soapMessage, XPathConstants.NODESET);
			Assert.assertNotNull("Cannot find soap parameter " + entry.getKey(), 
					nodelist!=null && nodelist.getLength()>0);
			nodelist.item(0).getFirstChild().setNodeValue(entry.getValue());
		}
	}
}
