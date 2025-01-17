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
package com.ab.aspect.internal.procedure

import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformInvocation
import com.google.common.io.ByteStreams
import com.ab.aspect.internal.AJXUtils
import com.ab.aspect.internal.cache.VariantCache
import com.ab.aspect.internal.concurrent.BatchTaskScheduler
import com.ab.aspect.internal.concurrent.ITask
import org.gradle.api.Project

import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * class description here
 * @author simon
 * @version 1.0.0
 * @since 2018-04-23
 */
class CacheAspectFilesProcedure extends AbsProcedure {
    CacheAspectFilesProcedure(Project project, VariantCache variantCache, TransformInvocation transformInvocation) {
        super(project, variantCache, transformInvocation)
    }

    @Override
    boolean doWorkContinuously() {
        project.logger.debug("~~~~~~~~~~~~~~~~~~~~cache aspect files")
        //缓存aspect文件
        BatchTaskScheduler batchTaskScheduler = new BatchTaskScheduler()

        transformInvocation.inputs.each { TransformInput input ->
            input.directoryInputs.each { DirectoryInput dirInput ->
//                    collect aspect file
                batchTaskScheduler.addTask(new ITask() {
                    @Override
                    Object call() throws Exception {
                        dirInput.file.eachFileRecurse { File item ->
                            if (AJXUtils.isAspectClass(item)) {
                                project.logger.warn("[ajx] collect aspect file:${item.absolutePath}")
                                String path = item.absolutePath
                                String subPath = path.substring(dirInput.file.absolutePath.length())
                                File cacheFile = new File(variantCache.aspectPath + subPath)
                                variantCache.add(item, cacheFile)
                            }
                        }

                        return null
                    }
                })
            }

            input.jarInputs.each { JarInput jarInput ->
//                    collect aspect file
                if (!jarInput.file.exists()) {
                    return
                }
                batchTaskScheduler.addTask(new ITask() {
                    @Override
                    Object call() throws Exception {
                        JarFile jarFile = new JarFile(jarInput.file)
                        Enumeration<JarEntry> entries = jarFile.entries()
                        while (entries.hasMoreElements()) {
                            JarEntry jarEntry = entries.nextElement()
                            String entryName = jarEntry.getName()
                            if (!jarEntry.isDirectory() && AJXUtils.isClassFile(entryName)) {
                                byte[] bytes = ByteStreams.toByteArray(jarFile.getInputStream(jarEntry))
                                if (AJXUtils.isAspectClass(bytes)) {
                                    project.logger.warn("[ajx] collect aspect file[${entryName}] from JAR:${jarFile}")
                                    File cacheFile = new File(variantCache.aspectPath + File.separator + entryName)
                                    variantCache.add(bytes, cacheFile)
                                }
                            }
                        }

                        jarFile.close()

                        return null
                    }
                })
            }
        }

        batchTaskScheduler.execute()
        batchTaskScheduler.shutDown()

        if (AJXUtils.countOfFiles(variantCache.aspectDir) == 0) {
            AJXUtils.doWorkWithNoAspectj(transformInvocation)
            return false
        }

        return true
    }
}
