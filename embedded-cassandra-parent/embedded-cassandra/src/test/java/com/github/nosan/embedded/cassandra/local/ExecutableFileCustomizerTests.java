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

import java.io.File;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.nosan.embedded.cassandra.Version;
import com.github.nosan.embedded.cassandra.test.support.DisableIfOS;
import com.github.nosan.embedded.cassandra.test.support.OSRule;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ExecutableFileCustomizer}.
 *
 * @author Dmytro Nosan
 */
public class ExecutableFileCustomizerTests {

	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Rule
	public final OSRule osRule = new OSRule();

	private final ExecutableFileCustomizer customizer = new ExecutableFileCustomizer();

	@Test
	@DisableIfOS("windows")
	public void setExecutableUnixFile() throws IOException {
		File file = createFile("cassandra");
		this.customizer.customize(this.temporaryFolder.getRoot().toPath(), new Version(3, 11, 3));
		assertThat(file.canExecute()).isTrue();
	}

	private File createFile(String name) throws IOException {
		TemporaryFolder temporaryFolder = this.temporaryFolder;
		File file = new File(temporaryFolder.newFolder("bin"), name);
		assertThat(file.createNewFile()).isTrue();
		assertThat(file.setExecutable(false)).isTrue();
		return file;
	}
}
