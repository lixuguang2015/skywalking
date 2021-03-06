/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.plugin.tomcat78x;

import org.skywalking.apm.agent.core.context.CarrierItem;
import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.tag.Tags;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.network.trace.component.ComponentsDefine;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

/**
 * {@link TomcatInvokeInterceptor} fetch the serialized context data by using {@link
 * HttpServletRequest#getHeader(String)}. The {@link TraceSegment#refs} of current trace segment will reference to the
 * trace segment id of the previous level if the serialized context is not null.
 */
public class TomcatInvokeInterceptor implements InstanceMethodsAroundInterceptor {

    /**
     * * The {@link TraceSegment#refs} of current trace segment will reference to the
     * trace segment id of the previous level if the serialized context is not null.
     *
     * @param objInst
     * @param method
     * @param allArguments
     * @param argumentsTypes
     * @param result change this result, if you want to truncate the method.
     * @throws Throwable
     */
    @Override public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

        // 解析 ContextCarrier 对象，用于跨进程的链路追踪
        HttpServletRequest request = (HttpServletRequest)allArguments[0];
        ContextCarrier contextCarrier = new ContextCarrier();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            next.setHeadValue(request.getHeader(next.getHeadKey()));
        }

        // 创建 EntrySpan 对象
        AbstractSpan span = ContextManager.createEntrySpan(request.getRequestURI(), contextCarrier);

        // 设置标签键值对
        Tags.URL.set(span, request.getRequestURL().toString());
        Tags.HTTP.METHOD.set(span, request.getMethod());

        // 设置组件
        span.setComponent(ComponentsDefine.TOMCAT);

        // 设置组件分层
        SpanLayer.asHttp(span);
    }

    @Override public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        HttpServletResponse response = (HttpServletResponse)allArguments[1];

        // 若返回状态码大于等于 400 时：
        AbstractSpan span = ContextManager.activeSpan();
        if (response.getStatus() >= 400) {
            // 设置 EntrySpan 发生异常
            span.errorOccurred();
            // 并设置状态码的标签键值对
            Tags.STATUS_CODE.set(span, Integer.toString(response.getStatus()));
        }

        // 完成 EntrySpan 对象
        ContextManager.stopSpan();
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan span = ContextManager.activeSpan();
        // 设置 EntrySpan 的日志
        span.log(t);

        // 设置 EntrySpan 发生异常
        span.errorOccurred();
    }

}
