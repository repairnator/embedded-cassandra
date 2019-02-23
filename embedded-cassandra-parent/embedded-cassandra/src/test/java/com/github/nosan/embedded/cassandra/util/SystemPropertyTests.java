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

package com.github.nosan.embedded.cassandra.util;

import java.util.Map;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SystemProperty}.
 *
 * @author Dmytro Nosan
 */
public class SystemPropertyTests {

	@Test
	public void shouldGetSystemProperty() {
		try {
			System.setProperty("test", "value");
			assertThat(new SystemProperty("test").get()).isEqualTo("value");
		}
		finally {
			System.clearProperty("test");
		}
	}

	@Test
	public void shouldGetEnvironmentProperty() {
		Map<String, String> environment = System.getenv();
		assertThat(environment).isNotEmpty();
		String key = environment.keySet().iterator().next();
		String value = environment.get(key);
		assertThat(value).isNotNull();
		assertThat(new SystemProperty(key).get()).isEqualTo(value);
	}

	@Test
	public void shouldNotGet() {
		assertThatThrownBy(() -> new SystemProperty("test").getRequired())
				.isInstanceOf(NullPointerException.class)
				.hasStackTraceContaining("Both System and Environment Properties are not present for a key (test)");
	}

	@Test
	public void shouldReturnNull() {
		assertThat(new SystemProperty("test").get()).isNull();
	}
}
