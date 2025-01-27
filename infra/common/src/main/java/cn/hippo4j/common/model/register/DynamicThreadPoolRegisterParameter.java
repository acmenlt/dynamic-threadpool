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

package cn.hippo4j.common.model.register;

import cn.hippo4j.common.executor.support.BlockingQueueTypeEnum;
import cn.hippo4j.common.executor.support.RejectedPolicyTypeEnum;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.ThreadFactory;

/**
 * Dynamic thread-pool register parameter.
 * 封装动态线程池核心信息的对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DynamicThreadPoolRegisterParameter {

    /**
     * Thread-pool id
     * Empty or empty strings are not allowed, and `+` signs are not allowed
     */
    private String threadPoolId;

    /**
     * Content
     */
    private String content;

    /**
     * Core pool size
     */
    private Integer corePoolSize;

    /**
     * Maximum pool size
     */
    private Integer maximumPoolSize;

    /**
     * Blocking queue type
     */
    private BlockingQueueTypeEnum blockingQueueType;

    /**
     * Capacity
     */
    private Integer capacity;

    /**
     * Keep alive time
     */
    private Long keepAliveTime;

    /**
     * Rejected policy type
     */
    private RejectedPolicyTypeEnum rejectedPolicyType;

    /**
     * Is alarm
     */
    private Boolean isAlarm;

    /**
     * Capacity alarm
     */
    private Integer capacityAlarm;

    /**
     * Active alarm
     */
    @JsonAlias("livenessAlarm")
    private Integer activeAlarm;

    /**
     * Allow core thread timeout
     */
    private Boolean allowCoreThreadTimeOut;

    /**
     * Thread name prefix
     */
    private String threadNamePrefix;

    /**
     * Thread factory
     */
    @JsonIgnore
    private ThreadFactory threadFactory;

    /**
     * Execute timeout
     */
    private Long executeTimeOut;

    public Integer getIsAlarm() {
        return this.isAlarm ? 1 : 0;
    }

    public Integer getAllowCoreThreadTimeOut() {
        return this.allowCoreThreadTimeOut ? 1 : 0;
    }
}
