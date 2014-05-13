package com.gatf.distributed;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

import com.gatf.distributed.DistributedAcceptanceContext.Command;
import com.gatf.executor.core.GatfTestCaseExecutorMojo;

public class DistributedGatfListener {

	private static final Logger logger = Logger.getLogger(DistributedGatfListener.class.getSimpleName());
	
	public static void main(String[] args) throws Exception {
		
		ServerSocket server = new ServerSocket(4567);
		logger.info("Distributed GATF node listening on port 4567");
		while(true) {
			final Socket client = server.accept();
			InputStream in = client.getInputStream();
			OutputStream out = client.getOutputStream();
			
			final ObjectInputStream ois = new ObjectInputStream(in);
			final ObjectOutputStream oos = new ObjectOutputStream(out);
			
			new Thread(new Runnable() {
				public void run() {
					try {
						handleCommand(ois, oos);
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						if(client!=null)
						{
							try {
								client.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				}
			}).start();
		}
	}
	
	private static void handleCommand(ObjectInputStream ois, ObjectOutputStream oos) throws Exception {
		
		logger.info("Got a new distributed GATF request...");
		
		DistributedAcceptanceContext context = null;
		DistributedTestContext tContext = null;
		try {
			
			Command command = (Command)ois.readObject();
			logger.info("Received command - " + command);
			if(command==Command.CONFIG_SHARE_REQ) {
				context = (DistributedAcceptanceContext)ois.readObject();
				if(context!=null) {
					oos.writeObject(Command.CONFIG_SHARE_RES);
					oos.flush();
					logger.info("Fetched GATF configuration...");
				} else {
					oos.writeObject(Command.INVALID);
					oos.flush();
					logger.info("Invalid GATF configuration received...");
				}
			} else {
				oos.writeObject(Command.INVALID);
				oos.flush();
				logger.info("Invalid Command received...");
			}
			
			command = (Command)ois.readObject();
			logger.info("Received command - " + command);
			if(command==Command.TESTS_SHARE_REQ) {
				tContext = (DistributedTestContext)ois.readObject();
				if(context!=null) {
					oos.writeObject(Command.TESTS_SHARE_RES);
					oos.flush();
					logger.info("Fetched GATF tests ...");
				} else {
					oos.writeObject(Command.INVALID);
					oos.flush();
					logger.info("Invalid GATF tests received...");
				}
			} else {
				oos.writeObject(Command.INVALID);
				oos.flush();
				logger.info("Invalid Command received...");
			}
			
			if(context!=null && tContext!=null) {
				logger.info("Started executing GATF tests...");
				GatfTestCaseExecutorMojo mojo = new GatfTestCaseExecutorMojo();
				DistributedTestStatus report = mojo.handleDistributedTests(context, tContext);
				oos.writeObject(report);
				oos.flush();
				logger.info("Writing GATF results...");
			}
		} catch (Exception e) {
			oos.write(0);
			logger.info("Error occurred during distributed GATF execution...");
			throw e;
		}
	}
}
