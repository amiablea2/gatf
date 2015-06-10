package com.gatf.executor.distributed;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.distributed.DistributedAcceptanceContext.Command;
import com.gatf.executor.report.ReportHandler;
import com.gatf.executor.report.RuntimeReportUtil;
import com.gatf.executor.report.RuntimeReportUtil.LoadTestEntry;

public class DistributedGatfTester {

	private static final Logger logger = Logger.getLogger(DistributedGatfTester.class.getSimpleName());
	
	public static class DistributedConnection {
		private Socket sock;
		private String node;
		private ObjectOutputStream oos = null;
		private ObjectInputStream ois = null;
		
		public String toString()
		{
			return node;
		}
	}
	
	public DistributedConnection distributeContext(String node, AcceptanceTestContext context, 
			List<TestCase> simTestCases)
	{
		Socket client = null;
		DistributedConnection conn = null;
		try {
			logger.info("Connecting to node " + node);
			
			client = new Socket(node, 4567);
			DistributedAcceptanceContext disContext = context.getDistributedContext(node);
			
			ObjectOutputStream oos = new ObjectOutputStream(client.getOutputStream());
			ObjectInputStream ois = new ObjectInputStream(client.getInputStream());
			
			logger.info("Sending GATF configuration to node " + node);
			oos.writeObject(Command.CONFIG_SHARE_REQ);
			oos.flush();
			oos.writeObject(disContext);
			oos.flush();
			logger.info("Sent GATF configuration to node " + node);
			
			Command command = (Command)ois.readObject();
			if(command==Command.CONFIG_SHARE_RES) {
				conn = new DistributedConnection();
				conn.sock = client;
				conn.node = node;
				conn.ois = ois;
				conn.oos = oos;
				logger.info("Sending GATF configuration Successful to node " + node);
			} else {
				logger.info("Sending GATF configuration Failed to node " + node);
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.info("Sending GATF configuration Failed to node " + node);
			if(client!=null)
			{
				try {
					client.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}
		return conn;
	}
	
	public FutureTask<DistributedTestStatus> distributeTests(List<TestCase> simTestCases, 
			final DistributedConnection connection, boolean dorep, int index, int numberOfRuns,
			final AcceptanceTestContext context, Set<String> relativeFileNames)
	{
		if(connection==null)return null;
		
		FutureTask<DistributedTestStatus> task = null;
		try {
			DistributedTestContext testContext = new DistributedTestContext();
			testContext.setSimTestCases(simTestCases);
			testContext.setDoReporting(dorep);
			testContext.setIndex(index);
			testContext.setNumberOfRuns(numberOfRuns);
			testContext.setRelativeFileNames(relativeFileNames);
			
			logger.info("Sending GATF tests to node " + connection.node);
			connection.oos.writeObject(Command.TESTS_SHARE_REQ);
			connection.oos.flush();
			connection.oos.writeObject(testContext);
			connection.oos.flush();
			logger.info("Sent GATF tests to node " + connection.node);
			
			Command command = (Command)connection.ois.readObject();
			if(command==Command.TESTS_SHARE_RES) {
				task = new FutureTask<DistributedTestStatus>(new Callable<DistributedTestStatus>() {
					public DistributedTestStatus call() throws Exception {
						DistributedTestStatus res = null;
						Socket fClient = connection.sock;
						try {
							logger.info("Waiting for GATF tests Results from node " + connection.node);
							
							Command command = (Command)connection.ois.readObject();
							while(command==Command.LOAD_TESTS_RES)
							{
								LoadTestEntry lentry = (LoadTestEntry)connection.ois.readObject();
								RuntimeReportUtil.addEntry(lentry);
								command = (Command)connection.ois.readObject();
							}
							
							res = (DistributedTestStatus)connection.ois.readObject();
							logger.info("Received GATF tests Results from node " + connection.node);
							
							String fileName = "dist-" + res.getZipFileName();
							File basePath = null;
				        	if(context.getGatfExecutorConfig().getOutFilesBasePath()!=null)
				        		basePath = new File(context.getGatfExecutorConfig().getOutFilesBasePath());
				        	else
				        	{
				        		URL url = Thread.currentThread().getContextClassLoader().getResource(".");
				        		basePath = new File(url.getPath());
				        	}
				        	File resource = new File(basePath, context.getGatfExecutorConfig().getOutFilesDir());
				        	
				        	File zipFile = new File(resource, fileName);
				        	FileOutputStream fos = new FileOutputStream(zipFile);
							IOUtils.copy(connection.ois, fos);
							fos.flush();
							fos.close();
				        	
				        	ReportHandler.unzipZipFile(new FileInputStream(zipFile), resource.getAbsolutePath());
							
							logger.info("Done Receiving GATF tests Result files from node " + connection.node);
						} catch (Exception e) {
							e.printStackTrace();
							logger.info("Failure occurred while waiting for GATF tests Results from node " + connection.node);
						} finally {
							if(fClient!=null)
							{
								try {
									fClient.close();
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
						}
						return res;
					}
				});
				new Thread(task).start();
				logger.info("Sending GATF tests Successful to node " + connection.node);
			} else {
				logger.info("Sending GATF tests Failed to node " + connection.node);
				if(connection!=null && connection.sock!=null)
				{
					try {
						connection.sock.close();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.info("Sending GATF tests Failed to node " + connection.node);
			if(connection!=null && connection.sock!=null)
			{
				try {
					connection.sock.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}
		return task;
	}
}
