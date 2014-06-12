package com.gatf.ui;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.ws.rs.core.MediaType;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.jackson.map.ObjectMapper;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.GatfTestCaseExecutorMojo;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.dataprovider.DatabaseTestDataSource;
import com.gatf.executor.dataprovider.FileTestDataProvider;
import com.gatf.executor.dataprovider.GatfTestDataProvider;
import com.gatf.executor.dataprovider.GatfTestDataSource;
import com.gatf.executor.dataprovider.GatfTestDataSourceHook;
import com.gatf.executor.dataprovider.InlineValueTestDataProvider;
import com.gatf.executor.dataprovider.MongoDBTestDataSource;
import com.gatf.executor.dataprovider.RandomValueTestDataProvider;
import com.gatf.executor.finder.XMLTestCaseFinder;
import com.gatf.executor.report.ReportHandler;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.core.util.QuickWriter;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import com.thoughtworks.xstream.io.xml.XppDriver;


/**
 * @author sumeetc
 *
 */
@Mojo(
		name = "gatf-config", 
		aggregator = false, 
		executionStrategy = "always", 
		inheritByDefault = true, 
		instantiationStrategy = InstantiationStrategy.PER_LOOKUP, 
		requiresDependencyResolution = ResolutionScope.TEST, 
		requiresDirectInvocation = true, 
		requiresOnline = false, 
		requiresProject = true, 
		threadSafe = true)
public class GatfConfigToolMojo extends AbstractMojo {

	@Component
    private MavenProject project;
	
	@Parameter(alias = "rootDir", defaultValue = "${project.build.testOutputDirectory}")
	private String rootDir;
	
	@Parameter(alias = "ipAddress", defaultValue = "localhost")
	private String ipAddress;
	
	@Parameter(alias = "port", defaultValue = "9080")
	private int port;
	
	public GatfConfigToolMojo() {
	}

	@SuppressWarnings("rawtypes")
	public void execute() throws MojoExecutionException, MojoFailureException {
		HttpServer server = new HttpServer();

		final String mainDir = rootDir + "/" + "gatf-config-tool";
		InputStream resourcesIS = GatfConfigToolMojo.class.getResourceAsStream("/gatf-config-tool.zip");
        if (resourcesIS != null)
        {
        	ReportHandler.unzipZipFile(resourcesIS, rootDir);
        }
		
        server.addListener(new NetworkListener("ConfigServer", ipAddress, port));
		
        server.getServerConfiguration().addHttpHandler(
			    new HttpHandler() {
			        public void service(Request request, Response response) throws Exception {
			        	new CacheLessStaticHttpHandler(mainDir).service(request, response);
			        }
			    }, "/");
		
		server.getServerConfiguration().addHttpHandler(
			    new HttpHandler() {
			        public void service(Request request, Response response) throws Exception {
			        	try {
		        			if(new File(rootDir, "gatf-config.xml").exists()) {
			        			GatfExecutorConfig gatfConfig = GatfTestCaseExecutorMojo.getConfig(new FileInputStream(
									new File(rootDir, "gatf-config.xml")));
			        			if(gatfConfig == null) {
			        				throw new RuntimeException("Invalid configuration present");
			        			}
			        			String basepath = gatfConfig.getOutFilesBasePath()==null?rootDir:gatfConfig.getOutFilesBasePath();
			        			String dirPath = basepath + "/" + gatfConfig.getOutFilesDir();
		        				if(!new File(dirPath).exists()) {
		        					new File(dirPath).mkdir();
		        				}
		        				if(request.getMethod().equals(Method.GET) ) {
					        		new CacheLessStaticHttpHandler(dirPath).service(request, response);
					        	}
		        			}
			        	} catch (Exception e) {
							String configJson = e.getMessage();
							response.setContentLength(configJson.length());
				            response.getWriter().write(configJson);
							response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
						}
			        }
			    }, "/reports");
		
		server.getServerConfiguration().addHttpHandler(
		    new HttpHandler() {
		        public void service(Request request, Response response) throws Exception {
		        	if(request.getMethod().equals(Method.GET) ) {
		        		try {
		        			if(!new File(rootDir, "gatf-config.xml").exists()) {
		        				new File(rootDir, "gatf-config.xml").createNewFile();
		        				String configJson = "No configuration present in config";
		        				response.setContentLength(configJson.length());
					            response.getWriter().write(configJson);
		        				response.setStatus(HttpStatus.OK_200);
		        			} else {
			        			GatfExecutorConfig gatfConfig = GatfTestCaseExecutorMojo.getConfig(new FileInputStream(
									new File(rootDir, "gatf-config.xml")));
			        			if(gatfConfig == null) {
			        				throw new RuntimeException("Invalid configuration present in gatf-config.xml");
			        			}
			        			String configJson = new ObjectMapper().writeValueAsString(gatfConfig);
			        			response.setContentType(MediaType.APPLICATION_JSON);
					            response.setContentLength(configJson.length());
					            response.getWriter().write(configJson);
					            response.setStatus(HttpStatus.OK_200);
		        			}
						} catch (Exception e) {
							String configJson = e.getMessage();
							response.setContentLength(configJson.length());
				            response.getWriter().write(configJson);
							response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
						}
		        	} else if(request.getMethod().equals(Method.POST) ) {
		        		try {
		        			GatfExecutorConfig gatfConfig = new ObjectMapper().readValue(request.getInputStream(), 
		        					GatfExecutorConfig.class);
		        			if(gatfConfig == null) {
		        				throw new RuntimeException("Invalid configuration provided");
		        			}
		        			if(gatfConfig.getTestCasesBasePath()==null) {
		        				gatfConfig.setTestCasesBasePath(rootDir);
		        			}
		        			if(gatfConfig.getOutFilesBasePath()==null) {
		        				gatfConfig.setOutFilesBasePath(rootDir);
		        			}
		        			if(gatfConfig.getTestCaseDir()==null) {
		        				gatfConfig.setTestCaseDir("data");
		        			}
		        			if(gatfConfig.getOutFilesDir()==null) {
		        				gatfConfig.setOutFilesDir("out");
		        			}
		        			if(gatfConfig.isEnabled()==null) {
		        				gatfConfig.setEnabled(true);
		        			}
		        			if(gatfConfig.getConcurrentUserSimulationNum()==null) {
		        				gatfConfig.setConcurrentUserSimulationNum(0);
		        			}
		        			if(gatfConfig.getHttpConnectionTimeout()==null) {
		        				gatfConfig.setHttpConnectionTimeout(10000);
		        			}
		        			if(gatfConfig.getHttpRequestTimeout()==null) {
		        				gatfConfig.setHttpRequestTimeout(10000);
		        			}
		        			if(gatfConfig.getLoadTestingReportSamples()==null) {
		        				gatfConfig.setLoadTestingReportSamples(3);
		        			}
		        			if(gatfConfig.getConcurrentUserRampUpTime()==null) {
		        				gatfConfig.setConcurrentUserRampUpTime(0L);
		        			}
		        			if(gatfConfig.getNumConcurrentExecutions()==null) {
		        				gatfConfig.setNumConcurrentExecutions(1);
		        			}
		        			
		        			response.setStatus(HttpStatus.OK_200);
		        			FileUtils.writeStringToFile(new File(rootDir, "gatf-config.xml"), 
		        					GatfTestCaseExecutorMojo.getConfigStr(gatfConfig));
						} catch (Exception e) {
							String configJson = e.getMessage();
							response.setContentLength(configJson.length());
				            response.getWriter().write(configJson);
							response.setStatus(HttpStatus.BAD_REQUEST_400);
						}
		        	}
		        }
		    },
		    "/configure");
		
		server.getServerConfiguration().addHttpHandler(
			    new HttpHandler() {
			        public void service(Request request, Response response) throws Exception {
			        	if(request.getMethod().equals(Method.GET) ) {
			        		try {
			        			if(new File(rootDir, "gatf-config.xml").exists()) {
				        			GatfExecutorConfig gatfConfig = GatfTestCaseExecutorMojo.getConfig(new FileInputStream(
										new File(rootDir, "gatf-config.xml")));
				        			if(gatfConfig == null) {
				        				throw new RuntimeException("Invalid configuration present in gatf-config.xml");
				        			}
				        			
				        			Map<String, List<String>> miscMap = new HashMap<String, List<String>>();
				        				
			        				if(gatfConfig.getGatfTestDataConfig().getDataSourceList()!=null) {
			        					List<String> dataLst = new ArrayList<String>();
			        					dataLst.add("");
			        					for (GatfTestDataSource ds : gatfConfig.getGatfTestDataConfig().getDataSourceList()) {
			        						dataLst.add(ds.getDataSourceName());
										}
			        					miscMap.put("datasources", dataLst);
				        			}
			        				if(gatfConfig.getGatfTestDataConfig().getProviderTestDataList()!=null) {
			        					List<String> dataLst = new ArrayList<String>();
			        					dataLst.add("");
			        					for (GatfTestDataProvider pv : gatfConfig.getGatfTestDataConfig().getProviderTestDataList()) {
			        						dataLst.add(pv.getProviderName());
										}
			        					miscMap.put("providers", dataLst);
			        				}
			        				if(gatfConfig.getGatfTestDataConfig().getDataSourceHooks()!=null) {
			        					List<String> dataLst = new ArrayList<String>();
			        					dataLst.add("");
			        					for (GatfTestDataSourceHook hk : gatfConfig.getGatfTestDataConfig().getDataSourceHooks()) {
			        						dataLst.add(hk.getHookName());
										}
			        					miscMap.put("hooks", dataLst);
			        				}
			        				List<String> dataLst = new ArrayList<String>();
			        				dataLst.add("");
			        				dataLst.add(MongoDBTestDataSource.class.getName());
			        				dataLst.add(DatabaseTestDataSource.class.getName());
			        				miscMap.put("datasourcecls", dataLst);
				        			
			        				dataLst = new ArrayList<String>();
			        				dataLst.add("");
			        				dataLst.add(FileTestDataProvider.class.getName());
			        				dataLst.add(InlineValueTestDataProvider.class.getName());
			        				dataLst.add(RandomValueTestDataProvider.class.getName());
			        				miscMap.put("providercls", dataLst);
				        			
				        			String configJson = new ObjectMapper().writeValueAsString(miscMap);
				        			response.setContentType(MediaType.APPLICATION_JSON);
						            response.setContentLength(configJson.length());
						            response.getWriter().write(configJson);
						            response.setStatus(HttpStatus.OK_200);
			        			} else {
			        				String configJson = "No configuration present in config";
			        				response.setContentLength(configJson.length());
						            response.getWriter().write(configJson);
			        				response.setStatus(HttpStatus.OK_200);
			        			}
							} catch (Exception e) {
								String configJson = e.getMessage();
								response.setContentLength(configJson.length());
					            response.getWriter().write(configJson);
								response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
							}
			        	}
			        }
			    },
			    "/misc");
		
		server.getServerConfiguration().addHttpHandler(
			    new HttpHandler() {
			        public void service(Request request, Response response) throws Exception {
			        	if(request.getMethod().equals(Method.POST)) {
			        		try {
			        			String testcaseFileName = request.getQueryString();
			        			if(testcaseFileName.indexOf("testcaseFileName=")!=-1) {
			        				testcaseFileName = testcaseFileName.substring(testcaseFileName.indexOf("=")+1);
			        				GatfExecutorConfig gatfConfig = GatfTestCaseExecutorMojo.getConfig(new FileInputStream(
										new File(rootDir, "gatf-config.xml")));
				        			if(gatfConfig == null) {
				        				throw new RuntimeException("Invalid configuration present");
				        			}
				        			if(!testcaseFileName.endsWith(".xml"))
				        			{
				        				throw new RuntimeException("Testcase File should be an xml file, extension should be (.xml)");
				        			}
				        			String basepath = gatfConfig.getTestCasesBasePath()==null?rootDir:gatfConfig.getTestCasesBasePath();
				        			String dirPath = basepath + "/" + gatfConfig.getTestCaseDir();
			        				if(!new File(dirPath).exists()) {
			        					new File(dirPath).mkdir();
			        				}
				        			String filePath = basepath + "/" + gatfConfig.getTestCaseDir() + "/" + testcaseFileName;
				        			if(new File(filePath).exists()) {
				        				throw new RuntimeException("Testcase file already exists");
				        			}
				        			new File(filePath).createNewFile();
				        			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
				        			bos.write("<TestCases></TestCases>".getBytes());
				        			bos.close();
				        			response.setStatus(HttpStatus.OK_200);
			        			} else {
			        				throw new RuntimeException("No testcaseFileName query parameter specified");
			        			}
							} catch (Exception e) {
								String configJson = e.getMessage();
								response.setContentLength(configJson.length());
					            response.getWriter().write(configJson);
								response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
							}
			        	} else if(request.getMethod().equals(Method.DELETE)) {
			        		try {
			        			String testcaseFileName = request.getQueryString();
			        			if(testcaseFileName.indexOf("testcaseFileName=")!=-1) {
			        				testcaseFileName = testcaseFileName.substring(testcaseFileName.indexOf("=")+1);
			        				GatfExecutorConfig gatfConfig = GatfTestCaseExecutorMojo.getConfig(new FileInputStream(
										new File(rootDir, "gatf-config.xml")));
				        			if(gatfConfig == null) {
				        				throw new RuntimeException("Invalid configuration present");
				        			}
				        			if(!testcaseFileName.endsWith(".xml"))
				        			{
				        				throw new RuntimeException("Testcase File should be an xml file, extension should be (.xml)");
				        			}
				        			String basepath = gatfConfig.getTestCasesBasePath()==null?rootDir:gatfConfig.getTestCasesBasePath();
				        			String dirPath = basepath + "/" + gatfConfig.getTestCaseDir();
			        				if(!new File(dirPath).exists()) {
			        					new File(dirPath).mkdir();
			        				}
				        			String filePath = basepath + "/" + gatfConfig.getTestCaseDir() + "/" + testcaseFileName;
				        			if(new File(filePath).exists()) {
				        				new File(filePath).delete();
				        			} else {
				        				throw new RuntimeException("Testcase file does not exist");
				        			}
				        			response.setStatus(HttpStatus.OK_200);
			        			} else {
			        				throw new RuntimeException("No testcaseFileName query parameter specified");
			        			}
							} catch (Exception e) {
								String configJson = e.getMessage();
								response.setContentLength(configJson.length());
					            response.getWriter().write(configJson);
								response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
							}
			        	} else if(request.getMethod().equals(Method.GET) ) {
			        		try {
			        			GatfExecutorConfig gatfConfig = GatfTestCaseExecutorMojo.getConfig(new FileInputStream(
									new File(rootDir, "gatf-config.xml")));
			        			if(gatfConfig == null) {
			        				throw new RuntimeException("");
			        			}
			        			String basepath = gatfConfig.getTestCasesBasePath()==null?rootDir:gatfConfig.getTestCasesBasePath();
			        			String dirPath = basepath + "/" + gatfConfig.getTestCaseDir();
		        				if(!new File(dirPath).exists()) {
		        					new File(dirPath).mkdir();
		        				}
			        			File[] xmlFiles = new File(dirPath).listFiles(new FilenameFilter() {
			        				public boolean accept(File folder, String name) {
			        					return name.toLowerCase().endsWith(".xml");
			        				}
			        			});
			        			List<String> fileNames = new ArrayList<String>();
			        			if(xmlFiles!=null) {
				        			for (File file : xmlFiles) {
				        				fileNames.add(file.getName());
									}
			        			}
			        			String json = new ObjectMapper().writeValueAsString(fileNames);
			        			response.setContentType(MediaType.APPLICATION_JSON);
					            response.setContentLength(json.length());
					            response.getWriter().write(json);
			        			response.setStatus(HttpStatus.OK_200);
							} catch (Exception e) {
								String configJson = e.getMessage();
								response.setContentLength(configJson.length());
					            response.getWriter().write(configJson);
								response.setStatus(HttpStatus.BAD_REQUEST_400);
							}
			        	}
			        }
			    },
			    "/testcasefiles");
		
		server.getServerConfiguration().addHttpHandler(
			    new HttpHandler() {
			        public void service(Request request, Response response) throws Exception {
			        	String testcaseFileName = request.getQueryString();
	        			if(testcaseFileName.indexOf("testcaseFileName=")==-1) {
	        				response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
	        				return;
	        			}
	        			testcaseFileName = testcaseFileName.substring(testcaseFileName.indexOf("=")+1);
	        			GatfExecutorConfig gatfConfig = null;
	        			String filePath = null;
	        			try {
	        				gatfConfig = GatfTestCaseExecutorMojo.getConfig(new FileInputStream(
								new File(rootDir, "gatf-config.xml")));
		        			if(gatfConfig == null) {
		        				throw new RuntimeException("Invalid configuration present");
		        			}
		        			String basepath = gatfConfig.getTestCasesBasePath()==null?rootDir:gatfConfig.getTestCasesBasePath();
		        			String dirPath = basepath + "/" + gatfConfig.getTestCaseDir();
	        				if(!new File(dirPath).exists()) {
	        					new File(dirPath).mkdir();
	        				}
		        			filePath = basepath + "/" + gatfConfig.getTestCaseDir() + "/"
		        					+ testcaseFileName;
		        			if(!new File(filePath).exists()) {
		        				throw new RuntimeException("Test case file does not exist");
		        			}
	        			} catch (Exception e) {
	        				String configJson = e.getMessage();
							response.setContentLength(configJson.length());
				            response.getWriter().write(configJson);
							response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
							return;
						}
	        			boolean isUpdate = request.getMethod().equals(Method.PUT);
	        			if(request.getMethod().equals(Method.DELETE)) {
			        		try {
			        			String testCaseName = request.getHeader("testcasename");
			        			List<TestCase> tcs = new XMLTestCaseFinder().resolveTestCases(new File(filePath));
			        			TestCase found = null;
			        			for (TestCase tc : tcs) {
									if(tc.getName().equals(testCaseName)) {
										found = tc;
										break;
									}
								}
			        			if(found==null) {
			        				throw new RuntimeException("Testcase does not exist");
			        			}
			        			tcs.remove(found);
			        			
			        			XStream xstream = new XStream(
			                			new XppDriver() {
			                				public HierarchicalStreamWriter createWriter(Writer out) {
			                					return new PrettyPrintWriter(out) {
			                						boolean cdata = false;
													public void startNode(String name, Class clazz){
			                							super.startNode(name, clazz);
			                							cdata = (name.equals("content") || name.equals("expectedResContent"));
			                						}
			                						protected void writeText(QuickWriter writer, String text) {
			                							if(cdata) {
			                								writer.write("<![CDATA[");
			                								writer.write(text);
			                								writer.write("]]>");
			                							} else {
			                								writer.write(text);
			                							}
			                						}
			                					};
			                				}
			                			}
			                		);
			                		xstream.processAnnotations(new Class[]{TestCase.class});
			                		xstream.alias("TestCases", List.class);
			                		xstream.toXML(tcs, new FileOutputStream(filePath));
				        			
				        			response.setStatus(HttpStatus.OK_200);
			        		} catch (Exception e) {
								String configJson = e.getMessage();
								response.setContentLength(configJson.length());
					            response.getWriter().write(configJson);
								response.setStatus(HttpStatus.BAD_REQUEST_400);
							}
	        			} else if(request.getMethod().equals(Method.POST) || isUpdate) {
			        		try {
			        			TestCase testCase = new ObjectMapper().readValue(request.getInputStream(), 
			        					TestCase.class);
			        			if(testCase.getName()==null) {
			        				throw new RuntimeException("Testcase does not specify name");
			        			}
			        			testCase.validate(AcceptanceTestContext.getHttpHeadersMap());
			        			List<TestCase> tcs = new XMLTestCaseFinder().resolveTestCases(new File(filePath));
			        			if(!isUpdate)
			        			{
				        			for (TestCase tc : tcs) {
										if(tc.getName().equals(testCase.getName())) {
											throw new RuntimeException("Testcase with same name already exists");
										}
									}
			        			}
			        			tcs.add(testCase);
			        			
			        			XStream xstream = new XStream(
		                			new XppDriver() {
		                				public HierarchicalStreamWriter createWriter(Writer out) {
		                					return new PrettyPrintWriter(out) {
		                						boolean cdata = false;
		                						public void startNode(String name, Class clazz){
		                							super.startNode(name, clazz);
		                							cdata = (name.equals("content") || name.equals("expectedResContent"));
		                						}
		                						protected void writeText(QuickWriter writer, String text) {
		                							if(cdata) {
		                								writer.write("<![CDATA[");
		                								writer.write(text);
		                								writer.write("]]>");
		                							} else {
		                								writer.write(text);
		                							}
		                						}
		                					};
		                				}
		                			}
		                		);
		                		xstream.processAnnotations(new Class[]{TestCase.class});
		                		xstream.alias("TestCases", List.class);
		                		xstream.toXML(tcs, new FileOutputStream(filePath));
			        			
			        			response.setStatus(HttpStatus.OK_200);
							} catch (Exception e) {
								String configJson = e.getMessage();
								response.setContentLength(configJson.length());
					            response.getWriter().write(configJson);
								response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
							} catch (Error e) {
								String configJson = e.getMessage();
								response.setContentLength(configJson.length());
					            response.getWriter().write(configJson);
								response.setStatus(HttpStatus.BAD_REQUEST_400);
							}
			        	} else if(request.getMethod().equals(Method.GET) ) {
			        		try {
			        			List<TestCase> tcs = new XMLTestCaseFinder().resolveTestCases(new File(filePath));
			        			String json = new ObjectMapper().writeValueAsString(tcs);
			        			response.setContentType(MediaType.APPLICATION_JSON);
					            response.setContentLength(json.length());
					            response.getWriter().write(json);
			        			response.setStatus(HttpStatus.OK_200);
							} catch (Exception e) {
								String configJson = e.getMessage();
								response.setContentLength(configJson.length());
					            response.getWriter().write(configJson);
								response.setStatus(HttpStatus.BAD_REQUEST_400);
							}
			        	}
			        }
			    },
			    "/testcases");
		
		server.getServerConfiguration().addHttpHandler(
			    new HttpHandler() {
			    	AtomicBoolean isStarted = new AtomicBoolean(false);
			    	AtomicBoolean isDone = new AtomicBoolean(false);
			    	Thread _executorThread = null;
			    	volatile String status = "";
			        public void service(Request request, Response response) throws Exception {
	        			GatfExecutorConfig gatfConfig = null;
	        			try {
	        				gatfConfig = GatfTestCaseExecutorMojo.getConfig(new FileInputStream(
								new File(rootDir, "gatf-config.xml")));
		        			if(gatfConfig == null) {
		        				throw new RuntimeException("Invalid configuration present");
		        			}
		        			String basepath = gatfConfig.getOutFilesBasePath()==null?rootDir:gatfConfig.getOutFilesBasePath();
		        			String dirPath = basepath + "/" + gatfConfig.getOutFilesDir();
	        				if(!new File(dirPath).exists()) {
	        					new File(dirPath).mkdir();
	        				}
	        				if(request.getMethod().equals(Method.GET) ) {
	        					if(isStarted.get()) {
	        						throw new RuntimeException("Execution already in progress..");
	        					} else if(!status.equals("")) {
	        						String temp = status;
	        						status = "";
	        						throw new RuntimeException("Execution failed with Error - " + temp);
	        					} else if(isDone.get()) {
	        						isDone.set(false);
	        						isStarted.set(false);
	        						String text = "Execution completed, check Reports Section";
				        			response.setContentType(MediaType.TEXT_PLAIN);
						            response.setContentLength(text.length());
						            response.getWriter().write(text);
				        			response.setStatus(HttpStatus.OK_200);
	        					} else if(!isStarted.get()) {
	        						throw new RuntimeException("Please Start the Execution....");
	        					} else {
	        						throw new RuntimeException("Unknown Error...");
	        					}
	        				}
	        				else if(request.getMethod().equals(Method.PUT) ) {
	        					if(!isStarted.get() && !isDone.get()) {
	        						isStarted.set(true);
	        						final GatfExecutorConfig config = gatfConfig;
	        						_executorThread = new Thread(new Runnable() {
										public void run() {
											GatfTestCaseExecutorMojo executorMojo = new GatfTestCaseExecutorMojo();
			        						executorMojo.setProject(project);
			        						try {
			        		        			if(config.getTestCasesBasePath()==null) {
			        		        				config.setTestCasesBasePath(rootDir);
			        		        			}
			        		        			if(config.getOutFilesBasePath()==null) {
			        		        				config.setOutFilesBasePath(rootDir);
			        		        			}
			        		        			if(config.getTestCaseDir()==null) {
			        		        				config.setTestCaseDir("data");
			        		        			}
			        		        			if(config.getOutFilesDir()==null) {
			        		        				config.setOutFilesDir("out");
			        		        			}
			        		        			if(config.isEnabled()==null) {
			        		        				config.setEnabled(true);
			        		        			}
			        		        			if(config.getConcurrentUserSimulationNum()==null) {
			        		        				config.setConcurrentUserSimulationNum(0);
			        		        			}
			        		        			if(config.getHttpConnectionTimeout()==null) {
			        		        				config.setHttpConnectionTimeout(10000);
			        		        			}
			        		        			if(config.getHttpRequestTimeout()==null) {
			        		        				config.setHttpRequestTimeout(10000);
			        		        			}
			        		        			if(config.getLoadTestingReportSamples()==null) {
			        		        				config.setLoadTestingReportSamples(3);
			        		        			}
			        		        			if(config.getConcurrentUserRampUpTime()==null) {
			        		        				config.setConcurrentUserRampUpTime(0L);
			        		        			}
			        		        			if(config.getNumConcurrentExecutions()==null) {
			        		        				config.setNumConcurrentExecutions(1);
			        		        			}
			        		        			
												executorMojo.doExecute(config);
											} catch (Throwable e) {
												e.printStackTrace();
												if(e.getMessage()!=null)
													status = e.getMessage();
												else
													status = ExceptionUtils.getStackTrace(e);
											}
			        						isDone.set(true);
											isStarted.set(false);
										}
									});
	        						_executorThread.start();
	        						String text = "Execution Started";
				        			response.setContentType(MediaType.TEXT_PLAIN);
						            response.setContentLength(text.length());
						            response.getWriter().write(text);
				        			response.setStatus(HttpStatus.OK_200);
	        					} else if(!status.equals("")) {
	        						String temp = status;
	        						status = "";
	        						throw new RuntimeException("Execution failed with Error - " + temp);
	        					} else if(isDone.get()) {
	        						isDone.set(false);
	        						isStarted.set(false);
	        						String text = "Execution completed, check Reports Section";
				        			response.setContentType(MediaType.TEXT_PLAIN);
						            response.setContentLength(text.length());
						            response.getWriter().write(text);
				        			response.setStatus(HttpStatus.OK_200);
	        					} else if(isStarted.get()) {
	        						throw new RuntimeException("Execution already in progress..");
	        					} else {
	        						throw new RuntimeException("Unknown Error...");
	        					}
				        	} else if(request.getMethod().equals(Method.DELETE) ) {
				        		if(isStarted.get() && _executorThread!=null) {
				        			_executorThread.interrupt();
				        			_executorThread = null;
	        					} else if(!status.equals("")) {
	        						String temp = status;
	        						status = "";
	        						throw new RuntimeException("Execution failed with Error - " + temp);
	        					} else if(!isStarted.get()) {
	        						throw new RuntimeException("Testcase execution is not in progress...");
	        					} else if(isDone.get()) {
	        						isDone.set(false);
	        						isStarted.set(false);
	        						String text = "Execution completed, check Reports Section";
				        			response.setContentType(MediaType.TEXT_PLAIN);
						            response.setContentLength(text.length());
						            response.getWriter().write(text);
				        			response.setStatus(HttpStatus.OK_200);
	        					} else {
	        						throw new RuntimeException("Unknown Error...");
	        					}
				        	}
	        			} catch (Exception e) {
	        				String configJson = e.getMessage();
							response.setContentLength(configJson.length());
				            response.getWriter().write(configJson);
							response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
							return;
						}
			        }
			    },
			    "/execute");
		
		try {
		    server.start();
		    System.out.println("Press any key to stop the server...");
		    System.in.read();
		} catch (Exception e) {
		    System.err.println(e);
		}
	}

	public static void main(String[] args) throws MojoExecutionException, MojoFailureException {
		
		if(args.length>3 && args[0].equals("-configtool") && !args[1].trim().isEmpty() 
				&& !args[2].trim().isEmpty() && !args[3].trim().isEmpty()) {
			GatfConfigToolMojo mojo = new GatfConfigToolMojo();
			mojo.port = 9080;
			try {
				mojo.port = Integer.parseInt(args[1].trim());
			} catch (Exception e) {
			}
			mojo.ipAddress = args[2].trim();
			mojo.rootDir = args[3].trim();
			mojo.execute();
		}
	}
}
