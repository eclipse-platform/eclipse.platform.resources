/*******************************************************************************
 * Copyright (c) 2004, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM - Initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.utils;

import java.util.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.*;
import org.osgi.framework.Bundle;

/**
 * Performs string sharing passes on all string pool participants registered
 * with the platform.
 */
public class StringPoolJob extends Job {
	private static final long INITIAL_DELAY = 10000;//ten seconds
	private static final long RESCHEDULE_DELAY = 300000;//five minutes
	private long lastDuration;
	/**
	 * Stores all registered string pool participants, along with the scheduling
	 * rule required when running it.
	 */
	private Map participants = Collections.synchronizedMap(new HashMap(10));

	private final Bundle systemBundle = Platform.getBundle("org.eclipse.osgi"); //$NON-NLS-1$

	public StringPoolJob() {
		super(Messages.utils_stringJobName);
		setSystem(true);
		setPriority(DECORATE);
	}

	/**
	 * Adds a string pool participant.  The job periodically builds
	 * a string pool and asks all registered participants to share their strings in
	 * the pool.  Once all participants have added their strings to the pool, the
	 * pool is discarded to avoid additional memory overhead.
	 * 
	 * Adding a participant that is equal to a participant already registered will
	 * replace the scheduling rule associated with the participant, but will otherwise
	 * be ignored.
	 * 
	 * @param participant The participant to add
	 * @param rule The scheduling rule that must be owned at the time the
	 * participant is called.  This allows a participant to protect their data structures
	 * against access at unsafe times.
	 * 
	 * @see #removeStringPoolParticipant(IStringPoolParticipant)
	 * @since 3.1
	 */
	public void addStringPoolParticipant(IStringPoolParticipant participant, ISchedulingRule rule) {
		participants.put(participant, rule);
		if (getState() == Job.SLEEPING)
			wakeUp(INITIAL_DELAY);
		else
			schedule(INITIAL_DELAY);
	}

	/** 
	 * Removes the indicated log listener from the set of registered string
	 * pool participants.  If no such participant is registered, no action is taken.
	 *
	 * @param participant the participant to deregister
	 * @see #addStringPoolParticipant(IStringPoolParticipant, ISchedulingRule)
	 * @since 3.1
	 */
	public void removeStringPoolParticipant(IStringPoolParticipant participant) {
		participants.remove(participant);
	}

	/* (non-Javadoc)
	 * Method declared on Job
	 */
	protected IStatus run(IProgressMonitor monitor) {
		//if the system is shutting down, don't share strings
		if (systemBundle != null && systemBundle.getState() == Bundle.STOPPING)
			return Status.OK_STATUS;

		//copy current participants to handle concurrent additions and removals to map
		Map.Entry[] entries = (Map.Entry[]) participants.entrySet().toArray(new Map.Entry[0]);
		ISchedulingRule[] rules = new ISchedulingRule[entries.length];
		IStringPoolParticipant[] toRun = new IStringPoolParticipant[entries.length];
		for (int i = 0; i < toRun.length; i++) {
			toRun[i] = (IStringPoolParticipant) entries[i].getKey();
			rules[i] = (ISchedulingRule) entries[i].getValue();
		}
		final ISchedulingRule rule = MultiRule.combine(rules);
		long start = -1;
		int savings = 0;
		final IJobManager jobManager = Platform.getJobManager();
		try {
			jobManager.beginRule(rule, monitor);
			start = System.currentTimeMillis();
			savings = shareStrings(toRun, monitor);
		} finally {
			jobManager.endRule(rule);
		}
		if (start > 0) {
			lastDuration = System.currentTimeMillis() - start;
			if (Policy.DEBUG_STRINGS)
				Policy.debug("String sharing saved " + savings + " bytes in: " + lastDuration); //$NON-NLS-1$ //$NON-NLS-2$ 
		}
		//throttle frequency if it takes too long
		long scheduleDelay = Math.max(RESCHEDULE_DELAY, lastDuration*100);
		if (Policy.DEBUG_STRINGS)
			Policy.debug("Rescheduling string sharing job in: " + scheduleDelay); //$NON-NLS-1$
		schedule(scheduleDelay);
		return Status.OK_STATUS;
	}

	private int shareStrings(IStringPoolParticipant[] toRun, IProgressMonitor monitor) {
		final StringPool pool = new StringPool();
		for (int i = 0; i < toRun.length; i++) {
			if (monitor.isCanceled())
				break;
			final IStringPoolParticipant current = toRun[i];
			Platform.run(new ISafeRunnable() {
				public void handleException(Throwable exception) {
					//exceptions are already logged, so nothing to do
				}

				public void run() {
					current.shareStrings(pool);
				}
			});
		}
		return pool.getSavedStringCount();
	}
}
