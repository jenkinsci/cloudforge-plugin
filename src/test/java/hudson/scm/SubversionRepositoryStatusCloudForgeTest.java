package hudson.scm;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.ItemGroup;
import hudson.model.AbstractProject;
import hudson.model.Project;
import hudson.triggers.SCMTrigger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

/**
 * @see SubversionRepositoryStatusTest
 */
public class SubversionRepositoryStatusCloudForgeTest {

	@SuppressWarnings("rawtypes")
	static class JobProviderBean implements SubversionRepositoryStatus.JobProvider {
		private List<AbstractProject> allJobs = new ArrayList<AbstractProject>();

		public List<AbstractProject> getAllJobs() {
			return Collections.unmodifiableList(allJobs);
		}

		public void addJob(AbstractProject job) {
			allJobs.add(job);
		}
	}
	
	static abstract class MockBuild extends Build<MockProject, MockBuild> {

		protected MockBuild(MockProject project) throws IOException {
			super(project);
		}
		
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	static abstract class MockProject extends Project<MockProject, MockBuild> {

		protected MockProject(ItemGroup parent, String name) {
			super(parent, name);
		}

	}

	static BufferedReader asReader(String string) {
		return new BufferedReader(new StringReader(string));
	}

	static SubversionSCM.ModuleLocation mockModuleLocation(String remote, String local) {
		SubversionSCM.ModuleLocation loc = new SubversionSCM.ModuleLocation(remote, local);
		try {
			Field uuidf = SubversionSCM.ModuleLocation.class.getDeclaredField("repositoryUUID");
			uuidf.setAccessible(true);
			uuidf.set(loc, UUID.randomUUID());

			Field rootf = SubversionSCM.ModuleLocation.class.getDeclaredField("repositoryRoot");
			rootf.setAccessible(true);
			SVNURL root = SVNURL.parseURIEncoded(remote);
			rootf.set(loc, root);
		} catch (NoSuchFieldException e) {
			throw (Error) new NoSuchFieldError().initCause(e);
		} catch (IllegalAccessException e) {
			throw new SecurityException(e);
		} catch (SVNException e) {
			throw new IllegalArgumentException(remote, e);
		}
		return loc;
	}

	private SubversionRepositoryStatusCloudForge repositoryStatus = 
			new SubversionRepositoryStatusCloudForge("cloudforge.com");
	private JobProviderBean jobProvider = new JobProviderBean();

	@Before
	public void setUp() {
		repositoryStatus.setJobProvider(jobProvider);
	}

	@Test
	@Bug(15794)
	public void shouldIgnoreDisabledJobs() throws IOException {

		// GIVEN: a disabled project
		final MockProject project = mock(MockProject.class);
		when(project.isDisabled()).thenReturn(true);

		jobProvider.addJob(project);

		// WHEN: post-commit hook is triggered
		StaplerRequest request = mock(StaplerRequest.class);
		when(request.getReader()).thenReturn(asReader("/somepath\n"));
		when(request.getParameter("changed")).thenReturn("/somepath\n");
		StaplerResponse response = mock(StaplerResponse.class);

		repositoryStatus.doNotifyCommit(request, response);

		// THEN: disabled project is not considered at all
		verify(response, atLeastOnce()).setStatus(SC_OK);
		verify(project, never()).getScm();
	}

	@Test
	public void shouldPoll() throws IOException {
		// GIVEN: an active project
		final MockProject project = mock(MockProject.class);
		when(project.isDisabled()).thenReturn(false);
		final SubversionSCM svn = mock(SubversionSCM.class);
		final SCMTrigger trigger = mock(SCMTrigger.class);
		when(project.getTrigger(SCMTrigger.class)).thenReturn(trigger);
		when(project.getScm()).thenReturn(svn);
		when(svn.getProjectLocations(project)).thenReturn(new SubversionSCM.ModuleLocation[] { 
				mockModuleLocation("https://testing.svn.cloudforge.com/test", "local")
		});
		jobProvider.addJob(project);

		// WHEN: post-commit hook is triggered
		StaplerRequest request = mock(StaplerRequest.class);
		when(request.getParameter("changed")).thenReturn("/somepath\n");
		when(request.getParameter("service")).thenReturn("svn");
		when(request.getParameter("project")).thenReturn("test");
		when(request.getParameter("organization")).thenReturn("testing");
		when(request.getParameter("youngest")).thenReturn("0");

		StaplerResponse response = mock(StaplerResponse.class);

		repositoryStatus.doNotifyCommit(request, response);

		// THEN: poll the project to see if it changed
		verify(response, atLeastOnce()).setStatus(SC_OK);
		verify(project, atLeastOnce()).getScm();
		verify(project, atLeastOnce()).getTrigger(SCMTrigger.class);
		assertEquals(trigger, project.getTrigger(SCMTrigger.class));
		verify(trigger, atLeastOnce()).run(anyListOf(Action.class).toArray(new Action[0]));
	}
}
