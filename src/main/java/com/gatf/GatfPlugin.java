package com.gatf;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

public interface GatfPlugin {
	void doExecute(GatfPluginConfig configuration) throws MojoFailureException;
	void setProject(MavenProject project);
	void shutdown();
}
