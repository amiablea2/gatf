package com.gatf.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.gatf.report.TestCaseReport;
import com.ning.http.client.ListenableFuture;

public class ScenarioTestCaseExecutor implements TestCaseExecutor {

	public List<TestCaseReport> execute(TestCase testCase, TestCaseExecutorUtil testCaseExecutorUtil) {

		List<TestCaseReport> lst = new ArrayList<TestCaseReport>();
		
		boolean isReportingEnabled = testCaseExecutorUtil.getContext().getGatfExecutorConfig().isReportingEnabled();
		WorkflowContextHandler workflowContextHandler = testCaseExecutorUtil.getContext().getWorkflowContextHandler();
		
		TestCaseReport testCaseReport = null;
		
		List<ListenableFuture<TestCaseReport>> futures = new ArrayList<ListenableFuture<TestCaseReport>>();
		for (Map<String, String> scenarioMap : testCase.getRepeatScenarios()) {
			
			TestCase testCaseCopy = new TestCase(testCase);
			if(isReportingEnabled)
			{
				testCaseReport = new TestCaseReport();
				testCaseReport.setTestCase(testCaseCopy);
				testCaseReport.setNumberOfRuns(1);
			}
			try {
				workflowContextHandler.handleContextVariables(testCaseCopy, scenarioMap);
			} catch (Exception e) {
				if(isReportingEnabled)
				{
					testCaseReport.setStatus("Failed");
					testCaseReport.setError(e.getMessage());
					testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
					lst.add(testCaseReport);
					testCaseExecutorUtil.getContext().getFinalTestResults().get(testCaseCopy.getSourcefileName()).add(testCaseReport);
				}
				e.printStackTrace();
				continue;
			}
				
			ListenableFuture<TestCaseReport> listenableFuture = testCaseExecutorUtil.executeTestCase(testCaseCopy, testCaseReport);
			
			if(!testCaseReport.getTestCase().isRepeatScenariosConcurrentExecution())
			{
				try {
					testCaseReport = listenableFuture.get();
				} catch (Exception e) {
					if(isReportingEnabled)
					{
						testCaseReport.setStatus("Failed");
						testCaseReport.setError(e.getMessage());
						testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
					}
					e.printStackTrace();
				}
				
				if(isReportingEnabled && testCaseReport!=null) {
					lst.add(testCaseReport);
					testCaseExecutorUtil.getContext().getFinalTestResults().get(testCaseReport.getTestCase().getSourcefileName()).add(testCaseReport);
				}
			}
			else
			{
				futures.add(listenableFuture);
			}
		}
		
		for (ListenableFuture<TestCaseReport> listenableFuture : futures) {
			
			if(isReportingEnabled)
			{
				TestCase testCaseCopy = new TestCase(testCase);
				testCaseReport = new TestCaseReport();
				testCaseReport.setTestCase(testCaseCopy);
			}
			
			try {
				testCaseReport = listenableFuture.get();
			} catch (Exception e) {
				if(isReportingEnabled)
				{
					testCaseReport.setStatus("Failed");
					testCaseReport.setError(e.getMessage());
					testCaseReport.setErrorText(ExceptionUtils.getStackTrace(e));
				}
				e.printStackTrace();
			}
			
			if(isReportingEnabled && testCaseReport!=null)
			{
				testCaseExecutorUtil.getContext().getFinalTestResults().get(testCaseReport.getTestCase().getSourcefileName()).add(testCaseReport);
				lst.add(testCaseReport);
			}
		}
		
		return lst;
	}

}