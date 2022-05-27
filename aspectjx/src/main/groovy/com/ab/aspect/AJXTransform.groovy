/*
 * Copyright 2018 firefly1126, Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.gradle_plugin_android_aspectjx
 */
package com.ab.aspect

import com.ab.aspect.internal.procedure.AJXProcedure
import com.ab.aspect.internal.procedure.CacheAspectFilesProcedure
import com.ab.aspect.internal.procedure.CacheInputFilesProcedure
import com.ab.aspect.internal.procedure.CheckAspectJXEnableProcedure
import com.ab.aspect.internal.procedure.DoAspectWorkProcedure
import com.ab.aspect.internal.procedure.OnFinishedProcedure
import com.ab.aspect.internal.procedure.UpdateAspectFilesProcedure
import com.ab.aspect.internal.procedure.UpdateAspectOutputProcedure
import com.ab.aspect.internal.procedure.UpdateInputFilesProcedure
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import com.ab.aspect.internal.cache.AJXCache
import com.ab.aspect.internal.cache.VariantCache
import com.ab.aspect.internal.procedure.*
import org.aspectj.org.eclipse.jdt.internal.compiler.batch.ClasspathJar
import org.gradle.api.Project

/**
 * Aspect处理<br>
 * 自定义transform几个问题需要注意：
 * <ul>
 *     <li>对于多flavor的构建，每个flavor都会执行transform</li>
 *     <li>对于开启gradle daemon的情况（默认开启的，一般也不会去关闭），每次构建都是运行在同一个进程上，
 *     所以要注意到有没有使用到有状态的静态或者单例，如果有的话，需要在构建结束进行处理，否则会影响到后续的构建</li>
 *     <li>增量构建时，要注意是否需要删除之前在outputProvider下已产生的产物</li>
 * </ul>
 * @author simon* @version 1.0.0* @since 2018-03-12
 */
class AJXTransform extends Transform {

    com.ab.aspect.internal.cache.AJXCache ajxCache
    Project project

    AJXTransform(Project proj) {
        project = proj
        // 初始化缓存
        ajxCache = new com.ab.aspect.internal.cache.AJXCache(proj)
    }

    @Override
    String getName() {
        return "ajx"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        //是否支持增量编译
        return true
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        // 每个变种都会执行
        String transformTaskVariantName = transformInvocation.context.getVariantName()
        long cost = System.currentTimeMillis()
        project.logger.warn("ajx[$transformTaskVariantName] transform start...")
        // 之前可能是构建失败，也关闭所有打开的文件
        ClasspathJar.closeAllOpenedArchives()
        com.ab.aspect.internal.cache.VariantCache variantCache = new com.ab.aspect.internal.cache.VariantCache(project, ajxCache, transformTaskVariantName)
        def ajxProcedure = new AJXProcedure(project)
        //check enable
        ajxProcedure.with(new CheckAspectJXEnableProcedure(project, variantCache, transformInvocation))
        def incremental = transformInvocation.incremental
        project.logger.warn("ajx[$transformTaskVariantName] incremental=${incremental}")
        if (incremental) {
            //incremental build
            ajxProcedure
                    .with(new UpdateAspectFilesProcedure(project, variantCache, transformInvocation))
                    .with(new UpdateInputFilesProcedure(project, variantCache, transformInvocation))
                    .with(new UpdateAspectOutputProcedure(project, variantCache, transformInvocation))
        } else {
            //delete output and cache before full build
            transformInvocation.outputProvider.deleteAll()
            //full build
            ajxProcedure
                    .with(new CacheAspectFilesProcedure(project, variantCache, transformInvocation))
                    .with(new CacheInputFilesProcedure(project, variantCache, transformInvocation))
                    .with(new DoAspectWorkProcedure(project, variantCache, transformInvocation))
        }

        ajxProcedure.with(new OnFinishedProcedure(project, variantCache, transformInvocation))

        ajxProcedure.doWorkContinuously()
        // 构建结束后关闭所有打开的文件
        ClasspathJar.closeAllOpenedArchives()
        project.logger.warn("ajx[$transformTaskVariantName] transform finish.spend ${System.currentTimeMillis() - cost}ms")
    }
}
