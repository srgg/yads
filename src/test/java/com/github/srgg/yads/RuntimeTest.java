/*
 * ⁣​
 * YADS
 * ⁣⁣
 * Copyright (C) 2015 - 2016 srgg
 * ⁣⁣
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ​⁣
 */
package com.github.srgg.yads;

import com.github.srgg.yads.api.messages.Message;
import com.github.srgg.yads.api.messages.StorageOperationRequest;
import com.github.srgg.yads.api.messages.StorageOperationResponse;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.*;
import com.github.srgg.yads.impl.runtime.LocalRuntime;
import org.junit.runners.MethodSorters;

import static net.javacrumbs.jsonunit.JsonAssert.assertJsonEquals;
import static net.javacrumbs.jsonunit.JsonAssert.when;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author Sergey Galkin <srggal at gmail dot com>
 */
@FixMethodOrder(MethodSorters.JVM)
public class RuntimeTest {
    @Rule
    public FancyTestWatcher watcher = new FancyTestWatcher();

    private LocalRuntime rt;


    @BeforeClass
    public static void setupTolerantJSON() {
        JsonUnitInitializer.initialize();
    }

    @Before
    public void initialize() throws Exception {
        rt = new LocalRuntime();
        rt.start();
    }

    @After
    public void shutdown() throws Exception {
        rt.stop();
    }

    @Ignore
    @Test(timeout = 10000)
    public void checkNotGentleChainCreation() throws Exception {
        rt.createStorageNode("storage-1");
        rt.createStorageNode("storage-2");
        rt.createStorageNode("storage-3");
        rt.createMasterNode("master-1");

        rt.waitForCompleteChain();
        Thread.sleep(200);
    }

    @Test(timeout = 10000)
    public void checkGentleChainCreation() throws Exception {

        rt.createMasterNode("master-1");

        rt.createStorageNode("storage-1");
        rt.waitForCompleteChain();
        Thread.sleep(100);

        verifyStorageStates("{" +
                "storage-1:{" +
                "role:          ['Head', 'Tail']," +
                "state:         'RUNNING'," +
                "prevNode:      null," +
                "nextNode:      null," +
                "lastUpdatedBy: 'master-1'" +
                "}" +
            "}"
        );


        rt.createStorageNode("storage-2");
        rt.waitForCompleteChain();
        Thread.sleep(300);

        verifyStorageStates("{" +
                "storage-1:{" +
                    "role:          ['Head']," +
                    "state:         'RUNNING'," +
                    "prevNode:      null," +
                    "nextNode:      'storage-2'," +
                    "lastUpdatedBy: 'master-1'" +
                "}," +
                "storage-2:{" +
                    "role:          ['Tail']," +
                    "state:         'RUNNING'," +
                    "prevNode:      'storage-1'," +
                    "nextNode:      null," +
                    "lastUpdatedBy: 'master-1'" +
                "}" +
            "}"
        );


        rt.createStorageNode("storage-3");
        rt.waitForCompleteChain();
        Thread.sleep(400);
        verifyStorageStates("{" +
                "storage-1:{" +
                    "role:          ['Head']," +
                    "state:         'RUNNING'," +
                    "prevNode:      null," +
                    "nextNode:      'storage-2'," +
                    "lastUpdatedBy: 'master-1'" +
                "}," +
                "storage-2:{" +
                    "role:          ['Middle']," +
                    "state:         'RUNNING'," +
                    "prevNode:      'storage-1'," +
                    "nextNode:      'storage-3'," +
                    "lastUpdatedBy: 'master-1'" +
                "}," +
                "storage-3: {" +
                    "role:          ['Tail']," +
                    "state:         'RUNNING'," +
                    "prevNode:      'storage-2'," +
                    "nextNode:      null," +
                    "lastUpdatedBy: 'master-1'" +
                "}" +
            "}"
        );
    }

    @Test(timeout = 60000)
    public void checkGentleReplication() throws Exception {
        rt.createMasterNode("master-21");

        rt.createStorageNode("storage-21");
        rt.waitForRunningChain();


        final Message m1 = rt.performRequest("storage-21", new StorageOperationRequest.Builder()
                .setType(StorageOperationRequest.OperationType.Put)
                .setKey("1")
                .setObject("42")
        );

        assertThat(m1, TestUtils.message(StorageOperationResponse.class,
                "{sender: 'storage-21', object: null}")
        );

        final StorageOperationRequest.Builder getValueBuilder = new StorageOperationRequest.Builder()
                .setType(StorageOperationRequest.OperationType.Get)
                .setKey("1");

        // read value to be sure in 100% it has been stored properly
        final StorageOperationResponse m2 = rt.performRequest("storage-21", getValueBuilder);

        assertEquals("42", m2.getObject());

        // -- spinnup storage-2 and check that it'll be recovered properly
        rt.createStorageNode("storage-22");
        rt.waitForRunningChain();

        final StorageOperationResponse m3 = rt.performRequest("storage-22", getValueBuilder);
        assertEquals("42", m3.getObject());

        // -- spinnup storage-3 and check that it'll be recovered properly
        rt.createStorageNode("storage-23");
        rt.waitForRunningChain();

        final StorageOperationResponse m4 = rt.performRequest("storage-23", getValueBuilder);
        assertEquals("42", m4.getObject());
    }

    private void verifyStorageStates(String expected) {
        assertJsonEquals(expected,
                rt.getAllStorageStates(),
                when(Option.IGNORING_EXTRA_FIELDS)
        );
    }
}
