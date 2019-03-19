/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.nosan.embedded.cassandra.test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.Statement;
import org.apiguardian.api.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.nosan.embedded.cassandra.Cassandra;
import com.github.nosan.embedded.cassandra.CassandraException;
import com.github.nosan.embedded.cassandra.CassandraFactory;
import com.github.nosan.embedded.cassandra.Settings;
import com.github.nosan.embedded.cassandra.cql.CqlScript;
import com.github.nosan.embedded.cassandra.local.LocalCassandraFactory;
import com.github.nosan.embedded.cassandra.test.util.CqlScriptUtils;
import com.github.nosan.embedded.cassandra.test.util.CqlUtils;
import com.github.nosan.embedded.cassandra.util.annotation.Nullable;

/**
 * Test {@link Cassandra} that allows the Cassandra to be {@link #start() started} and
 * {@link #stop() stopped}. {@link TestCassandra} does not launch {@link Cassandra} itself, it simply delegates calls to
 * the underlying {@link Cassandra}.
 * <p>
 * In addition to the basic functionality includes utility methods to test {@code Cassandra} code.
 *
 * @author Dmytro Nosan
 * @see TestCassandraBuilder
 * @see CassandraFactory
 * @see CqlScriptUtils
 * @see CqlUtils
 * @see CqlScript
 * @since 1.0.0
 */
@API(since = "1.0.0", status = API.Status.STABLE)
public class TestCassandra implements Cassandra {

	private static final Logger log = LoggerFactory.getLogger(TestCassandra.class);

	private final boolean registerShutdownHook;

	private final Object lock = new Object();

	private final List<CqlScript> scripts;

	private final ClusterFactory clusterFactory;

	private final CassandraFactory cassandraFactory;

	private volatile State state = State.NEW;

	@Nullable
	private volatile Cassandra cassandra;

	@Nullable
	private volatile Cluster cluster;

	@Nullable
	private volatile Session session;

	@Nullable
	private volatile Thread startThread;

	/**
	 * Creates a {@link TestCassandra}.
	 *
	 * @param scripts CQL scripts to execute
	 */
	public TestCassandra(@Nullable CqlScript... scripts) {
		this(true, null, null, scripts);
	}

	/**
	 * Creates a {@link TestCassandra}.
	 *
	 * @param scripts CQL scripts to execute
	 * @param registerShutdownHook whether shutdown hook should be registered or not
	 */
	public TestCassandra(boolean registerShutdownHook, @Nullable CqlScript... scripts) {
		this(registerShutdownHook, null, null, scripts);
	}

	/**
	 * Creates a {@link TestCassandra}.
	 *
	 * @param clusterFactory factory to create a {@link Cluster}
	 * @param scripts CQL scripts to execute
	 */
	public TestCassandra(@Nullable ClusterFactory clusterFactory, @Nullable CqlScript... scripts) {
		this(true, null, clusterFactory, scripts);
	}

	/**
	 * Creates a {@link TestCassandra}.
	 *
	 * @param cassandraFactory factory to create a {@link Cassandra}
	 * @param scripts CQL scripts to execute
	 */
	public TestCassandra(@Nullable CassandraFactory cassandraFactory, @Nullable CqlScript... scripts) {
		this(true, cassandraFactory, null, scripts);
	}

	/**
	 * Creates a {@link TestCassandra}.
	 *
	 * @param clusterFactory factory to create a {@link Cluster}
	 * @param scripts CQL scripts to execute
	 * @param registerShutdownHook whether shutdown hook should be registered or not
	 */
	public TestCassandra(boolean registerShutdownHook, @Nullable ClusterFactory clusterFactory,
			@Nullable CqlScript... scripts) {
		this(registerShutdownHook, null, clusterFactory, scripts);
	}

	/**
	 * Creates a {@link TestCassandra}.
	 *
	 * @param cassandraFactory factory to create a {@link Cassandra}
	 * @param scripts CQL scripts to execute
	 * @param registerShutdownHook whether shutdown hook should be registered or not
	 */
	public TestCassandra(boolean registerShutdownHook, @Nullable CassandraFactory cassandraFactory,
			@Nullable CqlScript... scripts) {
		this(registerShutdownHook, cassandraFactory, null, scripts);
	}

	/**
	 * Creates a {@link TestCassandra}.
	 *
	 * @param cassandraFactory factory to create a {@link Cassandra}
	 * @param clusterFactory factory to create a {@link Cluster}
	 * @param scripts CQL scripts to execute
	 */
	public TestCassandra(@Nullable CassandraFactory cassandraFactory,
			@Nullable ClusterFactory clusterFactory, @Nullable CqlScript... scripts) {
		this(true, cassandraFactory, clusterFactory, scripts);
	}

	/**
	 * Creates a {@link TestCassandra}.
	 *
	 * @param cassandraFactory factory to create a {@link Cassandra}
	 * @param clusterFactory factory to create a {@link Cluster}
	 * @param scripts CQL scripts to execute
	 * @param registerShutdownHook whether shutdown hook should be registered or not
	 */
	public TestCassandra(boolean registerShutdownHook, @Nullable CassandraFactory cassandraFactory,
			@Nullable ClusterFactory clusterFactory, @Nullable CqlScript... scripts) {
		this.cassandraFactory = (cassandraFactory != null) ? cassandraFactory : new LocalCassandraFactory();
		this.scripts = Collections.unmodifiableList(Arrays.asList((scripts != null) ? scripts : new CqlScript[0]));
		this.clusterFactory = (clusterFactory != null) ? clusterFactory : new DefaultClusterFactory();
		this.registerShutdownHook = registerShutdownHook;
	}

	@Override
	public void start() throws CassandraException {
		synchronized (this.lock) {
			if (this.state != State.STARTED) {
				try {
					this.startThread = Thread.currentThread();
					try {
						registerShutdownHook();
					}
					catch (Throwable ex) {
						throw new CassandraException("Unable to register a shutdown hook for Test Cassandra", ex);
					}
					try {
						this.state = State.STARTING;
						start0();
						this.state = State.STARTED;
					}
					catch (InterruptedException ex) {
						stopSilently();
						this.state = State.START_INTERRUPTED;
						Thread.currentThread().interrupt();
					}
					catch (Throwable ex) {
						stopSilently();
						this.state = State.START_FAILED;
						throw new CassandraException("Unable to start Test Cassandra", ex);
					}
				}
				finally {
					this.startThread = null;
				}
			}
		}
	}

	@Override
	public void stop() throws CassandraException {
		synchronized (this.lock) {
			if (this.state != State.STOPPED) {
				try {
					this.state = State.STOPPING;
					stop0();
					this.state = State.STOPPED;
				}
				catch (InterruptedException ex) {
					this.state = State.STOP_INTERRUPTED;
					Thread.currentThread().interrupt();
				}
				catch (Throwable ex) {
					this.state = State.STOP_FAILED;
					throw new CassandraException("Unable to stop Test Cassandra", ex);
				}
			}
		}
	}

	@Override
	public Settings getSettings() throws CassandraException {
		return getCassandra().getSettings();
	}

	@Override
	public State getState() {
		return this.state;
	}

	/**
	 * Lazy initialize a {@link Cluster}.
	 * This {@link Cluster} will be closed by this {@code Cassandra}.
	 *
	 * @return a cluster
	 */
	public Cluster getCluster() {
		Cluster cluster = this.cluster;
		if (cluster == null) {
			synchronized (this.lock) {
				cluster = this.cluster;
				if (cluster == null) {
					cluster = this.clusterFactory.create(getSettings());
					Objects.requireNonNull(cluster, "Cluster is not initialized");
					this.cluster = cluster;
				}
			}
		}
		return cluster;
	}

	/**
	 * Lazy initialize a {@link Session} using a {@link #getCluster() Cluster}.
	 * This {@link Session} will be closed by this {@code Cassandra}.
	 *
	 * @return a session
	 */
	public Session getSession() {
		Session session = this.session;
		if (session == null) {
			synchronized (this.lock) {
				session = this.session;
				if (session == null) {
					session = getCluster().connect();
					this.session = session;
				}
			}
		}
		return session;
	}

	/**
	 * Returns the underlying {@link Cassandra}.
	 *
	 * @return the underlying {@link Cassandra}.
	 * @since 1.4.1
	 */
	@API(since = "1.4.1", status = API.Status.EXPERIMENTAL)
	public Cassandra getCassandra() {
		Cassandra cassandra = this.cassandra;
		if (cassandra == null) {
			synchronized (this.lock) {
				cassandra = this.cassandra;
				if (cassandra == null) {
					cassandra = this.cassandraFactory.create();
					Objects.requireNonNull(cassandra, "Cassandra is not initialized");
					this.cassandra = cassandra;
				}
			}
		}
		return cassandra;
	}

	/**
	 * Delete all rows from the specified tables.
	 *
	 * @param tableNames the names of the tables to delete from
	 * @since 1.0.6
	 */
	public void deleteFromTables(String... tableNames) {
		CqlUtils.deleteFromTables(getSession(), tableNames);
	}

	/**
	 * Drop the specified tables.
	 *
	 * @param tableNames the names of the tables to drop
	 * @since 1.0.6
	 */
	public void dropTables(String... tableNames) {
		CqlUtils.dropTables(getSession(), tableNames);
	}

	/**
	 * Drop the specified keyspaces.
	 *
	 * @param keyspaceNames the names of the keyspaces to drop
	 * @since 1.0.6
	 */
	public void dropKeyspaces(String... keyspaceNames) {
		CqlUtils.dropKeyspaces(getSession(), keyspaceNames);
	}

	/**
	 * Count the rows in the given table.
	 *
	 * @param tableName name of the table to count rows in
	 * @return the number of rows in the table
	 * @since 1.0.6
	 */
	public long getRowCount(String tableName) {
		return CqlUtils.getRowCount(getSession(), tableName);
	}

	/**
	 * Executes the given scripts.
	 *
	 * @param scripts the CQL scripts to execute.
	 * @since 1.0.6
	 */
	public void executeScripts(CqlScript... scripts) {
		CqlScriptUtils.executeScripts(getSession(), scripts);
	}

	/**
	 * Executes the provided query using the provided values.
	 *
	 * @param statement the CQL query to execute.
	 * @param args values required for the execution of {@code query}. See {@link
	 * SimpleStatement#SimpleStatement(String, Object...)} for more details.
	 * @return the result of the query. That result will never be null but can be empty (and will be
	 * for any non SELECT query).
	 * @since 1.0.6
	 */
	public ResultSet executeStatement(String statement, @Nullable Object... args) {
		return CqlUtils.executeStatement(getSession(), statement, args);
	}

	/**
	 * Executes the provided statement.
	 *
	 * @param statement the CQL statement to execute
	 * @return the result of the query. That result will never be null
	 * but can be empty (and will be for any non SELECT query).
	 * @since 1.2.8
	 */
	public ResultSet executeStatement(Statement statement) {
		return CqlUtils.executeStatement(getSession(), statement);
	}

	@Override
	public String toString() {
		return String.format("Test Cassandra '%s'", getCassandra());
	}

	private void start0() throws InterruptedException {
		Cassandra cassandra = getCassandra();
		if (log.isDebugEnabled()) {
			log.debug("Starts Test Cassandra '{}'", cassandra);
		}
		cassandra.start();
		if (cassandra.getState() == State.START_INTERRUPTED || Thread.interrupted()) {
			throw new InterruptedException();
		}
		if (!this.scripts.isEmpty()) {
			executeScripts(this.scripts.toArray(new CqlScript[0]));
		}
		if (log.isDebugEnabled()) {
			log.debug("Test Cassandra '{}' has been started", cassandra);
		}
	}

	private void stop0() throws InterruptedException {
		try {
			Session session = this.session;
			if (session != null) {
				if (log.isDebugEnabled()) {
					log.debug("Closes a session '{}'", session);
				}
				session.close();
			}
		}
		catch (Throwable ex) {
			log.error(String.format("Session '%s' has not been closed", this.session), ex);
		}
		this.session = null;

		try {
			Cluster cluster = this.cluster;
			if (cluster != null) {
				if (log.isDebugEnabled()) {
					log.debug("Closes a cluster '{}'", cluster);
				}
				cluster.close();
			}
		}
		catch (Throwable ex) {
			log.error(String.format("Cluster '%s' has not been closed", this.cluster), ex);
		}
		this.cluster = null;

		Cassandra cassandra = this.cassandra;
		if (cassandra != null && cassandra.getState() != State.STOPPED) {
			cassandra.stop();
			if (cassandra.getState() == State.STOP_INTERRUPTED || Thread.interrupted()) {
				throw new InterruptedException();
			}
			if (log.isDebugEnabled()) {
				log.debug("Test Cassandra '{}' has been stopped", cassandra);
			}
		}
		this.cassandra = null;
	}

	private void registerShutdownHook() {
		if (this.registerShutdownHook && this.state == State.NEW) {
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				Optional.ofNullable(this.startThread).ifPresent(Thread::interrupt);
				stopSilently();
			}, "Test Cassandra Shutdown Hook"));
		}
	}

	private void stopSilently() {
		try {
			stop();
		}
		catch (Throwable ex) {
			if (log.isDebugEnabled()) {
				log.error("Unable to stop Test Cassandra", ex);
			}
		}
	}

}
