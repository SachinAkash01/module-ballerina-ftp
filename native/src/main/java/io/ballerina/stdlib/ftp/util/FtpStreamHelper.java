/*
 * Copyright (c) 2025 WSO2 LLC. (http://www.wso2.com) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.stdlib.ftp.util;

import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.PredefinedTypes;
import io.ballerina.runtime.api.types.StreamType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.types.TypeTags;
import io.ballerina.runtime.api.values.BObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Helper for creating Ballerina streams backed by Java {@link InputStream}s.
 */
public final class FtpStreamHelper {

    private static final Logger log = LoggerFactory.getLogger(FtpStreamHelper.class);

    private FtpStreamHelper() {
        // utility class
    }

    /**
     * Creates a Ballerina stream value backed by the provided content {@link InputStream}.
     *
     * @param content         Input stream with the file content
     * @param streamValueType Constraint type of the stream
     * @param laxDataBinding  Whether lax data binding is enabled (propagated to stream iterators)
     * @return Ballerina stream value or ftp:Error
     */
    public static Object createStreamWithContent(InputStream content, Type streamValueType, boolean laxDataBinding) {
        try {
            String streamObjectName = resolveStreamObjectName(streamValueType);
            BObject streamObject = ValueCreator.createObjectValue(ModuleUtils.getModule(), streamObjectName, null, null);
            streamObject.addNativeData(FtpConstants.NATIVE_INPUT_STREAM, content);
            streamObject.addNativeData(FtpConstants.NATIVE_LAX_DATABINDING, laxDataBinding);
            streamObject.addNativeData(FtpConstants.NATIVE_STREAM_VALUE_TYPE, streamValueType);
            StreamType streamType = TypeCreator.createStreamType(streamValueType,
                    TypeCreator.createUnionType(PredefinedTypes.TYPE_ERROR, PredefinedTypes.TYPE_NULL));
            return ValueCreator.createStreamValue(streamType, streamObject);
        } catch (Exception e) {
            log.error("Failed to create stream with content", e);
            return FtpUtil.createError(FtpConstants.ERR_CREATE_STREAM, e, FtpConstants.FTP_ERROR);
        }
    }

    private static String resolveStreamObjectName(Type streamValueType) {
        if (streamValueType.getTag() == TypeTags.ARRAY_TAG) {
            ArrayType arrayType = (ArrayType) streamValueType;
            if (arrayType.getElementType().getTag() == TypeTags.BYTE_TAG) {
                return "ContentByteStream";
            }
            return "ContentCsvStringArrayStream";
        }
        return "ContentCsvRecordStream";
    }
}
