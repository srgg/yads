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

import net.javacrumbs.jsonunit.core.Option;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import com.github.srgg.yads.impl.runtime.LocalRuntime;

import static net.javacrumbs.jsonunit.JsonAssert.assertJsonEquals;
import static net.javacrumbs.jsonunit.JsonAssert.when;

/**
 * @author Sergey Galkin <srggal at gmail dot com>
 */
public class RuntimeTest {
    private LocalRuntime rt;

    @Before
    public void initialize() throws Exception {
        JsonUnitInitializer.initialize();
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
        Thread.sleep(100);

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
                    "state:         'RECOVERING'," +
                    "prevNode:      'storage-1'," +
                    "nextNode:      null," +
                    "lastUpdatedBy: 'master-1'" +
                "}" +
            "}"
        );


        rt.createStorageNode("storage-3");
        rt.waitForCompleteChain();
        Thread.sleep(100);
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
                    "state:         'RECOVERING'," +
                    "prevNode:      'storage-1'," +
                    "nextNode:      'storage-3'," +
                    "lastUpdatedBy: 'master-1'" +
                "}," +
                "storage-3: {" +
                    "role:          ['Tail']," +
                    "state:         'RECOVERING'," +
                    "prevNode:      'storage-2'," +
                    "nextNode:      null," +
                    "lastUpdatedBy: 'master-1'" +
                "}" +
            "}"
        );
    }

    private void verifyStorageStates(String expected) {
        assertJsonEquals(expected,
                rt.getAllStorageStates(),
                when(Option.IGNORING_EXTRA_FIELDS)
        );
    }
}
