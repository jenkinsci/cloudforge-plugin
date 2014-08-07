/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.scm;

import hudson.Extension;
import hudson.model.AbstractModelObject;
import hudson.model.AbstractProject;
import hudson.model.UnprotectedRootAction;
import org.kohsuke.stapler.StaplerRequest;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * Receives the push notification of commits from repository.
 * Opened up for untrusted access.
 *
 * @see SubversionStatus
 */
@Extension
public class CloudForgeWebHookReciever extends AbstractPostCommitHookReceiver implements UnprotectedRootAction {
    public String getDisplayName() {
        return "cloudforge-webhook";
    }

    public String getSearchUrl() {
        return getUrlName();
    }

    public String getIconFileName() {
        // TODO
        return null;
    }

    public String getUrlName() {
        return "cloudforge-webhook";
    }



    @Override
    protected Set<String> getAffectedPath(StaplerRequest req) throws IOException {
        final String changed  = req.getParameter("changed");
        return new HashSet<String>(Arrays.asList(changed.split("\n")));
    }

    @Override
    protected long getRevision(StaplerRequest req) {
        final String revision = req.getParameter("youngest");
        if (revision != null) try {
            return Long.parseLong(revision);
        } catch (NumberFormatException e) {
            LOGGER.log(INFO, "Ignoring bad revision " + revision, e);
        }
        return -1;
    }

    @Override
    protected ModuleMatcher matcher(StaplerRequest req) {

        final String project  = req.getParameter("project");
        final String domain   = req.getParameter("organization");
        final String host = domain + ".svn.cloudforge.com";
        final SVNURL root;
        try {
            root = SVNURL.create("https", null, host, -1, project, false);
        } catch (SVNException e) {
            LOGGER.log(WARNING, "Failed to handle Subversion commit notification", e);
            return null;
        }

        return new ModuleMatcher() {
            public boolean match(AbstractProject<?, ?> p, SubversionSCM.ModuleLocation loc) throws SVNException {
                return loc.getRepositoryRoot(p).equals(root);
            }
        };
    }

    private static final Logger LOGGER = Logger.getLogger(CloudForgeWebHookReciever.class.getName());

}
