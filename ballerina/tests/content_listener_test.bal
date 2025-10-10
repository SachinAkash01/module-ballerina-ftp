// Copyright (c) 2025 WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 LLC. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/io;
import ballerina/lang.runtime;
import ballerina/test;

// Test data file paths
const string BINARY_TEST_FILE = "tests/resources/datafiles/content_test_data/binary_test.dat";
const string TEXT_TEST_FILE = "tests/resources/datafiles/content_test_data/text_test.txt";
const string JSON_TEST_FILE = "tests/resources/datafiles/content_test_data/json_test.json";
const string XML_TEST_FILE = "tests/resources/datafiles/content_test_data/xml_test.xml";
const string CSV_TEST_FILE = "tests/resources/datafiles/content_test_data/csv_test.csv";

// NOTE: These tests require a running SFTP server. 
// Make sure sftpConfig and sftpClientEp are defined in your test setup
// or import them from listener_endpoint_test.bal

//================================================================================
// Test 1: Binary content with onFile method
//================================================================================

@test:Config {enable: false}  // Disable until SFTP server is configured
public function testOnFileBinaryContent() returns error? {
    boolean callbackReceived = false;
    byte[] receivedContent = [];
    string receivedFileName = "";

    Listener binaryListener = check new ({
        protocol: SFTP,
        host: "127.0.0.1",
        auth: sftpConfig.auth,
        port: 21213,
        pollingInterval: 2,
        path: "/content_listener_tests/binary",
        fileNamePattern: "(.*).dat"
    });

    Service binaryService = service object {
        remote function onFile(byte[] content, FileInfo fileInfo) returns error? {
            receivedContent = content;
            receivedFileName = fileInfo.name;
            callbackReceived = true;
        }
    };

    check binaryListener.attach(binaryService);
    check binaryListener.'start();

    // Upload test file
    stream<io:Block, io:Error?> fileStream = check io:fileReadBlocksAsStream(BINARY_TEST_FILE);
    check (<Client>sftpClientEp)->put("/content_listener_tests/binary/test1.dat", fileStream);

    // Wait for callback
    int timeout = 15;
    while timeout > 0 && !callbackReceived {
        runtime:sleep(1);
        timeout -= 1;
    }

    // Assertions
    test:assertTrue(callbackReceived, "Binary content callback was not received");
    byte[] expectedContent = check io:fileReadBytes(BINARY_TEST_FILE);
    test:assertEquals(receivedContent, expectedContent, "Binary content mismatch");
    test:assertEquals(receivedFileName, "test1.dat", "FileInfo name mismatch");

    // Cleanup
    check (<Client>sftpClientEp)->delete("/content_listener_tests/binary/test1.dat");
    check binaryListener.gracefulStop();
}

//================================================================================
// Test 2: Text content with onFileText method
//================================================================================

@test:Config {enable: false}  // Disable until SFTP server is configured
public function testOnFileTextContent() returns error? {
    boolean callbackReceived = false;
    string receivedContent = "";

    Listener textListener = check new ({
        protocol: SFTP,
        host: "127.0.0.1",
        auth: sftpConfig.auth,
        port: 21213,
        pollingInterval: 2,
        path: "/content_listener_tests/text",
        fileNamePattern: "(.*).txt"
    });

    Service textService = service object {
        remote function onFileText(string content, FileInfo fileInfo) returns error? {
            receivedContent = content;
            callbackReceived = true;
        }
    };

    check textListener.attach(textService);
    check textListener.'start();

    // Upload test file
    stream<io:Block, io:Error?> fileStream = check io:fileReadBlocksAsStream(TEXT_TEST_FILE);
    check (<Client>sftpClientEp)->put("/content_listener_tests/text/test1.txt", fileStream);

    // Wait for callback
    int timeout = 15;
    while timeout > 0 && !callbackReceived {
        runtime:sleep(1);
        timeout -= 1;
    }

    // Assertions
    test:assertTrue(callbackReceived, "Text content callback was not received");
    string expectedContent = check io:fileReadString(TEXT_TEST_FILE);
    test:assertEquals(receivedContent, expectedContent, "Text content mismatch");

    // Cleanup
    check (<Client>sftpClientEp)->delete("/content_listener_tests/text/test1.txt");
    check textListener.gracefulStop();
}

//================================================================================
// Test 3: JSON content with onFileJson method
//================================================================================

@test:Config {enable: false}  // Disable until SFTP server is configured
public function testOnFileJsonContent() returns error? {
    boolean callbackReceived = false;
    json receivedContent = ();

    Listener jsonListener = check new ({
        protocol: SFTP,
        host: "127.0.0.1",
        auth: sftpConfig.auth,
        port: 21213,
        pollingInterval: 2,
        path: "/content_listener_tests/json",
        fileNamePattern: "(.*).json"
    });

    Service jsonService = service object {
        remote function onFileJson(json content, FileInfo fileInfo) returns error? {
            receivedContent = content;
            callbackReceived = true;
        }
    };

    check jsonListener.attach(jsonService);
    check jsonListener.'start();

    // Upload test file
    stream<io:Block, io:Error?> fileStream = check io:fileReadBlocksAsStream(JSON_TEST_FILE);
    check (<Client>sftpClientEp)->put("/content_listener_tests/json/test1.json", fileStream);

    // Wait for callback
    int timeout = 15;
    while timeout > 0 && !callbackReceived {
        runtime:sleep(1);
        timeout -= 1;
    }

    // Assertions
    test:assertTrue(callbackReceived, "JSON content callback was not received");
    json expectedContent = check io:fileReadJson(JSON_TEST_FILE);
    test:assertEquals(receivedContent, expectedContent, "JSON content mismatch");

    // Cleanup
    check (<Client>sftpClientEp)->delete("/content_listener_tests/json/test1.json");
    check jsonListener.gracefulStop();
}

//================================================================================
// Test 4: Typed JSON record with onFileJson method
//================================================================================

type UserRecord record {
    string name;
    int age;
    string email;
    boolean isActive;
    decimal salary;
};

@test:Config {enable: false}  // Disable until SFTP server is configured
public function testOnFileJsonTypedRecord() returns error? {
    boolean callbackReceived = false;
    UserRecord? receivedUser = ();

    Listener jsonTypedListener = check new ({
        protocol: SFTP,
        host: "127.0.0.1",
        auth: sftpConfig.auth,
        port: 21213,
        pollingInterval: 2,
        path: "/content_listener_tests/json_typed",
        fileNamePattern: "(.*).json"
    });

    Service jsonTypedService = service object {
        remote function onFileJson(UserRecord content, FileInfo fileInfo) returns error? {
            receivedUser = content;
            callbackReceived = true;
        }
    };

    check jsonTypedListener.attach(jsonTypedService);
    check jsonTypedListener.'start();

    // Upload test file
    stream<io:Block, io:Error?> fileStream = check io:fileReadBlocksAsStream(JSON_TEST_FILE);
    check (<Client>sftpClientEp)->put("/content_listener_tests/json_typed/test1.json", fileStream);

    // Wait for callback
    int timeout = 15;
    while timeout > 0 && !callbackReceived {
        runtime:sleep(1);
        timeout -= 1;
    }

    // Assertions
    test:assertTrue(callbackReceived, "Typed JSON callback was not received");
    test:assertTrue(receivedUser is UserRecord, "User record should be populated");

    UserRecord user = <UserRecord>receivedUser;
    test:assertEquals(user.name, "Test User", "User name mismatch");
    test:assertEquals(user.age, 30, "User age mismatch");
    test:assertEquals(user.email, "test@example.com", "User email mismatch");
    test:assertEquals(user.isActive, true, "User isActive mismatch");
    test:assertEquals(user.salary, 75000.50d, "User salary mismatch");

    // Cleanup
    check (<Client>sftpClientEp)->delete("/content_listener_tests/json_typed/test1.json");
    check jsonTypedListener.gracefulStop();
}

//================================================================================
// Test 5: XML content with onFileXml method
//================================================================================

@test:Config {enable: false}  // Disable until SFTP server is configured
public function testOnFileXmlContent() returns error? {
    boolean callbackReceived = false;
    xml receivedContent = xml ``;

    Listener xmlListener = check new ({
        protocol: SFTP,
        host: "127.0.0.1",
        auth: sftpConfig.auth,
        port: 21213,
        pollingInterval: 2,
        path: "/content_listener_tests/xml",
        fileNamePattern: "(.*).xml"
    });

    Service xmlService = service object {
        remote function onFileXml(xml content, FileInfo fileInfo) returns error? {
            receivedContent = content;
            callbackReceived = true;
        }
    };

    check xmlListener.attach(xmlService);
    check xmlListener.'start();

    // Upload test file
    stream<io:Block, io:Error?> fileStream = check io:fileReadBlocksAsStream(XML_TEST_FILE);
    check (<Client>sftpClientEp)->put("/content_listener_tests/xml/test1.xml", fileStream);

    // Wait for callback
    int timeout = 15;
    while timeout > 0 && !callbackReceived {
        runtime:sleep(1);
        timeout -= 1;
    }

    // Assertions
    test:assertTrue(callbackReceived, "XML content callback was not received");
    xml expectedContent = check io:fileReadXml(XML_TEST_FILE);
    test:assertEquals(receivedContent, expectedContent, "XML content mismatch");

    // Cleanup
    check (<Client>sftpClientEp)->delete("/content_listener_tests/xml/test1.xml");
    check xmlListener.gracefulStop();
}

//================================================================================
// Test 6: CSV string array with onFileCsv method
//================================================================================

@test:Config {enable: false}  // Disable until SFTP server is configured
public function testOnFileCsvStringArray() returns error? {
    boolean callbackReceived = false;
    string[][] receivedContent = [];

    Listener csvArrayListener = check new ({
        protocol: SFTP,
        host: "127.0.0.1",
        auth: sftpConfig.auth,
        port: 21213,
        pollingInterval: 2,
        path: "/content_listener_tests/csv_array",
        fileNamePattern: "(.*).csv"
    });

    Service csvArrayService = service object {
        remote function onFileCsv(string[][] content, FileInfo fileInfo) returns error? {
            receivedContent = content;
            callbackReceived = true;
        }
    };

    check csvArrayListener.attach(csvArrayService);
    check csvArrayListener.'start();

    // Upload test file
    stream<io:Block, io:Error?> fileStream = check io:fileReadBlocksAsStream(CSV_TEST_FILE);
    check (<Client>sftpClientEp)->put("/content_listener_tests/csv_array/test1.csv", fileStream);

    // Wait for callback
    int timeout = 15;
    while timeout > 0 && !callbackReceived {
        runtime:sleep(1);
        timeout -= 1;
    }

    // Assertions
    test:assertTrue(callbackReceived, "CSV array callback was not received");
    test:assertEquals(receivedContent.length(), 4, "Should have 4 CSV rows");
    test:assertEquals(receivedContent[0], ["name", "age", "salary", "active"], "CSV header mismatch");
    test:assertEquals(receivedContent[1], ["Alice Smith", "28", "55000.75", "true"], "CSV row 1 mismatch");

    // Cleanup
    check (<Client>sftpClientEp)->delete("/content_listener_tests/csv_array/test1.csv");
    check csvArrayListener.gracefulStop();
}

//================================================================================
// Test 7: CSV typed record array with onFileCsv method
//================================================================================

type EmployeeRecord record {
    string name;
    int age;
    decimal salary;
    boolean active;
};

@test:Config {enable: false}  // Disable until SFTP server is configured
public function testOnFileCsvTypedRecord() returns error? {
    boolean callbackReceived = false;
    EmployeeRecord[] receivedEmployees = [];

    Listener csvRecordListener = check new ({
        protocol: SFTP,
        host: "127.0.0.1",
        auth: sftpConfig.auth,
        port: 21213,
        pollingInterval: 2,
        path: "/content_listener_tests/csv_record",
        fileNamePattern: "(.*).csv"
    });

    Service csvRecordService = service object {
        remote function onFileCsv(EmployeeRecord[] content, FileInfo fileInfo) returns error? {
            receivedEmployees = content;
            callbackReceived = true;
        }
    };

    check csvRecordListener.attach(csvRecordService);
    check csvRecordListener.'start();

    // Upload test file
    stream<io:Block, io:Error?> fileStream = check io:fileReadBlocksAsStream(CSV_TEST_FILE);
    check (<Client>sftpClientEp)->put("/content_listener_tests/csv_record/test1.csv", fileStream);

    // Wait for callback
    int timeout = 15;
    while timeout > 0 && !callbackReceived {
        runtime:sleep(1);
        timeout -= 1;
    }

    // Assertions
    test:assertTrue(callbackReceived, "CSV record callback was not received");
    test:assertEquals(receivedEmployees.length(), 3, "Should have 3 employee records");

    EmployeeRecord emp1 = receivedEmployees[0];
    test:assertEquals(emp1.name, "Alice Smith");
    test:assertEquals(emp1.age, 28);
    test:assertEquals(emp1.salary, 55000.75d);
    test:assertEquals(emp1.active, true);

    // Cleanup
    check (<Client>sftpClientEp)->delete("/content_listener_tests/csv_record/test1.csv");
    check csvRecordListener.gracefulStop();
}

//================================================================================
// Test 8: Minimal signature (content only)
//================================================================================

@test:Config {enable: false}  // Disable until SFTP server is configured
public function testOnFileTextMinimalSignature() returns error? {
    boolean callbackReceived = false;
    string receivedContent = "";

    Listener minimalListener = check new ({
        protocol: SFTP,
        host: "127.0.0.1",
        auth: sftpConfig.auth,
        port: 21213,
        pollingInterval: 2,
        path: "/content_listener_tests/text_minimal",
        fileNamePattern: "(.*).txt"
    });

    Service minimalService = service object {
        remote function onFileText(string content) returns error? {
            receivedContent = content;
            callbackReceived = true;
        }
    };

    check minimalListener.attach(minimalService);
    check minimalListener.'start();

    // Upload test file
    stream<io:Block, io:Error?> fileStream = check io:fileReadBlocksAsStream(TEXT_TEST_FILE);
    check (<Client>sftpClientEp)->put("/content_listener_tests/text_minimal/test1.txt", fileStream);

    // Wait for callback
    int timeout = 15;
    while timeout > 0 && !callbackReceived {
        runtime:sleep(1);
        timeout -= 1;
    }

    // Assertions
    test:assertTrue(callbackReceived, "Minimal text callback was not received");
    string expectedContent = check io:fileReadString(TEXT_TEST_FILE);
    test:assertEquals(receivedContent, expectedContent, "Minimal text content mismatch");

    // Cleanup
    check (<Client>sftpClientEp)->delete("/content_listener_tests/text_minimal/test1.txt");
    check minimalListener.gracefulStop();
}

//================================================================================
// Test 9: Caller operations (rename and delete)
//================================================================================

@test:Config {enable: false}  // Disable until SFTP server is configured
public function testOnFileWithCaller() returns error? {
    boolean callbackReceived = false;
    boolean callerOperationsSuccess = false;

    Listener callerListener = check new ({
        protocol: SFTP,
        host: "127.0.0.1",
        auth: sftpConfig.auth,
        port: 21213,
        pollingInterval: 2,
        path: "/content_listener_tests/caller_in",
        fileNamePattern: "(.*).dat"
    });

    Service callerService = service object {
        remote function onFile(byte[] content, FileInfo fileInfo, Caller caller) returns error? {
            string newPath = "/content_listener_tests/caller_out/" + fileInfo.name;
            check caller->rename(fileInfo.path, newPath);
            check caller->delete(newPath);
            callerOperationsSuccess = true;
            callbackReceived = true;
        }
    };

    check callerListener.attach(callerService);
    check callerListener.'start();

    // Upload test file
    stream<io:Block, io:Error?> fileStream = check io:fileReadBlocksAsStream(BINARY_TEST_FILE);
    check (<Client>sftpClientEp)->put("/content_listener_tests/caller_in/test1.dat", fileStream);

    // Wait for callback
    int timeout = 15;
    while timeout > 0 && !callbackReceived {
        runtime:sleep(1);
        timeout -= 1;
    }

    // Assertions
    test:assertTrue(callbackReceived, "Caller test callback was not received");
    test:assertTrue(callerOperationsSuccess, "Caller operations (rename/delete) failed");

    // Wait for file operations to complete
    runtime:sleep(2);

    // Verify file was deleted from both locations
    stream<byte[] & readonly, io:Error?>|Error resultIn = (<Client>sftpClientEp)->get("/content_listener_tests/caller_in/test1.dat");
    test:assertTrue(resultIn is Error, "File should not exist in input directory");
    
    stream<byte[] & readonly, io:Error?>|Error resultOut = (<Client>sftpClientEp)->get("/content_listener_tests/caller_out/test1.dat");
    test:assertTrue(resultOut is Error, "File should not exist in output directory after delete");

    // Cleanup
    check callerListener.gracefulStop();
}

//================================================================================
// Test 10: Multiple files processed concurrently
//================================================================================

@test:Config {enable: false}  // Disable until SFTP server is configured
public function testMultipleFilesConcurrent() returns error? {
    int fileCount = 0;
    string[] fileNames = [];

    Listener multiListener = check new ({
        protocol: SFTP,
        host: "127.0.0.1",
        auth: sftpConfig.auth,
        port: 21213,
        pollingInterval: 2,
        path: "/content_listener_tests/multi",
        fileNamePattern: "(.*).txt"
    });

    Service multiService = service object {
        remote function onFileText(string content, FileInfo fileInfo) returns error? {
            lock {
                fileCount += 1;
                fileNames.push(fileInfo.name);
            }
        }
    };

    check multiListener.attach(multiService);
    check multiListener.'start();

    // Upload multiple test files
    stream<io:Block, io:Error?> fileStream1 = check io:fileReadBlocksAsStream(TEXT_TEST_FILE);
    check (<Client>sftpClientEp)->put("/content_listener_tests/multi/file1.txt", fileStream1);

    stream<io:Block, io:Error?> fileStream2 = check io:fileReadBlocksAsStream(TEXT_TEST_FILE);
    check (<Client>sftpClientEp)->put("/content_listener_tests/multi/file2.txt", fileStream2);

    stream<io:Block, io:Error?> fileStream3 = check io:fileReadBlocksAsStream(TEXT_TEST_FILE);
    check (<Client>sftpClientEp)->put("/content_listener_tests/multi/file3.txt", fileStream3);

    // Wait for all callbacks
    int timeout = 20;
    while timeout > 0 && fileCount < 3 {
        runtime:sleep(1);
        timeout -= 1;
    }

    // Assertions
    test:assertEquals(fileCount, 3, "Should process exactly 3 files");
    test:assertEquals(fileNames.length(), 3, "Should have 3 file names");

    boolean hasFile1 = fileNames.indexOf("file1.txt") is int;
    boolean hasFile2 = fileNames.indexOf("file2.txt") is int;
    boolean hasFile3 = fileNames.indexOf("file3.txt") is int;

    test:assertTrue(hasFile1, "file1.txt should be processed");
    test:assertTrue(hasFile2, "file2.txt should be processed");
    test:assertTrue(hasFile3, "file3.txt should be processed");

    // Cleanup
    check (<Client>sftpClientEp)->delete("/content_listener_tests/multi/file1.txt");
    check (<Client>sftpClientEp)->delete("/content_listener_tests/multi/file2.txt");
    check (<Client>sftpClientEp)->delete("/content_listener_tests/multi/file3.txt");
    check multiListener.gracefulStop();
}
