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

package cn.hippo4j.threadpool.dynamic.api;

import java.util.Map;

/**
 * Bootstrap properties interface.
 */
public interface BootstrapPropertiesInterface {

    /**
     * Get enable.
     */
    default Boolean getEnable() {
        return null;
    }

    /**
     * Get username.
     */
    default String getUsername() {
        return null;
    }

    /**
     * Get password.
     */
    default String getPassword() {
        return null;
    }

    /**
     * Get namespace.
     */
    default String getNamespace() {
        return null;
    }

    /**
     * Get item id.
     */
    default String getItemId() {
        return null;
    }

    /**
     * Get server addr.
     */
    default String getServerAddr() {
        return null;
    }

    /**
     * Get banner.
     */
    default Boolean getBanner() {
        return null;
    }

    /**
     * Get nacos.
     */
    default Map<String, String> getNacos() {
        return null;
    }

    /**
     * Get etcd.
     */
    default Map<String, String> getEtcd() {
        return null;
    }

    /**
     * Get apollo.
     */
    default Map<String, String> getApollo() {
        return null;
    }

    /**
     * Get Zookeeper.
     */
    default Map<String, String> getZookeeper() {
        return null;
    }

    /**
     * Get Polaris.
     */
    default Map<String, Object> getPolaris() {
        return null;
    }

    /**
     * Get consul.
     */
    default Map<String, String> getConsul() {
        return null;
    }
}
