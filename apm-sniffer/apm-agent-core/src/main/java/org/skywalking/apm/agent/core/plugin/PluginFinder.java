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

package org.skywalking.apm.agent.core.plugin;

import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.agent.core.plugin.bytebuddy.AbstractJunction;
import org.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.skywalking.apm.agent.core.plugin.match.IndirectMatch;
import org.skywalking.apm.agent.core.plugin.match.NameMatch;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * The <code>PluginFinder</code> represents a finder , which assist to find the one
 * from the given {@link AbstractClassEnhancePluginDefine} list.
 *
 * @author wusheng
 */
public class PluginFinder {

    /**
     * NameMatch 与 AbstractClassEnhancePluginDefine 对象的映射
     * key ：方法名
     * value ：AbstractClassEnhancePluginDefine 对象数组
     */
    private final Map<String, LinkedList<AbstractClassEnhancePluginDefine>> nameMatchDefine = new HashMap<String, LinkedList<AbstractClassEnhancePluginDefine>>();
    /**
     * 非 NameMatch 的 AbstractClassEnhancePluginDefine 对象数组
     */
    private final List<AbstractClassEnhancePluginDefine> signatureMatchDefine = new LinkedList<AbstractClassEnhancePluginDefine>();

    public PluginFinder(List<AbstractClassEnhancePluginDefine> plugins) {
        for (AbstractClassEnhancePluginDefine plugin : plugins) {
            ClassMatch match = plugin.enhanceClass();

            if (match == null) {
                continue;
            }

            // 处理 NameMatch 为匹配的 AbstractClassEnhancePluginDefine 对象，添加到 nameMatchDefine 属性
            if (match instanceof NameMatch) {
                NameMatch nameMatch = (NameMatch)match;
                LinkedList<AbstractClassEnhancePluginDefine> pluginDefines = nameMatchDefine.get(nameMatch.getClassName());
                if (pluginDefines == null) {
                    pluginDefines = new LinkedList<AbstractClassEnhancePluginDefine>();
                    nameMatchDefine.put(nameMatch.getClassName(), pluginDefines);
                }
                pluginDefines.add(plugin);
            // 处理非 NameMatch 为匹配的 AbstractClassEnhancePluginDefine 对象，添加到 signatureMatchDefine 属性
            } else {
                signatureMatchDefine.add(plugin);
            }
        }
    }

    /**
     * 获得 AbstractClassEnhancePluginDefine 对象
     *
     * 被 SkyWalkingAgent 在 {@link net.bytebuddy.agent.builder.AgentBuilder.Default#transformation} 方法里调用
     *
     * @param typeDescription 类型描述
     * @param classLoader 类加载器。暂时无用。
     * @return AbstractClassEnhancePluginDefine 对象
     */
    public List<AbstractClassEnhancePluginDefine> find(TypeDescription typeDescription, ClassLoader classLoader) {
        List<AbstractClassEnhancePluginDefine> matchedPlugins = new LinkedList<AbstractClassEnhancePluginDefine>();

        // 以 nameMatchDefine 属性来匹配 AbstractClassEnhancePluginDefine 对象
        String typeName = typeDescription.getTypeName();
        if (nameMatchDefine.containsKey(typeName)) {
            matchedPlugins.addAll(nameMatchDefine.get(typeName));
        }

        // 以 signatureMatchDefine 属性来匹配 AbstractClassEnhancePluginDefine 对象
        for (AbstractClassEnhancePluginDefine pluginDefine : signatureMatchDefine) {
            IndirectMatch match = (IndirectMatch)pluginDefine.enhanceClass();
            if (match.isMatch(typeDescription)) {
                matchedPlugins.add(pluginDefine);
            }
        }

        return matchedPlugins;
    }

    /**
     * 获得全部插件的类匹配
     *
     * 多个插件的类匹配条件以 or 分隔
     *
     * @return 类匹配
     */
    public ElementMatcher<? super TypeDescription> buildMatch() {
        // 以 nameMatchDefine 属性来匹配
        ElementMatcher.Junction judge = new AbstractJunction<NamedElement>() {
            @Override
            public boolean matches(NamedElement target) {
                return nameMatchDefine.containsKey(target.getActualName());
            }
        };
        // 非接口
        judge = judge.and(not(isInterface()));
        // 以 signatureMatchDefine 属性来匹配
        for (AbstractClassEnhancePluginDefine define : signatureMatchDefine) {
            ClassMatch match = define.enhanceClass();
            if (match instanceof IndirectMatch) {
                judge = judge.or(((IndirectMatch)match).buildJunction());
            }
        }
        return judge;
    }
}
