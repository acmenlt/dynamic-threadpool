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

package cn.hippo4j.common.toolkit;

import cn.hippo4j.common.api.JsonFacade;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import lombok.SneakyThrows;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Jackson util.
 */
public class JacksonHandler implements JsonFacade {

    private static ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.enable(JsonGenerator.Feature.IGNORE_UNKNOWN);
        String dateTimeFormat = "yyyy-MM-dd HH:mm:ss";
        MAPPER.setDateFormat(new SimpleDateFormat(dateTimeFormat));
        MAPPER.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        MAPPER.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
    }

    @Override
    @SneakyThrows
    public String toJSONString(Object object) {
        return MAPPER.writeValueAsString(object);
    }

    @Override
    @SneakyThrows
    public <T> T parseObject(String text, Class<T> clazz) {
        JavaType javaType = MAPPER.getTypeFactory().constructType(clazz);
        return MAPPER.readValue(text, javaType);
    }

    @Override
    @SneakyThrows
    public <T> T parseObject(String text, TypeReference<T> valueTypeRef) {
        return MAPPER.readValue(text, valueTypeRef);
    }

    @Override
    @SneakyThrows
    public <T> List<T> parseArray(String text, Class<T> clazz) {
        CollectionType collectionType = MAPPER.getTypeFactory().constructCollectionType(ArrayList.class, clazz);
        return MAPPER.readValue(text, collectionType);
    }

    @Override
    public boolean isJson(String text) {
        try {
            MAPPER.readTree(text);
            return true;
        } catch (JsonProcessingException ignored) {
        }
        return false;
    }
}
