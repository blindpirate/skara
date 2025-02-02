/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.skara.bots.notify;

import org.openjdk.skara.bot.*;
import org.openjdk.skara.forge.*;
import org.openjdk.skara.storage.StorageBuilder;

import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class NotifyBot implements Bot, Emitter {
    private final Logger log = Logger.getLogger("org.openjdk.skara.bots");
    private final HostedRepository repository;
    private final Path storagePath;
    private final Pattern branches;
    private final StorageBuilder<UpdatedTag> tagStorageBuilder;
    private final StorageBuilder<UpdatedBranch> branchStorageBuilder;
    private final StorageBuilder<PullRequestState> prStateStorageBuilder;
    private final List<RepositoryListener> repoListeners = new ArrayList<>();
    private final List<PullRequestListener> prListeners = new ArrayList<>();
    private final PullRequestUpdateCache updateCache;
    private final Map<String, Pattern> readyComments;
    private final String integratorId;

    private ZonedDateTime lastFullUpdate;

    NotifyBot(HostedRepository repository, Path storagePath, Pattern branches, StorageBuilder<UpdatedTag> tagStorageBuilder,
              StorageBuilder<UpdatedBranch> branchStorageBuilder, StorageBuilder<PullRequestState> prStateStorageBuilder,
              Map<String, Pattern> readyComments, String integratorId) {
        this.repository = repository;
        this.storagePath = storagePath;
        this.branches = branches;
        this.tagStorageBuilder = tagStorageBuilder;
        this.branchStorageBuilder = branchStorageBuilder;
        this.prStateStorageBuilder = prStateStorageBuilder;
        this.updateCache = new PullRequestUpdateCache();
        this.readyComments = readyComments;
        this.integratorId = integratorId;
    }

    public static NotifyBotBuilder newBuilder() {
        return new NotifyBotBuilder();
    }

    private boolean isOfInterest(PullRequest pr) {
        var labels = new HashSet<>(pr.labelNames());
        var branchExists = pr.repository().branches().stream()
                            .map(HostedBranch::name)
                            .anyMatch(name -> name.equals(PreIntegrations.preIntegrateBranch(pr)));
        if (!(labels.contains("rfr") || labels.contains("integrated") || branchExists)) {
            log.fine("PR is not yet ready - needs either 'rfr' or 'integrated' label");
            return false;
        }

        var comments = pr.comments();
        for (var readyComment : readyComments.entrySet()) {
            var commentFound = false;
            for (var comment : comments) {
                if (comment.author().username().equals(readyComment.getKey())) {
                    var matcher = readyComment.getValue().matcher(comment.body());
                    if (matcher.find()) {
                        commentFound = true;
                        break;
                    }
                }
            }
            if (!commentFound) {
                log.fine("PR is not yet ready - missing ready comment from '" + readyComment.getKey() +
                                 "containing '" + readyComment.getValue().pattern() + "'");
                return false;
            }
        }
        return true;
    }

    @Override
    public void registerPullRequestListener(PullRequestListener listener) {
        prListeners.add(listener);
    }

    @Override
    public void registerRepositoryListener(RepositoryListener listener) {
        repoListeners.add(listener);
    }

    @Override
    public String toString() {
        return "NotifyBot@" + repository.name();
    }

    @Override
    public List<WorkItem> getPeriodicItems() {
        var ret = new LinkedList<WorkItem>();
        List<PullRequest> prs;

        if (!prListeners.isEmpty()) {
            // Fetch all open pull requests periodically, and just the recently updated ones in between
            if (lastFullUpdate == null || lastFullUpdate.isBefore(ZonedDateTime.now().minus(Duration.ofMinutes(10)))) {
                lastFullUpdate = ZonedDateTime.now();
                log.info("Fetching all open pull requests");
                prs = repository.openPullRequests();
            } else {
                log.info("Fetching recently updated pull requests (open and closed)");
                prs = repository.pullRequestsAfter(ZonedDateTime.now().minus(Duration.ofDays(14)));
            }

            // Pull request events
            for (var pr : prs) {
                if (updateCache.needsUpdate(pr)) {
                    if (!isOfInterest(pr)) {
                        continue;
                    }
                    ret.add(new PullRequestWorkItem(pr,
                            prStateStorageBuilder,
                            prListeners,
                            e -> updateCache.invalidate(pr),
                            integratorId));
                }
            }
        }

        // Repository events
        if (!repoListeners.isEmpty()) {
            ret.add(new RepositoryWorkItem(repository, storagePath, branches, tagStorageBuilder, branchStorageBuilder, repoListeners));
        }

        return ret;
    }

    @Override
    public String name() {
        return NotifyBotFactory.NAME;
    }

    public Pattern getBranches() {
        return branches;
    }

    public Map<String, Pattern> getReadyComments() {
        return readyComments;
    }


}
