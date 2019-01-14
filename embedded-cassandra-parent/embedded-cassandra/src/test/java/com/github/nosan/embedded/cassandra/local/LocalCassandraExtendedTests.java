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

package com.github.nosan.embedded.cassandra.local;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;

import com.github.nosan.embedded.cassandra.Cassandra;
import com.github.nosan.embedded.cassandra.test.support.CaptureOutput;
import com.github.nosan.embedded.cassandra.test.support.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LocalCassandra}.
 *
 * @author Dmytro Nosan
 */
public class LocalCassandraExtendedTests {

	@Rule
	public final CaptureOutput output = new CaptureOutput();

	@Test
	public void interruptCassandra() throws InterruptedException {
		Cassandra cassandra = new LocalCassandraFactory().create();

		Thread thread = new Thread(cassandra::start);
		thread.start();
		Thread.sleep(5000);
		thread.interrupt();
		thread.join();

		assertThat(this.output.toString()).contains("Cassandra launch was interrupted");
		assertThat(this.output.toString()).contains("Apache Cassandra (3.11.3) has been stopped");

	}

	@Test
	public void shouldRegisterShutdownHookOnlyOnce() throws ClassNotFoundException {
		Set<Thread> beforeHooks = getHooks();
		LocalCassandraFactory factory = new LocalCassandraFactory();
		factory.setRegisterShutdownHook(true);
		Cassandra cassandra = factory.create();
		try {
			cassandra.start();
		}
		finally {
			cassandra.stop();
		}
		try {
			cassandra.start();
		}
		finally {
			cassandra.stop();
		}
		Set<Thread> afterHooks = getHooks();
		afterHooks.removeAll(beforeHooks);
		assertThat(afterHooks).filteredOn(
				thread -> thread.getName().contains("cassandra-") &&
						thread.getName().endsWith("-hook"))
				.hasSize(1);
	}

	@Test
	public void shouldNotRegisterShutdownHook() throws ClassNotFoundException {
		Set<Thread> beforeHooks = getHooks();
		LocalCassandraFactory factory = new LocalCassandraFactory();
		factory.setRegisterShutdownHook(false);
		Cassandra cassandra = factory.create();
		try {
			cassandra.start();
		}
		finally {
			cassandra.stop();
		}
		Set<Thread> afterHooks = getHooks();
		afterHooks.removeAll(beforeHooks);
		assertThat(afterHooks)
				.noneMatch(thread -> thread.getName().contains("cassandra-") &&
						thread.getName().endsWith("-hook"));
	}

	@SuppressWarnings("unchecked")
	private static Set<Thread> getHooks() throws ClassNotFoundException {
		return new LinkedHashSet<>(((Map<Thread, Thread>) ReflectionUtils
				.getStaticField(Class.forName("java.lang.ApplicationShutdownHooks"), "hooks"))
				.keySet());
	}
}
