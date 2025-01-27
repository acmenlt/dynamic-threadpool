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

package cn.hippo4j.springboot.starter.config;

import cn.hippo4j.common.constant.Constants;
import cn.hippo4j.threadpool.dynamic.api.BootstrapPropertiesInterface;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bootstrap properties.
 * //动态线程池服务端的地址
 * spring.dynamic.thread-pool.server-addr=http://localhost:6691
 * //客户端namespace
 * spring.dynamic.thread-pool.namespace=prescription
 * //itemId
 * spring.dynamic.thread-pool.item-id=dynamic-threadpool-example
 * //访问服务端时需要的用户名和密码
 * spring.dynamic.thread-pool.username=admin
 * spring.dynamic.thread-pool.password=123456
 */
@Slf4j
@Getter
@Setter
@ConfigurationProperties(prefix = Constants.CONFIGURATION_PROPERTIES_PREFIX)
/**
 * 既然是要使用 BootstrapProperties 来封装配置类中的信息，那么 BootstrapProperties 类的对象肯定要被
 * SpringBoot 容器来管理，所以接下来我要定义一个配置类，在这个配置类中，
 * 把 BootstrapProperties 的对象交给 SpringBoot 的容器来管理。
 * 这个配置类我也定义好了，DynamicThreadPoolAutoConfiguration
 */
public class BootstrapProperties implements BootstrapPropertiesInterface {

    /**
     * Username
     */
    private String username;

    /**
     * Password
     */
    private String password;

    /**
     * Server address
     */
    private String serverAddr;

    /**
     * Netty server port
     *  //netty服务器的端口号，这个是可配置的
     *     //在hippo4j框架，提供了两种通信方式，一种是http，一种就是netty
     *     //在该框架中默认使用的是http，所以我就不引入netty了
     */
    private String nettyServerPort;

    /**
     * Report type
     *  //客户端上报给服务端线程池历史信息的方法，这个也可以使用netty的方式上报
     *     //我仍然使用内部默认的http了，不引入netty
     */
    private String reportType;

    /**
     * Namespace
     * //命名空间
     */
    private String namespace;

    /**
     * Item id
     * //项目Id
     */
    private String itemId;

    /**
     * Whether to enable dynamic thread pool
     * //是否启动动态线程池
     */
    private Boolean enable = true;

    /**
     * Print dynamic thread pool banner
     * //是否在控制台打印hippo4j的启动图案
     */
    private Boolean banner = true;

    /**
     * Thread pool monitoring related configuration.
     */
    private MonitorProperties monitor = new MonitorProperties();

    /***
     * Latest use {@link MonitorProperties#getEnable()}
     */
    @Deprecated
    private Boolean collect = Boolean.TRUE;

    /**
     * Latest use {@link MonitorProperties#getCollectTypes()}
     */
    @Deprecated
    private String collectType;

    /**
     * Latest use {@link MonitorProperties#getInitialDelay()}
     */
    @Deprecated
    private Long initialDelay = 10000L;

    /**
     * Latest use {@link MonitorProperties#getCollectInterval()}
     */
    @Deprecated
    private Long collectInterval = 5000L;

    /**
     * Latest use {@link MonitorProperties#getTaskBufferSize()}
     */
    @Deprecated
    private Integer taskBufferSize = 4096;
}
