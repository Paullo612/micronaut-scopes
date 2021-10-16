/*
 * Copyright 2021 Paullo612
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.paullo612.micronaut;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.exceptions.BeanContextException;
import io.micronaut.context.exceptions.BeanCreationException;
import io.micronaut.context.scope.AbstractConcurrentCustomScope;
import io.micronaut.context.scope.BeanCreationContext;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.runtime.context.scope.ScopedProxy;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class App {

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Scope
    @ScopedProxy
    @interface MyScope1 { }

    @Singleton
    static class MyCustomScope1 extends AbstractConcurrentCustomScope<MyScope1> {

        private ConcurrentHashMap<BeanIdentifier, CreatedBean<?>> scopedBeans;

        MyCustomScope1() {
            super(MyScope1.class);
        }

        @Override
        protected <T> CreatedBean<T> doCreate(BeanCreationContext<T> creationContext) {
            throw new BeanCreationException("Sorry.") {};
        }

        @Override
        protected Map<BeanIdentifier, CreatedBean<?>> getScopeMap(boolean forCreation) {
            if (scopedBeans != null) {
                // Return existing map, if present.
                return scopedBeans;
            }

            if (forCreation) {
                scopedBeans = new ConcurrentHashMap<>(5);
            }

            assert scopedBeans != null;
            return scopedBeans;
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public void close() {
            // Not relevant.
        }
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Scope
    @ScopedProxy
    @interface MyScope2 { }

    @Singleton
    static class MyCustomScope2 implements CustomScope<MyScope2> {

        @Override
        public Class<MyScope2> annotationType() {
            return MyScope2.class;
        }

        @Override
        public <T> T getOrCreate(BeanCreationContext<T> creationContext) {
            throw new BeanCreationException("Sorry.") {};
        }

        @Override
        public <T> Optional<T> remove(BeanIdentifier identifier) {
            return Optional.empty();
        }
    }

    @Bean
    @MyScope1
    static class MyBean1 {
        void printHelloWorld() {
            System.out.println("Hello world!");
        }
    }

    @Bean
    @MyScope2
    static class MyBean2 {
        void printHelloWorld() {
            System.out.println("Hello world!");
        }
    }

    public static void main(String[] args) {
        BeanContext beanContext = BeanContext.run();

        try {
            beanContext.getBean(MyBean2.class).printHelloWorld();
        } catch (BeanContextException e) {
            System.out.println("Got bean creation exception.");
        }

        try {
            beanContext.getBean(MyBean1.class).printHelloWorld();
        } catch (BeanContextException e) {
            System.out.println("Got bean creation exception.");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
