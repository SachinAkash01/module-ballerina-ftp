# Testing Instructions for FTP Content Listener

## Prerequisites

1. **SFTP Server**: You need a running SFTP server for integration tests
2. **Test Configuration**: Configure SFTP server details
3. **Test Directories**: Create required directories on the SFTP server

## Setup Steps

### 1. Configure SFTP Server

The tests are currently disabled (`enable: false`) because they require an SFTP server. You have two options:

#### Option A: Use Docker SFTP Server (Recommended for Development)

```bash
# Run SFTP server using Docker
docker run -p 21213:22 -d \
  -e SFTP_USERS='testuser:testpass:1001' \
  -v /tmp/sftp:/home/testuser/upload \
  atmoz/sftp
```

#### Option B: Use Existing SFTP Server

Configure your existing SFTP server details in test configuration.

### 2. Update Test Configuration

In your test file (or create a `Config.toml` for tests), define:

```ballerina
// Add to listener_endpoint_test.bal or create a new config file

final AuthConfiguration sftpConfig = {
    credentials: {
        username: "testuser",
        password: "testpass"
    }
};

final Client sftpClientEp = check new ({
    protocol: SFTP,
    host: "127.0.0.1",
    port: 21213,
    auth: sftpConfig
});
```

### 3. Create Test Directories

Create the following directories on your SFTP server under the base path:

```
/content_listener_tests/
├── binary/
├── text/
├── json/
├── json_typed/
├── xml/
├── csv_array/
├── csv_record/
├── text_minimal/
├── caller_in/
├── caller_out/
└── multi/
```

You can create these using the SFTP client:

```ballerina
@test:BeforeSuite
function setupTestDirectories() returns error? {
    string[] dirs = [
        "/content_listener_tests/binary",
        "/content_listener_tests/text",
        "/content_listener_tests/json",
        "/content_listener_tests/json_typed",
        "/content_listener_tests/xml",
        "/content_listener_tests/csv_array",
        "/content_listener_tests/csv_record",
        "/content_listener_tests/text_minimal",
        "/content_listener_tests/caller_in",
        "/content_listener_tests/caller_out",
        "/content_listener_tests/multi"
    ];
    
    foreach string dir in dirs {
        // Try to create directory (may fail if exists - that's ok)
        _ = check sftpClientEp->mkdir(dir);
    }
}
```

### 4. Enable Tests

Once SFTP server is configured and directories created:

1. Remove `enable: false` from `@test:Config` annotations
2. Or change to `enable: true`

Example:
```ballerina
@test:Config {enable: true}  // Enable the test
public function testOnFileBinaryContent() returns error? {
    // ... test code
}
```

### 5. Run Tests

```bash
# Run all tests
bal test

# Run specific test
bal test --tests testOnFileTextContent

# Run with debug logging
bal test --debug
```

## Troubleshooting

### Test Failures: "callback was not received"

**Possible causes:**
1. SFTP server not running
2. Wrong credentials
3. Directories don't exist
4. Firewall blocking connection
5. Polling interval too short

**Solutions:**
- Increase timeout in tests
- Check SFTP server logs
- Verify credentials
- Test manual SFTP connection: `sftp -P 21213 testuser@127.0.0.1`

### Type Conversion Errors

**Error**: `incompatible types: expected 'UserRecord', found 'map<anydata>'`

**Solution**: 
- This might indicate the JSON conversion needs improvement
- Check that JSON structure matches record definition exactly
- Field names must match case-sensitively

### File Already Exists Errors

**Solution**:
- Clean up test files between runs
- Add better cleanup in `@test:AfterEach`

```ballerina
@test:AfterEach
function cleanup() returns error? {
    // Delete any remaining test files
    _ = check sftpClientEp->delete("/content_listener_tests/binary/test1.dat");
    // ... repeat for all test paths
}
```

### Stream Already Closed Errors

**Solution**:
- Don't reuse file streams
- Create fresh stream for each upload

## Unit Testing (Without SFTP Server)

To test the converter logic without an SFTP server:

```ballerina
@test:Config {}
function testContentConverters() returns error? {
    // Test text conversion
    byte[] textBytes = "Hello World".toBytes();
    string text = check string:fromBytes(textBytes);
    test:assertEquals(text, "Hello World");

    // Test JSON conversion
    byte[] jsonBytes = "{\"name\":\"test\"}".toBytes();
    json jsonContent = check value:fromJsonString(check string:fromBytes(jsonBytes));
    test:assertEquals(jsonContent.name, "test");
}
```

## Next Steps

1. ✅ SFTP server setup
2. ✅ Test directory creation
3. ✅ Configuration update
4. ✅ Enable one simple test (e.g., `testOnFileTextContent`)
5. ✅ Verify it passes
6. ✅ Enable remaining tests one by one
7. ✅ Debug any failures

## Test Execution Order

Test in this order for best debugging:

1. `testOnFileTextContent` - Simplest text file
2. `testOnFileBinaryContent` - Binary handling
3. `testOnFileJsonContent` - JSON parsing
4. `testOnFileJsonTypedRecord` - Type conversion
5. `testOnFileXmlContent` - XML parsing
6. `testOnFileCsvStringArray` - CSV as arrays
7. `testOnFileCsvTypedRecord` - CSV as typed records
8. `testOnFileTextMinimalSignature` - Parameter variations
9. `testOnFileWithCaller` - Caller operations
10. `testMultipleFilesConcurrent` - Concurrency
