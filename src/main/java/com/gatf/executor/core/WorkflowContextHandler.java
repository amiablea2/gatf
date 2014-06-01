package com.gatf.executor.core;

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

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.gatf.executor.validator.XMLResponseValidator;
import com.ning.http.client.cookie.Cookie;

/**
 * @author Sumeet Chhetri
 * Holds global/suite level workflow variable/values
 */
public class WorkflowContextHandler {

	private final VelocityEngine engine = new VelocityEngine();
	
	public enum ResponseType {
		JSON,
		XML,
		SOAP,
		PLAIN,
		NONE
	}
	
	public VelocityEngine getEngine() {
		return engine;
	}

	public void init() {
		try {
			engine.init();
		} catch (Exception e) {
		}
	}
	
	private final Map<String, String> globalworkflowContext = new ConcurrentHashMap<String, String>();
	
	private final Map<Integer, Map<String, String>> suiteWorkflowContext = new ConcurrentHashMap<Integer, Map<String, String>>();
	
	private final Map<Integer, Map<String, List<Map<String, String>>>> suiteWorkflowScenarioContext = 
			new ConcurrentHashMap<Integer, Map<String, List<Map<String, String>>>>();
	
	private final Map<Integer, Map<String, String>> cookies = new ConcurrentHashMap<Integer, Map<String, String>>();
	
	void initializeSuiteContext(int numberOfRuns) {
		
		suiteWorkflowContext.clear();
		suiteWorkflowScenarioContext.clear();
		cookies.clear();
		
		int start = (numberOfRuns>1?1:0);
		int end = (numberOfRuns>1?numberOfRuns+1:numberOfRuns);
		for (int i = start; i < end; i++) {
			suiteWorkflowContext.put(i, new ConcurrentHashMap<String, String>());
			suiteWorkflowScenarioContext.put(i, new ConcurrentHashMap<String, List<Map<String, String>>>());
			cookies.put(i, new ConcurrentHashMap<String, String>());
		}
	}
	
	void initializeSuiteContextWithnum(int index) {
		suiteWorkflowContext.clear();
		suiteWorkflowScenarioContext.clear();
		cookies.clear();
		suiteWorkflowContext.put(index, new ConcurrentHashMap<String, String>());
		suiteWorkflowScenarioContext.put(index, new ConcurrentHashMap<String, List<Map<String, String>>>());
		cookies.put(index, new ConcurrentHashMap<String, String>());
	}
	
	void addGlobalVariables(Map<String, String> variableMap) {
		if(variableMap!=null) {
			globalworkflowContext.putAll(variableMap);
		}
	}
	
	public Map<String, String> getSuiteWorkflowContext(TestCase testCase) {
		if(testCase.getSimulationNumber()==null) {
			return suiteWorkflowContext.get(0);
		} else {
			return suiteWorkflowContext.get(testCase.getSimulationNumber());
		}
	}
	
	public Map<String, List<Map<String, String>>> getSuiteWorkflowScnearioContext(TestCase testCase) {
		if(testCase.getSimulationNumber()==null) {
			return suiteWorkflowScenarioContext.get(0);
		} else {
			return suiteWorkflowScenarioContext.get(testCase.getSimulationNumber());
		}
	}
	
	public List<Map<String, String>> getSuiteWorkflowScenarioContextValues(TestCase testCase, String varName) {
		if(testCase.getSimulationNumber()==null) {
			return suiteWorkflowScenarioContext.get(0).get(varName);
		} else {
			return suiteWorkflowScenarioContext.get(testCase.getSimulationNumber()).get(varName);
		}
	}
	
	public void storeCookies(TestCase testCase, List<Cookie> cookieLst) {
		int simNumber = testCase.getSimulationNumber()==null?0:testCase.getSimulationNumber();
		if(cookieLst!=null && cookies.get(simNumber)!=null)
		{
			for (Cookie cookie : cookieLst) {
				cookies.get(simNumber).put(cookie.getName(), cookie.getValue());
			}
		}
	}
	
	private Map<String, String> getGlobalSuiteAndTestLevelParameters(TestCase testCase, Map<String, String> variableMap) {
		Map<String, String> nmap = new HashMap<String, String>(globalworkflowContext);
		if(testCase.getSimulationNumber()==null) {
			nmap.putAll(suiteWorkflowContext.get(0));
		} else {
			nmap.putAll(suiteWorkflowContext.get(testCase.getSimulationNumber()));
		}
		if(variableMap!=null && !variableMap.isEmpty()) {
			nmap.putAll(variableMap);
		}
		if(testCase.getCarriedOverVariables()!=null && !testCase.getCarriedOverVariables().isEmpty()) {
			nmap.putAll(testCase.getCarriedOverVariables());
		}
		return nmap;
	}
	
	public String evaluateTemplate(TestCase testCase, String template) {
		StringWriter writer = new StringWriter();
		try {
			Map<String, String> nmap = getGlobalSuiteAndTestLevelParameters(testCase, null);
			if(testCase!=null && !nmap.isEmpty()) {
				VelocityContext context = new VelocityContext(nmap);
				if(template!=null) {
					engine.evaluate(context, writer, "ERROR", template);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return writer.toString();
	}
	
	public void handleContextVariables(TestCase testCase, Map<String, String> variableMap) throws Exception {
		
		Map<String, String> nmap = getGlobalSuiteAndTestLevelParameters(testCase, variableMap);
		
		//initialize cookies
		if(testCase != null) {
			int simNumber = testCase.getSimulationNumber()==null?0:testCase.getSimulationNumber();
			Map<String, String> cookieMap = cookies.get(simNumber);
			if(cookieMap!=null) {
				for (Map.Entry<String, String> entry : cookieMap.entrySet()) {
					testCase.getHeaders().put("Cookie", entry.getKey() + "=" + entry.getValue());
				}
			}
		}
		
		if(testCase!=null && !nmap.isEmpty()) {
			
			VelocityContext context = new VelocityContext(nmap);
			if(testCase.getUrl()!=null) {
				StringWriter writer = new StringWriter();
				engine.evaluate(context, writer, "ERROR", testCase.getUrl());
				testCase.setAurl(writer.toString());
			}
			if(testCase.getContent()!=null) {
				StringWriter writer = new StringWriter();
				engine.evaluate(context, writer, "ERROR", testCase.getContent());
				testCase.setAcontent(writer.toString());
			}
			if(testCase.getExQueryPart()!=null) {
				StringWriter writer = new StringWriter();
				engine.evaluate(context, writer, "ERROR", testCase.getExQueryPart());
				testCase.setAexQueryPart(writer.toString());
			}
			if(testCase.getExpectedNodes()!=null && !testCase.getExpectedNodes().isEmpty()) {
				List<String> expectedNodes = new ArrayList<String>();
				for (String nodecase : testCase.getExpectedNodes()) {
					StringWriter writer = new StringWriter();
					engine.evaluate(context, writer, "ERROR", nodecase);
					expectedNodes.add(writer.toString());
				}
				testCase.setAexpectedNodes(expectedNodes);
			}
		} else if(testCase!=null) {
			testCase.setAurl(testCase.getUrl());
			testCase.setAcontent(testCase.getContent());
			testCase.setAexQueryPart(testCase.getExQueryPart());
			testCase.setAexpectedNodes(testCase.getExpectedNodes());
		}
	}
	
	public static List<Map<String, String>> getNodeCountMapList(String xmlValue, String nodeName)
	{
		int responseCount = -1;
		try {
			responseCount = Integer.valueOf(xmlValue);
		} catch (Exception e) {
			throw new AssertionError("Invalid responseMappedCount variable defined, " +
					"derived value should be number - "+nodeName);
		}
		
		List<Map<String, String>> xmlValues = new ArrayList<Map<String,String>>();
		for (int i = 0; i < responseCount; i++) {
			Map<String, String> row = new HashMap<String, String>();
			row.put("index", (i+1)+"");
			xmlValues.add(row);
		}
		
		return xmlValues;
	}
	
	public static List<Map<String, String>> getNodeValueMapList(String propNames, NodeList xmlNodeList)
	{
		List<Map<String, String>> nodeValues = new ArrayList<Map<String,String>>();
		if(propNames.endsWith("*")) 
		{
			for (int i = 0; i < xmlNodeList.getLength(); i++) {
				Map<String, String> row = new HashMap<String, String>();
				
				Node node = xmlNodeList.item(i);
				if(node.getAttributes()!=null && node.getAttributes().getLength()>0)
				{
					for (int j = 0; j < node.getAttributes().getLength(); j++) {
						Attr attr = (Attr) node.getAttributes().item(j);
						row.put(attr.getName(), attr.getValue());
					}
				}
				
				if(node.getChildNodes()!=null && node.getChildNodes().getLength()>0)
				{
					for (int j = 0; j < node.getChildNodes().getLength(); j++) {
						String xmlValue = XMLResponseValidator.getXMLNodeValue(node.getChildNodes().item(j));
						if(xmlValue!=null)
							row.put(node.getChildNodes().item(j).getNodeName(), xmlValue);
					}
				}
				
				String xmlValue = XMLResponseValidator.getXMLNodeValue(node);
				if(xmlValue!=null)
					row.put("this", xmlValue);
				
				if(row.size()>0)
					nodeValues.add(row);
			}
		} 
		else
		{
			String[] props = propNames.split(",");
			
			for (int i = 0; i < xmlNodeList.getLength(); i++) {
				Map<String, String> row = new HashMap<String, String>();
				
				Node node = xmlNodeList.item(i);
				
				boolean found = false;
				
				if(node.getAttributes()!=null && node.getAttributes().getLength()>0)
				{
					for (int j = 0; j < node.getAttributes().getLength(); j++) {
						Attr attr = (Attr) node.getAttributes().item(j);
						for (String propName : props) {
							if(attr.getName().equals(propName)) {
								found = true;
								row.put(propName, attr.getValue());
								break;
							}
						}
					}
				}
				
				if(!found && node.getChildNodes()!=null && node.getChildNodes().getLength()>0)
				{
					for (int j = 0; j < node.getChildNodes().getLength(); j++) {
						for (String propName : props) {
							if(node.getChildNodes().item(j).getNodeName().equals(propName)) {
								found = true;
								String xmlValue = XMLResponseValidator.getXMLNodeValue(node.getChildNodes().item(j));
								row.put(propName, xmlValue);
								break;
							}
						}
					}
				}
				if(row.size()>0)
					nodeValues.add(row);
			}
		}
		return nodeValues;
	}
}
