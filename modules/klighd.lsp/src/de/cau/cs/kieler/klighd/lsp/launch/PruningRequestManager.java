/*
 * KIELER - Kiel Integrated Environment for Layout Eclipse RichClient
 *
 * http://rtsys.informatik.uni-kiel.de/kieler
 *
 * Copyright 2026 by
 * + Kiel University
 *   + Department of Computer Science
 *     + Real-Time and Embedded Systems Group
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package de.cau.cs.kieler.klighd.lsp.launch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.eclipse.xtext.ide.server.concurrent.AbstractRequest;
import org.eclipse.xtext.ide.server.concurrent.RequestManager;
import org.eclipse.xtext.service.OperationCanceledManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Xtext's request manager keeps every request, finished ones and their results included, until the
 * next write request cancels them all. A running simulation lays its diagram out after every tick
 * with a read request, and no write comes between them: a long run kept every tick's diagram, some
 * gigabytes after a few thousand ticks. This manager forgets a request once it is done.
 */
@Singleton
public class PruningRequestManager extends RequestManager {

    /** The requests not done yet, oldest first: what a write request still has to cancel. */
    private final Set<AbstractRequest<?>> open = new LinkedHashSet<>();

    @Inject
    public PruningRequestManager(ExecutorService parallel, OperationCanceledManager operationCanceledManager) {
        super(parallel, operationCanceledManager);
    }

    @Override
    protected void addRequest(AbstractRequest<?> request) {
        synchronized (open) {
            open.add(request);
        }
        request.get().whenComplete((result, error) -> {
            synchronized (open) {
                open.remove(request);
            }
        });
    }

    @Override
    protected CompletableFuture<Void> cancel() {
        List<AbstractRequest<?>> cancelled;
        synchronized (open) {
            cancelled = new ArrayList<>(open);
            open.clear();
        }
        CompletableFuture<?>[] futures = new CompletableFuture<?>[cancelled.size()];
        for (int i = 0; i < cancelled.size(); i++) {
            cancelled.get(i).cancel();
            futures[i] = cancelled.get(i).get();
        }
        return CompletableFuture.allOf(futures);
    }
}
