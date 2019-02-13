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

package com.github.nosan.embedded.cassandra.test.spring;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.util.Assert;

/**
 * {@link ContextCustomizer} that register {@link EmbeddedCassandraFactoryBean} and {@link
 * EmbeddedClusterBeanFactoryPostProcessor}.
 *
 * @author Dmytro Nosan
 * @since 1.0.0
 */
class EmbeddedCassandraContextCustomizer implements ContextCustomizer {

	private static final Logger log = LoggerFactory.getLogger(EmbeddedCassandraContextCustomizer.class);

	@Nonnull
	private final EmbeddedCassandra annotation;

	/**
	 * Creates a {@link EmbeddedCassandraContextCustomizer}.
	 *
	 * @param annotation annotation
	 */
	EmbeddedCassandraContextCustomizer(@Nonnull EmbeddedCassandra annotation) {
		this.annotation = Objects.requireNonNull(annotation, "@EmbeddedCassandra must not be null");
	}

	@Override
	public void customizeContext(@Nonnull ConfigurableApplicationContext context,
			@Nonnull MergedContextConfiguration mergedConfig) {
		Assert.isInstanceOf(BeanDefinitionRegistry.class, context.getBeanFactory(),
				String.format("(%s) can only be used with a BeanDefinitionRegistry", getClass()));
		ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
		BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
		RootBeanDefinition bd = new RootBeanDefinition(EmbeddedCassandraFactoryBean.class);
		bd.setPrimary(true);
		bd.getConstructorArgumentValues().addIndexedArgumentValue(0, mergedConfig.getTestClass());
		bd.getConstructorArgumentValues().addIndexedArgumentValue(1, this.annotation);
		BeanDefinitionUtils.registerBeanDefinition(registry, EmbeddedCassandraFactoryBean.BEAN_NAME, bd);
		log.info("'{}' has been registered as a primary bean", EmbeddedCassandraFactoryBean.BEAN_NAME);
		if (this.annotation.replace() == EmbeddedCassandra.Replace.ANY) {
			BeanDefinitionUtils.registerBeanDefinition(registry, EmbeddedClusterBeanFactoryPostProcessor.BEAN_NAME,
					new RootBeanDefinition(EmbeddedClusterBeanFactoryPostProcessor.class));
		}
	}

	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || (other != null && getClass() == other.getClass()));
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

}
