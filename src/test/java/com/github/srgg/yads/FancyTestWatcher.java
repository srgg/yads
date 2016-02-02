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

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  @author Sergey Galkin <srggal at gmail dot com>
 */
public class FancyTestWatcher extends TestWatcher {
    private static final Logger logger= LoggerFactory.getLogger(FancyTestWatcher.class);

    @Override
    protected void starting(Description description) {
        super.starting(description);
        logger.info("\n----------------------------------------\n" +
                        "  [TEST STARTED]  {}\n" +
                        "----------------------------------------\n",
                description.getDisplayName());
    }

    @Override
    protected void finished(Description description) {
        super.finished(description);
        logger.info("\n----------------------------------------\n" +
                        "  [TEST FINISHED]  {}\n" +
                        "----------------------------------------\n",
                description.getDisplayName());
    }
    }