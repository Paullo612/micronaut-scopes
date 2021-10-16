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

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.context.scope.AbstractConcurrentCustomScope;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.runtime.context.scope.ScopedProxy;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
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

        void setKey(MyScopeKey key) {
            MY_SCOPE_KEY.set(key);
        }
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Around
    @Inherited
    public @interface MyIntercepted1 {
    }

    @Prototype
    @InterceptorBean(MyIntercepted1.class)
    static class MyIntercepted1Interceptor implements MethodInterceptor<Object, Object> {

        MyIntercepted1Interceptor() {
            System.out.println("Interceptor1 created.");
        }

        @Override
        public Object intercept(MethodInvocationContext<Object, Object> context) {
            System.out.println("Intercept 1");
            return context.proceed();
        }
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Around
    @Inherited
    public @interface MyIntercepted2 {
    }

    @Singleton
    @InterceptorBean(MyIntercepted2.class)
    static class MyIntercepted2Interceptor implements MethodInterceptor<Object, Object> {

        MyIntercepted2Interceptor() {
            System.out.println("Interceptor2 created.");
        }

        @Override
        public Object intercept(MethodInvocationContext<Object, Object> context) {
            System.out.println("Intercept 2");
            return context.proceed();
        }
    }

    @Bean
    @MyScope
    static class MyBean {

        MyBean() {
            System.out.println("My bean constructor");
        }

        @MyIntercepted1
        @MyIntercepted2
        void printHelloWorld() {
            System.out.println("Hello World!");
        }
    }

    public static void main(String[] args) {
        BeanContext beanContext = BeanContext.run();

        MyCustomScope scope = beanContext.getBean(MyCustomScope.class);

        MyScopeKey key1 = new MyScopeKey();
        MyScopeKey key2 = new MyScopeKey();

        scope.setKey(key1);

        beanContext.getBean(MyBean.class).printHelloWorld();

        scope.setKey(key2);

        beanContext.getBean(MyBean.class).printHelloWorld();
    }
}
