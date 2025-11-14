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
import io.ballerina.stdlib.ftp.exception.BallerinaFtpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

import static io.ballerina.stdlib.ftp.util.FtpConstants.ERR_CREATE_STREAM;
import static io.ballerina.stdlib.ftp.util.FtpConstants.NATIVE_INPUT_STREAM;
import static io.ballerina.stdlib.ftp.util.FtpConstants.NATIVE_LAX_DATABINDING;
import static io.ballerina.stdlib.ftp.util.FtpConstants.NATIVE_STREAM_VALUE_TYPE;

/**
 * Shared helpers for constructing Ballerina streams backed by {@link InputStream} instances.
 */
public final class FtpStreamUtils {

    private static final Logger log = LoggerFactory.getLogger(FtpStreamUtils.class);

    private FtpStreamUtils() {
    }

    /**
     * Creates a Ballerina {@code stream<T, error?>} backed by the provided {@link InputStream}.
     *
     * @param content         input stream with file content. Ownership of the stream is transferred to the caller.
     * @param streamValueType constrained type {@code T} of the stream.
     * @param laxDataBinding  whether lax data binding should be enabled when parsing structured content.
     * @return Ballerina stream value or {@link io.ballerina.runtime.api.values.BError} if creation fails.
     */
    public static Object createStreamWithContent(InputStream content, Type streamValueType, boolean laxDataBinding) {
        try {
            String objectTypeName = resolveStreamObjectName(streamValueType);
            BObject streamObject = ValueCreator.createObjectValue(ModuleUtils.getModule(), objectTypeName, null, null);
            streamObject.addNativeData(NATIVE_INPUT_STREAM, content);
            streamObject.addNativeData(NATIVE_LAX_DATABINDING, laxDataBinding);
            streamObject.addNativeData(NATIVE_STREAM_VALUE_TYPE, streamValueType);

            StreamType streamType = TypeCreator.createStreamType(
                    streamValueType,
                    TypeCreator.createUnionType(PredefinedTypes.TYPE_ERROR, PredefinedTypes.TYPE_NULL));
            return ValueCreator.createStreamValue(streamType, streamObject);
        } catch (Throwable throwable) {
            log.error("Failed to create stream with content", throwable);
            return FtpUtil.createError(ERR_CREATE_STREAM, throwable, FtpConstants.FTP_ERROR);
        }
    }

    private static String resolveStreamObjectName(Type streamValueType) throws BallerinaFtpException {
        if (streamValueType.getTag() == TypeTags.ARRAY_TAG) {
            Type elementType = ((ArrayType) streamValueType).getElementType();
            if (elementType.getTag() == TypeTags.BYTE_TAG) {
                return "ContentByteStream";
            }
            return "ContentCsvStringArrayStream";
        } else if (streamValueType.getTag() == TypeTags.RECORD_TYPE_TAG) {
            return "ContentCsvRecordStream";
        }
        throw new BallerinaFtpException("Unsupported stream constrained type: " + streamValueType);
    }
}
