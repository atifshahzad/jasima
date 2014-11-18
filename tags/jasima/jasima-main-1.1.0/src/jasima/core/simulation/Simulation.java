/*******************************************************************************
 * Copyright (c) 2010-2013 Torsten Hildebrandt and jasima contributors
 *
 * This file is part of jasima, v1.0.
 *
 * jasima is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * jasima is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with jasima.  If not, see <http://www.gnu.org/licenses/>.
 *
 * $Id$
 *******************************************************************************/
package jasima.core.simulation;

import jasima.core.random.RandomFactory;
import jasima.core.simulation.Simulation.SimEvent;
import jasima.core.util.Util;
import jasima.core.util.observer.Notifier;
import jasima.core.util.observer.NotifierAdapter;
import jasima.core.util.observer.NotifierListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for a discrete event simulation. This class doesn't do much, but
 * only maintains an event queue and manages simulation time.
 * <p />
 * The typical life cycle of a simulation would be to create it, and
 * subsequently set any parameters. Afterwards {@link #init()} has to be called
 * before the actual simulation can be performed in {@link #run()}. After
 * completing a simulation the {@link #done()}-method should be called to
 * perform clean-up, collecting simulation results, etc.
 * 
 * @author Torsten Hildebrandt <hil@biba.uni-bremen.de>, 2012-02-08
 * @version 
 *          "$Id$"
 */
public class Simulation implements Notifier<Simulation, SimEvent> {

	public static final String QUEUE_IMPL_KEY = "jasima.core.simulation.Simulation.queueImpl";
	public static final String QUEUE_IMPL_DEF = EventHeap.class.getName();

	/**
	 * Base class for notifier events (NOT simulation events, they are not
	 * handled by the event queue, just send to listeners).
	 */
	public static class SimEvent {
	}

	// constants for default events thrown by a simulation
	public static final SimEvent SIM_INIT = new SimEvent();
	public static final SimEvent SIM_START = new SimEvent();
	public static final SimEvent SIM_END = new SimEvent();
	public static final SimEvent SIM_DONE = new SimEvent();
	public static final SimEvent COLLECT_RESULTS = new SimEvent();

	public enum SimMsgCategory {
		OFF, ERROR, WARN, INFO, DEBUG, TRACE, ALL
	}

	public static class SimPrintEvent extends SimEvent {
		public SimPrintEvent(Simulation sim, SimMsgCategory category,
				String message) {
			super();
			this.sim = sim;
			this.category = category;
			this.message = message;
		}

		public final Simulation sim;
		public final SimMsgCategory category;
		public final String message;
	}

	public static interface EventQueue {
		/** Insert an event in the queue. */
		public void insert(Event e);

		/** Extract the (chronologically) next event from the queue. */
		public Event extract();
	}

	// /////////// simulation parameters
	private double simulationLength = 0.0d;
	private RandomFactory rndStreamFactory;

	private HashMap<Object, Object> valueStore;

	private String name = null;

	// fields used during event notification
	public Map<String, Object> resultMap;

	// ////////////// attributes/fields used during a simulation

	// the current simulation time.
	private double simTime;
	// event queue
	private EventQueue eventList;
	// eventNum is used to enforce FIFO-order of concurrent events with equal
	// priorities
	private int eventNum;
	private boolean continueSim;
	private int numAppEvents;

	/**
	 * Performs all initializations required for a successful simulation
	 * {@link #run()}.
	 */
	protected void init() {
		eventList = createEventQueue();
		simTime = 0.0d;
		eventNum = Integer.MIN_VALUE;
		numAppEvents = 0;

		if (numListener() > 0) {
			fire(SIM_INIT);
		}
	}

	/**
	 * Runs the main simulation loop. This means:
	 * <ol>
	 * <li>taking an event from the event queue,
	 * <li>advancing simulation time, and
	 * <li>triggering event processing.
	 * </ol>
	 * A simulation is terminated if either the maximum simulation length is
	 * reached, there are no more application events in the queue, or some other
	 * code called {@link #end()}.
	 * <p>
	 * 
	 * @see jasima.core.simulation.Event#isAppEvent()
	 */
	public void run() {
		beforeRun();

		continueSim = numAppEvents > 0;

		// main event loop
		while (continueSim) {
			Event event = eventList.extract();

			// Advance clock to time of next event
			simTime = event.getTime();

			event.handle();

			if (event.isAppEvent()) {
				if (--numAppEvents == 0)
					continueSim = false;
			}
		}

		afterRun();
	}

	/**
	 * Override this method to perform initializations after {@link #init()},
	 * but before running the simulation.
	 */
	protected void beforeRun() {
		// schedule simulation end
		if (getSimulationLength() > 0.0)
			schedule(new Event(getSimulationLength(), Event.EVENT_PRIO_LOWEST) {
				@Override
				public void handle() {
					end();
				}
			});

		if (numListener() > 0) {
			fire(SIM_START);
		}
	}

	/**
	 * Override this method to perform some action after running the simulation,
	 * but before {@link #done()} is called.
	 */
	protected void afterRun() {
		if (numListener() > 0) {
			fire(SIM_END);
		}
	}

	/**
	 * Performs clean-up etc., after a simulation's {@link #run()} method finished.
	 */
	protected void done() {
		if (numListener() > 0) {
			fire(SIM_DONE);
		}
	}

	/**
	 * Schedules a new event.
	 */
	public void schedule(Event event) {
		event.eventNum = eventNum++;
		if (event.isAppEvent())
			numAppEvents++;
		eventList.insert(event);
	}

	/**
	 * After calling end() the simulation is terminated (after handling the
	 * current event).
	 */
	public void end() {
		continueSim = false;
	}

	/** Returns the current simulation time. */
	public double simTime() {
		return simTime;
	}

	public void produceResults(Map<String, Object> res) {
		res.put("simTime", simTime());

		resultMap = res;
		fire(COLLECT_RESULTS);
		resultMap = null;
	}

	/**
	 * Triggers a print event of category "normal".
	 * 
	 * @param message
	 *            The message to print.
	 * @see #print(SimMsgCategory, String)
	 */
	public void print(String message) {
		print(SimMsgCategory.INFO, message);
	}

	/**
	 * Triggers a print event of the given category. If an appropriate listener
	 * is installed, this should produce an output of {@code message}.
	 * 
	 * @param message
	 *            The message to print.
	 */
	public void print(SimMsgCategory category, String message) {
		if (numListener() > 0) {
			fire(new SimPrintEvent(this, category, message));
		}
	}

	/**
	 * Factory method to create a new event queue.
	 * 
	 * @return The event queue to use in this simulation.
	 */
	protected EventQueue createEventQueue() {
		String queueImpl = System.getProperty(QUEUE_IMPL_KEY, QUEUE_IMPL_DEF);
		Class<?> qClass;
		try {
			qClass = Class.forName(queueImpl);
			return (EventQueue) qClass.newInstance();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/** Sets the maximum simulation time. A value of 0.0 means no such limit. */
	public void setSimulationLength(double simulationLength) {
		this.simulationLength = simulationLength;
	}

	public double getSimulationLength() {
		return simulationLength;
	}

	public RandomFactory getRndStreamFactory() {
		return rndStreamFactory;
	}

	/**
	 * Sets the random factory to use to create random number streams for
	 * stochastic simulations.
	 */
	public void setRndStreamFactory(RandomFactory rndStreamFactory) {
		this.rndStreamFactory = rndStreamFactory;
	}

	public String getName() {
		return name;
	}

	/**
	 * Sets a name for this simulation.
	 */
	public void setName(String name) {
		this.name = name;
	}

	// event notification

	private NotifierAdapter<Simulation, SimEvent> adapter = null;

	@Override
	public void addNotifierListener(
			NotifierListener<Simulation, SimEvent> listener) {
		if (adapter == null)
			adapter = new NotifierAdapter<Simulation, SimEvent>(this);
		adapter.addNotifierListener(listener);
	}

	/**
	 * Adds a listener to this simulation. This method only differs from
	 * {@link #addNotifierListener(NotifierListener)} in its ability to
	 * (optionally) clone the listener (using
	 * {@link Util#cloneIfPossible(Object)}) before installing it.
	 * 
	 * @param l
	 *            The shop listener to add.
	 * @param cloneIfPossbile
	 *            whether to try to clone a new instance for each machine using
	 *            {@link Util#cloneIfPossible(Object)}.
	 */
	public NotifierListener<Simulation, SimEvent> installSimulationListener(
			NotifierListener<Simulation, SimEvent> l, boolean cloneIfPossbile) {
		if (cloneIfPossbile)
			l = Util.cloneIfPossible(l);
		addNotifierListener(l);
		return l;
	}

	@Override
	public NotifierListener<Simulation, SimEvent> getNotifierListener(int index) {
		return adapter.getNotifierListener(index);
	}

	@Override
	public void removeNotifierListener(
			NotifierListener<Simulation, SimEvent> listener) {
		adapter.removeNotifierListener(listener);
	}

	protected void fire(SimEvent event) {
		if (adapter != null)
			adapter.fire(event);
	}

	@Override
	public int numListener() {
		return adapter == null ? 0 : adapter.numListener();
	}

	/**
	 * Offers a simple get/put-mechanism to store and retrieve information as a
	 * kind of global data store. This can be used as a simple extension
	 * mechanism.
	 * 
	 * @param key
	 *            The key name.
	 * @param value
	 *            value to assign to {@code key}.
	 * @see #valueStoreGet(String)
	 */
	public void valueStorePut(Object key, Object value) {
		if (valueStore == null)
			valueStore = new HashMap<Object, Object>();
		valueStore.put(key, value);
	}

	/**
	 * Retrieves a value from the value store.
	 * 
	 * @param key
	 *            The entry to return, e.g., identified by a name.
	 * @return The value associated with {@code key}.
	 * @see #valueStorePut(Object, Object)
	 */
	public Object valueStoreGet(Object key) {
		if (valueStore == null)
			return null;
		else
			return valueStore.get(key);
	}

}