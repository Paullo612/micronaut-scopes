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
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.context.scope.AbstractConcurrentCustomScope;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.runtime.context.scope.ScopedProxy;
import jakarta.inject.Provider;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class App {

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Scope
    @ScopedProxy
    @interface MyScope { }

    static class MyScopeKey {
        private ConcurrentHashMap<BeanIdentifier, CreatedBean<?>> scopedBeans;

        ConcurrentHashMap<BeanIdentifier, CreatedBean<?>> getScopedBeans() {
            return scopedBeans;
        }

        void setScopedBeans(ConcurrentHashMap<BeanIdentifier, CreatedBean<?>> scopedBeans) {
            this.scopedBeans = scopedBeans;
        }
    }

    static class NoMyScopedKeyException extends NoSuchBeanException {

        protected NoMyScopedKeyException() {
            super("No my scoped key present");
        }
    }

    @Singleton
    static class MyCustomScope extends AbstractConcurrentCustomScope<MyScope> {

        private static final ThreadLocal<MyScopeKey> MY_SCOPE_KEY = new ThreadLocal<>();

        MyCustomScope() {
            super(MyScope.class);
        }

        @Override
        protected Map<BeanIdentifier, CreatedBean<?>> getScopeMap(boolean forCreation) {
            MyScopeKey key = MY_SCOPE_KEY.get();

            if (key == null) {
                throw new NoMyScopedKeyException();
            }

            ConcurrentHashMap<BeanIdentifier, CreatedBean<?>> scopedBeans = key.getScopedBeans();

            if (scopedBeans != null) {
                // Return existing map, if present.
                return scopedBeans;
            }

            if (forCreation) {
                scopedBeans = new ConcurrentHashMap<>(5);
                key.setScopedBeans(scopedBeans);
            }

            assert scopedBeans != null;
            return scopedBeans;
        }

        @Override
        public boolean isRunning() {
            return MY_SCOPE_KEY.get() != null;
        }

        @Override
        public void close() {
            // Not relevant.
        }

        MyScopeKey getKey() {
            return MY_SCOPE_KEY.get();
        }

        void setKey(MyScopeKey key) {
            MY_SCOPE_KEY.set(key);
        }
    }

    static class MyCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context) {
            System.out.println("Condition test!");
            return context.getBean(MyCustomScope.class).getKey() != null;
        }
    }

    @Bean
    @MyScope
    @Requires(condition = MyCondition.class)
    static class MyHelloWorldPrinter {

        void doPrintHelloWorld() {
            System.out.println("Hello World!");
        }
    }

    @Singleton
    static class MyBean1 {

        private final BeanProvider<MyHelloWorldPrinter> myHelloWorldPrinterBeanProvider;
        private final MyCustomScope myCustomScope;

        MyBean1(BeanProvider<MyHelloWorldPrinter> myHelloWorldPrinterBeanProvider,
                MyCustomScope myCustomScope) {
            System.out.println("Bean1 constructor");
            this.myHelloWorldPrinterBeanProvider = myHelloWorldPrinterBeanProvider;
            this.myCustomScope = myCustomScope;
        }

        public void printHelloWorld() {
            myCustomScope.setKey(new MyScopeKey());

            if (myHelloWorldPrinterBeanProvider.isPresent()) {
                System.out.println("Bean is present.");
            } else {
                System.out.println("Bean is NOT present.");
            }

            if (myHelloWorldPrinterBeanProvider.isUnique()) {
                System.out.println("Bean is unique.");
            } else {
                System.out.println("Bean is NOT unique.");
            }

            try {
                myHelloWorldPrinterBeanProvider.get().doPrintHelloWorld();
            } finally {
                myCustomScope.setKey(null);
            }
        }
    }

    @Singleton
    static class MyBean2 {

        private final Provider<MyHelloWorldPrinter> myHelloWorldPrinterProvider;
        private final MyCustomScope myCustomScope;

        MyBean2(
                Provider<MyHelloWorldPrinter> myHelloWorldPrinterProvider,
                MyCustomScope myCustomScope) {
            System.out.println("Bean2 constructor");
            this.myHelloWorldPrinterProvider = myHelloWorldPrinterProvider;
            this.myCustomScope = myCustomScope;
        }

        public void printHelloWorld() {
            myCustomScope.setKey(new MyScopeKey());

            try {
                myHelloWorldPrinterProvider.get().doPrintHelloWorld();
            } finally {
                myCustomScope.setKey(null);
            }
        }
    }

    public static void main(String[] args) {
        BeanContext beanContext = BeanContext.run();

        beanContext.getBean(MyBean1.class).printHelloWorld();
        beanContext.getBean(MyBean2.class).printHelloWorld();
    }
}
