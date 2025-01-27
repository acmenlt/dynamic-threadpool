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

package cn.hippo4j.config.notify;

import cn.hippo4j.common.toolkit.MapUtil;
import cn.hippo4j.config.event.AbstractEvent;
import cn.hippo4j.config.event.AbstractSlowEvent;
import cn.hippo4j.config.notify.listener.AbstractSmartSubscriber;
import cn.hippo4j.config.notify.listener.AbstractSubscriber;
import cn.hippo4j.config.toolkit.ClassUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Unified event notify center.
 */
@Slf4j
public class NotifyCenter {

    private static final NotifyCenter INSTANCE = new NotifyCenter();

    public static final int RING_BUFFER_SIZE = 16384;

    public static final int SHARE_BUFFER_SIZE = 1024;

    private DefaultSharePublisher sharePublisher;

    private static final Supplier<EventPublisher> publisherSupplier = DefaultPublisher::new;

    private static BiFunction<Class<? extends AbstractEvent>, Integer, EventPublisher> publisherFactory;

    private final Map<String, EventPublisher> publisherMap = new ConcurrentHashMap(16);

    static {
        publisherFactory = (cls, buffer) -> {
            try {
                EventPublisher publisher = publisherSupplier.get();
                publisher.init(cls, buffer);
                return publisher;
            } catch (Throwable ex) {
                log.error("Service class newInstance has error: {}", ex);
                throw new RuntimeException(ex);
            }
        };
        INSTANCE.sharePublisher = new DefaultSharePublisher();
        INSTANCE.sharePublisher.init(AbstractSlowEvent.class, SHARE_BUFFER_SIZE);
    }

    public static void registerSubscriber(final AbstractSubscriber consumer) {
        if (consumer instanceof AbstractSmartSubscriber) {
            for (Class<? extends AbstractEvent> subscribeType : ((AbstractSmartSubscriber) consumer).subscribeTypes()) {
                if (ClassUtil.isAssignableFrom(AbstractSlowEvent.class, subscribeType)) {
                    INSTANCE.sharePublisher.addSubscriber(consumer, subscribeType);
                } else {
                    addSubscriber(consumer, subscribeType);
                }
            }
        } else {
            final Class<? extends AbstractEvent> subscribeType = consumer.subscribeType();
            if (ClassUtil.isAssignableFrom(AbstractSlowEvent.class, subscribeType)) {
                INSTANCE.sharePublisher.addSubscriber(consumer, subscribeType);
            } else {
                addSubscriber(consumer, subscribeType);
            }
        }
    }

    private static void addSubscriber(final AbstractSubscriber consumer, Class<? extends AbstractEvent> subscribeType) {
        final String topic = ClassUtil.getCanonicalName(subscribeType);
        synchronized (NotifyCenter.class) {
            MapUtil.computeIfAbsent(INSTANCE.publisherMap, topic, publisherFactory, subscribeType, RING_BUFFER_SIZE);
        }
        EventPublisher publisher = INSTANCE.publisherMap.get(topic);
        publisher.addSubscriber(consumer);
    }

    public static boolean publishEvent(final AbstractEvent event) {
        try {
            return publishEvent(event.getClass(), event);
        } catch (Throwable ex) {
            log.error("There was an exception to the message publishing: {}", ex);
            return false;
        }
    }

    private static boolean publishEvent(final Class<? extends AbstractEvent> eventType, final AbstractEvent event) {
        if (ClassUtil.isAssignableFrom(AbstractSlowEvent.class, eventType)) {
            return INSTANCE.sharePublisher.publish(event);
        }
        final String topic = ClassUtil.getCanonicalName(eventType);
        EventPublisher publisher = INSTANCE.publisherMap.get(topic);
        if (publisher != null) {
            return publisher.publish(event);
        }
        log.warn("There are no [{}] publishers for this event, please register", topic);
        return false;
    }

    public static EventPublisher registerToPublisher(final Class<? extends AbstractEvent> eventType, final int queueMaxSize) {
        if (ClassUtil.isAssignableFrom(AbstractSlowEvent.class, eventType)) {
            return INSTANCE.sharePublisher;
        }
        final String topic = ClassUtil.getCanonicalName(eventType);
        synchronized (NotifyCenter.class) {
            MapUtil.computeIfAbsent(INSTANCE.publisherMap, topic, publisherFactory, eventType, queueMaxSize);
        }
        return INSTANCE.publisherMap.get(topic);
    }
}
