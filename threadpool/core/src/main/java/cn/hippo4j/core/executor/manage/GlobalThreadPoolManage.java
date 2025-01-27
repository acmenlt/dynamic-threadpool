/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.hippo4j.core.executor.manage;

import cn.hippo4j.common.model.register.DynamicThreadPoolRegisterWrapper;
import cn.hippo4j.core.config.ApplicationContextHolder;
import cn.hippo4j.core.executor.support.service.DynamicThreadPoolService;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Global thread-pool manage.
 */
public class GlobalThreadPoolManage {

    /**
     * Dynamically register thread pool records and notification records.
     *
     * @param registerWrapper register wrapper
     */
    public static ThreadPoolExecutor dynamicRegister(DynamicThreadPoolRegisterWrapper registerWrapper) {
        //这里从ApplicationContext中得到了DynamicThreadPoolService对象
        //ApplicationContextHolder其实就是SpringBoot的ApplicationContext
        DynamicThreadPoolService dynamicThreadPoolService = ApplicationContextHolder.getBean(DynamicThreadPoolService.class);
        //DynamicThreadPoolService对象把动态线程池信息注册到服务端
        return dynamicThreadPoolService.registerDynamicThreadPool(registerWrapper);
    }
}
